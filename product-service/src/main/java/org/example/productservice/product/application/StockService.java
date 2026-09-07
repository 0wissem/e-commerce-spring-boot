package org.example.productservice.product.application;

import org.example.productservice.product.application.dto.ProductResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;

/**
 * Retry wrapper around {@link ProductService#decrementStock}.
 *
 * WHY THIS IS A SEPARATE BEAN — the important part:
 *
 * The retry must run OUTSIDE the failed transaction. When an optimistic-lock conflict fires,
 * that transaction is already marked rollback-only; retrying inside it would just replay work
 * against a doomed unit and fail again at commit. So the loop has to sit outside the
 * transactional boundary and start a fresh one on each attempt.
 *
 * A private method in ProductService could not do that. @Transactional is proxy-based, so an
 * internal `this.decrementStock(...)` call bypasses the proxy entirely and never opens a new
 * transaction. Calling through an injected bean means every attempt genuinely goes through
 * the proxy and gets its own transaction. Same self-invocation rule that governs @Cacheable
 * and @Async.
 *
 * Deliberately NOT annotated @Transactional: this class must own no transaction at all.
 */
@Service
public class StockService {

    private static final Logger log = LoggerFactory.getLogger(StockService.class);

    /**
     * Bounded, because a retry loop without a ceiling is an outage waiting for contention.
     *
     * This started at 3 and a 20-thread test proved it too low: four threads burned all three
     * attempts and failed a decrement that should have succeeded. Under N-way contention on
     * one row, an immediate retry collides with the same crowd that just beat it.
     */
    private static final int MAX_ATTEMPTS = 10;

    /**
     * Backoff base. Retrying instantly is what made 3 attempts fail — every loser re-tried in
     * lockstep and collided again. Sleeping a jittered interval spreads them out.
     *
     * The JITTER is the essential part, not the delay: a fixed delay just reconvenes the same
     * herd one interval later. Same reasoning as jittering cache TTLs to avoid a stampede.
     */
    private static final long BACKOFF_BASE_MS = 5;

    private final IProductService productService;   // the PROXY, not the raw object

    public StockService(IProductService productService) {
        this.productService = productService;
    }

    /**
     * Decrements stock, retrying when another transaction wins the race.
     *
     * An InsufficientStockException is NOT retried — it is a business answer ("there aren't
     * enough"), and retrying cannot change it. Only lock conflicts are retried, because those
     * are a timing accident rather than a decision.
     */
    public ProductResponse decrementStock(String id, int quantity) {
        OptimisticLockingFailureException lastFailure = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                // Goes through the proxy → a NEW transaction on every attempt.
                return productService.decrementStock(id, quantity);
            } catch (OptimisticLockingFailureException e) {
                lastFailure = e;
                log.debug("Optimistic lock conflict on product {} (attempt {}/{})",
                        id, attempt, MAX_ATTEMPTS);
                backOff(attempt);
            }
        }

        log.warn("Giving up on product {} after {} optimistic-lock conflicts", id, MAX_ATTEMPTS);
        throw lastFailure;
    }

    /** Same retry policy for the compensating direction — it races just as hard. */
    public ProductResponse incrementStock(String id, int quantity) {
        OptimisticLockingFailureException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return productService.incrementStock(id, quantity);
            } catch (OptimisticLockingFailureException e) {
                lastFailure = e;
                backOff(attempt);
            }
        }
        throw lastFailure;
    }

    /**
     * Exponential backoff with full jitter: sleep a random interval in [0, base * 2^attempt).
     *
     * Randomness is doing the real work here. With a fixed delay every loser wakes at the same
     * instant and collides again — the retry storm just moves. Randomising the wait scatters
     * them across the window so they arrive one at a time.
     */
    private void backOff(int attempt) {
        long ceiling = BACKOFF_BASE_MS * (1L << Math.min(attempt, 6));   // capped, no overflow
        long sleep = ThreadLocalRandom.current().nextLong(ceiling + 1);
        try {
            Thread.sleep(sleep);
        } catch (InterruptedException ie) {
            // Never swallow an interrupt: restore the flag so the caller can react.
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying stock decrement", ie);
        }
    }
}

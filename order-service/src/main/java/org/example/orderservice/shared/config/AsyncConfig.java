package org.example.orderservice.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A dedicated pool for the cross-service HTTP calls made while building an order.
 *
 * WHY NOT ForkJoinPool.commonPool() — the default for CompletableFuture.supplyAsync:
 * the common pool is sized for CPU-bound work (cores - 1) and is shared by the whole JVM,
 * including parallel streams. Blocking it on network I/O starves everything else on it. Any
 * time a CompletableFuture wraps blocking I/O, give it its own pool.
 *
 * Bounded on purpose: an unbounded pool turns a slow downstream service into an
 * out-of-memory error instead of a queue.
 */
@Configuration
public class AsyncConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService orderLookupExecutor() {
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                // Named threads: an unnamed pool-2-thread-7 in a stack trace tells you nothing.
                Thread t = new Thread(r, "order-lookup-" + counter.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        };
        return Executors.newFixedThreadPool(8, factory);
    }
}

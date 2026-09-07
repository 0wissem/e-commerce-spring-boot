package org.example.productservice.product.domain;

/**
 * Raised when an order asks for more units than exist.
 *
 * Unchecked on purpose: it is a business rule violation, not a recoverable condition the
 * caller should be forced to catch — and only unchecked exceptions trigger a @Transactional
 * rollback by default.
 *
 * Lives in the domain package because it expresses a domain rule, and the domain has no
 * framework imports.
 */
public class InsufficientStockException extends RuntimeException {

    private final String productId;
    private final int available;
    private final int requested;

    public InsufficientStockException(String productId, int available, int requested) {
        super("Insufficient stock for product " + productId
                + ": requested " + requested + ", available " + available);
        this.productId = productId;
        this.available = available;
        this.requested = requested;
    }

    public String getProductId() { return productId; }
    public int getAvailable()    { return available; }
    public int getRequested()    { return requested; }
}

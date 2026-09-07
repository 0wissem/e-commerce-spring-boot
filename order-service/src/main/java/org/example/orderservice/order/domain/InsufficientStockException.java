package org.example.orderservice.order.domain;

/**
 * Raised when product-service refuses a stock reservation (HTTP 409).
 *
 * order-service does not own stock, so it cannot decide this itself — it only translates the
 * remote refusal into a domain exception, so the failure surfaces as a 409 to the caller
 * rather than a generic 500 from an unhandled client error.
 */
public class InsufficientStockException extends RuntimeException {

    private final String productId;
    private final int requested;

    public InsufficientStockException(String productId, int requested) {
        super("Insufficient stock for product " + productId + ": requested " + requested);
        this.productId = productId;
        this.requested = requested;
    }

    public String getProductId() { return productId; }
    public int getRequested()    { return requested; }
}

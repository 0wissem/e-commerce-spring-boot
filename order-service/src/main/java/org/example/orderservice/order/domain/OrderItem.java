package org.example.orderservice.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String productId;

    @Column(nullable = false)
    private String productName;

    /** @deprecated legacy double, dual-written during the expand/contract overlap. */
    @Deprecated
    @Column(name = "unit_price", nullable = false)
    private double unitPrice;

    @Column(name = "unit_amount", precision = 12, scale = 2)
    private BigDecimal unitAmount;

    @Convert(converter = OrderProductSnapshotConverter.class)
    @Column(columnDefinition = "text")
    private OrderProductSnapshot orderProductSnapshot;

    @Column(nullable = false)
    private int quantity;

    /** @deprecated legacy double, dual-written during the expand/contract overlap. */
    @Deprecated
    @Column(name = "total_price", nullable = false)
    private double totalPrice;

    /** unit_amount * quantity, computed exactly. */
    @Column(name = "line_amount", precision = 12, scale = 2)
    private BigDecimal lineAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    public OrderItem() {}

    /**
     * The line amount is DERIVED here rather than passed in, so it can never disagree with
     * unitAmount * quantity. That mismatch is exactly what floating-point money produced.
     */
    public OrderItem(String id, String productId, String productName, BigDecimal unitAmount,
                     OrderProductSnapshot orderProductSnapshot, int quantity) {
        this.id = id;
        this.productId = productId;
        this.productName = productName;
        this.orderProductSnapshot = orderProductSnapshot;
        this.quantity = quantity;
        setUnitAmount(unitAmount);
    }

    /** @deprecated pass a BigDecimal; the line total is derived, not supplied. */
    @Deprecated
    public OrderItem(String id, String productId, String productName, double unitPrice,
                     OrderProductSnapshot orderProductSnapshot, int quantity, double totalPrice) {
        this(id, productId, productName, BigDecimal.valueOf(unitPrice),
             orderProductSnapshot, quantity);
    }

    public BigDecimal getUnitAmount() { return unitAmount; }

    /** Sets the unit price and recomputes the line total. Both legacy doubles stay in sync. */
    public void setUnitAmount(BigDecimal unitAmount) {
        this.unitAmount = unitAmount == null ? null : unitAmount.setScale(2, RoundingMode.HALF_UP);
        this.unitPrice = this.unitAmount == null ? 0d : this.unitAmount.doubleValue();
        recomputeLineAmount();
    }

    public BigDecimal getLineAmount() { return lineAmount; }

    private void recomputeLineAmount() {
        this.lineAmount = unitAmount == null
                ? null
                : unitAmount.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
        this.totalPrice = this.lineAmount == null ? 0d : this.lineAmount.doubleValue();
    }

    public String getId() {
        return id;
    }

    public String getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public double getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public OrderProductSnapshot getProductSnapshot() {
        return orderProductSnapshot;
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    public Order getOrder() {
        return order;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    /** @deprecated use {@link #setUnitAmount(BigDecimal)}. Routed through it to keep the invariant. */
    @Deprecated
    public void setUnitPrice(double unitPrice) {
        setUnitAmount(BigDecimal.valueOf(unitPrice));
    }

    public void setProductSnapshot(OrderProductSnapshot orderProductSnapshot) {
        this.orderProductSnapshot = orderProductSnapshot;
    }

    /** Recomputes the line total — quantity and lineAmount must never drift apart. */
    public void setQuantity(int quantity) {
        this.quantity = quantity;
        recomputeLineAmount();
    }

    /**
     * @deprecated the line total is DERIVED from unitAmount * quantity and cannot be set
     * independently. Kept only so old callers compile; it intentionally does nothing.
     */
    @Deprecated
    public void setTotalPrice(double totalPrice) {
        // no-op: setting a derived value is what allowed totals to disagree with their lines.
    }

    public void setOrder(Order order) {
        this.order = order;
    }
}

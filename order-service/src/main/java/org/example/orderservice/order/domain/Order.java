package org.example.orderservice.order.domain;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false)
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    /** @deprecated legacy double total, dual-written during the expand/contract overlap. */
    @Deprecated
    @Column(name = "total_price", nullable = false)
    private double totalPrice;

    /** The real total. Exact decimal, and always equal to the sum of the line amounts. */
    @Column(name = "total_amount", precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "EUR";

    /** Human-readable reference: ORD-2026-A1B2C3D4. Nobody reads a UUID over the phone. */
    @Column(name = "order_number", nullable = false, length = 20, updatable = false)
    private String orderNumber;

    /**
     * Mapped with @JdbcTypeCode(SqlTypes.JSON), not an AttributeConverter.
     *
     * A converter hands JDBC a String, which binds as `varchar`, and Postgres refuses to
     * implicitly cast varchar -> jsonb. (order_product_snapshot gets away with a converter
     * only because its column is TEXT, not JSONB.) SqlTypes.JSON makes Hibernate bind the
     * parameter as a real JSON type, and it serializes the record with Jackson itself.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "shipping_address", columnDefinition = "jsonb")
    private ShippingAddress shippingAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "order",
            cascade = CascadeType.ALL,
            orphanRemoval = true )
    private List<OrderItem> orderItems;

    public Order () {}

    public Order (String id, String customerId, String customerName, BigDecimal totalAmount, OrderStatus orderStatus) {
        this.id = id;
        this.customerId = customerId;
        this.customerName = customerName;
        this.status = orderStatus;
        setTotalAmount(totalAmount);
    }

    /** @deprecated pass a BigDecimal. Kept so existing callers compile during the migration. */
    @Deprecated
    public Order (String id, String customerId, String customerName, double totalPrice, OrderStatus orderStatus) {
        this(id, customerId, customerName, BigDecimal.valueOf(totalPrice), orderStatus);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (orderNumber == null) orderNumber = generateOrderNumber(now);
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    private static String generateOrderNumber(Instant at) {
        int year = at.atZone(ZoneOffset.UTC).getYear();
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "ORD-" + year + "-" + suffix;
    }

    public OrderStatus getStatus() {
        return status;
    }

    /** @deprecated use {@link #getTotalAmount()}. */
    @Deprecated
    public double getTotalPrice() {
        return totalPrice;
    }

    public BigDecimal getTotalAmount() { return totalAmount; }

    /** Writes both columns — the new one is authoritative, the legacy double stays in sync. */
    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount == null
                ? null
                : totalAmount.setScale(2, RoundingMode.HALF_UP);
        this.totalPrice = this.totalAmount == null ? 0d : this.totalAmount.doubleValue();
    }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }

    public ShippingAddress getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(ShippingAddress shippingAddress) { this.shippingAddress = shippingAddress; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public String getCustomerId() {
        return customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getId() {
        return id;
    }

    public List<OrderItem> getOrderItems() {
        return orderItems;
    }

    public void setOrderItems(List<OrderItem> orderItems) {
        this.orderItems = orderItems;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    /** @deprecated use {@link #setTotalAmount(BigDecimal)}. */
    @Deprecated
    public void setTotalPrice(double totalPrice) {
        setTotalAmount(BigDecimal.valueOf(totalPrice));
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }
}

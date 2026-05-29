package org.example.springboot0.order.domain;

import jakarta.persistence.*;
import org.example.springboot0.order.infrastructure.OrderProductSnapshotConverter;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false)
    private String productId;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private double unitPrice;

    @Convert(converter = OrderProductSnapshotConverter.class)
    @Column(columnDefinition = "text")
    private OrderProductSnapshot productSnapshot;

    public OrderItem() {}

    public OrderItem(String id, String productId, String productName, int quantity, double unitPrice, OrderProductSnapshot productSnapshot) {
        this.id = id;
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.productSnapshot = productSnapshot;
    }

    public double getSubtotal() {
        return unitPrice * quantity;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(double unitPrice) { this.unitPrice = unitPrice; }

    public OrderProductSnapshot getProductSnapshot() { return productSnapshot; }
    public void setProductSnapshot(OrderProductSnapshot productSnapshot) { this.productSnapshot = productSnapshot; }
}
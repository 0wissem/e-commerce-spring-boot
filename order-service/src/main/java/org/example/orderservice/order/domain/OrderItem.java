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

    @Column(nullable = false)
    private double unitPrice;

    @Convert(converter = OrderProductSnapshotConverter.class)
    @Column(columnDefinition = "text")
    private OrderProductSnapshot orderProductSnapshot;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private double totalPrice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    public OrderItem() {}

    public OrderItem(String id, String productId, String productName, double unitPrice,
                     OrderProductSnapshot orderProductSnapshot, int quantity, double totalPrice) {
        this.id = id;
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.orderProductSnapshot = orderProductSnapshot;
        this.quantity = quantity;
        this.totalPrice = totalPrice;
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

    public void setUnitPrice(double unitPrice) {
        this.unitPrice = unitPrice;
    }

    public void setProductSnapshot(OrderProductSnapshot orderProductSnapshot) {
        this.orderProductSnapshot = orderProductSnapshot;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public void setTotalPrice(double totalPrice) {
        this.totalPrice = totalPrice;
    }

    public void setOrder(Order order) {
        this.order = order;
    }
}

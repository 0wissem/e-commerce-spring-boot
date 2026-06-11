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
    private String product_id;

    @Column(nullable = false)
    private String product_name;

    @Column(nullable = false)
    private double unit_price;

    @Convert(converter = OrderProductSnapshotConverter.class)
    @Column(columnDefinition = "text")
    private OrderProductSnapshot orderProductSnapshot;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private double total_price;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    public OrderItem() {}

    public OrderItem(String id, String product_id, String product_name, double unit_price,
                     OrderProductSnapshot orderProductSnapshot, int quantity, double total_price) {
        this.id = id;
        this.product_id = product_id;
        this.product_name = product_name;
        this.unit_price = unit_price;
        this.orderProductSnapshot = orderProductSnapshot;
        this.quantity = quantity;
        this.total_price = total_price;
    }

    public String getId() {
        return id;
    }

    public String getProduct_id() {
        return product_id;
    }

    public String getProduct_name() {
        return product_name;
    }

    public double getUnit_price() {
        return unit_price;
    }

    public int getQuantity() {
        return quantity;
    }

    public OrderProductSnapshot getProductSnapshot() {
        return orderProductSnapshot;
    }

    public double getTotal_price() {
        return total_price;
    }

    public Order getOrder() {
        return order;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setProduct_id(String product_id) {
        this.product_id = product_id;
    }

    public void setProduct_name(String product_name) {
        this.product_name = product_name;
    }

    public void setUnit_price(double unit_price) {
        this.unit_price = unit_price;
    }

    public void setProductSnapshot(OrderProductSnapshot orderProductSnapshot) {
        this.orderProductSnapshot = orderProductSnapshot;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public void setTotal_price(double total_price) {
        this.total_price = total_price;
    }

    public void setOrder(Order order) {
        this.order = order;
    }
}

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
import jakarta.persistence.Table;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String customer_id;

    @Column(nullable = false)
    private String customer_name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false)
    private double total_price;

    @OneToMany(mappedBy = "order",
            cascade = CascadeType.ALL,
            orphanRemoval = true )
    private List<OrderItem> orderItems;

    public Order () {}

    public Order (String id, String customer_id, String customer_name, double total_price, OrderStatus orderStatus) {
        this.id = id;
        this.customer_id = customer_id;
        this.customer_name = customer_name;
        this.total_price = total_price;
        this.status = orderStatus;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public double getTotal_price() {
        return total_price;
    }

    public String getCustomer_id() {
        return customer_id;
    }

    public String getCustomer_name() {
        return customer_name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public void setTotal_price(double total_price) {
        this.total_price = total_price;
    }

    public void setCustomer_id(String customer_id) {
        this.customer_id = customer_id;
    }

    public void setCustomer_name(String customer_name) {
        this.customer_name = customer_name;
    }
}

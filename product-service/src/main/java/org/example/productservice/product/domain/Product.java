package org.example.productservice.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.example.productservice.category.domain.Category;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "products")
@SQLRestriction("deleted_at IS NULL")
public class Product {

    /** VAT rate applied by {@link #getFinalPrice()}. Exact decimal — never a double. */
    private static final BigDecimal VAT_MULTIPLIER = new BigDecimal("1.19");

    /** Money is stored to 2 decimal places, rounded half-up. Matches NUMERIC(12,2) in the DB. */
    private static final int MONEY_SCALE = 2;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;

    @Id
    private String id;

    @ManyToMany
    @JoinTable(
            name = "product_categories",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private Set<Category> categories = new HashSet<>();


    @Column(nullable = false)
    private String name;

    /**
     * Legacy floating-point price. Still written during the expand/contract overlap so that an
     * older instance running alongside this one keeps working. Dropped in a later migration.
     * Never read for calculations — {@link #priceAmount} is the source of truth.
     */
    @Deprecated
    @Column(name = "price", nullable = false)
    private double price;

    /** The real price. Exact decimal, backed by NUMERIC(12,2). */
    @Column(name = "price_amount", precision = 12, scale = MONEY_SCALE)
    private BigDecimal priceAmount;

    /** ISO-4217 alpha-3. An amount without a currency is not money. */
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "EUR";

    @Column(nullable = false)
    private int stockQuantity;

    /** Business identifier, distinct from the surrogate UUID id. Unique. */
    @Column(name = "sku", nullable = false, length = 64)
    private String sku;

    @Column(name = "brand", length = 120)
    private String brand;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column
    private LocalDateTime deletedAt;

    /**
     * Audit timestamps maintained by JPA lifecycle callbacks rather than the DB default,
     * so the in-memory entity carries the same values as the row without a re-read.
     * The DB defaults remain as a backstop for writes that bypass Hibernate.
     */
    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (sku == null) sku = generateSku(id);
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    private static String generateSku(String id) {
        String seed = (id == null ? UUID.randomUUID().toString() : id).replace("-", "");
        return "SKU-" + seed.substring(0, Math.min(10, seed.length())).toUpperCase();
    }

    public Product() {}

    public Product(String id, String name, BigDecimal priceAmount, int stockQuantity) {
        this.id = id;
        this.name = name;
        this.stockQuantity = stockQuantity;
        setPriceAmount(priceAmount);
    }

    /**
     * @deprecated pass a {@link BigDecimal} — a double cannot represent 0.10 exactly.
     * Kept so existing callers compile during the migration.
     */
    @Deprecated
    public Product(String id, String name, double price, int stockQuantity) {
        this(id, name, BigDecimal.valueOf(price), stockQuantity);
    }

    /**
     * Price including VAT, rounded half-up to 2 decimals.
     * Computed in {@link BigDecimal}: {@code 0.1 + 0.2} is exactly {@code 0.3} here, which is
     * not true of the double arithmetic this replaced.
     */
    public BigDecimal getFinalPrice() {
        if (priceAmount == null) return null;
        return priceAmount.multiply(VAT_MULTIPLIER).setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getPriceAmount() { return priceAmount; }

    /** Writes both columns — the new one is authoritative, the legacy double is kept in sync. */
    public void setPriceAmount(BigDecimal priceAmount) {
        this.priceAmount = priceAmount == null
                ? null
                : priceAmount.setScale(MONEY_SCALE, MONEY_ROUNDING);
        this.price = this.priceAmount == null ? 0d : this.priceAmount.doubleValue();
    }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    /** @deprecated use {@link #getPriceAmount()}. */
    @Deprecated
    public double getPrice() { return price; }

    /** @deprecated use {@link #setPriceAmount(BigDecimal)}. */
    @Deprecated
    public void setPrice(double price) { setPriceAmount(BigDecimal.valueOf(price)); }

    public int getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(int stockQuantity) { this.stockQuantity = stockQuantity; }

    public Set<Category> getCategories() { return categories; }
    public void setCategories(Set<Category> categories) { this.categories = categories; }

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
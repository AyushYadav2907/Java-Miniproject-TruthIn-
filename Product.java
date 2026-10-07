package com.truthscan.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A packaged product. Nutrition values are per 100 g / 100 ml; null means "unknown".
 * Maps to the "products" table created in db/migration/V1__create_products.sql.
 */
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 14)
    private String barcode;

    @Column(nullable = false)
    private String name;

    private String brand;
    private String category;

    @Column(name = "ingredients_text", length = 5000)
    private String ingredientsText;

    /** Comma separated additive codes, e.g. "e102,e211". */
    @Column(length = 1000)
    private String additives;

    /** Comma separated allergens, e.g. "milk,peanuts". */
    @Column(length = 1000)
    private String allergens;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(name = "energy_kcal")
    private Double energyKcal;
    private Double sugar;
    private Double fat;
    @Column(name = "saturated_fat")
    private Double saturatedFat;
    private Double salt;
    private Double fiber;
    private Double protein;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProductSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status;

    @Column(name = "submitted_by")
    private String submittedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    // ---------- Getters and setters ----------

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getIngredientsText() { return ingredientsText; }
    public void setIngredientsText(String ingredientsText) { this.ingredientsText = ingredientsText; }

    public String getAdditives() { return additives; }
    public void setAdditives(String additives) { this.additives = additives; }

    public String getAllergens() { return allergens; }
    public void setAllergens(String allergens) { this.allergens = allergens; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public Double getEnergyKcal() { return energyKcal; }
    public void setEnergyKcal(Double energyKcal) { this.energyKcal = energyKcal; }

    public Double getSugar() { return sugar; }
    public void setSugar(Double sugar) { this.sugar = sugar; }

    public Double getFat() { return fat; }
    public void setFat(Double fat) { this.fat = fat; }

    public Double getSaturatedFat() { return saturatedFat; }
    public void setSaturatedFat(Double saturatedFat) { this.saturatedFat = saturatedFat; }

    public Double getSalt() { return salt; }
    public void setSalt(Double salt) { this.salt = salt; }

    public Double getFiber() { return fiber; }
    public void setFiber(Double fiber) { this.fiber = fiber; }

    public Double getProtein() { return protein; }
    public void setProtein(Double protein) { this.protein = protein; }

    public ProductSource getSource() { return source; }
    public void setSource(ProductSource source) { this.source = source; }

    public ProductStatus getStatus() { return status; }
    public void setStatus(ProductStatus status) { this.status = status; }

    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

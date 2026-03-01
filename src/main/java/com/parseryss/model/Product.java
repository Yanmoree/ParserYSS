package com.parseryss.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Модель товара
 */
public class Product {
    private String id;
    private String site;
    private String query;
    private String url;
    private String title;
    private double price;
    private List<String> images;
    private Long publishTime;
    private Integer ageMinutes;
    private String location;
    private String seller;

    public Product() {
        this.images = new ArrayList<>();
    }

    public void addImage(String imageUrl) {
        if (imageUrl != null && !imageUrl.isEmpty()) {
            this.images.add(imageUrl);
        }
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSite() {
        return site;
    }

    public void setSite(String site) {
        this.site = site;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public List<String> getImages() {
        return images;
    }

    public void setImages(List<String> images) {
        this.images = images;
    }

    public Long getPublishTime() {
        return publishTime;
    }

    public void setPublishTime(Long publishTime) {
        this.publishTime = publishTime;
    }

    public Integer getAgeMinutes() {
        return ageMinutes;
    }

    public void setAgeMinutes(Integer ageMinutes) {
        this.ageMinutes = ageMinutes;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getSeller() {
        return seller;
    }

    public void setSeller(String seller) {
        this.seller = seller;
    }

    // Alias method for compatibility
    public void setPlatform(String platform) {
        this.site = platform;
    }
    
    // Category field for Avito
    private String category;
    
    public String getCategory() {
        return category;
    }
    
    public void setCategory(String category) {
        this.category = category;
    }
    
    // Курсы валют (приблизительные)
    private static final double YEN_TO_RUB = 0.65;  // 1 йена ≈ 0.65 рубля
    private static final double YUAN_TO_RUB = 14.0; // 1 юань ≈ 14 рублей
    
    /**
     * Получить цену в рублях
     */
    public double getPriceRubles() {
        if ("avito".equalsIgnoreCase(site)) {
            return price; // Уже в рублях
        }
        if ("mercari".equalsIgnoreCase(site)) {
            return Math.round(price * YEN_TO_RUB * 100.0) / 100.0;
        }
        if ("goofish".equalsIgnoreCase(site)) {
            return Math.round(price * YUAN_TO_RUB * 100.0) / 100.0;
        }
        return price;
    }
    
    /**
     * Получить полное отображение цены: "¥1000 (~650₽)" или "1000₽"
     */
    public String getFullPriceDisplay() {
        if ("avito".equalsIgnoreCase(site)) {
            return String.format("%.0f₽", price);
        }
        if ("mercari".equalsIgnoreCase(site)) {
            return String.format("¥%.0f (~%.0f₽)", price, getPriceRubles());
        }
        if ("goofish".equalsIgnoreCase(site)) {
            return String.format("%.2f¥ (~%.0f₽)", price, getPriceRubles());
        }
        return String.format("%.2f", price);
    }
}

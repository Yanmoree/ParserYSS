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
}

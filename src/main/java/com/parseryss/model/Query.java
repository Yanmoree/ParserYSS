package com.parseryss.model;

import java.time.LocalDateTime;

/**
 * Поисковый запрос
 */
public class Query {
    private String id; // UUID
    private long userId;
    private String text;
    private LocalDateTime createdAt;

    public Query(String id, long userId, String text) {
        this.id = id;
        this.userId = userId;
        this.text = text;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    // Alias methods for compatibility
    public String getQueryId() {
        return id;
    }

    public String getQueryText() {
        return text;
    }

    public boolean isActive() {
        return true; // По умолчанию все запросы активны
    }

    /**
     * Преобразовать в JSON
     */
    public org.json.JSONObject toJson() {
        org.json.JSONObject json = new org.json.JSONObject();
        try {
            json.put("id", id);
            json.put("userId", userId);
            json.put("text", text);
            json.put("createdAt", createdAt != null ? createdAt.toString() : "");
        } catch (Exception e) {
            // Ignore
        }
        return json;
    }

    /**
     * Создать из JSON
     */
    public static Query fromJson(org.json.JSONObject json) {
        String id = json.optString("id", "");
        long userId = json.optLong("userId", 0);
        String text = json.optString("text", "");
        Query query = new Query(id, userId, text);
        String createdAtStr = json.optString("createdAt", "");
        if (!createdAtStr.isEmpty()) {
            try {
                query.setCreatedAt(LocalDateTime.parse(createdAtStr));
            } catch (Exception e) {
                // Ignore
            }
        }
        return query;
    }
}

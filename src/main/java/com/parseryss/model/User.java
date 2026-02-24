package com.parseryss.model;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Модель пользователя системы
 */
public class User {
    private long userId;
    private String username;
    private boolean isAdmin;
    private LocalDateTime subscriptionExpiry;
    private boolean hasLifetimeSubscription;
    private Set<String> allowedPlatforms; // "avito", "mercari", "goofish"
    private LocalDateTime createdAt;
    private LocalDateTime lastActive;

    public User(long userId) {
        this.userId = userId;
        this.isAdmin = false;
        this.hasLifetimeSubscription = false;
        this.allowedPlatforms = new HashSet<>();
        this.createdAt = LocalDateTime.now();
        this.lastActive = LocalDateTime.now();
    }

    // Проверка активности подписки
    public boolean isSubscriptionActive() {
        if (hasLifetimeSubscription) {
            return true;
        }
        if (subscriptionExpiry == null) {
            return false;
        }
        return LocalDateTime.now().isBefore(subscriptionExpiry);
    }

    // Проверка доступа к платформе
    public boolean hasPlatformAccess(String platform) {
        if (isAdmin) {
            return true; // Админ имеет доступ ко всем платформам
        }
        return allowedPlatforms.contains(platform.toLowerCase());
    }

    // Getters and Setters
    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean isAdmin() {
        return isAdmin;
    }

    public void setAdmin(boolean admin) {
        isAdmin = admin;
    }

    public LocalDateTime getSubscriptionExpiry() {
        return subscriptionExpiry;
    }

    public void setSubscriptionExpiry(LocalDateTime subscriptionExpiry) {
        this.subscriptionExpiry = subscriptionExpiry;
    }

    public boolean hasLifetimeSubscription() {
        return hasLifetimeSubscription;
    }

    public void setLifetimeSubscription(boolean lifetimeSubscription) {
        this.hasLifetimeSubscription = lifetimeSubscription;
    }

    public Set<String> getAllowedPlatforms() {
        return allowedPlatforms;
    }

    public void setAllowedPlatforms(Set<String> allowedPlatforms) {
        this.allowedPlatforms = allowedPlatforms;
    }

    public void addPlatform(String platform) {
        this.allowedPlatforms.add(platform.toLowerCase());
    }

    public void removePlatform(String platform) {
        this.allowedPlatforms.remove(platform.toLowerCase());
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastActive() {
        return lastActive;
    }

    public void setLastActive(LocalDateTime lastActive) {
        this.lastActive = lastActive;
    }

    public void updateLastActive() {
        this.lastActive = LocalDateTime.now();
    }

    // Alias methods for compatibility
    public long getId() {
        return userId;
    }

    public void setId(long id) {
        this.userId = id;
    }

    public void setChatId(long chatId) {
        this.userId = chatId;
    }

    public LocalDateTime getSubscriptionEnd() {
        return subscriptionExpiry;
    }

    public Set<String> getEnabledPlatforms() {
        return allowedPlatforms;
    }

    public boolean hasAccessToPlatform(String platform) {
        return hasPlatformAccess(platform);
    }

    public void setSubscriptionActive(boolean active) {
        if (active && subscriptionExpiry == null) {
            this.hasLifetimeSubscription = true;
        }
    }

    /**
     * Преобразовать в JSON
     */
    public org.json.JSONObject toJson() {
        org.json.JSONObject json = new org.json.JSONObject();
        try {
            json.put("userId", userId);
            json.put("username", username != null ? username : "");
            json.put("isAdmin", isAdmin);
            json.put("subscriptionExpiry", subscriptionExpiry != null ? subscriptionExpiry.toString() : "");
            json.put("hasLifetimeSubscription", hasLifetimeSubscription);
            json.put("allowedPlatforms", new org.json.JSONArray(allowedPlatforms));
            json.put("createdAt", createdAt != null ? createdAt.toString() : "");
            json.put("lastActive", lastActive != null ? lastActive.toString() : "");
        } catch (Exception e) {
            // Ignore
        }
        return json;
    }

    /**
     * Создать из JSON
     */
    public static User fromJson(org.json.JSONObject json) {
        long userId = json.optLong("userId", 0);
        User user = new User(userId);
        user.setUsername(json.optString("username", ""));
        user.setAdmin(json.optBoolean("isAdmin", false));
        user.setLifetimeSubscription(json.optBoolean("hasLifetimeSubscription", false));
        
        String expiryStr = json.optString("subscriptionExpiry", "");
        if (!expiryStr.isEmpty()) {
            try {
                user.setSubscriptionExpiry(LocalDateTime.parse(expiryStr));
            } catch (Exception e) {
                // Ignore
            }
        }
        
        org.json.JSONArray platformsArray = json.optJSONArray("allowedPlatforms");
        if (platformsArray != null) {
            Set<String> platforms = new HashSet<>();
            for (int i = 0; i < platformsArray.length(); i++) {
                platforms.add(platformsArray.optString(i));
            }
            user.setAllowedPlatforms(platforms);
        }
        
        String createdAtStr = json.optString("createdAt", "");
        if (!createdAtStr.isEmpty()) {
            try {
                user.setCreatedAt(LocalDateTime.parse(createdAtStr));
            } catch (Exception e) {
                // Ignore
            }
        }
        
        String lastActiveStr = json.optString("lastActive", "");
        if (!lastActiveStr.isEmpty()) {
            try {
                user.setLastActive(LocalDateTime.parse(lastActiveStr));
            } catch (Exception e) {
                // Ignore
            }
        }
        
        return user;
    }
}

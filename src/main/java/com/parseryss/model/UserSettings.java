package com.parseryss.model;

import java.util.HashSet;
import java.util.Set;

/**
 * Настройки парсера для пользователя
 */
public class UserSettings {
    private long userId;
    private Set<String> enabledPlatforms; // Выбранные платформы для парсинга
    private double minPrice;
    private double maxPrice;
    private int checkIntervalSeconds; // Интервал проверки
    private int maxAgeMinutes; // Максимальный возраст товара
    private int maxPages; // Максимальное количество страниц

    public UserSettings(long userId) {
        this.userId = userId;
        this.enabledPlatforms = new HashSet<>();
        // Значения по умолчанию
        this.minPrice = 0;
        this.maxPrice = 999999999;
        this.checkIntervalSeconds = 25; // 25 секунд
        this.maxAgeMinutes = 0; // Не используется
        this.maxPages = 3;
        // По умолчанию включены все платформы
        this.enabledPlatforms.add("mercari");
        this.enabledPlatforms.add("avito");
        this.enabledPlatforms.add("goofish");
    }

    // Getters and Setters
    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public Set<String> getEnabledPlatforms() {
        return enabledPlatforms;
    }

    public void setEnabledPlatforms(Set<String> enabledPlatforms) {
        this.enabledPlatforms = enabledPlatforms;
    }

    public void enablePlatform(String platform) {
        this.enabledPlatforms.add(platform.toLowerCase());
    }

    public void disablePlatform(String platform) {
        this.enabledPlatforms.remove(platform.toLowerCase());
    }

    public boolean isPlatformEnabled(String platform) {
        return enabledPlatforms.contains(platform.toLowerCase());
    }

    public double getMinPrice() {
        return minPrice;
    }

    public void setMinPrice(double minPrice) {
        this.minPrice = minPrice;
    }

    public double getMaxPrice() {
        return maxPrice;
    }

    public void setMaxPrice(double maxPrice) {
        this.maxPrice = maxPrice;
    }

    public int getCheckIntervalSeconds() {
        return checkIntervalSeconds;
    }

    public void setCheckIntervalSeconds(int checkIntervalSeconds) {
        this.checkIntervalSeconds = checkIntervalSeconds;
    }

    public int getMaxAgeMinutes() {
        return maxAgeMinutes;
    }

    public void setMaxAgeMinutes(int maxAgeMinutes) {
        this.maxAgeMinutes = maxAgeMinutes;
    }

    public int getMaxPages() {
        return maxPages;
    }

    public void setMaxPages(int maxPages) {
        this.maxPages = maxPages;
    }

    /**
     * Преобразовать в JSON
     */
    public org.json.JSONObject toJson() {
        org.json.JSONObject json = new org.json.JSONObject();
        try {
            json.put("userId", userId);
            json.put("enabledPlatforms", new org.json.JSONArray(enabledPlatforms));
            json.put("minPrice", minPrice);
            json.put("maxPrice", maxPrice);
            json.put("checkIntervalSeconds", checkIntervalSeconds);
            json.put("maxAgeMinutes", maxAgeMinutes);
            json.put("maxPages", maxPages);
        } catch (Exception e) {
            // Ignore
        }
        return json;
    }

    /**
     * Создать из JSON
     */
    public static UserSettings fromJson(org.json.JSONObject json) {
        long userId = json.optLong("userId", 0);
        UserSettings settings = new UserSettings(userId);
        
        org.json.JSONArray platformsArray = json.optJSONArray("enabledPlatforms");
        if (platformsArray != null) {
            Set<String> platforms = new HashSet<>();
            for (int i = 0; i < platformsArray.length(); i++) {
                platforms.add(platformsArray.optString(i));
            }
            settings.setEnabledPlatforms(platforms);
        }
        
        settings.setMinPrice(json.optDouble("minPrice", 0));
        settings.setMaxPrice(json.optDouble("maxPrice", 999999999));
        settings.setCheckIntervalSeconds(json.optInt("checkIntervalSeconds", 25));
        settings.setMaxAgeMinutes(json.optInt("maxAgeMinutes", 0));
        settings.setMaxPages(json.optInt("maxPages", 3));
        
        return settings;
    }
}

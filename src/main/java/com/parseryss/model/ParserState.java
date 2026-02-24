package com.parseryss.model;

import org.json.JSONObject;

/**
 * Состояние парсера для конкретной комбинации (userId, queryId, platform)
 */
public class ParserState {
    private long userId;
    private String queryId;
    private String platform;
    private long lastPublishTime;
    private String lastProductId;

    public ParserState() {
    }

    public ParserState(long userId, String queryId, String platform) {
        this.userId = userId;
        this.queryId = queryId;
        this.platform = platform;
        this.lastPublishTime = 0;
        this.lastProductId = null;
    }

    // Getters and Setters
    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public String getQueryId() {
        return queryId;
    }

    public void setQueryId(String queryId) {
        this.queryId = queryId;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public long getLastPublishTime() {
        return lastPublishTime;
    }

    public void setLastPublishTime(long lastPublishTime) {
        this.lastPublishTime = lastPublishTime;
    }

    public String getLastProductId() {
        return lastProductId;
    }

    public void setLastProductId(String lastProductId) {
        this.lastProductId = lastProductId;
    }

    public void setLastUpdateTime(long lastUpdateTime) {
        this.lastPublishTime = lastUpdateTime;
    }
    
    /**
     * Преобразовать в JSON
     */
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("userId", userId);
            json.put("queryId", queryId);
            json.put("platform", platform);
            json.put("lastPublishTime", lastPublishTime);
            json.put("lastProductId", lastProductId != null ? lastProductId : "");
        } catch (Exception e) {
            // Ignore
        }
        return json;
    }
    
    /**
     * Создать из JSON
     */
    public static ParserState fromJson(JSONObject json) {
        ParserState state = new ParserState();
        state.userId = json.optLong("userId", 0);
        state.queryId = json.optString("queryId", "");
        state.platform = json.optString("platform", "");
        state.lastPublishTime = json.optLong("lastPublishTime", 0);
        String productId = json.optString("lastProductId", "");
        state.lastProductId = productId.isEmpty() ? null : productId;
        return state;
    }
}

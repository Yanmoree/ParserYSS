package com.parseryss.bot;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Управление состояниями пользователей
 */
public class UserState {
    
    public enum State {
        NONE,
        WAITING_FOR_QUERY,
        WAITING_FOR_MIN_PRICE,
        WAITING_FOR_MAX_PRICE,
        WAITING_FOR_USER_ID,
        WAITING_FOR_SUBSCRIPTION_DAYS
    }
    
    // Хранение целевого пользователя для операций с подпиской
    private static final Map<Long, Long> subscriptionTargetUsers = new ConcurrentHashMap<>();
    
    public static void setSubscriptionTargetUser(long adminId, long targetUserId) {
        subscriptionTargetUsers.put(adminId, targetUserId);
    }
    
    public static Long getSubscriptionTargetUser(long adminId) {
        return subscriptionTargetUsers.get(adminId);
    }
    
    public static void clearSubscriptionTargetUser(long adminId) {
        subscriptionTargetUsers.remove(adminId);
    }
    
    private static final Map<Long, State> userStates = new ConcurrentHashMap<>();
    
    public static void setState(long userId, State state) {
        userStates.put(userId, state);
    }
    
    public static State getState(long userId) {
        return userStates.getOrDefault(userId, State.NONE);
    }
    
    public static void clearState(long userId) {
        userStates.remove(userId);
    }
    
    public static boolean isWaitingForInput(long userId) {
        State state = getState(userId);
        return state != State.NONE;
    }
}

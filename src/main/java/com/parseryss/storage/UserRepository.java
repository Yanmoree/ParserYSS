package com.parseryss.storage;

import com.parseryss.model.User;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Репозиторий для управления пользователями
 */
public class UserRepository {
    private static final Logger logger = LoggerFactory.getLogger(UserRepository.class);
    private static final String USERS_FILE = "users.json";
    
    private final FileStorage storage;
    private final Map<Long, User> cache = new ConcurrentHashMap<>();
    
    public UserRepository(FileStorage storage) {
        this.storage = storage;
        loadAll();
    }
    
    /**
     * Загрузить всех пользователей из файла
     */
    private void loadAll() {
        try {
            JSONArray array = storage.loadJsonArray(USERS_FILE);
            for (int i = 0; i < array.length(); i++) {
                JSONObject json = array.getJSONObject(i);
                User user = User.fromJson(json);
                cache.put(user.getUserId(), user);
            }
            logger.info("Загружено {} пользователей", cache.size());
        } catch (Exception e) {
            logger.error("Ошибка загрузки пользователей: {}", e.getMessage());
        }
    }
    
    /**
     * Сохранить всех пользователей в файл
     */
    private void saveAll() {
        try {
            JSONArray array = new JSONArray();
            for (User user : cache.values()) {
                array.put(user.toJson());
            }
            storage.saveJsonArray(USERS_FILE, array);
        } catch (Exception e) {
            logger.error("Ошибка сохранения пользователей: {}", e.getMessage());
        }
    }
    
    /**
     * Получить пользователя по ID
     */
    public User getUser(long userId) {
        return cache.get(userId);
    }
    
    /**
     * Проверить существование пользователя
     */
    public boolean exists(long userId) {
        return cache.containsKey(userId);
    }
    
    /**
     * Сохранить пользователя
     */
    public void save(User user) {
        cache.put(user.getUserId(), user);
        saveAll();
        logger.info("Пользователь {} сохранен", user.getUserId());
    }
    
    /**
     * Удалить пользователя
     */
    public void delete(long userId) {
        cache.remove(userId);
        saveAll();
        logger.info("Пользователь {} удален", userId);
    }
    
    /**
     * Получить всех пользователей
     */
    public List<User> getAll() {
        return new ArrayList<>(cache.values());
    }
    
    /**
     * Проверить, является ли пользователь администратором
     */
    public boolean isAdmin(long userId) {
        User user = cache.get(userId);
        return user != null && user.isAdmin();
    }
    
    /**
     * Проверить активность подписки
     */
    public boolean isSubscriptionActive(long userId) {
        User user = cache.get(userId);
        if (user == null) return false;
        
        if (user.getSubscriptionEnd() == null) {
            return true; // Бессрочная подписка
        }
        
        return user.getSubscriptionEnd().isAfter(LocalDateTime.now());
    }
    
    /**
     * Проверить доступ к платформе
     */
    public boolean hasPlatformAccess(long userId, String platform) {
        User user = cache.get(userId);
        if (user == null) return false;
        
        // Проверяем активность подписки
        if (!isSubscriptionActive(userId)) return false;
        
        // Если список платформ пустой - доступ ко всем
        if (user.getEnabledPlatforms().isEmpty()) return true;
        
        return user.getEnabledPlatforms().contains(platform.toLowerCase());
    }

    /**
     * Получить пользователя по ID (альтернативный метод)
     */
    public User getById(long userId) {
        return getUser(userId);
    }
}

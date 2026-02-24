package com.parseryss.storage;

import com.parseryss.model.UserSettings;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Репозиторий для управления настройками пользователей
 */
public class UserSettingsRepository {
    private static final Logger logger = LoggerFactory.getLogger(UserSettingsRepository.class);
    private static final String SETTINGS_FILE_PREFIX = "settings_";
    
    private final FileStorage storage;
    private final Map<Long, UserSettings> cache = new ConcurrentHashMap<>();
    
    public UserSettingsRepository(FileStorage storage) {
        this.storage = storage;
    }
    
    /**
     * Получить настройки пользователя
     */
    public UserSettings getSettings(long userId) {
        // Проверяем кэш
        if (cache.containsKey(userId)) {
            return cache.get(userId);
        }
        
        // Загружаем из файла
        String filename = SETTINGS_FILE_PREFIX + userId + ".json";
        if (storage.exists(filename)) {
            try {
                JSONObject json = storage.loadJson(filename);
                UserSettings settings = UserSettings.fromJson(json);
                cache.put(userId, settings);
                return settings;
            } catch (Exception e) {
                logger.error("Ошибка загрузки настроек пользователя {}: {}", userId, e.getMessage());
            }
        }
        
        // Создаем настройки по умолчанию
        UserSettings settings = new UserSettings(userId);
        cache.put(userId, settings);
        return settings;
    }
    
    /**
     * Сохранить настройки пользователя
     */
    public void save(UserSettings settings) {
        cache.put(settings.getUserId(), settings);
        
        String filename = SETTINGS_FILE_PREFIX + settings.getUserId() + ".json";
        storage.saveJson(filename, settings.toJson());
        
        logger.info("Настройки пользователя {} сохранены", settings.getUserId());
    }
    
    /**
     * Удалить настройки пользователя
     */
    public void delete(long userId) {
        cache.remove(userId);
        
        String filename = SETTINGS_FILE_PREFIX + userId + ".json";
        storage.delete(filename);
        
        logger.info("Настройки пользователя {} удалены", userId);
    }
    
    /**
     * Проверить, включена ли платформа
     */
    public boolean isPlatformEnabled(long userId, String platform) {
        UserSettings settings = getSettings(userId);
        return settings.isPlatformEnabled(platform);
    }

    /**
     * Получить настройки по ID пользователя (альтернативный метод)
     */
    public UserSettings getByUserId(long userId) {
        return getSettings(userId);
    }
}

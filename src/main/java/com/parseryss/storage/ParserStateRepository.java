package com.parseryss.storage;

import com.parseryss.model.ParserState;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Репозиторий для управления состояниями парсера
 * Хранит время последних товаров для каждой комбинации (userId, queryId, platform)
 */
public class ParserStateRepository {
    private static final Logger logger = LoggerFactory.getLogger(ParserStateRepository.class);
    private static final String STATES_FILE = "parser_states.json";
    
    private final FileStorage storage;
    private final Map<String, ParserState> cache = new ConcurrentHashMap<>(); // key: userId_queryId_platform
    
    public ParserStateRepository(FileStorage storage) {
        this.storage = storage;
        loadAll();
    }
    
    /**
     * Загрузить все состояния из файла
     */
    private void loadAll() {
        try {
            JSONArray array = storage.loadJsonArray(STATES_FILE);
            for (int i = 0; i < array.length(); i++) {
                JSONObject json = array.getJSONObject(i);
                ParserState state = ParserState.fromJson(json);
                String key = makeKey(state.getUserId(), state.getQueryId(), state.getPlatform());
                cache.put(key, state);
            }
            logger.info("Загружено {} состояний парсера", cache.size());
        } catch (Exception e) {
            logger.error("Ошибка загрузки состояний: {}", e.getMessage());
        }
    }
    
    /**
     * Сохранить все состояния в файл
     */
    private void saveAll() {
        try {
            JSONArray array = new JSONArray();
            for (ParserState state : cache.values()) {
                array.put(state.toJson());
            }
            storage.saveJsonArray(STATES_FILE, array);
        } catch (Exception e) {
            logger.error("Ошибка сохранения состояний: {}", e.getMessage());
        }
    }
    
    private String makeKey(long userId, String queryId, String platform) {
        return userId + "_" + queryId + "_" + platform.toLowerCase();
    }
    
    /**
     * Получить состояние
     */
    public ParserState getState(long userId, String queryId, String platform) {
        String key = makeKey(userId, queryId, platform);
        return cache.get(key);
    }
    
    /**
     * Получить время последнего товара
     */
    public long getLastPublishTime(long userId, String queryId, String platform) {
        ParserState state = getState(userId, queryId, platform);
        return state != null ? state.getLastPublishTime() : 0;
    }
    
    /**
     * Получить ID последнего товара
     */
    public String getLastProductId(long userId, String queryId, String platform) {
        ParserState state = getState(userId, queryId, platform);
        return state != null ? state.getLastProductId() : null;
    }
    
    /**
     * Обновить состояние (время и ID последнего товара)
     */
    public void updateState(long userId, String queryId, String platform, long publishTime, String productId) {
        String key = makeKey(userId, queryId, platform);
        ParserState state = cache.get(key);
        
        if (state == null) {
            state = new ParserState(userId, queryId, platform);
        }
        
        state.setLastPublishTime(publishTime);
        state.setLastProductId(productId);
        state.setLastUpdateTime(System.currentTimeMillis());
        
        cache.put(key, state);
        saveAll();
        
        logger.debug("Обновлено состояние: user={}, query={}, platform={}, time={}, id={}",
            userId, queryId, platform, publishTime, productId);
    }
    
    /**
     * Получить все состояния пользователя
     */
    public List<ParserState> getUserStates(long userId) {
        return cache.values().stream()
            .filter(s -> s.getUserId() == userId)
            .collect(Collectors.toList());
    }
    
    /**
     * Удалить состояние
     */
    public void deleteState(long userId, String queryId, String platform) {
        String key = makeKey(userId, queryId, platform);
        cache.remove(key);
        saveAll();
        logger.info("Удалено состояние: user={}, query={}, platform={}", userId, queryId, platform);
    }
    
    /**
     * Удалить все состояния для запроса
     */
    public void deleteQueryStates(long userId, String queryId) {
        String prefix = userId + "_" + queryId + "_";
        List<String> keysToRemove = cache.keySet().stream()
            .filter(key -> key.startsWith(prefix))
            .collect(Collectors.toList());
        
        keysToRemove.forEach(cache::remove);
        saveAll();
        logger.info("Удалено {} состояний для запроса {} пользователя {}", keysToRemove.size(), queryId, userId);
    }
    
    /**
     * Удалить все состояния пользователя
     */
    public void deleteUserStates(long userId) {
        String prefix = userId + "_";
        List<String> keysToRemove = cache.keySet().stream()
            .filter(key -> key.startsWith(prefix))
            .collect(Collectors.toList());
        
        keysToRemove.forEach(cache::remove);
        saveAll();
        logger.info("Удалено {} состояний пользователя {}", keysToRemove.size(), userId);
    }

    /**
     * Сохранить состояние (альтернативный метод для совместимости)
     */
    public void saveState(long userId, long queryIdLong, String platform, Long publishTime, String productId) {
        String queryId = String.valueOf(queryIdLong);
        updateState(userId, queryId, platform, publishTime != null ? publishTime : 0, productId);
    }
}

package com.parseryss.storage;

import com.parseryss.model.Query;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Репозиторий для управления поисковыми запросами
 */
public class QueryRepository {
    private static final Logger logger = LoggerFactory.getLogger(QueryRepository.class);
    private static final String QUERIES_FILE = "queries.json";
    
    private final FileStorage storage;
    private final Map<String, Query> cache = new ConcurrentHashMap<>(); // key: userId_queryId
    
    public QueryRepository(FileStorage storage) {
        this.storage = storage;
        loadAll();
    }
    
    /**
     * Загрузить все запросы из файла
     */
    private void loadAll() {
        try {
            JSONArray array = storage.loadJsonArray(QUERIES_FILE);
            for (int i = 0; i < array.length(); i++) {
                JSONObject json = array.getJSONObject(i);
                Query query = Query.fromJson(json);
                String key = makeKey(query.getUserId(), query.getQueryId());
                cache.put(key, query);
            }
            logger.info("Загружено {} запросов", cache.size());
        } catch (Exception e) {
            logger.error("Ошибка загрузки запросов: {}", e.getMessage());
        }
    }
    
    /**
     * Сохранить все запросы в файл
     */
    private void saveAll() {
        try {
            JSONArray array = new JSONArray();
            for (Query query : cache.values()) {
                array.put(query.toJson());
            }
            storage.saveJsonArray(QUERIES_FILE, array);
        } catch (Exception e) {
            logger.error("Ошибка сохранения запросов: {}", e.getMessage());
        }
    }
    
    private String makeKey(long userId, String queryId) {
        return userId + "_" + queryId;
    }
    
    /**
     * Получить запрос по ID
     */
    public Query getQuery(long userId, String queryId) {
        return cache.get(makeKey(userId, queryId));
    }
    
    /**
     * Получить все запросы пользователя
     */
    public List<Query> getUserQueries(long userId) {
        return cache.values().stream()
            .filter(q -> q.getUserId() == userId)
            .collect(Collectors.toList());
    }
    
    /**
     * Сохранить запрос
     */
    public void save(Query query) {
        String key = makeKey(query.getUserId(), query.getQueryId());
        cache.put(key, query);
        saveAll();
        logger.info("Запрос {} для пользователя {} сохранен", query.getQueryId(), query.getUserId());
    }
    
    /**
     * Удалить запрос
     */
    public void delete(long userId, String queryId) {
        String key = makeKey(userId, queryId);
        cache.remove(key);
        saveAll();
        logger.info("Запрос {} для пользователя {} удален", queryId, userId);
    }
    
    /**
     * Удалить все запросы пользователя
     */
    public void deleteUserQueries(long userId) {
        List<String> keysToRemove = cache.keySet().stream()
            .filter(key -> key.startsWith(userId + "_"))
            .collect(Collectors.toList());
        
        keysToRemove.forEach(cache::remove);
        saveAll();
        logger.info("Удалено {} запросов пользователя {}", keysToRemove.size(), userId);
    }
    
    /**
     * Проверить существование запроса
     */
    public boolean exists(long userId, String queryId) {
        return cache.containsKey(makeKey(userId, queryId));
    }

    /**
     * Получить запрос по ID (альтернативный метод)
     */
    public Query getById(long queryIdLong) {
        String queryId = String.valueOf(queryIdLong);
        // Ищем по всем пользователям
        return cache.values().stream()
            .filter(q -> q.getId().equals(queryId))
            .findFirst()
            .orElse(null);
    }

    /**
     * Получить запросы пользователя (альтернативный метод)
     */
    public List<Query> getByUserId(long userId) {
        return getUserQueries(userId);
    }

    /**
     * Получить все запросы
     */
    public List<Query> getAll() {
        return new ArrayList<>(cache.values());
    }
}

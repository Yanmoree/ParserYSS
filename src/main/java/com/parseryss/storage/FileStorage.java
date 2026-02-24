package com.parseryss.storage;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Упрощенное файловое хранилище с блокировками для многопоточного доступа
 */
public class FileStorage {
    private static final Logger logger = LoggerFactory.getLogger(FileStorage.class);
    
    private static final String DATA_DIR = "./data";
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    public FileStorage() {
        try {
            Files.createDirectories(Paths.get(DATA_DIR));
        } catch (IOException e) {
            logger.error("Ошибка создания директории данных: {}", e.getMessage());
        }
    }
    
    /**
     * Сохранить JSON объект в файл
     */
    public void saveJson(String filename, JSONObject json) {
        lock.writeLock().lock();
        try {
            Path path = Paths.get(DATA_DIR, filename);
            Files.writeString(path, json.toString(2), StandardCharsets.UTF_8);
            logger.debug("Сохранено: {}", filename);
        } catch (IOException e) {
            logger.error("Ошибка сохранения {}: {}", filename, e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Сохранить JSON массив в файл
     */
    public void saveJsonArray(String filename, JSONArray array) {
        lock.writeLock().lock();
        try {
            Path path = Paths.get(DATA_DIR, filename);
            Files.writeString(path, array.toString(2), StandardCharsets.UTF_8);
            logger.debug("Сохранено: {}", filename);
        } catch (IOException e) {
            logger.error("Ошибка сохранения {}: {}", filename, e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Загрузить JSON объект из файла
     */
    public JSONObject loadJson(String filename) {
        lock.readLock().lock();
        try {
            Path path = Paths.get(DATA_DIR, filename);
            if (!Files.exists(path)) {
                return new JSONObject();
            }
            String content = Files.readString(path, StandardCharsets.UTF_8);
            return new JSONObject(content);
        } catch (Exception e) {
            logger.error("Ошибка загрузки {}: {}", filename, e.getMessage());
            return new JSONObject();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Загрузить JSON массив из файла
     */
    public JSONArray loadJsonArray(String filename) {
        lock.readLock().lock();
        try {
            Path path = Paths.get(DATA_DIR, filename);
            if (!Files.exists(path)) {
                return new JSONArray();
            }
            String content = Files.readString(path, StandardCharsets.UTF_8);
            return new JSONArray(content);
        } catch (Exception e) {
            logger.error("Ошибка загрузки {}: {}", filename, e.getMessage());
            return new JSONArray();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Проверить существование файла
     */
    public boolean exists(String filename) {
        return Files.exists(Paths.get(DATA_DIR, filename));
    }
    
    /**
     * Удалить файл
     */
    public void delete(String filename) {
        lock.writeLock().lock();
        try {
            Path path = Paths.get(DATA_DIR, filename);
            Files.deleteIfExists(path);
            logger.debug("Удалено: {}", filename);
        } catch (IOException e) {
            logger.error("Ошибка удаления {}: {}", filename, e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Сохранить строку в файл
     */
    public void saveString(String filename, String content) {
        lock.writeLock().lock();
        try {
            Path path = Paths.get(DATA_DIR, filename);
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("Ошибка сохранения {}: {}", filename, e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Загрузить строку из файла
     */
    public String loadString(String filename) {
        lock.readLock().lock();
        try {
            Path path = Paths.get(DATA_DIR, filename);
            if (!Files.exists(path)) {
                return "";
            }
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("Ошибка загрузки {}: {}", filename, e.getMessage());
            return "";
        } finally {
            lock.readLock().unlock();
        }
    }
}

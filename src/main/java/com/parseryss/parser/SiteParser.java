package com.parseryss.parser;

import com.parseryss.model.Product;
import java.util.List;

/**
 * Интерфейс для всех парсеров
 */
public interface SiteParser {
    /**
     * Поиск товаров
     * @param query поисковый запрос
     * @param maxPages максимальное количество страниц
     * @param rowsPerPage количество товаров на странице
     * @param maxAgeMinutes максимальный возраст товара в минутах (0 = без фильтра)
     * @return список найденных товаров
     */
    List<Product> search(String query, int maxPages, int rowsPerPage, int maxAgeMinutes);
    
    /**
     * Поиск товаров с параметрами по умолчанию
     */
    List<Product> search(String query);
    
    /**
     * Получить название сайта
     */
    String getSiteName();
    
    /**
     * Поиск новых товаров с учетом состояния
     * @param userId ID пользователя
     * @param queryId ID запроса
     * @param queryText текст запроса
     * @param settings настройки пользователя
     * @return список новых товаров
     */
    List<Product> searchNewProducts(long userId, String queryId, String queryText, com.parseryss.model.UserSettings settings);
}

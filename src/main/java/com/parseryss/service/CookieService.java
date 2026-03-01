package com.parseryss.service;

import com.parseryss.config.Config;
import com.parseryss.config.CookieConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Сервис для работы с cookies через Selenium
 * Автоматически обновляет cookies для Goofish каждые 2 часа
 */
public class CookieService {
    private static final Logger logger = LoggerFactory.getLogger(CookieService.class);

    // Кэш cookies для доменов
    private static final Map<String, Map<String, String>> cookieCache = new ConcurrentHashMap<>();
    private static final Map<String, Long> cacheTimestamp = new ConcurrentHashMap<>();
    private static final long CACHE_TTL = 30 * 60 * 1000; // 30 минут

    private static long lastRefreshTime = 0;
    private static final long REFRESH_INTERVAL = 120 * 60 * 1000; // 2 часа

    private static ScheduledExecutorService scheduler;

    // Основные домены для Goofish
    private static final String PRIMARY_DOMAIN = "www.goofish.com";
    private static final String API_DOMAIN = "h5api.m.goofish.com";
    private static final String M_DOMAIN = "m.goofish.com";

    /**
     * Инициализация сервиса
     */
    public static synchronized void initialize() {
        if (scheduler != null && !scheduler.isShutdown()) {
            return;
        }

        logger.info("🍪 Инициализация CookieService...");

        // Создаем планировщик для автообновления
        scheduler = Executors.newScheduledThreadPool(1);

        // Автообновление cookies каждые 2 часа
        if (Config.getBoolean("cookie.auto.update", true)) {
            int interval = Config.getInt("cookie.update.interval.minutes", 120);
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    logger.info("🔄 Автообновление cookies...");
                    if (refreshCookies(PRIMARY_DOMAIN)) {
                        logger.info("✅ Cookies успешно обновлены");
                    } else {
                        logger.warn("⚠️ Автообновление cookies не удалось");
                    }
                } catch (Exception e) {
                    logger.error("❌ Ошибка при автообновлении cookies: {}", e.getMessage());
                }
            }, interval, interval, TimeUnit.MINUTES);

            logger.info("✅ Автообновление настроено: каждые {} минут", interval);
        }

        // Предварительная загрузка cookies (не критично если не удалось)
        try {
            logger.info("🔄 Предварительная загрузка cookies Goofish...");
            getFreshCookies(PRIMARY_DOMAIN);
            logger.info("✅ Cookies Goofish загружены");
        } catch (Exception e) {
            logger.warn("⚠️ Не удалось загрузить cookies Goofish при старте: {}", e.getMessage());
            logger.info("ℹ️ Cookies будут загружены при первом запросе или через {} минут", 
                Config.getInt("cookie.update.interval.minutes", 120));
        }

        logger.info("✅ CookieService инициализирован");
    }

    /**
     * Получение свежих cookies для домена
     */
    public static Map<String, String> getFreshCookies(String domain) {
        if (!Config.isDynamicCookiesEnabled()) {
            logger.debug("🍪 Динамические cookies отключены, используем статические");
            return getCookiesFromConfig(domain);
        }

        // Проверяем кэш
        if (cookieCache.containsKey(domain) && cacheTimestamp.containsKey(domain)) {
            long cacheAge = System.currentTimeMillis() - cacheTimestamp.get(domain);
            if (cacheAge < CACHE_TTL) {
                logger.debug("🍪 Используем кэшированные cookies для {} (возраст: {} мин)",
                        domain, cacheAge / (60 * 1000));
                return new HashMap<>(cookieCache.get(domain));
            } else {
                logger.debug("🍪 Кэш cookies для {} устарел (возраст: {} мин)",
                        domain, cacheAge / (60 * 1000));
            }
        }

        logger.info("🔄 Получение свежих cookies для {}", domain);

        // Получаем куки из конфига как fallback
        Map<String, String> configCookies = getCookiesFromConfig(domain);

        try {
            // Получаем свежие cookies через Selenium
            Map<String, String> freshCookies = SeleniumCookieFetcher.getFreshCookies();

            if (SeleniumCookieFetcher.validateCookies(freshCookies)) {
                // Сохраняем для всех доменов Goofish
                updateCookieConfigForAllDomains(freshCookies);

                // Обновляем кэш
                cookieCache.put(PRIMARY_DOMAIN, new HashMap<>(freshCookies));
                cookieCache.put(API_DOMAIN, new HashMap<>(freshCookies));
                cookieCache.put(M_DOMAIN, new HashMap<>(freshCookies));

                cacheTimestamp.put(PRIMARY_DOMAIN, System.currentTimeMillis());
                cacheTimestamp.put(API_DOMAIN, System.currentTimeMillis());
                cacheTimestamp.put(M_DOMAIN, System.currentTimeMillis());

                lastRefreshTime = System.currentTimeMillis();

                logger.info("✅ Получены свежие cookies, {} элементов", freshCookies.size());
                return freshCookies;
            } else {
                logger.warn("⚠️ Валидация свежих cookies не пройдена, используем кэшированные");
                if (!configCookies.isEmpty()) {
                    return configCookies;
                }
            }
        } catch (Exception e) {
            logger.error("❌ Ошибка получения свежих cookies: {}", e.getMessage());
            if (!configCookies.isEmpty()) {
                logger.info("🍪 Используем cookies из конфига");
                return configCookies;
            }
        }

        logger.warn("⚠️ Не удалось получить cookies, возвращаем пустые");
        return new HashMap<>();
    }

    /**
     * Принудительное обновление cookies
     */
    public static boolean refreshCookies(String domain) {
        logger.info("🔄 Принудительное обновление cookies для: {}", domain);

        try {
            Map<String, String> freshCookies = SeleniumCookieFetcher.getFreshCookies();

            if (SeleniumCookieFetcher.validateCookies(freshCookies)) {
                updateCookieConfigForAllDomains(freshCookies);

                // Обновляем кэш
                cookieCache.put(PRIMARY_DOMAIN, new HashMap<>(freshCookies));
                cookieCache.put(API_DOMAIN, new HashMap<>(freshCookies));
                cookieCache.put(M_DOMAIN, new HashMap<>(freshCookies));

                cacheTimestamp.put(PRIMARY_DOMAIN, System.currentTimeMillis());
                cacheTimestamp.put(API_DOMAIN, System.currentTimeMillis());
                cacheTimestamp.put(M_DOMAIN, System.currentTimeMillis());

                lastRefreshTime = System.currentTimeMillis();

                logger.info("✅ Cookies успешно обновлены, {} элементов", freshCookies.size());
                return true;
            } else {
                logger.error("❌ Валидация cookies не пройдена");
                return false;
            }
        } catch (Exception e) {
            logger.error("❌ Ошибка обновления cookies: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Обновление cookies через GUI (для отладки)
     */
    public static boolean refreshCookiesWithGUI(String domain) {
        logger.info("🔄 Обновление cookies через GUI для: {}", domain);

        try {
            Map<String, String> freshCookies = SeleniumCookieFetcher.getFreshCookiesWithGUI();

            if (SeleniumCookieFetcher.validateCookies(freshCookies)) {
                updateCookieConfigForAllDomains(freshCookies);
                lastRefreshTime = System.currentTimeMillis();
                logger.info("✅ Cookies обновлены через GUI");
                return true;
            } else {
                logger.error("❌ Валидация не пройдена");
                return false;
            }
        } catch (Exception e) {
            logger.error("❌ Ошибка обновления cookies через GUI: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Получение строки cookies для HTTP заголовка
     */
    public static String getCookieHeader(String domain) {
        Map<String, String> cookies = getFreshCookies(domain);
        return cookiesToHeaderString(cookies);
    }

    /**
     * Очистка кэша
     */
    public static void clearCache() {
        cookieCache.clear();
        cacheTimestamp.clear();
        lastRefreshTime = 0;
        logger.info("🧹 Кэш cookies очищен");
    }

    /**
     * Получение статистики
     */
    public static Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("lastRefreshTime", new Date(lastRefreshTime));
        stats.put("refreshIntervalMinutes", REFRESH_INTERVAL / 60000);
        stats.put("cacheTTLMinutes", CACHE_TTL / 60000);
        stats.put("cachedDomains", cookieCache.size());
        stats.put("dynamicCookiesEnabled", Config.isDynamicCookiesEnabled());

        List<String> cachedDomains = new ArrayList<>();
        for (Map.Entry<String, Long> entry : cacheTimestamp.entrySet()) {
            long age = System.currentTimeMillis() - entry.getValue();
            cachedDomains.add(String.format("%s (возраст: %d мин)",
                    entry.getKey(), age / (60 * 1000)));
        }
        stats.put("cachedDomainsInfo", cachedDomains);

        return stats;
    }

    /**
     * Завершение работы сервиса
     */
    public static void shutdown() {
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            logger.info("🛑 CookieService остановлен");
        }
    }

    /**
     * Обновление конфигурации для всех доменов Goofish
     */
    private static void updateCookieConfigForAllDomains(Map<String, String> cookies) {
        String cookieString = cookiesToHeaderString(cookies);

        // Обновляем для всех доменов
        CookieConfig.setCookiesForDomain(PRIMARY_DOMAIN, cookieString);
        CookieConfig.setCookiesForDomain(API_DOMAIN, cookieString);
        CookieConfig.setCookiesForDomain(M_DOMAIN, cookieString);

        saveCookiesToProperties(cookies);
        logger.debug("Обновлены cookies для всех доменов Goofish");
    }

    /**
     * Сохранение cookies в файл properties
     */
    private static void saveCookiesToProperties(Map<String, String> cookies) {
        try {
            Properties props = new Properties();

            String cookieString = cookiesToHeaderString(cookies);

            // Сохраняем для всех доменов
            props.setProperty("www.goofish.com.cookies", cookieString);
            props.setProperty("h5api.m.goofish.com.cookies", cookieString);
            props.setProperty("m.goofish.com.cookies", cookieString);

            try (FileOutputStream fos = new FileOutputStream("data/cookies.properties")) {
                props.store(fos, "Cookies for HTTP requests\nAuto-generated file");
                logger.info("💾 Cookies сохранены в: data/cookies.properties");
            }

        } catch (Exception e) {
            logger.error("❌ Ошибка сохранения cookies: {}", e.getMessage());
        }
    }

    /**
     * Получение cookies из конфига
     */
    private static Map<String, String> getCookiesFromConfig(String domain) {
        // Пробуем основной домен, если не найдены
        String cookieString = CookieConfig.getCookiesForDomain(domain);
        if (cookieString == null || cookieString.isEmpty()) {
            cookieString = CookieConfig.getCookiesForDomain(PRIMARY_DOMAIN);
        }

        Map<String, String> cookies = new HashMap<>();

        if (cookieString != null && !cookieString.trim().isEmpty()) {
            String[] cookiePairs = cookieString.split("; ");
            for (String pair : cookiePairs) {
                String[] parts = pair.split("=", 2);
                if (parts.length == 2) {
                    cookies.put(parts[0].trim(), parts[1].trim());
                }
            }
        }

        return cookies;
    }

    /**
     * Преобразование Map cookies в строку для заголовка
     */
    private static String cookiesToHeaderString(Map<String, String> cookies) {
        if (cookies == null || cookies.isEmpty()) {
            return "";
        }

        StringBuilder header = new StringBuilder();
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            if (header.length() > 0) {
                header.append("; ");
            }
            header.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return header.toString();
    }

    /**
     * Получение конкретного cookie
     */
    public static String getCookie(String domain, String cookieName) {
        Map<String, String> cookies = getFreshCookies(domain);
        return cookies.getOrDefault(cookieName, "");
    }

    /**
     * Проверка, доступны ли cookies
     */
    public static boolean hasValidCookies() {
        try {
            String cookieHeader = getCookieHeader(PRIMARY_DOMAIN);
            return cookieHeader != null && !cookieHeader.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
}

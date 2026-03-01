package com.parseryss.service;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.json.JSONObject;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Сервис для перехвата заголовков Mercari API через Chrome Performance Logs
 */
public class MercariHeaderService {
    private static final Logger logger = LoggerFactory.getLogger(MercariHeaderService.class);
    
    private static final String MERCARI_SEARCH_URL = "https://jp.mercari.com/search?keyword=test&sort=created_time&order=desc";
    private static final String API_URL_PATTERN = "api.mercari.jp/v2/entities:search";
    
    // Кэш заголовков
    private static Map<String, String> cachedHeaders = new ConcurrentHashMap<>();
    private static long headersTimestamp = 0;
    private static final long HEADERS_TTL = 2 * 60 * 60 * 1000; // 2 часа
    
    private static java.util.concurrent.ScheduledExecutorService scheduler;
    private static volatile boolean isInitialized = false;
    
    /**
     * Инициализация сервиса с автообновлением каждые 2 часа
     */
    public static synchronized void initialize() {
        if (isInitialized) {
            return;
        }
        
        logger.info("🍪 Инициализация MercariHeaderService...");
        
        // Создаем планировщик
        scheduler = java.util.concurrent.Executors.newScheduledThreadPool(1);
        
        // Первоначальная загрузка headers
        logger.info("🔄 Первоначальная загрузка headers Mercari...");
        refreshHeaders();
        
        // Автообновление каждые 2 часа
        scheduler.scheduleAtFixedRate(() -> {
            try {
                logger.info("🔄 Автообновление headers Mercari...");
                refreshHeaders();
                logger.info("✅ Headers Mercari успешно обновлены");
            } catch (Exception e) {
                logger.error("❌ Ошибка при автообновлении headers Mercari: {}", e.getMessage());
            }
        }, 120, 120, java.util.concurrent.TimeUnit.MINUTES);
        
        isInitialized = true;
        logger.info("✅ MercariHeaderService инициализирован (автообновление каждые 120 минут)");
    }
    
    /**
     * Получение заголовков (из кэша или свежих)
     */
    public static synchronized Map<String, String> getHeaders() {
        // Проверяем кэш
        if (!cachedHeaders.isEmpty() && System.currentTimeMillis() - headersTimestamp < HEADERS_TTL) {
            logger.debug("📋 Используем кэшированные заголовки Mercari ({} шт, возраст: {} мин)", 
                cachedHeaders.size(), (System.currentTimeMillis() - headersTimestamp) / 60000);
            return new HashMap<>(cachedHeaders);
        }
        
        logger.warn("⚠️ Headers Mercari не инициализированы или устарели, обновляем...");
        refreshHeaders();
        return new HashMap<>(cachedHeaders);
    }
    
    /**
     * Принудительное обновление заголовков
     */
    public static synchronized Map<String, String> refreshHeaders() {
        logger.info("🔄 Перехватываем свежие заголовки Mercari...");
        Map<String, String> headers = interceptHeadersWithPerformanceLogs();
        
        if (headers != null && !headers.isEmpty()) {
            cachedHeaders.clear();
            cachedHeaders.putAll(headers);
            headersTimestamp = System.currentTimeMillis();
            logger.info("✅ Заголовки Mercari обновлены ({} шт)", headers.size());
        } else {
            logger.error("❌ Не удалось получить заголовки Mercari");
        }
        
        return new HashMap<>(cachedHeaders);
    }
    
    /**
     * Очистка кэша
     */
    public static void clearCache() {
        cachedHeaders.clear();
        headersTimestamp = 0;
        logger.info("🧹 Кэш заголовков Mercari очищен");
    }
    
    /**
     * Перехват заголовков через Chrome Performance Logs
     */
    private static Map<String, String> interceptHeadersWithPerformanceLogs() {
        Map<String, String> headers = new HashMap<>();
        ChromeDriver driver = null;
        
        try {
            WebDriverManager.chromedriver().setup();
            
            // Настраиваем логирование
            LoggingPreferences logPrefs = new LoggingPreferences();
            logPrefs.enable(LogType.PERFORMANCE, Level.ALL);
            
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless=new");
            options.addArguments("--disable-gpu");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--window-size=1920,1080");
            options.addArguments("--user-agent=Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36");
            options.setCapability("goog:loggingPrefs", logPrefs);
            
            Map<String, Object> perfLogPrefs = new HashMap<>();
            perfLogPrefs.put("enableNetwork", true);
            perfLogPrefs.put("enablePage", false);
            options.setExperimentalOption("perfLoggingPrefs", perfLogPrefs);
            
            logger.info("🚀 Запуск Chrome с Performance Logs для перехвата заголовков Mercari...");
            driver = new ChromeDriver(options);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
            
            logger.info("📄 Загружаем страницу Mercari: {}", MERCARI_SEARCH_URL);
            driver.get(MERCARI_SEARCH_URL);
            
            // Ждём загрузки и API запросов
            logger.info("⏳ Ожидаем API запросы (10 секунд)...");
            Thread.sleep(10000);
            
            // Парсим логи
            logger.info("🔍 Анализируем Performance Logs...");
            List<LogEntry> logs = driver.manage().logs().get(LogType.PERFORMANCE).getAll();
            logger.info("📊 Получено {} записей логов", logs.size());
            
            // Сначала собираем requestId для POST запросов к API
            Map<String, String> apiRequestIds = new HashMap<>();
            
            for (LogEntry entry : logs) {
                try {
                    JSONObject log = new JSONObject(entry.getMessage());
                    JSONObject msg = log.optJSONObject("message");
                    if (msg == null) continue;
                    
                    String method = msg.optString("method", "");
                    if ("Network.requestWillBeSent".equals(method)) {
                        JSONObject params = msg.optJSONObject("params");
                        if (params == null) continue;
                        
                        JSONObject request = params.optJSONObject("request");
                        if (request == null) continue;
                        
                        String url = request.optString("url", "");
                        String httpMethod = request.optString("method", "");
                        String requestId = params.optString("requestId", "");
                        
                        if (url.contains(API_URL_PATTERN) && "POST".equalsIgnoreCase(httpMethod)) {
                            logger.info("🎯 Найден POST запрос к Mercari API: {}, requestId: {}", url, requestId);
                            apiRequestIds.put(requestId, url);
                            
                            // Также пробуем получить заголовки из этого события
                            JSONObject requestHeaders = request.optJSONObject("headers");
                            if (requestHeaders != null) {
                                for (String key : requestHeaders.keySet()) {
                                    String value = requestHeaders.optString(key, "");
                                    if (!key.startsWith(":") && !value.isEmpty()) {
                                        headers.put(key, value);
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // Игнорируем
                }
            }
            
            // Теперь ищем Network.requestWillBeSentExtraInfo для этих requestId
            for (LogEntry entry : logs) {
                try {
                    JSONObject log = new JSONObject(entry.getMessage());
                    JSONObject msg = log.optJSONObject("message");
                    if (msg == null) continue;
                    
                    String method = msg.optString("method", "");
                    if ("Network.requestWillBeSentExtraInfo".equals(method)) {
                        JSONObject params = msg.optJSONObject("params");
                        if (params == null) continue;
                        
                        String requestId = params.optString("requestId", "");
                        if (apiRequestIds.containsKey(requestId)) {
                            logger.info("🔑 Найдены дополнительные заголовки для requestId: {}", requestId);
                            
                            JSONObject extraHeaders = params.optJSONObject("headers");
                            if (extraHeaders != null) {
                                logger.info("📝 Extra заголовки: {}", extraHeaders.keySet());
                                for (String key : extraHeaders.keySet()) {
                                    String value = extraHeaders.optString(key, "");
                                    if (!key.startsWith(":") && !value.isEmpty()) {
                                        headers.put(key, value);
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // Игнорируем
                }
            }
            
            if (!headers.isEmpty()) {
                logger.info("✅ Перехвачено {} заголовков", headers.size());
                for (String key : headers.keySet()) {
                    if (key.equalsIgnoreCase("dpop")) {
                        logger.info("  🔑 {}: {}...", key, headers.get(key).substring(0, Math.min(50, headers.get(key).length())));
                    } else {
                        logger.info("  📋 {}: {}", key, headers.get(key));
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("❌ Ошибка перехвата заголовков: {}", e.getMessage(), e);
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                    logger.info("🔚 Chrome закрыт");
                } catch (Exception e) {
                    logger.debug("Ошибка закрытия драйвера: {}", e.getMessage());
                }
            }
        }
        
        return headers;
    }
    
    /**
     * Проверка наличия критичных заголовков
     */
    public static boolean validateHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            logger.warn("⚠️ Заголовки пусты");
            return false;
        }
        
        boolean hasDpop = headers.containsKey("dpop") || headers.containsKey("Dpop") || headers.containsKey("DPoP");
        boolean hasXPlatform = headers.containsKey("x-platform") || headers.containsKey("X-Platform");
        
        if (!hasDpop) {
            logger.warn("⚠️ Отсутствует DPoP токен");
        }
        if (!hasXPlatform) {
            logger.warn("⚠️ Отсутствует X-Platform заголовок");
        }
        
        return hasDpop && hasXPlatform;
    }
}

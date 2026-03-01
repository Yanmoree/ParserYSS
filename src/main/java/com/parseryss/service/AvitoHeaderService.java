package com.parseryss.service;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.json.JSONObject;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис для получения cookies Avito через Firefox (лучше обходит детекцию)
 */
public class AvitoHeaderService {
    private static final Logger logger = LoggerFactory.getLogger(AvitoHeaderService.class);
    
    private static final String AVITO_SEARCH_URL = "https://www.avito.ru/rossiya?q=iphone";
    
    private static Map<String, String> cachedHeaders = new ConcurrentHashMap<>();
    private static Map<String, String> cachedCookies = new ConcurrentHashMap<>();
    private static long headersTimestamp = 0;
    private static final long HEADERS_TTL = 2 * 60 * 60 * 1000; // 2 часа
    
    private static java.util.concurrent.ScheduledExecutorService scheduler;
    private static volatile boolean isInitialized = false;
    
    public static synchronized Map<String, String> getHeaders() {
        if (!cachedHeaders.isEmpty() && System.currentTimeMillis() - headersTimestamp < HEADERS_TTL) {
            logger.info("📋 Используем кэшированные заголовки Avito ({} шт, возраст: {} мин)", 
                cachedHeaders.size(), (System.currentTimeMillis() - headersTimestamp) / 60000);
            return new HashMap<>(cachedHeaders);
        }
        
        logger.info("🔄 Перехватываем свежие заголовки Avito...");
        Map<String, String> headers = interceptHeadersWithPerformanceLogs();
        
        if (headers != null && !headers.isEmpty()) {
            cachedHeaders.clear();
            cachedHeaders.putAll(headers);
            headersTimestamp = System.currentTimeMillis();
            logger.info("✅ Заголовки Avito обновлены ({} шт)", headers.size());
        }
        
        return headers;
    }
    
    /**
     * Инициализация сервиса с автообновлением каждые 2 часа
     */
    public static synchronized void initialize() {
        if (isInitialized) {
            return;
        }
        
        logger.info("🍪 Инициализация AvitoHeaderService...");
        
        // Создаем планировщик
        scheduler = java.util.concurrent.Executors.newScheduledThreadPool(1);
        
        // Первоначальная загрузка cookies
        logger.info("🔄 Первоначальная загрузка cookies Avito...");
        refreshHeaders();
        
        // Автообновление каждые 2 часа
        scheduler.scheduleAtFixedRate(() -> {
            try {
                logger.info("🔄 Автообновление cookies Avito...");
                refreshHeaders();
                logger.info("✅ Cookies Avito успешно обновлены");
            } catch (Exception e) {
                logger.error("❌ Ошибка при автообновлении cookies Avito: {}", e.getMessage());
            }
        }, 120, 120, java.util.concurrent.TimeUnit.MINUTES);
        
        isInitialized = true;
        logger.info("✅ AvitoHeaderService инициализирован (автообновление каждые 120 минут)");
    }
    
    public static synchronized Map<String, String> getCookies() {
        if (!cachedCookies.isEmpty() && System.currentTimeMillis() - headersTimestamp < HEADERS_TTL) {
            logger.debug("🍪 Используем кэшированные cookies Avito ({} шт, возраст: {} мин)", 
                cachedCookies.size(), (System.currentTimeMillis() - headersTimestamp) / 60000);
            return new HashMap<>(cachedCookies);
        }
        
        logger.warn("⚠️ Cookies Avito не инициализированы или устарели, обновляем...");
        refreshHeaders();
        return new HashMap<>(cachedCookies);
    }
    
    /**
     * Проверка наличия критичных cookies
     */
    public static boolean validateCookies(Map<String, String> cookies) {
        if (cookies == null || cookies.isEmpty()) {
            logger.warn("⚠️ Cookies пусты");
            return false;
        }
        
        // Проверяем наличие любых cookies (минимум 3)
        if (cookies.size() >= 3) {
            logger.info("✅ Валидация cookies Avito пройдена ({} cookies)", cookies.size());
            return true;
        } else {
            logger.warn("❌ Валидация cookies Avito не пройдена ({} cookies, нужно минимум 3)", cookies.size());
            return false;
        }
    }
    
    /**
     * Принудительное обновление заголовков
     */
    public static synchronized Map<String, String> refreshHeaders() {
        logger.info("🔄 Перехватываем свежие cookies Avito...");
        Map<String, String> headers = interceptHeadersWithPerformanceLogs();
        
        // Cookies уже обновлены в interceptHeadersWithPerformanceLogs
        if (!cachedCookies.isEmpty()) {
            headersTimestamp = System.currentTimeMillis();
            logger.info("✅ Cookies Avito обновлены ({} шт)", cachedCookies.size());
        } else {
            logger.error("❌ Не удалось получить cookies Avito");
        }
        
        return headers;
    }
    
    /**
     * Очистка кэша
     */
    public static void clearCache() {
        cachedHeaders.clear();
        cachedCookies.clear();
        headersTimestamp = 0;
        logger.info("🧹 Кэш заголовков Avito очищен");
    }
    
    /**
     * Получение cookies через Firefox (лучше обходит детекцию headless)
     */
    private static Map<String, String> interceptHeadersWithPerformanceLogs() {
        Map<String, String> headers = new HashMap<>();
        WebDriver driver = null;
        
        try {
            WebDriverManager.firefoxdriver().setup();
            
            FirefoxOptions options = new FirefoxOptions();
            options.addArguments("--headless");
            options.addArguments("--width=1920");
            options.addArguments("--height=1080");
            
            // Настройки для обхода детекции
            options.addPreference("general.useragent.override", 
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:121.0) Gecko/20100101 Firefox/121.0");
            options.addPreference("intl.accept_languages", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7");
            options.addPreference("dom.webdriver.enabled", false);
            options.addPreference("useAutomationExtension", false);
            
            logger.info("🚀 Запуск Firefox для получения cookies Avito...");
            driver = new FirefoxDriver(options);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(60));
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            
            logger.info("📄 Загружаем страницу поиска Avito: {}", AVITO_SEARCH_URL);
            driver.get(AVITO_SEARCH_URL);
            
            // Ждём загрузки страницы
            logger.info("⏳ Ожидаем загрузку страницы (8 секунд)...");
            Thread.sleep(8000);
            
            // Получаем cookies из браузера
            Set<Cookie> seleniumCookies = driver.manage().getCookies();
            for (Cookie cookie : seleniumCookies) {
                cachedCookies.put(cookie.getName(), cookie.getValue());
            }
            logger.info("🍪 Получено {} cookies из браузера", cachedCookies.size());
            
        } catch (Exception e) {
            logger.error("❌ Ошибка получения cookies: {}", e.getMessage());
        } finally {
            if (driver != null) {
                try { 
                    driver.quit(); 
                    logger.info("🔚 Firefox закрыт");
                } catch (Exception e) {}
            }
        }
        
        return headers;
    }
    
    public static synchronized Map<String, String> solveCaptchaAutomatically() {
        logger.info("🤖 Решение капчи через 2Captcha...");
        WebDriver driver = null;
        
        try {
            WebDriverManager.firefoxdriver().setup();
            FirefoxOptions options = new FirefoxOptions();
            options.addArguments("--headless");
            
            driver = new FirefoxDriver(options);
            driver.get(AVITO_SEARCH_URL);
            Thread.sleep(3000);
            
            String pageSource = driver.getPageSource();
            String siteKey = extractCaptchaSiteKey(pageSource);
            
            if (siteKey == null) {
                Set<Cookie> cookies = driver.manage().getCookies();
                Map<String, String> newCookies = new HashMap<>();
                for (Cookie cookie : cookies) {
                    newCookies.put(cookie.getName(), cookie.getValue());
                }
                if (!newCookies.isEmpty()) {
                    cachedCookies.clear();
                    cachedCookies.putAll(newCookies);
                    headersTimestamp = System.currentTimeMillis();
                }
                return newCookies;
            }
            
            String solution = TwoCaptchaService.solveRecaptchaV2(siteKey, AVITO_SEARCH_URL);
            if (solution != null) {
                ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(String.format(
                    "document.getElementById('g-recaptcha-response').innerHTML = '%s';", solution
                ));
                Thread.sleep(2000);
                driver.navigate().refresh();
                Thread.sleep(5000);
                
                Set<Cookie> cookies = driver.manage().getCookies();
                Map<String, String> newCookies = new HashMap<>();
                for (Cookie cookie : cookies) {
                    newCookies.put(cookie.getName(), cookie.getValue());
                }
                cachedCookies.clear();
                cachedCookies.putAll(newCookies);
                headersTimestamp = System.currentTimeMillis();
                return newCookies;
            }
        } catch (Exception e) {
            logger.error("❌ Ошибка решения капчи: {}", e.getMessage());
        } finally {
            if (driver != null) {
                try { driver.quit(); } catch (Exception e) {}
            }
        }
        return new HashMap<>();
    }
    
    private static String extractCaptchaSiteKey(String html) {
        String[] patterns = {
            "data-sitekey=\"([^\"]+)\"",
            "sitekey: '([^']+)'"
        };
        
        for (String pattern : patterns) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(html);
            if (m.find()) return m.group(1);
        }
        return null;
    }
    
    /**
     * Преобразование cookies в строку для HTTP заголовка
     */
    public static String cookiesToString(Map<String, String> cookies) {
        if (cookies == null || cookies.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }
}

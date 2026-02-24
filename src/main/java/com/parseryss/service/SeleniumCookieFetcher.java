package com.parseryss.service;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Класс для получения cookies через Selenium Firefox
 * Используется для Goofish
 */
public class SeleniumCookieFetcher {
    private static final Logger logger = LoggerFactory.getLogger(SeleniumCookieFetcher.class);
    
    private static final String GOOFISH_URL = "https://www.goofish.com";
    private static final int PAGE_LOAD_TIMEOUT = 30;
    private static final int WAIT_FOR_COOKIES = 5000;
    
    // Ключевые cookies для валидации
    private static final String[] KEY_COOKIES = {"_m_h5_tk", "_tb_token_", "cna", "cookie2", "t"};
    
    /**
     * Получение свежих cookies в headless режиме
     */
    public static Map<String, String> getFreshCookies() {
        return fetchCookies(true);
    }
    
    /**
     * Получение свежих cookies с GUI (для отладки)
     */
    public static Map<String, String> getFreshCookiesWithGUI() {
        return fetchCookies(false);
    }
    
    /**
     * Основной метод получения cookies
     */
    private static Map<String, String> fetchCookies(boolean headless) {
        WebDriver driver = null;
        Map<String, String> cookies = new HashMap<>();
        
        try {
            logger.info("🌐 Запуск Firefox для получения cookies Goofish (headless: {})", headless);
            
            // Настройка Firefox WebDriver
            WebDriverManager.firefoxdriver().setup();
            
            FirefoxOptions options = new FirefoxOptions();
            
            if (headless) {
                options.addArguments("--headless");
            }
            
            options.addArguments("--width=1920");
            options.addArguments("--height=1080");
            options.addArguments("--disable-gpu");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            
            // User-Agent
            options.addPreference("general.useragent.override", 
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            
            driver = new FirefoxDriver(options);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(PAGE_LOAD_TIMEOUT));
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            
            logger.info("🔗 Переход на: {}", GOOFISH_URL);
            driver.get(GOOFISH_URL);
            
            // Ждем загрузки страницы и установки cookies
            logger.info("⏳ Ожидание установки cookies ({} мс)...", WAIT_FOR_COOKIES);
            Thread.sleep(WAIT_FOR_COOKIES);
            
            // Получаем все cookies
            Set<Cookie> seleniumCookies = driver.manage().getCookies();
            
            for (Cookie cookie : seleniumCookies) {
                cookies.put(cookie.getName(), cookie.getValue());
            }
            
            logger.info("✅ Получено {} cookies", cookies.size());
            
            // Логируем ключевые cookies
            for (String key : KEY_COOKIES) {
                if (cookies.containsKey(key)) {
                    String value = cookies.get(key);
                    logger.debug("   {} = {}", key, 
                        value.length() > 30 ? value.substring(0, 27) + "..." : value);
                }
            }
            
        } catch (Exception e) {
            logger.error("❌ Ошибка получения cookies: {}", e.getMessage());
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                    logger.debug("🛑 Firefox закрыт");
                } catch (Exception e) {
                    logger.warn("⚠️ Ошибка при закрытии Firefox: {}", e.getMessage());
                }
            }
        }
        
        return cookies;
    }
    
    /**
     * Валидация cookies
     */
    public static boolean validateCookies(Map<String, String> cookies) {
        if (cookies == null || cookies.isEmpty()) {
            logger.warn("⚠️ Cookies пусты");
            return false;
        }
        
        int validCount = 0;
        for (String key : KEY_COOKIES) {
            if (cookies.containsKey(key) && !cookies.get(key).isEmpty()) {
                validCount++;
            }
        }
        
        // Достаточно 3 из 5 ключевых cookies
        boolean isValid = validCount >= 3;
        
        if (isValid) {
            logger.info("✅ Валидация cookies пройдена ({}/{} ключевых)", validCount, KEY_COOKIES.length);
        } else {
            logger.warn("❌ Валидация cookies не пройдена ({}/{} ключевых)", validCount, KEY_COOKIES.length);
        }
        
        return isValid;
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

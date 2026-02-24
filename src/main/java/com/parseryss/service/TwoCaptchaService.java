package com.parseryss.service;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Сервис для автоматического решения капчи через 2Captcha API
 */
public class TwoCaptchaService {
    private static final Logger logger = LoggerFactory.getLogger(TwoCaptchaService.class);
    
    // API ключ 2Captcha (нужно установить через переменную окружения или конфиг)
    private static final String API_KEY = System.getenv("TWOCAPTCHA_API_KEY");
    private static final String API_URL = "https://2captcha.com";
    
    // Таймауты
    private static final int MAX_WAIT_TIME = 180000; // 3 минуты
    private static final int POLL_INTERVAL = 5000; // 5 секунд
    
    /**
     * Решение reCAPTCHA v2
     * @param siteKey ключ сайта (можно найти в HTML странице)
     * @param pageUrl URL страницы с капчей
     * @return токен решения капчи или null при ошибке
     */
    public static String solveRecaptchaV2(String siteKey, String pageUrl) {
        if (API_KEY == null || API_KEY.isEmpty()) {
            logger.error("❌ API ключ 2Captcha не установлен! Установите переменную окружения TWOCAPTCHA_API_KEY");
            return null;
        }
        
        try {
            logger.info("🔐 Отправляем капчу на решение в 2Captcha...");
            logger.info("   SiteKey: {}", siteKey);
            logger.info("   PageURL: {}", pageUrl);
            
            // Шаг 1: Отправляем капчу на решение
            String captchaId = submitCaptcha(siteKey, pageUrl);
            if (captchaId == null) {
                logger.error("❌ Не удалось отправить капчу на решение");
                return null;
            }
            
            logger.info("✅ Капча отправлена, ID: {}", captchaId);
            logger.info("⏳ Ожидаем решение (это может занять до 3 минут)...");
            
            // Шаг 2: Ждем решение
            String solution = waitForSolution(captchaId);
            if (solution == null) {
                logger.error("❌ Не удалось получить решение капчи");
                return null;
            }
            
            logger.info("✅ Капча решена успешно!");
            return solution;
            
        } catch (Exception e) {
            logger.error("❌ Ошибка при решении капчи: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Решение hCaptcha
     * @param siteKey ключ сайта
     * @param pageUrl URL страницы с капчей
     * @return токен решения капчи или null при ошибке
     */
    public static String solveHCaptcha(String siteKey, String pageUrl) {
        if (API_KEY == null || API_KEY.isEmpty()) {
            logger.error("❌ API ключ 2Captcha не установлен!");
            return null;
        }
        
        try {
            logger.info("🔐 Отправляем hCaptcha на решение в 2Captcha...");
            
            String captchaId = submitHCaptcha(siteKey, pageUrl);
            if (captchaId == null) {
                return null;
            }
            
            logger.info("✅ hCaptcha отправлена, ID: {}", captchaId);
            logger.info("⏳ Ожидаем решение...");
            
            String solution = waitForSolution(captchaId);
            if (solution != null) {
                logger.info("✅ hCaptcha решена успешно!");
            }
            
            return solution;
            
        } catch (Exception e) {
            logger.error("❌ Ошибка при решении hCaptcha: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Отправка reCAPTCHA v2 на решение
     */
    private static String submitCaptcha(String siteKey, String pageUrl) {
        try {
            String urlStr = String.format("%s/in.php?key=%s&method=userrecaptcha&googlekey=%s&pageurl=%s&json=1",
                API_URL,
                URLEncoder.encode(API_KEY, StandardCharsets.UTF_8),
                URLEncoder.encode(siteKey, StandardCharsets.UTF_8),
                URLEncoder.encode(pageUrl, StandardCharsets.UTF_8)
            );
            
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);
            
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                JSONObject json = new JSONObject(response.toString());
                int status = json.optInt("status", 0);
                
                if (status == 1) {
                    return json.optString("request", null);
                } else {
                    String error = json.optString("request", "Unknown error");
                    logger.error("❌ Ошибка 2Captcha: {}", error);
                    return null;
                }
            } else {
                logger.error("❌ HTTP ошибка при отправке капчи: {}", responseCode);
                return null;
            }
            
        } catch (Exception e) {
            logger.error("❌ Ошибка при отправке капчи: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Отправка hCaptcha на решение
     */
    private static String submitHCaptcha(String siteKey, String pageUrl) {
        try {
            String urlStr = String.format("%s/in.php?key=%s&method=hcaptcha&sitekey=%s&pageurl=%s&json=1",
                API_URL,
                URLEncoder.encode(API_KEY, StandardCharsets.UTF_8),
                URLEncoder.encode(siteKey, StandardCharsets.UTF_8),
                URLEncoder.encode(pageUrl, StandardCharsets.UTF_8)
            );
            
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);
            
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                JSONObject json = new JSONObject(response.toString());
                int status = json.optInt("status", 0);
                
                if (status == 1) {
                    return json.optString("request", null);
                } else {
                    String error = json.optString("request", "Unknown error");
                    logger.error("❌ Ошибка 2Captcha: {}", error);
                    return null;
                }
            } else {
                logger.error("❌ HTTP ошибка при отправке hCaptcha: {}", responseCode);
                return null;
            }
            
        } catch (Exception e) {
            logger.error("❌ Ошибка при отправке hCaptcha: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Ожидание решения капчи
     */
    private static String waitForSolution(String captchaId) {
        long startTime = System.currentTimeMillis();
        
        while (System.currentTimeMillis() - startTime < MAX_WAIT_TIME) {
            try {
                Thread.sleep(POLL_INTERVAL);
                
                String urlStr = String.format("%s/res.php?key=%s&action=get&id=%s&json=1",
                    API_URL,
                    URLEncoder.encode(API_KEY, StandardCharsets.UTF_8),
                    URLEncoder.encode(captchaId, StandardCharsets.UTF_8)
                );
                
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(30000);
                
                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    
                    JSONObject json = new JSONObject(response.toString());
                    int status = json.optInt("status", 0);
                    
                    if (status == 1) {
                        // Капча решена
                        return json.optString("request", null);
                    } else {
                        String request = json.optString("request", "");
                        if ("CAPCHA_NOT_READY".equals(request)) {
                            // Капча еще не готова, продолжаем ждать
                            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                            logger.info("⏳ Ожидание... ({} сек)", elapsed);
                            continue;
                        } else {
                            // Ошибка
                            logger.error("❌ Ошибка получения решения: {}", request);
                            return null;
                        }
                    }
                }
                
            } catch (Exception e) {
                logger.error("❌ Ошибка при проверке статуса: {}", e.getMessage());
            }
        }
        
        logger.error("❌ Превышено время ожидания решения капчи");
        return null;
    }
    
    /**
     * Проверка баланса 2Captcha
     */
    public static double getBalance() {
        if (API_KEY == null || API_KEY.isEmpty()) {
            logger.error("❌ API ключ 2Captcha не установлен!");
            return -1;
        }
        
        try {
            String urlStr = String.format("%s/res.php?key=%s&action=getbalance&json=1",
                API_URL,
                URLEncoder.encode(API_KEY, StandardCharsets.UTF_8)
            );
            
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);
            
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                JSONObject json = new JSONObject(response.toString());
                int status = json.optInt("status", 0);
                
                if (status == 1) {
                    double balance = json.optDouble("request", 0);
                    logger.info("💰 Баланс 2Captcha: ${}", balance);
                    return balance;
                } else {
                    logger.error("❌ Ошибка получения баланса: {}", json.optString("request", "Unknown"));
                    return -1;
                }
            }
            
        } catch (Exception e) {
            logger.error("❌ Ошибка при проверке баланса: {}", e.getMessage());
        }
        
        return -1;
    }
}

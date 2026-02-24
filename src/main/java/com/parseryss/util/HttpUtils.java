package com.parseryss.util;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Упрощенные утилиты для работы с HTTP запросами
 */
public class HttpUtils {
    private static final Logger logger = LoggerFactory.getLogger(HttpUtils.class);
    private static final Random random = new Random();

    // Конфигурация HTTP клиента
    private static final RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(15000)
            .setSocketTimeout(15000)
            .setConnectionRequestTimeout(5000)
            .setRedirectsEnabled(true)
            .setCookieSpec(CookieSpecs.STANDARD)
            .build();

    // Пул HTTP клиентов
    private static volatile CloseableHttpClient httpClient = null;

    /**
     * Получение HTTP клиента
     */
    private static synchronized CloseableHttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = HttpClients.custom()
                    .setDefaultRequestConfig(requestConfig)
                    .setRedirectStrategy(new LaxRedirectStrategy())
                    .setUserAgent(getRandomUserAgent())
                    .setMaxConnTotal(100)
                    .setMaxConnPerRoute(20)
                    .disableCookieManagement()
                    .build();
            logger.debug("HTTP client initialized");
        }
        return httpClient;
    }

    /**
     * Отправка GET запроса
     */
    public static String sendGetRequest(String url) throws Exception {
        return sendGetRequest(url, getDefaultUserAgent());
    }

    /**
     * Отправка GET запроса с указанием User-Agent
     */
    public static String sendGetRequest(String url, String userAgent) throws Exception {
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("URL cannot be empty");
        }

        logger.debug("Sending GET request to: {}", url);

        HttpGet request = new HttpGet(url);
        request.setHeader(HttpHeaders.USER_AGENT, userAgent != null ? userAgent : getDefaultUserAgent());
        request.setHeader(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        request.setHeader(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9,ru;q=0.8");
        request.setHeader(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate, br");
        request.setHeader(HttpHeaders.CONNECTION, "keep-alive");

        try (CloseableHttpResponse response = getHttpClient().execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            logger.debug("Response status: {} for URL: {}", statusCode, url);

            if (statusCode == HttpStatus.SC_OK) {
                String content = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                logger.trace("Response content length: {} chars for URL: {}", content.length(), url);
                return content;
            } else {
                logger.warn("HTTP error {} for URL: {}", statusCode, url);
                throw new Exception("HTTP error: " + statusCode);
            }
        } catch (Exception e) {
            logger.error("Error sending request to {}: {}", url, e.getMessage());
            throw e;
        }
    }
    
    /**
     * Отправка GET запроса с пользовательскими заголовками
     */
    public static String sendGetRequest(String url, Map<String, String> headers) {
        if (url == null || url.isEmpty()) {
            logger.error("URL cannot be empty");
            return null;
        }

        logger.debug("Sending GET request to: {}", url);

        HttpGet request = new HttpGet(url);
        
        // Добавляем заголовки
        if (headers != null && !headers.isEmpty()) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                request.setHeader(entry.getKey(), entry.getValue());
            }
        } else {
            // Заголовки по умолчанию
            request.setHeader(HttpHeaders.USER_AGENT, getDefaultUserAgent());
            request.setHeader(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        }

        try (CloseableHttpResponse response = getHttpClient().execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            logger.debug("Response status: {} for URL: {}", statusCode, url);

            if (statusCode == HttpStatus.SC_OK) {
                String content = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                logger.trace("Response content length: {} chars for URL: {}", content.length(), url);
                return content;
            } else {
                logger.warn("HTTP error {} for URL: {}", statusCode, url);
                return null;
            }
        } catch (Exception e) {
            logger.error("Error sending request to {}: {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * Отправка POST запроса с дополнительными заголовками
     */
    public static String sendPostRequest(String url, String jsonBody, Map<String, String> headers) throws Exception {
        logger.debug("Sending POST request to: {}", url);

        HttpPost request = new HttpPost(url);
        
        // Сначала добавляем дополнительные заголовки (если есть)
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                request.setHeader(entry.getKey(), entry.getValue());
                logger.debug("Added header: {} = {}", entry.getKey(), 
                        entry.getValue().length() > 50 ? entry.getValue().substring(0, 50) + "..." : entry.getValue());
            }
        }
        
        // Затем устанавливаем стандартные заголовки ТОЛЬКО если они не были установлены выше
        if (headers == null || !headers.containsKey(HttpHeaders.USER_AGENT)) {
            request.setHeader(HttpHeaders.USER_AGENT, getDefaultUserAgent());
        }
        if (headers == null || !headers.containsKey(HttpHeaders.CONTENT_TYPE)) {
            request.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        }
        if (headers == null || !headers.containsKey(HttpHeaders.ACCEPT)) {
            request.setHeader(HttpHeaders.ACCEPT, "application/json");
        }
        if (headers == null || !headers.containsKey(HttpHeaders.ACCEPT_ENCODING)) {
            request.setHeader(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate, br");
        }

        if (jsonBody != null && !jsonBody.isEmpty()) {
            request.setEntity(new StringEntity(jsonBody, StandardCharsets.UTF_8));
        }

        try (CloseableHttpResponse response = getHttpClient().execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == HttpStatus.SC_OK) {
                String content = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                logger.debug("POST response length: {} chars", content.length());
                return content;
            } else {
                // Логируем тело ответа при ошибке для отладки
                String errorBody = "";
                try {
                    errorBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                    logger.error("HTTP POST error {} response body: {}", statusCode, 
                            errorBody.length() > 500 ? errorBody.substring(0, 500) + "..." : errorBody);
                } catch (Exception e) {
                    logger.debug("Could not read error response body");
                }
                throw new Exception("HTTP POST error: " + statusCode + (errorBody.isEmpty() ? "" : " - " + errorBody));
            }
        }
    }

    /**
     * Извлечение домена из URL
     */
    public static String extractDomain(String url) {
        try {
            URL urlObj = new URL(url);
            return urlObj.getHost();
        } catch (Exception e) {
            logger.warn("Failed to extract domain from URL: {}", url);
            return "";
        }
    }

    /**
     * Получение стандартного User-Agent
     */
    public static String getDefaultUserAgent() {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    }

    /**
     * Получение случайного User-Agent
     */
    public static String getRandomUserAgent() {
        String[] userAgents = {
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/121.0",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        };

        return userAgents[random.nextInt(userAgents.length)];
    }

    /**
     * Кодирование URL параметров
     */
    public static String encodeUrl(String value) {
        if (value == null) {
            return "";
        }
        try {
            return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
        } catch (Exception e) {
            logger.error("Error encoding URL value: {}", e.getMessage());
            return value;
        }
    }

    /**
     * Декодирование URL параметров
     */
    public static String decodeUrl(String value) {
        if (value == null) {
            return "";
        }
        try {
            return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8.toString());
        } catch (Exception e) {
            logger.error("Error decoding URL value: {}", e.getMessage());
            return value;
        }
    }

    /**
     * Создание случайной задержки
     */
    public static void randomDelay(int minMs, int maxMs) {
        try {
            int delay = minMs + random.nextInt(maxMs - minMs);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Delay interrupted");
        }
    }
    
    /**
     * Построить URL с параметрами
     */
    public static String buildUrlWithParams(String baseUrl, Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return baseUrl;
        }
        
        String queryString = params.entrySet().stream()
            .map(entry -> {
                try {
                    return URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8.toString()) + 
                           "=" + 
                           URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.toString());
                } catch (Exception e) {
                    logger.error("Error encoding parameter: {}", e.getMessage());
                    return entry.getKey() + "=" + entry.getValue();
                }
            })
            .collect(Collectors.joining("&"));
        
        return baseUrl + (baseUrl.contains("?") ? "&" : "?") + queryString;
    }
}

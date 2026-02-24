package com.parseryss.parser;

import com.parseryss.model.Product;
import com.parseryss.service.AvitoHeaderService;
import com.parseryss.storage.ParserStateRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Парсер Avito через API с перехватом cookies через Chrome
 */
public class AvitoApiParser extends BaseParser {
    private static final Logger logger = LoggerFactory.getLogger(AvitoApiParser.class);
    
    private static final String API_URL = "https://www.avito.ru/web/1/js/items";
    private static final String DEFAULT_LOCATION_ID = "625650";
    private static final String DEFAULT_CATEGORY_ID = "5";
    
    // Примечание: X-Forwarded-For не работает без реального прокси
    // Для обхода блокировки Avito нужен реальный прокси-сервер
    
    private String locationId = DEFAULT_LOCATION_ID;
    private String categoryId = DEFAULT_CATEGORY_ID;
    private ParserStateRepository stateRepository;
    
    public AvitoApiParser(ParserStateRepository stateRepository) {
        super("avito", "https://www.avito.ru");
        this.stateRepository = stateRepository;
    }
    
    public void setLocationId(String locationId) {
        this.locationId = locationId;
    }
    
    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }
    
    @Override
    protected String buildSearchUrl(String query, int page, int rows) {
        return API_URL;
    }
    
    @Override
    public List<Product> search(String query, int maxPages, int rowsPerPage, int maxAgeMinutes) {
        List<Product> products = new ArrayList<>();
        
        try {
            Map<String, String> cookies = AvitoHeaderService.getCookies();
            
            if (cookies == null || cookies.isEmpty()) {
                logger.error("❌ Не удалось получить cookies");
                return products;
            }
            
            if (!AvitoHeaderService.validateCookies(cookies)) {
                logger.warn("⚠️ Cookies невалидны, пробуем обновить...");
                AvitoHeaderService.refreshHeaders();
                cookies = AvitoHeaderService.getCookies();
                
                if (cookies == null || cookies.isEmpty() || !AvitoHeaderService.validateCookies(cookies)) {
                    logger.error("❌ Не удалось получить валидные cookies");
                    return products;
                }
            }
            
            logger.info("🔍 Поиск Avito: '{}' (сортировка: новые первые)", query);
            
            // Парсим только первую страницу с сортировкой по дате (новые первые)
            String response = sendApiRequest(query, cookies, 1, rowsPerPage);
            
            if (response == null) {
                logger.warn("⚠️ Пустой ответ от API");
            } else {
                List<Product> pageProducts = parseApiResponse(response, query);
                
                if (pageProducts.isEmpty()) {
                    logger.info("📄 Товаров не найдено");
                } else {
                    products.addAll(pageProducts);
                    logger.info("📄 Найдено {} товаров", pageProducts.size());
                }
            }
            
            logger.info("✅ Всего найдено {} товаров для '{}'", products.size(), query);
            
        } catch (Exception e) {
            logger.error("❌ Ошибка поиска: {}", e.getMessage(), e);
        }
        
        return products;
    }
    
    private String sendApiRequest(String query, Map<String, String> cookies, int page, int pageSize) {
        try {
            StringBuilder urlBuilder = new StringBuilder(API_URL);
            urlBuilder.append("?")
                .append("categoryId=").append(categoryId)
                .append("&locationId=").append(locationId)
                .append("&name=").append(URLEncoder.encode(query, StandardCharsets.UTF_8.toString()))
                .append("&geoCoords=").append(URLEncoder.encode("59.127443,37.906902", StandardCharsets.UTF_8.toString()))
                .append("&cd=1")
                .append("&s=104")
                .append("&verticalCategoryId=").append("4")
                .append("&localPriority=0")
                .append("&updateListOnly=true");
            
            if (page > 1) {
                urlBuilder.append("&page=").append(page);
            }
            
            URL url = new URL(urlBuilder.toString());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            
            // Устанавливаем все необходимые заголовки как в реальном браузере
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Accept-Language", "ru,en;q=0.9");
            conn.setRequestProperty("Accept-Encoding", "gzip, deflate, br");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 YaBrowser/25.12.0.0 Safari/537.36");
            conn.setRequestProperty("sec-ch-ua", "\"Chromium\";v=\"142\", \"YaBrowser\";v=\"25.12\", \"Not_A Brand\";v=\"99\"");
            conn.setRequestProperty("sec-ch-ua-mobile", "?0");
            conn.setRequestProperty("sec-ch-ua-platform", "\"macOS\"");
            conn.setRequestProperty("sec-fetch-dest", "empty");
            conn.setRequestProperty("sec-fetch-mode", "cors");
            conn.setRequestProperty("sec-fetch-site", "same-origin");
            conn.setRequestProperty("x-requested-with", "XMLHttpRequest");
            conn.setRequestProperty("x-source", "client-browser");
            conn.setRequestProperty("priority", "u=1, i");
            conn.setRequestProperty("Referer", "https://www.avito.ru/cherepovets/lichnye_veschi?cd=1&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8.toString()));
            
            String cookieHeader = AvitoHeaderService.cookiesToString(cookies);
            if (!cookieHeader.isEmpty()) {
                conn.setRequestProperty("Cookie", cookieHeader);
                logger.debug("🍪 Установлены cookies ({} символов)", cookieHeader.length());
            }
            
            int code = conn.getResponseCode();
            logger.debug("📡 API ответ: {}", code);
            
            if (code == 200) {
                java.io.InputStream is = conn.getInputStream();
                if ("gzip".equalsIgnoreCase(conn.getContentEncoding())) {
                    is = new GZIPInputStream(is);
                }
                
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                }
                return sb.toString();
            } else if (code == 403 || code == 429) {
                java.io.InputStream errorStream = conn.getErrorStream();
                if (errorStream != null) {
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader r = new BufferedReader(new InputStreamReader(errorStream, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = r.readLine()) != null) sb.append(line);
                    }
                    String errorBody = sb.toString();
                    logger.error("❌ API ошибка {}: {}", code, errorBody);
                    
                    if (errorBody.toLowerCase().contains("captcha")) {
                        logger.warn("🤖 Обнаружена капча, запускаем авторешение...");
                        Map<String, String> solvedCookies = AvitoHeaderService.solveCaptchaAutomatically();
                        if (solvedCookies != null && !solvedCookies.isEmpty()) {
                            logger.info("✅ Капча решена, повторяем запрос...");
                            return sendApiRequest(query, AvitoHeaderService.getCookies(), page, pageSize);
                        }
                    }
                }
                
                AvitoHeaderService.clearCache();
            }
            
        } catch (Exception e) {
            logger.error("❌ Ошибка запроса: {}", e.getMessage());
        }
        
        return null;
    }
    
    private List<Product> parseApiResponse(String response, String query) {
        List<Product> products = new ArrayList<>();
        
        try {
            JSONObject json = new JSONObject(response);
            
            // Проверяем структуру catalog (как в оригинале)
            JSONArray items = null;
            
            if (json.has("catalog")) {
                JSONObject catalog = json.getJSONObject("catalog");
                if (catalog.has("items")) {
                    items = catalog.getJSONArray("items");
                    logger.info("✅ Найден массив items в catalog! Количество: {}", items.length());
                }
            }
            
            // Если не нашли в catalog, пробуем на верхнем уровне
            if (items == null) {
                items = json.optJSONArray("items");
                if (items != null) {
                    logger.info("✅ Найден массив items на верхнем уровне! Количество: {}", items.length());
                }
            }
            
            // Пробуем другие возможные ключи
            if (items == null) {
                String[] possibleKeys = {"result", "data", "results", "list"};
                for (String key : possibleKeys) {
                    if (json.has(key)) {
                        Object obj = json.get(key);
                        if (obj instanceof JSONArray) {
                            items = (JSONArray) obj;
                            logger.info("✅ Найден массив в ключе '{}': {}", key, items.length());
                            break;
                        } else if (obj instanceof JSONObject) {
                            JSONObject inner = (JSONObject) obj;
                            items = inner.optJSONArray("items");
                            if (items != null) {
                                logger.info("✅ Найден массив items в '{}': {}", key, items.length());
                                break;
                            }
                        }
                    }
                }
            }
            
            if (items == null || items.length() == 0) {
                logger.warn("⚠️ Массив items пуст или отсутствует. Ключи JSON: {}", json.keySet());
                return products;
            }
            
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item != null) {
                    Product p = parseItem(item, query);
                    if (p != null) products.add(p);
                }
            }
            
        } catch (Exception e) {
            logger.error("❌ Ошибка парсинга ответа: {}", e.getMessage());
        }
        
        return products;
    }
    
    private Product parseItem(JSONObject item, String query) {
        try {
            // ID товара - пробуем разные варианты
            long id = item.optLong("id", 0);
            if (id == 0) {
                String idStr = item.optString("id", "");
                if (!idStr.isEmpty()) {
                    try { id = Long.parseLong(idStr); } catch (Exception ignored) {}
                }
            }
            if (id == 0) return null;
            
            Product p = new Product();
            p.setId(String.valueOf(id));
            p.setPlatform("avito");
            p.setQuery(query);
            
            // URL товара
            String urlPath = item.optString("urlPath", "");
            if (!urlPath.isEmpty()) {
                p.setUrl("https://www.avito.ru" + urlPath);
            } else {
                p.setUrl("https://www.avito.ru/item/" + id);
            }
            
            p.setTitle(item.optString("title", "Avito Item"));
            
            // Цена - из объекта priceDetailed (как в оригинале)
            double priceValue = 0;
            JSONObject priceDetailed = item.optJSONObject("priceDetailed");
            if (priceDetailed != null && priceDetailed.optBoolean("hasValue", false)) {
                priceValue = priceDetailed.optDouble("value", -1);
                if (priceValue <= 0) {
                    String priceString = priceDetailed.optString("string", "0");
                    try {
                        priceString = priceString.replaceAll("\\s+", "").replaceAll("[^0-9]", "");
                        priceValue = Double.parseDouble(priceString);
                    } catch (NumberFormatException ignored) {}
                }
            }
            
            // Альтернативный путь к цене
            if (priceValue <= 0) {
                Object price = item.opt("price");
                if (price instanceof Number) {
                    priceValue = ((Number) price).doubleValue();
                } else if (price instanceof String) {
                    try {
                        priceValue = Double.parseDouble(((String) price).replaceAll("[^0-9]", ""));
                    } catch (NumberFormatException ignored) {}
                } else if (price instanceof JSONObject) {
                    priceValue = ((JSONObject) price).optDouble("value", 0);
                }
            }
            p.setPrice(priceValue);
            
            // Изображения - структура: массив объектов с размерами как ключами
            JSONArray images = item.optJSONArray("images");
            if (images != null && images.length() > 0) {
                for (int i = 0; i < Math.min(images.length(), 5); i++) {
                    JSONObject img = images.optJSONObject(i);
                    if (img != null) {
                        // Пробуем разные размеры (от большего к меньшему)
                        String imgUrl = img.optString("1232x924", 
                                        img.optString("864x864", 
                                        img.optString("636x636",
                                        img.optString("432x324",
                                        img.optString("236x236", 
                                        img.optString("640x480",
                                        img.optString("url", "")))))));
                        if (!imgUrl.isEmpty()) {
                            p.addImage(imgUrl);
                        }
                    }
                }
            }
            
            // Время публикации - из sortTimeStamp (как в оригинале)
            long sortTimeStamp = item.optLong("sortTimeStamp", 0);
            if (sortTimeStamp > 0) {
                // sortTimeStamp уже в миллисекундах
                p.setPublishTime(sortTimeStamp);
                
                long ageMs = System.currentTimeMillis() - sortTimeStamp;
                int ageMinutes = (int) (ageMs / (1000 * 60));
                p.setAgeMinutes(Math.max(0, ageMinutes));
            } else {
                // Fallback на time
                long created = item.optLong("time", 0);
                if (created > 0) {
                    if (created < 10000000000L) created *= 1000;
                    p.setPublishTime(created);
                    
                    long ageMs = System.currentTimeMillis() - created;
                    int ageMinutes = (int) (ageMs / (1000 * 60));
                    p.setAgeMinutes(Math.max(0, ageMinutes));
                }
            }
            
            // Локация - пробуем разные поля
            String locationName = "";
            
            // Сначала пробуем addressDetailed (как в оригинале)
            JSONObject addressDetailed = item.optJSONObject("addressDetailed");
            if (addressDetailed != null) {
                locationName = addressDetailed.optString("name", "");
            }
            
            // Если не нашли, пробуем location
            if (locationName.isEmpty()) {
                JSONObject location = item.optJSONObject("location");
                if (location != null) {
                    locationName = location.optString("name", "");
                }
            }
            
            // Пробуем geo
            if (locationName.isEmpty()) {
                JSONObject geo = item.optJSONObject("geo");
                if (geo != null) {
                    JSONArray geoReferences = geo.optJSONArray("geoReferences");
                    if (geoReferences != null && geoReferences.length() > 0) {
                        JSONObject firstGeo = geoReferences.optJSONObject(0);
                        if (firstGeo != null) {
                            locationName = firstGeo.optString("content", "");
                        }
                    }
                }
            }
            
            if (locationName.isEmpty()) {
                locationName = "Россия";
            }
            
            p.setLocation(locationName);
            
            JSONObject category = item.optJSONObject("category");
            if (category != null) {
                p.setCategory(category.optString("name", ""));
            }
            
            JSONObject seller = item.optJSONObject("seller");
            if (seller != null) {
                String sellerName = seller.optString("name", "");
                if (!sellerName.isEmpty()) {
                    p.setSeller(sellerName);
                }
            }
            
            return p;
            
        } catch (Exception e) {
            logger.debug("Ошибка парсинга товара: {}", e.getMessage());
            return null;
        }
    }
    
    @Override
    public List<Product> searchNewProducts(long userId, String queryId, String queryText, com.parseryss.model.UserSettings settings) {
        logger.info("🔍 Поиск НОВЫХ товаров Avito: query='{}', userId={}, queryId={}", queryText, userId, queryId);
        
        int maxPages = settings.getMaxPages();
        List<Product> all = search(queryText, maxPages, 50, 0);
        if (all.isEmpty()) {
            logger.warn("⚠️ Товары не найдены для '{}'", queryText);
            return all;
        }
        
        // Получаем состояние из репозитория
        long lastTime = stateRepository.getLastPublishTime(userId, queryId, "avito");
        String lastId = stateRepository.getLastProductId(userId, queryId, "avito");
        boolean isFirstRun = (lastTime == 0);
        
        logger.info("📊 Последнее время: {}, ID: {} (первый запуск: {})", 
            lastTime > 0 ? new Date(lastTime) : "null", lastId != null ? lastId : "null", isFirstRun);
        
        // Сортируем по времени (новые первые)
        all.sort((a, b) -> {
            Long t1 = a.getPublishTime() != null ? a.getPublishTime() : 0L;
            Long t2 = b.getPublishTime() != null ? b.getPublishTime() : 0L;
            return t2.compareTo(t1);
        });
        
        // Находим самый свежий товар
        Long newestTime = null;
        String newestId = null;
        for (Product p : all) {
            if (p.getPublishTime() != null && p.getPublishTime() > 0) {
                if (newestTime == null || p.getPublishTime() > newestTime) {
                    newestTime = p.getPublishTime();
                    newestId = p.getId();
                }
            }
        }
        
        // Если первый запуск - отправляем самый новый товар и сохраняем его состояние
        if (isFirstRun) {
            if (newestTime != null && newestId != null) {
                stateRepository.updateState(userId, queryId, "avito", newestTime, newestId);
                logger.info("📌 Первый запуск - отправляем и сохраняем: {}, ID: {}", new Date(newestTime), newestId);
                // Находим самый новый товар
                for (Product p : all) {
                    if (p.getPublishTime() != null && p.getPublishTime().equals(newestTime) && p.getId().equals(newestId)) {
                        return Collections.singletonList(p);
                    }
                }
            }
            return Collections.emptyList();
        }
        
        // Фильтруем новые товары
        List<Product> newProducts = new ArrayList<>();
        for (Product p : all) {
            if (p.getPublishTime() != null && p.getPublishTime() > lastTime) {
                newProducts.add(p);
            } else if (p.getPublishTime() != null && p.getPublishTime().equals(lastTime)) {
                // Если время совпадает, проверяем ID
                if (lastId != null && !p.getId().equals(lastId)) {
                    newProducts.add(p);
                }
            }
        }
        
        // Обновляем время и ID
        if (newestTime != null && newestId != null && newestTime >= lastTime) {
            stateRepository.updateState(userId, queryId, "avito", newestTime, newestId);
            logger.info("📌 Обновлено: {}, ID: {}", new Date(newestTime), newestId);
        }
        
        logger.info("📦 Найдено {} новых товаров для '{}'", newProducts.size(), queryText);
        return newProducts;
    }
    
    public void refreshCookies() {
        AvitoHeaderService.refreshHeaders();
    }
    
    @Override
    protected String executeSearchRequest(String url, String query, int page, int rows) { 
        return null; 
    }
    
    @Override
    protected List<Product> parseResponse(String response, String query) { 
        return new ArrayList<>(); 
    }
    
    @Override
    protected int getRequestDelay() { 
        return 5000 + new Random().nextInt(3000);
    }
    
    @Override
    protected boolean shouldStopOnError(Exception e) { 
        String msg = e.getMessage();
        return msg != null && (msg.contains("403") || msg.contains("429") || msg.contains("blocked"));
    }
}

package com.parseryss.parser;

import com.parseryss.model.Product;
import com.parseryss.service.MercariHeaderService;
import com.parseryss.storage.ParserStateRepository;
import com.parseryss.util.HttpUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Парсер Mercari через API с перехватом заголовков через MercariHeaderService
 */
public class MercariParser extends BaseParser {
    private static final Logger logger = LoggerFactory.getLogger(MercariParser.class);
    
    private static final String API_URL = "https://api.mercari.jp/v2/entities:search";
    private final ParserStateRepository stateRepository;
    
    public MercariParser(ParserStateRepository stateRepository) {
        super("mercari", "https://jp.mercari.com");
        this.stateRepository = stateRepository;
    }
    
    public String getPlatformName() {
        return "mercari";
    }
    
    @Override
    protected String buildSearchUrl(String query, int page, int rows) {
        // Mercari uses POST API, so URL is constant
        return API_URL;
    }
    
    @Override
    protected List<Product> parseResponse(String response, String query) {
        List<Product> products = new ArrayList<>();
        try {
            JSONObject json = new JSONObject(response);
            JSONArray items = json.optJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    Product p = parseItem(items.getJSONObject(i), query);
                    if (p != null) {
                        products.add(p);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Ошибка парсинга ответа Mercari: {}", e.getMessage());
        }
        return products;
    }
    
    public List<Product> search(String query, int maxPages, int pageSize) {
        List<Product> products = new ArrayList<>();
        
        try {
            Map<String, String> headers = MercariHeaderService.getHeaders();
            
            if (headers == null || headers.isEmpty()) {
                logger.error("❌ Не удалось получить заголовки Mercari");
                return products;
            }
            
            if (!MercariHeaderService.validateHeaders(headers)) {
                logger.warn("⚠️ Заголовки невалидны, обновляем...");
                headers = MercariHeaderService.refreshHeaders();
                
                if (headers == null || headers.isEmpty() || !MercariHeaderService.validateHeaders(headers)) {
                    logger.error("❌ Не удалось получить валидные заголовки");
                    return products;
                }
            }
            
            logger.info("🔍 Поиск Mercari: '{}' (сортировка: новые первые)", query);
            
            // Парсим только первую страницу с сортировкой по дате
            String response = sendApiRequest(query, headers, pageSize, "");
            
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
            logger.error("❌ Ошибка поиска Mercari: {}", e.getMessage(), e);
        }
        
        return products;
    }
    
    private String sendApiRequest(String query, Map<String, String> headers, int pageSize, String pageToken) {
        try {
            JSONObject body = new JSONObject();
            body.put("userId", "");
            body.put("config", new JSONObject().put("responseToggles", new JSONArray().put("QUERY_SUGGESTION_WEB_1")));
            body.put("indexRouting", "INDEX_ROUTING_UNSPECIFIED");
            body.put("laplaceDeviceUuid", UUID.randomUUID().toString());
            body.put("pageSize", pageSize);
            body.put("pageToken", pageToken);
            body.put("searchCondition", new JSONObject()
                .put("keyword", query)
                .put("excludeKeyword", "")
                .put("sort", "SORT_CREATED_TIME")
                .put("order", "ORDER_DESC"));
            body.put("searchSessionId", UUID.randomUUID().toString().replace("-", ""));
            body.put("serviceFrom", "suruga");
            body.put("source", "BaseSerp");
            body.put("thumbnailTypes", new JSONArray());
            body.put("useDynamicAttribute", true);
            body.put("withAuction", true);
            body.put("withItemBrand", true);
            body.put("withItemPromotions", true);
            body.put("withItemSize", false);
            body.put("withItemSizes", true);
            body.put("withOfferPricePromotion", true);
            body.put("withParentProducts", false);
            body.put("withProductArticles", true);
            body.put("withProductSuggest", true);
            body.put("withSearchConditionId", false);
            body.put("withShopname", false);
            body.put("withSuggestedItems", true);
            
            logger.debug("📡 Отправляем запрос к Mercari API");
            
            java.net.URL url = new java.net.URL(API_URL);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            
            byte[] bodyBytes = body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            
            // Устанавливаем перехваченные заголовки
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                
                // Пропускаем псевдо-заголовки HTTP/2
                if (key.startsWith(":")) continue;
                
                // Пропускаем заголовки, которые устанавливаются автоматически
                if (key.equalsIgnoreCase("Host") || 
                    key.equalsIgnoreCase("Connection") ||
                    key.equalsIgnoreCase("Content-Length")) continue;
                
                conn.setRequestProperty(key, value);
            }
            
            // Устанавливаем обязательные заголовки
            if (!headers.containsKey("Content-Type") && !headers.containsKey("content-type")) {
                conn.setRequestProperty("Content-Type", "application/json");
            }
            conn.setRequestProperty("Content-Length", String.valueOf(bodyBytes.length));
            
            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(bodyBytes);
            }
            
            int code = conn.getResponseCode();
            logger.debug("📡 API ответ: {}", code);
            
            if (code == 200) {
                java.io.InputStream is = conn.getInputStream();
                if ("gzip".equalsIgnoreCase(conn.getContentEncoding())) {
                    is = new java.util.zip.GZIPInputStream(is);
                }
                
                StringBuilder sb = new StringBuilder();
                try (java.io.BufferedReader r = new java.io.BufferedReader(
                        new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                }
                logger.debug("✅ API ответ получен ({} символов)", sb.length());
                return sb.toString();
            } else {
                logger.error("❌ Mercari API ошибка: {}", code);
                if (code == 401 || code == 403) {
                    MercariHeaderService.clearCache();
                }
            }
            
        } catch (Exception e) {
            logger.error("❌ Ошибка запроса к Mercari API: {}", e.getMessage());
        }
        
        return null;
    }
    
    private List<Product> parseApiResponse(String response, String query) {
        List<Product> products = new ArrayList<>();
        
        try {
            JSONObject json = new JSONObject(response);
            JSONArray items = json.optJSONArray("items");
            
            if (items == null || items.length() == 0) {
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
            logger.error("❌ Ошибка парсинга ответа Mercari: {}", e.getMessage());
        }
        
        return products;
    }
    
    private Product parseItem(JSONObject item, String query) {
        try {
            String id = item.optString("id", "");
            if (id.isEmpty()) return null;
            
            String status = item.optString("status", "");
            if (status.contains("SOLD") || status.contains("TRADING")) return null;
            
            Product p = new Product();
            p.setId(id);
            p.setPlatform("mercari");
            p.setQuery(query);
            
            // Для товаров с ID начинающимся на "2J" используем другой endpoint
            if (id.startsWith("2J")) {
                p.setUrl("https://jp.mercari.com/en/shops/product/" + id);
            } else {
                p.setUrl("https://jp.mercari.com/item/" + id);
            }
            
            p.setTitle(item.optString("name", "Mercari Item"));
            
            // Цена может быть строкой или числом
            double priceValue = 0;
            Object price = item.opt("price");
            if (price instanceof Number) {
                priceValue = ((Number) price).doubleValue();
            } else if (price instanceof String) {
                try {
                    priceValue = Double.parseDouble((String) price);
                } catch (NumberFormatException ignored) {}
            }
            p.setPrice(priceValue);
            
            // Изображения
            JSONArray thumbs = item.optJSONArray("thumbnails");
            if (thumbs != null && thumbs.length() > 0) {
                for (int i = 0; i < Math.min(thumbs.length(), 5); i++) {
                    String imgUrl = thumbs.optString(i, "");
                    if (!imgUrl.isEmpty()) {
                        p.addImage(imgUrl);
                    }
                }
            }
            
            // Время создания может быть строкой или числом
            long created = 0;
            Object createdObj = item.opt("created");
            if (createdObj instanceof Number) {
                created = ((Number) createdObj).longValue();
            } else if (createdObj instanceof String) {
                try {
                    created = Long.parseLong((String) createdObj);
                } catch (NumberFormatException ignored) {}
            }
            
            if (created > 0) {
                // Если время в секундах (меньше 10 млрд), конвертируем в миллисекунды
                if (created < 10000000000L) created *= 1000;
                p.setPublishTime(created);
            }
            
            // Продавец
            JSONObject seller = item.optJSONObject("seller");
            if (seller != null) {
                String sellerName = seller.optString("name", "");
                if (!sellerName.isEmpty()) {
                    p.setSeller(sellerName);
                }
            }
            
            p.setLocation("Mercari Japan");
            return p;
            
        } catch (Exception e) {
            logger.debug("Ошибка парсинга товара Mercari: {}", e.getMessage());
            return null;
        }
    }
    
    @Override
    public List<Product> searchNewProducts(long userId, String queryId, String queryText, com.parseryss.model.UserSettings settings) {
        logger.info("🔍 Поиск НОВЫХ товаров Mercari: query='{}', userId={}, queryId={}", queryText, userId, queryId);
        
        int maxPages = settings.getMaxPages();
        int pageSize = 50; // Default page size for Mercari
        
        List<Product> all = search(queryText, maxPages, pageSize);
        if (all.isEmpty()) {
            logger.warn("⚠️ Товары не найдены для '{}'", queryText);
            return all;
        }
        
        // Получаем сохраненное состояние
        long lastTime = stateRepository.getLastPublishTime(userId, queryId, "mercari");
        String lastProductId = stateRepository.getLastProductId(userId, queryId, "mercari");
        boolean isFirstRun = (lastTime == 0);
        
        logger.info("📊 Последнее состояние для '{}': time={}, id={}, первый запуск: {}", 
            queryText, lastTime > 0 ? new Date(lastTime) : "null", lastProductId, isFirstRun);
        
        // Сортируем по времени (новые первые)
        all.sort((a, b) -> {
            Long t1 = a.getPublishTime() != null ? a.getPublishTime() : 0L;
            Long t2 = b.getPublishTime() != null ? b.getPublishTime() : 0L;
            return t2.compareTo(t1);
        });
        
        // Находим самый свежий товар
        Product newestProduct = null;
        for (Product p : all) {
            if (p.getPublishTime() != null && p.getPublishTime() > 0) {
                newestProduct = p;
                break;
            }
        }
        
        // Если первый запуск - отправляем самый новый товар и сохраняем его состояние
        if (isFirstRun) {
            if (newestProduct != null) {
                stateRepository.updateState(userId, queryId, "mercari", 
                    newestProduct.getPublishTime(), newestProduct.getId());
                logger.info("📌 Первый запуск - отправляем и сохраняем: id={}, time={}", 
                    newestProduct.getId(), new Date(newestProduct.getPublishTime()));
                return Collections.singletonList(newestProduct); // Отправляем самый новый товар
            }
            return Collections.emptyList();
        }
        
        // Фильтруем новые товары
        List<Product> newProducts = new ArrayList<>();
        for (Product p : all) {
            if (p.getPublishTime() == null) continue;
            
            long pTime = p.getPublishTime();
            
            // Товар новее сохраненного времени
            if (pTime > lastTime) {
                newProducts.add(p);
            }
            // Время одинаковое, но ID другой (для товаров с одинаковым временем публикации)
            else if (pTime == lastTime && lastProductId != null && !p.getId().equals(lastProductId)) {
                newProducts.add(p);
            }
            // Время меньше - прекращаем (товары отсортированы)
            else if (pTime < lastTime) {
                break;
            }
        }
        
        // Обновляем состояние, если есть более новый товар
        if (newestProduct != null && newestProduct.getPublishTime() > lastTime) {
            stateRepository.updateState(userId, queryId, "mercari", 
                newestProduct.getPublishTime(), newestProduct.getId());
            logger.info("📌 Обновлено состояние: id={}, time={}", 
                newestProduct.getId(), new Date(newestProduct.getPublishTime()));
        }
        
        logger.info("📦 Найдено {} новых товаров для '{}'", newProducts.size(), queryText);
        return newProducts;
    }
}

package com.parseryss.parser;

import com.parseryss.model.Product;
import com.parseryss.service.CookieService;
import com.parseryss.storage.ParserStateRepository;
import com.parseryss.util.HttpUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Парсер для сайта Goofish (闲鱼) с поддержкой динамических cookies
 */
public class GoofishParser extends BaseParser {
    private static final Logger logger = LoggerFactory.getLogger(GoofishParser.class);

    private static final String SEARCH_ENDPOINT = "/h5/mtop.taobao.idlemtopsearch.pc.search/1.0/";
    private static final String APP_KEY = "34839810";
    private static final String API_DOMAIN = "h5api.m.goofish.com";
    
    // Статистика попыток обновления кук
    private int cookieRefreshAttempts = 0;
    private long lastCookieRefreshTime = 0;
    
    private final ParserStateRepository stateRepository;
    
    public GoofishParser(ParserStateRepository stateRepository) {
        super("goofish", "https://h5api.m.goofish.com");
        this.stateRepository = stateRepository;
    }
    
    public String getPlatformName() {
        return "goofish";
    }
    
    public List<Product> search(String query, int maxPages, int pageSize) {
        List<Product> products = new ArrayList<>();
        
        try {
            logger.info("🔍 Поиск Goofish: '{}' (сортировка: новые первые)", query);
            
            // Парсим только первую страницу с сортировкой по дате
            String url = buildSearchUrl(query, 1, pageSize);
            
            if (url == null || url.isEmpty()) {
                logger.error("❌ Не удалось построить URL");
            } else {
                Map<String, String> headers = buildHeaders();
                String response = HttpUtils.sendGetRequest(url, headers);
                
                if (response == null) {
                    logger.warn("⚠️ Пустой ответ от API");
                } else {
                    List<Product> pageProducts = parseResponse(response, query);
                    
                    if (pageProducts.isEmpty()) {
                        logger.info("📄 Товаров не найдено");
                    } else {
                        products.addAll(pageProducts);
                        logger.info("📄 Найдено {} товаров", pageProducts.size());
                    }
                }
            }
            
            logger.info("✅ Всего найдено {} товаров для '{}'", products.size(), query);
            
        } catch (Exception e) {
            logger.error("❌ Ошибка поиска Goofish: {}", e.getMessage(), e);
        }
        
        return products;
    }
    
    @Override
    protected String buildSearchUrl(String query, int page, int rows) {
        try {
            long timestamp = System.currentTimeMillis();
            
            // Получаем токен из куки _m_h5_tk
            String mh5tk = getTokenFromCookies();
            String token = "";
            if (mh5tk != null && mh5tk.contains("_")) {
                token = mh5tk.split("_")[0];
            }
            
            // Формируем data
            JSONObject dataJson = new JSONObject();
            dataJson.put("pageNumber", page);
            dataJson.put("keyword", query);
            dataJson.put("fromFilter", false);
            dataJson.put("rowsPerPage", Math.min(rows, 30));
            dataJson.put("sortValue", "desc");
            dataJson.put("sortField", "create");
            dataJson.put("customDistance", "");
            dataJson.put("gps", "");
            dataJson.put("propValueStr", new JSONObject());
            dataJson.put("customGps", "");
            dataJson.put("searchReqFromPage", "pcSearch");
            dataJson.put("extraFilterValue", "{}");
            dataJson.put("userPositionJson", "{}");
            
            String dataStr = dataJson.toString();
            
            // Генерация подписи
            String sign = generateGoofishSignature(token, timestamp, dataStr);
            
            Map<String, String> params = new LinkedHashMap<>();
            params.put("jsv", "2.7.2");
            params.put("appKey", APP_KEY);
            params.put("t", String.valueOf(timestamp));
            params.put("sign", sign);
            params.put("v", "1.0");
            params.put("type", "originaljson");
            params.put("accountSite", "xianyu");
            params.put("dataType", "json");
            params.put("timeout", "20000");
            params.put("api", "mtop.taobao.idlemtopsearch.pc.search");
            params.put("sessionOption", "AutoLoginOnly");
            params.put("spm_cnt", "a21ybx.search.0.0");
            params.put("spm_pre", "a21ybx.search.searchInput.0");
            params.put("data", dataStr);
            
            String url = baseUrl + SEARCH_ENDPOINT;
            return HttpUtils.buildUrlWithParams(url, params);
            
        } catch (Exception e) {
            logger.error("❌ Ошибка построения URL: {}", e.getMessage(), e);
            return "";
        }
    }
    
    /**
     * Получение токена из куки _m_h5_tk
     */
    private String getTokenFromCookies() {
        try {
            // Получаем куки из CookieService
            String cookies = CookieService.getCookieHeader(API_DOMAIN);
            if (cookies == null || cookies.isEmpty()) {
                logger.warn("⚠️ Cookies пусты, попытка обновления...");
                if (shouldRefreshCookies()) {
                    CookieService.refreshCookies(API_DOMAIN);
                    cookies = CookieService.getCookieHeader(API_DOMAIN);
                }
                if (cookies == null || cookies.isEmpty()) {
                    return "";
                }
            }

            // Ищем _m_h5_tk в строке кук
            String[] cookiePairs = cookies.split("; ");
            for (String pair : cookiePairs) {
                if (pair.startsWith("_m_h5_tk=")) {
                    return pair.substring("_m_h5_tk=".length());
                }
            }

            return "";
        } catch (Exception e) {
            logger.error("❌ Ошибка получения токена: {}", e.getMessage());
            return "";
        }
    }
    
    /**
     * Проверка необходимости обновления кук
     */
    private boolean shouldRefreshCookies() {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastRefresh = currentTime - lastCookieRefreshTime;

        // Не обновляем чаще чем раз в 5 минут
        if (timeSinceLastRefresh < 5 * 60 * 1000) {
            return false;
        }

        // Если было много попыток, увеличиваем интервал
        if (cookieRefreshAttempts > 10) {
            return timeSinceLastRefresh > 30 * 60 * 1000; // 30 минут
        }

        return true;
    }
    
    private String generateGoofishSignature(String token, long timestamp, String data) {
        try {
            String signString = token + "&" + timestamp + "&" + APP_KEY + "&" + data;
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(signString.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            
            return hexString.toString();
            
        } catch (Exception e) {
            logger.error("❌ Ошибка генерации подписи: {}", e.getMessage());
            return "";
        }
    }
    
    /**
     * Построение заголовков с динамическими cookies
     */
    private Map<String, String> buildHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        headers.put("Accept", "application/json, text/plain, */*");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        headers.put("Referer", "https://www.goofish.com/");
        headers.put("Origin", "https://www.goofish.com");
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        
        // Получаем свежие cookies из CookieService
        try {
            String cookies = CookieService.getCookieHeader(API_DOMAIN);
            if (cookies != null && !cookies.isEmpty()) {
                headers.put("Cookie", cookies);
                logger.debug("🍪 Cookies добавлены в заголовки ({} символов)", cookies.length());
            } else {
                logger.warn("⚠️ Cookies пусты, запрос может не сработать");
            }
        } catch (Exception e) {
            logger.error("❌ Ошибка получения cookies: {}", e.getMessage());
        }
        
        return headers;
    }
    
    @Override
    protected List<Product> parseResponse(String response, String query) {
        List<Product> products = new ArrayList<>();
        
        if (response == null || response.isEmpty()) {
            logger.warn("⚠️ Пустой ответ");
            return products;
        }
        
        try {
            logger.debug("📄 Парсинг ответа ({} символов)", response.length());
            
            JSONObject json = new JSONObject(response);
            
            // Проверка статуса
            String ret = json.optString("ret", "");
            String status = json.optString("status", "");
            String msg = json.optString("msg", "");
            
            logger.debug("API response - ret: '{}', status: '{}', msg: '{}'", ret, status, msg);
            
            // Проверяем успешность ответа
            boolean isSuccess = false;
            
            if ("SUCCESS".equals(status)) {
                isSuccess = true;
            } else if (ret != null && !ret.isEmpty()) {
                if (ret.contains("SUCCESS")) {
                    isSuccess = true;
                } else if (ret.startsWith("[") && ret.endsWith("]")) {
                    try {
                        JSONArray retArray = new JSONArray(ret);
                        if (retArray.length() > 0) {
                            String firstRet = retArray.getString(0);
                            if (firstRet != null && firstRet.contains("SUCCESS")) {
                                isSuccess = true;
                                logger.debug("✅ Success detected in ret array: {}", firstRet);
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("⚠️ Failed to parse ret as JSON array: {}", ret);
                    }
                }
            }
            
            if (!isSuccess) {
                logger.error("❌ API вернул ошибку: {}, msg: {}", ret, msg);
                return products;
            }
            
            // Получение данных
            JSONObject data = json.optJSONObject("data");
            if (data == null) {
                logger.warn("⚠️ Нет объекта data в ответе");
                return products;
            }
            
            logger.debug("📊 Data keys: {}", data.keySet());
            
            // Получение списка товаров - пробуем разные ключи
            JSONArray resultList = data.optJSONArray("resultList");
            if (resultList == null || resultList.length() == 0) {
                String[] possibleKeys = {"items", "list", "result", "dataList", "resultData", "itemList"};
                for (String key : possibleKeys) {
                    if (data.has(key) && data.get(key) instanceof JSONArray) {
                        resultList = data.getJSONArray(key);
                        logger.debug("✅ Найдены товары в ключе '{}': {} items", key, resultList.length());
                        break;
                    }
                }
                
                if (resultList == null || resultList.length() == 0) {
                    logger.info("ℹ️ Товары не найдены в ответе");
                    return products;
                }
            }
            
            logger.info("📦 Найдено {} товаров в ответе", resultList.length());
            
            for (int i = 0; i < resultList.length(); i++) {
                JSONObject item = resultList.optJSONObject(i);
                if (item != null) {
                    Product p = parseProductItem(item, query);
                    if (p != null) products.add(p);
                }
            }
            
        } catch (Exception e) {
            logger.error("❌ Ошибка парсинга ответа Goofish: {}", e.getMessage());
        }
        
        return products;
    }
    
    private Product parseProductItem(JSONObject item, String query) {
        try {
            
            String itemId = null;
            String title = "";
            double price = 0;
            String picUrl = "";
            long publishTime = 0;
            
            // ПУТЬ 1: Структура data -> item -> main (оригинальная)
            JSONObject data = item.optJSONObject("data");
            if (data != null) {
                JSONObject itemObj = data.optJSONObject("item");
                if (itemObj != null) {
                    JSONObject mainObj = itemObj.optJSONObject("main");
                    JSONObject extra = itemObj.optJSONObject("extra");
                    JSONObject itemData = data.optJSONObject("itemData");
                    
                    // Сначала пробуем извлечь из clickParam.args (актуальная структура)
                    JSONObject args = null;
                    if (mainObj != null) {
                        JSONObject clickParam = mainObj.optJSONObject("clickParam");
                        if (clickParam != null) {
                            args = clickParam.optJSONObject("args");
                        }
                    }
                    
                    // ID - из args или itemData
                    if (args != null) {
                        itemId = args.optString("itemId", "");
                        if (itemId.isEmpty()) itemId = args.optString("id", "");
                        if (itemId.isEmpty()) itemId = args.optString("item_id", "");
                    }
                    if ((itemId == null || itemId.isEmpty()) && itemData != null) {
                        itemId = itemData.optString("itemId", "");
                    }
                    if ((itemId == null || itemId.isEmpty()) && extra != null) {
                        itemId = extra.optString("itemId", "");
                    }
                    
                    // Title - из mainObj.title или args
                    if (mainObj != null) {
                        JSONObject titleObj = mainObj.optJSONObject("title");
                        if (titleObj != null) {
                            title = titleObj.optString("text", "");
                        }
                    }
                    if (title.isEmpty() && args != null) {
                        title = args.optString("title", "");
                    }
                    if (title.isEmpty() && itemData != null) {
                        title = itemData.optString("title", "");
                    }
                    if (title.isEmpty() && extra != null) {
                        title = extra.optString("title", "");
                    }
                    
                    // Price - из args или itemData
                    if (args != null) {
                        String priceStr = args.optString("price", "0");
                        try { price = Double.parseDouble(priceStr.replaceAll("[^0-9.]", "")); } catch (Exception ignored) {}
                    }
                    if (price == 0 && itemData != null) {
                        String priceStr = itemData.optString("price", "0");
                        try { price = Double.parseDouble(priceStr); } catch (Exception ignored) {}
                    }
                    
                    // Image - из разных мест структуры (как в multiParser)
                    
                    // Путь 1: Из itemObj.extra.picUrl
                    if (picUrl.isEmpty() && extra != null) {
                        picUrl = extra.optString("picUrl", "");
                        if (picUrl.isEmpty()) picUrl = extra.optString("pic", "");
                        if (picUrl.isEmpty()) picUrl = extra.optString("mainPic", "");
                    }
                    
                    // Путь 2: Из data.pics (массив изображений)
                    if (picUrl.isEmpty() && data.has("pics")) {
                        Object picsObj = data.get("pics");
                        if (picsObj instanceof JSONArray) {
                            JSONArray picsList = (JSONArray) picsObj;
                            if (picsList.length() > 0) {
                                Object firstPic = picsList.get(0);
                                if (firstPic instanceof JSONObject) {
                                    JSONObject pic = (JSONObject) firstPic;
                                    picUrl = pic.optString("picUrl", "");
                                    if (picUrl.isEmpty()) picUrl = pic.optString("url", "");
                                }
                            }
                        }
                    }
                    
                    // Путь 3: Из mainObj.exContent.picUrl
                    if (picUrl.isEmpty() && mainObj != null) {
                        JSONObject exContent = mainObj.optJSONObject("exContent");
                        if (exContent != null) {
                            picUrl = exContent.optString("picUrl", "");
                            if (picUrl.isEmpty()) picUrl = exContent.optString("pic", "");
                        }
                    }
                    
                    // Путь 4: Из args
                    if (picUrl.isEmpty() && args != null) {
                        String[] picFields = {"picUrl", "pic", "img", "image", "mainPic", "coverPic", "cover"};
                        for (String field : picFields) {
                            if (picUrl.isEmpty()) {
                                picUrl = args.optString(field, "");
                            }
                        }
                    }
                    
                    // Путь 5: Из mainObj.pic
                    if (picUrl.isEmpty() && mainObj != null) {
                        JSONObject picObj = mainObj.optJSONObject("pic");
                        if (picObj != null) {
                            picUrl = picObj.optString("picUrl", "");
                            if (picUrl.isEmpty()) picUrl = picObj.optString("url", "");
                            if (picUrl.isEmpty()) picUrl = picObj.optString("src", "");
                        }
                    }
                    
                    // Путь 6: Из itemData
                    if (picUrl.isEmpty() && itemData != null) {
                        picUrl = itemData.optString("picUrl", "");
                        if (picUrl.isEmpty()) picUrl = itemData.optString("mainPic", "");
                        if (picUrl.isEmpty()) picUrl = itemData.optString("pic", "");
                    }
                    
                    // PublishTime - из args (пробуем разные поля)
                    if (args != null) {
                        // Пробуем все возможные поля для времени
                        String[] timeFields = {"publishTime", "gmtCreate", "gmtModified", "createTime", "time", "sortTime"};
                        for (String field : timeFields) {
                            if (publishTime == 0) {
                                publishTime = args.optLong(field, 0);
                                if (publishTime == 0) {
                                    String timeStr = args.optString(field, "0");
                                    try { publishTime = Long.parseLong(timeStr); } catch (Exception ignored) {}
                                }
                            }
                        }
                    }
                    
                    // Если время не найдено, используем текущее
                    if (publishTime == 0) {
                        publishTime = System.currentTimeMillis();
                    }
                    
                    // Fallback publishTime
                    if (publishTime == 0 && extra != null) {
                        publishTime = extra.optLong("publishTime", 0);
                        if (publishTime == 0) {
                            String timeStr = extra.optString("publishTime", "0");
                            try { publishTime = Long.parseLong(timeStr); } catch (Exception ignored) {}
                        }
                    }
                }
            }
            
            // ПУТЬ 2: Прямая структура (item содержит данные напрямую)
            if (itemId == null || itemId.isEmpty()) {
                itemId = item.optString("itemId", "");
                if (itemId.isEmpty()) itemId = item.optString("id", "");
                if (itemId.isEmpty()) itemId = String.valueOf(item.optLong("itemId", 0));
                if ("0".equals(itemId)) itemId = "";
                
                if (!itemId.isEmpty()) {
                    if (title.isEmpty()) title = item.optString("title", "");
                    if (title.isEmpty()) title = item.optString("name", "");
                    
                    if (price == 0) {
                        price = item.optDouble("price", 0);
                        if (price == 0) {
                            String priceStr = item.optString("price", "0");
                            try { price = Double.parseDouble(priceStr.replaceAll("[^0-9.]", "")); } catch (Exception ignored) {}
                        }
                    }
                    
                    if (picUrl.isEmpty()) {
                        picUrl = item.optString("picUrl", "");
                        if (picUrl.isEmpty()) picUrl = item.optString("pic", "");
                        if (picUrl.isEmpty()) picUrl = item.optString("mainPic", "");
                        if (picUrl.isEmpty()) picUrl = item.optString("image", "");
                    }
                    
                    if (publishTime == 0) {
                        publishTime = item.optLong("publishTime", 0);
                        if (publishTime == 0) publishTime = item.optLong("gmtCreate", 0);
                        if (publishTime == 0) publishTime = item.optLong("gmtModified", 0);
                    }
                }
            }
            
            // ПУТЬ 3: Структура через traceInfo или другие ключи
            if (itemId == null || itemId.isEmpty()) {
                JSONObject traceInfo = item.optJSONObject("traceInfo");
                if (traceInfo != null) {
                    itemId = traceInfo.optString("itemId", "");
                }
            }
            
            // Если ID не найден - пропускаем
            if (itemId == null || itemId.isEmpty()) {
                logger.trace("No itemId found in any path");
                return null;
            }
            
            // Создаем продукт
            Product product = new Product();
            product.setId(itemId);
            product.setPlatform("goofish");
            product.setQuery(query);
            product.setUrl("https://www.goofish.com/item?id=" + itemId);
            product.setTitle(title.isEmpty() ? "Goofish Item" : title);
            product.setPrice(price);
            
            // Добавляем фото
            if (!picUrl.isEmpty()) {
                // Если URL не полный, добавляем https:
                if (picUrl.startsWith("//")) {
                    picUrl = "https:" + picUrl;
                }
                if (isValidImageUrl(picUrl)) {
                    product.addImage(picUrl);
                } else {
                    logger.debug("⚠️ Невалидный URL фото: {}", picUrl);
                }
            } else {
                logger.debug("⚠️ Фото не найдено для товара {}", itemId);
            }
            
            if (publishTime < 10000000000L && publishTime > 0) publishTime *= 1000;
            product.setPublishTime(publishTime > 0 ? publishTime : System.currentTimeMillis());
            product.setLocation("Goofish China");
            
            logger.debug("✅ Parsed Goofish item: {} - {}", itemId, title);
            return product;
            
        } catch (Exception e) {
            logger.debug("Ошибка парсинга товара Goofish: {}", e.getMessage());
            return null;
        }
    }
    
    private boolean isValidImageUrl(String url) {
        if (url == null || url.isEmpty() || url.equals("None") || url.equals("null")) {
            return false;
        }
        
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return false;
        }
        
        String lowerUrl = url.toLowerCase();
        return lowerUrl.contains(".jpg") || lowerUrl.contains(".jpeg") ||
                lowerUrl.contains(".png") || lowerUrl.contains(".gif") ||
                lowerUrl.contains(".webp") || lowerUrl.contains("alicdn.com") ||
                lowerUrl.contains("tbcdn.cn") || lowerUrl.contains("goofish.com");
    }
    
    @Override
    public List<Product> searchNewProducts(long userId, String queryId, String queryText, com.parseryss.model.UserSettings settings) {
        logger.info("🔍 Поиск НОВЫХ товаров Goofish: query='{}', userId={}, queryId={}", queryText, userId, queryId);
        
        int maxPages = settings.getMaxPages();
        int pageSize = 50; // Default page size for Goofish
        List<Product> all = search(queryText, maxPages, pageSize);
        if (all.isEmpty()) {
            logger.warn("⚠️ Товары не найдены для '{}'", queryText);
            return all;
        }
        
        // Получаем сохраненное состояние
        long lastTime = stateRepository.getLastPublishTime(userId, queryId, "goofish");
        String lastProductId = stateRepository.getLastProductId(userId, queryId, "goofish");
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
                stateRepository.updateState(userId, queryId, "goofish", 
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
            stateRepository.updateState(userId, queryId, "goofish", 
                newestProduct.getPublishTime(), newestProduct.getId());
            logger.info("📌 Обновлено состояние: id={}, time={}", 
                newestProduct.getId(), new Date(newestProduct.getPublishTime()));
        }
        
        logger.info("📦 Найдено {} новых товаров для '{}'", newProducts.size(), queryText);
        return newProducts;
    }
}

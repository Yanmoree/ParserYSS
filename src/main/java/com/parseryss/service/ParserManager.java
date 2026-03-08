package com.parseryss.service;

import com.parseryss.model.Product;
import com.parseryss.model.Query;
import com.parseryss.model.User;
import com.parseryss.model.UserSettings;
import com.parseryss.parser.ParserFactory;
import com.parseryss.parser.SiteParser;
import com.parseryss.storage.QueryRepository;
import com.parseryss.storage.UserRepository;
import com.parseryss.storage.UserSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Менеджер для управления многопользовательским парсингом
 */
public class ParserManager {
    private static final Logger logger = LoggerFactory.getLogger(ParserManager.class);
    
    private final ParserFactory parserFactory;
    private final UserRepository userRepository;
    private final QueryRepository queryRepository;
    private final UserSettingsRepository settingsRepository;
    
    // Пул потоков для параллельного парсинга
    private final ExecutorService executorService;
    
    // Активные задачи парсинга для каждой платформы пользователя (ключ: "userId_platform")
    private final Map<String, ScheduledFuture<?>> activeParsingTasks;
    
    // Планировщик для периодического парсинга
    private final ScheduledExecutorService scheduler;
    
    // Блокировки для каждой платформы (чтобы запросы разных пользователей шли последовательно)
    private final Map<String, Object> platformLocks;
    
    // Callback для отправки найденных товаров
    private Consumer<ParsingResult> resultCallback;
    
    public ParserManager(ParserFactory parserFactory, 
                        UserRepository userRepository,
                        QueryRepository queryRepository,
                        UserSettingsRepository settingsRepository) {
        this.parserFactory = parserFactory;
        this.userRepository = userRepository;
        this.queryRepository = queryRepository;
        this.settingsRepository = settingsRepository;
        
        // Создаем пул потоков (по количеству ядер процессора)
        int threads = Runtime.getRuntime().availableProcessors();
        this.executorService = Executors.newFixedThreadPool(threads);
        
        // Планировщик для периодических задач (минимум 10 потоков для параллельного парсинга)
        int schedulerThreads = Math.max(10, threads);
        this.scheduler = Executors.newScheduledThreadPool(schedulerThreads);
        
        this.activeParsingTasks = new ConcurrentHashMap<>();
        this.platformLocks = new ConcurrentHashMap<>();
        
        logger.info("✅ ParserManager инициализирован ({} потоков)", threads);
    }
    
    /**
     * Установить callback для обработки результатов парсинга
     */
    public void setResultCallback(Consumer<ParsingResult> callback) {
        this.resultCallback = callback;
    }
    
    /**
     * Установить callback для отправки товаров (совместимость с Main.java)
     */
    public void setProductCallback(ProductCallback callback) {
        this.setResultCallback(result -> {
            callback.onProducts(result.getUserId(), result.getProducts());
        });
    }
    
    @FunctionalInterface
    public interface ProductCallback {
        void onProducts(long userId, List<Product> products);
    }
    
    /**
     * Запустить парсинг для пользователя
     */
    public boolean startParsing(long userId) {
        // Проверяем пользователя
        User user = userRepository.getUser(userId);
        if (user == null) {
            logger.error("❌ Пользователь {} не найден", userId);
            return false;
        }
        
        if (!userRepository.isSubscriptionActive(userId)) {
            logger.warn("⚠️ Подписка пользователя {} неактивна", userId);
            return false;
        }
        
        // Получаем настройки
        UserSettings settings = settingsRepository.getSettings(userId);
        if (settings == null) {
            logger.error("❌ Настройки пользователя {} не найдены", userId);
            return false;
        }
        
        // Получаем запросы
        List<Query> queries = queryRepository.getUserQueries(userId);
        if (queries.isEmpty()) {
            logger.warn("⚠️ У пользователя {} нет запросов для парсинга", userId);
            return false;
        }
        
        Set<String> enabledPlatforms = settings.getEnabledPlatforms();
        if (enabledPlatforms.isEmpty()) {
            logger.warn("⚠️ У пользователя {} нет включенных платформ", userId);
            return false;
        }
        
        // Запускаем НЕЗАВИСИМЫЙ цикл для КАЖДОЙ платформы
        boolean anyStarted = false;
        for (String platform : enabledPlatforms) {
            if (!userRepository.hasPlatformAccess(userId, platform)) {
                logger.debug("⚠️ У пользователя {} нет доступа к {}", userId, platform);
                continue;
            }
            
            SiteParser parser = parserFactory.getParser(platform);
            if (parser == null) {
                logger.warn("⚠️ Парсер для {} не найден", platform);
                continue;
            }
            
            String taskKey = userId + "_" + platform;
            if (activeParsingTasks.containsKey(taskKey)) {
                logger.warn("⚠️ Парсинг {} для пользователя {} уже запущен", platform, userId);
                continue;
            }
            
            // Разные интервалы для разных платформ
            int intervalSeconds = getIntervalForPlatform(platform);
            
            // Запускаем независимый цикл для платформы
            ScheduledFuture<?> task = scheduler.scheduleWithFixedDelay(
                () -> runPlatformParsingCycleWithLock(userId, platform, parser, queries, settings),
                0,
                intervalSeconds,
                TimeUnit.SECONDS
            );
            
            activeParsingTasks.put(taskKey, task);
            logger.info("🚀 Парсинг {} запущен для пользователя {} (интервал: {}с)", platform, userId, intervalSeconds);
            anyStarted = true;
        }
        
        return anyStarted;
    }
    
    /**
     * Получить интервал для платформы (в секундах)
     */
    private int getIntervalForPlatform(String platform) {
        if ("avito".equalsIgnoreCase(platform)) {
            return 60; // Avito: 60 секунд между циклами (защита от бана)
        } else {
            return 10; // Mercari/Goofish: 10 секунд
        }
    }
    
    /**
     * Остановить парсинг для пользователя (все платформы)
     */
    public boolean stopParsing(long userId) {
        boolean anyStopped = false;
        List<String> keysToRemove = new ArrayList<>();
        
        // Находим все задачи этого пользователя
        String userPrefix = userId + "_";
        for (String key : activeParsingTasks.keySet()) {
            if (key.startsWith(userPrefix)) {
                keysToRemove.add(key);
            }
        }
        
        if (keysToRemove.isEmpty()) {
            logger.warn("⚠️ Парсинг для пользователя {} не запущен", userId);
            return false;
        }
        
        // Останавливаем все задачи
        for (String key : keysToRemove) {
            ScheduledFuture<?> task = activeParsingTasks.remove(key);
            if (task != null) {
                task.cancel(false);
                String platform = key.substring(userPrefix.length());
                logger.info("🛑 Парсинг {} остановлен для пользователя {}", platform, userId);
                anyStopped = true;
            }
        }
        
        return anyStopped;
    }
    
    /**
     * Проверить, запущен ли парсинг для пользователя (любая платформа)
     */
    public boolean isParsingActive(long userId) {
        String userPrefix = userId + "_";
        for (String key : activeParsingTasks.keySet()) {
            if (key.startsWith(userPrefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Alias для isParsingActive
     */
    public boolean isRunning(long userId) {
        return isParsingActive(userId);
    }
    

    
    /**
     * Выполнить один цикл парсинга с блокировкой по платформе
     * (чтобы запросы разных пользователей к одной платформе шли последовательно)
     */
    private void runPlatformParsingCycleWithLock(long userId, String platform, SiteParser parser, List<Query> queries, UserSettings settings) {
        // Получаем или создаем блокировку для платформы
        Object lock = platformLocks.computeIfAbsent(platform, k -> new Object());
        
        // Блокируем выполнение для этой платформы
        synchronized (lock) {
            runPlatformParsingCycle(userId, platform, parser, queries, settings);
        }
    }
    
    /**
     * Выполнить один цикл парсинга для ОДНОЙ платформы
     */
    private void runPlatformParsingCycle(long userId, String platform, SiteParser parser, List<Query> queries, UserSettings settings) {
        try {
            logger.info("🔄 Начат цикл парсинга {} для пользователя {}", platform, userId);
            
            int totalProducts = 0;
            boolean isAvito = "avito".equalsIgnoreCase(platform);
            boolean isGoofish = "goofish".equalsIgnoreCase(platform);
            Random random = new Random();
            
            if (isAvito) {
                // Avito: ПОСЛЕДОВАТЕЛЬНО с задержкой 15-20 сек (защита от бана)
                int requestCount = 0;
                
                for (Query query : queries) {
                    if (!query.isActive()) continue;
                    
                    if (requestCount > 0) {
                        try {
                            // Случайная задержка 15-20 секунд
                            int delaySeconds = 15 + random.nextInt(6); // 15-20 сек
                            logger.debug("⏳ [Avito] Задержка {} сек...", delaySeconds);
                            Thread.sleep(delaySeconds * 1000L);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                    requestCount++;
                    
                    totalProducts += parseAndSendProducts(userId, platform, parser, query, settings);
                }
            } else if (isGoofish) {
                // Goofish: ПОСЛЕДОВАТЕЛЬНО с задержкой 3-5 сек (защита от бана IP)
                int requestCount = 0;
                
                for (Query query : queries) {
                    if (!query.isActive()) continue;
                    
                    if (requestCount > 0) {
                        try {
                            // Случайная задержка 3-5 секунд
                            int delaySeconds = 3 + random.nextInt(3); // 3-5 сек
                            logger.debug("⏳ [Goofish] Задержка {} сек...", delaySeconds);
                            Thread.sleep(delaySeconds * 1000L);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                    requestCount++;
                    
                    totalProducts += parseAndSendProducts(userId, platform, parser, query, settings);
                }
            } else {
                // Mercari: ПОСЛЕДОВАТЕЛЬНО с задержкой 1 секунда
                int requestCount = 0;
                
                for (Query query : queries) {
                    if (!query.isActive()) continue;
                    
                    if (requestCount > 0) {
                        try {
                            // Задержка 1 секунда между запросами
                            logger.debug("⏳ [{}] Задержка 1 сек...", platform);
                            Thread.sleep(1000L);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                    requestCount++;
                    
                    totalProducts += parseAndSendProducts(userId, platform, parser, query, settings);
                }
            }
            
            logger.info("✅ Цикл {} завершен: {} товаров", platform, totalProducts);
            
        } catch (Exception e) {
            logger.error("❌ Ошибка в цикле {} для пользователя {}: {}", platform, userId, e.getMessage(), e);
        }
    }
    
    /**
     * Парсить и отправить товары для одного запроса
     */
    private int parseAndSendProducts(long userId, String platform, SiteParser parser, Query query, UserSettings settings) {
        try {
            List<Product> products = parser.searchNewProducts(userId, query.getQueryId(), query.getQueryText(), settings);
            
            if (!products.isEmpty() && resultCallback != null) {
                List<Product> filtered = filterProducts(products, settings);
                if (!filtered.isEmpty()) {
                    ParsingResult result = new ParsingResult(userId, filtered);
                    resultCallback.accept(result);
                    logger.info("📤 Отправлено {} товаров для '{}' на {}", filtered.size(), query.getQueryText(), platform);
                }
            }
            
            return products.size();
        } catch (Exception e) {
            logger.error("❌ Ошибка парсинга {} для '{}': {}", platform, query.getQueryText(), e.getMessage());
            return 0;
        }
    }
    
    /**
     * Фильтровать товары по настройкам пользователя
     */
    private List<Product> filterProducts(List<Product> products, UserSettings settings) {
        List<Product> filtered = new ArrayList<>();
        
        double minPrice = settings.getMinPrice();
        double maxPrice = settings.getMaxPrice();
        
        logger.debug("📊 Фильтрация: {} товаров, minPrice={}, maxPrice={}", products.size(), minPrice, maxPrice);
        
        for (Product product : products) {
            // Фильтр по цене
            double productPrice = product.getPrice();
            if (minPrice > 0 && productPrice < minPrice) {
                logger.debug("⚠️ Товар {} отфильтрован: цена {} < minPrice {}", product.getId(), productPrice, minPrice);
                continue;
            }
            if (maxPrice > 0 && productPrice > maxPrice) {
                logger.debug("⚠️ Товар {} отфильтрован: цена {} > maxPrice {}", product.getId(), productPrice, maxPrice);
                continue;
            }
            
            filtered.add(product);
        }
        
        logger.debug("✅ После фильтрации: {} товаров", filtered.size());
        return filtered;
    }
    
    /**
     * Получить список активных пользователей
     */
    public Set<Long> getActiveUsers() {
        Set<Long> userIds = new HashSet<>();
        for (String key : activeParsingTasks.keySet()) {
            // Ключ имеет формат "userId_platform"
            String userIdStr = key.split("_")[0];
            userIds.add(Long.parseLong(userIdStr));
        }
        return userIds;
    }
    
    /**
     * Остановить все активные парсинги
     */
    public void stopAll() {
        logger.info("🛑 Остановка всех активных парсингов...");
        
        Set<Long> userIds = getActiveUsers();
        for (Long userId : userIds) {
            stopParsing(userId);
        }
        
        logger.info("✅ Все парсинги остановлены");
    }
    
    /**
     * Завершить работу менеджера
     */
    public void shutdown() {
        logger.info("🛑 Завершение работы ParserManager...");
        
        stopAll();
        
        scheduler.shutdown();
        executorService.shutdown();
        
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("✅ ParserManager завершил работу");
    }
    
    /**
     * Результат парсинга
     */
    public static class ParsingResult {
        private final long userId;
        private final List<Product> products;
        private final long timestamp;
        
        public ParsingResult(long userId, List<Product> products) {
            this.userId = userId;
            this.products = products;
            this.timestamp = System.currentTimeMillis();
        }
        
        public long getUserId() { return userId; }
        public List<Product> getProducts() { return products; }
        public long getTimestamp() { return timestamp; }
    }
}

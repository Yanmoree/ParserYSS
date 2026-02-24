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
    
    // Активные задачи парсинга для каждого пользователя
    private final Map<Long, ScheduledFuture<?>> activeParsingTasks;
    
    // Планировщик для периодического парсинга
    private final ScheduledExecutorService scheduler;
    
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
        
        // Планировщик для периодических задач
        this.scheduler = Executors.newScheduledThreadPool(threads);
        
        this.activeParsingTasks = new ConcurrentHashMap<>();
        
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
        // Проверяем, не запущен ли уже парсинг
        if (activeParsingTasks.containsKey(userId)) {
            logger.warn("⚠️ Парсинг для пользователя {} уже запущен", userId);
            return false;
        }
        
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
        
        // Запускаем периодическую задачу парсинга
        int intervalSeconds = settings.getCheckIntervalSeconds();
        ScheduledFuture<?> task = scheduler.scheduleWithFixedDelay(
            () -> runParsingCycle(userId, user, settings, queries),
            0, // Начать сразу
            intervalSeconds,
            TimeUnit.SECONDS
        );
        
        activeParsingTasks.put(userId, task);
        logger.info("🚀 Парсинг запущен для пользователя {} (интервал: {}с)", userId, intervalSeconds);
        
        return true;
    }
    
    /**
     * Остановить парсинг для пользователя
     */
    public boolean stopParsing(long userId) {
        ScheduledFuture<?> task = activeParsingTasks.remove(userId);
        
        if (task == null) {
            logger.warn("⚠️ Парсинг для пользователя {} не запущен", userId);
            return false;
        }
        
        task.cancel(false);
        logger.info("🛑 Парсинг остановлен для пользователя {}", userId);
        
        return true;
    }
    
    /**
     * Проверить, запущен ли парсинг для пользователя
     */
    public boolean isParsingActive(long userId) {
        return activeParsingTasks.containsKey(userId);
    }

    /**
     * Alias для isParsingActive
     */
    public boolean isRunning(long userId) {
        return isParsingActive(userId);
    }
    
    /**
     * Выполнить один цикл парсинга для пользователя
     */
    private void runParsingCycle(long userId, User user, UserSettings settings, List<Query> queries) {
        try {
            logger.info("🔄 Начат цикл парсинга для пользователя {}", userId);
            
            // Получаем включенные платформы
            Set<String> enabledPlatforms = settings.getEnabledPlatforms();
            
            if (enabledPlatforms.isEmpty()) {
                logger.warn("⚠️ У пользователя {} нет включенных платформ", userId);
                return;
            }
            
            // Парсим каждую комбинацию (платформа, запрос)
            List<CompletableFuture<List<Product>>> futures = new ArrayList<>();
            
            for (String platform : enabledPlatforms) {
                // Проверяем доступ к платформе
                if (!userRepository.hasPlatformAccess(userId, platform)) {
                    logger.debug("⚠️ У пользователя {} нет доступа к платформе {}", userId, platform);
                    continue;
                }
                
                SiteParser parser = parserFactory.getParser(platform);
                if (parser == null) {
                    logger.warn("⚠️ Парсер для платформы {} не найден", platform);
                    continue;
                }
                
                for (Query query : queries) {
                    // Пропускаем неактивные запросы
                    if (!query.isActive()) {
                        continue;
                    }
                    
                    // Запускаем парсинг асинхронно с немедленной отправкой товаров
                    CompletableFuture<List<Product>> future = CompletableFuture.supplyAsync(() -> {
                        try {
                            List<Product> products = parser.searchNewProducts(userId, query.getQueryId(), query.getQueryText(), settings);
                            
                            // Отправляем товары сразу после нахождения
                            if (!products.isEmpty() && resultCallback != null) {
                                // Фильтруем по настройкам
                                List<Product> filtered = filterProducts(products, settings);
                                if (!filtered.isEmpty()) {
                                    ParsingResult result = new ParsingResult(userId, filtered);
                                    resultCallback.accept(result);
                                    logger.info("📤 Отправлено {} товаров для '{}' на {}", filtered.size(), query.getQueryText(), platform);
                                }
                            }
                            
                            return products;
                        } catch (Exception e) {
                            logger.error("❌ Ошибка парсинга {} для запроса '{}': {}", 
                                platform, query.getQueryText(), e.getMessage());
                            return Collections.emptyList();
                        }
                    }, executorService);
                    
                    futures.add(future);
                }
            }
            
            // Ждем завершения всех задач
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            
            // Подсчитываем общее количество найденных товаров
            int totalProducts = 0;
            for (CompletableFuture<List<Product>> future : futures) {
                try {
                    List<Product> products = future.get();
                    totalProducts += products.size();
                } catch (Exception e) {
                    logger.error("❌ Ошибка получения результатов: {}", e.getMessage());
                }
            }
            
            logger.info("✅ Цикл парсинга завершен для пользователя {}: найдено {} товаров", 
                userId, totalProducts);
            
        } catch (Exception e) {
            logger.error("❌ Ошибка в цикле парсинга для пользователя {}: {}", userId, e.getMessage(), e);
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
        return new HashSet<>(activeParsingTasks.keySet());
    }
    
    /**
     * Остановить все активные парсинги
     */
    public void stopAll() {
        logger.info("🛑 Остановка всех активных парсингов...");
        
        for (Long userId : new ArrayList<>(activeParsingTasks.keySet())) {
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

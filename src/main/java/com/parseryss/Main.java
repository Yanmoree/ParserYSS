package com.parseryss;

import com.parseryss.bot.TelegramBotService;
import com.parseryss.model.Product;
import com.parseryss.service.ParserManager;
import com.parseryss.storage.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.List;

/**
 * Главный класс приложения ParserYSS
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    
    public static void main(String[] args) {
        logger.info("🚀 Запуск ParserYSS...");
        
        try {
            // Инициализация CookieService для Goofish
            logger.info("🍪 Инициализация CookieService...");
            com.parseryss.service.CookieService.initialize();
            logger.info("✅ CookieService инициализирован");
            
            // Инициализация Storage Layer
            logger.info("📦 Инициализация хранилищ...");
            FileStorage fileStorage = new FileStorage();
            UserRepository userRepository = new UserRepository(fileStorage);
            QueryRepository queryRepository = new QueryRepository(fileStorage);
            ParserStateRepository stateRepository = new ParserStateRepository(fileStorage);
            UserSettingsRepository settingsRepository = new UserSettingsRepository(fileStorage);
            
            logger.info("✅ Хранилища инициализированы");
            
            // Инициализация ParserFactory
            logger.info("🏭 Инициализация ParserFactory...");
            com.parseryss.parser.ParserFactory parserFactory = new com.parseryss.parser.ParserFactory(stateRepository);
            
            // Инициализация ParserManager
            logger.info("🤖 Инициализация ParserManager...");
            ParserManager parserManager = new ParserManager(
                parserFactory,
                userRepository,
                queryRepository,
                settingsRepository
            );
            
            logger.info("✅ ParserManager инициализирован");
            
            // Инициализация Telegram бота
            logger.info("📱 Инициализация Telegram бота...");
            TelegramBotService bot = new TelegramBotService(
                userRepository,
                queryRepository,
                settingsRepository,
                parserManager
            );
            
            // Устанавливаем callback для отправки товаров
            parserManager.setProductCallback((userId, products) -> {
                for (Product product : products) {
                    bot.sendProduct(userId, product);
                }
            });
            
            // Регистрация бота
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(bot);
            
            logger.info("✅ Telegram бот запущен");
            logger.info("🎉 ParserYSS успешно запущен!");
            logger.info("📊 Статистика:");
            logger.info("   - Пользователей: {}", userRepository.getAll().size());
            logger.info("   - Запросов: {}", queryRepository.getAll().size());
            
            // Добавляем shutdown hook для корректного завершения
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("🛑 Остановка ParserYSS...");
                parserManager.shutdown();
                com.parseryss.service.CookieService.shutdown();
                logger.info("✅ ParserYSS остановлен");
            }));
            
            // Держим приложение запущенным
            Thread.currentThread().join();
            
        } catch (TelegramApiException e) {
            logger.error("❌ Ошибка инициализации Telegram бота: {}", e.getMessage(), e);
            System.exit(1);
        } catch (InterruptedException e) {
            logger.info("⚠️ Приложение прервано");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("❌ Критическая ошибка: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}

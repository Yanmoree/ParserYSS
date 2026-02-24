package com.parseryss.bot.handlers;

import com.parseryss.bot.TelegramBotService;
import com.parseryss.bot.keyboards.MainKeyboard;
import com.parseryss.model.User;
import com.parseryss.model.UserSettings;
import com.parseryss.service.ParserManager;
import com.parseryss.storage.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.time.LocalDateTime;

/**
 * Обработчик команды /start
 */
public class StartCommandHandler {
    private static final Logger logger = LoggerFactory.getLogger(StartCommandHandler.class);
    
    private final TelegramBotService bot;
    private final UserRepository userRepository;
    private final ParserManager parserManager;
    
    public StartCommandHandler(
            TelegramBotService bot,
            UserRepository userRepository,
            ParserManager parserManager
    ) {
        this.bot = bot;
        this.userRepository = userRepository;
        this.parserManager = parserManager;
    }
    
    public void handleStart(long chatId, long userId, String telegramUsername) {
        User user = userRepository.getById(userId);
        
        if (user == null) {
            // Регистрация нового пользователя
            user = new User(userId);
            // Используем реальный Telegram username, если он есть
            if (telegramUsername != null && !telegramUsername.isEmpty()) {
                user.setUsername("@" + telegramUsername);
            } else {
                user.setUsername("user_" + userId);
            }
            user.setAdmin(false);
            
            userRepository.save(user);
            
            logger.info("✅ Зарегистрирован новый пользователь: {} ({})", userId, user.getUsername());
            
            String welcomeText = "👋 <b>Добро пожаловать в ParserYSS!</b>\n\n" +
                    "Это бот для автоматического парсинга товаров с различных платформ.\n\n" +
                    "❗️ Ваш аккаунт создан, но подписка не активна.\n" +
                    "Обратитесь к администратору для активации.";
            
            bot.sendMessage(chatId, welcomeText);
        } else {
            // Обновляем username если он изменился
            if (telegramUsername != null && !telegramUsername.isEmpty()) {
                String newUsername = "@" + telegramUsername;
                if (!newUsername.equals(user.getUsername())) {
                    user.setUsername(newUsername);
                    userRepository.save(user);
                    logger.info("🔄 Обновлен username для {}: {}", userId, newUsername);
                }
            }
            // Показываем главное меню
            showMainMenu(chatId, userId);
        }
    }
    
    public void showMainMenu(long chatId, long userId) {
        User user = userRepository.getById(userId);
        
        if (user == null) {
            bot.sendMessage(chatId, "❌ Ошибка: пользователь не найден");
            return;
        }
        
        boolean isRunning = parserManager.isRunning(userId);
        
        StringBuilder text = new StringBuilder();
        text.append("🏠 <b>Главное меню</b>\n\n");
        text.append("👤 Пользователь: ").append(user.getUsername()).append("\n");
        text.append("📊 Статус подписки: ");
        
        if (user.isSubscriptionActive()) {
            text.append("✅ Активна");
            if (user.getSubscriptionEnd() != null) {
                text.append(" до ").append(user.getSubscriptionEnd());
            }
        } else {
            text.append("❌ Не активна");
        }
        
        text.append("\n\n🤖 Парсер: ");
        text.append(isRunning ? "🟢 Запущен" : "🔴 Остановлен");
        
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text.toString());
        message.enableHtml(true);
        message.setReplyMarkup(MainKeyboard.getKeyboard(isRunning, user.isAdmin()));
        
        bot.sendMessageWithKeyboard(message);
    }
}

package com.parseryss.bot;

import com.parseryss.bot.handlers.*;
import com.parseryss.model.Product;
import com.parseryss.model.User;
import com.parseryss.model.UserSettings;
import com.parseryss.service.ParserManager;
import com.parseryss.storage.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;

/**
 * Telegram бот для управления парсером
 */
public class TelegramBotService extends TelegramLongPollingBot {
    private static final Logger logger = LoggerFactory.getLogger(TelegramBotService.class);
    
    private static final String BOT_TOKEN = "8538627254:AAE_niIKdyWgM69JSrto7tKntao5vS7qj5g";
    private static final String BOT_USERNAME = "multiparse_bot";
    
    private final UserRepository userRepository;
    private final QueryRepository queryRepository;
    private final UserSettingsRepository settingsRepository;
    private final ParserManager parserManager;
    
    // Handlers
    private final StartCommandHandler startHandler;
    private final SettingsHandler settingsHandler;
    private final QueryHandler queryHandler;
    private final AdminHandler adminHandler;
    private final CallbackHandler callbackHandler;
    
    public TelegramBotService(
            UserRepository userRepository,
            QueryRepository queryRepository,
            UserSettingsRepository settingsRepository,
            ParserManager parserManager
    ) {
        this.userRepository = userRepository;
        this.queryRepository = queryRepository;
        this.settingsRepository = settingsRepository;
        this.parserManager = parserManager;
        
        // Инициализация handlers
        this.startHandler = new StartCommandHandler(this, userRepository, parserManager);
        this.settingsHandler = new SettingsHandler(this, settingsRepository);
        this.queryHandler = new QueryHandler(this, queryRepository);
        this.adminHandler = new AdminHandler(this, userRepository);
        this.callbackHandler = new CallbackHandler(
            this, 
            userRepository, 
            settingsRepository, 
            queryRepository, 
            parserManager
        );
        
        logger.info("✅ TelegramBotService инициализирован");
    }
    
    @Override
    public String getBotUsername() {
        return BOT_USERNAME;
    }
    
    @Override
    public String getBotToken() {
        return BOT_TOKEN;
    }
    
    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                handleTextMessage(update);
            } else if (update.hasCallbackQuery()) {
                callbackHandler.handle(update.getCallbackQuery());
            }
        } catch (Exception e) {
            logger.error("❌ Ошибка обработки update: {}", e.getMessage(), e);
        }
    }
    
    private void handleTextMessage(Update update) {
        long chatId = update.getMessage().getChatId();
        long userId = update.getMessage().getFrom().getId();
        String text = update.getMessage().getText();
        
        logger.info("📨 Сообщение от {}: {}", userId, text);
        
        // Проверяем, зарегистрирован ли пользователь
        User user = userRepository.getById(userId);
        if (user == null && !text.equals("/start")) {
            sendMessage(chatId, "❌ Вы не зарегистрированы. Используйте /start");
            return;
        }
        
        // Проверяем состояние пользователя
        if (UserState.isWaitingForInput(userId)) {
            handleUserInput(chatId, userId, text);
            return;
        }
        
        // Обработка команд
        if (text.equals("/start")) {
            String username = update.getMessage().getFrom().getUserName();
            startHandler.handleStart(chatId, userId, username);
        } else if (text.equals("/admin") && isAdmin(userId)) {
            adminHandler.showAdminPanel(chatId);
        } else {
            sendMessage(chatId, "❓ Неизвестная команда. Используйте кнопки ниже.");
        }
    }
    
    private void handleUserInput(long chatId, long userId, String text) {
        UserState.State state = UserState.getState(userId);
        
        switch (state) {
            case WAITING_FOR_QUERY:
                // Добавление нового запроса
                String queryId = java.util.UUID.randomUUID().toString();
                com.parseryss.model.Query newQuery = new com.parseryss.model.Query(queryId, userId, text);
                queryRepository.save(newQuery);
                UserState.clearState(userId);
                sendMessage(chatId, "✅ Запрос '" + text + "' добавлен!");
                queryHandler.showQueries(chatId, userId);
                break;
                
            case WAITING_FOR_MIN_PRICE:
                try {
                    double minPrice = Double.parseDouble(text);
                    UserSettings settings = settingsRepository.getSettings(userId);
                    if (settings == null) {
                        settings = new UserSettings(userId);
                    }
                    settings.setMinPrice(minPrice);
                    settingsRepository.save(settings);
                    UserState.clearState(userId);
                    sendMessage(chatId, "✅ Минимальная цена установлена: " + minPrice);
                    settingsHandler.showSettings(chatId, userId);
                } catch (NumberFormatException e) {
                    sendMessage(chatId, "❌ Неверный формат числа. Попробуйте еще раз.");
                }
                break;
                
            case WAITING_FOR_MAX_PRICE:
                try {
                    double maxPrice = Double.parseDouble(text);
                    UserSettings settings = settingsRepository.getSettings(userId);
                    if (settings == null) {
                        settings = new UserSettings(userId);
                    }
                    settings.setMaxPrice(maxPrice);
                    settingsRepository.save(settings);
                    UserState.clearState(userId);
                    sendMessage(chatId, "✅ Максимальная цена установлена: " + maxPrice);
                    settingsHandler.showSettings(chatId, userId);
                } catch (NumberFormatException e) {
                    sendMessage(chatId, "❌ Неверный формат числа. Попробуйте еще раз.");
                }
                break;
                
            case WAITING_FOR_USER_ID:
                try {
                    long newUserId = Long.parseLong(text);
                    
                    // Проверяем, существует ли уже пользователь
                    com.parseryss.model.User existingUser = userRepository.getById(newUserId);
                    if (existingUser != null) {
                        sendMessage(chatId, "❌ Пользователь с ID " + newUserId + " уже существует!");
                        UserState.clearState(userId);
                        return;
                    }
                    
                    // Создаем нового пользователя
                    com.parseryss.model.User newUser = new com.parseryss.model.User(newUserId);
                    newUser.setUsername("User_" + newUserId);
                    newUser.setLifetimeSubscription(true); // Бессрочная подписка
                    
                    // Добавляем доступ ко всем платформам
                    newUser.addPlatform("mercari");
                    newUser.addPlatform("avito");
                    newUser.addPlatform("goofish");
                    
                    userRepository.save(newUser);
                    
                    UserState.clearState(userId);
                    sendMessage(chatId, "✅ Пользователь " + newUserId + " успешно добавлен!");
                    adminHandler.showAdminPanel(chatId);
                } catch (NumberFormatException e) {
                    sendMessage(chatId, "❌ Неверный формат ID. Попробуйте еще раз.");
                }
                break;
                
            case WAITING_FOR_SUBSCRIPTION_DAYS:
                try {
                    int days = Integer.parseInt(text);
                    if (days <= 0) {
                        sendMessage(chatId, "❌ Количество дней должно быть больше 0.");
                        return;
                    }
                    
                    Long targetUserId = UserState.getSubscriptionTargetUser(userId);
                    if (targetUserId == null) {
                        sendMessage(chatId, "❌ Ошибка: целевой пользователь не найден.");
                        UserState.clearState(userId);
                        return;
                    }
                    
                    com.parseryss.model.User targetUser = userRepository.getById(targetUserId);
                    if (targetUser == null) {
                        sendMessage(chatId, "❌ Пользователь не найден.");
                        UserState.clearState(userId);
                        UserState.clearSubscriptionTargetUser(userId);
                        return;
                    }
                    
                    // Устанавливаем подписку на N дней
                    java.time.LocalDateTime expiryDate = java.time.LocalDateTime.now().plusDays(days);
                    targetUser.setSubscriptionExpiry(expiryDate);
                    targetUser.setLifetimeSubscription(false);
                    userRepository.save(targetUser);
                    
                    UserState.clearState(userId);
                    UserState.clearSubscriptionTargetUser(userId);
                    
                    sendMessage(chatId, "✅ Подписка для " + targetUser.getUsername() + " активирована на " + days + " дней (до " + expiryDate.toLocalDate() + ")");
                    adminHandler.showAdminPanel(chatId);
                } catch (NumberFormatException e) {
                    sendMessage(chatId, "❌ Неверный формат числа. Введите целое число дней.");
                }
                break;
        }
    }
    
    /**
     * Отправить текстовое сообщение
     */
    public void sendMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.enableHtml(true);
        
        try {
            execute(message);
        } catch (TelegramApiException e) {
            logger.error("❌ Ошибка отправки сообщения: {}", e.getMessage());
        }
    }
    
    /**
     * Отправить сообщение с клавиатурой
     */
    public void sendMessageWithKeyboard(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            logger.error("❌ Ошибка отправки сообщения с клавиатурой: {}", e.getMessage());
        }
    }
    
    /**
     * Отправить товар пользователю
     */
    public void sendProduct(long userId, Product product) {
        try {
            StringBuilder text = new StringBuilder();
            text.append("🆕 <b>Новый товар!</b>\n\n");
            text.append("📦 <b>").append(escapeHtml(product.getTitle())).append("</b>\n\n");
            text.append("💰 Цена: <b>").append(escapeHtml(product.getFullPriceDisplay())).append("</b>\n");
            text.append("🌐 Платформа: <b>").append(product.getSite()).append("</b>\n");
            text.append("🔍 Запрос: <i>").append(escapeHtml(product.getQuery())).append("</i>\n");
            
            if (product.getPublishTime() != null && product.getPublishTime() > 0) {
                text.append("🕐 Опубликовано: ").append(formatTime(product.getPublishTime())).append("\n");
            }
            
            if (product.getLocation() != null && !product.getLocation().isEmpty()) {
                text.append("📍 Локация: ").append(escapeHtml(product.getLocation())).append("\n");
            }
            
            text.append("\n🔗 <a href=\"").append(product.getUrl()).append("\">Открыть товар</a>");
            
            // Если есть изображения, отправляем первое
            List<String> images = product.getImages();
            if (images != null && !images.isEmpty()) {
                SendPhoto photo = new SendPhoto();
                photo.setChatId(String.valueOf(userId));
                photo.setPhoto(new InputFile(images.get(0)));
                photo.setCaption(text.toString());
                photo.setParseMode("HTML");
                
                execute(photo);
            } else {
                // Если нет изображений, отправляем текст
                sendMessage(userId, text.toString());
            }
            
        } catch (Exception e) {
            logger.error("❌ Ошибка отправки товара: {}", e.getMessage());
            // Fallback - отправляем хотя бы ссылку
            sendMessage(userId, "🆕 Новый товар: " + product.getUrl());
        }
    }
    
    /**
     * Проверить, является ли пользователь админом
     */
    private boolean isAdmin(long userId) {
        User user = userRepository.getById(userId);
        return user != null && user.isAdmin();
    }
    
    /**
     * Экранирование HTML
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }
    
    /**
     * Форматирование цены (устаревший метод, используйте product.getFullPriceDisplay())
     */
    @Deprecated
    private String formatPrice(double price) {
        if (price == 0) return "Не указана";
        return String.format("%.2f ₽", price);
    }
    
    /**
     * Форматирование времени
     */
    private String formatTime(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;
        
        long minutes = diff / (1000 * 60);
        long hours = diff / (1000 * 60 * 60);
        long days = diff / (1000 * 60 * 60 * 24);
        
        if (minutes < 60) {
            return minutes + " мин. назад";
        } else if (hours < 24) {
            return hours + " ч. назад";
        } else {
            return days + " дн. назад";
        }
    }
}

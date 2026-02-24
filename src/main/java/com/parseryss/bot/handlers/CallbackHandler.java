package com.parseryss.bot.handlers;

import com.parseryss.bot.TelegramBotService;
import com.parseryss.bot.UserState;
import com.parseryss.bot.keyboards.*;
import com.parseryss.model.Query;
import com.parseryss.model.User;
import com.parseryss.model.UserSettings;
import com.parseryss.service.ParserManager;
import com.parseryss.storage.QueryRepository;
import com.parseryss.storage.UserRepository;
import com.parseryss.storage.UserSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;

/**
 * Обработчик callback запросов (нажатий на кнопки)
 */
public class CallbackHandler {
    private static final Logger logger = LoggerFactory.getLogger(CallbackHandler.class);
    
    private final TelegramBotService bot;
    private final UserRepository userRepository;
    private final UserSettingsRepository settingsRepository;
    private final QueryRepository queryRepository;
    private final ParserManager parserManager;
    
    public CallbackHandler(
            TelegramBotService bot,
            UserRepository userRepository,
            UserSettingsRepository settingsRepository,
            QueryRepository queryRepository,
            ParserManager parserManager
    ) {
        this.bot = bot;
        this.userRepository = userRepository;
        this.settingsRepository = settingsRepository;
        this.queryRepository = queryRepository;
        this.parserManager = parserManager;
    }
    
    public void handle(CallbackQuery callback) {
        String data = callback.getData();
        long userId = callback.getFrom().getId();
        long chatId = callback.getMessage().getChatId();
        int messageId = callback.getMessage().getMessageId();
        
        logger.info("📲 Callback от {}: {}", userId, data);
        
        try {
            // Парсер старт/стоп
            if (data.equals("parser_start")) {
                handleParserStart(callback, userId, chatId);
            } else if (data.equals("parser_stop")) {
                handleParserStop(callback, userId, chatId);
            }
            // Главное меню
            else if (data.equals("main_menu")) {
                handleMainMenu(callback, userId, chatId, messageId);
            }
            // Настройки
            else if (data.equals("settings_main")) {
                handleSettingsMain(callback, userId, chatId, messageId);
            } else if (data.equals("settings_platforms")) {
                handleSettingsPlatforms(callback, userId, chatId, messageId);
            } else if (data.startsWith("platform_toggle_")) {
                handlePlatformToggle(callback, userId, chatId, messageId, data);
            } else if (data.equals("settings_filters")) {
                handleSettingsFilters(callback, userId, chatId, messageId);
            } else if (data.equals("filter_set_min_price")) {
                handleSetMinPrice(callback, userId, chatId);
            } else if (data.equals("filter_set_max_price")) {
                handleSetMaxPrice(callback, userId, chatId);
            }
            // Запросы
            else if (data.equals("queries_list")) {
                handleQueriesList(callback, userId, chatId, messageId);
            } else if (data.equals("query_add")) {
                handleQueryAdd(callback, userId, chatId);
            } else if (data.startsWith("query_delete_")) {
                handleQueryDelete(callback, userId, chatId, messageId, data);
            }
            // Админ панель
            else if (data.equals("admin_panel")) {
                handleAdminPanel(callback, userId, chatId, messageId);
            } else if (data.equals("admin_users")) {
                handleAdminUsers(callback, userId, chatId, messageId);
            } else if (data.equals("admin_add_user")) {
                handleAdminAddUser(callback, userId, chatId);
            } else if (data.startsWith("admin_user_")) {
                handleAdminUserDetails(callback, userId, chatId, messageId, data);
            } else if (data.startsWith("admin_toggle_platform_")) {
                handleAdminTogglePlatform(callback, userId, chatId, messageId, data);
            } else if (data.startsWith("admin_delete_user_")) {
                handleAdminDeleteUser(callback, userId, chatId, messageId, data);
            }
            // Управление подпиской
            else if (data.startsWith("admin_sub_days_")) {
                handleAdminSubDays(callback, userId, chatId, data);
            } else if (data.startsWith("admin_sub_lifetime_")) {
                handleAdminSubLifetime(callback, userId, chatId, messageId, data);
            } else if (data.startsWith("admin_sub_disable_")) {
                handleAdminSubDisable(callback, userId, chatId, messageId, data);
            }
            
            // Отправляем ответ на callback
            answerCallback(callback.getId(), null);
            
        } catch (Exception e) {
            logger.error("❌ Ошибка обработки callback: {}", e.getMessage(), e);
            answerCallback(callback.getId(), "❌ Произошла ошибка");
        }
    }
    
    private void handleParserStart(CallbackQuery callback, long userId, long chatId) {
        User user = userRepository.getById(userId);
        
        if (user == null || !user.isSubscriptionActive()) {
            answerCallback(callback.getId(), "❌ Подписка не активна");
            return;
        }
        
        if (parserManager.isRunning(userId)) {
            answerCallback(callback.getId(), "⚠️ Парсер уже запущен");
            return;
        }
        
        parserManager.startParsing(userId);
        answerCallback(callback.getId(), "✅ Парсер запущен!");
        
        // Обновляем главное меню
        handleMainMenu(callback, userId, chatId, callback.getMessage().getMessageId());
    }
    
    private void handleParserStop(CallbackQuery callback, long userId, long chatId) {
        if (!parserManager.isRunning(userId)) {
            answerCallback(callback.getId(), "⚠️ Парсер не запущен");
            return;
        }
        
        parserManager.stopParsing(userId);
        answerCallback(callback.getId(), "⏸ Парсер остановлен");
        
        // Обновляем главное меню
        handleMainMenu(callback, userId, chatId, callback.getMessage().getMessageId());
    }
    
    private void handleMainMenu(CallbackQuery callback, long userId, long chatId, int messageId) {
        User user = userRepository.getById(userId);
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
        
        EditMessageText editMessage = new EditMessageText();
        editMessage.setChatId(String.valueOf(chatId));
        editMessage.setMessageId(messageId);
        editMessage.setText(text.toString());
        editMessage.enableHtml(true);
        editMessage.setReplyMarkup(MainKeyboard.getKeyboard(isRunning, user.isAdmin()));
        
        try {
            bot.execute(editMessage);
        } catch (TelegramApiException e) {
            logger.error("❌ Ошибка обновления сообщения: {}", e.getMessage());
        }
    }
    
    private void handleSettingsMain(CallbackQuery callback, long userId, long chatId, int messageId) {
        UserSettings settings = settingsRepository.getByUserId(userId);
        
        StringBuilder text = new StringBuilder();
        text.append("⚙️ <b>Настройки парсера</b>\n\n");
        
        // Платформы
        text.append("🌐 <b>Активные платформы:</b>\n");
        text.append("  • Avito: ").append(settings.isPlatformEnabled("avito") ? "✅" : "❌").append("\n");
        text.append("  • Mercari: ").append(settings.isPlatformEnabled("mercari") ? "✅" : "❌").append("\n");
        text.append("  • Goofish: ").append(settings.isPlatformEnabled("goofish") ? "✅" : "❌").append("\n\n");
        
        // Фильтры
        text.append("🔧 <b>Фильтры:</b>\n");
        text.append("  • Мин. цена: ").append(settings.getMinPrice() > 0 ? settings.getMinPrice() + " ₽" : "Не установлена").append("\n");
        text.append("  • Макс. цена: ").append(settings.getMaxPrice() > 0 ? settings.getMaxPrice() + " ₽" : "Не установлена").append("\n");
        text.append("  • Макс. возраст: ").append(settings.getMaxAgeMinutes() > 0 ? settings.getMaxAgeMinutes() + " мин" : "Не установлен").append("\n\n");
        
        // Интервал
        text.append("⏱ <b>Интервал проверки:</b> ").append(settings.getCheckIntervalSeconds()).append(" сек\n\n");
        text.append("📄 <b>Макс. страниц:</b> ").append(settings.getMaxPages());
        
        EditMessageText editMessage = new EditMessageText();
        editMessage.setChatId(String.valueOf(chatId));
        editMessage.setMessageId(messageId);
        editMessage.setText(text.toString());
        editMessage.enableHtml(true);
        editMessage.setReplyMarkup(SettingsKeyboard.getMainSettingsKeyboard());
        
        try {
            bot.execute(editMessage);
        } catch (TelegramApiException e) {
            logger.error("❌ Ошибка обновления сообщения: {}", e.getMessage());
        }
    }
    
    private void handleSettingsPlatforms(CallbackQuery callback, long userId, long chatId, int messageId) {
        UserSettings settings = settingsRepository.getByUserId(userId);
        
        StringBuilder text = new StringBuilder();
        text.append("🌐 <b>Выбор платформ</b>\n\n");
        text.append("Нажмите на платформу, чтобы включить/выключить её:\n\n");
        text.append("✅ - платформа включена\n");
        text.append("❌ - платформа выключена");
        
        EditMessageText editMessage = new EditMessageText();
        editMessage.setChatId(String.valueOf(chatId));
        editMessage.setMessageId(messageId);
        editMessage.setText(text.toString());
        editMessage.enableHtml(true);
        editMessage.setReplyMarkup(SettingsKeyboard.getPlatformsKeyboard(
            settings.isPlatformEnabled("avito"),
            settings.isPlatformEnabled("mercari"),
            settings.isPlatformEnabled("goofish")
        ));
        
        try {
            bot.execute(editMessage);
        } catch (TelegramApiException e) {
            logger.error("❌ Ошибка обновления сообщения: {}", e.getMessage());
        }
    }
    
    private void handlePlatformToggle(CallbackQuery callback, long userId, long chatId, int messageId, String data) {
        String platform = data.replace("platform_toggle_", "");
        UserSettings settings = settingsRepository.getByUserId(userId);
        
        // Проверяем доступ пользователя к платформе
        User user = userRepository.getById(userId);
        if (!user.hasAccessToPlatform(platform)) {
            answerCallback(callback.getId(), "❌ У вас нет доступа к этой платформе");
            return;
        }
        
        // Переключаем платформу
        if (settings.isPlatformEnabled(platform)) {
            settings.disablePlatform(platform);
            answerCallback(callback.getId(), "❌ " + platform + " выключена");
        } else {
            settings.enablePlatform(platform);
            answerCallback(callback.getId(), "✅ " + platform + " включена");
        }
        
        settingsRepository.save(settings);
        
        // Обновляем меню
        handleSettingsPlatforms(callback, userId, chatId, messageId);
    }
    
    private void handleQueriesList(CallbackQuery callback, long userId, long chatId, int messageId) {
        var queries = queryRepository.getByUserId(userId);
        
        StringBuilder text = new StringBuilder();
        text.append("🔍 <b>Ваши запросы</b>\n\n");
        
        if (queries.isEmpty()) {
            text.append("❌ У вас пока нет запросов.\n");
            text.append("Нажмите '➕ Добавить' чтобы создать первый запрос.");
        } else {
            for (int i = 0; i < queries.size(); i++) {
                Query q = queries.get(i);
                text.append(String.format("%d. <b>%s</b>\n", i + 1, q.getQueryText()));
                text.append(String.format("   Статус: %s\n\n", q.isActive() ? "✅ Активен" : "❌ Неактивен"));
            }
        }
        
        EditMessageText editMessage = new EditMessageText();
        editMessage.setChatId(String.valueOf(chatId));
        editMessage.setMessageId(messageId);
        editMessage.setText(text.toString());
        editMessage.enableHtml(true);
        editMessage.setReplyMarkup(QueryKeyboard.getQueriesListKeyboard(queries));
        
        try {
            bot.execute(editMessage);
        } catch (TelegramApiException e) {
            logger.error("❌ Ошибка обновления сообщения: {}", e.getMessage());
        }
    }
    
    private void handleQueryAdd(CallbackQuery callback, long userId, long chatId) {
        UserState.setState(userId, UserState.State.WAITING_FOR_QUERY);
        answerCallback(callback.getId(), "📝 Отправьте текст запроса");
        bot.sendMessage(chatId, "📝 Отправьте текст запроса для поиска товаров:\n\nНапример: iPhone 15, MacBook Pro, Ноутбук");
    }
    
    private void handleQueryDelete(CallbackQuery callback, long userId, long chatId, int messageId, String data) {
        String queryId = data.replace("query_delete_", "");
        
        Query query = queryRepository.getQuery(userId, queryId);
        if (query == null) {
            answerCallback(callback.getId(), "❌ Запрос не найден");
            return;
        }
        
        queryRepository.delete(userId, queryId);
        answerCallback(callback.getId(), "🗑 Запрос удалён");
        
        // Обновляем список
        handleQueriesList(callback, userId, chatId, messageId);
    }
    
    private void handleAdminPanel(CallbackQuery callback, long userId, long chatId, int messageId) {
        User user = userRepository.getById(userId);
        
        if (!user.isAdmin()) {
            answerCallback(callback.getId(), "❌ Доступ запрещён");
            return;
        }
        
        var users = userRepository.getAll();
        
        StringBuilder text = new StringBuilder();
        text.append("👑 <b>Админ панель</b>\n\n");
        text.append("👥 Всего пользователей: ").append(users.size()).append("\n");
        
        long activeSubscriptions = users.stream()
                .filter(User::isSubscriptionActive)
                .count();
        text.append("✅ Активных подписок: ").append(activeSubscriptions).append("\n\n");
        
        text.append("📊 <b>Последние пользователи:</b>\n");
        
        int count = Math.min(5, users.size());
        for (int i = 0; i < count; i++) {
            User u = users.get(i);
            text.append(String.format("%d. %s (ID: %d)\n", i + 1, u.getUsername(), u.getId()));
            text.append("   Подписка: ").append(u.isSubscriptionActive() ? "✅" : "❌").append("\n");
        }
        
        EditMessageText editMessage = new EditMessageText();
        editMessage.setChatId(String.valueOf(chatId));
        editMessage.setMessageId(messageId);
        editMessage.setText(text.toString());
        editMessage.enableHtml(true);
        editMessage.setReplyMarkup(AdminKeyboard.getMainAdminKeyboard());
        
        try {
            bot.execute(editMessage);
        } catch (TelegramApiException e) {
            logger.error("❌ Ошибка обновления сообщения: {}", e.getMessage());
        }
    }
    
    private void answerCallback(String callbackId, String text) {
        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackId);
        if (text != null) {
            answer.setText(text);
            answer.setShowAlert(false);
        }
        
        try {
            bot.execute(answer);
        } catch (TelegramApiException e) {
            logger.error("❌ Ошибка ответа на callback: {}", e.getMessage());
        }
    }
    
    private void handleSettingsFilters(CallbackQuery callback, long userId, long chatId, int messageId) {
        UserSettings settings = settingsRepository.getSettings(userId);
        if (settings == null) {
            settings = new UserSettings(userId);
        }
        
        StringBuilder text = new StringBuilder();
        text.append("🔧 <b>Фильтры</b>\n\n");
        text.append("💰 Минимальная цена: ");
        text.append(settings.getMinPrice() > 0 ? settings.getMinPrice() : "не установлена").append("\n");
        text.append("💰 Максимальная цена: ");
        text.append(settings.getMaxPrice() > 0 ? settings.getMaxPrice() : "не установлена").append("\n");
        
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        // Минимальная цена
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton minPriceBtn = new InlineKeyboardButton();
        minPriceBtn.setText("💵 Установить мин. цену");
        minPriceBtn.setCallbackData("filter_set_min_price");
        row1.add(minPriceBtn);
        keyboard.add(row1);
        
        // Максимальная цена
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton maxPriceBtn = new InlineKeyboardButton();
        maxPriceBtn.setText("💵 Установить макс. цену");
        maxPriceBtn.setCallbackData("filter_set_max_price");
        row2.add(maxPriceBtn);
        keyboard.add(row2);
        
        // Назад
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        InlineKeyboardButton backBtn = new InlineKeyboardButton();
        backBtn.setText("◀️ Назад");
        backBtn.setCallbackData("settings_main");
        row3.add(backBtn);
        keyboard.add(row3);
        
        markup.setKeyboard(keyboard);
        
        EditMessageText editMessage = new EditMessageText();
        editMessage.setChatId(String.valueOf(chatId));
        editMessage.setMessageId(messageId);
        editMessage.setText(text.toString());
        editMessage.enableHtml(true);
        editMessage.setReplyMarkup(markup);
        
        try {
            bot.execute(editMessage);
        } catch (TelegramApiException e) {
            logger.error("❌ Ошибка обновления сообщения: {}", e.getMessage());
        }
    }
    
    private void handleSetMinPrice(CallbackQuery callback, long userId, long chatId) {
        UserState.setState(userId, UserState.State.WAITING_FOR_MIN_PRICE);
        answerCallback(callback.getId(), "💵 Отправьте минимальную цену");
        bot.sendMessage(chatId, "💵 Отправьте минимальную цену (0 для отмены):");
    }
    
    private void handleSetMaxPrice(CallbackQuery callback, long userId, long chatId) {
        UserState.setState(userId, UserState.State.WAITING_FOR_MAX_PRICE);
        answerCallback(callback.getId(), "💵 Отправьте максимальную цену");
        bot.sendMessage(chatId, "💵 Отправьте максимальную цену (0 для отмены):");
    }
    
    // ==================== АДМИН ПАНЕЛЬ ====================
    
    private void handleAdminUsers(CallbackQuery callback, long userId, long chatId, int messageId) {
        List<User> users = userRepository.getAll();
        
        StringBuilder text = new StringBuilder();
        text.append("👥 <b>Список пользователей</b>\n\n");
        
        if (users.isEmpty()) {
            text.append("Пользователей нет");
        } else {
            for (User u : users) {
                text.append(String.format("👤 %s (ID: %d)\n", u.getUsername(), u.getId()));
                text.append("   Подписка: ").append(u.isSubscriptionActive() ? "✅" : "❌").append("\n");
            }
        }
        
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        // Кнопки пользователей
        for (User u : users) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton btn = new InlineKeyboardButton();
            btn.setText(String.format("%s %s", u.isSubscriptionActive() ? "✅" : "❌", u.getUsername()));
            btn.setCallbackData("admin_user_" + u.getId());
            row.add(btn);
            keyboard.add(row);
        }
        
        // Назад
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton backBtn = new InlineKeyboardButton();
        backBtn.setText("◀️ Назад");
        backBtn.setCallbackData("admin_panel");
        backRow.add(backBtn);
        keyboard.add(backRow);
        
        markup.setKeyboard(keyboard);
        
        EditMessageText editMessage = new EditMessageText();
        editMessage.setChatId(String.valueOf(chatId));
        editMessage.setMessageId(messageId);
        editMessage.setText(text.toString());
        editMessage.enableHtml(true);
        editMessage.setReplyMarkup(markup);
        
        try {
            bot.execute(editMessage);
        } catch (TelegramApiException e) {
            logger.error("❌ Ошибка обновления сообщения: {}", e.getMessage());
        }
    }
    
    private void handleAdminAddUser(CallbackQuery callback, long userId, long chatId) {
        UserState.setState(userId, UserState.State.WAITING_FOR_USER_ID);
        answerCallback(callback.getId(), "👤 Отправьте ID пользователя");
        bot.sendMessage(chatId, "👤 Отправьте ID пользователя для добавления:");
    }
    
    private void handleAdminUserDetails(CallbackQuery callback, long userId, long chatId, int messageId, String data) {
        long targetUserId = Long.parseLong(data.replace("admin_user_", ""));
        User targetUser = userRepository.getById(targetUserId);
        
        if (targetUser == null) {
            answerCallback(callback.getId(), "❌ Пользователь не найден");
            return;
        }
        
        StringBuilder text = new StringBuilder();
        text.append("👤 <b>Пользователь: ").append(targetUser.getUsername()).append("</b>\n\n");
        text.append("🆔 ID: ").append(targetUser.getId()).append("\n");
        text.append("📊 Подписка: ").append(targetUser.isSubscriptionActive() ? "✅ Активна" : "❌ Неактивна").append("\n");
        
        if (targetUser.getSubscriptionEnd() != null) {
            text.append("📅 До: ").append(targetUser.getSubscriptionEnd()).append("\n");
        }
        
        text.append("\n🌐 <b>Доступные платформы:</b>\n");
        text.append("Mercari: ").append(targetUser.hasAccessToPlatform("mercari") ? "✅" : "❌").append("\n");
        text.append("Avito: ").append(targetUser.hasAccessToPlatform("avito") ? "✅" : "❌").append("\n");
        text.append("Goofish: ").append(targetUser.hasAccessToPlatform("goofish") ? "✅" : "❌").append("\n");
        
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        // Переключатели платформ
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton mercariBtn = new InlineKeyboardButton();
        mercariBtn.setText((targetUser.hasAccessToPlatform("mercari") ? "✅" : "❌") + " Mercari");
        mercariBtn.setCallbackData("admin_toggle_platform_" + targetUserId + "_mercari");
        row1.add(mercariBtn);
        keyboard.add(row1);
        
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton avitoBtn = new InlineKeyboardButton();
        avitoBtn.setText((targetUser.hasAccessToPlatform("avito") ? "✅" : "❌") + " Avito");
        avitoBtn.setCallbackData("admin_toggle_platform_" + targetUserId + "_avito");
        row2.add(avitoBtn);
        keyboard.add(row2);
        
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        InlineKeyboardButton goofishBtn = new InlineKeyboardButton();
        goofishBtn.setText((targetUser.hasAccessToPlatform("goofish") ? "✅" : "❌") + " Goofish");
        goofishBtn.setCallbackData("admin_toggle_platform_" + targetUserId + "_goofish");
        row3.add(goofishBtn);
        keyboard.add(row3);
        
        // Разделитель - Управление подпиской
        List<InlineKeyboardButton> subRow1 = new ArrayList<>();
        InlineKeyboardButton subDaysBtn = new InlineKeyboardButton();
        subDaysBtn.setText("📅 Подписка на N дней");
        subDaysBtn.setCallbackData("admin_sub_days_" + targetUserId);
        subRow1.add(subDaysBtn);
        keyboard.add(subRow1);
        
        List<InlineKeyboardButton> subRow2 = new ArrayList<>();
        InlineKeyboardButton subLifetimeBtn = new InlineKeyboardButton();
        subLifetimeBtn.setText("♾️ Бессрочная подписка");
        subLifetimeBtn.setCallbackData("admin_sub_lifetime_" + targetUserId);
        subRow2.add(subLifetimeBtn);
        keyboard.add(subRow2);
        
        List<InlineKeyboardButton> subRow3 = new ArrayList<>();
        InlineKeyboardButton subDisableBtn = new InlineKeyboardButton();
        subDisableBtn.setText("❌ Отключить подписку");
        subDisableBtn.setCallbackData("admin_sub_disable_" + targetUserId);
        subRow3.add(subDisableBtn);
        keyboard.add(subRow3);
        
        // Удалить пользователя
        List<InlineKeyboardButton> row4 = new ArrayList<>();
        InlineKeyboardButton deleteBtn = new InlineKeyboardButton();
        deleteBtn.setText("🗑 Удалить пользователя");
        deleteBtn.setCallbackData("admin_delete_user_" + targetUserId);
        row4.add(deleteBtn);
        keyboard.add(row4);
        
        // Назад
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton backBtn = new InlineKeyboardButton();
        backBtn.setText("◀️ Назад");
        backBtn.setCallbackData("admin_users");
        backRow.add(backBtn);
        keyboard.add(backRow);
        
        markup.setKeyboard(keyboard);
        
        EditMessageText editMessage = new EditMessageText();
        editMessage.setChatId(String.valueOf(chatId));
        editMessage.setMessageId(messageId);
        editMessage.setText(text.toString());
        editMessage.enableHtml(true);
        editMessage.setReplyMarkup(markup);
        
        try {
            bot.execute(editMessage);
        } catch (TelegramApiException e) {
            logger.error("❌ Ошибка обновления сообщения: {}", e.getMessage());
        }
    }
    
    private void handleAdminTogglePlatform(CallbackQuery callback, long userId, long chatId, int messageId, String data) {
        String[] parts = data.replace("admin_toggle_platform_", "").split("_");
        long targetUserId = Long.parseLong(parts[0]);
        String platform = parts[1];
        
        User targetUser = userRepository.getById(targetUserId);
        if (targetUser == null) {
            answerCallback(callback.getId(), "❌ Пользователь не найден");
            return;
        }
        
        // Переключаем доступ к платформе
        if (targetUser.hasAccessToPlatform(platform)) {
            targetUser.removePlatform(platform);
        } else {
            targetUser.addPlatform(platform);
        }
        
        userRepository.save(targetUser);
        
        // Обновляем сообщение
        handleAdminUserDetails(callback, userId, chatId, messageId, "admin_user_" + targetUserId);
    }
    
    private void handleAdminDeleteUser(CallbackQuery callback, long userId, long chatId, int messageId, String data) {
        long targetUserId = Long.parseLong(data.replace("admin_delete_user_", ""));
        
        User targetUser = userRepository.getById(targetUserId);
        if (targetUser == null) {
            answerCallback(callback.getId(), "❌ Пользователь не найден");
            return;
        }
        
        userRepository.delete(targetUserId);
        answerCallback(callback.getId(), "✅ Пользователь удален");
        
        // Возвращаемся к списку пользователей
        handleAdminUsers(callback, userId, chatId, messageId);
    }
    
    // ==================== УПРАВЛЕНИЕ ПОДПИСКОЙ ====================
    
    private void handleAdminSubDays(CallbackQuery callback, long userId, long chatId, String data) {
        long targetUserId = Long.parseLong(data.replace("admin_sub_days_", ""));
        
        User targetUser = userRepository.getById(targetUserId);
        if (targetUser == null) {
            answerCallback(callback.getId(), "❌ Пользователь не найден");
            return;
        }
        
        // Сохраняем целевого пользователя и устанавливаем состояние
        UserState.setSubscriptionTargetUser(userId, targetUserId);
        UserState.setState(userId, UserState.State.WAITING_FOR_SUBSCRIPTION_DAYS);
        
        answerCallback(callback.getId(), "📅 Введите количество дней");
        bot.sendMessage(chatId, "📅 Введите количество дней подписки для пользователя " + targetUser.getUsername() + ":");
    }
    
    private void handleAdminSubLifetime(CallbackQuery callback, long userId, long chatId, int messageId, String data) {
        long targetUserId = Long.parseLong(data.replace("admin_sub_lifetime_", ""));
        
        User targetUser = userRepository.getById(targetUserId);
        if (targetUser == null) {
            answerCallback(callback.getId(), "❌ Пользователь не найден");
            return;
        }
        
        // Устанавливаем бессрочную подписку
        targetUser.setLifetimeSubscription(true);
        targetUser.setSubscriptionExpiry(null);
        userRepository.save(targetUser);
        
        answerCallback(callback.getId(), "✅ Бессрочная подписка активирована");
        
        // Обновляем страницу пользователя
        handleAdminUserDetails(callback, userId, chatId, messageId, "admin_user_" + targetUserId);
    }
    
    private void handleAdminSubDisable(CallbackQuery callback, long userId, long chatId, int messageId, String data) {
        long targetUserId = Long.parseLong(data.replace("admin_sub_disable_", ""));
        
        User targetUser = userRepository.getById(targetUserId);
        if (targetUser == null) {
            answerCallback(callback.getId(), "❌ Пользователь не найден");
            return;
        }
        
        // Отключаем подписку
        targetUser.setLifetimeSubscription(false);
        targetUser.setSubscriptionExpiry(null);
        userRepository.save(targetUser);
        
        answerCallback(callback.getId(), "❌ Подписка отключена");
        
        // Обновляем страницу пользователя
        handleAdminUserDetails(callback, userId, chatId, messageId, "admin_user_" + targetUserId);
    }
}

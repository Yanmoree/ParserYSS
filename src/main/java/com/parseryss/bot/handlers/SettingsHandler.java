package com.parseryss.bot.handlers;

import com.parseryss.bot.TelegramBotService;
import com.parseryss.bot.keyboards.SettingsKeyboard;
import com.parseryss.model.UserSettings;
import com.parseryss.storage.UserSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

/**
 * Обработчик настроек
 */
public class SettingsHandler {
    private static final Logger logger = LoggerFactory.getLogger(SettingsHandler.class);
    
    private final TelegramBotService bot;
    private final UserSettingsRepository settingsRepository;
    
    public SettingsHandler(
            TelegramBotService bot,
            UserSettingsRepository settingsRepository
    ) {
        this.bot = bot;
        this.settingsRepository = settingsRepository;
    }
    
    public void showSettings(long chatId, long userId) {
        UserSettings settings = settingsRepository.getByUserId(userId);
        
        if (settings == null) {
            bot.sendMessage(chatId, "❌ Настройки не найдены");
            return;
        }
        
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
        
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text.toString());
        message.enableHtml(true);
        message.setReplyMarkup(SettingsKeyboard.getMainSettingsKeyboard());
        
        bot.sendMessageWithKeyboard(message);
    }
}

package com.parseryss.bot.keyboards;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Главная клавиатура бота
 */
public class MainKeyboard {
    
    public static InlineKeyboardMarkup getKeyboard(boolean isRunning, boolean isAdmin) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        // Кнопка Старт/Стоп
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton startStopBtn = new InlineKeyboardButton();
        if (isRunning) {
            startStopBtn.setText("⏸ Остановить парсер");
            startStopBtn.setCallbackData("parser_stop");
        } else {
            startStopBtn.setText("▶️ Запустить парсер");
            startStopBtn.setCallbackData("parser_start");
        }
        row1.add(startStopBtn);
        keyboard.add(row1);
        
        // Настройки и Запросы
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        
        InlineKeyboardButton settingsBtn = new InlineKeyboardButton();
        settingsBtn.setText("⚙️ Настройки");
        settingsBtn.setCallbackData("settings_main");
        row2.add(settingsBtn);
        
        InlineKeyboardButton queriesBtn = new InlineKeyboardButton();
        queriesBtn.setText("🔍 Запросы");
        queriesBtn.setCallbackData("queries_list");
        row2.add(queriesBtn);
        
        keyboard.add(row2);
        
        // Админ панель (только для админов)
        if (isAdmin) {
            List<InlineKeyboardButton> row3 = new ArrayList<>();
            InlineKeyboardButton adminBtn = new InlineKeyboardButton();
            adminBtn.setText("👑 Админ панель");
            adminBtn.setCallbackData("admin_panel");
            row3.add(adminBtn);
            keyboard.add(row3);
        }
        
        markup.setKeyboard(keyboard);
        return markup;
    }
}

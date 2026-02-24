package com.parseryss.bot.keyboards;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Клавиатура настроек
 */
public class SettingsKeyboard {
    
    public static InlineKeyboardMarkup getMainSettingsKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        // Платформы
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton platformsBtn = new InlineKeyboardButton();
        platformsBtn.setText("🌐 Платформы");
        platformsBtn.setCallbackData("settings_platforms");
        row1.add(platformsBtn);
        keyboard.add(row1);
        
        // Фильтры
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton filtersBtn = new InlineKeyboardButton();
        filtersBtn.setText("🔧 Фильтры");
        filtersBtn.setCallbackData("settings_filters");
        row2.add(filtersBtn);
        keyboard.add(row2);
        
        // Назад
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        InlineKeyboardButton backBtn = new InlineKeyboardButton();
        backBtn.setText("◀️ Назад");
        backBtn.setCallbackData("main_menu");
        row3.add(backBtn);
        keyboard.add(row3);
        
        markup.setKeyboard(keyboard);
        return markup;
    }
    
    public static InlineKeyboardMarkup getPlatformsKeyboard(boolean avitoEnabled, boolean mercariEnabled, boolean goofishEnabled) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        // Avito
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton avitoBtn = new InlineKeyboardButton();
        avitoBtn.setText((avitoEnabled ? "✅" : "❌") + " Avito");
        avitoBtn.setCallbackData("platform_toggle_avito");
        row1.add(avitoBtn);
        keyboard.add(row1);
        
        // Mercari
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton mercariBtn = new InlineKeyboardButton();
        mercariBtn.setText((mercariEnabled ? "✅" : "❌") + " Mercari");
        mercariBtn.setCallbackData("platform_toggle_mercari");
        row2.add(mercariBtn);
        keyboard.add(row2);
        
        // Goofish
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        InlineKeyboardButton goofishBtn = new InlineKeyboardButton();
        goofishBtn.setText((goofishEnabled ? "✅" : "❌") + " Goofish");
        goofishBtn.setCallbackData("platform_toggle_goofish");
        row3.add(goofishBtn);
        keyboard.add(row3);
        
        // Назад
        List<InlineKeyboardButton> row4 = new ArrayList<>();
        InlineKeyboardButton backBtn = new InlineKeyboardButton();
        backBtn.setText("◀️ Назад");
        backBtn.setCallbackData("settings_main");
        row4.add(backBtn);
        keyboard.add(row4);
        
        markup.setKeyboard(keyboard);
        return markup;
    }
}

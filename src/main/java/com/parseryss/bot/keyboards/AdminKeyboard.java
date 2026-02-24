package com.parseryss.bot.keyboards;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Клавиатура админ панели
 */
public class AdminKeyboard {
    
    public static InlineKeyboardMarkup getMainAdminKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        // Управление пользователями
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton usersBtn = new InlineKeyboardButton();
        usersBtn.setText("👥 Управление пользователями");
        usersBtn.setCallbackData("admin_users");
        row1.add(usersBtn);
        keyboard.add(row1);
        
        // Добавить пользователя
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton addUserBtn = new InlineKeyboardButton();
        addUserBtn.setText("➕ Добавить пользователя");
        addUserBtn.setCallbackData("admin_add_user");
        row2.add(addUserBtn);
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
}

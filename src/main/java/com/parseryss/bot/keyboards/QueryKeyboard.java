package com.parseryss.bot.keyboards;

import com.parseryss.model.Query;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Клавиатура запросов
 */
public class QueryKeyboard {
    
    public static InlineKeyboardMarkup getQueriesListKeyboard(List<Query> queries) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        // Кнопки для каждого запроса (удалить)
        for (Query q : queries) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            
            InlineKeyboardButton queryBtn = new InlineKeyboardButton();
            queryBtn.setText(q.getQueryText());
            queryBtn.setCallbackData("query_view_" + q.getId());
            row.add(queryBtn);
            
            InlineKeyboardButton deleteBtn = new InlineKeyboardButton();
            deleteBtn.setText("🗑");
            deleteBtn.setCallbackData("query_delete_" + q.getId());
            row.add(deleteBtn);
            
            keyboard.add(row);
        }
        
        // Добавить новый
        List<InlineKeyboardButton> addRow = new ArrayList<>();
        InlineKeyboardButton addBtn = new InlineKeyboardButton();
        addBtn.setText("➕ Добавить запрос");
        addBtn.setCallbackData("query_add");
        addRow.add(addBtn);
        keyboard.add(addRow);
        
        // Назад
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton backBtn = new InlineKeyboardButton();
        backBtn.setText("◀️ Назад");
        backBtn.setCallbackData("main_menu");
        backRow.add(backBtn);
        keyboard.add(backRow);
        
        markup.setKeyboard(keyboard);
        return markup;
    }
}

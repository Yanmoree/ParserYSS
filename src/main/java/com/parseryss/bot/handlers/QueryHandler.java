package com.parseryss.bot.handlers;

import com.parseryss.bot.TelegramBotService;
import com.parseryss.bot.keyboards.QueryKeyboard;
import com.parseryss.model.Query;
import com.parseryss.storage.QueryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.util.List;

/**
 * Обработчик запросов
 */
public class QueryHandler {
    private static final Logger logger = LoggerFactory.getLogger(QueryHandler.class);
    
    private final TelegramBotService bot;
    private final QueryRepository queryRepository;
    
    public QueryHandler(
            TelegramBotService bot,
            QueryRepository queryRepository
    ) {
        this.bot = bot;
        this.queryRepository = queryRepository;
    }
    
    public void showQueries(long chatId, long userId) {
        List<Query> queries = queryRepository.getByUserId(userId);
        
        StringBuilder text = new StringBuilder();
        text.append("🔍 <b>Ваши запросы</b>\n\n");
        
        if (queries.isEmpty()) {
            text.append("❌ У вас пока нет запросов.\n");
            text.append("Нажмите '➕ Добавить' чтобы создать первый запрос.");
        } else {
            for (int i = 0; i < queries.size(); i++) {
                Query q = queries.get(i);
                text.append(String.format("%d. <b>%s</b>\n", i + 1, q.getQueryText()));
                text.append(String.format("   ID: <code>%s</code>\n", q.getId()));
                text.append(String.format("   Статус: %s\n\n", q.isActive() ? "✅ Активен" : "❌ Неактивен"));
            }
        }
        
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text.toString());
        message.enableHtml(true);
        message.setReplyMarkup(QueryKeyboard.getQueriesListKeyboard(queries));
        
        bot.sendMessageWithKeyboard(message);
    }
}

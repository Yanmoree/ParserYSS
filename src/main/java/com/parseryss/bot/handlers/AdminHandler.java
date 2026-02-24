package com.parseryss.bot.handlers;

import com.parseryss.bot.TelegramBotService;
import com.parseryss.bot.keyboards.AdminKeyboard;
import com.parseryss.model.User;
import com.parseryss.storage.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.util.List;

/**
 * Обработчик админ панели
 */
public class AdminHandler {
    private static final Logger logger = LoggerFactory.getLogger(AdminHandler.class);
    
    private final TelegramBotService bot;
    private final UserRepository userRepository;
    
    public AdminHandler(
            TelegramBotService bot,
            UserRepository userRepository
    ) {
        this.bot = bot;
        this.userRepository = userRepository;
    }
    
    public void showAdminPanel(long chatId) {
        List<User> users = userRepository.getAll();
        
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
        
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text.toString());
        message.enableHtml(true);
        message.setReplyMarkup(AdminKeyboard.getMainAdminKeyboard());
        
        bot.sendMessageWithKeyboard(message);
    }
}

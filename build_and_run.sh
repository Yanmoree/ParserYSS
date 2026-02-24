#!/bin/bash

echo "🔨 Сборка и запуск ParserYSS..."
echo ""

# Переходим в директорию проекта
cd "$(dirname "$0")"

# Компилируем проект
echo "📦 Компиляция проекта..."
mvn clean compile

if [ $? -ne 0 ]; then
    echo "❌ Ошибка компиляции!"
    echo "Проверьте логи выше для деталей."
    exit 1
fi

echo "✅ Компиляция успешна!"
echo ""

# Создаем необходимые директории
mkdir -p data
mkdir -p logs

echo "✅ Запуск бота..."
echo "📱 Bot: @yss_parser_bot"
echo "🔑 Token: 8291586731:AAFIjnerJdg0IzwSm2JgNkVfrOc0rPyHWW8"
echo ""
echo "Для остановки нажмите Ctrl+C"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Запускаем приложение
mvn exec:java -Dexec.mainClass="com.parseryss.Main"

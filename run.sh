#!/bin/bash

echo "🚀 Запуск ParserYSS..."
echo ""

# Переходим в директорию проекта
cd "$(dirname "$0")"

# Проверяем наличие скомпилированных классов
if [ ! -d "target/classes" ]; then
    echo "📦 Компиляция проекта..."
    mvn clean compile
    if [ $? -ne 0 ]; then
        echo "❌ Ошибка компиляции!"
        exit 1
    fi
fi

# Создаем необходимые директории
mkdir -p data
mkdir -p logs

echo "✅ Запуск бота..."
echo "📱 Bot: @multiparse_bot"
echo "🔑 Token: 8538627254:AAE_niIKdyWgM69JSrto7tKntao5vS7qj5g"
echo ""
echo "Для остановки нажмите Ctrl+C"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Запускаем приложение
mvn exec:java -Dexec.mainClass="com.parseryss.Main" -q

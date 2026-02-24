#!/bin/bash
cd /Users/yanmore/IdeaProjects/ParserYSS
echo "🔨 Быстрая компиляция..."
mvn compile -q
if [ $? -eq 0 ]; then
    echo "✅ Компиляция успешна!"
else
    echo "❌ Ошибка компиляции"
fi

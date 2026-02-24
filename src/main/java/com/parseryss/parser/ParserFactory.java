package com.parseryss.parser;

import com.parseryss.storage.ParserStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Фабрика для создания парсеров
 */
public class ParserFactory {
    private static final Logger logger = LoggerFactory.getLogger(ParserFactory.class);
    
    private final ParserStateRepository stateRepository;
    private final Map<String, SiteParser> parsers;
    
    public ParserFactory(ParserStateRepository stateRepository) {
        this.stateRepository = stateRepository;
        this.parsers = new HashMap<>();
        initializeParsers();
    }
    
    private void initializeParsers() {
        parsers.put("avito", new AvitoApiParser(stateRepository));
        parsers.put("mercari", new MercariParser(stateRepository));
        parsers.put("goofish", new GoofishParser(stateRepository));
        
        logger.info("✅ Инициализировано {} парсеров: {}", parsers.size(), parsers.keySet());
    }
    
    /**
     * Получить парсер по имени платформы
     */
    public SiteParser getParser(String platform) {
        SiteParser parser = parsers.get(platform.toLowerCase());
        
        if (parser == null) {
            logger.warn("⚠️ Парсер для платформы '{}' не найден", platform);
        }
        
        return parser;
    }
    
    /**
     * Проверить, поддерживается ли платформа
     */
    public boolean isSupported(String platform) {
        return parsers.containsKey(platform.toLowerCase());
    }
    
    /**
     * Получить список поддерживаемых платформ
     */
    public String[] getSupportedPlatforms() {
        return parsers.keySet().toArray(new String[0]);
    }
}

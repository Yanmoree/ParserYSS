package com.parseryss.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.util.Properties;

public class Config {
    private static final Logger logger = LoggerFactory.getLogger(Config.class);
    private static final Properties properties = new Properties();
    private static final String CONFIG_FILE = "config.properties";
    private static volatile boolean isLoaded = false;

    static {
        synchronized (Config.class) {
            if (!isLoaded) {
                loadProperties();
                isLoaded = true;
            }
        }
    }

    private static void loadProperties() {
        File externalConfig = new File(CONFIG_FILE);
        if (externalConfig.exists()) {
            try (InputStream input = new FileInputStream(externalConfig)) {
                properties.load(new InputStreamReader(input, "UTF-8"));
                logger.info("Loaded configuration from: {}", CONFIG_FILE);
                return;
            } catch (IOException e) {
                logger.warn("Failed to load external config: {}", e.getMessage());
            }
        }

        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input != null) {
                properties.load(new InputStreamReader(input, "UTF-8"));
                logger.info("Loaded configuration from resources");
            } else {
                setDefaults();
                logger.warn("Config file not found, using defaults");
            }
        } catch (IOException e) {
            setDefaults();
            logger.error("Error loading config: {}", e.getMessage());
        }
    }

    private static void setDefaults() {
        properties.setProperty("cookie.auto.update", "true");
        properties.setProperty("cookie.update.interval.minutes", "120");
        properties.setProperty("cookie.dynamic.enabled", "true");
        properties.setProperty("api.goofish.delay.between.requests", "5000");
    }

    public static int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(properties.getProperty(key, String.valueOf(defaultValue)).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        String value = properties.getProperty(key, String.valueOf(defaultValue));
        return value != null && (value.trim().toLowerCase().matches("true|yes|1"));
    }

    public static boolean isDynamicCookiesEnabled() {
        return getBoolean("cookie.dynamic.enabled", true);
    }

    public static boolean getCookieAutoUpdate() {
        return getBoolean("cookie.auto.update", true);
    }

    public static int getCookieUpdateInterval() {
        return getInt("cookie.update.interval.minutes", 120);
    }
}

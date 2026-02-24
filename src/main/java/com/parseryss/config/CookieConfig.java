package com.parseryss.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.util.Properties;

public class CookieConfig {
    private static final Logger logger = LoggerFactory.getLogger(CookieConfig.class);
    private static final Properties cookies = new Properties();
    private static final String COOKIE_FILE = "cookies.properties";
    private static volatile boolean isLoaded = false;

    static {
        synchronized (CookieConfig.class) {
            if (!isLoaded) {
                loadCookies();
                isLoaded = true;
            }
        }
    }

    private static void loadCookies() {
        File externalFile = new File(COOKIE_FILE);
        if (externalFile.exists() && externalFile.isFile()) {
            try (InputStream input = new FileInputStream(externalFile)) {
                cookies.load(new InputStreamReader(input, "UTF-8"));
                logger.info("Loaded cookies from external file: {}", COOKIE_FILE);
                return;
            } catch (IOException e) {
                logger.warn("Failed to load external cookie file: {}", e.getMessage());
            }
        }

        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(COOKIE_FILE)) {
            if (input != null) {
                cookies.load(new InputStreamReader(input, "UTF-8"));
                logger.info("Loaded cookies from resources: {}", COOKIE_FILE);
            } else {
                logger.warn("Cookie file not found");
            }
        } catch (IOException e) {
            logger.error("Error loading cookies from resources: {}", e.getMessage(), e);
        }
    }

    public static String getCookiesForDomain(String domain) {
        if (domain == null || domain.trim().isEmpty()) {
            return "";
        }
        String key = domain.toLowerCase().trim() + ".cookies";
        String cookiesStr = cookies.getProperty(key, "");
        return cookiesStr != null ? cookiesStr.trim() : "";
    }

    public static void setCookiesForDomain(String domain, String cookieString) {
        if (domain == null || domain.trim().isEmpty()) {
            return;
        }
        String key = domain.toLowerCase().trim() + ".cookies";
        cookies.setProperty(key, cookieString != null ? cookieString.trim() : "");
        saveCookies();
        logger.info("Cookies updated for domain: {}", domain);
    }

    public static String getCookie(String domain, String cookieName) {
        if (domain == null || domain.trim().isEmpty() || cookieName == null || cookieName.trim().isEmpty()) {
            return "";
        }
        String allCookies = getCookiesForDomain(domain);
        if (allCookies == null || allCookies.isEmpty()) {
            return "";
        }
        String[] cookiePairs = allCookies.split("; ");
        for (String pair : cookiePairs) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && parts[0].trim().equals(cookieName.trim())) {
                return parts[1].trim();
            }
        }
        return "";
    }

    public static void setCookie(String domain, String cookieName, String cookieValue) {
        if (domain == null || domain.trim().isEmpty() || cookieName == null || cookieName.trim().isEmpty()) {
            return;
        }
        String key = domain.toLowerCase().trim() + ".cookies";
        String currentCookies = cookies.getProperty(key, "");
        StringBuilder newCookies = new StringBuilder();
        boolean replaced = false;

        if (currentCookies != null && !currentCookies.trim().isEmpty()) {
            String[] cookiePairs = currentCookies.split("; ");
            for (String pair : cookiePairs) {
                String[] parts = pair.split("=", 2);
                if (parts.length == 2) {
                    if (parts[0].trim().equals(cookieName.trim())) {
                        newCookies.append(cookieName.trim()).append("=").append(cookieValue != null ? cookieValue.trim() : "").append("; ");
                        replaced = true;
                    } else {
                        newCookies.append(pair).append("; ");
                    }
                }
            }
        }

        if (!replaced) {
            newCookies.append(cookieName.trim()).append("=").append(cookieValue != null ? cookieValue.trim() : "").append("; ");
        }

        String result = newCookies.toString();
        if (result.endsWith("; ")) {
            result = result.substring(0, result.length() - 2);
        }

        cookies.setProperty(key, result);
        saveCookies();
    }

    public static void clearCookiesForDomain(String domain) {
        if (domain == null || domain.trim().isEmpty()) {
            return;
        }
        String key = domain.toLowerCase().trim() + ".cookies";
        cookies.remove(key);
        saveCookies();
        logger.info("All cookies cleared for domain: {}", domain);
    }

    public static void saveCookies() {
        File externalFile = new File(COOKIE_FILE);
        try (OutputStream output = new FileOutputStream(externalFile)) {
            cookies.store(output, "Cookies for HTTP requests\nAuto-generated file");
        } catch (IOException e) {
            logger.error("Failed to save cookies: {}", e.getMessage(), e);
        }
    }

    public static boolean hasCookiesForDomain(String domain) {
        if (domain == null || domain.trim().isEmpty()) {
            return false;
        }
        String key = domain.toLowerCase().trim() + ".cookies";
        String cookieString = cookies.getProperty(key, "");
        return cookieString != null && !cookieString.trim().isEmpty();
    }
}

package com.controlj.addon.weather.util;

import java.io.*;
import java.util.Properties;

/**
 * SettingsManager - Persistent storage for add-on settings.
 * Stores configuration in a properties file on the server.
 */
public class SettingsManager {
    
    // Путь к файлу настроек (в папке конфигурации Tomcat)
    private static final String CONFIG_FILE = System.getProperty("catalina.base") + 
                                               "/conf/weather-settings.properties";
    private static Properties props = new Properties();
    
    static {
        load();
    }
    
    private static void load() {
        File file = new File(CONFIG_FILE);
        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                props.load(fis);
                System.out.println("[SettingsManager] Loaded settings from: " + CONFIG_FILE);
            } catch (IOException e) {
                System.err.println("[SettingsManager] Failed to load settings: " + e.getMessage());
            }
        } else {
            System.out.println("[SettingsManager] No existing settings file, will create on save");
        }
    }
    
    private static void save() {
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            props.store(fos, "Weather Add-on Settings");
            System.out.println("[SettingsManager] Saved settings to: " + CONFIG_FILE);
        } catch (IOException e) {
            System.err.println("[SettingsManager] Failed to save settings: " + e.getMessage());
        }
    }
    
    // API Key
    public static void saveApiKey(String apiKey) {
        props.setProperty("apiKey", apiKey == null ? "" : apiKey);
        save();
    }
    
    public static String getApiKey() {
        return props.getProperty("apiKey", "");
    }
    
    // Units (metric/us)
    public static void saveUnits(String units) {
        props.setProperty("units", units == null ? "metric" : units);
        save();
    }
    
    public static String getUnits() {
        return props.getProperty("units", "metric");
    }
    
    // Refresh Rate (minutes)
    public static void saveRefreshRate(int rate) {
        props.setProperty("refreshRate", String.valueOf(rate));
        save();
    }
    
    public static int getRefreshRate() {
        try {
            return Integer.parseInt(props.getProperty("refreshRate", "15"));
        } catch (NumberFormatException e) {
            return 15;
        }
    }
    
    // Database path (GEO tree)
    public static void saveDatabasePath(String path) {
        props.setProperty("databasePath", path == null ? "" : path);
        save();
    }
    
    public static String getDatabasePath() {
        return props.getProperty("databasePath", "#weather_prog");
    }
    
    // Location name
    public static void saveLocation(String location) {
        props.setProperty("location", location == null ? "Moscow, Russia" : location);
        save();
    }
    
    public static String getLocation() {
        return props.getProperty("location", "Moscow, Russia");
    }
    
    // Clear all settings
    public static void clear() {
        props.clear();
        save();
    }
}
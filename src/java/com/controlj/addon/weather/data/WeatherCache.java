package com.controlj.addon.weather.data;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WeatherCache - In-memory cache for weather data.
 * Stores current conditions, hourly forecasts and daily forecasts for multiple locations.
 * Each location is identified by its WebCTRL program path.
 *
 * 1.1.4: keeps the last fetch error per location so the UI can show the real
 * reason ("connect timed out", "403 - ...") instead of a generic "no data".
 */
public class WeatherCache {
    
    // Storage for each location (key = programPath)
    private static ConcurrentHashMap<String, LocationWeatherData> cache = new ConcurrentHashMap<>();
    
    // Last fetch error for each location (key = programPath), cleared on success
    private static ConcurrentHashMap<String, String> lastErrors = new ConcurrentHashMap<>();
    
    /**
     * Update weather data for a specific location.
     * 
     * @param programPath WebCTRL GEO tree path (cache key)
     * @param temp Current temperature
     * @param humidity Current humidity percentage
     * @param conditions Current weather description
     * @param windSpeed Current wind speed
     * @param windDir Current wind direction in degrees
     * @param feelsLike Current "feels like" temperature
     * @param pressure Current pressure in hPa
     * @param hourlyForecast List of hourly forecast data (12 hours)
     * @param dailyForecast List of daily forecast data (7 days)
     */
    public static void updateForLocation(String programPath, double temp, int humidity, 
                                          String conditions, double windSpeed, double windDir,
                                          double feelsLike, double pressure,
                                          List<HourlyForecast> hourlyForecast,
                                          List<DailyForecast> dailyForecast) {
        LocationWeatherData data = new LocationWeatherData();
        data.setCurrentTemp(temp);
        data.setCurrentHumidity(humidity);
        data.setCurrentConditions(conditions);
        data.setCurrentWindSpeed(windSpeed);
        data.setCurrentWindDir(windDir);
        data.setCurrentFeelsLike(feelsLike);
        data.setCurrentPressure(pressure);
        data.setHourlyForecast(hourlyForecast != null ? new ArrayList<>(hourlyForecast) : new ArrayList<>());
        data.setDailyForecast(dailyForecast != null ? new ArrayList<>(dailyForecast) : new ArrayList<>());
        data.setLastUpdate(System.currentTimeMillis());
        
        cache.put(programPath, data);
        lastErrors.remove(programPath);
        
        System.out.println("[WeatherCache] Updated data for " + programPath + 
                          ": temp=" + temp + ", humidity=" + humidity + 
                          ", hourly=" + (hourlyForecast != null ? hourlyForecast.size() : 0) + 
                          ", daily=" + (dailyForecast != null ? dailyForecast.size() : 0));
    }
    
    /**
     * Retrieve weather data for a specific location.
     * 
     * @param programPath WebCTRL GEO tree path (cache key)
     * @return LocationWeatherData object or null if not found
     */
    public static LocationWeatherData getForLocation(String programPath) {
        return cache.get(programPath);
    }
    
    /**
     * Check if weather data exists for a specific location.
     * 
     * @param programPath WebCTRL GEO tree path (cache key)
     * @return true if data exists, false otherwise
     */
    public static boolean hasDataForLocation(String programPath) {
        return cache.containsKey(programPath);
    }
    
    /**
     * Remember the reason why the last fetch for this location failed.
     * The value is cleared automatically by {@link #updateForLocation}.
     */
    public static void setLastError(String programPath, String message) {
        if (message == null || message.isEmpty()) {
            lastErrors.remove(programPath);
        } else {
            lastErrors.put(programPath, message);
        }
    }
    
    /**
     * @return the last fetch error for this location or null if the last fetch succeeded.
     */
    public static String getLastError(String programPath) {
        return lastErrors.get(programPath);
    }
    
    /**
     * Clear all cached data.
     */
    public static void clear() {
        cache.clear();
        lastErrors.clear();
    }
    
    /**
     * Inner class holding weather data for a single location.
     */
    public static class LocationWeatherData {
        private double currentTemp;
        private int currentHumidity;
        private String currentConditions;
        private double currentWindSpeed;
        private double currentWindDir;
        private double currentFeelsLike;
        private double currentPressure;
        private List<HourlyForecast> hourlyForecast;
        private List<DailyForecast> dailyForecast;
        private long lastUpdate;
        
        // Getters
        public double getCurrentTemp() { return currentTemp; }
        public int getCurrentHumidity() { return currentHumidity; }
        public String getCurrentConditions() { return currentConditions; }
        public double getCurrentWindSpeed() { return currentWindSpeed; }
        public double getCurrentWindDir() { return currentWindDir; }
        public double getCurrentFeelsLike() { return currentFeelsLike; }
        public double getCurrentPressure() { return currentPressure; }
        public List<HourlyForecast> getHourlyForecast() { return hourlyForecast; }
        public List<DailyForecast> getDailyForecast() { return dailyForecast; }
        public long getLastUpdate() { return lastUpdate; }
        
        // Setters
        public void setCurrentTemp(double currentTemp) { this.currentTemp = currentTemp; }
        public void setCurrentHumidity(int currentHumidity) { this.currentHumidity = currentHumidity; }
        public void setCurrentConditions(String currentConditions) { this.currentConditions = currentConditions; }
        public void setCurrentWindSpeed(double currentWindSpeed) { this.currentWindSpeed = currentWindSpeed; }
        public void setCurrentWindDir(double currentWindDir) { this.currentWindDir = currentWindDir; }
        public void setCurrentFeelsLike(double currentFeelsLike) { this.currentFeelsLike = currentFeelsLike; }
        public void setCurrentPressure(double currentPressure) { this.currentPressure = currentPressure; }
        public void setHourlyForecast(List<HourlyForecast> hourlyForecast) { this.hourlyForecast = hourlyForecast; }
        public void setDailyForecast(List<DailyForecast> dailyForecast) { this.dailyForecast = dailyForecast; }
        public void setLastUpdate(long lastUpdate) { this.lastUpdate = lastUpdate; }
    }
}

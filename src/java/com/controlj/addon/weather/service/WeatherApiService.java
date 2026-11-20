package com.controlj.addon.weather.service;

import com.controlj.addon.weather.data.HourlyForecast;
import com.controlj.addon.weather.data.DailyForecast;
import com.controlj.addon.weather.data.LocationConfig;
import com.controlj.addon.weather.data.WeatherCache.LocationWeatherData;
import com.controlj.addon.weather.util.SettingsManager;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Universal weather service supporting multiple providers.
 * Switch between providers by changing the USE_VISUAL_CROSSING flag.
 *
 * 1.1.4: transient network failures (connect/read timeouts, connection reset)
 * are retried MAX_ATTEMPTS times with RETRY_DELAY_MS pause. HTTP errors
 * returned by the provider (401/403/...) are NOT retried - they are reported
 * immediately so that a bad key or an exhausted quota does not multiply.
 */
public class WeatherApiService {

    // ========== SELECT WEATHER PROVIDER ==========
    // Set to true for Visual Crossing, false for WeatherAPI.com
    private static final boolean USE_VISUAL_CROSSING = false;  // ← МЕНЯЙТЕ ЗДЕСЬ

    // Provider-specific endpoints
    private static final String VC_API_URL = "https://weather.visualcrossing.com/VisualCrossingWebServices/rest/services/timeline/";
    private static final String WA_API_URL = "https://api.weatherapi.com/v1/forecast.json";

    // ========== RETRY / FORECAST SETTINGS ==========
    // Total attempts per location (1 try + 2 retries). Only I/O failures are retried.
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 2000L;
    // Days requested from the provider. WeatherAPI returns only what the
    // subscription allows (3 days on the free plan) - see info in the add-on docs.
    private static final int FORECAST_DAYS = 3;

    /**
     * Fetches weather data for a location, retrying transient network failures.
     */
    public static LocationWeatherData fetchWeatherData(LocationConfig loc, String apiKey) throws Exception {
        Exception lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                if (USE_VISUAL_CROSSING) {
                    return fetchFromVisualCrossing(loc, apiKey);
                } else {
                    return fetchFromWeatherAPI(loc, apiKey);
                }
            } catch (IOException e) {
                lastError = e;
                System.out.println("[WeatherApiService] Attempt " + attempt + "/" + MAX_ATTEMPTS
                        + " failed for " + loc.getName() + ": " + e);
                if (attempt < MAX_ATTEMPTS) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        throw lastError;
    }

    // ========== VISUAL CROSSING IMPLEMENTATION ==========
    private static LocationWeatherData fetchFromVisualCrossing(LocationConfig loc, String apiKey) throws Exception {
        String query = loc.getCoordinates();
        String units = SettingsManager.getUnits();
        String locationName = loc.getName();
        
        System.out.println("[WeatherApiService] Fetching from Visual Crossing: " + locationName);
        
        String urlString = VC_API_URL + query +
                "?unitGroup=" + units +
                "&include=current,hours,days" +
                "&key=" + apiKey +
                "&contentType=json";
        
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        
        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new Exception("Visual Crossing API error: " + responseCode);
        }
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder jsonBuilder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            jsonBuilder.append(line);
        }
        reader.close();
        
        JSONObject timeline = new JSONObject(jsonBuilder.toString());
        String timezone = timeline.getString("timezone");
        ZoneId zoneId = ZoneId.of(timezone);
        LocalDateTime now = LocalDateTime.now(zoneId);
        int currentHour = now.getHour();
        
        // Current weather
        JSONObject current = timeline.getJSONObject("currentConditions");
        double currentTemp = current.getDouble("temp");
        int currentHumidity = current.getInt("humidity");
        String currentConditions = current.getString("conditions");
        double currentWind = current.optDouble("windspeed", 0);
        double currentWindDir = current.optDouble("winddir", 0);
        double currentFeelsLike = current.optDouble("feelslike", currentTemp);
        double currentPressure = current.optDouble("pressure", 0);
        
        // 12-hour forecast
        JSONArray daysArray = timeline.getJSONArray("days");
        List<HourlyForecast> hourlyList = new ArrayList<>();
        int hoursNeeded = 12;
        int currentDayIndex = 0;
        
        while (hoursNeeded > 0 && currentDayIndex < daysArray.length()) {
            JSONObject day = daysArray.getJSONObject(currentDayIndex);
            JSONArray hoursArray = day.getJSONArray("hours");
            int startHour = (currentDayIndex == 0) ? currentHour : 0;
            
            for (int i = startHour; i < hoursArray.length() && hoursNeeded > 0; i++) {
                JSONObject hour = hoursArray.getJSONObject(i);
                HourlyForecast hf = new HourlyForecast();
                
                long epoch = hour.getLong("datetimeEpoch");
                LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), zoneId);
                hf.setTime(time.format(DateTimeFormatter.ofPattern("HH:mm")));
                hf.setTemp(hour.getDouble("temp"));
                hf.setHumidity(hour.getInt("humidity"));
                hf.setConditions(hour.getString("conditions"));
                hf.setWindSpeed(hour.optDouble("windspeed", 0));
                hf.setWindDir(hour.optDouble("winddir", 0));
                
                hourlyList.add(hf);
                hoursNeeded--;
            }
            currentDayIndex++;
        }
        
        // 7-day forecast
        List<DailyForecast> dailyList = new ArrayList<>();
        for (int i = 0; i < Math.min(7, daysArray.length()); i++) {
            JSONObject day = daysArray.getJSONObject(i);
            DailyForecast df = new DailyForecast();
            long epoch = day.getLong("datetimeEpoch");
            LocalDateTime date = LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), zoneId);
            df.setDate(date.format(DateTimeFormatter.ofPattern("dd.MM")));
            df.setMaxTemp(day.getDouble("tempmax"));
            df.setMinTemp(day.getDouble("tempmin"));
            df.setConditions(day.getString("conditions"));
            dailyList.add(df);
        }
        
        LocationWeatherData result = new LocationWeatherData();
        result.setCurrentTemp(currentTemp);
        result.setCurrentHumidity(currentHumidity);
        result.setCurrentConditions(currentConditions);
        result.setCurrentWindSpeed(currentWind);
        result.setCurrentWindDir(currentWindDir);
        result.setCurrentFeelsLike(currentFeelsLike);
        result.setCurrentPressure(currentPressure);
        result.setHourlyForecast(hourlyList);
        result.setDailyForecast(dailyList);
        result.setLastUpdate(System.currentTimeMillis());
        
        System.out.println("[WeatherApiService] Visual Crossing success: " + locationName + ", temp=" + currentTemp);
        return result;
    }
    
    // ========== WEATHERAPI.COM IMPLEMENTATION ==========
  private static LocationWeatherData fetchFromWeatherAPI(LocationConfig loc, String apiKey) throws Exception {
    String query = loc.getCoordinates();
    String locationName = loc.getName();
    
    System.out.println("[WeatherApiService] Fetching from WeatherAPI.com: " + locationName);
    
    String urlString = WA_API_URL +
            "?key=" + apiKey +
            "&q=" + query +
            "&days=" + FORECAST_DAYS +
            "&aqi=no" +
            "&alerts=no" +
            "&units=metric";
    
    URL url = new URL(urlString);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod("GET");
    conn.setRequestProperty("User-Agent", "Weather-Addon/1.1.4 (WebCTRL)");
    conn.setConnectTimeout(15000);
    conn.setReadTimeout(15000);
    
    int responseCode = conn.getResponseCode();
    if (responseCode != 200) {
        BufferedReader errorReader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
        StringBuilder errorBody = new StringBuilder();
        String line;
        while ((line = errorReader.readLine()) != null) {
            errorBody.append(line);
        }
        errorReader.close();
        throw new Exception("WeatherAPI error: " + responseCode + " - " + errorBody.toString());
    }
    
    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
    StringBuilder jsonBuilder = new StringBuilder();
    String line;
    while ((line = reader.readLine()) != null) {
        jsonBuilder.append(line);
    }
    reader.close();
    
    JSONObject root = new JSONObject(jsonBuilder.toString());
    
    JSONObject current = root.getJSONObject("current");
    JSONObject location = root.getJSONObject("location");
    String timezone = location.getString("tz_id");
    ZoneId zoneId = ZoneId.of(timezone);
    LocalDateTime now = LocalDateTime.now(zoneId);
    int currentHour = now.getHour();
    
    double currentTemp = current.getDouble("temp_c");
    int currentHumidity = current.getInt("humidity");
    String currentConditions = current.getJSONObject("condition").getString("text");
    
    // ===== ОКРУГЛЯЕМ СКОРОСТЬ ВЕТРА ДО 1 ЗНАКА =====
    double currentWind = Math.round((current.getDouble("wind_kph") / 3.6) * 10.0) / 10.0;
    
    double currentWindDir = current.getInt("wind_degree");
    double currentFeelsLike = current.getDouble("feelslike_c");
    double currentPressure = current.getDouble("pressure_mb");
    
    JSONObject forecast = root.getJSONObject("forecast");
    JSONArray forecastDays = forecast.getJSONArray("forecastday");
    
    List<HourlyForecast> hourlyList = new ArrayList<>();
    List<DailyForecast> dailyList = new ArrayList<>();
    int hoursNeeded = 12;
    
    for (int dayIndex = 0; dayIndex < forecastDays.length() && hoursNeeded > 0; dayIndex++) {
        JSONObject day = forecastDays.getJSONObject(dayIndex);
        JSONArray hoursArray = day.getJSONArray("hour");
        
        int startHour = (dayIndex == 0) ? currentHour : 0;
        
        for (int i = startHour; i < hoursArray.length() && hoursNeeded > 0; i++) {
            JSONObject hour = hoursArray.getJSONObject(i);
            HourlyForecast hf = new HourlyForecast();
            
            String timeStr = hour.getString("time");
            String hourTime = timeStr.split(" ")[1].substring(0, 5);
            hf.setTime(hourTime);
            hf.setTemp(Math.round(hour.getDouble("temp_c") * 10.0) / 10.0); // Округляем температуру до 1 знака
            
            // ===== ОКРУГЛЯЕМ СКОРОСТЬ ВЕТРА В ПОЧАСОВОМ ПРОГНОЗЕ =====
            double windSpeed = hour.getDouble("wind_kph") / 3.6;
            hf.setWindSpeed(Math.round(windSpeed * 10.0) / 10.0);
            
            hf.setHumidity(hour.getInt("humidity"));
            hf.setConditions(hour.getJSONObject("condition").getString("text"));
            hf.setWindDir(hour.getInt("wind_degree"));
            
            hourlyList.add(hf);
            hoursNeeded--;
        }
    }
    
    for (int i = 0; i < forecastDays.length() && i < 7; i++) {
        JSONObject day = forecastDays.getJSONObject(i);
        JSONObject dayData = day.getJSONObject("day");
        
        DailyForecast df = new DailyForecast();
        String dateStr = day.getString("date");
        df.setDate(dateStr.substring(5));
        df.setMaxTemp(Math.round(dayData.getDouble("maxtemp_c") * 10.0) / 10.0);
        df.setMinTemp(Math.round(dayData.getDouble("mintemp_c") * 10.0) / 10.0);
        df.setConditions(dayData.getJSONObject("condition").getString("text"));
        dailyList.add(df);
    }
    
    LocationWeatherData result = new LocationWeatherData();
    result.setCurrentTemp(currentTemp);
    result.setCurrentHumidity(currentHumidity);
    result.setCurrentConditions(currentConditions);
    result.setCurrentWindSpeed(currentWind);
    result.setCurrentWindDir(currentWindDir);
    result.setCurrentFeelsLike(currentFeelsLike);
    result.setCurrentPressure(currentPressure);
    result.setHourlyForecast(hourlyList);
    result.setDailyForecast(dailyList);
    result.setLastUpdate(System.currentTimeMillis());
    
    System.out.println("[WeatherApiService] WeatherAPI success: " + locationName + ", temp=" + currentTemp);
    return result;
    }
}

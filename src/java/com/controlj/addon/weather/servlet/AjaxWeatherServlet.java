package com.controlj.addon.weather.servlet;

import com.controlj.addon.weather.data.WeatherCache;
import com.controlj.addon.weather.data.HourlyForecast;
import com.controlj.addon.weather.data.LocationConfig;
import com.controlj.addon.weather.data.WeatherCache.LocationWeatherData;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AjaxWeatherServlet - Handles AJAX requests for dynamic UI updates.
 * Provides JSON endpoints for location management and weather data retrieval.
 */
@WebServlet("/ajax/*")
public class AjaxWeatherServlet extends HttpServlet {
    
    private static final String DEFAULT_PROGRAM_PATH = "#weather_prog";
    
    /**
     * Handles GET requests for configuration data and location lists.
     * Endpoints:
     * - /init: Returns initialization status
     * - /locations: Returns list of all configured locations
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        String pathInfo = request.getPathInfo();
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        
        if ("/init".equals(pathInfo)) {
            // Return initialization status
            JSONObject result = new JSONObject();
            result.put("success", true);
            out.print(result.toString());
        } else if ("/locations".equals(pathInfo)) {
            // Return list of all locations
            ConcurrentHashMap<String, LocationConfig> locations = ConfigServlet.getLocations();
            JSONArray locationsArray = new JSONArray();
            for (LocationConfig loc : locations.values()) {
                JSONObject locObj = new JSONObject();
                locObj.put("id", loc.getId());
                locObj.put("name", loc.getName());
                locObj.put("programPath", loc.getProgramPath());
                locObj.put("coordinates", loc.getCoordinates());
                locObj.put("enabled", loc.isEnabled());
                locationsArray.put(locObj);
            }
            out.print(locationsArray.toString());
        } else {
            out.print("{\"success\":false, \"error\":\"Unknown GET request: " + pathInfo + "\"}");
        }
    }
    
    /**
     * Handles POST requests for weather data.
     * Endpoint:
     * - /showdata: Returns current weather and hourly forecast for a location
     *   Parameter: programPath (optional, defaults to DEFAULT_PROGRAM_PATH)
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        String pathInfo = request.getPathInfo();
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        
        if ("/showdata".equals(pathInfo)) {
            // Get programPath from request parameters
            String programPath = request.getParameter("programPath");
            if (programPath == null || programPath.isEmpty()) {
                programPath = DEFAULT_PROGRAM_PATH;
            }
            
            LocationWeatherData data = WeatherCache.getForLocation(programPath);
            
            JSONObject result = new JSONObject();
            if (data == null) {
                result.put("success", false);
                result.put("error", "No data available for " + programPath);
            } else {
                result.put("success", true);
                result.put("currentTemp", data.getCurrentTemp());
                result.put("currentHumidity", data.getCurrentHumidity());
                result.put("currentConditions", data.getCurrentConditions());
                result.put("currentWindSpeed", data.getCurrentWindSpeed());
                result.put("currentWindDir", data.getCurrentWindDir());
                result.put("lastUpdate", data.getLastUpdate());
                
                // Hourly forecast data
                JSONArray hourlyArray = new JSONArray();
                for (HourlyForecast hf : data.getHourlyForecast()) {
                    JSONObject hour = new JSONObject();
                    hour.put("time", hf.getTime());
                    hour.put("temp", hf.getTemp());
                    hour.put("humidity", hf.getHumidity());
                    hour.put("conditions", hf.getConditions());
                    hour.put("windSpeed", hf.getWindSpeed());
                    hour.put("windDir", hf.getWindDir());
                    hour.put("pressureHpa", hf.getPressureHpa());
                    hour.put("dewPoint", hf.getDewPoint());
                    hour.put("humidityRatioGPerKg", hf.getHumidityRatioGPerKg());
                    hour.put("enthalpy", hf.getEnthalpy());
                    hourlyArray.put(hour);
                }
                result.put("hourlyForecast", hourlyArray);
            }
            out.print(result.toString());
        } else {
            out.print("{\"success\":false, \"error\":\"Unknown POST request: " + pathInfo + "\"}");
        }
    }
}
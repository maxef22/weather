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
 * PrimitiveUpdateServlet - Updates WebCTRL primitive values with latest weather data.
 * This servlet provides JSON data that WebCTRL uses to update BACnet points.
 * Endpoint: /updatedata (POST - for WebCTRL, GET - for manual testing)
 */
@WebServlet("/updatedata")
public class PrimitiveUpdateServlet extends HttpServlet {
    
    private static final String DEFAULT_PROGRAM_PATH = "#weather_prog";
    
    /**
     * POST endpoint for WebCTRL - returns JSON with current weather values.
     * WebCTRL calls this periodically to get fresh data for BACnet points.
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        
        // Get programPath from parameters or use default
        String programPath = request.getParameter("programPath");
        if (programPath == null || programPath.isEmpty()) {
            programPath = DEFAULT_PROGRAM_PATH;
        }
        
        LocationWeatherData data = WeatherCache.getForLocation(programPath);
        
        JSONObject root = new JSONObject();
        JSONArray values = new JSONArray();
        
        if (data != null) {
            List<HourlyForecast> hourlyForecast = data.getHourlyForecast();
            
            for (int hour = 0; hour < 12; hour++) {
                if (hour < hourlyForecast.size()) {
                    HourlyForecast hf = hourlyForecast.get(hour);
                    
                    // Temperature
                    JSONObject tempValue = new JSONObject();
                    tempValue.put("fieldName", "wf" + hour + "_highesttemperature");
                    tempValue.put("value", String.valueOf(hf.getTemp()));
                    tempValue.put("type", "PrimitiveNumber");
                    values.put(tempValue);
                    
                    // Humidity
                    JSONObject humidityValue = new JSONObject();
                    humidityValue.put("fieldName", "wf" + hour + "_avghumidity");
                    humidityValue.put("value", String.valueOf(hf.getHumidity()));
                    humidityValue.put("type", "PrimitiveNumber");
                    values.put(humidityValue);
                    
                    // Wind speed
                    JSONObject windSpeedValue = new JSONObject();
                    windSpeedValue.put("fieldName", "wf" + hour + "_windspeed");
                    windSpeedValue.put("value", String.valueOf(hf.getWindSpeed()));
                    windSpeedValue.put("type", "PrimitiveNumber");
                    values.put(windSpeedValue);
                    
                    // Wind direction
                    JSONObject windDirValue = new JSONObject();
                    windDirValue.put("fieldName", "wf" + hour + "_winddirection");
                    windDirValue.put("value", String.valueOf(hf.getWindDir()));
                    windDirValue.put("type", "PrimitiveNumber");
                    values.put(windDirValue);
                    
                    // Enthalpy
                    JSONObject enthalpyValue = new JSONObject();
                    enthalpyValue.put("fieldName", "wf" + hour + "_enthalpy");
                    enthalpyValue.put("value", String.valueOf(hf.getEnthalpy()));
                    enthalpyValue.put("type", "PrimitiveNumber");
                    values.put(enthalpyValue);
                    
                    // Dew point
                    JSONObject dewPointValue = new JSONObject();
                    dewPointValue.put("fieldName", "wf" + hour + "_dewpoint");
                    dewPointValue.put("value", String.valueOf(hf.getDewPoint()));
                    dewPointValue.put("type", "PrimitiveNumber");
                    values.put(dewPointValue);
                    
                    // Humidity ratio, g/kg
                    JSONObject humidityRatioValue = new JSONObject();
                    humidityRatioValue.put("fieldName", "wf" + hour + "_humidityratio");
                    humidityRatioValue.put("value", String.valueOf(hf.getHumidityRatioGPerKg()));
                    humidityRatioValue.put("type", "PrimitiveNumber");
                    values.put(humidityRatioValue);
                }
            }
        }
        
        root.put("values", values);
        out.print(root.toString());
    }
    
    /**
     * GET endpoint for manual testing in browser.
     * Displays current cache contents in a human-readable format.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = response.getWriter();
        
        out.println("<!DOCTYPE html>");
        out.println("<html>");
        out.println("<head><title>PrimitiveUpdateServlet Debug</title>");
        out.println("<meta charset='UTF-8'>");
        out.println("<style>");
        out.println("body { font-family: Arial; margin: 20px; }");
        out.println("pre { background: #f0f0f0; padding: 10px; border-radius: 5px; }");
        out.println("</style>");
        out.println("</head>");
        out.println("<body>");
        out.println("<h1>PrimitiveUpdateServlet Debug</h1>");
        out.println("<p>This servlet expects POST requests from WebCTRL with JSON data.</p>");
        out.println("<p>Current data in cache:</p>");
        
        ConcurrentHashMap<String, LocationConfig> locations = ConfigServlet.getLocations();
        
        for (LocationConfig loc : locations.values()) {
            if (!loc.isEnabled()) continue;
            
            LocationWeatherData data = WeatherCache.getForLocation(loc.getProgramPath());
            out.println("<h3>" + loc.getName() + " (" + loc.getProgramPath() + ")</h3>");
            
            if (data == null) {
                out.println("<p>No data available</p>");
            } else {
                out.println("<ul>");
                out.println("<li>Last update: " + new java.util.Date(data.getLastUpdate()) + "</li>");
                out.println("<li>Current temp: " + data.getCurrentTemp() + "°C</li>");
                out.println("<li>Current humidity: " + data.getCurrentHumidity() + "%</li>");
                out.println("</ul>");
                
                out.println("<h4>Hourly Forecast (first 3 hours)</h4>");
                List<HourlyForecast> hourly = data.getHourlyForecast();
                for (int i = 0; i < Math.min(3, hourly.size()); i++) {
                    HourlyForecast hf = hourly.get(i);
                    out.println("<p>" + hf.getTime() + ": " + hf.getTemp() + "°C, " + hf.getHumidity() + "%, wind " + hf.getWindSpeed() + " m/s</p>");
                }
            }
        }
        
        out.println("<p><a href='/weather/'>Back to main page</a></p>");
        out.println("</body>");
        out.println("</html>");
    }
}
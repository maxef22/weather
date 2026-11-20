package com.controlj.addon.weather.servlet;

import com.controlj.addon.weather.data.WeatherCache;
import com.controlj.addon.weather.data.HourlyForecast;
import com.controlj.addon.weather.data.DailyForecast;
import com.controlj.addon.weather.data.LocationConfig;
import com.controlj.addon.weather.data.WeatherCache.LocationWeatherData;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import com.controlj.addon.weather.util.SettingsManager;

/**
 * WeatherServlet - Main servlet for weather display.
 * Displays current weather, 12-hour forecast and 7-day forecast from cache.
 * Does NOT make direct API calls - uses cached data from WeatherUpdater.
 */
@WebServlet("/weather")
public class WeatherServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = response.getWriter();

        // Check API key
        String apiKey = SettingsManager.getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            out.println("<html><body style='background:#000;color:#ffd700;font-family:Arial;text-align:center;padding:50px;'>");
            out.println("<h1>⚠️ API Key Not Configured</h1>");
            out.println("<p>Please configure the API key in the <a href='" + ctx() + "/config' style='color:#ffd700'>settings</a>.</p>");
            out.println("</body></html>");
            return;
        }

        // Get selected location
        String selectedLocationId = request.getParameter("location");
        LocationConfig selectedLoc = null;
        ConcurrentHashMap<String, LocationConfig> locations = ConfigServlet.getLocations();
        
        if (selectedLocationId == null) {
            for (LocationConfig loc : locations.values()) {
                if (loc.isEnabled()) {
                    selectedLoc = loc;
                    break;
                }
            }
        } else {
            selectedLoc = locations.get(selectedLocationId);
        }
        
        if (selectedLoc == null) {
            out.println("<html><body style='background:#000;color:#ffd700;font-family:Arial;text-align:center;padding:50px;'>");
            out.println("<h1>No locations configured</h1>");
            out.println("<p>Please add locations in the <a href='" + ctx() + "/config' style='color:#ffd700'>settings</a>.</p>");
            out.println("</body></html>");
            return;
        }
        
        // Get data from shared cache (NO API CALLS!)
        LocationWeatherData data = WeatherCache.getForLocation(selectedLoc.getProgramPath());
        
        if (data == null) {
            out.println("<html><body style='background:#000;color:#ff6b6b;font-family:Arial;text-align:center;padding:50px;'>");
            out.println("<h1>❌ No weather data available</h1>");
            String reason = WeatherCache.getLastError(selectedLoc.getProgramPath());
            if (reason != null) {
                out.println("<p style='color:#ff9f43;font-family:Consolas,monospace;font-size:13px;'>Причина: " + esc(reason) + "</p>");
            }
            out.println("<p>Please run <a href='" + request.getContextPath() + "/writeweather' style='color:#ffd700'>Force Update</a> first.</p>");
            out.println("</body></html>");
            return;
        }
        
        // Generate HTML (reuse the same display logic)
        generateHtml(out, selectedLoc, data, locations);
    }
    
    private void generateHtml(PrintWriter out, LocationConfig selectedLoc, LocationWeatherData data, 
                              ConcurrentHashMap<String, LocationConfig> locations) {
        
        out.println("<!DOCTYPE html>");
        out.println("<html>");
        out.println("<head><title>Weather - " + selectedLoc.getName() + "</title>");
        out.println("<meta charset='UTF-8'>");
        out.println("<script src='" + ctx() + "/js/chart.umd.min.js'></script>");
        out.println("<style>");
        out.println("* { margin: 0; padding: 0; box-sizing: border-box; }");
        out.println("body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;");
        out.println("background: #000000; font-size: 12px; padding: 15px; }");
        out.println(".container { max-width: 1600px; margin: 0 auto; }");
        
        out.println(".nav-menu { background: #1a1a1a; border-radius: 10px; margin-bottom: 15px;");
        out.println("padding: 10px 15px; display: flex; gap: 12px; flex-wrap: wrap; }");
        out.println(".nav-menu a { color: #ffd700; text-decoration: none; padding: 6px 12px;");
        out.println("border-radius: 6px; font-weight: bold; background: #2a2a2a; font-size: 12px; }");
        out.println(".nav-menu a:hover { background: #ffd700; color: #000000; }");
        
        out.println(".location-selector { background: #1a1a1a; border-radius: 10px; margin-bottom: 15px;");
        out.println("padding: 10px 15px; display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }");
        out.println(".location-selector label { font-weight: bold; color: #ffd700; font-size: 12px; }");
        out.println(".location-selector select { padding: 5px 8px; border-radius: 6px; border: 1px solid #333;");
        out.println("background: #2a2a2a; color: #ffd700; font-size: 12px; cursor: pointer; }");
        
        out.println(".weather-card { background: #0d0d0d; border-radius: 15px; overflow: hidden; }");
        out.println(".weather-header { background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%);");
        out.println("color: #ffd700; padding: 12px 20px; text-align: center; }");
        out.println(".weather-header h1 { font-size: 18px; margin: 0; }");
        out.println(".weather-header p { font-size: 11px; opacity: 0.8; margin-top: 4px; }");
        
        // Top row: Current weather + Hourly forecast
        out.println(".top-row { display: flex; gap: 20px; padding: 20px; flex-wrap: wrap; }");
        
        out.println(".current-card { background: #1a1a1a; border-radius: 12px; padding: 15px; min-width: 200px; }");
        out.println(".current-temp { font-size: 42px; font-weight: bold; color: #ffd700; line-height: 1; }");
        out.println(".current-temp-unit { font-size: 18px; color: #ffd700; }");
        out.println(".current-conditions { font-size: 11px; color: #aaa; margin-top: 5px; }");
        out.println(".current-details { margin-top: 10px; }");
        out.println(".current-detail { display: flex; justify-content: space-between; font-size: 11px; margin: 5px 0; }");
        out.println(".current-detail-label { color: #aaa; }");
        out.println(".current-detail-value { color: #ffd700; font-weight: bold; }");
        
        out.println(".hourly-container { flex: 1; min-width: 200px; }");
        out.println(".hourly-title { font-size: 12px; color: #ffd700; margin-bottom: 8px; border-left: 2px solid #ffd700; padding-left: 8px; }");
        out.println(".hourly-scroll { display: flex; overflow-x: auto; gap: 8px; padding-bottom: 5px; }");
        out.println(".hour-card { flex: 0 0 70px; background: #1a1a1a; border-radius: 8px; padding: 6px; text-align: center; }");
        out.println(".hour-card:hover { background: #252525; }");
        out.println(".hour-time { font-size: 10px; font-weight: bold; color: #ffd700; }");
        out.println(".hour-temp { font-size: 14px; font-weight: bold; margin: 4px 0; color: #ffd700; }");
        out.println(".hour-card div { font-size: 8px; color: #aaa; }");
        
        out.println(".daily-section { padding: 15px 20px; }");
        out.println(".section-title { font-size: 13px; margin-bottom: 10px; color: #ffd700; border-left: 2px solid #ffd700; padding-left: 8px; }");
        out.println(".daily-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(80px,1fr));");
        out.println("gap: 8px; margin-top: 10px; }");
        out.println(".day-card { background: #1a1a1a; border-radius: 8px; padding: 8px; text-align: center; }");
        out.println(".day-card:hover { background: #252525; }");
        out.println(".day-date { font-weight: bold; color: #ffd700; font-size: 10px; }");
        out.println(".day-temp { font-size: 14px; font-weight: bold; margin: 4px 0; color: #ffd700; }");
        out.println(".day-temp-min { color: #aaa; font-size: 8px; }");
        out.println(".day-card div:last-child { font-size: 8px; color: #aaa; margin-top: 4px; }");
        
        out.println(".chart-container { background: #0d0d0d; padding: 15px; border-radius: 10px; margin: 0 20px 15px 20px; }");
        out.println(".info-text { color: #aaa; font-size: 10px; text-align: center; padding: 5px; }");
        out.println(".refresh-btn { display: block; width: 160px; margin: 15px auto; padding: 8px;");
        out.println("background: #ffd700; color: #000; border: none; border-radius: 20px; cursor: pointer; font-size: 11px; font-weight: bold; }");
        out.println("</style>");
        out.println("</head>");
        out.println("<body>");
        out.println("<div class='container'>");
        
        // Navigation menu
        out.println("<div class='nav-menu'>");
        out.println("<a href='" + ctx() + "/weather'>🏠 Home</a>");
        out.println("<a href='" + ctx() + "/config'>⚙️ Settings</a>");
        out.println("<a href='" + ctx() + "/writeweather'>✍️ Write to WebCTRL</a>");
        out.println("</div>");
        
        // Location selector
        out.println("<div class='location-selector'>");
        out.println("<form method='get' action='" + ctx() + "/weather'>");
        out.println("<label>📍 Select location:</label>");
        out.println("<select name='location' onchange='this.form.submit()'>");
        
        List<LocationConfig> sortedLocs = new ArrayList<>(locations.values());
        sortedLocs.sort((a, b) -> Integer.compare(a.getRefreshOrder(), b.getRefreshOrder()));
        
        for (LocationConfig loc : sortedLocs) {
            if (!loc.isEnabled()) continue;
            String selected = (selectedLoc != null && selectedLoc.getId().equals(loc.getId())) ? "selected" : "";
            out.println("<option value='" + loc.getId() + "' " + selected + ">" + loc.getName() + "</option>");
        }
        out.println("</select>");
        out.println("</form>");
        out.println("</div>");
        
        out.println("<div class='weather-card'>");
        out.println("<div class='weather-header'>");
        out.println("<h1>🌍 " + selectedLoc.getName() + "</h1>");
        out.println("<p>Current weather and forecast</p>");
        out.println("</div>");

        // Current weather + Hourly forecast row
        out.println("<div class='top-row'>");
        
        out.println("<div class='current-card'>");
        out.println("<div class='current-temp'>" + Math.round(data.getCurrentTemp()) + "<span class='current-temp-unit'>°C</span></div>");
        out.println("<div class='current-conditions'>" + data.getCurrentConditions() + "</div>");
        out.println("<div class='current-details'>");
        out.println("<div class='current-detail'><span class='current-detail-label'>🌡️ Feels like</span><span class='current-detail-value'>" + Math.round(data.getCurrentFeelsLike()) + "°C</span></div>");
        out.println("<div class='current-detail'><span class='current-detail-label'>💧 Humidity</span><span class='current-detail-value'>" + data.getCurrentHumidity() + "%</span></div>");
        out.println("<div class='current-detail'><span class='current-detail-label'>💨 Wind</span><span class='current-detail-value'>" + data.getCurrentWindSpeed() + " m/s</span></div>");
        out.println("<div class='current-detail'><span class='current-detail-label'>🧭 Direction</span><span class='current-detail-value'>" + getWindDirectionString(data.getCurrentWindDir()) + " (" + (int)data.getCurrentWindDir() + "°)</span></div>");
        out.println("<div class='current-detail'><span class='current-detail-label'>🗜️ Pressure</span><span class='current-detail-value'>" + Math.round(data.getCurrentPressure()) + " hPa</span></div>");
        out.println("</div>");
        out.println("</div>");
        
        // Hourly forecast
        if (data.getHourlyForecast() != null && !data.getHourlyForecast().isEmpty()) {
            out.println("<div class='hourly-container'>");
            out.println("<div class='hourly-title'>📅 12-Hour Forecast</div>");
            out.println("<div class='hourly-scroll'>");
            for (HourlyForecast hf : data.getHourlyForecast()) {
                out.println("<div class='hour-card'>");
                out.println("<div class='hour-time'>" + hf.getTime() + "</div>");
                out.println("<div class='hour-temp'>" + Math.round(hf.getTemp()) + "°</div>");
                out.println("<div>" + (hf.getConditions().length() > 8 ? hf.getConditions().substring(0, 8) : hf.getConditions()) + "</div>");
                out.println("<div>💧 " + hf.getHumidity() + "%</div>");
                out.println("<div>💨 " + Math.round(hf.getWindSpeed()) + "</div>");
                out.println("</div>");
            }
            out.println("</div>");
            out.println("</div>");
        }
        out.println("</div>"); // end top-row

        // Temperature chart
        if (data.getDailyForecast() != null && !data.getDailyForecast().isEmpty()) {
            out.println("<div class='chart-container'>");
            out.println("<canvas id='tempChart' style='max-height: 200px; width: 100%;'></canvas>");
            out.println("</div>");

            // 7-day forecast
            out.println("<div class='daily-section'>");
            out.println("<div class='section-title'>📆 3-Day Forecast</div>");
            out.println("<div class='daily-grid'>");
            for (DailyForecast df : data.getDailyForecast()) {
                out.println("<div class='day-card'>");
                out.println("<div class='day-date'>" + df.getDate() + "</div>");
                out.println("<div class='day-temp'>" + Math.round(df.getMaxTemp()) + "°</div>");
                out.println("<div class='day-temp-min'>min " + Math.round(df.getMinTemp()) + "°</div>");
                out.println("<div>" + (df.getConditions().length() > 10 ? df.getConditions().substring(0, 10) : df.getConditions()) + "</div>");
                out.println("</div>");
            }
            out.println("</div>");
            out.println("</div>");
            
            // Chart.js script
            out.println("<script>");
            out.println("const canvas = document.getElementById('tempChart');");
            out.println("if (canvas && typeof Chart !== 'undefined') {");
            out.println("    const ctx = canvas.getContext('2d');");
            out.println("    new Chart(ctx, {");
            out.println("        type: 'line',");
            out.println("        data: {");
            out.println("            labels: [");
            for (DailyForecast df : data.getDailyForecast()) {
                out.println("'" + df.getDate() + "',");
            }
            out.println("            ],");
            out.println("            datasets: [{");
            out.println("                label: 'Max Temperature (°C)',");
            out.println("                data: [");
            for (DailyForecast df : data.getDailyForecast()) {
                out.println(df.getMaxTemp() + ",");
            }
            out.println("                ],");
            out.println("                borderColor: '#ff6b6b',");
            out.println("                backgroundColor: 'rgba(255, 107, 107, 0.1)',");
            out.println("                borderWidth: 1.5,");
            out.println("                tension: 0.3,");
            out.println("                pointBackgroundColor: '#ff6b6b',");
            out.println("                pointBorderColor: '#ffffff',");
            out.println("                pointRadius: 3,");
            out.println("                pointHoverRadius: 5");
            out.println("            }, {");
            out.println("                label: 'Min Temperature (°C)',");
            out.println("                data: [");
            for (DailyForecast df : data.getDailyForecast()) {
                out.println(df.getMinTemp() + ",");
            }
            out.println("                ],");
            out.println("                borderColor: '#4ecdc4',");
            out.println("                backgroundColor: 'rgba(78, 205, 196, 0.1)',");
            out.println("                borderWidth: 1.5,");
            out.println("                tension: 0.3,");
            out.println("                pointBackgroundColor: '#4ecdc4',");
            out.println("                pointBorderColor: '#ffffff',");
            out.println("                pointRadius: 3,");
            out.println("                pointHoverRadius: 5");
            out.println("            }]");
            out.println("        },");
            out.println("        options: {");
            out.println("            responsive: true,");
            out.println("            maintainAspectRatio: true,");
            out.println("            plugins: {");
            out.println("                legend: { labels: { color: '#ffd700', font: { size: 10 } } },");
            out.println("                tooltip: { mode: 'index', intersect: false, backgroundColor: '#1a1a1a', titleColor: '#ffd700', bodyColor: '#dddddd' }");
            out.println("            },");
            out.println("            scales: {");
            out.println("                y: { grid: { color: '#2a2a2a' }, title: { display: true, text: 'Temperature (°C)', color: '#ffd700', font: { size: 10 } }, ticks: { color: '#ffd700', font: { size: 9 } } },");
            out.println("                x: { grid: { color: '#2a2a2a' }, title: { display: true, text: 'Date', color: '#ffd700', font: { size: 10 } }, ticks: { color: '#ffd700', font: { size: 9 } } }");
            out.println("            }");
            out.println("        }");
            out.println("    });");
            out.println("}");
            out.println("</script>");
        }
        
        // Cache info and refresh button
        long age = System.currentTimeMillis() - data.getLastUpdate();
        out.println("<div class='info-text'>📅 Data updated: " + new java.util.Date(data.getLastUpdate()) + " (" + (age / 60000) + " minutes ago)</div>");
        
        out.println("<form action='" + ctx() + "/weather' method='get'>");
        out.println("<input type='hidden' name='location' value='" + selectedLoc.getId() + "'>");
        out.println("<button type='submit' class='refresh-btn'>🔄 Refresh Page</button>");
        out.println("</form>");
        
        out.println("</div></div>");
        out.println("</body>");
        out.println("</html>");
    }
    
    private static String esc(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    .replace("\"", "&quot;").replace("'", "&#39;");
    }
    
    private String ctx() {
        String c = getServletContext().getContextPath();
        return (c == null) ? "" : c;
    }
    private String getWindDirectionString(double windDir) {
        String[] directions = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
                               "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};
        int index = (int) Math.round(windDir / 22.5) % 16;
        return directions[index];
    }
}
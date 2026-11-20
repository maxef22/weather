package com.controlj.addon.weather.servlet;

import com.controlj.addon.weather.data.LocationConfig;
import com.controlj.addon.weather.data.WeatherCache;
import com.controlj.addon.weather.data.WeatherCache.LocationWeatherData;
import com.controlj.addon.weather.WeatherUpdater;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WeatherWriterServlet - status page for the WebCTRL point update.
 *
 * 1.1.5: the page no longer rewrites all points on every view and no longer prints
 * one line per point. Points are written by WeatherUpdater (one transaction for all
 * locations) right after every successful fetch; this page only shows the state,
 * the last write result and the per-location errors.
 */
@WebServlet("/writeweather")
public class WeatherWriterServlet extends HttpServlet {
    
    // Age after which cached data is considered stale (3 hours). Used for a hint only.
    private static final long STALE_AGE_MS = 180 * 60 * 1000;
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = response.getWriter();
        String ctx = request.getContextPath();
        
        out.println("<!DOCTYPE html>");
        out.println("<html>");
        out.println("<head><title>Weather Writer</title>");
        out.println("<meta charset='UTF-8'>");
        out.println("<style>");
        out.println("body { font-family: Arial, sans-serif; margin: 20px; background: #1a1a1a; }");
        out.println(".container { max-width: 1200px; margin: 0 auto; }");
        out.println(".card { background: #0d0d0d; padding: 16px 20px; border-radius: 8px; margin-bottom: 14px; box-shadow: 0 2px 4px rgba(0,0,0,0.3); }");
        out.println("h1, h2, h3 { color: #ffd700; }");
        out.println("h3 { margin: 0 0 6px 0; font-size: 15px; }");
        out.println(".success { color: #4ecdc4; }");
        out.println(".error { color: #ff6b6b; }");
        out.println(".info { color: #ffd700; }");
        out.println(".reason { color: #ff9f43; font-family: Consolas, monospace; font-size: 12px; }");
        out.println(".muted { color: #aaa; font-size: 12px; }");
        out.println(".nav { margin-bottom: 20px; }");
        out.println(".nav a { color: #ffd700; text-decoration: none; margin-right: 15px; }");
        out.println("button { background: #ffd700; color: #000; padding: 8px 16px; border: none; border-radius: 6px; cursor: pointer; margin-right: 8px; }");
        out.println("button:hover { opacity: 0.9; }");
        out.println("</style>");
        out.println("</head>");
        out.println("<body>");
        out.println("<div class='container'>");
        
        // Navigation menu
        out.println("<div class='nav'>");
        out.println("<a href='" + ctx + "/weather'>🏠 Home</a>");
        out.println("<a href='" + ctx + "/config'>⚙️ Settings</a>");
        out.println("<a href='" + ctx + "/writeweather'>✍️ Write to WebCTRL</a>");
        out.println("</div>");
        
        out.println("<h1>✍️ Weather data in WebCTRL</h1>");
        
        // Actions
        out.println("<div class='card' style='text-align:center'>");
        out.println("<form action='" + ctx + "/forceupdate' method='post' style='display:inline'>");
        out.println("<button type='submit'>🔄 Force Update (fetch from API + write)</button>");
        out.println("</form>");
        out.println("<form action='" + ctx + "/forceupdate' method='post' style='display:inline'>");
        out.println("<input type='hidden' name='writeOnly' value='1'>");
        out.println("<button type='submit'>↻ Rewrite points from cache (no API call)</button>");
        out.println("</form>");
        out.println("</div>");
        
        try {
            ConcurrentHashMap<String, LocationConfig> locations = ConfigServlet.getLocations();
            
            if (locations.isEmpty()) {
                out.println("<div class='card'><p class='error'>No locations configured. Please add locations in <a href='" + ctx + "/config'>Settings</a>.</p></div>");
                out.println("</div></body></html>");
                return;
            }
            
            // Fetch from the API only when some enabled location has no data at all.
            // Stale data is refreshed by the scheduler or by the Force Update button.
            boolean needsRefresh = false;
            for (LocationConfig loc : locations.values()) {
                if (!loc.isEnabled()) continue;
                if (WeatherCache.getForLocation(loc.getProgramPath()) == null) {
                    needsRefresh = true;
                    break;
                }
            }
            
            if (needsRefresh) {
                out.println("<div class='card'>");
                out.println("<p class='info'>🔄 Some locations have no data yet. Fetching from API...</p>");
                try {
                    WeatherUpdater.UpdateResult result = WeatherUpdater.updateAllLocations(true);
                    String cssClass = result.failed > 0 ? "error" : "success";
                    out.println("<p class='" + cssClass + "'>" + esc(result.summary()) + "</p>");
                    if (result.hasErrors()) {
                        out.println("<ul>");
                        for (Map.Entry<String, String> error : result.errors.entrySet()) {
                            out.println("<li class='reason'>" + esc(error.getKey()) + ": " + esc(error.getValue()) + "</li>");
                        }
                        out.println("</ul>");
                    }
                } catch (Exception e) {
                    out.println("<p class='error'>❌ Data fetch failed: " + esc(e.getMessage()) + "</p>");
                }
                out.println("</div>");
            }
            
            // Last write result
            WeatherUpdater.WriteResult write = WeatherUpdater.getLastWrite();
            out.println("<div class='card'>");
            out.println("<h2>Last write to WebCTRL</h2>");
            if (write == null) {
                out.println("<p class='muted'>Ещё не было записи в этой сессии аддона.</p>");
            } else {
                String cssClass = write.isOk() ? "success" : "error";
                out.println("<p class='" + cssClass + "'>" + esc(write.summary()) + "</p>");
                out.println("<p class='muted'>" + new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date(write.timestamp)) + "</p>");
                if (!write.missingPaths.isEmpty()) {
                    out.println("<p class='muted'>Не записаны (первые " + write.missingPaths.size() + "):</p><ul>");
                    for (String path : write.missingPaths) {
                        out.println("<li class='reason'>" + esc(path) + "</li>");
                    }
                    out.println("</ul>");
                }
            }
            out.println("</div>");
            
            // Per location state
            out.println("<div class='card'>");
            out.println("<h2>Locations (" + locations.size() + ")</h2>");
            List<LocationConfig> sortedLocs = new java.util.ArrayList<>(locations.values());
            sortedLocs.sort((a, b) -> Integer.compare(a.getRefreshOrder(), b.getRefreshOrder()));
            for (LocationConfig loc : sortedLocs) {
                LocationWeatherData data = WeatherCache.getForLocation(loc.getProgramPath());
                out.println("<div style='border-bottom:1px solid #222; padding:8px 0;'>");
                out.println("<h3>" + (loc.isEnabled() ? "📍 " : "⏸️ ") + esc(loc.getName())
                        + (loc.isEnabled() ? "" : " (disabled)") + "</h3>");
                out.println("<p class='muted'>" + esc(loc.getProgramPath()) + " &middot; " + esc(loc.getCoordinates()) + "</p>");
                if (data == null) {
                    String reason = WeatherCache.getLastError(loc.getProgramPath());
                    out.println("<p class='error'>⚠️ No weather data" + (reason != null ? ": " + esc(reason) : "") + "</p>");
                } else {
                    long age = System.currentTimeMillis() - data.getLastUpdate();
                    out.println("<p class='success'>✓ " + Math.round(data.getCurrentTemp()) + "°C, "
                            + data.getCurrentHumidity() + "%, "
                            + data.getCurrentWindSpeed() + " m/s &middot; обновлено "
                            + (age / 60000) + " мин назад</p>");
                    if (age > STALE_AGE_MS) {
                        out.println("<p class='error'>⚠️ Данные устарели - нажмите Force Update.</p>");
                    }
                }
                out.println("</div>");
            }
            out.println("</div>");
            
        } catch (Exception e) {
            out.println("<div class='card'>");
            out.println("<p class='error'>❌ Error: " + esc(e.getMessage()) + "</p>");
            out.println("</div>");
        }
        
        out.println("</div>");
        out.println("</body>");
        out.println("</html>");
    }
    
    private static String esc(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    .replace("\"", "&quot;").replace("'", "&#39;");
    }
}

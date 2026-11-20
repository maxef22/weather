package com.controlj.addon.weather.servlet;

import com.controlj.addon.weather.data.LocationConfig;
import com.controlj.addon.weather.util.LocationStore;
import com.controlj.addon.weather.util.SettingsManager;

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

/**
 * ConfigServlet - Handles configuration UI and API for the weather add-on.
 * Manages global settings (API key, units, refresh rate) and location configurations.
 *
 * 1.1.5: the location list is persisted in ${catalina.base}/conf/weather-locations.json,
 *        new locations get a unique id, the generated HTML is escaped.
 * 1.1.6: the API key is never sent to the browser in clear text. The form shows a masked
 *        value ("......ABCD"); leaving the field untouched (empty or masked) keeps the
 *        stored key, typing a new value replaces it.
 */
@WebServlet("/config")
public class ConfigServlet extends HttpServlet {
    
    /** Mask prefix; a submitted value starting with it means "keep the current key". */
    private static final String MASK_PREFIX = "••••••";
    
    // Thread-safe storage for locations (restored from persistent storage on startup)
    private static ConcurrentHashMap<String, LocationConfig> locations = new ConcurrentHashMap<>();
    
    static {
        loadOrInitLocations();
    }
    
    /**
     * Restore the location list from disk, or create the built-in default list on first run.
     */
    private static synchronized void loadOrInitLocations() {
        List<LocationConfig> stored = LocationStore.load();
        if (stored != null && !stored.isEmpty()) {
            for (LocationConfig config : stored) {
                locations.put(config.getId(), config);
            }
            return;
        }
        initDefaultLocations();
        LocationStore.save(locations.values());
    }
    
    /**
     * Initialize default locations with program paths #weather_prog_1 ... #weather_prog_N
     */
    private static void initDefaultLocations() {
        String[][] defaultLocations = {
            {"loc_1", "SHCHELKOVO", "#weather_prog_1", "55.9231,37.9788"},
            {"loc_2", "KLIMOVSK", "#weather_prog_2", "55.3636,37.5373"},
            {"loc_3", "VLADIMIR", "#weather_prog_3", "56.1290,40.4070"},
            {"loc_4", "RYAZAN", "#weather_prog_4", "54.6291,39.7421"},
            {"loc_5", "YAROSLAVL", "#weather_prog_5", "57.6261,39.8845"},
            {"loc_6", "KOROLEV", "#weather_prog_6", "55.9162,37.8545"},
            {"loc_7", "KRASNOGORSK", "#weather_prog_7", "55.8318,37.3306"},
            {"loc_8", "TVERT", "#weather_prog_8", "56.8587,35.9176"},
            {"loc_9", "ELEKTROSTAL", "#weather_prog_9", "55.7858,38.4446"},
            {"loc_10", "TULA", "#weather_prog_10", "54.1930,37.6173"},
            {"loc_11", "PUSHKINO", "#weather_prog_11", "56.0107,37.8467"},
            {"loc_12", "ODINTSOVO", "#weather_prog_12", "55.6789,37.2640"},
            {"loc_13", "KOTELNIKI", "#weather_prog_13", "55.6706,37.8632"},
            {"loc_14", "BALASHIKHA", "#weather_prog_14", "55.8095,37.9581"},
            {"loc_15", "SALAREVO", "#weather_prog_15", "55.6214,37.4222"},
            {"loc_16", "KALUGA", "#weather_prog_16", "54.5138,36.2611"},
            {"loc_17", "KOMMUNARKA", "#weather_prog_17", "55.5690,37.4855"},
            {"loc_18", "MEDVEDKOVO", "#weather_prog_18", "55.8873,37.6427"},
            {"loc_19", "KOSINO", "#weather_prog_19", "55.7042,37.8586"},
            {"loc_20", "MITINO", "#weather_prog_20", "55.8475,37.3643"},
            {"loc_21", "MOSCOW", "#weather_prog_21", "55.7558,37.6173"}
        };
        
        for (String[] loc : defaultLocations) {
            LocationConfig config = new LocationConfig(loc[0], loc[1], loc[2], loc[3]);
            config.setRefreshOrder(suffixNumber(loc[0]));
            config.setEnabled(true);
            locations.put(config.getId(), config);
        }
    }
    
    /** Numeric part of "loc_12" -> 12, 0 when there is none. */
    private static int suffixNumber(String id) {
        if (id == null) return 0;
        int index = id.lastIndexOf('_');
        if (index < 0 || index == id.length() - 1) return 0;
        try {
            return Integer.parseInt(id.substring(index + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    /** Next free "loc_N" id and the corresponding refresh order. */
    private static int nextNumber() {
        int max = 0;
        for (LocationConfig config : locations.values()) {
            int number = suffixNumber(config.getId());
            if (number > max) max = number;
        }
        return max + 1;
    }
    
    private static void persist() {
        LocationStore.save(locations.values());
    }
    
    /**
     * "abcdef123456" -> "......3456". The browser never receives the full key.
     */
    private static String maskKey(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        if (key.length() <= 4) {
            return MASK_PREFIX;
        }
        return MASK_PREFIX + key.substring(key.length() - 4);
    }
    

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        String action = request.getParameter("action");
        
        if ("api".equals(action)) {
            // API endpoint - returns location list as JSON (no secrets)
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            PrintWriter out = response.getWriter();
            
            // Manual JSON construction (no Gson dependency)
            out.print("[");
            boolean first = true;
            for (LocationConfig loc : sortedLocations()) {
                if (!first) out.print(",");
                first = false;
                out.print("{");
                out.print("\"id\":\"" + escapeJson(loc.getId()) + "\",");
                out.print("\"name\":\"" + escapeJson(loc.getName()) + "\",");
                out.print("\"programPath\":\"" + escapeJson(loc.getProgramPath()) + "\",");
                out.print("\"coordinates\":\"" + escapeJson(loc.getCoordinates()) + "\",");
                out.print("\"enabled\":" + loc.isEnabled() + ",");
                out.print("\"refreshOrder\":" + loc.getRefreshOrder());
                out.print("}");
            }
            out.print("]");
            return;
        }
        
        // HTML configuration interface
        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = response.getWriter();
        String ctx = request.getContextPath();
        
        // Load settings from persistent storage
        String apiKey = SettingsManager.getApiKey();
        boolean keyConfigured = apiKey != null && !apiKey.isEmpty();
        String units = SettingsManager.getUnits();
        int refreshRate = SettingsManager.getRefreshRate();
        String databasePath = SettingsManager.getDatabasePath();
        String location = SettingsManager.getLocation();
        
        out.println("<!DOCTYPE html>");
        out.println("<html>");
        out.println("<head><title>Weather Add-on Configuration</title>");
        out.println("<meta charset='UTF-8'>");
        out.println("<style>");
        out.println("body { font-family: Arial, sans-serif; margin: 20px; background: #1a1a1a; }");
        out.println(".container { max-width: 1400px; margin: 0 auto; }");
        out.println(".card { background: #0d0d0d; padding: 20px; border-radius: 8px; margin-bottom: 20px; box-shadow: 0 2px 4px rgba(0,0,0,0.3); }");
        out.println("h1, h2, h3 { color: #ffd700; }");
        out.println("label, p, td, th { color: #ffffff; }");
        out.println("table { border-collapse: collapse; width: 100%; margin-top: 10px; }");
        out.println("th, td { border: 1px solid #333; padding: 8px; text-align: left; font-size: 13px; }");
        out.println("th { background: #1a1a2e; color: white; }");
        out.println(".enabled { color: #4ecdc4; font-weight: bold; }");
        out.println(".disabled { color: #ff6b6b; font-weight: bold; }");
        out.println("input, select { padding: 8px; margin: 5px; border: 1px solid #333; border-radius: 4px; background: #2a2a2a; color: white; }");
        out.println("button { padding: 6px 12px; background: #ffd700; color: #000; border: none; border-radius: 4px; cursor: pointer; margin-right: 4px; }");
        out.println("button:hover { opacity: 0.9; }");
        out.println(".delete-btn { background: #dc3545; color: white; }");
        out.println(".nav { margin-bottom: 20px; }");
        out.println(".nav a { color: #ffd700; text-decoration: none; margin-right: 15px; }");
        out.println(".hint { color: #aaa; font-size: 12px; }");
        out.println(".ok { color: #4ecdc4; font-size: 12px; }");
        out.println(".warn { color: #ff9f43; font-size: 12px; }");
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
        
        out.println("<h1>⚙️ Weather Add-on Configuration</h1>");
        
        // ========== GLOBAL SETTINGS ==========
        out.println("<div class='card'>");
        out.println("<h2>Global Settings</h2>");
        out.println("<form id='configForm' method='post' action='" + ctx + "/config'>");
        
        out.println("<label>API Key (WeatherAPI.com / Visual Crossing):</label><br>");
        if (keyConfigured) {
            out.println("<p class='ok'>✓ Ключ сохранён: <code>" + esc(maskKey(apiKey)) + "</code></p>");
        } else {
            out.println("<p class='warn'>Ключ не задан — погода загружаться не будет. Получите его на "
                    + "<a href='https://www.weatherapi.com/' target='_blank' rel='noopener' style='color:#ffd700'>"
                    + "weatherapi.com</a> (Account → API Keys).</p>");
        }
        out.println("<input type='password' name='apiKey' value='' size='60' autocomplete='new-password' "
                + "placeholder='Новый ключ (оставьте пустым, чтобы не менять)'><br>");
        out.println("<small class='hint'>Полный ключ не отображается в браузере и не передаётся в форму.</small><br>");
        
        out.println("<label>Units:</label><br>");
        out.println("<select name='units'>");
        out.println("<option value='metric' " + (units.equals("metric") ? "selected" : "") + ">Metric (°C, m/s)</option>");
        out.println("<option value='us' " + (units.equals("us") ? "selected" : "") + ">Imperial (°F, mph)</option>");
        out.println("</select><br>");
        
        out.println("<label>Refresh Rate (minutes, 15-180):</label><br>");
        out.println("<input type='number' name='refreshRate' value='" + refreshRate + "' min='15' max='180'><br>");
        
        out.println("<label>Default Database Path (GEO tree):</label><br>");
        out.println("<input type='text' name='databasePath' value='" + esc(databasePath) + "' size='50' placeholder='#weather_prog'><br>");
        
        out.println("<label>Default Location:</label><br>");
        out.println("<input type='text' name='location' value='" + esc(location) + "' size='50' placeholder='Moscow, Russia'><br>");
        
        out.println("<button type='submit'>💾 Save Global Settings</button>");
        out.println("</form>");
        out.println("</div>");
        
        // ========== ADD NEW LOCATION FORM ==========
        out.println("<div class='card'>");
        out.println("<h2>➕ Add New Location</h2>");
        out.println("<form id='addLocationForm' onsubmit='addLocation(event)'>");
        out.println("<label>Location Name:</label><br>");
        out.println("<input type='text' id='locName' placeholder='e.g., LONDON' required><br>");
        out.println("<label>Program Path (GEO tree):</label><br>");
        out.println("<input type='text' id='programPath' placeholder='#weather_prog_N (leave empty to auto-assign)'><br>");
        out.println("<label>City or Coordinates:</label><br>");
        out.println("<input type='text' id='query' placeholder='London or 51.5074,-0.1278' required><br>");
        out.println("<small class='hint'>Locations are saved to weather-locations.json and survive restarts.</small><br><br>");
        out.println("<button type='submit'>➕ Add Location</button>");
        out.println("</form>");
        out.println("</div>");
        
        // ========== LOCATIONS TABLE ==========
        List<LocationConfig> sortedLocs = sortedLocations();
        out.println("<div class='card'>");
        out.println("<h2>📍 Locations (" + sortedLocs.size() + ")</h2>");
        out.println("<table>");
        out.println("<tr><th>ID</th><th>Name</th><th>Program Path</th><th>Coordinates</th><th>Enabled</th><th>Order</th><th>Actions</th></tr>");
        
        for (LocationConfig loc : sortedLocs) {
            out.println("<tr>");
            out.println("<td>" + esc(loc.getId()) + "</td>");
            out.println("<td>" + esc(loc.getName()) + "</td>");
            out.println("<td>" + esc(loc.getProgramPath()) + "</td>");
            out.println("<td>" + esc(loc.getCoordinates()) + "</td>");
            out.println("<td class='" + (loc.isEnabled() ? "enabled" : "disabled") + "'>" + (loc.isEnabled() ? "✓ Enabled" : "✗ Disabled") + "</td>");
            out.println("<td>" + loc.getRefreshOrder() + "</td>");
            out.println("<td>");
            out.println("<button onclick='toggleLocation(\"" + esc(loc.getId()) + "\")'>Toggle</button>");
            out.println("<button onclick='editLocation(\"" + esc(loc.getId()) + "\")'>Edit</button>");
            out.println("<button class='delete-btn' onclick='deleteLocation(\"" + esc(loc.getId()) + "\")'>Delete</button>");
            out.println("</td>");
            out.println("</tr>");
        }
        out.println("</table>");
        out.println("</div>");
        
        out.println("</div>");
        
        // ========== JAVASCRIPT ==========
        out.println("<script>");
        out.println("const CTX = '" + escapeJson(ctx) + "';");
        out.println("function addLocation(event) {");
        out.println("    event.preventDefault();");
        out.println("    const name = document.getElementById('locName').value;");
        out.println("    const programPath = document.getElementById('programPath').value;");
        out.println("    const query = document.getElementById('query').value;");
        out.println("    fetch(CTX + '/config', {");
        out.println("        method: 'POST',");
        out.println("        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },");
        out.println("        body: 'action=addLocation&name=' + encodeURIComponent(name) +");
        out.println("              '&programPath=' + encodeURIComponent(programPath) +");
        out.println("              '&query=' + encodeURIComponent(query)");
        out.println("    })");
        out.println("    .then(response => response.json())");
        out.println("    .then(data => {");
        out.println("        if (data.success) {");
        out.println("            alert('Location added. Program path: ' + data.programPath);");
        out.println("            location.reload();");
        out.println("        } else {");
        out.println("            alert('Error: ' + data.error);");
        out.println("        }");
        out.println("    })");
        out.println("    .catch(error => console.error('Error:', error));");
        out.println("}");
        
        out.println("function toggleLocation(id) {");
        out.println("    fetch(CTX + '/config', {");
        out.println("        method: 'POST',");
        out.println("        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },");
        out.println("        body: 'action=toggleLocation&id=' + encodeURIComponent(id)");
        out.println("    })");
        out.println("    .then(() => location.reload());");
        out.println("}");
        
        out.println("function deleteLocation(id) {");
        out.println("    if (confirm('Delete this location?')) {");
        out.println("        fetch(CTX + '/config', {");
        out.println("            method: 'POST',");
        out.println("            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },");
        out.println("            body: 'action=deleteLocation&id=' + encodeURIComponent(id)");
        out.println("        })");
        out.println("        .then(() => location.reload());");
        out.println("    }");
        out.println("}");
        
        out.println("function editLocation(id) {");
        out.println("    const newName = prompt('Enter new name:');");
        out.println("    if (newName === null) return;");
        out.println("    const newPath = prompt('Enter program path:');");
        out.println("    if (newPath === null) return;");
        out.println("    const newQuery = prompt('Enter city or coordinates:');");
        out.println("    if (newQuery === null) return;");
        out.println("    fetch(CTX + '/config', {");
        out.println("        method: 'POST',");
        out.println("        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },");
        out.println("        body: 'action=updateLocation&id=' + encodeURIComponent(id) +");
        out.println("              '&name=' + encodeURIComponent(newName) +");
        out.println("              '&programPath=' + encodeURIComponent(newPath) +");
        out.println("              '&query=' + encodeURIComponent(newQuery)");
        out.println("    })");
        out.println("    .then(() => location.reload());");
        out.println("}");
        out.println("</script>");
        
        out.println("</body>");
        out.println("</html>");
    }
    
    private static List<LocationConfig> sortedLocations() {
        List<LocationConfig> sorted = new ArrayList<>(locations.values());
        sorted.sort((a, b) -> Integer.compare(a.getRefreshOrder(), b.getRefreshOrder()));
        return sorted;
    }
    
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
    
    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        // Browsers submit the page as UTF-8; without this the container decodes as ISO-8859-1
        // and non-ASCII values (for example location names) get corrupted.
        request.setCharacterEncoding("UTF-8");
        
        String action = request.getParameter("action");
        String ctx = request.getContextPath();
        
        // Save global settings (the settings form sends no action parameter)
        if (action == null) {
            String newApiKey = request.getParameter("apiKey");
            String newUnits = request.getParameter("units");
            String newRefreshRate = request.getParameter("refreshRate");
            String newDatabasePath = request.getParameter("databasePath");
            String newLocation = request.getParameter("location");
            
            // Empty field means "keep the current key"; the masked value is never submitted.
            if (newApiKey != null && !newApiKey.trim().isEmpty()) {
                SettingsManager.saveApiKey(newApiKey.trim());
            }
            if (newUnits != null) {
                SettingsManager.saveUnits(newUnits);
            }
            if (newRefreshRate != null) {
                try {
                    int rate = Integer.parseInt(newRefreshRate.trim());
                    if (rate < 15) rate = 15;
                    if (rate > 180) rate = 180;
                    SettingsManager.saveRefreshRate(rate);
                } catch (NumberFormatException e) {
                    // keep the previous value
                }
            }
            if (newDatabasePath != null) {
                SettingsManager.saveDatabasePath(newDatabasePath);
            }
            if (newLocation != null) {
                SettingsManager.saveLocation(newLocation);
            }
            
            response.sendRedirect(ctx + "/config");
            return;
        }
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        
        // Add new location
        if ("addLocation".equals(action)) {
            String name = request.getParameter("name");
            String programPath = request.getParameter("programPath");
            String query = request.getParameter("query");
            
            if (name != null && !name.isEmpty() && query != null && !query.isEmpty()) {
                int number = nextNumber();
                String id = "loc_" + number;
                String targetPath = (programPath != null && !programPath.trim().isEmpty())
                        ? programPath.trim() : "#weather_prog_" + number;
                
                LocationConfig newLoc = new LocationConfig(id, name.trim().toUpperCase(), targetPath, query.trim());
                newLoc.setEnabled(true);
                newLoc.setRefreshOrder(number);
                locations.put(id, newLoc);
                persist();
                
                out.print("{\"success\":true, \"id\":\"" + escapeJson(id) + "\", \"programPath\":\"" + escapeJson(targetPath) + "\"}");
            } else {
                out.print("{\"success\":false, \"error\":\"Missing required fields\"}");
            }
            return;
        }
        
        // Enable/disable location
        if ("toggleLocation".equals(action)) {
            String id = request.getParameter("id");
            LocationConfig loc = locations.get(id);
            if (loc != null) {
                loc.setEnabled(!loc.isEnabled());
                persist();
                out.print("{\"success\":true, \"enabled\":" + loc.isEnabled() + "}");
            } else {
                out.print("{\"success\":false, \"error\":\"Location not found\"}");
            }
            return;
        }
        
        // Delete location
        if ("deleteLocation".equals(action)) {
            String id = request.getParameter("id");
            LocationConfig removed = locations.remove(id);
            if (removed != null) {
                persist();
                out.print("{\"success\":true}");
            } else {
                out.print("{\"success\":false, \"error\":\"Location not found\"}");
            }
            return;
        }
        
        // Update location
        if ("updateLocation".equals(action)) {
            String id = request.getParameter("id");
            String name = request.getParameter("name");
            String programPath = request.getParameter("programPath");
            String query = request.getParameter("query");
            
            LocationConfig loc = locations.get(id);
            if (loc != null) {
                if (name != null && !name.trim().isEmpty()) loc.setName(name.trim().toUpperCase());
                if (programPath != null && !programPath.trim().isEmpty()) loc.setProgramPath(programPath.trim());
                if (query != null && !query.trim().isEmpty()) loc.setCoordinates(query.trim());
                persist();
                out.print("{\"success\":true}");
            } else {
                out.print("{\"success\":false, \"error\":\"Location not found\"}");
            }
            return;
        }
        
        out.print("{\"success\":false, \"error\":\"Unknown action\"}");
    }
    
    // Getters for other classes
    public static ConcurrentHashMap<String, LocationConfig> getLocations() { return locations; }
}

package com.controlj.addon.weather;

import com.controlj.addon.weather.data.WeatherCache;
import com.controlj.addon.weather.data.HourlyForecast;
import com.controlj.addon.weather.data.DailyForecast;
import com.controlj.addon.weather.data.LocationConfig;
import com.controlj.addon.weather.data.WeatherCache.LocationWeatherData;
import com.controlj.addon.weather.service.WeatherApiService;
import com.controlj.addon.weather.util.SettingsManager;
import com.controlj.addon.weather.servlet.ConfigServlet;
import com.controlj.green.addonsupport.access.DirectAccess;
import com.controlj.green.addonsupport.access.FieldAccessFactory;
import com.controlj.green.addonsupport.access.Location;
import com.controlj.green.addonsupport.access.SystemConnection;
import com.controlj.green.addonsupport.access.SystemTree;
import com.controlj.green.addonsupport.access.WriteAction;
import com.controlj.green.addonsupport.access.WritableSystemAccess;
import com.controlj.green.addonsupport.access.aspect.PresentValue;
import com.controlj.green.addonsupport.access.value.FloatValue;
import com.controlj.green.addonsupport.access.value.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * WeatherUpdater - Handles fetching weather data from the configured API service
 * and updating the cache for all configured locations.
 *
 * 1.1.4: single update at a time, retries, per-location errors, no 15 minute block.
 * 1.1.5: all locations are written to WebCTRL in ONE write action (was one transaction
 *        per location), points are checked with isWritable()/instanceof FloatValue,
 *        and the result of the last write is kept for the UI.
 */
public class WeatherUpdater {
    
    private static final ReentrantLock UPDATE_LOCK = new ReentrantLock();
    
    /** Protects against two full updates started back to back (manual + scheduled). */
    private static final long MIN_SCHEDULED_INTERVAL_MS = 60 * 1000L;
    
    /** How many missing point paths are collected for the UI. */
    private static final int MAX_REPORTED_MISSING = 20;
    
    private static volatile long lastAttempt = 0L;
    private static volatile long lastSuccess = 0L;
    private static volatile WriteResult lastWrite = null;
    
    /**
     * Scheduled update: skipped when another update is already running or when
     * the previous attempt was less than a minute ago.
     */
    public static UpdateResult updateAllLocations() throws Exception {
        return updateAllLocations(false);
    }
    
    /**
     * Update weather data for all enabled locations.
     *
     * @param force true for an explicit user action (Force Update button) - runs even
     *              if a scheduled update happened a moment ago.
     */
    public static UpdateResult updateAllLocations(boolean force) throws Exception {
        if (!UPDATE_LOCK.tryLock()) {
            System.out.println("[WeatherUpdater] Skipping update - another update is already running");
            return UpdateResult.busy();
        }
        try {
            long now = System.currentTimeMillis();
            if (!force && now - lastAttempt < MIN_SCHEDULED_INTERVAL_MS) {
                System.out.println("[WeatherUpdater] Skipping scheduled update - previous attempt at "
                        + new Date(lastAttempt));
                return UpdateResult.skipped();
            }
            lastAttempt = now;
            
            ConcurrentHashMap<String, LocationConfig> locations = ConfigServlet.getLocations();
            
            // Get API key from persistent storage
            String apiKey = SettingsManager.getApiKey();
            
            if (apiKey == null || apiKey.isEmpty()) {
                throw new Exception("API key not configured");
            }
            
            System.out.println("[WeatherUpdater] Starting " + (force ? "forced" : "scheduled")
                    + " update for " + locations.size() + " locations");
            
            int total = 0;
            int ok = 0;
            int failed = 0;
            Map<String, String> errors = new LinkedHashMap<>();
            
            for (LocationConfig loc : locations.values()) {
                if (!loc.isEnabled()) {
                    continue;
                }
                total++;
                System.out.println("[WeatherUpdater] Processing " + total + "/" + locations.size() + ": " + loc.getName());
                try {
                    // ===== ЕДИНЫЙ ВЫЗОВ ЧЕРЕЗ WeatherApiService =====
                    // WeatherApiService сам решит, к какому провайдеру идти
                    // (Visual Crossing или WeatherAPI.com) на основе флага USE_VISUAL_CROSSING,
                    // и сам повторит запрос при сетевой ошибке.
                    LocationWeatherData data = WeatherApiService.fetchWeatherData(loc, apiKey);
                    
                    // Save to cache for this specific location (also clears the last error)
                    WeatherCache.updateForLocation(
                        loc.getProgramPath(),
                        data.getCurrentTemp(),
                        data.getCurrentHumidity(),
                        data.getCurrentConditions(),
                        data.getCurrentWindSpeed(),
                        data.getCurrentWindDir(),
                        data.getCurrentFeelsLike(),
                        data.getCurrentPressure(),
                        data.getHourlyForecast(),
                        data.getDailyForecast()
                    );
                    
                    ok++;
                    System.out.println("[WeatherUpdater] Updated " + loc.getName() + 
                                     ": temp=" + data.getCurrentTemp() + "°C, " +
                                     "wind=" + data.getCurrentWindSpeed() + "m/s, " +
                                     data.getHourlyForecast().size() + " hours, " +
                                     data.getDailyForecast().size() + " days");
                    
                    // Delay between requests to avoid rate limiting
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    System.out.println("[WeatherUpdater] Update interrupted");
                    break;
                } catch (Exception e) {
                    failed++;
                    String message = (e.getMessage() == null || e.getMessage().isEmpty())
                            ? e.getClass().getName() : e.getMessage();
                    errors.put(loc.getName(), message);
                    WeatherCache.setLastError(loc.getProgramPath(), message);
                    System.err.println("[WeatherUpdater] Failed to update " + loc.getName() + ": " + message);
                }
            }
            
            if (ok > 0) {
                lastSuccess = System.currentTimeMillis();
                System.out.println("[WeatherUpdater] Update completed: " + ok + " ok, " + failed + " failed");
                
                // ===== ЗАПИСЬ В WebCTRL (одной транзакцией на все локации) =====
                WriteResult writeResult = writeCachedToWebCTRL();
                if (writeResult.error != null) {
                    System.err.println("[WeatherUpdater] WebCTRL write failed: " + writeResult.error);
                }
            } else {
                System.out.println("[WeatherUpdater] Nothing was updated (" + failed
                        + " failed) - skipping WebCTRL write");
            }
            
            return new UpdateResult(total, ok, failed, errors, false, false);
        } finally {
            UPDATE_LOCK.unlock();
        }
    }
    
    /**
     * Result of one update run. Used by the servlets to show what actually happened
     * instead of a generic "no data".
     */
    public static class UpdateResult {
        public final boolean busy;
        public final boolean skipped;
        public final int total;
        public final int ok;
        public final int failed;
        public final Map<String, String> errors;
        
        UpdateResult(int total, int ok, int failed, Map<String, String> errors,
                     boolean busy, boolean skipped) {
            this.total = total;
            this.ok = ok;
            this.failed = failed;
            this.errors = errors;
            this.busy = busy;
            this.skipped = skipped;
        }
        
        static UpdateResult busy() {
            return new UpdateResult(0, 0, 0, Collections.<String, String>emptyMap(), true, false);
        }
        
        static UpdateResult skipped() {
            return new UpdateResult(0, 0, 0, Collections.<String, String>emptyMap(), false, true);
        }
        
        public boolean isBusy() { return busy; }
        public boolean isSkipped() { return skipped; }
        public boolean hasErrors() { return failed > 0; }
        
        /** Short human readable summary for the UI log. */
        public String summary() {
            if (busy) return "Обновление уже выполняется другим запросом - подождите и обновите страницу";
            if (skipped) return "Обновление пропущено (предыдущая попытка была меньше минуты назад)";
            return "Локаций обработано: " + total + ", успешно: " + ok + ", с ошибкой: " + failed;
        }
    }
    
    /**
     * Result of writing cached values into the WebCTRL tree.
     */
    public static class WriteResult {
        public int locations;
        public int written;
        public int missing;
        public int notWritable;
        public long timestamp;
        public String error;
        public final List<String> missingPaths = new ArrayList<>();
        
        public boolean isOk() { return error == null; }
        
        public String summary() {
            if (error != null) return "Запись не выполнена: " + error;
            if (locations == 0) return "Нет данных в кэше - записывать нечего";
            return "Записано значений: " + written + " в " + locations
                    + " локаций (нет точек: " + missing + ", не для записи: " + notWritable + ")";
        }
    }
    
    /**
     * Writes the cached weather values of all enabled locations into the WebCTRL tree.
     * Everything happens in a single write action / transaction.
     */
    public static WriteResult writeCachedToWebCTRL() {
        WriteResult result = new WriteResult();
        result.timestamp = System.currentTimeMillis();
        
        ConcurrentHashMap<String, LocationConfig> locations = ConfigServlet.getLocations();
        final List<String> paths = new ArrayList<>();
        final List<LocationWeatherData> dataList = new ArrayList<>();
        
        for (LocationConfig loc : locations.values()) {
            if (!loc.isEnabled()) {
                continue;
            }
            LocationWeatherData data = WeatherCache.getForLocation(loc.getProgramPath());
            if (data == null) {
                System.out.println("[WeatherUpdater] No data for location: " + loc.getName());
                continue;
            }
            paths.add(loc.getProgramPath());
            dataList.add(data);
        }
        
        result.locations = paths.size();
        if (paths.isEmpty()) {
            System.out.println("[WeatherUpdater] Nothing to write - no cached data");
            lastWrite = result;
            return result;
        }
        
        final WriteResult[] holder = new WriteResult[] { result };
        try {
            SystemConnection connection = DirectAccess.getDirectAccess().getRootSystemConnection();
            connection.runWriteAction(
                FieldAccessFactory.newFieldAccess(),
                "Weather add-on: write weather data for " + paths.size() + " locations",
                new WriteAction() {
                    @Override
                    public void execute(WritableSystemAccess access) throws Exception {
                        Location root = access.getTree(SystemTree.Geographic).getRoot();
                        WriteResult current = new WriteResult();
                        current.locations = paths.size();
                        for (int i = 0; i < paths.size(); i++) {
                            writeLocation(root, paths.get(i), dataList.get(i), current);
                        }
                        holder[0] = current;
                    }
                });
            result = holder[0];
        } catch (Exception e) {
            result.error = (e.getMessage() == null) ? e.getClass().getName() : e.getMessage();
            System.err.println("[WeatherUpdater] WebCTRL write failed: " + result.error);
            e.printStackTrace();
        }
        
        result.timestamp = System.currentTimeMillis();
        lastWrite = result;
        
        if (result.error == null) {
            System.out.println("[WeatherUpdater] WebCTRL write completed: " + result.written
                    + " values, " + result.locations + " locations, missing points: " + result.missing
                    + ", not writable: " + result.notWritable);
            for (String path : result.missingPaths) {
                System.out.println("[WeatherUpdater] Not written: " + path);
            }
        }
        return result;
    }
    
    /** @return statistics of the last write, or null if nothing has been written yet. */
    public static WriteResult getLastWrite() {
        return lastWrite;
    }
    
    /**
     * Writes one location: 12 hourly values (temperature, humidity, wind speed, wind direction,
     * enthalpy, dew point, humidity ratio) and up to 7 daily values (max / min temperature).
     */
    private static void writeLocation(Location root, String programPath,
                                      LocationWeatherData data, WriteResult result) {
        int count = 0;
        
        List<HourlyForecast> hourlyForecast = data.getHourlyForecast();
        if (hourlyForecast != null) {
            for (int hour = 0; hour < 12 && hour < hourlyForecast.size(); hour++) {
                HourlyForecast hf = hourlyForecast.get(hour);
                count += writePoint(root, programPath + "/wf" + hour + "_highesttemperature", hf.getTemp(), result);
                count += writePoint(root, programPath + "/wf" + hour + "_avghumidity", hf.getHumidity(), result);
                count += writePoint(root, programPath + "/wf" + hour + "_windspeed", hf.getWindSpeed(), result);
                count += writePoint(root, programPath + "/wf" + hour + "_winddirection", hf.getWindDir(), result);
                count += writePoint(root, programPath + "/wf" + hour + "_enthalpy", hf.getEnthalpy(), result);
                count += writePoint(root, programPath + "/wf" + hour + "_dewpoint", hf.getDewPoint(), result);
                count += writePoint(root, programPath + "/wf" + hour + "_humidityratio", hf.getHumidityRatioGPerKg(), result);
            }
        }
        
        List<DailyForecast> dailyForecast = data.getDailyForecast();
        if (dailyForecast != null) {
            for (int day = 0; day < 7 && day < dailyForecast.size(); day++) {
                DailyForecast df = dailyForecast.get(day);
                count += writePoint(root, programPath + "/wd" + day + "_highesttemperature", df.getMaxTemp(), result);
                count += writePoint(root, programPath + "/wd" + day + "_mintemperature", df.getMinTemp(), result);
            }
        }
        
        result.written += count;
        System.out.println("[WeatherUpdater] Wrote " + count + " values to " + programPath);
    }
    
    /** @return 1 when the value was written, 0 otherwise (counters are updated in {@code result}). */
    private static int writePoint(Location root, String path, double value, WriteResult result) {
        try {
            Location point = root.getDescendant(path);
            PresentValue presentValue = point.getAspect(PresentValue.class);
            Value current = presentValue.getValue();
            if (current instanceof FloatValue && current.isWritable()) {
                ((FloatValue) current).set((float) value);
                return 1;
            }
            result.notWritable++;
            if (result.missingPaths.size() < MAX_REPORTED_MISSING) {
                result.missingPaths.add(path + " (not writable)");
            }
        } catch (Exception e) {
            // UnresolvableException / NoSuchAspectException - point does not exist
            result.missing++;
            if (result.missingPaths.size() < MAX_REPORTED_MISSING) {
                result.missingPaths.add(path);
            }
        }
        return 0;
    }
}

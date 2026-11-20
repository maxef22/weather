package com.controlj.addon.weather.util;

import com.controlj.addon.weather.data.LocationConfig;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * LocationStore - persistent storage for the configured weather locations.
 *
 * Kept next to the add-on settings file:
 *   ${catalina.base}/conf/weather-locations.json
 *
 * 1.1.5: before this the location list lived only in memory and every change
 * made in /config was lost on the next WebCTRL restart.
 */
public class LocationStore {

    private static final String FILE_NAME = "weather-locations.json";

    private static File storeFile() {
        String base = System.getProperty("catalina.base");
        if (base == null || base.isEmpty()) {
            base = ".";
        }
        return new File(base, "conf" + File.separator + FILE_NAME);
    }

    /**
     * @return stored locations, or null when the file is missing or cannot be read
     *         (the caller then falls back to the built-in default list).
     */
    public static List<LocationConfig> load() {
        File file = storeFile();
        if (!file.exists()) {
            System.out.println("[LocationStore] No saved locations at " + file + ", defaults will be used");
            return null;
        }
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) > 0) {
                sb.append(buffer, 0, read);
            }
            JSONArray array = new JSONArray(sb.toString());
            List<LocationConfig> result = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                LocationConfig config = new LocationConfig(
                        object.optString("id", "loc_" + (i + 1)),
                        object.optString("name", ""),
                        object.optString("programPath", ""),
                        object.optString("coordinates", ""));
                config.setEnabled(object.optBoolean("enabled", true));
                config.setRefreshOrder(object.optInt("refreshOrder", i + 1));
                if (config.getProgramPath() != null && !config.getProgramPath().isEmpty()) {
                    result.add(config);
                }
            }
            System.out.println("[LocationStore] Loaded " + result.size() + " locations from " + file);
            return result;
        } catch (Exception e) {
            System.err.println("[LocationStore] Failed to read " + file + ": " + e);
            return null;
        }
    }

    /**
     * Writes the current location list. Returns false when the file cannot be written
     * (for example when the WebCTRL service has no rights on the conf directory).
     */
    public static boolean save(Collection<LocationConfig> locations) {
        File file = storeFile();
        JSONArray array = new JSONArray();
        for (LocationConfig config : locations) {
            JSONObject object = new JSONObject();
            object.put("id", config.getId() == null ? "" : config.getId());
            object.put("name", config.getName() == null ? "" : config.getName());
            object.put("programPath", config.getProgramPath() == null ? "" : config.getProgramPath());
            object.put("coordinates", config.getCoordinates() == null ? "" : config.getCoordinates());
            object.put("enabled", config.isEnabled());
            object.put("refreshOrder", config.getRefreshOrder());
            array.put(object);
        }
        try {
            File dir = file.getParentFile();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                writer.write(array.toString(2));
            }
            System.out.println("[LocationStore] Saved " + array.length() + " locations to " + file);
            return true;
        } catch (Exception e) {
            System.err.println("[LocationStore] Failed to write " + file
                    + " (location changes will be lost after restart): " + e);
            return false;
        }
    }
}

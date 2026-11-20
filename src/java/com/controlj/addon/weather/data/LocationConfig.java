package com.controlj.addon.weather.data;

/**
 * LocationConfig - Configuration data for a single weather location.
 * Stores all settings needed to fetch weather data for a specific location
 * and map it to WebCTRL program points.
 */
public class LocationConfig {
    private String id;              // Unique ID (loc_0 ... loc_N)
    private String name;            // Display name (e.g., "Moscow", "Saint Petersburg")
    private String programPath;     // WebCTRL GEO tree path (e.g., "#weather_prog_moscow")
    private String coordinates;     // Query string - city name or "lat,lon" coordinates
    private boolean enabled;        // Whether this location is active for updates
    private int refreshOrder;       // Update priority order (0 = first)
    
    /**
     * Default constructor (required for JSON deserialization).
     */
    public LocationConfig() {}
    
    /**
     * Constructor with required fields.
     * 
     * @param id Unique identifier for the location
     * @param name Display name shown in UI
     * @param programPath WebCTRL GEO tree path for the program
     * @param coordinates City name or lat,lon coordinates for API query
     */
    public LocationConfig(String id, String name, String programPath, String coordinates) {
        this.id = id;
        this.name = name;
        this.programPath = programPath;
        this.coordinates = coordinates;
        this.enabled = true;
        this.refreshOrder = 0;
    }
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getProgramPath() {
        return programPath;
    }
    
    public void setProgramPath(String programPath) {
        this.programPath = programPath;
    }
    
    public String getCoordinates() {
        return coordinates;
    }
    
    public void setCoordinates(String coordinates) {
        this.coordinates = coordinates;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public int getRefreshOrder() {
        return refreshOrder;
    }
    
    public void setRefreshOrder(int refreshOrder) {
        this.refreshOrder = refreshOrder;
    }
}
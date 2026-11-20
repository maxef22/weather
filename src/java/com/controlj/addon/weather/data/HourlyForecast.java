package com.controlj.addon.weather.data;

/**
 * HourlyForecast - Data model for hourly weather forecast.
 * Contains temperature, humidity, wind speed, wind direction, and conditions for a specific hour.
 *
 * 1.2.0: also carries the absolute pressure and the psychrometric values derived from
 * temperature + humidity (dew point, humidity ratio, enthalpy).
 */
public class HourlyForecast {
    private String time;        // Time in HH:mm format (local time)
    private double temp;        // Temperature in degrees Celsius
    private int humidity;       // Relative humidity percentage
    private String conditions;  // Weather description (e.g., "Partly cloudy", "Rain")
    private double windSpeed;   // Wind speed in m/s
    private double windDir;     // Wind direction in degrees (0-360)

    private double pressureHpa;    // Absolute pressure, hPa
    private double dewPoint;       // Dew point, °C
    private double humidityRatio;  // kg water / kg dry air
    private double enthalpy;       // kJ per kg dry air

    // Getters and Setters
    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }
    
    public double getTemp() { return temp; }
    public void setTemp(double temp) { this.temp = temp; }
    
    public int getHumidity() { return humidity; }
    public void setHumidity(int humidity) { this.humidity = humidity; }
    
    public String getConditions() { return conditions; }
    public void setConditions(String conditions) { this.conditions = conditions; }
    
    public double getWindSpeed() { return windSpeed; }
    public void setWindSpeed(double windSpeed) { this.windSpeed = windSpeed; }
    
    public double getWindDir() { return windDir; }
    public void setWindDir(double windDir) { this.windDir = windDir; }

    public double getPressureHpa() { return pressureHpa; }
    public void setPressureHpa(double pressureHpa) { this.pressureHpa = pressureHpa; }

    public double getDewPoint() { return dewPoint; }
    public void setDewPoint(double dewPoint) { this.dewPoint = dewPoint; }

    public double getHumidityRatio() { return humidityRatio; }
    public void setHumidityRatio(double humidityRatio) { this.humidityRatio = humidityRatio; }

    public double getEnthalpy() { return enthalpy; }
    public void setEnthalpy(double enthalpy) { this.enthalpy = enthalpy; }

    /** Humidity ratio in g/kg, rounded to one decimal place. */
    public double getHumidityRatioGPerKg() {
        return Math.round(humidityRatio * 1000.0 * 10.0) / 10.0;
    }
    
    /**
     * Converts wind direction from degrees to cardinal direction string.
     * 
     * @return Cardinal direction (N, NE, E, SE, S, SW, W, NW, etc.)
     */
    public String getWindDirectionString() {
        String[] directions = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
                               "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};
        int index = (int) Math.round(windDir / 22.5) % 16;
        return directions[index];
    }
}

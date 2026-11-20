package com.controlj.addon.weather.data;

public class DailyForecast {
    private String date;
    private double maxTemp;
    private double minTemp;
    private String conditions;
    
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public double getMaxTemp() { return maxTemp; }
    public void setMaxTemp(double maxTemp) { this.maxTemp = maxTemp; }
    public double getMinTemp() { return minTemp; }
    public void setMinTemp(double minTemp) { this.minTemp = minTemp; }
    public String getConditions() { return conditions; }
    public void setConditions(String conditions) { this.conditions = conditions; }
}
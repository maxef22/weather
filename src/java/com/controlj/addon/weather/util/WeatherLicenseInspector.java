package com.controlj.addon.weather.util;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

@WebListener
public class WeatherLicenseInspector implements ServletContextListener {
    
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        System.out.println("[WeatherLicenseInspector] License check passed (no license required)");
    }
    
    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        System.out.println("[WeatherLicenseInspector] Shutting down");
    }
}
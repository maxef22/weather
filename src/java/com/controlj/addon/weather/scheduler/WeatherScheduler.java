package com.controlj.addon.weather.scheduler;

import com.controlj.addon.weather.WeatherUpdater;
import com.controlj.addon.weather.util.SettingsManager;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * WeatherScheduler - Automatically updates weather data at regular intervals.
 *
 * 1.1.4 changes:
 *  - the first update runs in a background thread after a short delay instead of
 *    blocking the add-on (and WebCTRL) startup;
 *  - the refresh rate is clamped to the same 15..180 minutes range as in the UI,
 *    so the schedule and the update logic no longer contradict each other.
 */
public class WeatherScheduler implements ServletContextListener {
    
    private ScheduledExecutorService scheduler;
    
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        System.out.println("[WeatherScheduler] Starting...");
        
        // Get refresh rate from persistent storage
        int refreshRate = SettingsManager.getRefreshRate();
        if (refreshRate < 15) refreshRate = 15;
        if (refreshRate > 180) refreshRate = 180;
        final int interval = refreshRate;
        
        // Initial update must not block context startup (WebCTRL may still be
        // bringing controllers online). Run it in the background after a delay.
        Thread initial = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(30 * 1000L);
                    System.out.println("[WeatherScheduler] Running initial update...");
                    WeatherUpdater.UpdateResult result = WeatherUpdater.updateAllLocations(true);
                    System.out.println("[WeatherScheduler] Initial update: " + result.summary());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    System.err.println("[WeatherScheduler] Initial update failed: " + e.getMessage());
                }
            }
        }, "weather-initial-update");
        initial.setDaemon(true);
        initial.start();
        
        // Schedule periodic updates
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    System.out.println("[WeatherScheduler] Running scheduled update...");
                    WeatherUpdater.UpdateResult result = WeatherUpdater.updateAllLocations(false);
                    System.out.println("[WeatherScheduler] Scheduled update: " + result.summary());
                } catch (Exception e) {
                    System.err.println("[WeatherScheduler] Scheduled update failed: " + e.getMessage());
                }
            }
        }, interval, interval, TimeUnit.MINUTES);
        
        System.out.println("[WeatherScheduler] Started with " + interval + " minute interval");
    }
    
    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        System.out.println("[WeatherScheduler] Shutting down...");
        if (scheduler != null) {
            scheduler.shutdownNow();
            try {
                scheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                System.err.println("[WeatherScheduler] Shutdown interrupted");
            }
        }
    }
}

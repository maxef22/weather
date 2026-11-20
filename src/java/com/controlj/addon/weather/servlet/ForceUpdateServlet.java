package com.controlj.addon.weather.servlet;

import com.controlj.addon.weather.WeatherUpdater;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

/**
 * ForceUpdateServlet - explicit user actions:
 *   POST /forceupdate                - fetch fresh data for all locations and write them
 *   POST /forceupdate?writeOnly=1    - only rewrite WebCTRL points from the current cache
 *
 * Both actions return to the write page after a short pause.
 */
@WebServlet("/forceupdate")
public class ForceUpdateServlet extends HttpServlet {
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = response.getWriter();
        String ctx = request.getContextPath();
        boolean writeOnly = request.getParameter("writeOnly") != null;
        
        out.println("<!DOCTYPE html>");
        out.println("<html><head><title>Force Update</title>");
        out.println("<meta charset='UTF-8'>");
        out.println("<meta http-equiv='refresh' content='3;url=" + ctx + "/writeweather'>");
        out.println("<style>");
        out.println("body { background: #1a1a1a; color: #ffd700; font-family: Arial; text-align: center; padding: 50px; }");
        out.println(".reason { color: #ff9f43; font-family: Consolas, monospace; font-size: 12px; }");
        out.println("ul { display: inline-block; text-align: left; }");
        out.println("</style>");
        out.println("</head><body>");
        
        try {
            if (writeOnly) {
                WeatherUpdater.WriteResult write = WeatherUpdater.writeCachedToWebCTRL();
                out.println("<h2 style='color:" + (write.isOk() ? "#4ecdc4" : "#ff6b6b") + "'>"
                        + esc(write.summary()) + "</h2>");
                if (!write.missingPaths.isEmpty()) {
                    out.println("<ul>");
                    for (String path : write.missingPaths) {
                        out.println("<li class='reason'>" + esc(path) + "</li>");
                    }
                    out.println("</ul>");
                }
            } else {
                WeatherUpdater.UpdateResult result = WeatherUpdater.updateAllLocations(true);
                boolean good = !result.hasErrors() && !result.isBusy() && !result.isSkipped() && result.ok > 0;
                out.println("<h2 style='color:" + (good ? "#4ecdc4" : "#ff6b6b") + "'>"
                        + esc(result.summary()) + "</h2>");
                if (result.hasErrors()) {
                    out.println("<ul>");
                    for (Map.Entry<String, String> error : result.errors.entrySet()) {
                        out.println("<li class='reason'>" + esc(error.getKey()) + ": " + esc(error.getValue()) + "</li>");
                    }
                    out.println("</ul>");
                }
                WeatherUpdater.WriteResult write = WeatherUpdater.getLastWrite();
                if (write != null) {
                    out.println("<p>WebCTRL: " + esc(write.summary()) + "</p>");
                }
            }
        } catch (Exception e) {
            out.println("<h2 style='color:#ff6b6b'>❌ Update failed: " + esc(e.getMessage()) + "</h2>");
        }
        
        out.println("<p>Redirecting back...</p>");
        out.println("</body></html>");
    }
    
    private static String esc(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    .replace("\"", "&quot;").replace("'", "&#39;");
    }
}

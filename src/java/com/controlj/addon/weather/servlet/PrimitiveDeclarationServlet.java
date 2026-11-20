package com.controlj.addon.weather.servlet;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * PrimitiveDeclarationServlet - Declares WebCTRL primitive variables.
 * This servlet returns a JSON list of all weather data fields that can be mapped to BACnet points.
 * Endpoint: /data/primitive/declaration?path=<GEO_tree_path>
 */
@WebServlet("/data/primitive/declaration")
public class PrimitiveDeclarationServlet extends HttpServlet {
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        
        // Get path parameter (GEO tree path)
        String path = request.getParameter("path");
        
        JSONObject root = new JSONObject();
        JSONArray declarations = new JSONArray();
        
        // Same field set as WeatherUpdater writes (names must be LOWERCASE only).
        String[] hourlyFields = {
            "highesttemperature", "avghumidity", "windspeed", "winddirection",
            "enthalpy", "dewpoint", "humidityratio"
        };
        String[] dailyFields = {"highesttemperature", "mintemperature"};
        
        // 12 hours x 7 values
        for (int hour = 0; hour < 12; hour++) {
            for (String field : hourlyFields) {
                JSONObject item = new JSONObject();
                item.put("fieldName", "wf" + hour + "_" + field);
                item.put("value", "0");
                item.put("type", "PrimitiveNumber");
                declarations.put(item);
            }
        }
        
        // 3 days x 2 values
        for (int day = 0; day < 3; day++) {
            for (String field : dailyFields) {
                JSONObject item = new JSONObject();
                item.put("fieldName", "wd" + day + "_" + field);
                item.put("value", "0");
                item.put("type", "PrimitiveNumber");
                declarations.put(item);
            }
        }
        
        root.put("declarations", declarations);
        out.print(root.toString());
    }
}
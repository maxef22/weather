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
        
        // Declare variables for 12 hours (temperature + humidity)
        // Variable names must be in LOWERCASE only!
        for (int hour = 0; hour < 12; hour++) {
            // Temperature field
            JSONObject tempField = new JSONObject();
            tempField.put("fieldName", "wf" + hour + "_highesttemperature");
            tempField.put("value", "0");
            tempField.put("type", "PrimitiveNumber");
            declarations.put(tempField);
            
            // Humidity field
            JSONObject humidityField = new JSONObject();
            humidityField.put("fieldName", "wf" + hour + "_avghumidity");
            humidityField.put("value", "0");
            humidityField.put("type", "PrimitiveNumber");
            declarations.put(humidityField);
        }
        
        root.put("declarations", declarations);
        out.print(root.toString());
    }
}
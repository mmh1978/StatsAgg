/*
 * Copyright 2015 prashant kumar(prashant4nov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pearson.statsagg.webui.api;

import com.pearson.statsagg.database.metric_group.MetricGroup;
import com.pearson.statsagg.globals.GlobalVariables;
import com.pearson.statsagg.utilities.StackTrace;
import com.pearson.statsagg.webui.MetricGroupsLogic;
import java.io.PrintWriter;
import java.util.Scanner;
import java.util.TreeSet;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author prashant kumar(prashant4nov)
 */
@WebServlet(name = "API_CreateMetricGroup", urlPatterns = {"/api/create-metric-group"})
public class CreateMetricGroup extends HttpServlet {
    
    private static final Logger logger = LoggerFactory.getLogger(CreateMetricGroup.class.getName());
    
    public static final String PAGE_NAME = "API_CreateMetricGroup";
    
    /**
     * Handles the HTTP <code>POST</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        processPostRequest(request, response);
    }

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return PAGE_NAME;
    }
    
    protected void processPostRequest(HttpServletRequest request, HttpServletResponse response) {
        
        if ((request == null) || (response == null)) {
            return;
        }
        
        JSONObject json = new JSONObject();
        response.setContentType("application/json");
        PrintWriter out = null;
        
        try {
            String result = parseMetricGroup(request);
            json.put("response", result);
            out = response.getWriter();
            out.println(json);
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        finally {            
            if (out != null) {
                out.close();
            }
        }
    }
    
    private String parseMetricGroup(HttpServletRequest request) {
        
        if (request == null) {
            return null;
        }
        
        String returnString;
        
        MetricGroup metricGroup = getMetricGroupFromMetricGroupParameters(request);
        String oldName = null;
        if (request.getParameter("old_name") != null) {
          oldName = request.getParameter("old_name");
        }
        TreeSet<String> matchRegexes = getMetricGroupNewlineDelimitedParameterValues(request, "match_regex");
        TreeSet<String> blacklistRegexes = getMetricGroupNewlineDelimitedParameterValues(request, "blacklist_regex");
        TreeSet<String> tags = getMetricGroupNewlineDelimitedParameterValues(request, "tags");
        
        // insert/update records in the database
        if ((metricGroup != null) && (metricGroup.getName() != null)) {
            MetricGroupsLogic metricGroupsLogic = new MetricGroupsLogic();
            returnString = metricGroupsLogic.alterRecordInDatabase(metricGroup, matchRegexes, blacklistRegexes, tags, oldName);
            
            if ((GlobalVariables.alertInvokerThread != null) && (MetricGroupsLogic.STATUS_CODE_SUCCESS == metricGroupsLogic.getLastAlterRecordStatus())) {
                GlobalVariables.alertInvokerThread.runAlertThread(true, false);
            }
        }
        else {
            returnString = "Failed to add metric group. Reason=\"Field validation failed.\"";
            logger.warn(returnString);
        }
        
        return returnString;
    }
    
    private MetricGroup getMetricGroupFromMetricGroupParameters(HttpServletRequest request) {
        
        if (request == null) {
            return null;
        }
        
        boolean didEncounterError = false;
        
        MetricGroup metricGroup = new MetricGroup();

        try {
            String parameter;

            parameter = request.getParameter("name");
            String trimmedName = parameter.trim();
            metricGroup.setName(trimmedName);
            metricGroup.setUppercaseName(trimmedName.toUpperCase());
            if ((metricGroup.getName() == null) || metricGroup.getName().isEmpty()) didEncounterError = true;

            parameter = request.getParameter("description");
            if (parameter != null) {
                String trimmedParameter = parameter.trim();
                String description;
                if (trimmedParameter.length() > 100000) description = trimmedParameter.substring(0, 99999);
                else description = trimmedParameter;
                metricGroup.setDescription(description);
            }
            else metricGroup.setDescription("");
        }
        catch (Exception e) {
            didEncounterError = true;
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
            
        if (didEncounterError) {
            metricGroup = null;
        }
        
        return metricGroup;
    }
    
    protected static TreeSet<String> getMetricGroupNewlineDelimitedParameterValues(HttpServletRequest request, String parameterName) {
        
        if ((request == null) || (parameterName == null)) {
            return null;
        }
        
        boolean didEncounterError = false;
        TreeSet<String> parameterValues = new TreeSet<>();

        try {
            String parameter = request.getParameter(parameterName);
            
            if (parameter != null) {
                Scanner scanner = new Scanner(parameter);
                
                while (scanner.hasNext()) {
                    parameterValues.add(scanner.nextLine().trim());
                }
            }
        }
        catch (Exception e) {
            didEncounterError = true;
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
            
        if (didEncounterError) parameterValues = null;
        
        return parameterValues;
    }
}

package com.pearson.statsagg.web_api;

import com.google.gson.JsonObject;
import com.pearson.statsagg.database_objects.notifications.NotificationGroup;
import com.pearson.statsagg.database_objects.notifications.NotificationGroupsDao;
import com.pearson.statsagg.utilities.json_utils.JsonUtils;
import com.pearson.statsagg.utilities.core_utils.StackTrace;
import java.io.PrintWriter;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author prashant kumar (prashant4nov)
 * @author Jeffrey Schmidt
 */
@WebServlet(name = "API_NotificationGroup_Remove", urlPatterns = {"/api/notification-group-remove"})
public class NotificationGroupRemove extends HttpServlet {
    
    private static final Logger logger = LoggerFactory.getLogger(NotificationGroupRemove.class.getName());
    
    public static final String PAGE_NAME = "API_NotificationGroup_Remove";
    
    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return PAGE_NAME;
    }
    
    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        
        PrintWriter out = null;
        
        try {  
            request.setCharacterEncoding("UTF-8");
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json");
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        try {    
            String returnString = processPostRequest(request);       
            out = response.getWriter();
            out.println(returnString);
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

    /**
     * Returns a string with a success message if the notification group is deleted successfully,
     * or an error message if the request fails to delete the notification group.
     * 
     * @param request servlet request
     * @return success or error message
     */
    protected String processPostRequest(HttpServletRequest request) {
        
        if (request == null) {
            return Helper.ERROR_UNKNOWN_JSON;
        }
        
        try {
            JsonObject jsonObject = Helper.getJsonObjectFromRequestBody(request);
            Integer id = JsonUtils.getIntegerFieldFromJsonObject(jsonObject, "id");
            String name = JsonUtils.getStringFieldFromJsonObject(jsonObject, "name");
            
            if ((id == null) && (name != null)) {
                NotificationGroupsDao notificationGroupsDao = new NotificationGroupsDao();
                NotificationGroup notificationGroup = notificationGroupsDao.getNotificationGroupByName(name);
                id = notificationGroup.getId();
            }
            
            NotificationGroupsDao notificationGroupsDao = new NotificationGroupsDao();
            NotificationGroup notificationGroup = notificationGroupsDao.getNotificationGroup(id);
            if (notificationGroup == null) return Helper.ERROR_NOTFOUND_JSON;
            
            com.pearson.statsagg.web_ui.NotificationGroups notificationGroups = new com.pearson.statsagg.web_ui.NotificationGroups(); 
            String result = notificationGroups.removeNotificationGroup(id);
            
            return Helper.createSimpleJsonResponse(result);
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            return Helper.ERROR_UNKNOWN_JSON;
        }
        
    }
    
}

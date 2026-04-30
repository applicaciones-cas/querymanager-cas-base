/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package ph.com.guanzongroup.querymanager.cas.base;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import org.guanzon.appdriver.agent.ShowDialogFX;
import org.guanzon.appdriver.base.GProperty;
import org.guanzon.appdriver.base.GRiderCAS;

import org.guanzon.appdriver.base.GuanzonException;
import org.guanzon.appdriver.base.LogWrapper;
import org.guanzon.appdriver.base.MiscUtil;
import org.guanzon.appdriver.base.SQLUtil;
import org.json.simple.JSONObject;
import java.util.List;
import java.util.Arrays;

/**
 * BatchQuery poBatch = new BatchQuery();
 * poBatch.setGRider(poGRider);
 * poBatch.setConfig("c:/ggc_java_systems/config/queryfx", "GCASys_DBF");
 * poBatch.searchBranch("M001", true);
 * poBatch.searchModule("", false);
 * @author kalyptus
 */
public class BatchQuery {
    private final String pxeModuleName = "BatchQuery";
    private static LogWrapper logwrapr = new LogWrapper("querymgrfx.GQuery", "temp/GQuery.log");
    private GRiderCAS poGRider;
    private QueryFXConnection poConn;
    
    private String psBranchCD;
    private String psBranchNm;
    private String psBranchIP;
    
    private String psSourceCD;
    private String psSourceNm;
    
    private String psSourceNo;
    private String psRemarksx;
    
    private String psMessagex;

    private List<String> pasDMLUQuery;
    
    private String psDBHost;
    private String psDBName;
    private String psDBPort;
    private String psDBUser;
    private String psDBPass;
    private String psDBSign;
    private GProperty poProp;
    
    public void setGRider(GRiderCAS rider) throws SQLException{
        if(rider == null){
            psMessagex = "Invalid Application Driver detected...";
            psBranchCD = "";
            psBranchNm = "";
        }
        else{
            poGRider = rider;
            psBranchCD = poGRider.getBranchCode();
            psBranchNm = poGRider.getBranchName();
            psMessagex = "";
        }
        
        psSourceCD = "";
        psSourceNm = "";
        psSourceNo = "";
        
        pasDMLUQuery = new ArrayList<>();
    }

    public String getBranchCode(){
        return this.psBranchCD;
    }

    public String getBranchName(){
        return this.psBranchNm;
    }
    
    public String getBranchIP(){
        return this.psBranchIP;
    }
    
    public String getSourceName(){
        return this.psSourceNm;
    }
    
    public String getSourceCode(){
        return this.psSourceCD;
    }

    public void setSourceNo(String fsSourceNo){
        this.psSourceNo = fsSourceNo;
    }

    public String getSourceNo(){
        return this.psSourceNo;
    }
    
    public void setRemarks(String fsRemarksx){
        this.psRemarksx = fsRemarksx;
    }

    public String getRemarks(){
        return this.psRemarksx;
    }
    
    public void beginTrans() throws SQLException, GuanzonException{
        if (poConn.getConnection().getAutoCommit()) {
            pasDMLUQuery = new ArrayList<>();
            poConn.beginTrans("UPDATE", psRemarksx, psSourceCD, psSourceNo);
        }
    }

    public boolean executeDMLUpdate(String fsQuery){
        try {
            // Ensure that a transaction has been properly initiated before proceeding
            if (poConn.getConnection().getAutoCommit()) {
                psMessagex = "Error: Transaction not started. Please call BeginTrans first.";
                rollbackTrans();
                return false;
            }
            
            //Check if user is authorized to use this application
            if(poGRider.getUserLevel() < 16){
                psMessagex = "User is not authorized to use this application...";
                rollbackTrans();
                return false;
            }
            
            // Validate if user role is authorized to use the application
            String right = getRights(poGRider.getUserID()).toLowerCase();
            if(right.equalsIgnoreCase("encoder") ||
                    right.equalsIgnoreCase("supervisor") ||
                    right.equalsIgnoreCase("sysadmin") ||
                    right.equalsIgnoreCase("engineer")) {
                psMessagex = "Error: User is not authorized to use the application...";
                rollbackTrans();
                return false;
            }
            
            //Split the query using semi-colon as parameter
            String lasQuery[] = fsQuery.split(";");
            
            //process each part...
            for (String query : lasQuery) {
                if (!query.trim().isEmpty()) {
                    // Validate SQL statement length (must not exceed 4112 characters)
                    if(query.length() > 4112){
                        psMessagex = "Cannot execute SQL statements longer than 4112 characters:" + query;
                        rollbackTrans();
                        return false;
                    }
                    
                    // Normalize formatting: convert to uppercase and collapse multiple spaces
                    query = query.trim().replaceAll("\\s{2,}", " ");
                    String lsSQL = query.toUpperCase();
                    
                    // Ensure SQL statement begins with an allowed keyword
                    List<String> pasAllowedSQL = Arrays.asList("INSERT ", "UPDATE ", "DELETE ", "REPLACE ");
                    if (pasAllowedSQL.stream().noneMatch(lsSQL::startsWith)) {
                        // Reject unrecognized or unauthorized SQL statements
                        psMessagex = "Error: SQL statement is not recognized: " + query;
                        rollbackTrans();
                        return false;
                    }
                    
                    // Restrict access for specific roles (encoder/supervisor)
                    if(right.equalsIgnoreCase("encoder") ||
                            right.equalsIgnoreCase("supervisor")) {
                        // Prevent manipulation of sensitive tables
                        if(lsSQL.contains("xxxSysUserQFX") || lsSQL.contains("xxxQueryLog")){
                            //Block execution
                            psMessagex = "Error: Unauthorized SQL statement detected:" + query;
                            rollbackTrans();
                            return false;
                        } // end restricted table check
                    } // end role validation
                    
                    //Add the DML statement for batch registration
                    pasDMLUQuery.add(query);
                    
                    //execute the query/statement
                    long xcount = poConn.executeUpdate(query);
                    boolean result = false;
                    
                    // Authorize execution based on number of records updated and user role
                    if (xcount > 100 && right.equalsIgnoreCase("engineer")) {
                        result = true;
                    } else if (xcount > 50 && right.equalsIgnoreCase("sysadmin")) {
                        result = true;
                    } else if (xcount > 10 && right.equalsIgnoreCase("supervisor")) {
                        result = true;
                    } else if (xcount <= 10) {
                        result = true;
                    }
                    
                    //create message if user is not authorized before exiting the method...
                    if(!result){
                        psMessagex = "User is not authorized to execute a query that will update/delete " + xcount + " record(s)...";
                        rollbackTrans();
                        return false;
                    }
                } // end non-empty query check
            } // end query loop
            
        } catch (SQLException|GuanzonException ex) {
            ex.printStackTrace();
            psMessagex = ex.getMessage();
            rollbackTrans();
            return false;
        }

        return true;
    }

    public void rollbackTrans(){
        try {
            if (!poConn.getConnection().getAutoCommit()) {
                poConn.rollbackTrans();
                pasDMLUQuery = new ArrayList<>();
            }
        } catch (SQLException|GuanzonException ex) {
            ex.printStackTrace();
            psMessagex = ex.getMessage();
        }
    }
    
    public void commitTrans(){
        try {
            if (!poConn.getConnection().getAutoCommit()) {
                for (String query : pasDMLUQuery) {
                    System.out.println(query);
                    poConn.logQuery(query, psBranchCD, "", psDBSign);
                }
                
                poConn.commitTrans();
            }
        } catch (SQLException|GuanzonException ex) {
            ex.printStackTrace();
            psMessagex = ex.getMessage();
        }
    }
    
    //setConfig("c:/ggc_java_systems/config/queryfx", "GCASys_DBF");
    public boolean setConfig(String config, String dbfname){
        this.psDBName  = dbfname;
        
        this.poProp = loadConfig(config);
        
        this.psDBSign = poProp.getConfig("sys.default.signature");
        this.psDBHost = poProp.getConfig("sys.default.dbsrvr." + this.psDBName);
        this.psDBPort = poProp.getConfig("sys.default.dbport." + this.psDBName);
        //this.psDBUser = poProp.getConfig("sys.default.dbuser." + this.psDBName);    
        //this.psDBPass = poProp.getConfig("sys.default.dbpass." + this.psDBName);
        this.psDBUser = poGRider.Decrypt(poProp.getConfig("sys.default.dbuser." + this.psDBName), psDBSign);
        this.psDBPass = poGRider.Decrypt(poProp.getConfig("sys.default.dbpass." + this.psDBName), psDBSign);
        
        poConn = new QueryFXConnection();
        poConn.setupDataSource(psDBHost, psDBName, psDBUser, psDBPass, psDBPort);
        
        return true;
    }
    
    public boolean searchBranch(String branch, boolean code){
        boolean result=false;
        
        //initialize error message
        psMessagex = "";
        
        String query = "SELECT a.sBranchCD, a.sBranchNm, b.sDBIPAddr" + 
                      " FROM Branch a" + 
                            " LEFT JOIN Branch_Others b ON a.sBranchCD = b.sBranchCD" +
                      " WHERE a.cRecdStat = '1'";
        //query = MiscUtil.addCondition(query, code ? "a.sBranchCD = " + SQLUtil.toSQL(branch) : "a.sBranchNm LIKE " + SQLUtil.toSQL(branch + "%"));
        
        try {
            ResultSet loRS = poGRider.executeQuery(query);
            
            if(loRS.next()){
                if(code || MiscUtil.RecordCount(loRS) == 1){
                    psBranchCD = loRS.getString("sBranchCD");
                    psBranchNm = loRS.getString("sBranchNm");
                    psBranchIP = loRS.getString("sDBIPAddr");
                    result = true;
                }
                else{
                    //JSONObject loJSON = showFXDialog.jsonBrowse(poGRider, loRS, "Code»Branch", "sBranchCD»sBranchNm");
                    
                    JSONObject loJSON = ShowDialogFX.Browse(poGRider,
                                                                query,
                                                                branch,
                                                                "Branch Code»Branch Name",
                                                                "sBranchCD»sBranchNm",
                                                                "a.sBranchCD»a.sBranchNm",
                                                                code ? 0 : 1);
                    
                    if (loJSON != null){
                        psBranchCD = (String) loJSON.get("sBranchCD");
                        psBranchNm = (String) loJSON.get("sBranchNm");
                        psBranchIP = (String) loJSON.get("sDBIPAddr");
                        result = true;
                    } else
                        psMessagex = poGRider.getMessage();               
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            psBranchCD = "";
            psBranchNm = "";
            psBranchIP = "";
            psMessagex = ex.getMessage();
        }
        
        return result;
    }

    public boolean searchModule(String module, boolean code){
        boolean result=false;
        
        //initialize error message
        psMessagex = "";
        
        String query = "SELECT a.sSourceCD, a.sSourceNm" + 
                      " FROM xxxTransactionSource a" + 
                      " WHERE a.cRecdStat = '1'";
        //query = MiscUtil.addCondition(query, code ? "a.sSourceCD = " + SQLUtil.toSQL(module) : "a.sSourceNm LIKE " + SQLUtil.toSQL(module + "%"));
        
        try {
            ResultSet loRS = poGRider.executeQuery(query);

            if(loRS.next()){
                if(code || MiscUtil.RecordCount(loRS) == 1){
                    psSourceCD = loRS.getString("sSourceCD");
                    psSourceNm = loRS.getString("sSourceNm");
                    result = true;
                }
                else{
                    //JSONObject loJSON = showFXDialog.jsonBrowse(poGRider, loRS, "Code»Branch", "sBranchCD»sBranchNm");
                    
                    JSONObject loJSON = ShowDialogFX.Browse(poGRider,
                                                                query,
                                                                module,
                                                                "Source Code»Source Name",
                                                                "sSourceCD»sSourceNm",
                                                                "a.sSourceCD»a.sSourceNm",
                                                                code ? 0 : 1);
                    
                    if (loJSON != null){
                        psSourceCD = (String) loJSON.get("sSourceCD");
                        psSourceNm = (String) loJSON.get("sSourceNm");
                        result = true;
                    } else
                        psMessagex = poGRider.getMessage();               
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            psSourceCD = "";
            psSourceNm = "";
            psMessagex = ex.getMessage();
        }
        
        return result;
    }
    
    //query manager user rights are:
    //encoder, supervisor, sysadmin, engineer
    private String getRights(String userid){
        String rights="";
        String query = "SELECT sUserLvlx FROM xxxSysUserQFX" + 
                      " WHERE sUserIDxx = " + SQLUtil.toSQL(poGRider.Encrypt(userid, "sysadmin"));
        
        logwrapr.info(query);
        
        try {
            ResultSet loRS = poGRider.executeQuery(query);
            if(loRS.next()){
                rights = poGRider.Decrypt(loRS.getString("sUserLvlx"), "sysadmin");
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            psMessagex = ex.getMessage();
        }
        
        return rights;
    }
    
   private GProperty loadConfig(String sprop){
         return(new GProperty(sprop));
   }
    
}

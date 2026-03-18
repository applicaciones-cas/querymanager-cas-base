/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ph.com.guanzongroup.querymanager.cas.base;

import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.math.NumberUtils;
import org.guanzon.appdriver.agent.ShowDialogFX;
import org.guanzon.appdriver.base.GDBFChain;
import org.guanzon.appdriver.base.GProperty;
import org.guanzon.appdriver.base.GRiderCAS;
import org.guanzon.appdriver.base.GuanzonException;
import org.guanzon.appdriver.base.LogWrapper;
import org.guanzon.appdriver.base.MiscUtil;
import org.guanzon.appdriver.base.SQLUtil;
import org.json.simple.JSONObject;

/**
 *
 * @author sayso
 */
public class GQuery2 {
    private final String pxeModuleName = "GQuery";
    private static LogWrapper logwrapr = new LogWrapper("querymgrfx.GQuery", "temp/GQuery.log");
    private GRiderCAS poGRider;
    private String message;
    private ResultSet rsdata;
    private GDBFChain gchain;
    private String branchcd;
    private String branchnm;
    private String branchip;
    private String destntcd;
    private String destntnm;

    private String dbhost;
    private String dbname;
    private String dbport;
    private String dbuser;
    private String dbpass;
    private boolean logdelete;
    private String signature;
    
    private GProperty prop;
    
    private boolean debug_mode;
    private long count;
    private String auto_query = "";
    private String auto_messg = "";
    
    public String getMessage(){
        return message;
    }
    
    public ResultSet getResult(){
        return rsdata;
    }
    
    public long getCount(){
        return count;
    }

    public String getSolutionMessage(){
       return auto_messg;
    }
    
    public String getBranchName(){
        return branchnm;
    }
    
    public String getBranchIP(){
        return branchip;
    }
    
    public String getDestination(){
       return destntnm;
    }
    
    //setConfig("c:/ggc_java_systems/config/queryfx", "GGC_ISysDBF");
    public boolean setConfig(String config, String dbfname){
        this.dbname  = dbfname;
        
        this.prop = loadConfig(config);
        
        this.signature = prop.getConfig("sys.default.signature");
        this.dbhost = prop.getConfig("sys.default.dbsrvr." + this.dbname);
        this.dbport = prop.getConfig("sys.default.dbport." + this.dbname);
        //this.dbuser = Decrypt(prop.getConfig("sys.default.dbuser." + this.dbname), signature);
        //this.dbpass = Decrypt(prop.getConfig("sys.default.dbpass." + this.dbname), signature);
        this.dbuser = prop.getConfig("sys.default.dbuser." + this.dbname);
        this.dbpass = prop.getConfig("sys.default.dbpass." + this.dbname);
        this.logdelete = prop.getConfig("sys.default.logdelete." + this.dbname).equalsIgnoreCase("true");
        
        gchain = new GDBFChain();
        
        gchain.doDBFChain(poGRider.getBranchCode(), poGRider.getUserID(), dbhost, dbname, dbuser, dbpass, dbport);
        
        return true;
    }
    
    //initializes the branch info and GConnection object
    public void setGRiderX(GRiderCAS rider) throws SQLException{
        if(rider == null){
            branchcd = "";
            branchnm = "";
            branchip = "";
        }
        else{
            poGRider = rider;
            searchBranch(poGRider.getBranchCode(), true);
        }
        
         destntcd = "";
         destntnm = "";
    }
    
    //Note: a change of branch/ip means set reconnect to true
    public boolean searchBranch(String branch, boolean code) throws SQLException{
        boolean result=false;
        
        //initialize error message
        message = "";
        
        String query = "SELECT a.sBranchCD, a.sBranchNm, b.sDBIPAddr" + 
                      " FROM Branch a" + 
                            " LEFT JOIN Branch_Others b ON a.sBranchCD = b.sBranchCD" +
                      " WHERE a.cRecdStat = '1'";
        //query = MiscUtil.addCondition(query, code ? "a.sBranchCD = " + SQLUtil.toSQL(branch) : "a.sBranchNm LIKE " + SQLUtil.toSQL(branch + "%"));
        
        ResultSet loRS = poGRider.executeQuery(query);
        
        try {
            if(loRS.next()){
                if(code || MiscUtil.RecordCount(loRS) == 1){
                    branchcd = loRS.getString("sBranchCD");
                    branchnm = loRS.getString("sBranchNm");
                    branchip = loRS.getString("sDBIPAddr");
                    result = true;
                } else{
                    //JSONObject loJSON = showFXDialog.jsonBrowse(poGRider, loRS, "Code»Branch", "sBranchCD»sBranchNm");
                    JSONObject loJSON = ShowDialogFX.Browse(poGRider,
                                                                query,
                                                                branch,
                                                                "Branch Code»Branch Name",
                                                                "sBranchCD»sBranchNm",
                                                                "a.sBranchCD»a.sBranchNm",
                                                                code ? 0 : 1);
                    
                    
                    if (loJSON != null){
                        branchcd = (String) loJSON.get("sBranchCD");
                        branchnm = (String) loJSON.get("sBranchNm");
                        branchip = (String) loJSON.get("sDBIPAddr");
                        result = true;
                    } else
                        message = poGRider.getMessage();             
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            message = ex.getMessage();
        }
        
        return result;
    }

    public boolean searchDestination(String branch, boolean code) throws SQLException{
        boolean result=false;

        //initialize error message
        message = "";
        
        if(code && branch.isEmpty()){
            destntcd = "";
            destntnm = "";
        }
        
        String query = "SELECT a.sBranchCD, a.sBranchNm" + 
                      " FROM Branch a" + 
                      " WHERE a.cRecdStat = '1'";
        //query = MiscUtil.addCondition(query, code ? "a.sBranchCD = " + SQLUtil.toSQL(branch) : "a.sBranchNm LIKE " + SQLUtil.toSQL(branch + "%"));
        
        ResultSet loRS = poGRider.executeQuery(query);
        
        try {
            if(loRS.next()){
               int xcount = 0;
               
               if(code || MiscUtil.RecordCount(loRS) == 1){
                    destntcd = loRS.getString("sBranchCD");
                    destntnm = loRS.getString("sBranchNm");
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
                        destntcd = (String) loJSON.get("sBranchCD");
                        destntnm = (String) loJSON.get("sBranchNm");
                        result = true;
                    } else 
                        message = poGRider.getMessage();                  
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            message = ex.getMessage();
        }
        
        return result;
    }
    
    public boolean executeSolution() throws SQLException{
       if(auto_query.length() == 0){
          message = "No command/solution to auto-execute detected...";
          return false;
       } //if(auto_query.length() == 0){
       
       String lasMessage[] = auto_query.split("»");
       for(int ctr=0;ctr<lasMessage.length;ctr++){
          gchain.executeUpdate(lasMessage[ctr]);
          if(gchain.getMessage().length() > 0){
             message = gchain.getMessage()  + "\nUnable to execute the following: " + auto_query; 
             return false;
          } //if(gchain.getMessage().length() > 0){
          
         //log to the query manager log 
         if(!gchain.logQuery(lasMessage[ctr], branchcd, poGRider.Encrypt(poGRider.getUserID(), "sysmgr"))){
             message = gchain.getMessage()  + "\nUnable to log the following: " + auto_query; 
             return false;
         } //if(!gchain.logQuery(lasMessage[ctr], branchcd, Encrypt(poGRider.getUserID(), "sysadmin"))){        
       }// for(int ctr=0;ctr<lasMessage.length;ctr++){
       
       auto_query = "";
       auto_messg = "";
       return true;  
    }
    
    public String grabSQL(String table, int row){
       String result = "";
       try {
          //Retrieve the current row number...
          int prev_row = rsdata.getRow();
          
          //Move the cursor to the target row 
          rsdata.absolute(row);
          
          result = MiscUtil.makeSQL(rsdata.getMetaData(), MiscUtil.row2Map(rsdata), table, "");
          
          //Move the cursor back to the original position
          rsdata.absolute(prev_row);
       } catch (SQLException ex) {
          ex.printStackTrace();
          result = "";
       }
       
       return result;
    }
    
    public boolean execute(String query, boolean log, String division) throws SQLException{
        boolean result=false;

        //initialize error message
        message = "";
        
        //Only Users with account above System Administrator are allowed to use the program...
        //logwrapr.info("User ID: " + poGRider.getUserID());
        //logwrapr.info("User Level: " + poGRider.getUserLevel());
        if(poGRider.getUserLevel() < 16){
            message = "User is not authorized to use this application...";
            return false;
        }
        
        //establish the connection
        //gchain = poGRider.getGDBFChain(branchip);
        
        //validate branch 
        String ip = validBranch(branchcd, gchain.getConnection());
        if(ip.length() == 0){
            gchain = null;
            return false;
        }
        
        //trim query...
        query = query.trim();
        
        //Make sure that the length of SQL statement is within the SET length...
        if(query.length() > 2045){
            message = "Cannot execute more than 2045 characters SQL statement...";
            return false;
        }
        
        String clone = query.toUpperCase();
        
        if(clone.startsWith("SELECT ") || 
           clone.startsWith("DESCRIBE ") ||
           clone.startsWith("SHOW ") || 
           clone.startsWith("EXPLAIN ") || 
           clone.startsWith("HELP ")){
            return executeDMLRetrieve(query, log);
        }
        else if(clone.startsWith("CREATE ") ||
             clone.startsWith("RENAME ") ||
             clone.startsWith("ALTER ") ||
             clone.startsWith("DROP ") || 
             clone.startsWith("TRUNCATE ") ||
             clone.startsWith("FLUSH ") || 
             clone.startsWith("CHANGE ") ||
             clone.startsWith("STOP ") ||
             clone.startsWith("START ") ||
             clone.startsWith("RESET ") || 
             clone.startsWith("SET ") ||
             clone.startsWith("PURGE ") ||
             clone.startsWith("LOCK ") ||
             clone.startsWith("UNLOCK ") || 
             clone.startsWith("USE ") || 
             clone.startsWith("CALL ") ||
             clone.startsWith("DO ") ||
             clone.startsWith("EXECUTE ")){
            return executeDDL(query, log);
        }
        else if(clone.startsWith("INSERT ") ||
                clone.startsWith("UPDATE ") || 
                clone.startsWith("DELETE ") || 
                clone.startsWith("REPLACE ")){
            
            if(clone.startsWith("DELETE ") && this.logdelete){
                logDeletion(query);
            }
            
            return executeDMLUpdate(query, log, division);
        }
        else{
            message = "SQL Statement is not recognized...";
        }
        
        return result;
    }
    
    //execute Data Definition commands
    //create, alter, drop, truncate, rename
    //sysadmin : create, rename
    //manager  : all commands...
    private boolean executeDDL(String query, boolean log) throws SQLException{
        boolean result=true;
        
        String right = getRights(poGRider.getUserID()).toLowerCase();
        
        if(right.equalsIgnoreCase("encoder")){
            message = "Associates/Supervisors are not authorized user of this statement...";
            return false;
        }
        else if(right.equalsIgnoreCase("supervisor")){
            if(!query.startsWith("USE ")){
                message = "Associates/Supervisors are not authorized user of this statement...";
                return false;
            } 
        }
        else if(right.equalsIgnoreCase("sysadmin")){
            String clone = query.toUpperCase();
            if(!(clone.startsWith("CREATE ") || clone.startsWith("RENAME ") || clone.startsWith("USE "))){
                message = "SysAdmins are not authorized user of this statement...";
                return false;
            }
        }
        else if(!right.equalsIgnoreCase("engineer")){
            message = "User is not included with the list of authorized user...";
            return false;
        }
        
        gchain.beginTrans();
        count = gchain.executeUpdate(query);
        if(gchain.getMessage().length() > 0){
            message = gchain.getMessage();
            gchain.rollbackTrans();
            count = 0;
            return false;
        }
        
        //log to the query manager log 
        if(!gchain.logQuery(query, branchcd, poGRider.Encrypt(poGRider.getUserID(), "sysmgr"))){
            message = gchain.getMessage();
            gchain.rollbackTrans();
            count = 0;
            rsdata = null;
            return false;
        }        
        
        //Do not allow other commands other than the following commands to circulate
        //from the different servers...
        if(log){
            String clone = query.toUpperCase();
            if(!(clone.startsWith("CREATE ") ||
                 clone.startsWith("RENAME ") ||
                 clone.startsWith("ALTER ") ||
                 clone.startsWith("DROP ") || 
                 clone.startsWith("TRUNCATE ") || 
                 clone.startsWith("CALL ") ||
                 clone.startsWith("DO ") ||
                 clone.startsWith("EXECUTE "))){
               log = false;
            }           
        }
        
        if(log){
            //Log statement to the xxxReplicationLog
            if(!gchain.logQuery(query, "xxxTableAll", branchcd, branchcd, poGRider.getUserID())){
                message = gchain.getMessage();
                gchain.rollbackTrans();
                count = 0;
                return false;
            }
            
            //Log statement to the xxxAuditTrail
            if(!gchain.logAudit("QRYX", gchain.getLastReplNo(), "", "View Audit Log", "", MiscUtil.getPCName(), poGRider.Encrypt(poGRider.getUserID(), "sysmgr"))){
                message = gchain.getMessage();
                gchain.rollbackTrans();
                count = 0;
                return false;
            }
            
            gchain.commitTrans();
        }
        
        return result;
    }
    
    //execute Data Manipulation commands specifically for retrieving only
    //select, describe, show, explain
    private boolean executeDMLRetrieve(String query, boolean log) throws SQLException{
        boolean result=false;
        log = true;
        
        String right = getRights(poGRider.getUserID()).toLowerCase();
        //logwrapr.info(poGRider.getUserID() + " is " + right);
        
        if(right.equalsIgnoreCase("encoder") || 
           right.equalsIgnoreCase("supervisor") ||
           right.equalsIgnoreCase("sysadmin") ||
           right.equalsIgnoreCase("engineer")){
            result = true;
        }
        
        if(!result){
            message = "User is not included with the list of authorized user...";
            return false;
        }
        
        this.rsdata = gchain.executeQuery(query);
        
        if(rsdata == null){
            message = gchain.getMessage();
            return false;
        }
        
        gchain.beginTrans();
        if(gchain.logQuery(query, branchcd, poGRider.Encrypt(poGRider.getUserID(), "sysmgr"))){
            gchain.commitTrans();
        }
        else{
            message = gchain.getMessage();
            gchain.rollbackTrans();
            rsdata = null;
            return false;
        }
        
        //kalyptus - 2018.08.28 04:59pm
        //evaluate if command has an AUTO-HELP FEATURE...
        //TODO: todo pa laeng...
        if(right.equalsIgnoreCase("sysadmin") ||
           right.equalsIgnoreCase("engineer") || 
           right.equalsIgnoreCase("supervisor")){
           String clone = query.toUpperCase();
           
           auto_query = "";
           if(clone.startsWith("SHOW SLAVE STATUS")){
              try {
                 int err_no = rsdata.getInt("Last_Errno");
                 
                 switch(err_no){
                    case 1594:
                       /*
                           Error Message: Relay LOG READ failure: Could NOT parse relay LOG event entry.
                       */
                       auto_query = "STOP SLAVE";
                       auto_query = auto_query + "»" + 
                                    "CHANGE MASTER TO" +
                                   " MASTER_HOST = " + rsdata.getString("Master_Host") +
                                  ", MASTER_USER = 'ggcrepladmin'" +
                                  ", MASTER_PASSWORD = 'Tciadsoggc'" +
                                  ", MASTER_LOG_FILE = " + rsdata.getString("Relay_Master_Log_File") +
                                  ", MASTER_LOG_POS = " + rsdata.getLong("Exec_Master_Log_Pos");                        
                       auto_query = auto_query + "»" + "START SLAVE";
                       
                       //get the error message
                       if(rsdata.getString("Last_Error").length() > 0)
                          auto_messg = rsdata.getString("Last_Error");
                       else if(rsdata.getString("Last_Error").length() > 0)
                          auto_messg = rsdata.getString("Last_IO_Error");
                       else
                          auto_messg = rsdata.getString("Last_SQL_Error");
                       break;
                    case 1593:
                       /*
                           Error Message: The SLAVE I/O thread stops because SET @master_heartbeat_period ON MASTER failed.
                       */
                       auto_query = "STOP SLAVE";
                       auto_query = auto_query + "»" + "START SLAVE";

                       //get the error message
                       if(rsdata.getString("Last_Error").length() > 0)
                          auto_messg = rsdata.getString("Last_Error");
                       else if(rsdata.getString("Last_Error").length() > 0)
                          auto_messg = rsdata.getString("Last_IO_Error");
                       else
                          auto_messg = rsdata.getString("Last_SQL_Error");
                       
                       break;
                    case 1580:
                       /*
                           Error Message: Error 'You cannot 'ALTER' a log table if logging is enabled' on query. Default database: 'mysql'.
                       */
                       auto_query = "STOP SLAVE";
                       auto_query = auto_query + "»" + "SET GLOBAL SLOW_QUERY_LOG = 'OFF'";
                       auto_query = auto_query + "»" + "START SLAVE";
                       auto_query = auto_query + "»" + "DO SLEEP(5)";
                       auto_query = auto_query + "»" + "STOP SLAVE";
                       auto_query = auto_query + "»" + "SET GLOBAL SLOW_QUERY_LOG = 'ON'";
                       auto_query = auto_query + "»" + "START SLAVE";

                       //get the error message
                       if(rsdata.getString("Last_Error").length() > 0)
                          auto_messg = rsdata.getString("Last_Error");
                       else if(rsdata.getString("Last_Error").length() > 0)
                          auto_messg = rsdata.getString("Last_IO_Error");
                       else
                          auto_messg = rsdata.getString("Last_SQL_Error");
                       break;
                 } //switch(err_no){
              } catch (SQLException ex) {
                 ex.printStackTrace();
                 message = ex.getMessage();
              } //try {
           } //if(clone.startsWith("SHOW SLAVE STATUS")){
        } //if(right.equalsIgnoreCase("sysadmin") ||
        
        return result;
    }
    
    //execute Data Manipulation commands specifically for manipulating data only
    //insert, update, delete, replace
    //encoder   : maximum changes<= 10
    //supervisor: maximum change <= 50
    //sysadmin  : maximum change <=100
    //engineer  : unlimited...
    private boolean executeDMLUpdate(String query, boolean log, String division) throws SQLException{
        boolean result=false;

        String right = getRights(poGRider.getUserID()).toLowerCase();
        
        if(right.equalsIgnoreCase("encoder") || 
           right.equalsIgnoreCase("supervisor") ||
           right.equalsIgnoreCase("sysadmin") ||
           right.equalsIgnoreCase("engineer")){
            result = true;
        }
        
        if(!result){
            if(message.length() == 0){
                message = "User is not included with the list of authorized user...";
            }
            return false;
        }

        String clone = query.toUpperCase().replaceAll("\\s{2,}", " ");
        if(right.equalsIgnoreCase("encoder") || 
           right.equalsIgnoreCase("supervisor")) {
            //Make sure that these statements will not be executed...
             if(clone.startsWith("INSERT INTO xxxSysUserQFX ") ||
                clone.startsWith("UPDATE xxxSysUserQFX ") ||
                clone.startsWith("DELETE FROM xxxSysUserQFX ") || 
                clone.startsWith("REPLACE INTO xxxSysUserQFX ") || 
                clone.startsWith("INSERT INTO xxxQueryLog ") ||
                clone.startsWith("UPDATE xxxQueryLog ") ||
                clone.startsWith("DELETE FROM xxxQueryLog ") ||                     
                clone.startsWith("REPLACE INTO xxxQueryLog ")){
                
               //log to the query manager log 
               gchain.beginTrans();
               if(!gchain.logQuery("*:" + query, branchcd, poGRider.Encrypt(poGRider.getUserID(), "sysmgr"))){
                   message = gchain.getMessage();
                   gchain.rollbackTrans();
                   count = 0;
                   rsdata = null;
                   return false;
               }
               
               gchain.commitTrans();
                
               //but do not execute...
               message = "Error in SQL Statement detected...";
               return false;
             } //if(clone.startsWith("INSERT INTO xxxSysUserQFX ") ||
        } //if(right.equalsIgnoreCase("encoder")        
        
        gchain.beginTrans();

        //execute the query/statement
        long xcount = gchain.executeUpdate(query);
        
        //check for possible error generated...
        if(xcount == 0){
            if(gchain.getMessage().length() > 0){
                count = 0;
                message = gchain.getMessage();
                gchain.rollbackTrans();
                return false;
            }
        }
        
        //check if user is authorized to execute the statements based on the number of 
        //records updated...
        result = false;
        if(xcount > 100){
            if(right.equalsIgnoreCase("engineer")){
                result = true;
            }
        }
        else if(xcount > 50){
            if(right.equalsIgnoreCase("sysadmin")){
                result = true;
            }
        }
        else if(xcount > 10){
            if(right.equalsIgnoreCase("supervisor")){
                result = true;
            }
        }
        else{
            result = true;
        }
        
        //create message if user is not authorized before exiting the method...
        if(!result){
            message = "User is not authorized to execute a query that will update/delete " + xcount + " record(s)...";
            count = 0;
            gchain.rollbackTrans();
            return false;
        }
        
        //log to the query manager log 
        if(!gchain.logQuery(query, branchcd, poGRider.Encrypt(poGRider.getUserID(), "sysmgr"))){
            message = gchain.getMessage();
            gchain.rollbackTrans();
            count = 0;
            rsdata = null;
            return false;
        }
        division = division.trim();
        int divx = NumberUtils.isNumber(division) ? Integer.valueOf(division) : 0;
        
        String table = divx > 0 || destntcd.length() == 0 ? "xxxTableBranch" : "xxxTableSome";
        //log to the replication log...
        if(log){
            if(!gchain.logQuery(query, table, divx == 0 ? branchcd : division, destntcd, poGRider.getUserID())){
                message = gchain.getMessage();
                gchain.rollbackTrans();
                count = 0;
                return false;
            }
            
            //Log statement to the xxxAuditTrail
            if(!gchain.logAudit("QRYX", gchain.getLastReplNo(), "", "View Audit Log", "", MiscUtil.getPCName(), poGRider.Encrypt(poGRider.getUserID(), "sysmgr"))){
                message = gchain.getMessage();
                gchain.rollbackTrans();
                count = 0;
                return false;
            }
        }
        
        gchain.commitTrans();
        
        count = xcount;
        return result;
    }
    
    //query manager user rights are:
    //encoder, supervisor, sysadmin, engineer
    private String getRights(String userid){
        String rights="";
        String query = "SELECT sUserLvlx FROM xxxSysUserQFX" + 
                      " WHERE sUserIDxx = " + SQLUtil.toSQL(poGRider.Encrypt(userid, "sysadmin"));
        
        logwrapr.info(query);
        ResultSet loRS = gchain.executeQuery(query);
        
        try {
            if(loRS.next()){
                rights = poGRider.Decrypt(loRS.getString("sUserLvlx"), "sysadmin");
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            message = ex.getMessage();
        }
        
        return rights;
    }
    
    private String HostName(Connection conn){
        String hostname = "";
        if(gchain == null)  
            return "";

        try {
            ResultSet loRS = null;
            String lsSQL = "SHOW VARIABLES LIKE 'hostname'";
            loRS = conn.createStatement().executeQuery(lsSQL);

            if(loRS.next()) 
                hostname = loRS.getString("Value");

            MiscUtil.close(loRS);
            loRS = null;
        } catch (SQLException ex) {
            message = ex.getMessage();
            return "";
        }

        return hostname;
    }
    
    private String validBranch(String code, Connection conn){
        String query = "SELECT sDBHostNm, sDBIPAddr" + 
                      " FROM Branch_Others" + 
                      " WHERE sBranchCD = " + SQLUtil.toSQL(code);
        
        try {
            ResultSet result = conn.createStatement().executeQuery(query);
            if(result.next()){
                if(HostName(conn).equalsIgnoreCase(result.getString("sDBHostNm"))){
                    return result.getString("sDBIPAddr");
                }
                else{
                    message = "Server is not the default server of the branch...";
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            message = ex.getMessage();
        }
        
        return "";
    }

    private boolean logDeletion(String sql) throws SQLException{
        String transno = MiscUtil.getNextCode("xxxDeletedRec", "sTransNox", true, gchain.getConnection(), branchcd);
        String qry = "INSERT INTO xxxDeletedRec" + 
                    " SET sTransNox = " + SQLUtil.toSQL(transno) + 
                       ", sStatemnt = " + SQLUtil.toSQL(sql) + 
                       ", dModified = " + SQLUtil.toSQL(poGRider.getServerDate());
        gchain.executeUpdate(qry);
        
        return true;
    }
    
    public boolean adduser(String user, String right) throws SQLException, GuanzonException{
        boolean result = false;
        
        user = poGRider.Encrypt(user, "sysadmin");
        right = poGRider.Encrypt(right, "sysadmin");
        
        String query = "INSERT INTO xxxSysUserQFX(sUserIDxx, sUserLvlx)" + 
                  " VALUES(" + SQLUtil.toSQL(user) + "," 
                             + SQLUtil.toSQL(right) + ")";
        System.out.println(query);
        poGRider.executeQuery(query, "xxxSysUserQFX", "", "", "");
        
        return result;
    }
    
   private GProperty loadConfig(String sprop){
         return(new GProperty(sprop));
   }
}

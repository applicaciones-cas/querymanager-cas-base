/*
 * =========================================
 * Requirements:
 *    Spoken Requirements:
 *        1) Local Machine Date & Time is in sync with Server Machine
 * Sample Usage: Initialization
 *
 * GConnection loGCon = new GConnection();
 * loGCon.setBranch("01");
 * loGCon.setUser("0109035");
 * loGCon.setupDataSource("localhost", "GMC_ISysDBF", "sa", "Wtrtwh", "3306");
 *      :
 *      :
 * TimeStamp loTime = loGCon.getServerDate();
 *      :
 *      :
 * loGCon.beginTrans();
 * int lnRecdCtrx = loGCon.executeQuery(...);
 * if(lnRecdCtrxx > 0)
 *     loGCon.commitTrans();
 * else
 *     loGCon.rollbackTrans();
 * =========================================
 */
package ph.com.guanzongroup.querymanager.cas.base;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.lang3.StringUtils;
import org.guanzon.appdriver.base.GuanzonException;
import org.guanzon.appdriver.base.MiscUtil;
import org.guanzon.appdriver.base.SQLUtil;
import org.json.simple.JSONObject;

/**
 *
 * @author kalyptus
 */
public class QueryFXConnection {

    public void setupDataSource(String fsURL, String fsDBF, String fsUser, String fsPassWD, String fsPort) {
        BasicDataSource ds = new BasicDataSource();
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setUsername(fsUser);
        ds.setPassword(fsPassWD);
        ds.setUrl("jdbc:mysql://" + fsURL + ":" + fsPort + "/" + fsDBF);
        poDS = ds;
    }

    /*
    * Connect to the database using the values in the Data Source
     */
    public Connection doConnect() throws SQLException {
        if (poDS == null) {
            return null;
        }

        if (poCon != null) {
            poCon.close();
        }

        poCon = poDS.getConnection();

        psBatchNox = "";

        return poCon;
    }

    public Connection getConnection() {
        return poCon;
    }

    public void beginTrans(String fsEventDsc, String fsRemarksx, String fsSourceCD, String fsSourceNo) throws SQLException, GuanzonException {
        if (!poCon.getAutoCommit()) {
            throw new GuanzonException(GuanzonException.GE_SEQUENCE_EXCEPTION);
        }

        poCon.setAutoCommit(false);

        psBatchNox = generateBatchNo(psBranchCD, psTermNoxx);

        StringBuilder lsNme = new StringBuilder();

        //set fieldnames
        lsNme.append("(sTransNox");
        lsNme.append(", sComptrNm");
        lsNme.append(", sSourceCD");
        lsNme.append(", sSourceNo");
        lsNme.append(", sEventNme");
        lsNme.append(", sRemarksx");
        lsNme.append(", sModified");
        lsNme.append(", dModified)");

        Timestamp tme = getServerDate();

        StringBuilder lsSQL = new StringBuilder();
        lsSQL.append("(").append(SQLUtil.toSQL(psBatchNox));
        lsSQL.append(", ").append(SQLUtil.toSQL(MiscUtil.getPCName()));
        lsSQL.append(", ").append(SQLUtil.toSQL(fsSourceCD));
        lsSQL.append(", ").append(SQLUtil.toSQL(fsSourceNo));
        lsSQL.append(", ").append(SQLUtil.toSQL(fsEventDsc));
        lsSQL.append(", ").append(SQLUtil.toSQL(fsRemarksx));
        lsSQL.append(", ").append(SQLUtil.toSQL((psUserIDxx == null ? "" : psUserIDxx)));
        lsSQL.append(", ").append(SQLUtil.toSQL(tme)).append(")");

        //System.out.println(lsSQL.toString());
        executeUpdate("INSERT INTO xxxAuditLogMaster" + lsNme.toString() + " VALUES" + lsSQL.toString());
    }

    public static String extractTableName(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return null;
        }

        // Normalize SQL (uppercase, collapse spaces)
        String normalizedSQL = sql.trim().toUpperCase().replaceAll("\\s{2,}", " ");

        // Regex patterns for each statement type
        String[] patterns = {
            "^INSERT\\s+INTO\\s+(\\w+)", // INSERT INTO table
            "^UPDATE\\s+(\\w+)", // UPDATE table
            "^DELETE\\s+FROM\\s+(\\w+)", // DELETE FROM table
            "^REPLACE\\s+INTO\\s+(\\w+)" // REPLACE INTO table
        };

        for (String regex : patterns) {
            Matcher matcher = Pattern.compile(regex).matcher(normalizedSQL);
            if (matcher.find()) {
                return matcher.group(1); // return the captured table name
            }
        }

        return null; // no match found
    }

    public ResultSet executeQuery(String sql) throws SQLException {
        Statement loSQL = poCon.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
        ResultSet oRS = loSQL.executeQuery(sql);
        return oRS;
    }

    public long executeUpdate(String sql) throws SQLException, GuanzonException {
        if (!poCon.getAutoCommit()) {
            throw new GuanzonException(GuanzonException.GE_SEQUENCE_EXCEPTION);
        }

        long lnRecord;
        Statement loSQL = poCon.createStatement();
        sql = sql.replace(" 00:00:00", "");
        //System.out.println(":" + sql + ":" );
        lnRecord = loSQL.executeUpdate(sql);
        MiscUtil.close(loSQL);
        return lnRecord;
    }

    public void logQuery(String sql, String branch, String destinat, String division) throws SQLException, GuanzonException {
        if (!poCon.getAutoCommit()) {
            throw new GuanzonException(GuanzonException.GE_SEQUENCE_EXCEPTION);
        }

        //extract the table here...
        String table = extractTableName(sql);

        StringBuilder lsNme = new StringBuilder();
        String lsTransNox = getNextCode("xxxAuditLogDetail", "sTransNox", psBranchCD, psTermNoxx);
        //set fieldnames
        lsNme.append("(sTransNox");
        lsNme.append(", sBranchCd");
        lsNme.append(", sSourceNo");
        lsNme.append(", sQryTypex");
        lsNme.append(", cIsJsonxx");
        lsNme.append(", sPayloadx");
        lsNme.append(", sFilterxx");
        lsNme.append(", sTableNme");
        lsNme.append(", sDestinat");
        lsNme.append(", sDivision");
        lsNme.append(", sModified");
        lsNme.append(", dModified)");

        //set values
        StringBuilder lsSQL = new StringBuilder();
        //sTransNox
        lsSQL.append("(").append(SQLUtil.toSQL(lsTransNox));
        //sBranchCd
        lsSQL.append(", ").append(SQLUtil.toSQL(branch));
        //sSourceNo
        lsSQL.append(", ").append(SQLUtil.toSQL(psBatchNox));

        String query_type = sql.substring(0, sql.indexOf(" "));
        //sQryTypex
        lsSQL.append(", ").append(SQLUtil.toSQL(query_type));
        //cIsJsonxx
        lsSQL.append(", ").append(SQLUtil.toSQL("0"));

        JSONObject json = new JSONObject();
        json.put("data", sql);
        //sPayloadx
        lsSQL.append(", ").append(SQLUtil.toSQL(json.toJSONString()));
        //sFilterxx
        lsSQL.append(", ").append(SQLUtil.toSQL(""));
        //sTableNme
        lsSQL.append(", ").append(SQLUtil.toSQL(table));
        //sDestinat
        lsSQL.append(", ").append(SQLUtil.toSQL(destinat));
        //sDivision
        lsSQL.append(", ").append(SQLUtil.toSQL(division));
        //sModified
        lsSQL.append(", ").append(SQLUtil.toSQL((psUserIDxx == null ? "" : psUserIDxx)));

        Timestamp tme = getServerDate();
        //dModified
        lsSQL.append(", ").append(SQLUtil.toSQL(tme)).append(")");

        executeUpdate("INSERT INTO xxxAuditLogDetail" + lsNme.toString() + " VALUES" + lsSQL.toString());

    }

    public void commitTrans() throws SQLException, GuanzonException {
        if (poCon.getAutoCommit()) {
            throw new GuanzonException(GuanzonException.GE_SEQUENCE_EXCEPTION);
        }

        poCon.commit();
        poCon.setAutoCommit(true);
        psBatchNox = "";
    }

    public void rollbackTrans() throws SQLException, GuanzonException {
        if (poCon.getAutoCommit()) {
            throw new GuanzonException(GuanzonException.GE_SEQUENCE_EXCEPTION);
        }

        poCon.rollback();
        poCon.setAutoCommit(false);
        psBatchNox = "";
    }

    /*
    * get the timestamp from the mysql server
     */
    public Timestamp getServerDate() throws SQLException {
        if (poDS == null) {
            return null;
        }

        Connection loCon;
        if (poCon == null) {
            loCon = doConnect();
        } else {
            loCon = poCon;
        }

        //System.out.println(loCon.getMetaData().getDriverName());
        String lsSQL;

        //System.out.println(loCon.getMetaData().getDriverName());
        if (loCon.getMetaData().getDriverName().equalsIgnoreCase("SQLiteJDBC")) {
            lsSQL = "SELECT DATETIME('now','localtime')";
        } else if (loCon.getMetaData().getDriverName().equalsIgnoreCase("H2 JDBC Driver")) {
            lsSQL = "SELECT CURRENT_TIMESTAMP";
        } else {
            //assume that default database is MySQL ODBC
            lsSQL = "SELECT SYSDATE()";
        }

        ResultSet loRS = loCon.createStatement()
                .executeQuery(lsSQL);

        //position record pointer to the first record
        loRS.next();

        Timestamp loTimeStamp;

        if (loCon.getMetaData().getDriverName().equalsIgnoreCase("H2 JDBC Driver")) {
            loTimeStamp = loRS.getTimestamp(1);
        } else {
            loTimeStamp = Timestamp.valueOf(loRS.getString(1));
        }

        //assigned timestamp
        //loTimeStamp = loRS.getTimestamp(1);
        //loTimeStamp = loRS.getTimestamp(1); 
        MiscUtil.close(loRS);
        if (poCon == null) {
            MiscUtil.close(loCon);
        }

        return loTimeStamp;
    }

    /*
    Returns: 25 digit string with format BBBBTTYYMMDDHHMMSSXXXNNNN
    Where  :
        04 -> BBBB -> Branch Code
        02 -> TT   -> Terminal ID (should be numeric from 01-99)
        02 -> YY   -> 2 Digit Year
        02 -> MM   -> 2 Digit Month
        02 -> DD   -> 2 Digit Day of Month
        02 -> HH   -> 2 Digit hour (Make sure OS system date is in 24 hour format
        02 -> MM   -> 2 Digit minute 
        02 -> SS   -> 2 Digit seconds 
        03 -> XXXX -> 4 digit milliseconds
        04 -> NNNN -> 4 digit unique random number
     */
    private String generateBatchNo(String branchCode, String terminalId) {

        // Timestamp formatted as yyMMddHHmmssSSS (2-digit year, total 15 digits for timestamp)
        String timestamp = new SimpleDateFormat("yyMMddHHmmssSSS").format(new Date());

        // Append a 4-character random hexa suffix for extra uniqueness.
        String randomSuffix = String.format("%04X", random.nextInt(0x10000));

        // Concatenate branch code, terminal id, timestamp, and random suffix.
        return branchCode + terminalId + timestamp + randomSuffix;
    }

    /*
    Returns: 20 digit string with format BBBBTTYYYYMMNNNNNNNN

    Where  :
        04 -> BBBB -> Branch Code
        02 -> TT   -> Terminal ID (should be numeric from 01-99)
        04 -> YY   -> 4 Digit Year
        02 -> MM   -> 2 Digit Month
        08 -> NNNN -> 8 digit series
     */
    private String getNextCode(
            String fsTableNme,
            String fsFieldNme,
            String fsBranchCd,
            String fsTermIDxx) throws SQLException {

        int lnNext;
        String lsSQL;
        ResultSet loRS;

        // Timestamp formatted as yyMMddHHmmssSSS (6-digit year/month )
        String timestamp = new SimpleDateFormat("yyyyMM").format(new Date());

        //BBBBTTYYYYMM
        String lsPref = fsBranchCd + fsTermIDxx + timestamp;

        lsSQL = "SELECT " + fsFieldNme
                + " FROM " + fsTableNme
                + " WHERE " + fsFieldNme + " LIKE " + SQLUtil.toSQL(lsPref + "%")
                + " ORDER BY " + fsFieldNme + " DESC "
                + " LIMIT 1";

        loRS = executeQuery(lsSQL);
        if (loRS.next()) {
            lnNext = Integer.parseInt(loRS.getString(1).substring(lsPref.length()));
        } else {
            lnNext = 0;
        }

        String lsNextCde = lsPref + StringUtils.leftPad(String.valueOf(lnNext + 1), loRS.getMetaData().getPrecision(1) - lsPref.length(), "0");

        MiscUtil.close(loRS);

        return lsNextCde;
    }

    public void setBranch(String branch) {
        psBranchCD = branch;
    }

    public void setTerminalNo(String terminalno) {
        psTermNoxx = terminalno;
    }

    public void setUser(String user) {
        psUserIDxx = user;
    }

    public String getBatchNumber() {
        return psBatchNox;
    }

    private static final SecureRandom random = new SecureRandom();
    private BasicDataSource poDS;
    private Connection poCon;
    private String psUserIDxx;
    private String psBranchCD;
    private String psTermNoxx;

    private String psBatchNox;
}

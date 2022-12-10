package com.github.alanger.nexus.bootstrap;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;

import org.apache.shiro.util.JdbcUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.alanger.shiroext.AttributeMapper;
import com.orientechnologies.orient.jdbc.OrientDataSource;

public class OrientConnection {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    // Nexus jdbc data source
    private static final String CONNECTION_STR = "plocal:/nexus-data/db/security";
    private static final String CREATE_API_KEY_PROPERTY = "CREATE PROPERTY api_key.created IF NOT EXISTS DATETIME (MANDATORY TRUE)";
    private static final String ALTER_API_KEY_PROPERTY = "ALTER PROPERTY api_key.created DEFAULT \"sysdate()\"";
    private static final String REALM_NAMES_QUERY = "select realm_names from realm";

    private final AttributeMapper securityJdbcInfo = new AttributeMapper();
    private final OrientDataSource securityDataSource;

    public OrientConnection() {
        this(false);
    }

    public OrientConnection(boolean createProperty) {
        securityJdbcInfo.put("user", "admin");
        securityJdbcInfo.put("password", "admin");
        securityJdbcInfo.put("db.usePool", true);
        securityJdbcInfo.put("db.pool.min", 1);
        securityJdbcInfo.put("db.pool.max", 50);

        securityDataSource = new OrientDataSource("jdbc:orient:" + CONNECTION_STR, "admin", "admin", securityJdbcInfo);

        // Add created date to api_key table
        if (createProperty) {
            Connection conn = null;
            Statement stmt = null;
            try {
                conn = securityDataSource.getConnection();
                stmt = conn.createStatement();
                stmt.execute(CREATE_API_KEY_PROPERTY);
                stmt.execute(ALTER_API_KEY_PROPERTY);
            } catch (SQLException e) {
                logger.error("Create date in api_key table error", e);
            } finally {
                JdbcUtils.closeStatement(stmt);
                JdbcUtils.closeConnection(conn);
            }
        }
    }

    public AttributeMapper getSecurityJdbcInfo() {
        return securityJdbcInfo;
    }

    public OrientDataSource getSecurityDataSource() {
        return securityDataSource;
    }

    public Connection getSecurityConnection() throws SQLException {
        return getSecurityDataSource().getConnection();
    }

    @SuppressWarnings("unchecked")
    public List<String> getRealmNames() {
        List<String> realmNames = Arrays.asList("NexusAuthenticatingRealm", "NexusAuthorizingRealm");
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            conn = getSecurityConnection();
            stmt = conn.createStatement();
            rs = stmt.executeQuery(REALM_NAMES_QUERY);
            if (rs.next()) {
                Object obj = rs.getObject("realm_names"); // OTrackedList
                if (obj instanceof List)
                    realmNames = ((List<String>) obj).isEmpty() ? realmNames : (List<String>) obj;
            }
        } catch (Exception e) {
            logger.error("Getting realm names error", e);
        } finally {
            JdbcUtils.closeResultSet(rs);
            JdbcUtils.closeStatement(stmt);
            JdbcUtils.closeConnection(conn);
        }
        return realmNames;
    }

}

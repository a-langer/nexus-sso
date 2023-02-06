package com.github.alanger.nexus.bootstrap;

import static java.text.MessageFormat.format;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Collections;
import java.util.Properties;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.AuthenticationListener;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.util.JdbcUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.apache.shiro.web.subject.support.WebDelegatingSubject;

import com.github.alanger.shiroext.realm.pac4j.Pac4jPrincipalName;
import com.github.alanger.shiroext.realm.pac4j.Pac4jRealmName;
import com.github.alanger.shiroext.realm.RealmUtils;
import com.github.alanger.shiroext.realm.ICommonRole;

import org.pac4j.core.profile.UserProfile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Pac4jAuthenticationListener implements AuthenticationListener {

    public static final String ATTR_ID = "attrs";
    public static final String USER_ID = "id";
    public static final String PASSWORD_ID = "password";
    public static final String FIRST_NAME_ID = "firstName";
    public static final String LAST_NAME_ID = "lastName";
    public static final String EMAIL_ID = "email";
    public static final String STATUS_ID = "status";
    public static final String SOURCE_ID = "source";

    public static final String DEFAULT_STATUS = "active"; // active, locked, disabled, changepassword
    public static final String DEFAULT_SOURCE = "default";

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final DefaultWebSecurityManager securityManager;
    private final Properties mapper;
    private DataSource dataSource;

    private Class<?> principalClass = Pac4jPrincipalName.class;
    private Class<?> realmClass = Pac4jRealmName.class;

    private String userQuery = "SELECT * FROM user WHERE id = ''{0}''";
    private String userUpdate = "UPDATE user SET firstName = ''{1}'', lastName = ''{2}'', email = ''{3}'', status = ''{4}'', password = ''{5}'' WHERE id = ''{0}''";
    private String userInsert = "INSERT INTO user (id, firstName, lastName, email, status, password) VALUES (''{0}'', ''{1}'', ''{2}'', ''{3}'', ''{4}'', ''{5}'')";

    private String roleQuery = "SELECT * FROM user_role_mapping WHERE userId = ''{0}''";
    private String roleUpdate = "UPDATE user_role_mapping SET source = ''{1}'', roles = ''{2}'' WHERE userId = ''{0}''";
    private String roleInsert = "INSERT INTO user_role_mapping (userId, source, roles) VALUES (''{0}'', ''{1}'', ''{2}'')";

    public Pac4jAuthenticationListener() {
        this.securityManager = (DefaultWebSecurityManager) SecurityUtils.getSecurityManager();

        // Default SAML profile attributes
        Properties attrs = new Properties();
        attrs.put(USER_ID, USER_ID);
        attrs.put(PASSWORD_ID, PASSWORD_ID);
        attrs.put(FIRST_NAME_ID, FIRST_NAME_ID);
        attrs.put(LAST_NAME_ID, LAST_NAME_ID);
        attrs.put(EMAIL_ID, EMAIL_ID);
        attrs.put(STATUS_ID, STATUS_ID);
        attrs.put(SOURCE_ID, SOURCE_ID);
        this.mapper = new Properties();
        this.mapper.put(ATTR_ID, attrs);
    }

    public Pac4jAuthenticationListener(DataSource dataSource) {
        this();
        this.dataSource = dataSource;
    }

    @Override
    public void onSuccess(AuthenticationToken token, AuthenticationInfo ai) {
        PrincipalCollection principals = ai.getPrincipals();
        logger.trace("token: {}, info: {}, principals: {}", token, ai, principals);
        if (principals != null) {
            Pac4jPrincipalName principal = (Pac4jPrincipalName) principals.oneByType(principalClass);
            if (principal != null) {
                UserProfile profile = principal.getProfile();

                String id = this.getValue(profile, USER_ID, principal.toString());
                String password = this.getValue(profile, PASSWORD_ID, principals.getRealmNames().toString());
                String firstName = this.getValue(profile, FIRST_NAME_ID, id);
                String lastName = this.getValue(profile, LAST_NAME_ID, id);
                String realmName = principals.getRealmNames().iterator().next();
                String email = this.getValue(profile, EMAIL_ID, format("{0}@{1}.local", id, realmName));
                String status = this.getValue(profile, STATUS_ID, DEFAULT_STATUS);
                LinkedHashSet<String> roleSet = new LinkedHashSet<>(
                        profile != null ? profile.getRoles() : Collections.emptyList());
                for (Realm r : securityManager.getRealms()) {
                    if (principals.getRealmNames().contains(r.getName()) && realmClass.isAssignableFrom(r.getClass())
                            && (r instanceof ICommonRole)) {
                        roleSet.addAll(RealmUtils.asList(((ICommonRole) r).getCommonRole()));
                    }
                }
                String roles = String.join(",", roleSet);
                String source = this.getValue(profile, SOURCE_ID, DEFAULT_SOURCE);

                logger.trace("principal: {} = {}", principal, principal.getClass());
                logger.info("profile: {}", profile);
                logger.trace("attrs: firstName = {}, lastName = {}, email = {}, status = {}, source = {}, roles = '{}'",
                        firstName, lastName, email, status, source, roles);

                Connection conn = null;
                Statement stmt = null;
                ResultSet rs = null;
                try {
                    conn = dataSource.getConnection();
                    stmt = conn.createStatement();

                    // Set user profile
                    String sql = format(this.userUpdate, id, firstName, lastName, email, status, password);
                    String query = format(this.userQuery, id, firstName, lastName, email, status, password);
                    logger.trace("userQuery sql: {}", query);
                    rs = stmt.executeQuery(query);
                    if (rs.next()) {
                        String currentStatus = rs.getString(STATUS_ID);
                        if (!DEFAULT_STATUS.equals(currentStatus)) {
                            String tmpl = "Account '%s' must have '%s' status, current status is %s";
                            sendError(403, tmpl, id, DEFAULT_STATUS, currentStatus);
                            return;
                        }
                    } else {
                        sql = format(this.userInsert, id, firstName, lastName, email, status, password);
                    }
                    logger.trace("userUpdate/userInsert sql: {}", sql);
                    stmt.execute(sql);
                    rs.close();

                    // Set roles
                    sql = format(this.roleUpdate, id, source, roles);
                    query = format(this.roleQuery, id, source, roles);
                    logger.trace("roleQuery sql: {}", query);
                    rs = stmt.executeQuery(query);
                    if (!rs.next()) {
                        sql = format(this.roleInsert, id, source, roles);
                    }
                    logger.trace("roleUpdate/roleInsert sql: {}", sql);
                    stmt.execute(sql);
                } catch (SQLException e) {
                    logger.trace("onSuccess method error", e);
                    sendError(500, "Pac4jAuthenticationListener: %s", e.toString());
                } finally {
                    JdbcUtils.closeResultSet(rs);
                    JdbcUtils.closeStatement(stmt);
                    JdbcUtils.closeConnection(conn);
                }
            }
        }
    }

    public void onFailure(AuthenticationToken token, AuthenticationException ae) {
        logger.trace("onFailure token: {} , exception:", token, ae);
    }

    public void onLogout(PrincipalCollection principals) {
        logger.trace("onLogout principals: {}", principals);
    }

    private void sendError(int code, String tmpl, Object... objects) throws AuthenticationException {
        WebDelegatingSubject subject = (WebDelegatingSubject) SecurityUtils.getSubject();
        subject.logout();
        try {
            ((HttpServletResponse) subject.getServletResponse()).sendError(code, String.format(tmpl, objects));
        } catch (IOException e) {
            throw new AuthenticationException(e);
        }
    }

    // pac4jAuthenticationListener.attrs[firstName] = FirstName
    public Properties getAttrs() {
        return (Properties) this.mapper.get(ATTR_ID);
    }

    // pac4jAuthenticationListener.map(attrs) = firstName:FirstName,
    // lastName:LastName, email:Email
    public Properties getMap() {
        return mapper;
    }

    private String getValue(UserProfile profile, String key, String defValue) {
        if (profile == null)
            return defValue;
        key = getAttrs().get(key) != null ? getAttrs().get(key).toString() : key;
        Object val = profile.getAttribute(key) != null ? profile.getAttribute(key) : defValue;

        if (val.getClass().isArray()) {
            val = ((Object[]) val)[0];
        } else if (val instanceof List) {
            val = ((List<?>) val).get(0);
        } else if (val instanceof Set) {
            val = ((Set<?>) val).iterator().next();
        }
        return StringUtils.normalizeSpace(val.toString());
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void setPrincipalClass(String className) throws ClassNotFoundException {
        this.principalClass = Class.forName(className);
    }

    public void setRealmClass(String className) throws ClassNotFoundException {
        this.realmClass = Class.forName(className);
    }

    public String getUserQuery() {
        return userQuery;
    }

    public void setUserQuery(String userQuery) {
        this.userQuery = userQuery;
    }

    public String getUserUpdate() {
        return userUpdate;
    }

    public void setUserUpdate(String userUpdate) {
        this.userUpdate = userUpdate;
    }

    public String getUserInsert() {
        return userInsert;
    }

    public void setUserInsert(String userInsert) {
        this.userInsert = userInsert;
    }

    public String getRoleQuery() {
        return roleQuery;
    }

    public void setRoleQuery(String roleQuery) {
        this.roleQuery = roleQuery;
    }

    public String getRoleUpdate() {
        return roleUpdate;
    }

    public void setRoleUpdate(String roleUpdate) {
        this.roleUpdate = roleUpdate;
    }

    public String getRoleInsert() {
        return roleInsert;
    }

    public void setRoleInsert(String roleInsert) {
        this.roleInsert = roleInsert;
    }

}

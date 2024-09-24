package com.github.alanger.nexus.bootstrap;

import static java.text.MessageFormat.format;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.Properties;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.AuthenticationListener;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.apache.shiro.web.subject.support.WebDelegatingSubject;
import com.github.alanger.nexus.plugin.realm.NexusPac4jRealm;
import com.github.alanger.nexus.plugin.realm.Pac4jPrincipalName;
import com.github.alanger.nexus.plugin.realm.Pac4jRealmName;
import com.github.alanger.shiroext.realm.RealmUtils;
import io.buji.pac4j.token.Pac4jToken;
import com.github.alanger.shiroext.realm.ICommonRole;

import org.pac4j.core.profile.UserProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.nexus.security.config.CUser;
import org.sonatype.nexus.security.config.CUserRoleMapping;
import org.sonatype.nexus.security.config.SecurityConfiguration;

/**
 * SSO authentication listener.
 */
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

    private Class<?> principalClass = Pac4jPrincipalName.class;
    private Class<?> realmClass = Pac4jRealmName.class;

    public final SecurityConfiguration securityConfiguration;

    public Pac4jAuthenticationListener(final SecurityConfiguration securityConfiguration) {
        this.securityConfiguration = securityConfiguration;
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

    @Override
    public void onSuccess(AuthenticationToken token, AuthenticationInfo ai) {
        PrincipalCollection principals = ai.getPrincipals();
        logger.trace("token: {}, info: {}, principals: {}", token, ai, principals);

        if (principals != null && supports(token) && supports(principals)) {
            try {
                Pac4jPrincipalName principal = (Pac4jPrincipalName) principals.oneByType(principalClass);
                logger.trace("principal: {}, {}", principal, principal != null ? principal.getClass() : null);

                if (principal != null) {
                    UserProfile profile = principal.getProfile();
                    logger.info("profile: {}", profile);

                    String id = this.getValue(profile, USER_ID, principal.toString());
                    String password = this.getValue(profile, PASSWORD_ID, principals.getRealmNames().toString());
                    String firstName = this.getValue(profile, FIRST_NAME_ID, id);
                    String lastName = this.getValue(profile, LAST_NAME_ID, id);
                    String realmName = principals.getRealmNames().iterator().next();
                    String email = this.getValue(profile, EMAIL_ID, format("{0}@{1}.local", id, realmName));
                    String status = this.getValue(profile, STATUS_ID, DEFAULT_STATUS);

                    LinkedHashSet<String> roleSet = new LinkedHashSet<>(profile != null ? profile.getRoles() : Collections.emptyList());

                    for (Realm r : securityManager.getRealms()) {
                        if (r != null && principals.getRealmNames().contains(r.getName()) && realmClass.isAssignableFrom(r.getClass())
                                && (r instanceof ICommonRole)) {
                            roleSet.addAll(RealmUtils.asList(((ICommonRole) r).getCommonRole()));
                        }
                    }

                    String roles = String.join(",", roleSet);
                    String source = this.getValue(profile, SOURCE_ID, DEFAULT_SOURCE);

                    logger.trace("attrs: firstName = {}, lastName = {}, email = {}, status = {}, source = {}, roles = '{}'", firstName,
                            lastName, email, status, source, roles);

                    // Set user profile
                    CUser curUser = securityConfiguration.getUser(id);
                    CUser user = curUser != null ? curUser : securityConfiguration.newUser();
                    user.setId(id);
                    user.setFirstName(firstName);
                    user.setLastName(lastName);
                    user.setEmail(email);
                    user.setStatus(status);
                    user.setPassword(password);
                    if (curUser == null) {
                        securityConfiguration.addUser(user, roleSet);
                    } else {
                        securityConfiguration.updateUser(user, roleSet);
                    }

                    // Set roles
                    CUserRoleMapping curRoleMapping = securityConfiguration.getUserRoleMapping(id, source);
                    CUserRoleMapping roleMapping = curRoleMapping != null ? curRoleMapping : securityConfiguration.newUserRoleMapping();
                    roleMapping.setUserId(id);
                    roleMapping.setSource(source);
                    roleMapping.setRoles(roleSet);
                    if (curRoleMapping == null) {
                        securityConfiguration.addUserRoleMapping(roleMapping);
                    } else {
                        securityConfiguration.updateUserRoleMapping(roleMapping);
                    }
                }
            } catch (Exception e) {
                logger.error("onSuccess method error", e);
                sendError(500, "Pac4jAuthenticationListener: %s", e.toString());
            }
        }
    }

    public void onFailure(AuthenticationToken token, AuthenticationException ae) {
        if (supports(token)) {
            logger.trace("onFailure token: {}, exception:", token, ae);
        }
    }

    public void onLogout(PrincipalCollection principals) {
        if (supports(principals))
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
    @SuppressWarnings("unchecked")
    public Map<Object, Object> getAttrs() {
        return (Map<Object, Object>) this.mapper.get(ATTR_ID);
    }

    // pac4jAuthenticationListener.map(attrs) = firstName:FirstName, lastName:LastName, email:Email
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

    private boolean supports(AuthenticationToken token) {
        return token != null && Pac4jToken.class.isAssignableFrom(token.getClass());
    }

    private boolean supports(PrincipalCollection principals) {
        return NexusPac4jRealm.isSupports(principals, NexusPac4jRealm.NAME);
    }

    public void setPrincipalClass(String className) throws ClassNotFoundException {
        this.principalClass = Class.forName(className);
    }

    public void setRealmClass(String className) throws ClassNotFoundException {
        this.realmClass = Class.forName(className);
    }

    /** Deprecated since 3.70.1-02 */

    @Deprecated(since = "3.70.1-02")
    public void setUserQuery(String userQuery) {
        logger.warn("Property 'useQuery' has been deprecated since 3.70.1-02 and will be removed in next release");
    }

    @Deprecated(since = "3.70.1-02")
    public void setUserUpdate(String userUpdate) {
        logger.warn("Property 'userUpdate' has been deprecated since 3.70.1-02 and will be removed in next release");
    }

    @Deprecated(since = "3.70.1-02")
    public void setUserInsert(String userInsert) {
        logger.warn("Property 'userInsert' has been deprecated since 3.70.1-02 and will be removed in next release");
    }

    @Deprecated(since = "3.70.1-02")
    public void setRoleQuery(String roleQuery) {
        logger.warn("Property 'roleQuery' has been deprecated since 3.70.1-02 and will be removed in next release");
    }

    @Deprecated(since = "3.70.1-02")
    public void setRoleUpdate(String roleUpdate) {
        logger.warn("Property 'roleUpdate' has been deprecated since 3.70.1-02 and will be removed in next release");
    }

    @Deprecated(since = "3.70.1-02")
    public void setRoleInsert(String roleInsert) {
        logger.warn("Property 'roleInsert' has been deprecated since 3.70.1-02 and will be removed in next release");
    }

}

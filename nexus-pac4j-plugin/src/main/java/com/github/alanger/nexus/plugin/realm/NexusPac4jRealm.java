package com.github.alanger.nexus.plugin.realm;

import static java.text.MessageFormat.format;
import static com.github.alanger.shiroext.realm.RealmUtils.asList;
import static com.github.alanger.shiroext.realm.RealmUtils.filterBlackOrWhite;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.sisu.Description;
import org.pac4j.core.profile.UserProfile;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.RememberMeAuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authc.credential.SimpleCredentialsMatcher;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.web.subject.support.WebDelegatingSubject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.nexus.security.NexusSimpleAuthenticationInfo;
import org.sonatype.nexus.security.RealmCaseMapping;
import org.sonatype.nexus.security.SecurityHelper;
import org.sonatype.nexus.security.UserPrincipalsHelper;
import org.sonatype.nexus.security.anonymous.AnonymousPrincipalCollection;
import org.sonatype.nexus.security.config.CUser;
import org.sonatype.nexus.security.config.CUserRoleMapping;
import org.sonatype.nexus.security.config.SecurityConfiguration;
import com.github.alanger.shiroext.realm.RealmUtils;
import com.google.common.base.Preconditions;
import io.buji.pac4j.token.Pac4jToken;
import org.sonatype.nexus.security.user.NoSuchRoleMappingException;
import org.sonatype.nexus.security.user.NoSuchUserManagerException;
import org.sonatype.nexus.security.user.UserManager;
import org.sonatype.nexus.security.user.UserNotFoundException;

/**
 * Single Sign-On realm.
 * 
 * <p>
 * Since {@code 3.70.1-02} supports profile attribute mapping.
 */
@Singleton
@Named(NexusPac4jRealm.NAME)
@Description("SSO Pac4j Realm")
public class NexusPac4jRealm extends Pac4jRealmName {

    public static final String NAME = "pac4jRealm";

    // SAML profile attributes
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

    private final Properties mapper;

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    public final SecurityConfiguration securityConfiguration;

    private final SecurityHelper securityHelper;
    protected final UserPrincipalsHelper principalsHelper;

    @Inject
    public NexusPac4jRealm(final SecurityHelper securityHelper, final UserPrincipalsHelper principalsHelper,
            final SecurityConfiguration securityConfiguration) {

        this.securityHelper = Preconditions.checkNotNull(securityHelper);
        this.principalsHelper = Preconditions.checkNotNull(principalsHelper);
        this.securityConfiguration = Preconditions.checkNotNull(securityConfiguration);

        setName(NAME);

        // Cache not compatible with API token receiving request
        setAuthenticationCachingEnabled(false);
        setAuthorizationCachingEnabled(true);

        // Parent class for Pac4jToken and UsernamePasswordToken need for API token receiving request
        setAuthenticationTokenClass(RememberMeAuthenticationToken.class);

        // For API token receiving request
        setCredentialsMatcher(new SimpleCredentialsMatcher());

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

    protected void onInitDebug() {
        logger.trace("onInit name: {}, authenticationCachingEnabled: {}, authorizationCachingEnabled: {}, principalNameAttribute: {}" //
                + ", commonRole: [{}], commonPermission: [{}], permissionWhiteList: {}, permissionBlackList: {}, roleWhiteList: {}, roleBlackList: {}",
                getName(), isAuthenticationCachingEnabled(), isAuthorizationCachingEnabled(), getPrincipalNameAttribute(), getCommonRole(),
                getCommonPermission(), getPermissionWhiteList(), getPermissionBlackList(), getRoleWhiteList(), getRoleBlackList());
    }

    @Override
    protected void onInit() {
        onInitDebug();
        super.onInit();
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(final AuthenticationToken authToken) throws AuthenticationException {
        logger.trace("doGetAuthenticationInfo token: {}, {}", authToken, authToken != null ? authToken.getClass() : "class null");

        // API token receiving request
        if (authToken instanceof UsernamePasswordToken) {
            UsernamePasswordToken t = (UsernamePasswordToken) authToken;
            Subject subject = securityHelper.subject();

            logger.debug("API token receiving request for {}", t.getUsername());
            logger.trace("Authenticated: {}, subject principal: {} = {}, token principal: {} = {}", subject.isAuthenticated(), //
                    subject.getPrincipal(), subject.getPrincipal() != null ? subject.getPrincipal().getClass() : "class null", //
                    t.getPrincipal(), t.getPrincipal() != null ? t.getPrincipal().getClass() : "class null");

            if (subject.isAuthenticated() && subject.getPrincipal().toString().equals(t.getUsername())
                    && supports(subject.getPrincipals())) { // Using string principal
                AuthenticationInfo info = createAuthenticationInfo(subject);
                if (getCredentialsMatcher().doCredentialsMatch(authToken, info)) { // Login must be equal to password
                    logger.debug("API token receiving request has been approved for {}", t.getUsername());
                    return info;
                }
                throw new AuthenticationException("API token receiving request not approved for " + t.getUsername());
            }
        }

        // SAML
        if (authToken instanceof Pac4jToken) {
            logger.debug("SAML authentication: {}, {}", authToken, authToken.getClass());

            final Pac4jToken token = (Pac4jToken) authToken;

            // Compatibility with buji-pac4j 4.1.1
            final List<? extends UserProfile> profiles = token.getProfiles();

            final Pac4jPrincipalName principal = new Pac4jPrincipalName(profiles, getPrincipalNameAttribute());
            principal.setUserPrefix(getUserPrefix());
            final PrincipalCollection principalCollection = new SimplePrincipalCollection(principal.getName(), getName()); // Using string principal

            try {
                saveUserProfile(principal);
            } catch (Exception e) {
                logger.error("saveUserProfile method error", e);
                sendError(500, "NexusPac4jRealm: %s", e.toString());
                throw e instanceof AuthenticationException ? (AuthenticationException) e : new AuthenticationException(e);
            }
            return new SimpleAuthenticationInfo(principalCollection, profiles.hashCode());
        }

        throw new AuthenticationException("Token " + authToken + " is not applicable");
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        if (principals != null && supports(principals)) {
            logger.trace("doGetAuthorizationInfo principals: {}, {}", principals, principals.getClass());

            try {
                final SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();
                UserManager userManager = this.principalsHelper.findUserManager(principals);
                logger.debug("userManager: {}", userManager);

                Set<String> roles = userManager.getUser(principals.getPrimaryPrincipal().toString()).getRoles().stream()
                        .map(r -> r.getRoleId()).collect(Collectors.toSet());
                roles.addAll(asList(getCommonRole()));
                filterBlackOrWhite(roles, getRoleWhiteList(), getRoleBlackList());
                info.addRoles(roles);
                logger.debug("roles: {}", roles);

                Set<String> permissions = new HashSet<>();
                // Permissions are determined by roles
                // for (String role : roles) {
                //     permissions.addAll(securityConfiguration.getRole(role).getPrivileges());
                // }
                permissions.addAll(asList(getCommonPermission()));
                filterBlackOrWhite(permissions, getPermissionWhiteList(), getPermissionBlackList());
                info.addStringPermissions(permissions);
                logger.debug("permissions: {}", permissions);

                return info;
            } catch (NoSuchUserManagerException | UserNotFoundException e) {
                logger.debug("doGetAuthorizationInfo error", e);
            }
        }

        return null;
    }

    /**
     * AuthenticationInfo for API token receiving request, wher login = password.
     * Allows authentication using the username in cases where the user has logged in via SSO.
     */
    private AuthenticationInfo createAuthenticationInfo(final Subject subject) {
        return new NexusSimpleAuthenticationInfo(subject.getPrincipal().toString(), subject.getPrincipal().toString().toCharArray(),
                new RealmCaseMapping(getName(), true));
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean supports(AuthenticationToken token) {
        return token != null && getAuthenticationTokenClass().isAssignableFrom(token.getClass())
                && (Pac4jToken.class.isAssignableFrom(token.getClass()) || UsernamePasswordToken.class.isAssignableFrom(token.getClass()));
    }

    public boolean supports(PrincipalCollection principals) {
        return principals != null && (!AnonymousPrincipalCollection.class.isAssignableFrom(principals.getClass())
                && !principals.fromRealm(getName()).isEmpty());
    }

    /**
     * Save user profile to data store.
     * 
     * @param principal
     * @throws UserNotFoundException
     * @throws NoSuchRoleMappingException
     * 
     * @since 3.70.1-02
     */
    private void saveUserProfile(Pac4jPrincipalName principal) throws UserNotFoundException, NoSuchRoleMappingException {
        logger.trace("principal: {} ({})", principal, principal != null ? principal.getClass() : "class null");

        Objects.requireNonNull(principal, "principal must be not null");

        UserProfile profile = principal.getProfile();
        logger.info("profile: {}", profile);

        String id = this.getValue(profile, USER_ID, principal.toString());
        String password = this.getValue(profile, PASSWORD_ID, "[" + getName() + "]");
        String firstName = this.getValue(profile, FIRST_NAME_ID, id);
        String lastName = this.getValue(profile, LAST_NAME_ID, id);
        String realmName = getName();
        String email = this.getValue(profile, EMAIL_ID, format("{0}@{1}.local", id, realmName));
        String status = this.getValue(profile, STATUS_ID, DEFAULT_STATUS);

        LinkedHashSet<String> roleSet = new LinkedHashSet<>(profile != null ? profile.getRoles() : Collections.emptyList());
        roleSet.addAll(RealmUtils.asList(getCommonRole()));

        String roles = String.join(",", roleSet);
        String source = this.getValue(profile, SOURCE_ID, DEFAULT_SOURCE);

        logger.trace("attrs: firstName = {}, lastName = {}, email = {}, status = {}, source = {}, roles = '{}'", firstName, lastName, email,
                status, source, roles);

        // Set user profile
        CUser curUser = securityConfiguration.getUser(id);
        CUser user = curUser != null ? curUser : securityConfiguration.newUser();
        user.setId(id);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.setStatus(status);
        if (curUser == null) {
            user.setPassword(password); // Set only in new account
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

    private static void sendError(int code, String tmpl, Object... objects) throws AuthenticationException {
        WebDelegatingSubject subject = (WebDelegatingSubject) SecurityUtils.getSubject();
        subject.logout();
        try {
            ((HttpServletResponse) subject.getServletResponse()).sendError(code, String.format(tmpl, objects));
        } catch (IOException e) {
            throw new AuthenticationException(e);
        }
    }

    /**
     * User profile attributes mapping by separately, add to in shiro.ini:
     * 
     * <pre>
     * pac4jRealm.attrs[id] = myIdpUPN
     * pac4jRealm.attrs[firstName] = myIdpFirstName
     * pac4jRealm.attrs[lastName] = myIdpLastName
     * pac4jRealm.attrs[email] = myIdpEmailaddress
     * </pre>
     * 
     * @since 3.70.1-02
     */
    @SuppressWarnings("unchecked")
    public Map<Object, Object> getAttrs() {
        return (Map<Object, Object>) this.mapper.get(ATTR_ID);
    }

    /**
     * User profile attributes mapping in one line, example of shiro.ini:
     * 
     * <pre>
     * pac4jRealm.map(attrs) = firstName:myIdpFirstName, lastName:myIdpLastName, email:myIdpEmailaddress
     * </pre>
     * 
     * @since 3.70.1-02
     */
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

}

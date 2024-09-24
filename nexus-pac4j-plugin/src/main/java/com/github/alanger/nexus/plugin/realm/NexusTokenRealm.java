package com.github.alanger.nexus.plugin.realm;

import static com.github.alanger.shiroext.realm.RealmUtils.asList;
import static com.github.alanger.shiroext.realm.RealmUtils.filterBlackOrWhite;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.sql.DataSource;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.DisabledAccountException;
import org.apache.shiro.authc.ExpiredCredentialsException;
import org.apache.shiro.authc.HostAuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.UnknownAccountException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.subject.PrincipalCollection;
import org.eclipse.sisu.Description;
import org.sonatype.nexus.security.UserPrincipalsHelper;
import org.sonatype.nexus.security.anonymous.AnonymousPrincipalCollection;
import org.sonatype.nexus.security.authc.NexusApiKeyAuthenticationToken;
import org.sonatype.nexus.security.authc.apikey.ApiKey;
import org.sonatype.nexus.security.config.SecurityConfiguration;
import org.sonatype.nexus.security.user.NoSuchUserManagerException;
import org.sonatype.nexus.security.user.UserManager;
import org.sonatype.nexus.security.user.UserNotFoundException;
import com.github.alanger.nexus.plugin.apikey.ApiTokenService;
import com.github.alanger.shiroext.realm.jdbc.JdbcRealmName;
import com.google.common.base.Preconditions;

/**
 * User Token realm. Each user can set a personal token that can be used instead of a password.
 * The creation of tokens is implemented through the "NuGet API Key" menu (privilegies 
 * {@code nx-apikey-all} required), however, the tokens themselves apply to all types of repositories
 * <p>
 * 
 * NOTE: Extends {@link com.github.alanger.shiroext.realm.jdbc.JdbcRealmName JdbcRealmName}
 * for compatibility with previous version.
 * If extends {@link com.github.alanger.nexus.plugin.realm.NexusPac4jRealm NexusPac4jRealm},
 * then method {@link #doGetAuthorizationInfo(PrincipalCollection)} not working, extending 
 * {@link com.github.alanger.nexus.plugin.realm.Pac4jRealmName Pac4jRealmName} working well.
 * 
 * @see https://help.sonatype.com/en/user-tokens.html
 */
@Singleton
@Named(NexusTokenRealm.NAME)
@Description("SSO Token Realm")
public class NexusTokenRealm extends JdbcRealmName {

    public static final String NAME = "tokenRealm";
    public static final String DOMAIN = ApiTokenService.DOMAIN;

    private final UserPrincipalsHelper principalsHelper;

    private final ApiTokenService apiTokenService;

    private int expirationDays = 365; // One year

    @Inject
    public NexusTokenRealm(final SecurityConfiguration securityConfiguration //
            , final UserPrincipalsHelper principalsHelper //
            , final ApiTokenService apiTokenService) {

        this.principalsHelper = Preconditions.checkNotNull(principalsHelper);
        this.apiTokenService = Preconditions.checkNotNull(apiTokenService);

        setName(NAME);

        // Cache for API token
        setAuthenticationCachingEnabled(true);
        setAuthorizationCachingEnabled(true);

        // Parent class for UsernamePasswordToken and NexusApiKeyAuthenticationToken, except Pac4jToken
        setAuthenticationTokenClass(HostAuthenticationToken.class);

        logger.trace("NexusTokenRealm: {}", this);
    }

    @Override
    protected void onInit() {
        logger.trace("onInit name: {}, authenticationCachingEnabled: {}, authorizationCachingEnabled: {}, principalNameAttribute: {}" //
                + ", commonRole: {}, commonPermission: {}, permissionWhiteList: {}, permissionBlackList: {}, roleWhiteList: {}, roleBlackList: {}, expirationDays: {}",
                getName(), isAuthenticationCachingEnabled(), isAuthorizationCachingEnabled(), getPrincipalNameAttribute(), getCommonRole(),
                getCommonPermission(), getPermissionWhiteList(), getPermissionBlackList(), getRoleWhiteList(), getRoleBlackList(),
                getExpirationDays());
        super.onInit();
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        logger.trace("doGetAuthenticationInfo token: {}, {}", token, token != null ? token.getClass() : "class null");

        if (token != null) {
            UsernamePasswordToken t = toUsernamePasswordToken(token);

            logger.debug("Looking up API key for: {}", t);
            ApiKey key = apiTokenService.findApiKey(t).orElse(null);

            if (key != null) {
                logger.debug("Found API key principal: {}, realms: {}", key.getPrimaryPrincipal(), key.getPrincipals().getRealmNames());

                if (ApiTokenService.isExpired(key, expirationDays)) {
                    logger.debug("API token for {} ({}) is expired, created: {}, expirationDays: {}", t.getUsername(),
                            key.getPrimaryPrincipal(), key.getCreated(), expirationDays);
                    throw new ExpiredCredentialsException("Account " + t.getUsername() + " is expired");
                }
                try {
                    if (this.principalsHelper.getUserStatus(key.getPrincipals()).isActive()) {
                        logger.debug("API token has been authenticated for {} ({})", t.getUsername(), key.getPrimaryPrincipal());
                        return new SimpleAuthenticationInfo(key.getPrimaryPrincipal(), key.getApiKey(), getName());
                    }
                    throw new DisabledAccountException("Account " + t.getUsername() + " is disabled");
                } catch (UserNotFoundException e) {
                    logger.debug("User {} ({}) not found, removing stale API token", t.getUsername(), key.getPrimaryPrincipal());
                    apiTokenService.deleteApiKey(DOMAIN, key.getPrincipals());
                    throw new UnknownAccountException("Account " + t.getUsername() + " not found", e);
                }
            }
        }

        throw new AuthenticationException("Token " + token + " is not applicable");
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        logger.trace("doGetAuthorizationInfo principals: {}, {}", principals, principals != null ? principals.getClass() : "class null");

        if (principals != null && supports(principals)) {
            try {
                final SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();
                UserManager userManager = this.principalsHelper.findUserManager(principals);

                Set<String> roles = userManager.getUser(principals.getPrimaryPrincipal().toString()).getRoles().stream()
                        .map(r -> r.getRoleId()).collect(Collectors.toSet());
                roles.addAll(asList(getCommonRole()));
                filterBlackOrWhite(roles, getRoleWhiteList(), getRoleBlackList());
                info.addRoles(roles);
                logger.debug("Roles: {}", roles);

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
     * Token can be {@link org.apache.shiro.authc.UsernamePasswordToken}
     * or {@link org.sonatype.nexus.security.authc.NexusApiKeyAuthenticationToken}.
     * 
     * @see org.sonatype.nexus.security.authc.NexusApiKeyAuthenticationToken
     * @see org.apache.shiro.authc.UsernamePasswordToken
     */
    private UsernamePasswordToken toUsernamePasswordToken(AuthenticationToken token) {
        if (token instanceof UsernamePasswordToken) {
            return (UsernamePasswordToken) token;
        } else {
            return new UsernamePasswordToken(token.getPrincipal().toString(), (char[]) token.getCredentials());
        }
    }

    @Override
    public boolean supports(AuthenticationToken token) {
        return isSupports(token);
    }

    public static boolean isSupports(AuthenticationToken token) {
        return token != null && (UsernamePasswordToken.class.isAssignableFrom(token.getClass()) //
                || NexusApiKeyAuthenticationToken.class.isAssignableFrom(token.getClass()));
    }

    public boolean supports(PrincipalCollection principals) {
        return isSupports(principals, getName());
    }

    public static boolean isSupports(PrincipalCollection principals, String realmName) {
        return principals != null && (!AnonymousPrincipalCollection.class.isAssignableFrom(principals.getClass())
                && !principals.fromRealm(realmName).isEmpty());
    }

    public int getExpirationDays() {
        return expirationDays;
    }

    /**
     * API Token expiration in days, by default it is 365 days. Set to '-1' for unlimited.
     * 
     * @since 3.70.1-02
     * @param expirationDays Number of days for which you want user tokens to remain valid
     */
    public void setExpirationDays(int expirationDays) {
        this.expirationDays = expirationDays;
    }

    /** Deprecated since 3.70.1-02 */

    @Deprecated(since = "3.70.1-02")
    @Override
    public void setDataSource(DataSource dataSource) {
        logger.warn("Property 'dataSource' has been deprecated since 3.70.1-02 and will be removed in next release");
    }

    @Deprecated(since = "3.70.1-02")
    @Override
    public void setSkipIfNullAttribute(boolean skipIfNullAttribute) {
        logger.warn("Property 'skipIfNullAttribute' has been deprecated since 3.70.1-02 and will be removed in next release");
    }

    @Deprecated(since = "3.70.1-02")
    @Override
    public void setPrincipalNameAttribute(String principalNameAttribute) {
        logger.warn("Property 'principalNameAttribute' has been deprecated since 3.70.1-02 and will be removed in next release");
    }

    @Deprecated(since = "3.70.1-02")
    @Override
    public void setFindByPassword(boolean findByPassword) {
        logger.warn("Property 'findByPassword' has been deprecated since 3.70.1-02 and will be removed in next release");
    }

    @Deprecated(since = "3.70.1-02")
    @Override
    public void setPrincipalNameQuery(String principalNameQuery) {
        logger.warn("Property 'principalNameQuery' has been deprecated since 3.70.1-02 and will be removed in next release");
    }

    @Deprecated(since = "3.70.1-02")
    @Override
    public void setAuthenticationQuery(String authenticationQuery) {
        logger.warn("Property 'authenticationQuery' has been deprecated since 3.70.1-02 and will be removed in next release");
    }

    @Deprecated(since = "3.70.1-02")
    @Override
    public void setUserRolesQuery(String userRolesQuery) {
        logger.warn("Property 'userRolesQuer' has been deprecated since 3.70.1-02 and will be removed in next release");
    }

}

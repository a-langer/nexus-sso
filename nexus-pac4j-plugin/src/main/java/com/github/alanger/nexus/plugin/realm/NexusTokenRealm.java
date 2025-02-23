package com.github.alanger.nexus.plugin.realm;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.DisabledAccountException;
import org.apache.shiro.authc.ExpiredCredentialsException;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.UnknownAccountException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.eclipse.sisu.Description;
import org.sonatype.nexus.security.SecurityHelper;
import org.sonatype.nexus.security.UserPrincipalsHelper;
import org.sonatype.nexus.security.authc.NexusApiKeyAuthenticationToken;
import org.sonatype.nexus.security.authc.apikey.ApiKey;
import org.sonatype.nexus.security.config.SecurityConfiguration;
import org.sonatype.nexus.security.user.UserNotFoundException;
import com.github.alanger.nexus.plugin.apikey.ApiTokenService;
import com.google.common.base.Preconditions;

/**
 * User Token realm. Each user can set a personal token that can be used instead of a password.
 * The creation of tokens is implemented through the "NuGet API Key" menu (privilegies 
 * {@code nx-apikey-all} required), however, the tokens themselves apply to all types of repositories
 * 
 * @see https://help.sonatype.com/en/user-tokens.html
 * @see org.sonatype.nexus.security.token.BearerTokenRealm
 */
@Singleton
@Named(NexusTokenRealm.NAME)
@Description("SSO Token Realm")
public class NexusTokenRealm extends NexusPac4jRealm {

    public static final String NAME = "tokenRealm";
    public static final String DOMAIN = ApiTokenService.DOMAIN;

    private final ApiTokenService apiTokenService;

    private int expirationDays = 365; // One year

    private boolean domainAsLogin = false;

    @Inject
    public NexusTokenRealm(final SecurityHelper securityHelper, final UserPrincipalsHelper principalsHelper,
            final SecurityConfiguration securityConfiguration, final ApiTokenService apiTokenService) {
        super(securityHelper, principalsHelper, securityConfiguration);

        this.apiTokenService = Preconditions.checkNotNull(apiTokenService);

        setName(NAME);

        // Cache for API token
        setAuthenticationCachingEnabled(true);
        setAuthorizationCachingEnabled(true);

        // Only UsernamePasswordToken
        setAuthenticationTokenClass(UsernamePasswordToken.class);
    }

    @Override
    protected void onInitDebug() {
        logger.trace("onInit name: {}, authenticationCachingEnabled: {}, authorizationCachingEnabled: {}, principalNameAttribute: {}" //
                + ", commonRole: [{}], commonPermission: [{}], permissionWhiteList: {}, permissionBlackList: {}, roleWhiteList: {}, roleBlackList: {}, expirationDays: {}",
                getName(), isAuthenticationCachingEnabled(), isAuthorizationCachingEnabled(), getPrincipalNameAttribute(), getCommonRole(),
                getCommonPermission(), getPermissionWhiteList(), getPermissionBlackList(), getRoleWhiteList(), getRoleBlackList(),
                getExpirationDays());
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        logger.trace("doGetAuthenticationInfo token: {}, {}", token, token != null ? token.getClass() : "class null");

        if (token != null) {
            logger.debug("Looking up API key for: {}", token);

            UsernamePasswordToken t = toUsernamePasswordToken(token);
            ApiKey key = apiTokenService.findApiKey(t, domainAsLogin).orElse(null);

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
    @SuppressWarnings("unchecked")
    public boolean supports(AuthenticationToken token) {
        return token != null && getAuthenticationTokenClass().isAssignableFrom(token.getClass())
                && (UsernamePasswordToken.class.isAssignableFrom(token.getClass())
                        || NexusApiKeyAuthenticationToken.class.isAssignableFrom(token.getClass()));
    }

    public int getExpirationDays() {
        return expirationDays;
    }

    /**
     * API Token expiration in days, by default it is 365 days. Set to '-1' for unlimited.
     * Example of shiro.ini:
     * 
     * <pre>
     * # 365 days
     * tokenRealm.expirationDays = 365
     * # Unlimited
     * tokenRealm.expirationDays = -1
     * </pre>
     * 
     * @since 3.70.1-02
     * @param expirationDays Number of days for which you want user tokens to remain valid
     */
    public void setExpirationDays(int expirationDays) {
        this.expirationDays = expirationDays;
    }

    public boolean isDomainAsLogin() {
        return domainAsLogin;
    }

    /**
     * Alow using a domain, such as {@code NuGetApiKey} or {@code DockerToken}, as user login.
     * 
     * <p>
     * Example of shiro.ini, where using parent class for UsernamePasswordToken and NexusApiKeyAuthenticationToken, except Pac4jToken:
     * 
     * <pre language="ini">
     * tokenRealm.authenticationTokenClass = org.apache.shiro.authc.HostAuthenticationToken
     * tokenRealm.domainAsLogin = true
     * </pre>
     * 
     * @since 3.70.1-02
     * @param domainAsLogin
     */
    public void setDomainAsLogin(boolean domainAsLogin) {
        this.domainAsLogin = domainAsLogin;
    }

}

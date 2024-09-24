package com.github.alanger.nexus.plugin.realm;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.sisu.Description;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.RememberMeAuthenticationToken;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.nexus.security.NexusSimpleAuthenticationInfo;
import org.sonatype.nexus.security.RealmCaseMapping;
import org.sonatype.nexus.security.SecurityHelper;
import com.google.common.base.Preconditions;
import io.buji.pac4j.token.Pac4jToken;

/**
 * Single Sign-On realm.
 */
@Singleton
@Named(NexusPac4jRealm.NAME)
@Description("SSO Pac4j Realm")
public class NexusPac4jRealm extends Pac4jRealmName {

    public static final String NAME = "pac4jRealm";

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final SecurityHelper securityHelper;

    @Inject
    public NexusPac4jRealm(final SecurityHelper securityHelper) {

        this.securityHelper = Preconditions.checkNotNull(securityHelper);

        setName(NAME);

        // Cache not compatible with API token receiving request
        setAuthenticationCachingEnabled(false);

        // Parent class for Pac4jToken and UsernamePasswordToken need for API token receiving request
        setAuthenticationTokenClass(RememberMeAuthenticationToken.class);

        logger.trace("NexusPac4jRealm: {}", this);
    }

    @Override
    protected void onInit() {
        logger.trace("onInit name: {}, authenticationCachingEnabled: {}, authorizationCachingEnabled: {}, principalNameAttribute: {}" //
                + ", commonRole: {}, commonPermission: {}, permissionWhiteList: {}, permissionBlackList: {}, roleWhiteList: {}, roleBlackList: {}",
                getName(), isAuthenticationCachingEnabled(), isAuthorizationCachingEnabled(), getPrincipalNameAttribute(), getCommonRole(),
                getCommonPermission(), getPermissionWhiteList(), getPermissionBlackList(), getRoleWhiteList(), getRoleBlackList());
        super.onInit();
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(final AuthenticationToken token) throws AuthenticationException {
        logger.trace("doGetAuthenticationInfo token: {}, {}", token, token != null ? token.getClass() : "class null");

        // API token receiving request
        if (token instanceof UsernamePasswordToken) {
            UsernamePasswordToken t = (UsernamePasswordToken) token;
            Subject subject = securityHelper.subject();

            logger.debug("API token receiving request for {}", t.getUsername());
            logger.trace("Authenticated: {}, subject principal: {} = {}, token principal: {} = {}", subject.isAuthenticated(), //
                    subject.getPrincipal(), subject.getPrincipal() != null ? subject.getPrincipal().getClass() : "class null", //
                    t.getPrincipal(), t.getPrincipal() != null ? t.getPrincipal().getClass() : "class null");

            if (subject.isAuthenticated() && subject.getPrincipal().toString().equals(t.getUsername())
                    && Pac4jPrincipalName.class.isAssignableFrom(subject.getPrincipal().getClass())) {
                logger.debug("API token receiving request has been approved for {}", t.getUsername());
                return createAuthenticationInfo(subject);
            }
        }

        // SAML
        if (token instanceof Pac4jToken) {
            logger.debug("SAML authentication: {}, {}", token, token.getClass());
            return super.doGetAuthenticationInfo(token);
        }

        throw new AuthenticationException("Token " + token + " is not applicable");
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        logger.trace("doGetAuthorizationInfo principals: {}, {}", principals, principals != null ? principals.getClass() : "class null");
        if (supports(principals)) {
            return super.doGetAuthorizationInfo(principals);
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
    public boolean supports(AuthenticationToken token) {
        return isSupports(token);
    }

    public static boolean isSupports(AuthenticationToken token) {
        return token != null && (Pac4jToken.class.isAssignableFrom(token.getClass()) //
                || UsernamePasswordToken.class.isAssignableFrom(token.getClass()));
    }

    public boolean supports(PrincipalCollection principals) {
        return isSupports(principals, getName());
    }

    public static boolean isSupports(PrincipalCollection principals, String realmName) {
        return NexusTokenRealm.isSupports(principals, realmName);
    }

}

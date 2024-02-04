package com.github.alanger.nexus.plugin.realm;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.sisu.Description;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.RememberMeAuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.nexus.security.NexusSimpleAuthenticationInfo;
import org.sonatype.nexus.security.RealmCaseMapping;
import com.github.alanger.shiroext.realm.pac4j.Pac4jPrincipalName;
import com.github.alanger.shiroext.realm.pac4j.Pac4jRealmName;
import io.buji.pac4j.token.Pac4jToken;

/**
 * Single Sign-On realm.
 */
@Singleton
@Named(NexusPac4jRealm.NAME)
@Description("SSO Pac4j Realm")
public class NexusPac4jRealm extends Pac4jRealmName {

    public static final String NAME = "pac4jRealm";

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final boolean orient;

    @Inject
    public NexusPac4jRealm(@Named("${nexus.orient.enabled:-false}") final boolean orient) {
        this.orient = orient;
        setName(NAME);

        // Cache not compatible with API token
        setAuthenticationCachingEnabled(false);

        // Needed to request an API token
        setAuthenticationTokenClass(RememberMeAuthenticationToken.class);

        logger.trace("NexusPac4jRealm: {}, orient: {}", this, orient);
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(final AuthenticationToken token) throws AuthenticationException {
        logger.trace("doGetAuthenticationInfo token: {}, {}", token, token != null ? token.getClass() : null);

        // API Token
        if (token instanceof UsernamePasswordToken) {
            UsernamePasswordToken t = (UsernamePasswordToken) token;

            Subject subject = SecurityUtils.getSubject();
            logger.trace("Principal: {} = {}, authenticated: {}, token principal: {} = {}",  subject.getPrincipal(), subject.getPrincipal().getClass(),
                    subject.isAuthenticated(), t.getPrincipal(), t.getPrincipal().getClass());

            if (subject.isAuthenticated() && subject.getPrincipal().toString().equals(t.getUsername())
                    && Pac4jPrincipalName.class.isAssignableFrom(subject.getPrincipal().getClass())) {
                logger.trace("API token {} has been approved", token);
                return createAuthenticationInfo(subject);
            }
        }

        // SAML
        if (token instanceof Pac4jToken) {
            logger.trace("SAML token: {}, {}", token, token.getClass());
            return super.doGetAuthenticationInfo(token);
        }

        logger.trace("Unknown token: {}, {}", token, token != null ? token.getClass() : null);
        throw new AuthenticationException("Token " + token + " is not applicable");
    }

    private AuthenticationInfo createAuthenticationInfo(final Subject subject) {
        return orient
                ? new NexusSimpleAuthenticationInfo(subject.getPrincipal().toString(), subject.getPrincipal().toString().toCharArray(),
                        new RealmCaseMapping(getName(), true))
                : new SimpleAuthenticationInfo(subject.getPrincipals(), subject.getPrincipal().toString().toCharArray(), getName());
    }
    
}

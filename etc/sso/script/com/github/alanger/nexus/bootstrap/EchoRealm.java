package com.github.alanger.nexus.bootstrap;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authc.SimpleAccount;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.SecurityUtils;

import com.github.alanger.shiroext.realm.pac4j.Pac4jPrincipalName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EchoRealm extends AuthorizingRealm {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public EchoRealm() {
        setAuthenticationTokenClass(UsernamePasswordToken.class);
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        try {
            UsernamePasswordToken t = (UsernamePasswordToken) token;
            Subject subject = SecurityUtils.getSubject();
            if (subject != null && subject.isAuthenticated()
                    && subject.getPrincipal().toString().equals(t.getUsername())
                    && Pac4jPrincipalName.class.isAssignableFrom(subject.getPrincipal().getClass())) {
                logger.trace("echoRealm doGetAuthenticationInfo token: {}", token);
                return new SimpleAccount(t.getUsername(), t.getUsername(), this.getName());
            }
        } catch (AuthenticationException e) {
            logger.warn("doGetAuthenticationInfo error:", e);
        }
        return null;
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        return null;
    }
}

package com.github.alanger.nexus.bootstrap;

import java.util.Map;
import java.util.Properties;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.AuthenticationListener;
import org.apache.shiro.subject.PrincipalCollection;
import com.github.alanger.nexus.plugin.DI;
import com.github.alanger.nexus.plugin.realm.NexusPac4jRealm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SSO authentication listener.
 * 
 * @deprecated Since {@code 3.70.1-02} and will be removed. 
 * Attribute mapping moved to {@link com.github.alanger.nexus.plugin.realm.NexusPac4jRealm NexusPac4jRealm}.
 */
@Deprecated(since = "3.70.1-02", forRemoval = true)
public class Pac4jAuthenticationListener implements AuthenticationListener {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final NexusPac4jRealm pac4jRealm;

    public Pac4jAuthenticationListener() {
        this.pac4jRealm = DI.getInstance().pac4jRealm;
    }

    @Override
    public void onSuccess(AuthenticationToken token, AuthenticationInfo ai) {
        logger.warn("Class '{}' has been deprecated since 3.70.1-02 and will be removed in next release", getClass().getCanonicalName());
    }

    @Override
    public void onFailure(AuthenticationToken token, AuthenticationException ae) {
        // none
    }

    @Override
    public void onLogout(PrincipalCollection principals) {
        // none
    }

    /** 
     * @deprecated Since 3.70.1-02 and will be removed.
     * Use {@link com.github.alanger.nexus.plugin.realm.NexusPac4jRealm#getAttrs() NexusPac4jRealm#getAttrs}.
     * */
    @Deprecated(since = "3.70.1-02", forRemoval = true)
    public Map<Object, Object> getAttrs() {
        logger.warn("Property 'attrs' has been deprecated since 3.70.1-02 and will be removed in next release, use 'pac4jRealm.attrs[id]'");
        return this.pac4jRealm.getAttrs();
    }

    /** 
     * @deprecated Since 3.70.1-02 and will be removed.
     * Use {@link com.github.alanger.nexus.plugin.realm.NexusPac4jRealm#getMap() NexusPac4jRealm#getMap}.
     * */
    @Deprecated(since = "3.70.1-02", forRemoval = true)
    public Properties getMap() {
        logger.warn("Property 'map' has been deprecated since 3.70.1-02 and will be removed in next release, use 'pac4jRealm.map(attrs)'");
        return this.pac4jRealm.getMap();
    }

    /** @deprecated Since 3.70.1-02 and will be removed */
    @Deprecated(since = "3.70.1-02", forRemoval = true)
    public void setPrincipalClass(String className) throws ClassNotFoundException {
        logger.warn("Property 'principalClass' has been deprecated since 3.70.1-02 and will be removed in next release");
    }

    /** @deprecated Since 3.70.1-02 and will be removed */
    @Deprecated(since = "3.70.1-02", forRemoval = true)
    public void setRealmClass(String className) throws ClassNotFoundException {
        logger.warn("Property 'realmClass' has been deprecated since 3.70.1-02 and will be removed in next release");
    }

    /** @deprecated Since 3.70.1-02 and will be removed */
    @Deprecated(since = "3.70.1-02", forRemoval = true)
    public void setUserQuery(String userQuery) {
        logger.warn("Property 'useQuery' has been deprecated since 3.70.1-02 and will be removed in next release");
    }

    /** @deprecated Since 3.70.1-02 and will be removed */
    @Deprecated(since = "3.70.1-02", forRemoval = true)
    public void setUserUpdate(String userUpdate) {
        logger.warn("Property 'userUpdate' has been deprecated since 3.70.1-02 and will be removed in next release");
    }

    /** @deprecated Since 3.70.1-02 and will be removed */
    @Deprecated(since = "3.70.1-02", forRemoval = true)
    public void setUserInsert(String userInsert) {
        logger.warn("Property 'userInsert' has been deprecated since 3.70.1-02 and will be removed in next release");
    }

    /** @deprecated Since 3.70.1-02 and will be removed */
    @Deprecated(since = "3.70.1-02", forRemoval = true)
    public void setRoleQuery(String roleQuery) {
        logger.warn("Property 'roleQuery' has been deprecated since 3.70.1-02 and will be removed in next release");
    }

    /** @deprecated Since 3.70.1-02 and will be removed */
    @Deprecated(since = "3.70.1-02", forRemoval = true)
    public void setRoleUpdate(String roleUpdate) {
        logger.warn("Property 'roleUpdate' has been deprecated since 3.70.1-02 and will be removed in next release");
    }

    /** @deprecated Since 3.70.1-02 and will be removed */
    @Deprecated(since = "3.70.1-02", forRemoval = true)
    public void setRoleInsert(String roleInsert) {
        logger.warn("Property 'roleInsert' has been deprecated since 3.70.1-02 and will be removed in next release");
    }

}

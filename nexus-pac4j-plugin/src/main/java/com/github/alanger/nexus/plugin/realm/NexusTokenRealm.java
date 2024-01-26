package com.github.alanger.nexus.plugin.realm;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.sisu.Description;
import com.github.alanger.shiroext.realm.jdbc.JdbcRealmName;

/**
 * User Token realm.
 */
@Singleton
@Named(NexusTokenRealm.NAME)
@Description("SSO Token Realm")
public class NexusTokenRealm extends JdbcRealmName {

    public static final String NAME = "tokenRealm";

    @Inject
    public NexusTokenRealm() {
        super();
    }

}

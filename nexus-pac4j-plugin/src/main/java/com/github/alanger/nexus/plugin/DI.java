package com.github.alanger.nexus.plugin;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.servlet.ServletContext;
import org.apache.shiro.mgt.RealmSecurityManager;
import org.sonatype.nexus.common.app.ManagedLifecycle;
import org.sonatype.nexus.common.stateguard.StateGuardLifecycleSupport;
import org.sonatype.nexus.orient.DatabaseInstance;
import org.sonatype.nexus.orient.DatabaseInstanceNames;
import org.sonatype.nexus.security.realm.RealmManager;
import com.github.alanger.nexus.plugin.realm.NexusPac4jRealm;
import com.github.alanger.nexus.plugin.realm.NexusTokenRealm;
import com.github.alanger.nexus.plugin.resources.UiPac4jPluginDescriptor;
import com.google.common.base.Preconditions;

import static org.sonatype.nexus.common.app.ManagedLifecycle.Phase.SERVICES;

/**
 * Dependency injector for script environment.
 */
@Singleton
@Named
@Priority(Integer.MAX_VALUE)
@ManagedLifecycle(phase = SERVICES) // After SECURITY
public class DI extends StateGuardLifecycleSupport {

    public static final String NAME = DI.class.getCanonicalName();

    private static DI INSTANCE;

    public final boolean orient;

    public final Provider<DatabaseInstance> databaseSecurity;

    public final NexusPac4jRealm pac4jRealm;

    public final NexusTokenRealm tokenRealm;

    public final ServletContext servletContext;

    // org.sonatype.nexus.security.internal.RealmManagerImpl
    public final RealmManager realmManager;

    public final RealmSecurityManager realmSecurityManager;

    public final Init init;

    public final UiPac4jPluginDescriptor uiPac4jPluginDescriptor;

    @SuppressWarnings("java:S3010")
    @Inject
    public DI(@Named("${nexus.orient.enabled:-false}") final boolean orient,
            @Named(DatabaseInstanceNames.SECURITY) final Provider<DatabaseInstance> databaseSecurity,
            @Named(NexusPac4jRealm.NAME) final NexusPac4jRealm pac4jRealm, @Named(NexusTokenRealm.NAME) final NexusTokenRealm tokenRealm,
            final ServletContext servletContext, @Named final RealmManager realmManager, final RealmSecurityManager realmSecurityManager,
            @Named final Init init, @Named(UiPac4jPluginDescriptor.NAME) final UiPac4jPluginDescriptor uiPac4jPluginDescriptor) {
        super();

        this.orient = orient;
        this.databaseSecurity = Preconditions.checkNotNull(databaseSecurity);
        this.pac4jRealm = Preconditions.checkNotNull(pac4jRealm);
        this.tokenRealm = Preconditions.checkNotNull(tokenRealm);
        this.servletContext = Preconditions.checkNotNull(servletContext);
        this.realmManager = Preconditions.checkNotNull(realmManager);
        this.realmSecurityManager = Preconditions.checkNotNull(realmSecurityManager);
        this.init = init;
        this.uiPac4jPluginDescriptor = uiPac4jPluginDescriptor;

        if (INSTANCE == null) {
            INSTANCE = this;
            this.servletContext.setAttribute(NAME, INSTANCE);
        }

        log.trace(
                "DI orient: {}, databaseSecurity: {}, pac4jRealm: {}, tokenRealm: {}, servletContext: {}, realmManager: {}, realmSecurityManager: {}",
                orient, databaseSecurity, pac4jRealm, tokenRealm, servletContext, realmManager, realmSecurityManager);
    }

    @Override
    protected void doStart() throws Exception {
        log.trace("DI doStart");
    }

    public static DI getInstance() {
        return INSTANCE;
    }

}

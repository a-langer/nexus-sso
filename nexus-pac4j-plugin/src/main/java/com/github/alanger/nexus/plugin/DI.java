package com.github.alanger.nexus.plugin;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.servlet.ServletContext;
import javax.sql.DataSource;

import org.apache.shiro.mgt.RealmSecurityManager;
import org.sonatype.nexus.blobstore.api.BlobStoreManager;
import org.sonatype.nexus.blobstore.quota.BlobStoreQuotaService;
import org.sonatype.nexus.common.app.ManagedLifecycle;
import org.sonatype.nexus.common.stateguard.StateGuardLifecycleSupport;
import org.sonatype.nexus.datastore.api.DataStoreManager;
import org.sonatype.nexus.repository.manager.RepositoryManager;
import org.sonatype.nexus.repository.security.RepositoryPermissionChecker;
import org.sonatype.nexus.security.SecurityHelper;
import org.sonatype.nexus.security.config.SecurityConfiguration;
import org.sonatype.nexus.security.realm.RealmManager;
import org.sonatype.nexus.selector.SelectorManager;
import com.github.alanger.nexus.plugin.datastore.EncryptedString;
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

    // org.sonatype.nexus.datastore.internal.DataStoreManagerImpl
    public final DataStoreManager dataStoreManager;

    // com.zaxxer.hikari.HikariDataSource (nexus)
    public final DataSource dataSource;

    // org.sonatype.nexus.repository.manager.internal.RepositoryManagerImpl
    public final RepositoryManager repositoryManager;

    public final RepositoryPermissionChecker repositoryPermissionChecker;

    public final SecurityHelper securityHelper;

    // org.sonatype.nexus.internal.selector.SelectorManagerImpl
    public final SelectorManager selectorManager;

    // org.sonatype.nexus.repository.internal.blobstore.BlobStoreManagerImpl
    public final BlobStoreManager blobStoreManager;

    // org.sonatype.nexus.blobstore.quota.internal.BlobStoreQuotaServiceImpl
    public final BlobStoreQuotaService blobStoreQuotaService;

    // org.sonatype.nexus.internal.security.model.SecurityConfigurationImpl ("mybatis")
    public final SecurityConfiguration securityConfiguration;

    public final NexusPac4jRealm pac4jRealm;

    public final NexusTokenRealm tokenRealm;

    public final ServletContext servletContext;

    // org.sonatype.nexus.security.internal.RealmManagerImpl
    public final RealmManager realmManager;

    // org.apache.shiro.nexus.NexusWebSecurityManager
    public final RealmSecurityManager realmSecurityManager;

    public final Init init;

    public final UiPac4jPluginDescriptor uiPac4jPluginDescriptor;

    public final EncryptedString encryptedString;

    @SuppressWarnings("java:S3010")
    @Inject
    public DI(@Named final DataStoreManager dataStoreManager //
            , @Named final RepositoryManager repositoryManager //
            , @Named final RepositoryPermissionChecker repositoryPermissionChecker //
            , @Named final SecurityHelper securityHelper //
            , @Named final SelectorManager selectorManager //
            , @Named final BlobStoreManager blobStoreManager //
            , @Named final BlobStoreQuotaService blobStoreQuotaService //
            , @Named final SecurityConfiguration securityConfiguration //
            , @Named(NexusPac4jRealm.NAME) final NexusPac4jRealm pac4jRealm //
            , @Named(NexusTokenRealm.NAME) final NexusTokenRealm tokenRealm //
            , final ServletContext servletContext //
            , @Named final RealmManager realmManager //
            , final RealmSecurityManager realmSecurityManager //
            , @Named final Init init // 
            , @Named(UiPac4jPluginDescriptor.NAME) final UiPac4jPluginDescriptor uiPac4jPluginDescriptor //
            , @Named final EncryptedString encryptedString) {
        super();

        this.dataStoreManager = Preconditions.checkNotNull(dataStoreManager);
        this.dataSource = dataStoreManager.get(DataStoreManager.DEFAULT_DATASTORE_NAME)
                .orElseThrow(() -> new IllegalStateException("Missing DataStore named: " + DataStoreManager.DEFAULT_DATASTORE_NAME))
                .getDataSource();
        this.securityConfiguration = Preconditions.checkNotNull(securityConfiguration);
        this.pac4jRealm = Preconditions.checkNotNull(pac4jRealm);
        this.tokenRealm = Preconditions.checkNotNull(tokenRealm);
        this.servletContext = Preconditions.checkNotNull(servletContext);
        this.realmManager = Preconditions.checkNotNull(realmManager);
        this.realmSecurityManager = Preconditions.checkNotNull(realmSecurityManager);
        this.init = Preconditions.checkNotNull(init);
        this.uiPac4jPluginDescriptor = Preconditions.checkNotNull(uiPac4jPluginDescriptor);
        this.encryptedString = Preconditions.checkNotNull(encryptedString);
        this.repositoryManager = Preconditions.checkNotNull(repositoryManager);
        this.repositoryPermissionChecker = Preconditions.checkNotNull(repositoryPermissionChecker);
        this.securityHelper = Preconditions.checkNotNull(securityHelper);
        this.selectorManager = Preconditions.checkNotNull(selectorManager);
        this.blobStoreManager = Preconditions.checkNotNull(blobStoreManager);
        this.blobStoreQuotaService = Preconditions.checkNotNull(blobStoreQuotaService);

        if (INSTANCE == null) {
            INSTANCE = this;
            this.servletContext.setAttribute(NAME, INSTANCE);
        }

        log.trace("DI dataStoreManager: {}, dataSource: {}, repositoryManager: {}, repositoryPermissionChecker: {}, securityHelper: {}" //
                + ", selectorManager: {}, blobStoreManager: {}, blobStoreQuotaService: {}, securityConfiguration: {}, pac4jRealm: {}, tokenRealm: {}" //
                + ", servletContext: {}, realmManager: {}, realmSecurityManager: {}, init: {}, uiPac4jPluginDescriptor: {}, encryptedString: {}", //
                dataStoreManager, dataSource, repositoryManager, repositoryPermissionChecker, securityHelper, selectorManager,
                blobStoreManager, blobStoreQuotaService, securityConfiguration, pac4jRealm, tokenRealm, servletContext, realmManager,
                realmSecurityManager, init, uiPac4jPluginDescriptor, encryptedString);
    }

    @Override
    protected void doStart() throws Exception {
        log.trace("DI doStart");
    }

    public static DI getInstance() {
        return INSTANCE;
    }

}

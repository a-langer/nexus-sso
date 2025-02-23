package com.github.alanger.nexus.plugin.apikey;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Optional;
import javax.annotation.Priority;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.sql.DataSource;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.sonatype.nexus.common.app.ManagedLifecycle;
import org.sonatype.nexus.common.app.ManagedLifecycle.Phase;
import org.sonatype.nexus.common.stateguard.StateGuardLifecycleSupport;
import org.sonatype.nexus.datastore.api.DataStoreManager;
import org.sonatype.nexus.internal.security.apikey.ApiKeyServiceImpl;
import org.sonatype.nexus.kv.GlobalKeyValueStore;
import org.sonatype.nexus.security.authc.apikey.ApiKey;
import org.sonatype.nexus.security.authc.apikey.ApiKeyService;
import org.sonatype.nexus.security.config.CUser;
import org.sonatype.nexus.security.config.SecurityConfiguration;
import com.github.alanger.nexus.plugin.datastore.EncryptedString;
import com.google.common.base.Preconditions;

/**
 * Fix broken API V1 tokens.
 * Should run once before moving api keys to v2 table, see {@link org.sonatype.nexus.internal.security.apikey.upgrade.ApiKeyToSecretsTask ApiKeyToSecretsTask}.
 * 
 * <pre>
 * SELECT * FROM API_KEY
 * SELECT * FROM API_KEY_V2
 * SELECT DISTINCT PRIMARY_PRINCIPAL, * FROM API_KEY
 * DELETE API_KEY where DOMAIN = 'DockerToken'
 * </pre>
 * 
 * @since 3.75.1
 * 
 * @see org.sonatype.nexus.internal.security.apikey.upgrade.ApiKeyToSecretsTask
 * @see org.sonatype.nexus.internal.security.apikey.ApiKeyServiceImpl
 */
@Singleton
@Named
@Priority(Integer.MAX_VALUE)
@ManagedLifecycle(phase = Phase.UPGRADE) // Before TASKS
public class ApiKeySanitizer extends StateGuardLifecycleSupport {

    private final GlobalKeyValueStore kv;
    private final SecurityConfiguration securityConfiguration;
    private final EncryptedString encryptedString;

    // org.sonatype.nexus.internal.security.apikey.ApiKeyServiceImpl
    private final ApiKeyService apiKeyService;

    // org.sonatype.nexus.datastore.internal.DataStoreManagerImpl
    public final DataStoreManager dataStoreManager;

    // com.zaxxer.hikari.HikariDataSource (nexus)
    private final DataSource dataSource;

    @Inject
    public ApiKeySanitizer(final GlobalKeyValueStore kv //
            , final SecurityConfiguration securityConfiguration //
            , final ApiKeyService apiKeyService //
            , final EncryptedString encryptedString //
            , final DataStoreManager dataStoreManager) {
        this.kv = Preconditions.checkNotNull(kv);
        this.securityConfiguration = Preconditions.checkNotNull(securityConfiguration);
        this.apiKeyService = Preconditions.checkNotNull(apiKeyService);
        this.encryptedString = Preconditions.checkNotNull(encryptedString);
        this.dataStoreManager = Preconditions.checkNotNull(dataStoreManager);
        this.dataSource = dataStoreManager.get(DataStoreManager.DEFAULT_DATASTORE_NAME)
                .orElseThrow(() -> new IllegalStateException("Missing DataStore named: " + DataStoreManager.DEFAULT_DATASTORE_NAME))
                .getDataSource();
    }

    /** @see org.sonatype.nexus.internal.security.apikey.ApiKeyServiceImpl#doStart */
    @Override
    protected void doStart() throws Exception {
        boolean secretMigrationComplete = kv.getBoolean(ApiKeyServiceImpl.MIGRATION_COMPLETE).orElse(false);
        log.trace("ApiKeySanitizer doStart, secretMigrationComplete: {}", secretMigrationComplete);
        if (!secretMigrationComplete) {
            sanitizeBrokenTokens();
        }
    }

    private void sanitizeBrokenTokens() {
        String domain = ApiTokenService.DOMAIN;
        String sql = "SELECT * FROM api_key WHERE domain = '" + domain + "'";
        log.info("Get ApiKey V1 SQL: {}", sql);
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

            // Delete all Docker tokens from V1 storage, it will be created when necessary
            try (Statement stmtDeleteDockerV1 = conn.createStatement()) {
                stmtDeleteDockerV1.execute("DELETE api_key WHERE domain = 'DockerToken'");
            }

            // H2 timestamp format
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

            while (rs.next()) {
                String encPrincipal = rs.getString("PRIMARY_PRINCIPAL");
                String principal = encryptedString.decrypt(encPrincipal);
                String encToken = rs.getString("TOKEN");
                String token = encryptedString.decrypt(encToken);
                long created = rs.getTimestamp("CREATED").getTime();
                String createdStr = sdf.format(created);

                log.info("Key for principal: {} ({}), token: {}, created: {}", principal, encPrincipal,
                        log.isTraceEnabled() ? token : "***", createdStr);

                // Skip not exist user
                CUser user = securityConfiguration.getUser(principal);
                if (user == null) {
                    sql = "DELETE FROM api_key WHERE primary_principal = '" + encPrincipal + "'";
                    log.info("User is null, delete all token from V1 storage: {}", sql);
                    try (Statement stmtDeleteV1 = conn.createStatement()) {
                        stmtDeleteV1.execute(sql);
                    }
                    continue;
                }
                String realmName = "[pac4jRealm]".equals(user.getPassword()) ? "pac4jRealm" : "NexusAuthorizingRealm";
                log.trace("User id: {}, status: {}, password: {}, realm: {}", user.getId(), user.getStatus(), //
                        user.getPassword(), realmName);

                try {
                    // Detect broken token in V1 storage
                    Optional<ApiKey> apiKey = apiKeyService.getApiKeyByToken(domain, token.toCharArray());

                    if (apiKey.isPresent() && !(apiKey.get().getPrincipals().getPrimaryPrincipal() instanceof String)) {
                        log.trace("Principal class: {}, collection class: {}",
                                apiKey.get().getPrincipals().getPrimaryPrincipal().getClass(), apiKey.get().getPrincipals().getClass());
                        throw new IllegalStateException("Principal class not string: " //
                                + apiKey.get().getPrincipals().getPrimaryPrincipal().getClass());
                    }
                } catch (Exception te) {
                    try {
                        log.info("Detected broken token in V1 storage for principal: {} ({}), cause by: {}", principal, encPrincipal,
                                te.getMessage());

                        SimplePrincipalCollection principals = new SimplePrincipalCollection(principal, realmName);

                        sql = "DELETE FROM api_key WHERE domain = '" + domain + "' AND primary_principal = '" + encPrincipal + "'";
                        log.trace("  1 Delete old broken token from V1 storage: {}", sql);
                        try (Statement stmtDeleteV1 = conn.createStatement()) {
                            stmtDeleteV1.execute(sql);
                        }

                        log.trace("  2 Create new token in V1 and V2 storage for principal: {} ({})", principal, encPrincipal);
                        String newKey = new String(apiKeyService.createApiKey(domain, principals)); // Creates in V1 and V2 if NOT mirgated

                        sql = "DELETE FROM api_key_v2 WHERE domain = '" + domain + "' AND username = '" + principal + "' AND access_key = '"
                                + newKey.replaceAll("-([a-zA-Z0-9]*)-([a-zA-Z0-9]*)$", "") + "'"; // Trim key for V2 storage format
                        log.trace("  3 Delete duplicate token from V2 storage: {}", sql);
                        try (Statement stmtDeleteV2 = conn.createStatement()) {
                            stmtDeleteV2.execute(sql);
                        }

                        sql = "UPDATE api_key SET token = '" + encToken + "', created = (TIMESTAMP '" + createdStr + "') WHERE domain = '"
                                + domain + "' AND primary_principal = '" + encPrincipal + "'";
                        log.trace("  4 Set old value to new token in V1 storage: {}", sql);
                        try (Statement stmtUpdateV1 = conn.createStatement()) {
                            stmtUpdateV1.execute(sql);
                        }

                        log.info("Recreated broken token in V1 storage for principal: {} ({})", principal, encPrincipal);
                    } catch (Exception se) {
                        log.error("Error sanitize token for principal: {}", principal, se);
                    }
                }
            }
        } catch (SQLException sqle) {
            log.error("Error sanitizeBrokenTokens", sqle);
        }
    }

}

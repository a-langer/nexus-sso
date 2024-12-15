package com.github.alanger.nexus.plugin.apikey;

import com.github.alanger.nexus.plugin.DI;
import com.github.alanger.nexus.plugin.datastore.EncryptedString;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.PrincipalCollection;
import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.security.UserPrincipalsHelper;
import org.sonatype.nexus.security.authc.apikey.ApiKey;
import org.sonatype.nexus.security.authc.apikey.ApiKeyStore;
import org.sonatype.nexus.security.config.SecurityConfiguration;

/**
 * API Token service for "NuGet API Key".
 * 
 * @since 3.70.1-02
 * @see org.sonatype.nexus.internal.security.apikey.ApiKeyStoreImpl
 */
@Singleton
@Named
public class ApiTokenService extends ComponentSupport {

    public static final String DOMAIN = "NuGetApiKey";

    // org.sonatype.nexus.internal.security.apikey.ApiKeyStoreImpl
    private final ApiKeyStore apiKeyStore;

    private final EncryptedString encryptedString;

    @Inject
    public ApiTokenService(final EncryptedString encryptedString //
            , final SecurityConfiguration securityConfiguration //
            , final ApiKeyStore apiKeyStore //
            , final UserPrincipalsHelper principalsHelper) {
        this.apiKeyStore = Preconditions.checkNotNull(apiKeyStore);
        this.encryptedString = encryptedString;
        log.trace("ApiTokenService: {}", this);
    }

    public char[] createApiKey(String domain, PrincipalCollection principals) {
        ClassLoader oldTccl = Thread.currentThread().getContextClassLoader();

        char[] key = null;
        try {
            // FIX ClassNotFoundException: com.github.alanger.nexus.plugin.realm.Pac4jPrincipalName not found by org.hibernate.validator
            Thread.currentThread().setContextClassLoader(ApiTokenService.class.getClassLoader());
            key = this.apiKeyStore.createApiKey(domain, principals);
        } catch (Exception e) {
            log.trace("Error createApiKey for principal: {} and domain: {}", principals, domain, e);
        } finally {
            Thread.currentThread().setContextClassLoader(oldTccl);
        }

        return Objects.requireNonNull(key, "apiKeyStore returned null apikey for principals: " + principals);
    }

    public char[] createApiKey(PrincipalCollection principals) {
        return this.createApiKey(DOMAIN, principals);
    }

    public Optional<ApiKey> findApiKey(String domain, UsernamePasswordToken token, boolean domainAsLogin) {
        String username = token.getUsername();
        Optional<ApiKey> key = Optional.empty();

        if (username != null) {
            ClassLoader oldTccl = Thread.currentThread().getContextClassLoader();

            try {
                // FIX ClassNotFoundException: com.github.alanger.nexus.plugin.realm.Pac4jPrincipalName not found by org.hibernate.validator
                Thread.currentThread().setContextClassLoader(ApiTokenService.class.getClassLoader());

                // Domain as login for "DockerToken:XXXXX" if org.sonatype.nexus.security.authc.NexusApiKeyAuthenticationToken
                if (domainAsLogin) {
                    key = this.apiKeyStore.getApiKeyByToken(username, token.getPassword());
                    if (key.isPresent()) {
                        log.trace("findApiKey principal: {}, domain as login: {}", key.get().getPrimaryPrincipal(), username);
                        return key;
                    }
                }

                // Default domain for "Username:XXXXX" if org.apache.shiro.authc.UsernamePasswordToken
                key = this.apiKeyStore.getApiKeyByToken(domain, token.getPassword());
                if (key.isPresent() && username.equals(key.get().getPrimaryPrincipal())) {
                    log.trace("findApiKey principal: {}, default domain: {}", key.get().getPrimaryPrincipal(), domain);
                    return key;
                }
            } catch (Exception e) {
                log.trace("Error findApiKey for principal: {}", username, e);
            } finally {
                Thread.currentThread().setContextClassLoader(oldTccl);
            }
        }

        return key;
    }

    public Optional<ApiKey> findApiKey(UsernamePasswordToken token, boolean domainAsLogin) {
        return findApiKey(DOMAIN, token, domainAsLogin);
    }

    /** For compatibility with 3.70.1-java11-ubi-BETA-3 */
    public Optional<ApiKey> findApiKey(UsernamePasswordToken token) {
        return findApiKey(DOMAIN, token, false);
    }

    public Optional<ApiKey> getApiKey(String domain, PrincipalCollection principals) {

        ClassLoader oldTccl = Thread.currentThread().getContextClassLoader();
        Optional<ApiKey> apiKey = Optional.empty();

        try {
            // FIX ClassNotFoundException: com.github.alanger.nexus.plugin.realm.Pac4jPrincipalName not found by org.hibernate.validator
            Thread.currentThread().setContextClassLoader(ApiTokenService.class.getClassLoader());
            apiKey = this.apiKeyStore.getApiKey(domain, principals);
        } catch (Exception e) {
            log.trace("Error getApiKey for principal: " + principals.getPrimaryPrincipal() + " and domain: " + domain, e);
        } finally {
            Thread.currentThread().setContextClassLoader(oldTccl);
        }

        // Get via SQL if not present
        if (!apiKey.isPresent()) {
            apiKey = getApiKeyFromDatabase(domain, principals);
        }

        return apiKey;
    }

    public Optional<ApiKey> getApiKey(PrincipalCollection principals) {
        return this.getApiKey(DOMAIN, principals);
    }

    /**
     * Delete ApiKey by primary principal
     * 
     * @since buji-pac4j 5.0.0
     * @since Nexus 3.70.0
     * 
     * @see com.github.alanger.nexus.plugin.realm.Pac4jPrincipalName#equals(Object)
     */
    public void deleteApiKey(String domain, PrincipalCollection principals) {

        ClassLoader oldTccl = Thread.currentThread().getContextClassLoader();
        try {
            // FIX ClassNotFoundException: com.github.alanger.nexus.plugin.realm.Pac4jPrincipalName not found by org.hibernate.validator
            Thread.currentThread().setContextClassLoader(ApiTokenService.class.getClassLoader());

            // May not return an error even if the deletion failed
            this.apiKeyStore.deleteApiKey(domain, principals);
        } catch (Exception e) {
            log.error("Error delete apiKey from store by principal {}: {}", principals.getPrimaryPrincipal(), e.getMessage());
            log.trace("Invalid apiKey will be deleted from database");
        } finally {
            Thread.currentThread().setContextClassLoader(oldTccl);
            // Delete via SQL anyway
            deleteApiKeyFromDatabase(domain, principals);
        }
    }

    public void deleteApiKey(PrincipalCollection principals) {
        this.deleteApiKey(DOMAIN, principals);
    }

    /**
     * SQL: Select first API key from database
     * 
     * @since 3.70.1-02
     */
    public Optional<ApiKey> getApiKeyFromDatabase(String domain, PrincipalCollection principals) {
        String encPrincipal = encryptedString.encrypt(principals.getPrimaryPrincipal().toString());
        String sql = "SELECT token FROM api_key WHERE domain = '" + domain + "' AND primary_principal = '" + encPrincipal + "'";
        log.trace("Get ApiKey SQL: {}", sql);
        try (Connection conn = DI.getInstance().dataSource.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql);) {
            while (rs.next()) {
                String token = encryptedString.decrypt(rs.getString(1));
                return Optional.of(this.apiKeyStore.newApiKey(domain, principals, token.toCharArray()));
            }
        } catch (SQLException e) {
            log.error("Error select apiKey from DB by principal {} and domain {}: {}", //
                    principals.getPrimaryPrincipal(), domain, e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * SQL: Force deleting API key from database
     * 
     * @since 3.70.1-02
     */
    public void deleteApiKeyFromDatabase(String domain, PrincipalCollection principals) {
        String encPrincipal = encryptedString.encrypt(principals.getPrimaryPrincipal().toString());
        String sql = "DELETE FROM api_key WHERE domain = '" + domain + "' AND primary_principal = '" + encPrincipal + "'";
        log.trace("Delete ApiKey SQL: {}", sql);
        try (Connection conn = DI.getInstance().dataSource.getConnection(); Statement stmt = conn.createStatement();) {
            stmt.execute(sql);
        } catch (SQLException e) {
            log.error("Error delete apiKey from DB by principal {} and domain {}: {}", //
                    principals.getPrimaryPrincipal(), domain, e.getMessage());
        }
    }

    // Utils

    public static final long ONE_DAY_IN_MILLS = 1000L * 60L * 60L * 24L;

    public static boolean isExpired(ApiKey tokenRecord, int expirationDays) {
        if (isExpirationEnabled(expirationDays) && tokenRecord != null) {
            return isTokenExpired(tokenRecord, expirationDays);
        }
        return false;
    }

    public static boolean isExpirationEnabled(int expirationDays) {
        return expirationDays > 0;
    }

    public static boolean isTokenExpired(ApiKey userTokenRecord, int expirationDays) {
        long expiration = getExpirationTimeStamp(userTokenRecord, expirationDays);
        return (Instant.now().toEpochMilli() > expiration);
    }

    public static long getExpirationTimeStamp(ApiKey userToken, int expirationDays) {
        return ONE_DAY_IN_MILLS * expirationDays + userToken.getCreated().toInstant().toEpochMilli();
    }

}

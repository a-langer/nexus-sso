package com.github.alanger.nexus.plugin.apikey;

import com.github.alanger.nexus.plugin.datastore.EncryptedString;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.PrincipalCollection;
import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.security.authc.apikey.ApiKey;
import org.sonatype.nexus.security.authc.apikey.ApiKeyService;

import org.sonatype.nexus.security.config.SecurityConfiguration;

/**
 * API Token service for "NuGet API Key".
 * 
 * @since 3.70.1-02
 * 
 * @see org.sonatype.nexus.internal.security.apikey.ApiKeyServiceImpl
 * @see org.sonatype.nexus.internal.security.apikey.store.ApiKeyStoreV2Impl
 */
@Singleton
@Named
public class ApiTokenService extends ComponentSupport {

    public static final String DOMAIN = "NuGetApiKey";

    /**
     * @since 3.75.1-01 used {@link org.sonatype.nexus.internal.security.apikey.ApiKeyServiceImpl org.sonatype.nexus.security.authc.apikey.ApiKeyService}
     * instead of {@link org.sonatype.nexus.internal.security.apikey.ApiKeyStoreImpl org.sonatype.nexus.security.authc.apikey.ApiKeyStore} which was moved to
     * internal package {@link org.sonatype.nexus.internal.security.apikey.store.ApiKeyStoreV2Impl org.sonatype.nexus.internal.security.apikey.store.ApiKeyStore}.
     */
    private final ApiKeyService apiKeyService;

    @Inject
    public ApiTokenService(final EncryptedString encryptedString //
            , final SecurityConfiguration securityConfiguration //
            , final ApiKeyService apiKeyService) {
        this.apiKeyService = Preconditions.checkNotNull(apiKeyService);
        log.trace("ApiTokenService: {}, apiKeyService: {}", this, apiKeyService);
    }

    public char[] createApiKey(String domain, PrincipalCollection principals) {
        char[] key = this.apiKeyService.createApiKey(domain, principals);
        return Objects.requireNonNull(key, "apiKeyStore returned null apikey for principals: " + principals);
    }

    public char[] createApiKey(PrincipalCollection principals) {
        return this.createApiKey(DOMAIN, principals);
    }

    public Optional<ApiKey> findApiKey(String domain, UsernamePasswordToken token, boolean domainAsLogin) {
        String username = token.getUsername();
        Optional<ApiKey> key = Optional.empty();

        if (username != null) {
            // Domain as login for "DockerToken:XXXXX" if org.sonatype.nexus.security.authc.NexusApiKeyAuthenticationToken
            if (domainAsLogin) {
                key = this.apiKeyService.getApiKeyByToken(username, token.getPassword());
                if (key.isPresent()) {
                    log.trace("findApiKey principal: {}, domain as login: {}", key.get().getPrimaryPrincipal(), username);
                    return key;
                }
            }

            // Default domain for "Username:XXXXX" if org.apache.shiro.authc.UsernamePasswordToken
            key = this.apiKeyService.getApiKeyByToken(domain, token.getPassword());
            if (key.isPresent() && username.equals(key.get().getPrimaryPrincipal())) {
                log.trace("findApiKey principal: {}, default domain: {}", key.get().getPrimaryPrincipal(), domain);
                return key;
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
        return this.apiKeyService.getApiKey(domain, principals);
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
        // May not return an error even if the deletion failed
        this.apiKeyService.deleteApiKey(domain, principals);
    }

    public void deleteApiKey(PrincipalCollection principals) {
        this.deleteApiKey(DOMAIN, principals);
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

package com.github.alanger.nexus.plugin.apikey;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.time.Instant;
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
 * @see org.sonatype.nexus.internal.security.apikey.ApiKeyStoreImpl
 */
@Singleton
@Named
public class ApiTokenService extends ComponentSupport {

    public static final String DOMAIN = "NuGetApiKey";

    // org.sonatype.nexus.internal.security.apikey.ApiKeyStoreImpl
    private final ApiKeyStore apiKeyStore;

    @Inject
    public ApiTokenService(final SecurityConfiguration securityConfiguration //
            , final ApiKeyStore apiKeyStore //
            , final UserPrincipalsHelper principalsHelper) {
        this.apiKeyStore = Preconditions.checkNotNull(apiKeyStore);
        log.trace("ApiTokenServiceImpl: {}", this);
    }


    public char[] createApiKey(String domain, PrincipalCollection principals) {
        ClassLoader oldTccl = Thread.currentThread().getContextClassLoader();

        try {
            // FIX ClassNotFoundException: com.github.alanger.nexus.plugin.realm.Pac4jPrincipalName not found by org.hibernate.validator
            Thread.currentThread().setContextClassLoader(ApiTokenService.class.getClassLoader());
            return this.apiKeyStore.createApiKey(domain, principals);
        } catch (Exception e) {
            log.trace("Error createApiKey for principal: {} and domain: {}", principals, domain, e);
        } finally {
            Thread.currentThread().setContextClassLoader(oldTccl);
        }

        return new char[0];
    }

    public char[] createApiKey(PrincipalCollection principals) {
        return this.createApiKey(DOMAIN, principals);
    }

    public Optional<ApiKey> findApiKey(UsernamePasswordToken token) {
        String username = token.getUsername();

        if (username != null) {
            ClassLoader oldTccl = Thread.currentThread().getContextClassLoader();

            try {
                // FIX ClassNotFoundException: com.github.alanger.nexus.plugin.realm.Pac4jPrincipalName not found by org.hibernate.validator
                Thread.currentThread().setContextClassLoader(ApiTokenService.class.getClassLoader());

                // "DockerToken:XXXXX" if org.sonatype.nexus.security.authc.NexusApiKeyAuthenticationToken
                Optional<ApiKey> key = this.apiKeyStore.getApiKeyByToken(username, token.getPassword());
                if (key.isPresent()) {
                    log.trace("findApiKey principal {}, domain {}", key.get().getPrimaryPrincipal(), username);
                    return key;
                }

                // "Username:XXXXX" if org.apache.shiro.authc.UsernamePasswordToken
                key = this.apiKeyStore.getApiKeyByToken(DOMAIN, token.getPassword());
                if (key.isPresent() && username.equals(key.get().getPrimaryPrincipal())) {
                    log.trace("findApiKey principal {}, domain {}", key.get().getPrimaryPrincipal(), DOMAIN);
                    return key;
                }
            } catch (Exception e) {
                log.trace("Error findApiKey for principal: {}", username, e);
            } finally {
                Thread.currentThread().setContextClassLoader(oldTccl);
            }
        }

        return Optional.empty();
    }

    public Optional<ApiKey> getApiKey(String domain, PrincipalCollection principals) {
        ClassLoader oldTccl = Thread.currentThread().getContextClassLoader();

        try {
            // FIX ClassNotFoundException: com.github.alanger.nexus.plugin.realm.Pac4jPrincipalName not found by org.hibernate.validator
            Thread.currentThread().setContextClassLoader(ApiTokenService.class.getClassLoader());
            return this.apiKeyStore.getApiKey(domain, principals);
        } catch (Exception e) {
            log.trace("Error getApiKey for principal: " + principals.getPrimaryPrincipal() + " and domain: " + domain, e);
        } finally {
            Thread.currentThread().setContextClassLoader(oldTccl);
        }

        return Optional.empty();
    }

    public Optional<ApiKey> getApiKey(PrincipalCollection principals) {
        return this.getApiKey(DOMAIN, principals);
    }

    /**
     * Delete ApiKey by primary principal
     * 
     * @since buji-pac4j:5.0.0
     * @since Nexus:3.70.0
     * 
     * @see com.github.alanger.nexus.plugin.realm.Pac4jPrincipalName#equals(Object)
     */
    public void deleteApiKey(String domain, PrincipalCollection principals) {

        ClassLoader oldTccl = Thread.currentThread().getContextClassLoader();
        try {
            // FIX ClassNotFoundException: com.github.alanger.nexus.plugin.realm.Pac4jPrincipalName not found by org.hibernate.validator
            Thread.currentThread().setContextClassLoader(ApiTokenService.class.getClassLoader());

            // May not delete without error
            this.apiKeyStore.deleteApiKey(domain, principals);
        } catch (Exception e) {
            log.error("Error delete apiKey from store by principal {}: {}", principals.getPrimaryPrincipal(), e.getMessage());
            log.trace("Invalid apiKey will be deleted from database");
            // this.apiKeyStore.deleteApiKeys(principals); // TODO Remove all API tokens
        } finally {
            Thread.currentThread().setContextClassLoader(oldTccl);
        }
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

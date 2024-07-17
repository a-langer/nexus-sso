package com.github.alanger.nexus.plugin.rest;

import com.google.common.base.Preconditions;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.OCommandSQL;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.subject.PrincipalCollection;
import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.common.wonderland.AuthTicketService;
import org.sonatype.nexus.orient.DatabaseInstance;
import org.sonatype.nexus.orient.DatabaseInstanceNames;
import org.sonatype.nexus.rest.NotCacheable;
import org.sonatype.nexus.rest.Resource;
import org.sonatype.nexus.rest.WebApplicationMessageException;
import org.sonatype.nexus.security.SecurityHelper;
import org.sonatype.nexus.security.authc.apikey.ApiKey;
import org.sonatype.nexus.security.authc.apikey.ApiKeyStore;
import org.sonatype.nexus.validation.Validate;

import static org.sonatype.nexus.orient.transaction.OrientTransactional.inTxRetry;

/**
 * Nuget API key implementation for SSO.
 * <p>
 * GET: /service/rest/internal/nuget-api-key?authToken=XXXXX
 * 
 * @see org.sonatype.nexus.internal.security.apikey.orient.OrientApiKeyStore
 * @see org.sonatype.nexus.internal.security.apikey.orient.OrientApiKeyEntityAdapter
 */
@Named
@Singleton
@Path(NugetApiKeyResource.RESOURCE_URI)
@Produces({ "application/json" })
public class NugetApiKeyResource extends ComponentSupport implements Resource {

    public static final String RESOURCE_URI = "/internal/nuget-api-key";

    public static final String DOMAIN = "NuGetApiKey";

    // org.sonatype.nexus.internal.security.apikey.orient.OrientApiKeyStore
    private final Provider<ApiKeyStore> apiKeyStore;

    private final AuthTicketService authTicketService;

    private final SecurityHelper securityHelper;

    private final Provider<DatabaseInstance> databaseInstance;

    @Inject
    public NugetApiKeyResource(Provider<ApiKeyStore> apiKeyStore, AuthTicketService authTicketService,
            SecurityHelper securityHelper,
            @Named(DatabaseInstanceNames.SECURITY) final Provider<DatabaseInstance> databaseInstance) {
        super();
        this.apiKeyStore = Preconditions.checkNotNull(apiKeyStore);
        this.authTicketService = Preconditions.checkNotNull(authTicketService);
        this.securityHelper = Preconditions.checkNotNull(securityHelper);
        this.databaseInstance = Preconditions.checkNotNull(databaseInstance);

        log.trace("apiKeyStore: {}, authTicketService: {}, securityHelper: {}, databaseInstance: {}",
                apiKeyStore, authTicketService, securityHelper, databaseInstance);
    }

    @GET
    @RequiresPermissions({ "nexus:apikey:read" })
    @Validate
    @NotCacheable
    public NugetApiKeyXO readKey(@NotNull @Valid @QueryParam("authToken") String base64AuthToken) {
        validateAuthToken(base64AuthToken);
        PrincipalCollection principals = this.securityHelper.subject().getPrincipals();

        // Read by principals
        char[] apiKey = null;
        try {
            apiKey = this.apiKeyStore.get().getApiKey(DOMAIN, principals).map(ApiKey::getApiKey).orElse(null);
        } catch (Exception e) {
            log.trace("Error read apiKey from KeyStore by principal {}: {}", principals.getPrimaryPrincipal(), e);
            deleteApiKey(principals);
        }

        // Read by primary principal or create a new
        if (apiKey == null) {
            log.trace("Find apiKey by primary principal or create a new");
            apiKey = finfApiKey(principals).map(ApiKey::getApiKey)
                    .orElseGet(() -> this.apiKeyStore.get().createApiKey(DOMAIN, principals));
        }
        log.trace("Read apiKey for principal {} = {}", principals.getPrimaryPrincipal(), apiKey != null ? "***" : null);

        return new NugetApiKeyXO(apiKey);
    }

    @DELETE
    @RequiresAuthentication
    @RequiresPermissions({ "nexus:apikey:delete" })
    @Validate
    public NugetApiKeyXO resetKey(@NotNull @Valid @QueryParam("authToken") String base64AuthToken) {
        validateAuthToken(base64AuthToken);
        PrincipalCollection principals = this.securityHelper.subject().getPrincipals();

        // Delete by principals
        deleteApiKey(principals);

        char[] apiKey = this.apiKeyStore.get().createApiKey(DOMAIN, principals);
        log.trace("Reset apiKey for principal {} = {}", principals.getPrimaryPrincipal(),
                apiKey != null ? "***" : null);

        return new NugetApiKeyXO(apiKey);
    }

    /**
     * Find ApiKey by primary principal
     * <p>
     * Since version buji-pac4j:5.0.0 {@link org.pac4j.core.profile.UserProfile } is
     * interface and deserialization is not possible because it was previously
     * serialized as a class. Trying to read the API key results in an error:
     * 
     * <pre>
     * java.io.InvalidClassException: org.pac4j.core.profile.UserProfile; local class incompatible
     * </pre>
     * 
     * For compatibility with old API keys, operations are performed in the database
     * via SQL.
     * 
     * @since buji-pac4j:5.0.0
     * @since Nexus:3.70.0
     */
    private Optional<ApiKey> finfApiKey(PrincipalCollection principals) {

        // Since version buji-pac4j:5.0.0 is not possible
        // @formatter:off
        // for (ApiKey ak : apiKeyStore.get().browse(DOMAIN)) {
        //     if (principals.getPrimaryPrincipal().toString()
        //             .equals(ak.getPrincipals().getPrimaryPrincipal().toString())) {
        //         return Optional.of(ak);
        //     }
        // }
        // @formatter:on

        String apiKey = null;
        try (ODatabaseDocumentTx db = databaseInstance.get().acquire()) {
            List<ODocument> result = db.query(
                    new OSQLSynchQuery<ODocument>("select * from api_key where domain = ? and primary_principal = ?"),
                    DOMAIN, principals.getPrimaryPrincipal().toString() //
            );

            if (result != null && !result.isEmpty()) {
                apiKey = result.get(0).field("api_key");
            }
        }

        if (apiKey != null) {
            return Optional.of(this.apiKeyStore.get().newApiKey(DOMAIN, principals, apiKey.toCharArray()));
        }

        return Optional.empty();
    }

    /**
     * Delete ApiKey by primary principal
     * 
     * @since buji-pac4j:5.0.0
     * @since Nexus:3.70.0
     */
    private void deleteApiKey(PrincipalCollection principals) {
        try {
            // May not delete without error
            this.apiKeyStore.get().deleteApiKey(DOMAIN, principals);
        } catch (Exception e) {
            log.trace("Error delete apiKey from KeyStore by principal {}: {}", principals.getPrimaryPrincipal(), e);
            log.trace("Invalid apiKey will be deleted from database");
        } finally {
            inTxRetry(databaseInstance).run(db -> {
                db.command(new OCommandSQL("delete from api_key where domain = ? and primary_principal = ?"))
                        .execute(DOMAIN, principals.getPrimaryPrincipal().toString());
            });
        }
    }

    private void validateAuthToken(String base64AuthToken) {
        String authToken = new String(Base64.getDecoder().decode(base64AuthToken), StandardCharsets.UTF_8);
        if (!this.authTicketService.redeemTicket(authToken)) {
            throw new WebApplicationMessageException(Response.Status.FORBIDDEN, "Invalid authentication ticket");
        }
    }

}

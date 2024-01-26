package com.github.alanger.nexus.plugin.rest;

import com.google.common.base.Preconditions;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
import org.sonatype.nexus.common.entity.AbstractEntity;
import org.sonatype.nexus.common.wonderland.AuthTicketService;
import org.sonatype.nexus.orient.DatabaseInstance;
import org.sonatype.nexus.orient.DatabaseInstanceNames;
import org.sonatype.nexus.orient.entity.EntityAdapter;
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
 */
@Named
@Singleton
@Path(NugetApiKeyResource.RESOURCE_URI)
@Produces({"application/json"})
public class NugetApiKeyResource extends ComponentSupport implements Resource {

    public static final String RESOURCE_URI = "/internal/nuget-api-key";

    public static final String DAMAIN = "NuGetApiKey";

    private final Provider<ApiKeyStore> apiKeyStore;

    private final AuthTicketService authTicketService;

    private final SecurityHelper securityHelper;

    // org.sonatype.nexus.internal.security.apikey.orient.OrientApiKeyEntityAdapter<org.sonatype.nexus.internal.security.apikey.orient.OrientApiKey>
    private final EntityAdapter<AbstractEntity> entityAdapter;

    private final Provider<DatabaseInstance> databaseInstance;

    @Inject
    public NugetApiKeyResource(Provider<ApiKeyStore> apiKeyStore, AuthTicketService authTicketService, SecurityHelper securityHelper,
            EntityAdapter<AbstractEntity> entityAdapter,
            @Named(DatabaseInstanceNames.SECURITY) final Provider<DatabaseInstance> databaseInstance) {
        super();
        this.apiKeyStore = Preconditions.checkNotNull(apiKeyStore);
        this.authTicketService = Preconditions.checkNotNull(authTicketService);
        this.securityHelper = Preconditions.checkNotNull(securityHelper);
        this.entityAdapter = Preconditions.checkNotNull(entityAdapter);
        this.databaseInstance = Preconditions.checkNotNull(databaseInstance);

        log.trace("NugetApiKeyResource apiKeyStore: {}, authTicketService: {}, securityHelper: {}, entityAdapter: {}, databaseInstance: {}",
                apiKeyStore, authTicketService, securityHelper, entityAdapter, databaseInstance);
    }

    @GET
    @RequiresPermissions({"nexus:apikey:read"})
    @Validate
    @NotCacheable
    public NugetApiKeyXO readKey(@NotNull @Valid @QueryParam("authToken") String base64AuthToken) {
        validateAuthToken(base64AuthToken);
        PrincipalCollection principals = this.securityHelper.subject().getPrincipals();

        // Read by principals
        char[] apiKey = this.apiKeyStore.get().getApiKey(DAMAIN, principals).map(ApiKey::getApiKey).orElse(null);

        // Read by primary principal or create a new
        if (apiKey == null) {
            apiKey = finfApiKey(principals).map(ApiKey::getApiKey).orElseGet(() -> this.apiKeyStore.get().createApiKey(DAMAIN, principals));
        }
        log.trace("Read apiKey for principal {} = {}", principals.getPrimaryPrincipal(), apiKey != null ? "***" : null);

        return new NugetApiKeyXO(apiKey);
    }

    @DELETE
    @RequiresAuthentication
    @RequiresPermissions({"nexus:apikey:delete"})
    @Validate
    public NugetApiKeyXO resetKey(@NotNull @Valid @QueryParam("authToken") String base64AuthToken) {
        validateAuthToken(base64AuthToken);
        PrincipalCollection principals = this.securityHelper.subject().getPrincipals();

        // Delete by principals
        this.apiKeyStore.get().deleteApiKey(DAMAIN, principals);

        // Delete by entity, see org.sonatype.nexus.internal.security.apikey.orient.OrientApiKeyStore#deleteApiKey
        inTxRetry(databaseInstance).run(db -> {
            entityAdapter.deleteEntity(db, (AbstractEntity) finfApiKey(principals).orElse(null));
        });

        char[] apiKey = this.apiKeyStore.get().createApiKey(DAMAIN, principals);
        log.trace("Reset apiKey for principal {} = {}", principals.getPrimaryPrincipal(), apiKey != null ? "***" : null);

        return new NugetApiKeyXO(apiKey);
    }

    // ApiKey by primary principal
    private Optional<ApiKey> finfApiKey(PrincipalCollection principals) {
        for (ApiKey ak : apiKeyStore.get().browse(DAMAIN)) {
            if (principals.getPrimaryPrincipal().toString().equals(ak.getPrincipals().getPrimaryPrincipal().toString())) {
                return Optional.of(ak);
            }
        }
        return Optional.empty();
    }

    private void validateAuthToken(String base64AuthToken) {
        String authToken = new String(Base64.getDecoder().decode(base64AuthToken), StandardCharsets.UTF_8);
        if (!this.authTicketService.redeemTicket(authToken)) {
            throw new WebApplicationMessageException(Response.Status.FORBIDDEN, "Invalid authentication ticket");
        }

    }
}

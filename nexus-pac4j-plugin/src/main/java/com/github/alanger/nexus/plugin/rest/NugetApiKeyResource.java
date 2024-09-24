package com.github.alanger.nexus.plugin.rest;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.google.common.base.Preconditions;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.inject.Inject;
import javax.inject.Named;
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
import org.sonatype.nexus.rest.NotCacheable;
import org.sonatype.nexus.rest.Resource;
import org.sonatype.nexus.rest.WebApplicationMessageException;
import org.sonatype.nexus.security.SecurityHelper;
import org.sonatype.nexus.security.authc.apikey.ApiKey;
import org.sonatype.nexus.validation.Validate;
import com.github.alanger.nexus.plugin.apikey.ApiTokenService;

/**
 * Nuget API key implementation for SSO.
 * <p>
 * GET: /service/rest/internal/nuget-api-key?authToken=XXXXX
 * 
 * @see com.github.alanger.nexus.plugin.apikey.ApiTokenService
 */
@Named
@Singleton
@Path(NugetApiKeyResource.RESOURCE_URI)
@Produces({"application/json"})
public class NugetApiKeyResource extends ComponentSupport implements Resource {

    public static final String RESOURCE_URI = "/internal/nuget-api-key";

    public static final String DOMAIN = ApiTokenService.DOMAIN;

    private final ApiTokenService apiTokenService;

    private final AuthTicketService authTicketService;

    private final SecurityHelper securityHelper;

    @Inject
    public NugetApiKeyResource(final ApiTokenService apiTokenService //
            , final AuthTicketService authTicketService //
            , final SecurityHelper securityHelper) {
        super();
        this.apiTokenService = Preconditions.checkNotNull(apiTokenService);
        this.authTicketService = Preconditions.checkNotNull(authTicketService);
        this.securityHelper = Preconditions.checkNotNull(securityHelper);

        log.trace("NugetApiKeyResource apiTokenService: {}, authTicketService: {}, securityHelper: {}", //
                apiTokenService, authTicketService, securityHelper);
    }

    @GET
    @ExceptionMetered
    @RequiresPermissions({"nexus:apikey:read"})
    @Validate
    @NotCacheable
    public NugetApiKeyXO readKey(@NotNull @Valid @QueryParam("authToken") String base64AuthToken) {
        validateAuthToken(base64AuthToken);
        PrincipalCollection principals = this.securityHelper.subject().getPrincipals();

        // Read by principals
        char[] apiKey = null;
        try {
            apiKey = apiTokenService.getApiKey(DOMAIN, principals).map(ApiKey::getApiKey).orElse(null);
        } catch (Exception e) {
            log.trace("Error read apiKey from KeyStore by principal {}: {}", principals.getPrimaryPrincipal(), e);
            apiTokenService.deleteApiKey(DOMAIN, principals);
        }

        // Read by primary principal or create a new
        if (apiKey == null) {
            log.trace("Find apiKey by primary principal or create a new");
            apiKey = apiTokenService.getApiKey(DOMAIN, principals).map(ApiKey::getApiKey)
                    .orElseGet(() -> apiTokenService.createApiKey(DOMAIN, principals));
        }
        log.trace("Read apiKey for principal {} = {}", principals.getPrimaryPrincipal(),
                (apiKey != null && apiKey.length > 0) ? "***" : null);

        return new NugetApiKeyXO(apiKey);
    }

    @DELETE
    @ExceptionMetered
    @RequiresAuthentication
    @RequiresPermissions({"nexus:apikey:delete"})
    @Validate
    public NugetApiKeyXO resetKey(@NotNull @Valid @QueryParam("authToken") String base64AuthToken) {
        validateAuthToken(base64AuthToken);
        PrincipalCollection principals = this.securityHelper.subject().getPrincipals();

        // Delete by principals
        apiTokenService.deleteApiKey(DOMAIN, principals);

        char[] apiKey = apiTokenService.createApiKey(DOMAIN, principals);
        log.trace("Reset apiKey for principal {} = {}", principals.getPrimaryPrincipal(),
                (apiKey != null && apiKey.length > 0) ? "***" : null);

        return new NugetApiKeyXO(apiKey);
    }

    private void validateAuthToken(String base64AuthToken) {
        String authToken = new String(Base64.getDecoder().decode(base64AuthToken), StandardCharsets.UTF_8);
        if (!this.authTicketService.redeemTicket(authToken)) {
            throw new WebApplicationMessageException(Response.Status.FORBIDDEN, "Invalid authentication ticket");
        }
    }

}

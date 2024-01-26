package com.github.alanger.nexus.plugin;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.rest.NotCacheable;
import org.sonatype.nexus.rest.Resource;
import java.sql.Timestamp;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

/**
 * Reload endpoint "/service/rest/rewrite-status".
 * <p>
 * docker compose exec -- nexus curl -sSfkI http://localhost:8081/rewrite-status/?conf=etc/sso/config/urlrewrite.xml
 */
@Named
@Singleton
@Path("/rewrite-status")
@Produces({"application/json"})
public class InitResource extends ComponentSupport implements Resource {

    private final Init init;

    @Inject
    public InitResource(@Named Init init) {
        super();
        this.init = init;
        log.trace("InitResource Init object: {}", init);
    }

    // Parameter "conf" for compatibility with UrlRewriteFilter
    @GET
    @NotCacheable
    public InitResourceXO reload(@QueryParam("conf") String conf) throws Exception {
        String message = "Reload disabled";
        if (init.isTraceEnabled()) {
            message = "Reloaded " + new Timestamp(System.currentTimeMillis());
            init.doStart();
        }
        return new InitResourceXO(message);
    }

    @GET
    @NotCacheable
    public InitResourceXO reload() throws Exception {
        return reload(null);
    }
}

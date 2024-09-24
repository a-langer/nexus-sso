package com.github.alanger.nexus.plugin.datastore;

import java.io.File;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.nexus.common.app.ApplicationDirectories;
import org.sonatype.nexus.common.app.ManagedLifecycle;
import org.sonatype.nexus.common.event.EventAware;
import org.sonatype.nexus.common.node.NodeAccess;
import org.sonatype.nexus.common.stateguard.Guarded;
import org.sonatype.nexus.common.stateguard.StateGuardLifecycleSupport;

import org.h2.tools.Server;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sonatype.nexus.common.app.ManagedLifecycle.Phase.TASKS;
import static org.sonatype.nexus.common.stateguard.StateGuardLifecycleSupport.State.STARTED;

/**
 * Enable H2 TCP console.
 * <p>
 * 
 * Use internal web console instance:
 * 
 * <pre>
 * nexus.h2.httpListenerEnabled=true
 * nexus.h2.httpListenerPort=2480
 * docker compose up
 * Open http://localhost:2480 -> jdbc:h2:/nexus-data/db/nexus -> emplty login/pass
 * </pre>
 * 
 * Or different web console instance:
 * 
 * <pre>
 * nexus.h2.tcpListenerEnabled=true
 * nexus.h2.tcpListenerPort=2424
 * docker compose --profile debug up
 * Open http://localhost:2480 -> jdbc:h2:tcp://nexus:2424/nexus -> emplty login/pass
 * </pre>
 * 
 * Example SQL:
 * 
 * <pre>{@code
 * SELECT * FROM EMAIL_CONFIGURATION
 * SELECT * FROM QRTZ_CRON_TRIGGERS
 * SELECT * FROM QRTZ_TRIGGERS
 * SELECT * FROM SECURITY_USER
 * SELECT * FROM ROLE
 * SELECT * FROM API_KEY
 * SELECT * FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'API_KEY'
 * SELECT * FROM INFORMATION_SCHEMA.COLUMNS where TABLE_NAME = 'API_KEY'
 * }</pre>
 * 
 * TODO Not working, see {@link org.h2.util.SourceCompiler}:
 * <pre>{@code
 * CREATE ALIAS NX_DECRYPT AS '
 * import com.github.alanger.nexus.plugin.DI;
 * @CODE
 * String nxDecrypt(String value) throws Exception {
 *     return DI.getInstance().encryptedString.decrypt(value);
 * }
 * ';
 * DROP ALIAS "NX_DECRYPT" IF EXISTS;
 * }</pre>
 *
 * @since 3.70.1-02
 * @see http://www.h2database.com/html/tutorial.html#spring
 * @see http://h2database.com/html/tutorial.html#command_line_tools
 * @see https://h2database.com/html/features.html#user_defined_functions
 * @see org.sonatype.nexus.datastore.mybatis.internal.H2WebConsole
 */
@Named
@Singleton
@ManagedLifecycle(phase = TASKS)
public class H2TcpConsole extends StateGuardLifecycleSupport implements EventAware, EventAware.Asynchronous {
    private final File databasesDir;

    private final boolean tcpListenerEnabled;

    private final int tcpListenerPort;

    private Server h2Server;

    @Inject
    public H2TcpConsole(final ApplicationDirectories applicationDirectories, //
            @Named("${nexus.sso.h2.tcpListenerEnabled:-false}") final boolean tcpListenerEnabled, //
            @Named("${nexus.sso.h2.tcpListenerPort:-2424}") final int tcpListenerPort, //
            final NodeAccess nodeAccess) {
        checkNotNull(applicationDirectories);
        this.tcpListenerEnabled = tcpListenerEnabled;
        this.tcpListenerPort = tcpListenerPort;
        databasesDir = applicationDirectories.getWorkDirectory("db");
    }

    @Override
    protected void doStart() throws Exception {
        if (tcpListenerEnabled) {
            h2Server = Server.createTcpServer("-tcpPort", String.valueOf(tcpListenerPort), "-tcpAllowOthers", "-ifExists", "-baseDir",
                    databasesDir.toString()).start();
            log.info("Activated");
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (h2Server != null) {
            // instance shutdown
            h2Server.shutdown();
            h2Server = null;
        }
    }

    @Guarded(by = STARTED)
    public Server getH2Server() {
        return h2Server;
    }
}

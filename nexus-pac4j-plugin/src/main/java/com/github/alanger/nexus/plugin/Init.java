package com.github.alanger.nexus.plugin;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.servlet.ServletContext;

import org.sonatype.nexus.common.app.ManagedLifecycle;
import org.sonatype.nexus.common.stateguard.StateGuardLifecycleSupport;
import com.google.common.base.Preconditions;
import java.io.File;
import java.net.URL;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.tools.RootLoader;
import org.opensaml.xmlsec.signature.support.SignatureValidator;
import groovy.lang.GroovyClassLoader;
import net.shibboleth.utilities.java.support.logic.ConstraintViolationException;
import java.util.Arrays;
import java.util.stream.Collectors;
import static org.sonatype.nexus.common.app.ManagedLifecycle.Phase.TASKS;

/**
 * Initialization of SSO scripts and configs.
 */
@Singleton
@Named
@ManagedLifecycle(phase = TASKS)
public class Init extends StateGuardLifecycleSupport {

    public static final String CONFIG_PATH = "configPath";
    public static final String CONFIG_PATH_DEFAULT = "file:etc/sso/config/shiro.ini";

    public static final String SCRIPT_DIR = "scriptDir";
    public static final String SCRIPT_DIR_DEFAULT = "etc/sso/script/";

    public static final String MAIN_FILE = "mainFile";
    public static final String MAIN_FILE_DEFAULT = "com/github/alanger/nexus/bootstrap/Main.java";

    private final ServletContext servletContext;

    @Inject
    public Init(final ServletContext servletContext) {
        this.servletContext = Preconditions.checkNotNull(servletContext);

        // Default config location
        if (getConfigPath() == null) {
            setConfigPath(CONFIG_PATH_DEFAULT);
        }

        log.trace("Init servletContext: {}", this.servletContext);
    }

    @SuppressWarnings({"java:S2637", "null"})
    @Override
    public void doStart() throws Exception {
        log.trace("Init doStart()");

        ClassLoader oldTccl = Thread.currentThread().getContextClassLoader();
        GroovyClassLoader gcl = getGroovyClassLoader();

        try {
            // For Shiro Object Builder
            Thread.currentThread().setContextClassLoader(gcl);

            // FIX SAMLSignatureValidationException: Signature is not trusted
            try {
                SignatureValidator.validate(null, null);
            } catch (ConstraintViolationException e) {
                // OK: Validation credential cannot be null
                log.debug("Initialize SPI SignatureValidationProvider done");
            }

            String scriptDir = getInitParameter(SCRIPT_DIR) != null ? getInitParameter(SCRIPT_DIR) : SCRIPT_DIR_DEFAULT;
            String mainFile = getInitParameter(MAIN_FILE) != null ? getInitParameter(MAIN_FILE) : MAIN_FILE_DEFAULT;
            // String mainClassName = getInitParameter("mainClassName") ?: "com.github.alanger.nexus.bootstrap.Main";

            File scriptPath = new File(scriptDir);
            log.trace("scriptPath: {}", scriptPath.getAbsolutePath());
            gcl.addURL(scriptPath.toURI().toURL());

            Class<?> groovyClass = gcl.parseClass(new File(scriptPath, mainFile));
            // Class groovyClass = gcl.loadClass(mainClassName, true, false, true);

            // com.github.alanger.nexus.bootstrap.Main
            log.trace("groovyClass: {}", groovyClass.getCanonicalName());

            groovyClass.getDeclaredConstructor(ServletContext.class).newInstance(servletContext);
        } catch (Exception e) {
            log.error("Init doStart() error", e);
            throw e;
        } finally {
            Thread.currentThread().setContextClassLoader(oldTccl);
        }

    }

    //-- Utils --//

    private String getInitParameter(String name) {
        return servletContext != null ? servletContext.getInitParameter(name) : null;
    }

    private GroovyClassLoader getGroovyClassLoader() {
        GroovyClassLoader gcl = (GroovyClassLoader) servletContext.getAttribute(GroovyClassLoader.class.getCanonicalName());
        // Creates a new classloader if log level = TRACE
        if (gcl == null || isTraceEnabled()) {
            CompilerConfiguration cfg = new CompilerConfiguration();
            cfg.setSourceEncoding("UTF-8");
            cfg.setScriptExtensions(Arrays.stream(new String[] {"groovy", "java"}).collect(Collectors.toSet()));
            cfg.setRecompileGroovySource(true);
            cfg.setMinimumRecompilationInterval(0);

            RootLoader rl = new RootLoader(new URL[] {}, getClass().getClassLoader());
            gcl = new GroovyClassLoader(rl, cfg);
            servletContext.setAttribute(GroovyClassLoader.class.getCanonicalName(), gcl);
        }
        gcl.clearCache();
        gcl.setShouldRecompile(true);

        return gcl;
    }

    public String getConfigPath() {
        return this.servletContext.getInitParameter(CONFIG_PATH);
    }

    public void setConfigPath(String configPath) {
        this.servletContext.setInitParameter(CONFIG_PATH, configPath);
    }

    public boolean isTraceEnabled() {
        return log.isTraceEnabled();
    }

}

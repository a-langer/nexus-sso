package com.github.alanger.nexus.bootstrap;

import java.io.File;
import java.net.URL;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.tools.RootLoader;
import groovy.lang.GroovyClassLoader;
import java.util.logging.Level;

// JUL to Logback: finest,finer = trace, fine = debug, config,info = info, severe = error, warning = warn

String scriptDir = servletConfig.getInitParameter("scriptDir") ?: "etc/sso/script/";
String mainFile = servletConfig.getInitParameter("mainFile") ?: "com/github/alanger/nexus/bootstrap/Main.java";
// String mainClassName = servletConfig.getInitParameter("mainClassName") ?: "com.github.alanger.nexus.bootstrap.Main";
String initMarker = "invoke_done";

GroovyClassLoader getGroovyClassLoader() {
    GroovyClassLoader gcl = servletContext.getAttribute(GroovyClassLoader.class.getCanonicalName());
    // Creates a new classloader if log level = TRACE
    if (gcl == null || logger?.getLevel() == Level.FINEST) {
        CompilerConfiguration cfg = new CompilerConfiguration();
        cfg.setSourceEncoding("UTF-8");
        cfg.setScriptExtensions(["groovy","java"].toSet());
        cfg.setRecompileGroovySource(true);
        cfg.setMinimumRecompilationInterval(0);

        RootLoader rl = new RootLoader(([] as URL[]), getClass().getClassLoader());
        gcl = new GroovyClassLoader(rl, cfg);

        servletContext.setAttribute(GroovyClassLoader.class.getCanonicalName(), gcl);
    }
    gcl.clearCache();
    gcl.setShouldRecompile(true);

    return gcl;
}

// if (servletContext.getAttribute(initMarker) == null) {
    GroovyClassLoader gcl = getGroovyClassLoader();
    Thread.currentThread().setContextClassLoader(gcl);

    File scriptPath = new File(scriptDir);
    logger.finest("scriptPath: ${scriptPath.getAbsolutePath()}");
    gcl.addURL(scriptPath.toURI().toURL());

    Class groovyClass = gcl.parseClass(new File(scriptPath, mainFile));
    // Class groovyClass = gcl.loadClass(mainClassName, true, false, true);

    groovyClass.metaClass.parentLogger = logger;
    logger.finest("groovyClass: ${groovyClass.getCanonicalName()}");
    groovyClass.newInstance(servletContext, servletConfig);
    servletContext.setAttribute(initMarker, true);
// }

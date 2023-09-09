package com.github.alanger.nexus.bootstrap;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.servlet.Filter;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.apache.commons.configuration2.interpol.DefaultLookups;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.pam.ModularRealmAuthenticator;
import org.apache.shiro.authz.ModularRealmAuthorizer;
import org.apache.shiro.config.CommonsInterpolator;
import org.apache.shiro.config.Ini;
import org.apache.shiro.config.ReflectionBuilder;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.realm.text.IniRealm;
import org.apache.shiro.session.mgt.SessionManager;
import org.apache.shiro.web.env.WebEnvironment;
import org.apache.shiro.web.filter.mgt.DefaultFilterChainManager;
import org.apache.shiro.web.filter.mgt.NamedFilterList;
import org.apache.shiro.web.filter.mgt.PathMatchingFilterChainResolver;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.apache.shiro.web.util.WebUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.nexus.bootstrap.osgi.DelegatingFilter;

import com.github.alanger.shiroext.http.MockFilterChain;
import com.github.alanger.shiroext.http.MockHttpServletRequest;
import com.github.alanger.shiroext.http.MockHttpServletResponse;
import com.github.alanger.shiroext.realm.jdbc.JdbcRealmName;
import com.github.alanger.shiroext.realm.pac4j.Pac4jRealmName;

public class Main {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ServletContext servletContext;
    private final ServletConfig servletConfig;

    private final Map<String, Object> objects;
    private final WebEnvironment env;
    private final DefaultWebSecurityManager securityManager;
    private final CommonsInterpolator interpolator;

    public static final String OBJ_ID = "objects";
    public static final String CONTEXT_DONE = "AVAILABLE";

    @SuppressWarnings("unchecked")
    public Main(ServletContext servletContext, ServletConfig servletConfig) {
        this.servletContext = servletContext;
        this.servletConfig = servletConfig;
        logger.info("Context: {}, config: {}", servletContext, servletConfig);

        this.interpolator = new CommonsInterpolator();

        // Create default Shiro objects for Nexus
        this.objects = servletContext.getAttribute(OBJ_ID) != null
                ? (Map<String, Object>) servletContext.getAttribute(OBJ_ID)
                : new ConcurrentHashMap<>();
        servletContext.setAttribute(OBJ_ID, objects);

        // org.apache.shiro.guice.web.WebGuiceEnvironment
        this.env = WebUtils.getWebEnvironment(servletContext);
        logger.trace("env: {}", env);

        // org.apache.shiro.nexus.NexusWebSecurityManager
        this.securityManager = (DefaultWebSecurityManager) SecurityUtils.getSecurityManager();
        logger.trace("securityManager: {}", securityManager);
        objects.put("securityManager", securityManager);

        this.waitContextInitialization();
    }

    private void waitContextInitialization() {
        if (this.servletContext.toString().contains(CONTEXT_DONE)) {
            init();
            return;
        }

        new Thread() {
            int maxTime = 20000;
            int waitTime = 2000;
            int time = 0;
            public void run() {
                String contextName = servletContext.toString();
                if (!contextName.contains(CONTEXT_DONE) && time < maxTime) {
                    try {
                        time = time + waitTime;
                        logger.trace("Waiting {} msec. for context initialization: {}", time, contextName);
                        sleep(waitTime);
                        run();
                    } catch (InterruptedException e) {
                        logger.error("waitContextInitialization error", e);
                    }
                    return;
                }
                init();
            }
        }.start();
    }

    public void init() {
        initInterpolator();
        initDelegatingFilter();
        initObjects();
        logger.info("Init script done, env: {}", env);
    }

    protected void initInterpolator() {
        this.interpolator.getConfigurationInterpolator().registerLookup("b64decode",
                DefaultLookups.BASE64_DECODER.getLookup());
        this.interpolator.getConfigurationInterpolator().registerLookup("b64encode",
                DefaultLookups.BASE64_ENCODER.getLookup());
        this.interpolator.getConfigurationInterpolator().registerLookup("date", DefaultLookups.DATE.getLookup());
        this.interpolator.getConfigurationInterpolator().registerLookup("dns", DefaultLookups.DNS.getLookup());
        this.interpolator.getConfigurationInterpolator().registerLookup("env", DefaultLookups.ENVIRONMENT.getLookup());
        this.interpolator.getConfigurationInterpolator().registerLookup("file", DefaultLookups.FILE.getLookup());
        this.interpolator.getConfigurationInterpolator().registerLookup("java", DefaultLookups.JAVA.getLookup());
        this.interpolator.getConfigurationInterpolator().registerLookup("host", DefaultLookups.LOCAL_HOST.getLookup());
        this.interpolator.getConfigurationInterpolator().registerLookup("prop", DefaultLookups.PROPERTIES.getLookup());
        this.interpolator.getConfigurationInterpolator().registerLookup("res",
                DefaultLookups.RESOURCE_BUNDLE.getLookup());
        this.interpolator.getConfigurationInterpolator().registerLookup("script", DefaultLookups.SCRIPT.getLookup());
        this.interpolator.getConfigurationInterpolator().registerLookup("groovy", GroovyStringLookup.INSTANCE);
        this.interpolator.getConfigurationInterpolator().registerLookup("sys",
                DefaultLookups.SYSTEM_PROPERTIES.getLookup());
        this.interpolator.getConfigurationInterpolator().registerLookup("url", DefaultLookups.URL.getLookup());
        this.interpolator.getConfigurationInterpolator().registerLookup("urlDecode",
                DefaultLookups.URL_DECODER.getLookup());
        this.interpolator.getConfigurationInterpolator().registerLookup("urlEncode",
                DefaultLookups.URL_ENCODER.getLookup());
        this.interpolator.getConfigurationInterpolator().registerLookup("xml", DefaultLookups.XML.getLookup());
    }

    public void initDelegatingFilter() {
        MockHttpServletRequest mockRequest = new MockHttpServletRequest(servletContext);
        mockRequest.setMethod("GET");
        mockRequest.setRequestURI("/service/rapture/session");
        MockHttpServletResponse mockResponse = new MockHttpServletResponse();
        MockFilterChain mockChain = new MockFilterChain();
        DelegatingFilter delegating = new DelegatingFilter();
        try {
            delegating.doFilter(mockRequest, mockResponse, mockChain);
            logger.trace("delegating.doFilter response code: {} , content: {}", mockResponse.getStatus(),
                    mockResponse.getContentAsString());
        } catch (ServletException | IOException e) {
            logger.error("delegating.doFilter error", e);
        }
    }

    public void initObjects() {

        // Shiro ini config
        Ini ini = getIni();

        // Nexus jdbc data source
        OrientConnection orientConnection = new OrientConnection(true);
        objects.put("securityJdbcInfo", orientConnection.getSecurityJdbcInfo());
        objects.put("securityDataSource", orientConnection.getSecurityDataSource());

        // SSO/SAML profile authentication listener
        Pac4jAuthenticationListener pac4jAuthenticationListener = new Pac4jAuthenticationListener(
                orientConnection.getSecurityDataSource());
        objects.put("pac4jAuthenticationListener", pac4jAuthenticationListener);

        // org.sonatype.nexus.security.authc.FirstSuccessfulModularRealmAuthenticator
        ModularRealmAuthenticator authenticator = (ModularRealmAuthenticator) securityManager.getAuthenticator();
        // Optional Authenticator
        // authenticator = new org.apache.shiro.authc.pam.ModularRealmAuthenticator();
        // authenticator.setRealms(securityManager.getRealms());
        // securityManager.setAuthenticator(authenticator);
        logger.trace("authenticator: {}", authenticator);
        // org.apache.shiro.authc.pam.AtLeastOneSuccessfulStrategy
        logger.trace("authenticationStrategy: {}", authenticator.getAuthenticationStrategy());
        objects.put("authenticator", authenticator);

        // org.sonatype.nexus.security.authz.ExceptionCatchingModularRealmAuthorizer
        ModularRealmAuthorizer authorizer = (ModularRealmAuthorizer) securityManager.getAuthorizer();
        // authorizer.setPermissionResolver(new AntPermissionResolver()); // TODO Not loaded from groovy
        // Optional Authorizer
        // authorizer = new com.github.alanger.shiroext.authz.AssignedRealmAuthorizer();
        // authorizer.setRealms(securityManager.getRealms());
        // securityManager.setAuthorizer(authorizer);
        logger.trace("authorizer: {}", authorizer);
        objects.put("authorizer", authorizer);

        // Add all Nexus realms
        for (Realm r : securityManager.getRealms()) {
            if (r != null) {
                logger.trace("Previous realm: {} = {}", r.getName(), r);
                objects.put(r.getName(), r);
            }
        }

        // Clear all realms
        securityManager.getRealms().clear();

        // Ini realm
        IniRealm iniRealm = new IniRealm();
        iniRealm.setName("iniRealm");
        iniRealm.setIni(ini);
        objects.put("iniRealm", iniRealm);

        // Realm for authorization by API token (Basic and Bearer if enabled
        // com.github.alanger.shiroext.web.BearerAuthcFilter)
        JdbcRealmName tokenRealm = new JdbcRealmName();
        tokenRealm.setName("tokenRealm");
        objects.put("tokenRealm", tokenRealm);

        // Realm for access to API token from SSO user, see
        // org.sonatype.nexus.rapture.internal.security.SecurityComponent
        EchoRealm echoRealm = new EchoRealm();
        echoRealm.setName("echoRealm");
        objects.put("echoRealm", echoRealm);

        // Realm for authorization by SAML/SSO
        Pac4jRealmName pac4jRealm = new Pac4jRealmName();
        pac4jRealm.setName("pac4jRealm");
        objects.put("pac4jRealm", pac4jRealm);

        // org.apache.shiro.nexus.NexusWebSessionManager
        SessionManager sessionManager = securityManager.getSessionManager();
        logger.trace("sessionManager: {}", sessionManager);
        objects.put("sessionManager", sessionManager);

        // org.apache.shiro.web.filter.mgt.PathMatchingFilterChainResolver
        PathMatchingFilterChainResolver chainResolver = (PathMatchingFilterChainResolver) env.getFilterChainResolver();
        logger.trace("chainResolver: {}", chainResolver);
        objects.put("chainResolver", chainResolver);

        // org.sonatype.nexus.security.DynamicFilterChainManager
        DefaultFilterChainManager chainManager = (DefaultFilterChainManager) chainResolver.getFilterChainManager();
        objects.put("chainManager", chainManager);
        logger.trace("chainManager: {}", chainManager);
        // chainResolver.setFilterChainManager(chainManager);

        // Debugging a previous filter chains
        for (Entry<String, NamedFilterList> entry : chainManager.getFilterChains().entrySet()) {
            StringBuilder sb = new StringBuilder("");
            // org.apache.shiro.web.filter.mgt.SimpleNamedFilterList
            for (Filter f : entry.getValue()) {
                sb.append("\n  " + f.toString() + " = " + f.getClass().getName());
            }
            logger.trace("Previous chain {} = {}, {}: {}", entry.getKey(), entry.getValue(), entry.getValue().size(), sb);
        }

        // Clear all filter chains
        chainManager.getFilterChains().clear();

        // Add all filters to Shiro objects
        objects.putAll(chainManager.getFilters());

        // Build Shiro objects
        ReflectionBuilder builder = new ReflectionBuilder(objects);
        Map<String, ?> buildObjects = builder.buildObjects(ini.getSection("main"));

        // Add Nexus default realms to current realms list
        for (String nexusRealmName: orientConnection.getRealmNames()) {
            Realm nexusRealm = (Realm) buildObjects.get(nexusRealmName);
            securityManager.getRealms().add(nexusRealm);
        }

        // Debugging a current list of realms
        for (Realm r : securityManager.getRealms()) {
            if (r != null)
                logger.info("Current realm: {} = {}", r.getName(), r);
        }

        // Set all filters
        for (Entry<String, ?> entry : buildObjects.entrySet()) {
            if (entry.getValue() instanceof Filter) {
                logger.trace("filter: {} = {}", entry.getKey(), entry.getValue().getClass().getName());
                chainManager.addFilter(entry.getKey(), (Filter) entry.getValue(), true);
            }
        }

        // Set all filter chains
        for (Entry<String, String> entry : ini.getSection("urls").entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            logger.trace("Current chain: {} = {}", key, value);
            chainManager.createChain(key, value);
        }
    }

    private String getInitParameter(String name) {
        if (servletConfig != null && servletConfig.getInitParameter(name) != null)
            return servletConfig.getInitParameter(name);
        return servletContext.getInitParameter(name);
    }

    private Ini getIni() {
        Ini ini = new Ini();
        String configPath = getInitParameter("configPath");
        if (configPath != null) {
            ini.loadFromPath(configPath);
            interpolateIni(ini);
            String scanPeriod = ini.getSectionProperty("", "scanPeriod");
            String urlRewriteStatusPath = ini.getSectionProperty("", "urlRewriteStatusPath");
            long interval = scanPeriod != null ? Long.parseLong(scanPeriod) : 0;
            if (configPath.startsWith("file:") && interval > 0 && urlRewriteStatusPath != null) {
                createReloadThread(urlRewriteStatusPath, configPath, Long.parseLong(scanPeriod));
            } else {
                removeReloadThread();
            }
        } else {
            ini.load(getInitParameter("config"));
            interpolateIni(ini);
        }
        return ini;
    }

    private void interpolateIni(Ini ini) {
        for (String section : ini.getSectionNames()) {
            for (Entry<String, String> entry : ini.getSection(section).entrySet()) {
                String key = this.interpolator.interpolate(entry.getKey());
                String value = this.interpolator.interpolate(entry.getValue());
                ini.getSection(section).put(key, value);
            }
        }
    }

    private void createReloadThread(String urlRewriteStatusPath, String configPath, long interval) {
        File config = new File(configPath.substring(configPath.indexOf(":") + 1));
        ReloadCongiguration reloadThread = new ReloadCongiguration(urlRewriteStatusPath, config,
                TimeUnit.SECONDS.toMillis(interval), this.servletContext);
        removeReloadThread();
        servletContext.setAttribute(ReloadCongiguration.class.getCanonicalName(), reloadThread);
        logger.info("Enable reload thread '{}' of config '{}' with interval '{}' sec.", reloadThread.getName(),
                configPath, interval);
        reloadThread.start();
    }

    private void removeReloadThread() {
        Thread thread = (Thread) servletContext.getAttribute(ReloadCongiguration.class.getCanonicalName());
        if (thread != null) {
            logger.info("Remove previous reload thread '{}'", thread.getName());
            thread.interrupt();
            servletContext.removeAttribute(ReloadCongiguration.class.getCanonicalName());
        }
    }

}

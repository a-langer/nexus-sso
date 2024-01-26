package com.github.alanger.nexus.bootstrap;

import static com.github.alanger.shiroext.realm.RealmUtils.asList;
import static org.sonatype.nexus.common.text.UnitFormatter.formatStorage;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.servlet.RequestDispatcher.ERROR_MESSAGE;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.apache.shiro.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.record.impl.ODocument;

// https://orientdb.com/docs/2.2.x/Java-Web-Apps.html
// https://orientdb.com/docs/2.2.x/Document-API-Documents.html
// https://orientdb.com/docs/2.2.x/Document-API-Database.html
// https://orientdb.com/docs/2.2.x/Document-Database.html
public class QuotaFilter implements Filter {

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    public static final String REPO_NAME_ATTR = QuotaFilter.class.getCanonicalName() + ".REPO_NAME";
    public static final String REPO_FORMAT_ATTR = QuotaFilter.class.getCanonicalName() + ".REPO_FORMAT";

    private OPartitionedDatabasePool configPool = null;
    private String configURL = "plocal:/nexus-data/db/config";
    private String configUser = "admin";
    private String configPassword = "admin";
    private int configMaxPartitionSize = 1;
    private int configMaxPoolSize = 50;

    private OPartitionedDatabasePool componentPool = null;
    private String componentURL = "plocal:/nexus-data/db/component";
    private String componentUser = "admin";
    private String componentPassword = "admin";
    private int componentMaxPartitionSize = 1;
    private int componentMaxPoolSize = 50;

    // Example: nexus:repository-view:docker:myrepo-docker-hosted:add
    protected String permission = "nexus:repository-view:%s:%s:add";

    private boolean formatFromRepositoryName = false;
    private String formatSplitBy = "-";
    private int formatSplitIndex = 1;

    protected List<String> methods = asList("PUT,POST");

    private String quotaSql = new StringBuilder()
            .append("select name, attributes.blobStoreQuotaConfig.quotaLimitBytes as quota from repository_blobstore")
            .append(" where attributes.blobStoreQuotaConfig.quotaType = 'spaceUsedQuota'")
            .append(" and name in (select attributes.storage.blobStoreName from repository where repository_name = ?)")
            .toString();

    private String sizeSql = "SELECT sum(size) as size FROM asset where bucket.repository_name = ? GROUP BY bucket";

    private int responseStatus = 507; // Insufficient Storage

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        this.configPool = new OPartitionedDatabasePool(configURL, configUser, configPassword, configMaxPartitionSize,
                configMaxPoolSize);
        this.componentPool = new OPartitionedDatabasePool(componentURL, componentUser, componentPassword,
                componentMaxPartitionSize, componentMaxPoolSize);
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) resp;

        boolean isPush = methods.contains(request.getMethod());

        if (request.getAttribute(getClass().getCanonicalName()) != null || !isPush) {
            chain.doFilter(request, response);
            return;
        }
        request.setAttribute(getClass().getCanonicalName(), true);
        request.setCharacterEncoding(UTF_8.name());
        response.setCharacterEncoding(UTF_8.name());

        String repoName = getRepoName(request);
        boolean pushAllowed = isPushAllowed(request, repoName);

        if (repoName != null && pushAllowed && isPush) {
            ODatabaseDocumentTx component = null;
            ODatabaseDocumentTx config = null;
            try {
                config = configPool.acquire();
                List<ODocument> quotaResult = config.query(new OSQLSynchQuery<ODocument>(quotaSql), repoName);

                for (ODocument storeDoc : quotaResult) {
                    String storeName = storeDoc.field("name");
                    long quota = storeDoc.field("quota");

                    logger.trace("repoName: {}, storeName: {}, quota: {},", repoName, storeName, quota);

                    component = componentPool.acquire();
                    List<ODocument> sizeResult = component.query(new OSQLSynchQuery<ODocument>(sizeSql), repoName);

                    for (ODocument repoDoc : sizeResult) {
                        long size = repoDoc.field("size");
                        if (size >= quota) {
                            String msg = format("Blob store %s is using %s space and has a limit of %s", storeName,
                                    formatStorage(size),
                                    formatStorage(quota));
                            logger.trace(msg);
                            response.setStatus(responseStatus);
                            response.setHeader(ERROR_MESSAGE, msg);
                            request.setAttribute(ERROR_MESSAGE, msg);
                            writeJsonMessage(response, msg);
                            return;
                        }
                    }
                }
            } finally {
                if (component != null)
                    component.close();
                if (config != null)
                    config.close();
            }
        }

        chain.doFilter(request, response);
    }

    protected void writeJsonMessage(HttpServletResponse response, String msg) throws IOException {
        if (!response.isCommitted()) {
            response.setContentType("text/json");
            String data = new StringBuilder()
                    .append("[{'message':'")
                    .append(msg)
                    .append("','success':false,'tid':1,'action':'upload','method':'upload','type':'rpc'}]")
                    .toString().replace("'", "\"");
            response.getWriter().write(data);
            response.getWriter().close();
        }
    }

    protected String getRepoName(HttpServletRequest request) {
        String repoName = (String) request.getAttribute(REPO_NAME_ATTR);
        if (repoName == null) {
            // From URI /repository/<name>-<format>-<type>
            // or /service/rest/internal/ui/upload/<name>-<format>-<type>
            repoName = new File(request.getRequestURI()).getName();
        }
        return repoName;
    }

    protected boolean isPushAllowed(HttpServletRequest request, String repoName) {
        boolean pushAllowed = SecurityUtils.getSubject().isAuthenticated();
        if (!pushAllowed) {
            logger.trace("repoName: {}, pushAllowed: {}", repoName, pushAllowed);
            return false;
        }

        // Format from request attribute
        String repoFormat = (String) request.getAttribute(REPO_FORMAT_ATTR);

        // Format from repository name
        if (repoFormat == null && formatFromRepositoryName) {
            // Split repository name, ex.: <name>-<format>-<type>
            String[] arr = repoName.split(formatSplitBy);
            repoFormat = (arr.length > formatSplitIndex) ? arr[formatSplitIndex] : "*";
        } else {
            repoFormat = "*"; // Require permission for all formats
        }

        pushAllowed = SecurityUtils.getSubject().isPermitted(format(permission, repoFormat, repoName));

        logger.trace("repoName: {}, pushAllowed: {}, repoFormat: {}", repoName, pushAllowed, repoFormat);

        return pushAllowed;
    }

    @Override
    public void destroy() {
        if (configPool != null)
            configPool.close();
        if (componentPool != null)
            componentPool.close();
    }

    public String getConfigURL() {
        return configURL;
    }

    public void setConfigURL(String configURL) {
        this.configURL = configURL;
    }

    public String getConfigUser() {
        return configUser;
    }

    public void setConfigUser(String configUser) {
        this.configUser = configUser;
    }

    public String getConfigPassword() {
        return configPassword;
    }

    public void setConfigPassword(String configPassword) {
        this.configPassword = configPassword;
    }

    public int getConfigMaxPartitionSize() {
        return configMaxPartitionSize;
    }

    public void setConfigMaxPartitionSize(int configMaxPartitionSize) {
        this.configMaxPartitionSize = configMaxPartitionSize;
    }

    public int getConfigMaxPoolSize() {
        return configMaxPoolSize;
    }

    public void setConfigMaxPoolSize(int configMaxPoolSize) {
        this.configMaxPoolSize = configMaxPoolSize;
    }

    public String getComponentURL() {
        return componentURL;
    }

    public void setComponentURL(String componentURL) {
        this.componentURL = componentURL;
    }

    public String getComponentUser() {
        return componentUser;
    }

    public void setComponentUser(String componentUser) {
        this.componentUser = componentUser;
    }

    public String getComponentPassword() {
        return componentPassword;
    }

    public void setComponentPassword(String componentPassword) {
        this.componentPassword = componentPassword;
    }

    public int getComponentMaxPartitionSize() {
        return componentMaxPartitionSize;
    }

    public void setComponentMaxPartitionSize(int componentMaxPartitionSize) {
        this.componentMaxPartitionSize = componentMaxPartitionSize;
    }

    public int getComponentMaxPoolSize() {
        return componentMaxPoolSize;
    }

    public void setComponentMaxPoolSize(int componentMaxPoolSize) {
        this.componentMaxPoolSize = componentMaxPoolSize;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    public boolean isFormatFromRepositoryName() {
        return formatFromRepositoryName;
    }

    public void setFormatFromRepositoryName(boolean formatFromRepositoryName) {
        this.formatFromRepositoryName = formatFromRepositoryName;
    }

    public String getFormatSplitBy() {
        return formatSplitBy;
    }

    public void setFormatSplitBy(String formatSplitBy) {
        this.formatSplitBy = formatSplitBy;
    }

    public int getFormatSplitIndex() {
        return formatSplitIndex;
    }

    public void setFormatSplitIndex(int formatSplitIndex) {
        this.formatSplitIndex = formatSplitIndex;
    }

    public List<String> getMethods() {
        return methods;
    }

    public void setMethods(List<String> methods) {
        this.methods = methods;
    }

    public void setMethods(String methodsStr) {
        this.methods = asList(methodsStr);
    }

    public String getQuotaSql() {
        return quotaSql;
    }

    public void setQuotaSql(String quotaSql) {
        this.quotaSql = quotaSql;
    }

    public String getSizeSql() {
        return sizeSql;
    }

    public void setSizeSql(String sizeSql) {
        this.sizeSql = sizeSql;
    }

    public int getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(int responseStatus) {
        this.responseStatus = responseStatus;
    }

}

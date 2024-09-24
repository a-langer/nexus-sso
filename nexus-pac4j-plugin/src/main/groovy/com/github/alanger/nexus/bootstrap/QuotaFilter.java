package com.github.alanger.nexus.bootstrap;

import static com.github.alanger.shiroext.realm.RealmUtils.asList;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.servlet.RequestDispatcher.ERROR_MESSAGE;
import static java.util.Collections.singletonList;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.apache.shiro.authz.Permission;
import org.apache.shiro.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.nexus.blobstore.api.BlobStore;
import org.sonatype.nexus.blobstore.api.BlobStoreManager;
import org.sonatype.nexus.blobstore.quota.BlobStoreQuotaResult;
import org.sonatype.nexus.blobstore.quota.BlobStoreQuotaService;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.manager.RepositoryManager;
import org.sonatype.nexus.repository.security.RepositoryContentSelectorPermission;
import org.sonatype.nexus.repository.security.RepositoryViewPermission;
import org.sonatype.nexus.security.BreadActions;
import org.sonatype.nexus.security.SecurityHelper;
import org.sonatype.nexus.selector.SelectorManager;
import com.github.alanger.nexus.plugin.DI;

/**
 * Quota filter.
 * 
 * @see https://help.sonatype.com/en/configuring-blob-stores.html#adding-a-soft-quota
 * @see https://help.sonatype.com/en/storage-guide.html
 * @see org.sonatype.nexus.repository.internal.blobstore.BlobStoreQuotaHealthCheck
 */
public class QuotaFilter implements Filter {

    public static final String REPO_NAME_ATTR = QuotaFilter.class.getCanonicalName() + ".REPO_NAME";

    // All protected for fix groovy.lang.MissingPropertyException: No such property: XXXXX

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    protected String permission = BreadActions.ADD; // Example: read, add, delete 

    protected List<String> methods = asList("PUT,POST");

    private int responseStatus = 507; // Insufficient Storage

    protected RepositoryManager repositoryManager;

    protected SecurityHelper securityHelper;

    protected SelectorManager selectorManager;

    protected BlobStoreManager blobStoreManager;

    protected BlobStoreQuotaService blobStoreQuotaService;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        this.repositoryManager = DI.getInstance().repositoryManager;
        this.securityHelper = DI.getInstance().securityHelper;
        this.selectorManager = DI.getInstance().selectorManager;
        this.blobStoreManager = DI.getInstance().blobStoreManager;
        this.blobStoreQuotaService = DI.getInstance().blobStoreQuotaService;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException, ServletException {
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
        Repository repo = repoName != null ? this.repositoryManager.get(repoName) : null;
        boolean pushAllowed = repo != null && userCanInRepository(repo);

        if (repo != null && pushAllowed && isPush) {

            String storeName = repo.getConfiguration().attributes("storage").get("blobStoreName", String.class);
            BlobStore blobStore = storeName != null ? this.blobStoreManager.get(storeName) : null;
            BlobStoreQuotaResult result = blobStore != null ? this.blobStoreQuotaService.checkQuota(blobStore) : null;
            logger.trace("repoName: {}, storeName: {}, result: {},", repoName, storeName, result);

            if (result != null && result.isViolation()) {
                String msg = result.getMessage();
                logger.trace(msg);
                response.setStatus(responseStatus(request));
                response.setHeader(ERROR_MESSAGE, msg);
                request.setAttribute(ERROR_MESSAGE, msg);
                writeJsonMessage(response, msg);
                return;
            }
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        // nothing
    }

    protected void writeJsonMessage(HttpServletResponse response, String msg) throws IOException {
        if (!response.isCommitted()) {
            response.setContentType("text/json");
            String data = new StringBuilder().append("[{'message':'") //
                    .append(msg) //
                    .append("','success':false,'tid':1,'action':'upload','method':'upload','type':'rpc'}]") //
                    .toString().replace("'", "\""); //
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

    private int responseStatus(HttpServletRequest request) {
        // UI response must be 200
        return request.getRequestURI().startsWith("/repository/") ? getResponseStatus() : 200;
    }

    // org.sonatype.nexus.repository.security.RepositoryPermissionChecker

    public boolean userCanInRepository(final Repository repository) {
        return userHasRepositoryViewPermissionTo(permission, repository) || userHasAnyContentSelectorAccessTo(repository, permission);
    }

    private boolean userHasRepositoryViewPermissionTo(final String action, final Repository repository) {
        return this.securityHelper.anyPermitted(new RepositoryViewPermission(repository, action));
    }

    private boolean userHasAnyContentSelectorAccessTo(final Repository repository, final String... actions) {
        Subject subject = this.securityHelper.subject();
        return this.selectorManager.browse().stream()
                .anyMatch(selector -> this.securityHelper.anyPermitted(subject,
                        Arrays.stream(actions)
                                .map(action -> new RepositoryContentSelectorPermission(selector, repository, singletonList(action)))
                                .toArray(Permission[]::new)));
    }

    // Getters and Setters

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
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

    public int getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(int responseStatus) {
        this.responseStatus = responseStatus;
    }

}

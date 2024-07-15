package com.github.alanger.nexus.plugin.ui;

import org.sonatype.nexus.coreui.ComponentXO;
import org.sonatype.nexus.coreui.SearchComponent;

import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.sonatype.nexus.common.event.EventManager;
import org.sonatype.nexus.extdirect.model.LimitedPagedResponse;
import org.sonatype.nexus.extdirect.model.StoreLoadParameters;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.manager.RepositoryManager;
import org.sonatype.nexus.repository.search.SearchService;
import org.sonatype.nexus.repository.search.query.SearchResultsGenerator;
import org.sonatype.nexus.repository.security.RepositoryPermissionChecker;
import org.sonatype.nexus.repository.security.RepositoryViewPermission;
import org.sonatype.nexus.security.SecurityHelper;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.softwarementors.extjs.djn.config.annotations.DirectAction;
import org.apache.shiro.authz.annotation.RequiresPermissions;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Collections.singletonList;

// POST: /service/extdirect
// {"action":"coreui_Search_NonTransitive","method":"read","data":[{"formatSearch":false,"page":1,"start":0,"limit":300,"filter":[{"property":"keyword","value":"mystr"}]}],"type":"rpc","tid":8}
@Named
@Singleton
@DirectAction(action = NonTransitiveSearchComponent.ACTION)
public class NonTransitiveSearchComponent extends SearchComponent {

    public static final String ACTION = "coreui_Search_NonTransitive";

    private final RepositoryPermissionChecker repositoryPermissionChecker;

    private final SecurityHelper securityHelper;

    private final RepositoryManager repositoryManager;

    @Inject
    public NonTransitiveSearchComponent(RepositoryPermissionChecker repositoryPermissionChecker,
            SecurityHelper securityHelper, RepositoryManager repositoryManager, SearchService searchService,
            @Named("${nexus.searchResultsLimit:-1000}") int searchResultsLimit,
            SearchResultsGenerator searchResultsGenerator, EventManager eventManager) {
        super(searchService, searchResultsLimit, searchResultsGenerator, eventManager);

        this.repositoryPermissionChecker = checkNotNull(repositoryPermissionChecker);
        this.securityHelper = checkNotNull(securityHelper);
        this.repositoryManager = checkNotNull(repositoryManager);
        log.trace("searchService {}, searchResultsGenerator: {}, eventManager: {}", searchService,
                searchResultsGenerator, eventManager);
    }

    @Override
    @Timed
    @ExceptionMetered
    @RequiresPermissions("nexus:search:read")
    public LimitedPagedResponse<ComponentXO> read(StoreLoadParameters parameters) {
        log.trace("parameters: {}", parameters);
        List<ComponentXO> componentXOs = super.read(parameters).getData().stream()
                .filter(c -> isNonTransitive(c.getRepositoryName())).collect(Collectors.toList());
        return new LimitedPagedResponse<>(parameters.getLimit(), componentXOs.size(), componentXOs, false);
    }

    // Skip group repository
    private boolean isNonTransitive(String repositoryName) {
        Repository repository = repositoryManager.softGet(repositoryName);
        log.trace("repository: {}", repository);
        return !(repository == null || "group".equals(repository.getType().getValue()));
    }

    protected boolean isViewPermission(String format, String repositoryName) {
        RepositoryViewPermission rvp = new RepositoryViewPermission(format, repositoryName, singletonList("browse"));
        return securityHelper.isPermitted(rvp)[0];
    }

    protected boolean isViewPermission(Repository repository) {
        return repositoryPermissionChecker.userCanReadOrBrowse(repository);
    }

}

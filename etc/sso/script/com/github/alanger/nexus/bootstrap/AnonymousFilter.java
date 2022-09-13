package com.github.alanger.nexus.bootstrap;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.util.ThreadContext;
import org.apache.shiro.web.servlet.AdviceFilter;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.apache.shiro.mgt.DefaultSubjectDAO;
import org.apache.shiro.mgt.DefaultSessionStorageEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.sonatype.nexus.security.anonymous.AnonymousPrincipalCollection;

// https://github.com/sonatype/nexus-public/blob/master/components/nexus-security/src/main/java/org/sonatype/nexus/security/anonymous/AnonymousFilter.java
// https://github.com/sonatype/nexus-public/blob/master/components/nexus-base/src/main/java/org/sonatype/nexus/internal/security/anonymous/AnonymousManagerImpl.java
// https://github.com/sonatype/nexus-public/blob/master/components/nexus-security/src/main/java/org/apache/shiro/nexus/NexusSessionStorageEvaluator.java
public class AnonymousFilter extends AdviceFilter {

    private static final Logger logger = LoggerFactory.getLogger(AnonymousFilter.class);

    public static final String NAME = "anonPublic";

    private static final String ORIGINAL_SUBJECT = AnonymousFilter.class.getName() + ".originalSubject";

    public static final String DEFAULT_USER_ID = "anonymous";

    public static final String DEFAULT_REALM_NAME = "NexusAuthorizingRealm";

    private boolean sessionCreationEnabled = false;

    private String userId = DEFAULT_USER_ID;

    private String realmName = DEFAULT_REALM_NAME;

    public AnonymousFilter() {
        setName(NAME);
    }

    @Override
    protected boolean preHandle(final ServletRequest request, final ServletResponse response) throws Exception {
        Subject subject = SecurityUtils.getSubject();
        if (subject.getPrincipal() == null /* && manager.isEnabled() */) {
            request.setAttribute(ORIGINAL_SUBJECT, subject);
            subject = buildSubject();
            ThreadContext.bind(subject);
            logger.trace("Bound anonymous subject: {}", subject);
        }

        return true;
    }

    public Subject buildSubject() {
        logger.trace("Building anonymous subject with user-id: {}, realm-name: {}", getUserId(), getRealmName());
        PrincipalCollection principals = new AnonymousPrincipalCollection(getUserId(), getRealmName());
        return new Subject.Builder()
                .principals(principals)
                .authenticated(false)
                .sessionCreationEnabled(this.sessionCreationEnabled)
                .buildSubject();
    }

    @Override
    public void afterCompletion(final ServletRequest request, final ServletResponse response, final Exception exception)
            throws Exception {
        Subject subject = (Subject) request.getAttribute(ORIGINAL_SUBJECT);
        if (subject != null) {
            logger.trace("Binding original subject: {}", subject);
            ThreadContext.bind(subject);
        }
    }

    public void setSessionStorageEnabled(boolean enabled) {
        DefaultWebSecurityManager securityManager = (DefaultWebSecurityManager) SecurityUtils.getSecurityManager();
        DefaultSubjectDAO subjectDAO = (DefaultSubjectDAO) securityManager.getSubjectDAO();
        DefaultSessionStorageEvaluator sessionStorageEvaluator = (DefaultSessionStorageEvaluator) subjectDAO
                .getSessionStorageEvaluator();
        sessionStorageEvaluator.setSessionStorageEnabled(enabled);
    }

    public void setSessionCreationEnabled(boolean sessionCreationEnabled) {
        this.sessionCreationEnabled = sessionCreationEnabled;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getRealmName() {
        return realmName;
    }

    public void setRealmName(String realmName) {
        this.realmName = realmName;
    }

}

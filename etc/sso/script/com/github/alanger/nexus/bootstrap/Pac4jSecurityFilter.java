package com.github.alanger.nexus.bootstrap;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import io.buji.pac4j.filter.SecurityFilter;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.web.util.WebUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//// DisabledSessionException: Session creation has been disabled for the current subject.
// import org.sonatype.nexus.security.authc.NexusAuthenticationFilter;
// import org.sonatype.nexus.security.authc.apikey.ApiKeyAuthenticationFilter;

public class Pac4jSecurityFilter extends SecurityFilter {

    protected static Logger log = LoggerFactory.getLogger(Pac4jSecurityFilter.class);

    protected static final String AUTHORIZATION_HEADER = "Authorization";

    protected String getAuthzHeader(ServletRequest request) {
        HttpServletRequest httpRequest = WebUtils.toHttp(request);
        return httpRequest.getHeader(AUTHORIZATION_HEADER);
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {

        String authorizationHeader = getAuthzHeader(servletRequest);

        Subject subject = SecurityUtils.getSubject();
        boolean authenticated = subject.getPrincipal() != null && subject.isAuthenticated();

        if ((authorizationHeader == null || authorizationHeader.length() == 0) && !authenticated) {
            try {
                super.doFilter(servletRequest, servletResponse, filterChain);
            } catch (IOException | ServletException e) {
                throw e;
            } catch (Exception e) {
                log.warn("Filter error: ", e);
                throw new ServletException(e);
            }
        } else {
            filterChain.doFilter(servletRequest, servletResponse);
        }
    }

}

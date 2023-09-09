package com.github.alanger.nexus.bootstrap;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.buji.pac4j.filter.SecurityFilter;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//// DisabledSessionException: Session creation has been disabled for the current subject.
// import org.sonatype.nexus.security.authc.NexusAuthenticationFilter;
// import org.sonatype.nexus.security.authc.apikey.ApiKeyAuthenticationFilter;

public class Pac4jSecurityFilter extends SecurityFilter {

    protected static Logger log = LoggerFactory.getLogger(Pac4jSecurityFilter.class);

    private boolean ifNotAuthenticated = true;
    private boolean ifNotAuthzHeader = true;
    private boolean ifNotXMLHttpRequest = true;
    private boolean ifBrowserRequest = true;

    public boolean isAuthenticated() {
        Subject subject = SecurityUtils.getSubject();
        return subject.getPrincipal() != null && subject.isAuthenticated();
    }

    public boolean isAuthzHeader(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.length() > 0;
    }

    public boolean isBrowserRequest(HttpServletRequest request) {
        String header = request.getHeader("User-Agent");
        return header != null && header.toLowerCase().startsWith("mozilla");
    }

    public boolean isXMLHttpRequest(HttpServletRequest request) {
        String header = request.getHeader("X-Requested-With");
        return header != null && header.equals("XMLHttpRequest");
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) resp;

        if (request.getAttribute(getClass().getCanonicalName()) != null) {
            chain.doFilter(request, response);
            return;
        }
        request.setAttribute(getClass().getCanonicalName(), true);

        boolean need = true;
        if (ifNotAuthenticated) {
            need = !isAuthenticated();
        }
        if (need && ifNotAuthzHeader) {
            need = !isAuthzHeader(request);
        }
        if (need && ifNotXMLHttpRequest) {
            need = !isXMLHttpRequest(request);
        }
        if (need && ifBrowserRequest) {
            need = isBrowserRequest(request);
        }

        if (need) {
            try {
                super.doFilter(request, response, chain);
            } catch (IOException | ServletException e) {
                throw e;
            } catch (Exception e) {
                log.warn("Filter error: ", e);
                throw new ServletException(e);
            }
        } else {
            chain.doFilter(request, response);
        }
    }

    public boolean isIfNotAuthenticated() {
        return ifNotAuthenticated;
    }

    public void setIfNotAuthenticated(boolean ifNotAuthenticated) {
        this.ifNotAuthenticated = ifNotAuthenticated;
    }

    public boolean isIfNotAuthzHeader() {
        return ifNotAuthzHeader;
    }

    public void setIfNotAuthzHeader(boolean ifNotAuthzHeader) {
        this.ifNotAuthzHeader = ifNotAuthzHeader;
    }

    public boolean isIfNotXMLHttpRequest() {
        return ifNotXMLHttpRequest;
    }

    public void setIfNotXMLHttpRequest(boolean ifNotXMLHttpRequest) {
        this.ifNotXMLHttpRequest = ifNotXMLHttpRequest;
    }

    public boolean isIfBrowserRequest() {
        return ifBrowserRequest;
    }

    public void setIfBrowserRequest(boolean ifBrowserRequest) {
        this.ifBrowserRequest = ifBrowserRequest;
    }

}

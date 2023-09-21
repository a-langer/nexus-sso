package com.github.alanger.nexus.bootstrap;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.servlet.RequestDispatcher.ERROR_MESSAGE;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;

/*
 * Filter by subject name
 */
public class SubjectFilter extends QuotaFilter {

    private String namePattern = "admin";

    public SubjectFilter() {
        setMethods("PUT,POST,DELETE,MOVE,PROPPATCH");
        setResponseStatus(403);
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // none
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) resp;

        boolean isRecord = methods.contains(request.getMethod());

        if (request.getAttribute(getClass().getCanonicalName()) != null || !isRecord) {
            chain.doFilter(request, response);
            return;
        }
        request.setAttribute(getClass().getCanonicalName(), true);
        request.setCharacterEncoding(UTF_8.name());
        response.setCharacterEncoding(UTF_8.name());

        Subject subject = SecurityUtils.getSubject();
        String userName = String.valueOf(subject.getPrincipal());

        boolean allowed = userName.matches(namePattern);

        if (!allowed) {
            String msg = format("User %s is forbidden method %s to %s", userName, request.getMethod(), getRepoName(request));
            logger.trace(msg);
            response.setStatus(getResponseStatus());
            response.setHeader(ERROR_MESSAGE, msg);
            request.setAttribute(ERROR_MESSAGE, msg);
            writeJsonMessage(response, msg);
            return;
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        // none
    }

    public String getNamePattern() {
        return namePattern;
    }

    public void setNamePattern(String namePattern) {
        this.namePattern = namePattern;
    }
    
}

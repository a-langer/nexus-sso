package com.github.alanger.nexus.bootstrap;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Collection;
import java.util.Enumeration;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DebugFilter implements Filter {

    protected static Logger log = LoggerFactory.getLogger(DebugFilter.class);

    protected boolean printHeader = true;
    protected boolean printResponseHeader = false;
    protected boolean printAttribute = true;
    protected boolean printParameter = true;
    protected boolean printBody = false;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // nothing
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) resp;

        printRequest(request, printHeader, printAttribute, printParameter, printBody, log);
        if (printResponseHeader)
            printResponseHeaders(response, log);

        if (!response.isCommitted())
            chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        // nothing
    }

    public static void throwException(Throwable cause) throws ServletException, IOException {
        if (cause instanceof IOException)
            throw (IOException) cause;
        else if (cause instanceof ServletException)
            throw (ServletException) cause;
        else
            throw new ServletException(cause);
    }

    public static void printRequest(HttpServletRequest request) throws IOException {
        printRequest(request, true, true, true, false, log);
    }

    public static void printRequest(HttpServletRequest request, boolean printHeader, boolean printAttribute,
            boolean printParameter, boolean printBody, Logger log) throws IOException {
        log.debug("------ Time {} -----", System.currentTimeMillis());
        log.debug("request: {} {}", request.getMethod(), request.getRequestURI());

        if (printHeader) {
            printHeaders(request, log);
        }

        if (printAttribute) {
            printAttributes(request, log);
        }

        if (printParameter) {
            printParameters(request, log);
        }

        if (printBody) {
            printBody(request, log);
        }
    }

    public static void printHeaders(HttpServletRequest request, Logger log) {
        StringBuilder sb = new StringBuilder("");
        Enumeration<String> e = request.getHeaderNames();
        while (e.hasMoreElements()) {
            String s = e.nextElement();
            sb.append("\n  " + s + " = " + request.getHeader(s));
        }
        log.debug("headers: {}", sb);
    }

    public static void printResponseHeaders(HttpServletResponse response, Logger log) {
        StringBuilder sb = new StringBuilder("");
        Collection<String> e = response.getHeaderNames();
        sb.append("\n  status = " + response.getStatus());
        sb.append("\n  committed = " + response.isCommitted());
        for (String s : e) {
            sb.append("\n  " + s + " = " + response.getHeader(s));
        }
        log.debug("response headers: {}", sb);
    }

    public static void printAttributes(HttpServletRequest request, Logger log) {
        StringBuilder sb = new StringBuilder("");
        Enumeration<String> e = request.getAttributeNames();
        while (e.hasMoreElements()) {
            String s = e.nextElement();
            sb.append("\n  " + s + " = " + request.getAttribute(s));
        }
        log.debug("attributes: {}", sb);
    }

    public static void printParameters(HttpServletRequest request, Logger log) {
        StringBuilder sb = new StringBuilder("");
        Enumeration<String> e = request.getParameterNames();
        while (e.hasMoreElements()) {
            String s = e.nextElement();
            sb.append("\n  " + s + " = " + request.getParameter(s));
        }
        log.debug("parameters: {}", sb);
    }

    public static void printBody(HttpServletRequest request, Logger log) throws IOException {
        StringBuilder sb = new StringBuilder("");
        BufferedReader reader = request.getReader();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append("\n  " + line);
        }
        reader.close();
        log.debug("body: {}", sb);
    }

    public void setLog(String logName) {
        log = LoggerFactory.getLogger(logName);
    }

    public boolean isPrintHeader() {
        return printHeader;
    }

    public void setPrintHeader(boolean printHeader) {
        this.printHeader = printHeader;
    }

    public boolean isPrintResponseHeader() {
        return printResponseHeader;
    }

    public void setPrintResponseHeader(boolean printResponseHeader) {
        this.printResponseHeader = printResponseHeader;
    }

    public boolean isPrintAttribute() {
        return printAttribute;
    }

    public void setPrintAttribute(boolean printAttribute) {
        this.printAttribute = printAttribute;
    }

    public boolean isPrintParameter() {
        return printParameter;
    }

    public void setPrintParameter(boolean printParameter) {
        this.printParameter = printParameter;
    }

    public boolean isPrintBody() {
        return printBody;
    }

    public void setPrintBody(boolean printBody) {
        this.printBody = printBody;
    }

}

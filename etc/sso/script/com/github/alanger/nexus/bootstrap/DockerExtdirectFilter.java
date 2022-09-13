package com.github.alanger.nexus.bootstrap;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import groovy.json.JsonSlurper;
import groovy.json.JsonOutput;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.alanger.shiroext.servlets.MultiReadRequestWrapper;
import com.github.alanger.shiroext.servlets.MutableResponseWrapper;

public class DockerExtdirectFilter implements Filter {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private ServletContext servletContext;

    private final JsonSlurper jsonSlurper = new JsonSlurper();

    private static final String COREUI_COMPONENT = "coreui_Component";

    private static final String READ_COMPONENT = "readComponent";

    private static final String DOCKER_FORMAT = "docker";

    private String dockerRoot = "docker-root";

    private String prefix = "library/";

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        this.servletContext = filterConfig.getServletContext();
    }

    /*
     * Request docker:
     * {"action":"coreui_Component","method":"readComponent","data":["XXXXX",
     * "my-repo-name"],"type":"rpc","tid":41}
     * 
     * Response docker:
     * {"tid":41,"action":"coreui_Component","method":"readComponent","result":{
     * "success":true,"data":{"id":"XXXXX","repositoryName":"my-repo-name","group":
     * null,"name":"library/alpine","version":"latest","format":"docker"}},"type":
     * "rpc"}
     * 
     * Request realm settings:
     * {"action":"coreui_RealmSettings","method":"update","data":[{"realms":[
     * "NexusAuthenticatingRealm","NexusAuthorizingRealm","DockerToken",
     * "rutauth-realm"]}],"type":"rpc","tid":36}
     */
    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) resp;

        MultiReadRequestWrapper requestWrapper = new MultiReadRequestWrapper(request);
        boolean isJson = (request.getContentType() != null && request.getContentType().contains("json"));
        Map<String, Object> reqMap = isJson ? toMap(jsonSlurper.parse(requestWrapper.getInputStream()))
                : new HashMap<>();
        logger.trace("Request as Map: {}", reqMap);

        if (COREUI_COMPONENT.equals(reqMap.get("action")) && READ_COMPONENT.equals(reqMap.get("method"))) {

            MutableResponseWrapper responseWrapper = new MutableResponseWrapper(response);
            chain.doFilter(requestWrapper, responseWrapper);

            String respContent = responseWrapper.getContentAsString();
            Map<String, Object> resMap = toMap(jsonSlurper.parseText(respContent));
            logger.trace("Response as Map: {}", resMap);

            if (COREUI_COMPONENT.equals(resMap.get("action")) && READ_COMPONENT.equals(resMap.get("method"))) {
                Map<String, Object> result = toMap(resMap.get("result"));
                logger.trace("Response result: {}", result);

                if (result != null && Boolean.TRUE.equals(result.get("success"))) {
                    Map<String, Object> data = toMap(result.get("data"));
                    if (DOCKER_FORMAT.equals(data.get("format"))) {
                        String fullName = String.valueOf(data.get("name"));
                        if (fullName.startsWith(this.prefix)) {
                            fullName = fullName.substring(this.prefix.length(), fullName.length());
                        }
                        String repositoryName = String.valueOf(data.get("repositoryName"));
                        if (repositoryName.equals(dockerRoot) && fullName.startsWith(dockerRoot + "/")) {
                            fullName = fullName.substring((dockerRoot + "/").length(), fullName.length());
                        } else if (!fullName.startsWith(repositoryName + "/")) {
                            fullName = repositoryName + "/" + fullName;
                        }
                        fullName = getHostName(request) + "/" + fullName;
                        logger.trace("Image fullName: {}", fullName);
                        data.put("name", fullName);
                        respContent = JsonOutput.toJson(resMap);

                        response.setContentType(responseWrapper.getContentType());
                        response.setCharacterEncoding(responseWrapper.getCharacterEncoding());
                        response.setContentLength(respContent.length());
                        PrintWriter out = response.getWriter();
                        out.print(respContent);
                        out.flush();
                        return;
                    }
                }
            }
        }

        chain.doFilter(requestWrapper, response);

        if ("POST".equals(request.getMethod()) && response.getStatus() == 200
                && "coreui_RealmSettings".equals(reqMap.get("action")) && "update".equals(reqMap.get("method"))) {
            logger.trace("Realm update - request method: {}, json method: {}, response status: {}", request.getMethod(),
                    reqMap.get("method"), response.getStatus());
            servletContext.setAttribute(ReloadCongiguration.NEED_RELOAD, true);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object obj) {
        return (Map<String, Object>) obj;
    }

    private String getHostName(HttpServletRequest request) {
        String host = request.getServerName();
        int port = request.getServerPort();
        if (port != 80 && port != 443) {
            host = host + ":" + port;
        }
        return host + (request.getContextPath() != null ? request.getContextPath() : "");
    }

    public String getDockerRoot() {
        return dockerRoot;
    }

    public void setDockerRoot(String dockerRoot) {
        this.dockerRoot = dockerRoot;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public void destroy() {
        // nothing
    }

}

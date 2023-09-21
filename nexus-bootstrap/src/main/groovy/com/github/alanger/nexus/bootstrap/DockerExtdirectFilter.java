package com.github.alanger.nexus.bootstrap;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import groovy.json.JsonSlurper;
import groovy.json.JsonOutput;

import org.apache.shiro.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.alanger.shiroext.servlets.MultiReadRequestWrapper;
import com.github.alanger.shiroext.servlets.MutableResponseWrapper;

public class DockerExtdirectFilter extends QuotaFilter {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private ServletContext servletContext;

    private final JsonSlurper jsonSlurper = new JsonSlurper();

    private static final String COREUI_COMPONENT = "coreui_Component";

    private static final String READ_COMPONENT = "readComponent";

    private static final String READ_COMPONENT_ASSETS = "readComponentAssets";

    private static final String READ_ASSET = "readAsset";

    private static final String DOCKER_FORMAT = "docker";

    private String dockerRoot = "docker-root";

    private String prefix = "library/";

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        this.servletContext = filterConfig.getServletContext();
    }

    /**
     * Request readAsset:
     * <pre>
     * {"action":"coreui_Component","method":"readAsset","data":[
     * "XXXXX","test-raw-hosted"],"type":"rpc","tid":17}
     * </pre>
     * 
     * Response readAsset:
     * <pre>
     * {"tid":17,"action":"coreui_Component","method":"readAsset","result":{
     * "success":true,"data":{"id":"XXXXX","name":
     * "example.text","format":"raw","contentType":
     * "application/text","size":100,"repositoryName":"test-raw-hosted",
     * "containingRepositoryName":"test-raw-hosted","blobCreated":
     * "XXXX","blobUpdated":"XXXX"
     * ,"lastDownloaded":null,"blobRef":"test-raw-hosted@XXXX","componentId":
     * "XXXX","createdBy":"admin","createdByIp":"127.0.0.1","attributes":{
     * "checksum":{"sha1":"XXXX","sha512":"XXXX","sha256":"XXXX","md5":"XXXX"},
     * "content":{"last_modified":"XXXX"},
     * "provenance":{"hashes_not_verified":false}}}},"type":"rpc"}
     * </pre>
     * 
     * Request readComponent docker:
     * <pre>
     * {"action":"coreui_Component","method":"readComponent","data":["XXXXX",
     * "my-repo-name"],"type":"rpc","tid":41}
     * </pre>
     * 
     * Response readComponent docker:
     * <pre>
     * {"tid":41,"action":"coreui_Component","method":"readComponent","result":{
     * "success":true,"data":{"id":"XXXXX","repositoryName":"my-repo-name","group":
     * null,"name":"library/alpine","version":"latest","format":"docker"}},"type":
     * "rpc"}
     * </pre>
     * 
     * Request for update realm settings:
     * <pre>
     * {"action":"coreui_RealmSettings","method":"update","data":[{"realms":[
     * "NexusAuthenticatingRealm","NexusAuthorizingRealm","DockerToken",
     * "rutauth-realm"]}],"type":"rpc","tid":36}
     * </pre>
     */
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
        request.setCharacterEncoding(UTF_8.name());
        response.setCharacterEncoding(UTF_8.name());

        MultiReadRequestWrapper requestWrapper = new MultiReadRequestWrapper(request);

        boolean isJson = (request.getContentType() != null && request.getContentType().contains("json"));
        Map<String, Object> reqMap = toMap(isJson ? jsonSlurper.parse(requestWrapper.getInputStream()) : null);
        logger.trace("Request isJson: {}, map: {}", isJson, reqMap);

        String reqAction = String.valueOf(reqMap.get("action"));
        String reqMethod = String.valueOf(reqMap.get("method"));

        if (COREUI_COMPONENT.equals(reqAction) && (READ_COMPONENT.equals(reqMethod) ||
                READ_ASSET.equals(reqMethod)) || READ_COMPONENT_ASSETS.equals(reqMethod)) {

            MutableResponseWrapper responseWrapper = new MutableResponseWrapper(response);
            chain.doFilter(requestWrapper, responseWrapper);

            String respContent = responseWrapper.getContentAsString();
            Map<String, Object> resMap = toMap(jsonSlurper.parseText(respContent));
            logger.trace("Response as Map: {}", resMap);

            Map<String, Object> result = toMap(resMap.get("result"));
            boolean success = result != null && Boolean.TRUE.equals(result.get("success"));
            String action = String.valueOf(resMap.get("action"));
            String method = String.valueOf(resMap.get("method"));

            @SuppressWarnings("null")
            List<Object> datas = toList(result.get("data")); // List if readComponentAssets

            boolean changed = false;

            for (Object d : datas) {
                Map<String, Object> data = toMap(d);

                String repoName = String.valueOf(data.get("repositoryName"));
                String repoFormat = String.valueOf(data.get("format"));
                logger.trace("repoName: {}, repoFormat: {}, action: {}, method: {}", repoName, repoFormat, action,
                        method);

                // Hide private properties of an asset
                if (success && COREUI_COMPONENT.equals(action)
                        && (READ_ASSET.equals(method) || READ_COMPONENT_ASSETS.equals(method))) {
                    // Check push permission
                    boolean pushAllowed = SecurityUtils.getSubject()
                            .isPermitted(format(permission, repoFormat, repoName));
                    if (!pushAllowed) {
                        data.put("createdBy", "***");
                        data.put("createdByIp", "***");
                        changed = true;
                    }
                }

                // Change Docker image name
                if (success && COREUI_COMPONENT.equals(action) && DOCKER_FORMAT.equals(repoFormat)
                        && (READ_COMPONENT.equals(method) || READ_COMPONENT_ASSETS.equals(method))) {
                    String fullName = String.valueOf(data.get("name"));
                    if (fullName.startsWith(this.prefix)) {
                        fullName = fullName.substring(this.prefix.length(), fullName.length());
                    }
                    if (repoName.equals(dockerRoot) && fullName.startsWith(dockerRoot + "/")) {
                        fullName = fullName.substring((dockerRoot + "/").length(), fullName.length());
                    } else if (!repoName.equals(dockerRoot) && !fullName.startsWith(repoName + "/")) {
                        fullName = repoName + "/" + fullName;
                    }
                    fullName = getHostName(request) + "/" + fullName;
                    logger.trace("Image fullName: {}", fullName);
                    data.put("name", fullName);
                    changed = true;
                }
            }

            // Write changed response
            if (changed) {
                respContent = JsonOutput.toJson(resMap);
                response.setStatus(responseWrapper.getStatus());
                response.setContentType(responseWrapper.getContentType());
                response.setCharacterEncoding(responseWrapper.getCharacterEncoding());
                response.setContentLength(respContent.length());
                PrintWriter out = response.getWriter();
                out.print(respContent);
                out.flush();
                return;
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
        if (obj instanceof Map)
            return (Map<String, Object>) obj;
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private List<Object> toList(Object obj) {
        if (obj instanceof List)
            return (List<Object>) obj;
        return Arrays.asList(obj);
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

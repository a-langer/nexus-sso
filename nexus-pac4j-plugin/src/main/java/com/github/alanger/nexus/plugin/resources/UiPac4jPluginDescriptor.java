package com.github.alanger.nexus.plugin.resources;

import java.util.List;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.sonatype.nexus.common.app.BaseUrlHolder;
import org.sonatype.nexus.common.template.TemplateHelper;
import org.sonatype.nexus.common.template.TemplateParameters;
import org.sonatype.nexus.ui.UiPluginDescriptorSupport;
import org.sonatype.nexus.webresources.GeneratedWebResource;
import org.sonatype.nexus.webresources.WebResource;
import org.sonatype.nexus.webresources.WebResourceBundle;
import com.github.alanger.nexus.plugin.realm.NexusPac4jRealm;
import java.io.IOException;
import java.net.URL;

import static java.util.Arrays.asList;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Customize Nexus UI for SSO plugin.
 * 
 * @see org.sonatype.nexus.rapture.internal.UiReactPluginDescriptorImpl
 * @see org.sonatype.nexus.rapture.internal.RaptureWebResourceBundle
 * @see org.sonatype.nexus.internal.webresources.WebResourceServiceImpl 
 */
@Named(UiPac4jPluginDescriptor.NAME)
@Singleton
public class UiPac4jPluginDescriptor extends UiPluginDescriptorSupport implements WebResourceBundle {

    public static final String HEADER_PANEL_LOGO_TEXT = "Header_Panel_Logo_Text";
    public static final String SIGNIN_MODAL_DIALOG_HTML = "SignIn_Modal_Dialog_Html";
    public static final String SIGNIN_MODAL_DIALOG_TOOLTIP = "SignIn_Modal_Dialog_Tooltip";
    public static final String SIGNIN_SSO_ENABLED = "SignIn_SSO_Enabled";
    public static final String AUTHENTICATE_MODAL_DIALOG_MESSAGE = "Authenticate_Modal_Dialog_Message";

    private String headerPanelLogoText = "Nexus OSS";
    private String signinModalDialogHtml = "<div>Sign in with SSO</div>";
    private String signinModalDialogTooltip = "SSO Login";
    private String authenticateModalDialogMessage =
            "<div>Accessing API Key requires validation of your credentials (<strong>enter your username if using SSO login</strong>).</div>";

    public static final String NAME = "nexus-sso-customize";
    public static final String PATH = "/static/sso/" + NAME + ".js";

    private final List<String> scripts;
    private final String scriptLocation;
    private final TemplateHelper templateHelper;
    private final DefaultWebSecurityManager securityManager;

    @Inject
    public UiPac4jPluginDescriptor(@Named("${nexus.sso.script.location:-" + NAME + ".vm.js}") final String location,
            final TemplateHelper templateHelper) {
        super(NAME);
        scripts = asList(PATH);
        this.scriptLocation = location;
        this.templateHelper = checkNotNull(templateHelper);
        this.securityManager = (DefaultWebSecurityManager) SecurityUtils.getSecurityManager();
    }

    @Nullable
    @Override
    public List<String> getScripts(final boolean isDebug) {
        return scripts;
    }

    //-- WebResourceBundle interface --//

    @Override
    public List<WebResource> getResources() {
        return asList(getCustomizeJs());
    }

    private abstract class TemplateWebResource extends GeneratedWebResource {
        protected byte[] render(final String template, final TemplateParameters parameters) throws IOException {
            log.trace("Rendering template: {}, with params: {}", template, parameters);
            URL url = getClass().getResource(template);
            return templateHelper.render(url, parameters).getBytes();
        }
    }

    /**
     * The customize.js resource.
     */
    private WebResource getCustomizeJs() {
        return new TemplateWebResource() {
            @Override
            public String getPath() {
                return PATH;
            }

            @Override
            public String getContentType() {
                return JAVASCRIPT;
            }

            @Override
            protected byte[] generate() throws IOException {
                return render(scriptLocation, new TemplateParameters() //
                        .set("baseUrl", BaseUrlHolder.get()) //
                        .set("relativePath", BaseUrlHolder.getRelativePath()) //
                        .set(HEADER_PANEL_LOGO_TEXT, headerPanelLogoText) //
                        .set(SIGNIN_MODAL_DIALOG_HTML, signinModalDialogHtml) //
                        .set(SIGNIN_MODAL_DIALOG_TOOLTIP, signinModalDialogTooltip) //
                        .set(SIGNIN_SSO_ENABLED, isSsoEnabled()) //
                        .set(AUTHENTICATE_MODAL_DIALOG_MESSAGE, authenticateModalDialogMessage) //
                );
            }
        };
    }

    public void setHeaderPanelLogoText(String headerPanelLogoText) {
        if (headerPanelLogoText != null && !headerPanelLogoText.isEmpty())
            this.headerPanelLogoText = headerPanelLogoText;
    }

    public void setSigninModalDialogHtml(String signinModalDialogHtml) {
        if (signinModalDialogHtml != null && !signinModalDialogHtml.isEmpty())
            this.signinModalDialogHtml = signinModalDialogHtml;
    }

    public void setSigninModalDialogTooltip(String signinModalDialogTooltip) {
        if (signinModalDialogTooltip != null && !signinModalDialogTooltip.isEmpty())
            this.signinModalDialogTooltip = signinModalDialogTooltip;
    }

    public void setAuthenticateModalDialogMessage(String authenticateModalDialogMessage) {
        if (authenticateModalDialogMessage != null && !authenticateModalDialogMessage.isEmpty())
            this.authenticateModalDialogMessage = authenticateModalDialogMessage;
    }

    private boolean isSsoEnabled() {
        try {
            return this.securityManager.getRealms().stream().anyMatch(r -> r != null && r.getName().equals(NexusPac4jRealm.NAME));
        } catch (NullPointerException e) {
            log.trace("isSsoEnabled error", e);
            return false;
        }
    }

}

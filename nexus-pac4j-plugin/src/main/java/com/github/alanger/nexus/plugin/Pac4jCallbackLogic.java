package com.github.alanger.nexus.plugin;

import org.pac4j.core.config.Config;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.engine.DefaultCallbackLogic;
import org.pac4j.core.http.adapter.HttpActionAdapter;
import io.buji.pac4j.profile.ShiroProfileManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Required since buji-pac4j:8.0.0, add to in shiro.ini:
 * 
 * <pre>
 * callbackLogic = com.github.alanger.nexus.plugin.Pac4jCallbackLogic
 * config.callbackLogic = $callbackLogic
 * callbackFilter.callbackLogic = $callbackLogic
 * </pre>
 * 
 * Or use {@link io.buji.pac4j.bridge.Pac4jShiroBridge}:
 * <pre>
 * pac4jToShiroBridge = io.buji.pac4j.bridge.Pac4jShiroBridge
 * pac4jToShiroBridge.config = $config
 * </pre>
 * 
 * @see https://github.com/bujiio/buji-pac4j/blob/8.0.x/src/main/resources/buji-pac4j-default.ini
 * @see https://github.com/pac4j/buji-pac4j-demo/blob/8.0.x/src/main/resources/shiro.ini
 */
public class Pac4jCallbackLogic extends DefaultCallbackLogic {

    private static final Logger logger = LoggerFactory.getLogger(Pac4jCallbackLogic.class);

    public Pac4jCallbackLogic() {
        super();
        this.setProfileManagerFactory(ShiroProfileManager::new);
    }

    @Override
    public Object perform(WebContext webContext, SessionStore sessionStore, Config config,
            HttpActionAdapter httpActionAdapter, String inputDefaultUrl, Boolean inputRenewSession,
            String defaultClient) {
        try {
            return super.perform(webContext, sessionStore, config, httpActionAdapter, inputDefaultUrl,
                    inputRenewSession, defaultClient);
        } catch (final Exception e) {
            // Verbose error from org.opensaml.xmlsec.signature.support.SignatureValidator
            logger.trace("Callback perform error:", e);
            throw e;
        }
    }

}

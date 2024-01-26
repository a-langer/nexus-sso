package com.github.alanger.nexus.plugin;

import org.pac4j.core.config.Config;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.http.adapter.HttpActionAdapter;
import io.buji.pac4j.engine.ShiroCallbackLogic;

/**
 * Debug only.
 */
public class Pac4jCallbackLogic<R, C extends WebContext> extends ShiroCallbackLogic<R, C> {

    @Override
    public R perform(C context, Config config, HttpActionAdapter<R, C> httpActionAdapter, String inputDefaultUrl,
            Boolean inputSaveInSession, Boolean inputMultiProfile, Boolean inputRenewSession, String client) {
        try {
            return super.perform(context, config, httpActionAdapter, inputDefaultUrl, inputSaveInSession, inputMultiProfile,
                    inputRenewSession, client);
        } catch (final Exception e) {
            // Verbose error from org.opensaml.xmlsec.signature.support.SignatureValidator
            logger.trace("Callback perform error:", e);
            throw e;
        }
    }
}

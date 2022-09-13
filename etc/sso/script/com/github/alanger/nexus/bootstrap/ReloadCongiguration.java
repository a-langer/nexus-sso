package com.github.alanger.nexus.bootstrap;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.servlet.ServletContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReloadCongiguration extends Thread {

    public static final String NEED_RELOAD = "shiro_need_reload";

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final String urlRewriteStatusPath;
    private final File config;
    private final long interval;
    private final ServletContext servletContext;

    private long lastModified = 0L;

    private volatile boolean stopped;

    public ReloadCongiguration(String urlRewriteStatusPath, File config, long interval, ServletContext servletContext) {
        this.urlRewriteStatusPath = urlRewriteStatusPath;
        this.config = config;
        setLastModified(config.lastModified());
        this.interval = interval;
        this.servletContext = servletContext;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    @Override
    public void interrupt() {
        stopped = true;
        super.interrupt();
    }

    @Override
    public boolean isInterrupted() {
        return stopped ? stopped : super.isInterrupted();
    }

    @Override
    public void run() {
        if (isInterrupted() || Thread.interrupted() || Thread.currentThread().isInterrupted())
            return;
        logger.trace("run: config = {}, lastModified = {}", config, this.lastModified);
        if (config != null) {
            if (config.lastModified() > this.lastModified || Boolean.TRUE.equals(servletContext.getAttribute(NEED_RELOAD))) {
                try {
                    this.swapUrlRewriteConfig();
                    setLastModified(config.lastModified());
                    servletContext.removeAttribute(NEED_RELOAD);
                } catch (Exception e) {
                    logger.error("Ini reload error", e);
                }
            }
            try {
                sleep(interval);
            } catch (InterruptedException e) {
                logger.warn("Ini reload sleep error", e);
                interrupt();
                Thread.currentThread().interrupt();
                return;
            }
            run();
        }
    }

    public void swapUrlRewriteConfig() throws IOException {
        URL url = new URL(this.urlRewriteStatusPath);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setConnectTimeout(5000);
        con.setReadTimeout(10000);
        int status = con.getResponseCode();
        logger.trace("swapUrlRewriteConfig status: {}", status);
        if (status != 200) {
            throw new IOException("GET in " + this.urlRewriteStatusPath + " returned code " + status);
        }
    }
}

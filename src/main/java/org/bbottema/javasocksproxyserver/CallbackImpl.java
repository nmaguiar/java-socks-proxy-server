package org.bbottema.javasocksproxyserver;

import java.util.Map;

import org.slf4j.Logger;

public class CallbackImpl implements Callback {
    private Logger logger;

    public CallbackImpl(Logger logger) {
        this.logger = logger;
    }

    @Override
    public boolean exec(Map data) {
        return false;
    }

    @Override
    public void error(String msg) {
        logger.error(msg);
    }

    @Override
    public void debug(String msg) {
        logger.debug(msg);
    }

    @Override
    public void info(String msg) {
        logger.info(msg);
    }

    @Override
    public void error(String msg, Exception e) {
        logger.error(msg, e);
    }
    
}

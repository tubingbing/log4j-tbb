package com.tu.logging.log4j.core;

import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.message.MessageFactory;

import java.net.URI;

/**
 * @author: tb
 * @Date: 2018/3/22
 * @Time: 11:04
 */
public class TLoggerContext extends LoggerContext {
    public TLoggerContext(String name) {
        super(name);
    }

    public TLoggerContext(String name, Object externalContext) {
        super(name, externalContext);
    }

    public TLoggerContext(String name, Object externalContext, URI configLocn) {
        super(name, externalContext, configLocn);
    }

    public TLoggerContext(String name, Object externalContext, String configLocn) {
        super(name, externalContext, configLocn);
    }

    @Override
    protected Logger newInstance(final LoggerContext ctx, final String name, final MessageFactory messageFactory) {
        return new TLogger(ctx, name, messageFactory);
    }
}

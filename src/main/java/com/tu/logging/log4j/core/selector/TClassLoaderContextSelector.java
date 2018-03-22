package com.tu.logging.log4j.core.selector;

import com.tu.logging.log4j.core.TLoggerContext;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.selector.ContextSelector;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.ReflectionUtil;

import java.lang.ref.WeakReference;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author: tb
 * @Date: 2018/3/22
 * @Time: 15:00
 */
public class TClassLoaderContextSelector implements ContextSelector {

    private static final AtomicReference<TLoggerContext> CONTEXT = new AtomicReference<TLoggerContext>();

    protected static final StatusLogger LOGGER = StatusLogger.getLogger();

    protected static final ConcurrentMap<String, AtomicReference<WeakReference<TLoggerContext>>> CONTEXT_MAP =
            new ConcurrentHashMap<String, AtomicReference<WeakReference<TLoggerContext>>>();

    @Override
    public TLoggerContext getContext(final String fqcn, final ClassLoader loader, final boolean currentContext) {
        return getContext(fqcn, loader, currentContext, null);
    }

    @Override
    public TLoggerContext getContext(final String fqcn, final ClassLoader loader, final boolean currentContext,
                                      final URI configLocation) {
        if (currentContext) {
            return getDefault();
        } else if (loader != null) {
            return locateContext(loader, configLocation);
        } else {
            final Class<?> clazz = ReflectionUtil.getCallerClass(fqcn);
            if (clazz != null) {
                return locateContext(clazz.getClassLoader(), configLocation);
            }
            return getDefault();
        }
    }

    @Override
    public void removeContext(final LoggerContext context) {
        for (final Map.Entry<String, AtomicReference<WeakReference<TLoggerContext>>> entry : CONTEXT_MAP.entrySet()) {
            final LoggerContext ctx = entry.getValue().get().get();
            if (ctx == context) {
                CONTEXT_MAP.remove(entry.getKey());
            }
        }
    }

    @Override
    public List<LoggerContext> getLoggerContexts() {
        final List<LoggerContext> list = new ArrayList<LoggerContext>();
        final Collection<AtomicReference<WeakReference<TLoggerContext>>> coll = CONTEXT_MAP.values();
        for (final AtomicReference<WeakReference<TLoggerContext>> ref : coll) {
            final TLoggerContext ctx = ref.get().get();
            if (ctx != null) {
                list.add(ctx);
            }
        }
        return Collections.unmodifiableList(list);
    }

    private TLoggerContext locateContext(final ClassLoader loaderOrNull, final URI configLocation) {
        // LOG4J2-477: class loader may be null
        final ClassLoader loader = loaderOrNull != null ? loaderOrNull : ClassLoader.getSystemClassLoader();
        final String name = toContextMapKey(loader);
        AtomicReference<WeakReference<TLoggerContext>> ref = CONTEXT_MAP.get(name);
        if (ref == null) {
            if (configLocation == null) {
                ClassLoader parent = loader.getParent();
                while (parent != null) {

                    ref = CONTEXT_MAP.get(toContextMapKey(parent));
                    if (ref != null) {
                        final WeakReference<TLoggerContext> r = ref.get();
                        final TLoggerContext ctx = r.get();
                        if (ctx != null) {
                            return ctx;
                        }
                    }
                    parent = parent.getParent();
                }
            }
            TLoggerContext ctx = new TLoggerContext(name, null, configLocation);
            final AtomicReference<WeakReference<TLoggerContext>> r =
                    new AtomicReference<WeakReference<TLoggerContext>>();
            r.set(new WeakReference<TLoggerContext>(ctx));
            CONTEXT_MAP.putIfAbsent(name, r);
            ctx = CONTEXT_MAP.get(name).get().get();
            return ctx;
        }
        final WeakReference<TLoggerContext> weakRef = ref.get();
        TLoggerContext ctx = weakRef.get();
        if (ctx != null) {
            if (ctx.getConfigLocation() == null && configLocation != null) {
                LOGGER.debug("Setting configuration to {}", configLocation);
                ctx.setConfigLocation(configLocation);
            } else if (ctx.getConfigLocation() != null && configLocation != null &&
                    !ctx.getConfigLocation().equals(configLocation)) {
                LOGGER.warn("locateContext called with URI {}. Existing LoggerContext has URI {}", configLocation,
                        ctx.getConfigLocation());
            }
            return ctx;
        }
        ctx = new TLoggerContext(name, null, configLocation);
        ref.compareAndSet(weakRef, new WeakReference<TLoggerContext>(ctx));
        return ctx;
    }

    private String toContextMapKey(final ClassLoader loader) {
        return String.valueOf(System.identityHashCode(loader));
    }

    protected TLoggerContext getDefault() {
        final TLoggerContext ctx = CONTEXT.get();
        if (ctx != null) {
            return ctx;
        }
        CONTEXT.compareAndSet(null, new TLoggerContext("Default"));
        return CONTEXT.get();
    }
}
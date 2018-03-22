package com.tu.logging.log4j.core;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.MessageFactory;
import org.apache.logging.log4j.util.PropertiesUtil;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author: tb
 * @Date: 2018/3/22
 * @Time: 11:11
 */
public class TLogger extends Logger {
    private static final String FQCN = TLogger.class.getName();
    private static final String PROPERTIES_FILE = "META-INF/log4j-config.properties";
    public static final String PROPERTY_LENGTH = "length";
    public static final String PROPERTY_WRITE_LEVEL = "write_level";
    public static final String PROPERTY_ADD_LEVEL = "add_level";
    public static final String PROPERTY_SWITCH = "switch";
    public static final String PROPERTY_EXPIRE_TIME = "expire_time";
    //队列长度
    public static int LENGTH;
    //写入日志的级别
    public static int WRITE_LEVEL;
    //添加队列的日志级别
    public static int ADD_LEVEL;
    //开关  false 按正常的模式打印日志  true按设置的write_level批量打印日志
    public static boolean SWITCH;
    //队列存储失效时间
    public static int EXPIRE_TIME;
    private static ConcurrentMap<Thread, SoftReference<List<LogInfo>>> map = new ConcurrentHashMap<Thread, SoftReference<List<LogInfo>>>();


    static {
        PropertiesUtil propertiesUtil = new PropertiesUtil(PROPERTIES_FILE);
        LENGTH = propertiesUtil.getIntegerProperty(PROPERTY_LENGTH, 50);
        WRITE_LEVEL = propertiesUtil.getIntegerProperty(PROPERTY_WRITE_LEVEL, Level.ERROR.intLevel());
        ADD_LEVEL = propertiesUtil.getIntegerProperty(PROPERTY_ADD_LEVEL, Level.DEBUG.intLevel());
        SWITCH = propertiesUtil.getBooleanProperty(PROPERTY_SWITCH, false);
        EXPIRE_TIME = propertiesUtil.getIntegerProperty(PROPERTY_EXPIRE_TIME, 10);
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(new Thread() {
            @Override
            public void run() {
                if (map == null) {
                    return;
                }
                Date date = new Date();
                for (Map.Entry<Thread, SoftReference<List<LogInfo>>> m : map.entrySet()) {
                    SoftReference<List<LogInfo>> sf = m.getValue();
                    if (sf == null || sf.get() == null || sf.get().isEmpty()) {
                        return;
                    }
                    List<LogInfo> list = sf.get();
                    if (date.getTime() - list.get(0).time >= EXPIRE_TIME * 1000) {
                        list.clear();
                    }
                }
            }
        }, EXPIRE_TIME, EXPIRE_TIME, TimeUnit.SECONDS);
    }

    /**
     * The constructor.
     *
     * @param context        The LoggerContext this Logger is associated with.
     * @param name           The name of the Logger.
     * @param messageFactory The message factory.
     */
    protected TLogger(LoggerContext context, String name, MessageFactory messageFactory) {
        super(context, name, messageFactory);
    }


    @Override
    public void logIfEnabled(final String fqcn, final Level level, final Marker marker, final String message, final Throwable t) {
        if (SWITCH && level.intLevel() <= ADD_LEVEL) {
            addThreadLogQueue(new LogInfo(fqcn, level, marker, message, 1, t, null));
            return;
        }
        super.logIfEnabled(fqcn, level, marker, message);
    }

    @Override
    public void logIfEnabled(final String fqcn, final Level level, final Marker marker, final Message message,
                             final Throwable t) {
        if (SWITCH && level.intLevel() <= ADD_LEVEL) {
            addThreadLogQueue(new LogInfo(fqcn, level, marker, message, 2, t, null));
            return;
        }
        super.logIfEnabled(fqcn, level, marker, message, t);
    }

    @Override
    public void logIfEnabled(final String fqcn, final Level level, final Marker marker, final Object message,
                             final Throwable t) {
        if (SWITCH && level.intLevel() <= ADD_LEVEL) {
            addThreadLogQueue(new LogInfo(fqcn, level, marker, message, 3, t, null));
            return;
        }
        super.logIfEnabled(fqcn, level, marker, message, t);
    }

    @Override
    public void logIfEnabled(final String fqcn, final Level level, final Marker marker, final String message) {
        if (SWITCH && level.intLevel() <= ADD_LEVEL) {
            addThreadLogQueue(new LogInfo(fqcn, level, marker, message, 1, null, null));
            return;
        }
        super.logIfEnabled(fqcn, level, marker, message);
    }

    @Override
    public void logIfEnabled(final String fqcn, final Level level, final Marker marker, final String message,
                             final Object... params) {
        if (SWITCH && level.intLevel() <= ADD_LEVEL) {
            addThreadLogQueue(new LogInfo(fqcn, level, marker, message, 1, null, params));
            return;
        }
        super.logIfEnabled(fqcn, level, marker, message, params);
    }

    @Override
    public void exit() {
        super.exit();
        SoftReference<List<LogInfo>> softReference = null;
        try {
            softReference = map.get(Thread.currentThread());
            writeMessageLog(softReference);
        } finally {
            if (softReference != null) {
                List<LogInfo> list = softReference.get();
                list.clear();
                list = null;
                softReference = null;
            }
        }
    }

    private void writeMessageLog(SoftReference<List<LogInfo>> softReference) {
        if (softReference == null || softReference.get() == null || softReference.get().isEmpty()) {
            return;
        }
        List<LogInfo> list = softReference.get();
        boolean canWrite = false;
        for (LogInfo info : list) {
            if (info.level.intLevel() == WRITE_LEVEL) {
                canWrite = true;
            }
        }
        if (canWrite) {
            for (LogInfo info : list) {
                if (info.messageType == 1) {
                    if (info.t == null) {
                        if (info.params == null) {
                            logMessage(info.fqcn, info.level, info.marker, (String) info.message);
                        } else {
                            logMessage(info.fqcn, info.level, info.marker, (String) info.message, info.params);
                        }
                    } else {
                        logMessage(info.fqcn, info.level, info.marker, (String) info.message, info.t);
                    }
                }
                if (info.messageType == 2) {
                    logMessage(info.fqcn, info.level, info.marker, (Message) info.message, info.t);
                }
                if (info.messageType == 3) {
                    logMessage(info.fqcn, info.level, info.marker, info.message, info.t);
                }
            }
            list.clear();
        }
    }


    private void addThreadLogQueue(LogInfo logInfo) {
        Thread thread = Thread.currentThread();
        SoftReference<List<LogInfo>> softReference = map.get(thread);
        if (softReference == null || softReference.get() == null) {
            softReference = new SoftReference(new ArrayList<LogInfo>());
            map.put(thread, softReference);
        }
        int size = softReference.get().size();
        if (size >= LENGTH) {
            softReference.get().remove(size - 1);
        }
        softReference.get().add(logInfo);
        writeMessageLog(softReference);
    }

    class LogInfo {
        String fqcn;
        Level level;
        Object message;
        Throwable t;
        Marker marker;
        Object[] params;
        int messageType;
        long time;

        LogInfo(String fqcn, Level level, Marker marker, Object message, int messageType, Throwable t, Object... params) {
            this.fqcn = fqcn;
            this.level = level;
            this.marker = marker;
            this.message = message;
            this.t = t;
            this.params = params;
            this.messageType = messageType;
            time = new Date().getTime();
        }
    }


}

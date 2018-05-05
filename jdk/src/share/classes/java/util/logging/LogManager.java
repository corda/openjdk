/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */


package java.util.logging;

import java.io.*;
import java.util.*;
import java.security.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.beans.PropertyChangeListener;

/**
 * There is a single global LogManager object that is used to
 * maintain a set of shared state about Loggers and log services.
 * <p>
 * This LogManager object:
 * <ul>
 * <li> Manages a hierarchical namespace of Logger objects.  All
 *      named Loggers are stored in this namespace.
 * <li> Manages a set of logging control properties.  These are
 *      simple key-value pairs that can be used by Handlers and
 *      other logging objects to configure themselves.
 * </ul>
 * <p>
 * The global LogManager object can be retrieved using LogManager.getLogManager().
 * The LogManager object is created during class initialization and
 * cannot subsequently be changed.
 * <p>
 * At startup the LogManager class is located using the
 * java.util.logging.manager system property.
 * <p>
 * The LogManager defines two optional system properties that allow control over
 * the initial configuration:
 * <ul>
 * <li>"java.util.logging.config.class"
 * <li>"java.util.logging.config.file"
 * </ul>
 * These two properties may be specified on the command line to the "java"
 * command, or as system property definitions passed to JNI_CreateJavaVM.
 * <p>
 * If the "java.util.logging.config.class" property is set, then the
 * property value is treated as a class name.  The given class will be
 * loaded, an object will be instantiated, and that object's constructor
 * is responsible for reading in the initial configuration.  (That object
 * may use other system properties to control its configuration.)
 * <p>
 * If "java.util.logging.config.class" property is <b>not</b> set,
 * then the "java.util.logging.config.file" system property can be used
 * to specify a properties file (in java.util.Properties format). The
 * initial logging configuration will be read from this file.
 * <p>
 * If neither of these properties is defined then the LogManager uses its
 * default configuration. The default configuration is typically loaded from the
 * properties file "{@code lib/logging.properties}" in the Java installation
 * directory.
 * <p>
 * The properties for loggers and Handlers will have names starting
 * with the dot-separated name for the handler or logger.
 * <p>
 * The global logging properties may include:
 * <ul>
 * <li>A property "handlers".  This defines a whitespace or comma separated
 * list of class names for handler classes to load and register as
 * handlers on the root Logger (the Logger named "").  Each class
 * name must be for a Handler class which has a default constructor.
 * Note that these Handlers may be created lazily, when they are
 * first used.
 *
 * <li>A property "&lt;logger&gt;.handlers". This defines a whitespace or
 * comma separated list of class names for handlers classes to
 * load and register as handlers to the specified logger. Each class
 * name must be for a Handler class which has a default constructor.
 * Note that these Handlers may be created lazily, when they are
 * first used.
 *
 * <li>A property "&lt;logger&gt;.useParentHandlers". This defines a boolean
 * value. By default every logger calls its parent in addition to
 * handling the logging message itself, this often result in messages
 * being handled by the root logger as well. When setting this property
 * to false a Handler needs to be configured for this logger otherwise
 * no logging messages are delivered.
 *
 * <li>A property "config".  This property is intended to allow
 * arbitrary configuration code to be run.  The property defines a
 * whitespace or comma separated list of class names.  A new instance will be
 * created for each named class.  The default constructor of each class
 * may execute arbitrary code to update the logging configuration, such as
 * setting logger levels, adding handlers, adding filters, etc.
 * </ul>
 * <p>
 * Note that all classes loaded during LogManager configuration are
 * first searched on the system class path before any user class path.
 * That includes the LogManager class, any config classes, and any
 * handler classes.
 * <p>
 * Loggers are organized into a naming hierarchy based on their
 * dot separated names.  Thus "a.b.c" is a child of "a.b", but
 * "a.b1" and a.b2" are peers.
 * <p>
 * All properties whose names end with ".level" are assumed to define
 * log levels for Loggers.  Thus "foo.level" defines a log level for
 * the logger called "foo" and (recursively) for any of its children
 * in the naming hierarchy.  Log Levels are applied in the order they
 * are defined in the properties file.  Thus level settings for child
 * nodes in the tree should come after settings for their parents.
 * The property name ".level" can be used to set the level for the
 * root of the tree.
 * <p>
 * All methods on the LogManager object are multi-thread safe.
 *
 * @since 1.4
*/

public class LogManager {
    // The global LogManager object
    private static final LogManager manager = new LogManager();

    // 'props' is assigned within a lock but accessed without it.
    // Declaring it volatile makes sure that another thread will not
    // be able to see a partially constructed 'props' object.
    // (seeing a partially constructed 'props' object can result in
    // NPE being thrown in Hashtable.get(), because it leaves the door
    // open for props.getProperties() to be called before the construcor
    // of Hashtable is actually completed).
    private volatile Properties props = new Properties();
    private final static Level defaultLevel = Level.INFO;

    // The map of the registered listeners. The map value is the registration
    // count to allow for cases where the same listener is registered many times.
    private final Map<Object,Integer> listenerMap = new HashMap<>();

    // This is the only Logger.
    private final Logger rootLogger = new RootLogger();

    /**
     * Protected constructor.  This is protected so that container applications
     * (such as J2EE containers) can subclass the object.  It is non-public as
     * it is intended that there only be one LogManager object, whose value is
     * retrieved by calling LogManager.getLogManager.
     */
    protected LogManager() {
        this(checkSubclassPermissions());
    }

    private LogManager(Void checked) {
    }

    private static Void checkSubclassPermissions() {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            // These permission will be checked in the LogManager constructor,
            // in order to register the Cleaner() thread as a shutdown hook.
            // Check them here to avoid the penalty of constructing the object
            // etc...
            sm.checkPermission(new RuntimePermission("shutdownHooks"));
            sm.checkPermission(new RuntimePermission("setContextClassLoader"));
        }
        return null;
    }

    /**
     * Returns the global LogManager object.
     * @return the global LogManager object
     */
    public static LogManager getLogManager() {
        return manager;
    }

    /**
     * Adds an event listener to be invoked when the logging
     * properties are re-read. Adding multiple instances of
     * the same event Listener results in multiple entries
     * in the property event listener table.
     *
     * <p><b>WARNING:</b> This method is omitted from this class in all subset
     * Profiles of Java SE that do not include the {@code java.beans} package.
     * </p>
     *
     * @param l  event listener
     * @exception  SecurityException  if a security manager exists and if
     *             the caller does not have LoggingPermission("control").
     * @exception NullPointerException if the PropertyChangeListener is null.
     * @deprecated The dependency on {@code PropertyChangeListener} creates a
     *             significant impediment to future modularization of the Java
     *             platform. This method will be removed in a future release.
     */
    @Deprecated
    public void addPropertyChangeListener(PropertyChangeListener l) throws SecurityException {
        PropertyChangeListener listener = Objects.requireNonNull(l);
        checkPermission();
        synchronized (listenerMap) {
            // increment the registration count if already registered
            Integer value = listenerMap.get(listener);
            value = (value == null) ? 1 : (value + 1);
            listenerMap.put(listener, value);
        }
    }

    /**
     * Removes an event listener for property change events.
     * If the same listener instance has been added to the listener table
     * through multiple invocations of <CODE>addPropertyChangeListener</CODE>,
     * then an equivalent number of
     * <CODE>removePropertyChangeListener</CODE> invocations are required to remove
     * all instances of that listener from the listener table.
     * <P>
     * Returns silently if the given listener is not found.
     *
     * <p><b>WARNING:</b> This method is omitted from this class in all subset
     * Profiles of Java SE that do not include the {@code java.beans} package.
     * </p>
     *
     * @param l  event listener (can be null)
     * @exception  SecurityException  if a security manager exists and if
     *             the caller does not have LoggingPermission("control").
     * @deprecated The dependency on {@code PropertyChangeListener} creates a
     *             significant impediment to future modularization of the Java
     *             platform. This method will be removed in a future release.
     */
    @Deprecated
    public void removePropertyChangeListener(PropertyChangeListener l) throws SecurityException {
        checkPermission();
        if (l != null) {
            PropertyChangeListener listener = l;
            synchronized (listenerMap) {
                Integer value = listenerMap.get(listener);
                if (value != null) {
                    // remove from map if registration count is 1, otherwise
                    // just decrement its count
                    int i = value.intValue();
                    if (i == 1) {
                        listenerMap.remove(listener);
                    } else {
                        assert i > 1;
                        listenerMap.put(listener, i - 1);
                    }
                }
            }
        }
    }

    // Find or create a specified logger instance. If a logger has
    // already been created with the given name it is returned.
    // Otherwise a new logger instance is created and registered
    // in the LogManager global namespace.
    // This method will always return a non-null Logger object.
    // Synchronization is not required here. All synchronization for
    // adding a new Logger object is handled by addLogger().
    //
    // This method must delegate to the LogManager implementation to
    // add a new Logger or return the one that has been added previously
    // as a LogManager subclass may override the addLogger, getLogger,
    // and other methods.
    Logger demandLogger(String name, String resourceBundleName, Class<?> caller) {
        return getLogger(name);
    }

    Logger demandSystemLogger(String name, String resourceBundleName) {
        return getLogger(name);
    }

    /**
     * Add a named logger.  This does nothing and returns false if a logger
     * with the same name is already registered.
     * <p>
     * The Logger factory methods call this method to register each
     * newly created Logger.
     * <p>
     * The application should retain its own reference to the Logger
     * object to avoid it being garbage collected.  The LogManager
     * may only retain a weak reference.
     *
     * @param   logger the new logger.
     * @return  true if the argument logger was registered successfully,
     *          false if a logger of that name already exists.
     * @exception NullPointerException if the logger name is null.
     */
    public boolean addLogger(Logger logger) {
        final String name = logger.getName();
        if (name == null) {
            throw new NullPointerException();
        }
        return true;
    }

    /**
     * Method to find a named logger.
     * <p>
     * Note that since untrusted code may create loggers with
     * arbitrary names this method should not be relied on to
     * find Loggers for security sensitive logging.
     * It is also important to note that the Logger associated with the
     * String {@code name} may be garbage collected at any time if there
     * is no strong reference to the Logger. The caller of this method
     * must check the return value for null in order to properly handle
     * the case where the Logger has been garbage collected.
     * <p>
     * @param name name of the logger
     * @return  matching logger or null if none is found
     */
    public Logger getLogger(String name) {
        return rootLogger;
    }

    /**
     * Get an enumeration of known logger names.
     * <p>
     * Note:  Loggers may be added dynamically as new classes are loaded.
     * This method only reports on the loggers that are currently registered.
     * It is also important to note that this method only returns the name
     * of a Logger, not a strong reference to the Logger itself.
     * The returned String does nothing to prevent the Logger from being
     * garbage collected. In particular, if the returned name is passed
     * to {@code LogManager.getLogger()}, then the caller must check the
     * return value from {@code LogManager.getLogger()} for null to properly
     * handle the case where the Logger has been garbage collected in the
     * time since its name was returned by this method.
     * <p>
     * @return  enumeration of logger name strings
     */
    public Enumeration<String> getLoggerNames() {
        return Collections.emptyEnumeration();
    }

    /**
     * Reset the logging configuration.
     * <p>
     * For all named loggers, the reset operation removes and closes
     * all Handlers and (except for the root logger) sets the level
     * to null.  The root logger's level is set to Level.INFO.
     *
     * @exception  SecurityException  if a security manager exists and if
     *             the caller does not have LoggingPermission("control").
     */

    public void reset() throws SecurityException {
    }

    /**
     * Get the value of a logging property.
     * The method returns null if the property is not found.
     * @param name      property name
     * @return          property value
     */
    public String getProperty(String name) {
        return props.getProperty(name);
    }

    // Package private method to get a String property.
    // If the property is not defined we return the given
    // default value.
    String getStringProperty(String name, String defaultValue) {
        String val = getProperty(name);
        if (val == null) {
            return defaultValue;
        }
        return val.trim();
    }

    // Package private method to get an integer property.
    // If the property is not defined or cannot be parsed
    // we return the given default value.
    int getIntProperty(String name, int defaultValue) {
        String val = getProperty(name);
        if (val == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(val.trim());
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    // Package private method to get a boolean property.
    // If the property is not defined or cannot be parsed
    // we return the given default value.
    boolean getBooleanProperty(String name, boolean defaultValue) {
        String val = getProperty(name);
        if (val == null) {
            return defaultValue;
        }
        val = val.toLowerCase();
        if (val.equals("true") || val.equals("1")) {
            return true;
        } else if (val.equals("false") || val.equals("0")) {
            return false;
        }
        return defaultValue;
    }

    // Package private method to get a Level property.
    // If the property is not defined or cannot be parsed
    // we return the given default value.
    Level getLevelProperty(String name, Level defaultValue) {
        String val = getProperty(name);
        if (val == null) {
            return defaultValue;
        }
        Level l = Level.findLevel(val.trim());
        return l != null ? l : defaultValue;
    }

    // Package private method to get a filter property.
    // We return an instance of the class named by the "name"
    // property. If the property is not defined or has problems
    // we return the defaultValue.
    Filter getFilterProperty(String name, Filter defaultValue) {
        String val = getProperty(name);
        try {
            if (val != null) {
                Class<?> clz = ClassLoader.getSystemClassLoader().loadClass(val);
                return (Filter) clz.newInstance();
            }
        } catch (Exception ex) {
            // We got one of a variety of exceptions in creating the
            // class or creating an instance.
            // Drop through.
        }
        // We got an exception.  Return the defaultValue.
        return defaultValue;
    }


    // Package private method to get a formatter property.
    // We return an instance of the class named by the "name"
    // property. If the property is not defined or has problems
    // we return the defaultValue.
    Formatter getFormatterProperty(String name, Formatter defaultValue) {
        String val = getProperty(name);
        try {
            if (val != null) {
                Class<?> clz = ClassLoader.getSystemClassLoader().loadClass(val);
                return (Formatter) clz.newInstance();
            }
        } catch (Exception ex) {
            // We got one of a variety of exceptions in creating the
            // class or creating an instance.
            // Drop through.
        }
        // We got an exception.  Return the defaultValue.
        return defaultValue;
    }

    private final Permission controlPermission = new LoggingPermission("control", null);

    void checkPermission() {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null)
            sm.checkPermission(controlPermission);
    }

    /**
     * Check that the current context is trusted to modify the logging
     * configuration.  This requires LoggingPermission("control").
     * <p>
     * If the check fails we throw a SecurityException, otherwise
     * we return normally.
     *
     * @exception  SecurityException  if a security manager exists and if
     *             the caller does not have LoggingPermission("control").
     */
    public void checkAccess() throws SecurityException {
        checkPermission();
    }

    // We use a subclass of Logger for the root logger, so
    // that we only instantiate the global handlers when they
    // are first needed.
    private final class RootLogger extends Logger {
        private RootLogger() {
            // We do not call the protected Logger two args constructor here,
            // to avoid calling LogManager.getLogManager() from within the
            // RootLogger constructor.
            super("", null, null, LogManager.this, true);
        }
    }

    /**
     * A class that provides access to the java.beans.PropertyChangeListener
     * and java.beans.PropertyChangeEvent without creating a static dependency
     * on java.beans. This class can be removed once the addPropertyChangeListener
     * and removePropertyChangeListener methods are removed.
     */
    private static class Beans {
        private static final Class<?> propertyChangeListenerClass =
            getClass("java.beans.PropertyChangeListener");

        private static final Class<?> propertyChangeEventClass =
            getClass("java.beans.PropertyChangeEvent");

        private static final Method propertyChangeMethod =
            getMethod(propertyChangeListenerClass,
                      "propertyChange",
                      propertyChangeEventClass);

        private static final Constructor<?> propertyEventCtor =
            getConstructor(propertyChangeEventClass,
                           Object.class,
                           String.class,
                           Object.class,
                           Object.class);

        private static Class<?> getClass(String name) {
            try {
                return Class.forName(name, true, Beans.class.getClassLoader());
            } catch (ClassNotFoundException e) {
                return null;
            }
        }
        private static Constructor<?> getConstructor(Class<?> c, Class<?>... types) {
            try {
                return (c == null) ? null : c.getDeclaredConstructor(types);
            } catch (NoSuchMethodException x) {
                throw new AssertionError(x);
            }
        }

        private static Method getMethod(Class<?> c, String name, Class<?>... types) {
            try {
                return (c == null) ? null : c.getMethod(name, types);
            } catch (NoSuchMethodException e) {
                throw new AssertionError(e);
            }
        }

        /**
         * Returns {@code true} if java.beans is present.
         */
        static boolean isBeansPresent() {
            return propertyChangeListenerClass != null &&
                   propertyChangeEventClass != null;
        }

        /**
         * Returns a new PropertyChangeEvent with the given source, property
         * name, old and new values.
         */
        static Object newPropertyChangeEvent(Object source, String prop,
                                             Object oldValue, Object newValue)
        {
            try {
                return propertyEventCtor.newInstance(source, prop, oldValue, newValue);
            } catch (InstantiationException | IllegalAccessException x) {
                throw new AssertionError(x);
            } catch (InvocationTargetException x) {
                Throwable cause = x.getCause();
                if (cause instanceof Error)
                    throw (Error)cause;
                if (cause instanceof RuntimeException)
                    throw (RuntimeException)cause;
                throw new AssertionError(x);
            }
        }

        /**
         * Invokes the given PropertyChangeListener's propertyChange method
         * with the given event.
         */
        static void invokePropertyChange(Object listener, Object ev) {
            try {
                propertyChangeMethod.invoke(listener, ev);
            } catch (IllegalAccessException x) {
                throw new AssertionError(x);
            } catch (InvocationTargetException x) {
                Throwable cause = x.getCause();
                if (cause instanceof Error)
                    throw (Error)cause;
                if (cause instanceof RuntimeException)
                    throw (RuntimeException)cause;
                throw new AssertionError(x);
            }
        }
    }
}

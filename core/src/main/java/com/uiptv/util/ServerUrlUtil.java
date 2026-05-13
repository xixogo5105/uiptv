package com.uiptv.util;

import com.uiptv.db.DatabaseAccessException;
import com.uiptv.model.Configuration;
import com.uiptv.service.ConfigurationService;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ServerUrlUtil {
    private static final String SERVER_RUNTIME_CLASS = "com.uiptv.server.UIptvServer";

    private ServerUrlUtil() {
    }

    public static String getLocalServerUrl() {
        String port = "8888";
        try {
            ConfigurationService service = ConfigurationService.getInstance();
            if (service != null) {
                Configuration config = service.read();
                if (config != null) {
                    String configured = config.getServerPort();
                    if (configured != null && !configured.trim().isEmpty()) {
                        port = configured.trim();
                    }
                }
            }
        } catch (DatabaseAccessException | IllegalStateException _) {
            // Fall back to the default local server port when configuration cannot be read.
        }
        return "http://127.0.0.1:" + port;
    }

    public static void installServerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(ServerUrlUtil::stopServer));
    }

    public static void startServer() {
        try {
            startServerChecked();
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to start local web server", e);
        }
    }

    public static void startServerChecked() throws IOException {
        invokeServerVoid("start");
    }

    public static boolean ensureServerStarted() throws IOException {
        return invokeServerBoolean("ensureStarted");
    }

    public static boolean isServerRunning() {
        try {
            return invokeServerBooleanUnchecked("isRunning");
        } catch (IllegalStateException _) {
            return false;
        }
    }

    public static void stopServer() {
        try {
            invokeServerVoidUnchecked("stop");
        } catch (IllegalStateException _) {
            // The API server module may be absent in headless/core-only contexts.
        }
    }

    public static boolean ensureServerForWebPlayback() {
        try {
            ensureServerStarted();
            return true;
        } catch (IOException e) {
            AppLog.addErrorLog(ServerUrlUtil.class, "Unable to start local web server for playback: " + e.getMessage());
            return false;
        }
    }

    private static boolean invokeServerBoolean(String methodName) throws IOException {
        try {
            Method method = resolveServerMethod(methodName);
            Object result = method.invoke(null);
            return result instanceof Boolean booleanResult && booleanResult;
        } catch (InvocationTargetException e) {
            throw rethrowServerInvocation(e, methodName);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Local web server module is not available", e);
        }
    }

    private static boolean invokeServerBooleanUnchecked(String methodName) {
        try {
            Method method = resolveServerMethod(methodName);
            Object result = method.invoke(null);
            return result instanceof Boolean booleanResult && booleanResult;
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Unable to invoke local web server method '" + methodName + "'", e.getTargetException());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Local web server module is not available", e);
        }
    }

    private static void invokeServerVoid(String methodName) throws IOException {
        try {
            resolveServerMethod(methodName).invoke(null);
        } catch (InvocationTargetException e) {
            throw rethrowServerInvocation(e, methodName);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Local web server module is not available", e);
        }
    }

    private static void invokeServerVoidUnchecked(String methodName) {
        try {
            resolveServerMethod(methodName).invoke(null);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Unable to invoke local web server method '" + methodName + "'", e.getTargetException());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Local web server module is not available", e);
        }
    }

    private static Method resolveServerMethod(String methodName) throws ReflectiveOperationException {
        Class<?> serverClass = Class.forName(SERVER_RUNTIME_CLASS);
        return serverClass.getMethod(methodName);
    }

    private static IOException rethrowServerInvocation(InvocationTargetException e, String methodName) throws IOException {
        Throwable cause = e.getTargetException();
        if (cause instanceof IOException ioException) {
            throw ioException;
        }
        if (cause instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new IOException("Unable to invoke local web server method '" + methodName + "'", cause);
    }
}

package com.uiptv.ui;

final class SqlConnectionUiTestSupport {
    private static final String DB_PATH_PROPERTY = "uiptv.db.path";

    private SqlConnectionUiTestSupport() {
    }

    static void useDatabasePath(String path) {
        try {
            System.setProperty(DB_PATH_PROPERTY, path);
            invokeDatabasePathState("override", new Class<?>[]{String.class}, path);
            invokeSqlConnectionRuntime("close$uiptv_server");
            invokeSqlConnectionRuntime("initialize$uiptv_server");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to switch test database path", e);
        }
    }

    static void restoreConfiguredPath() {
        try {
            System.clearProperty(DB_PATH_PROPERTY);
            invokeDatabasePathState("reload", new Class<?>[0]);
            invokeSqlConnectionRuntime("close$uiptv_server");
            invokeSqlConnectionRuntime("initialize$uiptv_server");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to restore configured database path", e);
        }
    }

    static void shutdown() {
        try {
            invokeSqlConnectionRuntime("close$uiptv_server");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to shut down test database runtime", e);
        }
    }

    private static void invokeDatabasePathState(String methodName, Class<?>[] parameterTypes, Object... args)
            throws ReflectiveOperationException {
        Class<?> stateClass = Class.forName("com.uiptv.db.DatabasePathState");
        Object stateInstance = stateClass.getField("INSTANCE").get(null);
        var method = stateClass.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        method.invoke(stateInstance, args);
    }

    private static void invokeSqlConnectionRuntime(String methodName) throws ReflectiveOperationException {
        Class<?> runtimeClass = Class.forName("com.uiptv.db.SqlConnectionRuntime");
        Object runtimeInstance = runtimeClass.getField("INSTANCE").get(null);
        var method = runtimeClass.getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(runtimeInstance);
    }
}

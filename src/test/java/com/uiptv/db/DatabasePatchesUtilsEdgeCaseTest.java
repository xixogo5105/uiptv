package com.uiptv.db;

import com.uiptv.service.DbBackedTest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Provider;
import java.security.Security;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DatabasePatchesUtilsEdgeCaseTest extends DbBackedTest {

    @Test
    void directiveParsingAndSqlExecutionCoverage() throws Exception {
        Method findDirectiveLine = DatabasePatchesUtils.class.getDeclaredMethod("findDirectiveLine", String.class);
        findDirectiveLine.setAccessible(true);
        assertEquals("--@add_column TestTable name TEXT", findDirectiveLine.invoke(null,
                "-- comment\n--@add_column TestTable name TEXT\nSELECT 1;"));
        assertNull(findDirectiveLine.invoke(null, "SELECT 1;"));

        Method executeDirective = DatabasePatchesUtils.class.getDeclaredMethod("executeDirective", Connection.class, String.class);
        executeDirective.setAccessible(true);
        Method executeMigrationContent = DatabasePatchesUtils.class.getDeclaredMethod("executeMigrationContent", Connection.class, String.class);
        executeMigrationContent.setAccessible(true);
        Method executeSqlStatements = DatabasePatchesUtils.class.getDeclaredMethod("executeSqlStatements", Connection.class, String.class);
        executeSqlStatements.setAccessible(true);
        Method columnExists = DatabasePatchesUtils.class.getDeclaredMethod("columnExists", Connection.class, String.class, String.class);
        columnExists.setAccessible(true);

        try (Connection conn = SQLConnection.connect(); Statement st = conn.createStatement()) {
            st.executeUpdate("DROP TABLE IF EXISTS TestTable");
            st.executeUpdate("CREATE TABLE TestTable (id INTEGER PRIMARY KEY)");

            executeDirective.invoke(null, conn, "--@add_column TestTable name TEXT");
            assertTrue((Boolean) columnExists.invoke(null, conn, "TestTable", "name"));

            executeDirective.invoke(null, conn, "--@add_column TestTable name TEXT");

            Connection mockConn = Mockito.mock(Connection.class);
            Statement mockStatement = Mockito.mock(Statement.class);
            ResultSet mockResultSet = Mockito.mock(ResultSet.class);
            Mockito.when(mockConn.createStatement()).thenReturn(mockStatement);
            Mockito.when(mockStatement.executeQuery(Mockito.anyString())).thenReturn(mockResultSet);
            Mockito.when(mockResultSet.next()).thenReturn(true, false);
            Mockito.when(mockResultSet.getString("name")).thenReturn("name");
            executeDirective.invoke(null, mockConn, "--@drop_column TestTable name");
            Mockito.verify(mockStatement).executeUpdate("ALTER TABLE TestTable DROP COLUMN name");

            executeDirective.invoke(null, conn, "--@drop_column TestTable name");

            InvocationTargetException invalid = assertThrows(InvocationTargetException.class,
                    () -> executeDirective.invoke(null, conn, "--@add_column only-two"));
            assertInstanceOf(SQLException.class, invalid.getCause());

            InvocationTargetException unsupported = assertThrows(InvocationTargetException.class,
                    () -> executeDirective.invoke(null, conn, "--@rename_table TestTable Other"));
            assertInstanceOf(SQLException.class, unsupported.getCause());

            String sql = """
                    -- comment
                    CREATE TABLE TestSql (id INTEGER PRIMARY KEY);
                    
                    INSERT INTO TestSql(id) VALUES (1);
                    """;
            executeMigrationContent.invoke(null, conn, sql);
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM TestSql")) {
                rs.next();
                assertEquals(1, rs.getInt(1));
            }

            executeSqlStatements.invoke(null, conn, "INSERT INTO TestSql(id) VALUES (2);;\n-- skip\n");
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM TestSql")) {
                rs.next();
                assertEquals(2, rs.getInt(1));
            }

            assertFalse((Boolean) columnExists.invoke(null, conn, "TestSql", "missing"));
        }
    }

    @Test
    void resourceHandlingChecksumAndSafeErrorCoverage() throws Exception {
        Method openResource = DatabasePatchesUtils.class.getDeclaredMethod("openResource", String.class);
        openResource.setAccessible(true);
        Method readResource = DatabasePatchesUtils.class.getDeclaredMethod("readResource", String.class);
        readResource.setAccessible(true);
        Method resourceExists = DatabasePatchesUtils.class.getDeclaredMethod("resourceExists", String.class);
        resourceExists.setAccessible(true);
        Method checksum = DatabasePatchesUtils.class.getDeclaredMethod("checksum", String.class);
        checksum.setAccessible(true);
        Method safeError = DatabasePatchesUtils.class.getDeclaredMethod("safeError", Exception.class);
        safeError.setAccessible(true);
        Method readMigrationNames = DatabasePatchesUtils.class.getDeclaredMethod("readMigrationNames");
        readMigrationNames.setAccessible(true);

        try (InputStream in = (InputStream) openResource.invoke(null, "/db/migrations/migrations.txt")) {
            assertNotNull(in);
        }

        ClassLoader original = Thread.currentThread().getContextClassLoader();
        ClassLoader contextLoader = new ClassLoader(original) {
            @Override
            public InputStream getResourceAsStream(String name) {
                if ("context-only.txt".equals(name)) {
                    return new ByteArrayInputStream("context".getBytes(StandardCharsets.UTF_8));
                }
                return super.getResourceAsStream(name);
            }
        };

        Thread.currentThread().setContextClassLoader(contextLoader);
        try (InputStream in = (InputStream) openResource.invoke(null, "context-only.txt")) {
            assertNotNull(in);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }

        Path localFile = Path.of("src", "main", "resources", "local-only.txt");
        Files.writeString(localFile, "local");
        Thread.currentThread().setContextClassLoader(null);
        try (InputStream in = (InputStream) openResource.invoke(null, "local-only.txt")) {
            assertNotNull(in);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
            Files.deleteIfExists(localFile);
        }

        Path localDir = Path.of("src", "main", "resources", "local-only-dir");
        Files.createDirectories(localDir);
        Thread.currentThread().setContextClassLoader(null);
        try {
            assertNull(openResource.invoke(null, "local-only-dir"));
        } finally {
            Thread.currentThread().setContextClassLoader(original);
            Files.deleteIfExists(localDir);
        }

        assertFalse((Boolean) resourceExists.invoke(null, (Object) null));

        InvocationTargetException missing = assertThrows(InvocationTargetException.class,
                () -> readResource.invoke(null, "missing-resource.txt"));
        assertInstanceOf(IllegalStateException.class, missing.getCause());

        String content = (String) readResource.invoke(null, "db/migrations/migrations.txt");
        assertTrue(content.contains("0000_baseline.sql"));

        String digest = (String) checksum.invoke(null, "test");
        assertEquals(64, digest.length());

        Provider[] providers = Security.getProviders();
        for (Provider provider : providers) {
            Security.removeProvider(provider.getName());
        }
        try {
            InvocationTargetException checksumFailure = assertThrows(InvocationTargetException.class,
                    () -> checksum.invoke(null, "test"));
            assertInstanceOf(IllegalStateException.class, checksumFailure.getCause());
        } finally {
            int pos = 1;
            for (Provider provider : providers) {
                Security.insertProviderAt(provider, pos++);
            }
        }

        assertEquals("", safeError.invoke(null, new Object[]{null}));
        assertEquals("RuntimeException", safeError.invoke(null, new RuntimeException((String) null)));
        assertEquals("RuntimeException", safeError.invoke(null, new RuntimeException("  ")));
        String longMessage = "x".repeat(1100);
        String truncated = (String) safeError.invoke(null, new RuntimeException(longMessage));
        assertEquals(1000, truncated.length());
        assertEquals("ok", safeError.invoke(null, new RuntimeException("ok")));

        Path migrations = Path.of(DatabasePatchesUtils.class.getClassLoader()
                .getResource("db/migrations/migrations.txt").toURI());
        String originalContent = Files.readString(migrations);
        try {
            Files.writeString(migrations, originalContent + "\n#comment\n\n");
            List<String> names = (List<String>) readMigrationNames.invoke(null);
            assertTrue(names.contains("0000_baseline.sql"));
            assertEquals(originalContent.lines().filter(l -> !l.isBlank()).count(), names.size());
        } finally {
            Files.writeString(migrations, originalContent);
        }
    }
}

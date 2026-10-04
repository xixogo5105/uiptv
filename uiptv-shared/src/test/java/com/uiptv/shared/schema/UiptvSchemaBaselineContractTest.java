package com.uiptv.shared.schema;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the schema declared in {@link UiptvSchema} against
 * {@code core/src/main/resources/db/migrations/0000_baseline.sql}.
 *
 * <p>{@code 0000_baseline.sql} is the artifact every platform already shares: core loads it from the
 * classpath, the Android app repackages it as an asset, and {@code DatabaseUtils.dbStructure} mirrors it
 * under {@code BaselineSchemaCompatibilityTest}. This module declared its own copy of the column lists and
 * drifted by ten Configuration columns while still passing its own tests, because nothing compared the two.
 * Deriving the expectation from the baseline means the next schema change fails here first.
 */
class UiptvSchemaBaselineContractTest {

    private static final Pattern CREATE_TABLE = Pattern.compile(
            "CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?[\"`\\[]?(\\w+)[\"`\\]]?\\s*\\(",
            Pattern.CASE_INSENSITIVE);

    private static final Set<String> TABLE_LEVEL_KEYWORDS =
            Set.of("PRIMARY", "FOREIGN", "UNIQUE", "CHECK", "CONSTRAINT", "KEY");

    @Test
    void declaredColumnsMatchTheBaselineForEveryDeclaredTable() {
        Map<String, String> bodies = baselineTableBodies();

        for (UiptvTable table : UiptvTable.values()) {
            String body = bodies.get(table.tableName());
            assertTrue(body != null, table.tableName() + " is not declared in 0000_baseline.sql");

            assertEquals(
                    baselineColumns(body),
                    names(UiptvSchema.columnsFor(table)),
                    "Column drift between UiptvSchema and 0000_baseline.sql for " + table.tableName());
        }
    }

    @Test
    void everyBaselineTableIsRepresentedInTheContract() {
        Map<String, String> bodies = baselineTableBodies();

        List<String> missing = new ArrayList<>();
        for (String table : bodies.keySet()) {
            boolean declared = false;
            for (UiptvTable candidate : UiptvTable.values()) {
                if (candidate.tableName().equals(table)) {
                    declared = true;
                    break;
                }
            }
            if (!declared) {
                missing.add(table);
            }
        }

        assertEquals(List.of(), missing, "Baseline tables absent from UiptvTable");
    }

    @Test
    void androidPortableColumnsAreASubsetOfTheDeclaredConfigurationColumns() {
        Set<String> declared = Set.copyOf(names(UiptvSchema.columnsFor(UiptvTable.CONFIGURATION)));

        Set<String> undeclared = new LinkedHashSet<>(UiptvSchema.ANDROID_PORTABLE_CONFIGURATION_COLUMNS);
        undeclared.removeAll(declared);
        assertEquals(Set.of(), undeclared,
                "ANDROID_PORTABLE_CONFIGURATION_COLUMNS names columns the contract does not declare");

        Set<String> overlap = new LinkedHashSet<>(UiptvSchema.ANDROID_PORTABLE_CONFIGURATION_COLUMNS);
        overlap.retainAll(UiptvSchema.ANDROID_NEVER_SYNC_CONFIGURATION_COLUMNS);
        assertEquals(Set.of(), overlap,
                "A column cannot be both portable and never synced");
    }

    @Test
    void schemaVersionMatchesTheLastMigration() {
        List<String> migrations = readMigrations().stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList();

        String last = migrations.get(migrations.size() - 1);
        String version = last.substring(0, last.indexOf('_'));

        assertEquals(version, UiptvMigrationInfo.CURRENT_SCHEMA_VERSION);
        assertEquals(version, UiptvSchema.CURRENT_SCHEMA_VERSION);
    }

    private List<String> names(List<DataColumn> columns) {
        List<String> names = new ArrayList<>();
        for (DataColumn column : columns) {
            names.add(column.name());
        }
        return names;
    }

    private static List<String> baselineColumns(String body) {
        List<String> columns = new ArrayList<>();
        for (String entry : splitTopLevel(body)) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String first = trimmed.split("\\s+")[0];
            if (TABLE_LEVEL_KEYWORDS.contains(first.toUpperCase())) {
                continue;
            }
            columns.add(unquote(first));
        }
        return columns;
    }

    /** Splits a table body on commas at nesting depth zero, ignoring commas inside quotes or parentheses. */
    private static List<String> splitTopLevel(String body) {
        List<String> entries = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        char quote = 0;
        for (int i = 0; i < body.length(); i++) {
            char ch = body.charAt(i);
            if (quote != 0) {
                current.append(ch);
                if (ch == quote) {
                    quote = 0;
                }
            } else if (ch == '\'' || ch == '"' || ch == '`') {
                quote = ch;
                current.append(ch);
            } else if (ch == '(') {
                depth++;
                current.append(ch);
            } else if (ch == ')') {
                depth--;
                current.append(ch);
            } else if (ch == ',' && depth == 0) {
                entries.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        if (!current.toString().isBlank()) {
            entries.add(current.toString());
        }
        return entries;
    }

    private static Map<String, String> baselineTableBodies() {
        String sql = readBaseline();
        Map<String, String> bodies = new LinkedHashMap<>();
        Matcher matcher = CREATE_TABLE.matcher(sql);
        while (matcher.find()) {
            int open = matcher.end() - 1;
            int depth = 0;
            char quote = 0;
            int i = open;
            while (i < sql.length()) {
                char ch = sql.charAt(i);
                if (quote != 0) {
                    if (ch == quote) {
                        quote = 0;
                    }
                } else if (ch == '\'' || ch == '"' || ch == '`') {
                    quote = ch;
                } else if (ch == '(') {
                    depth++;
                } else if (ch == ')') {
                    depth--;
                    if (depth == 0) {
                        break;
                    }
                }
                i++;
            }
            if (i >= sql.length()) {
                throw new IllegalStateException("Unbalanced parentheses in 0000_baseline.sql at " + open);
            }
            bodies.put(matcher.group(1), sql.substring(open + 1, i));
        }
        if (bodies.isEmpty()) {
            throw new IllegalStateException("No CREATE TABLE statements parsed from 0000_baseline.sql");
        }
        return bodies;
    }

    private static String unquote(String identifier) {
        if (identifier.length() >= 2) {
            char first = identifier.charAt(0);
            char last = identifier.charAt(identifier.length() - 1);
            if ((first == '"' && last == '"') || (first == '`' && last == '`') || (first == '[' && last == ']')) {
                return identifier.substring(1, identifier.length() - 1);
            }
        }
        return identifier;
    }

    private static List<String> readMigrations() {
        try (InputStream in = open("/db/migrations/migrations.txt")) {
            List<String> lines = new ArrayList<>();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            return lines;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read migrations.txt", e);
        }
    }

    private static String readBaseline() {
        try (InputStream in = open("/db/migrations/0000_baseline.sql")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read 0000_baseline.sql", e);
        }
    }

    private static InputStream open(String path) throws IOException {
        InputStream in = UiptvSchemaBaselineContractTest.class.getResourceAsStream(path);
        if (in == null) {
            throw new IllegalStateException(path + " is not on the test classpath");
        }
        return in;
    }
}
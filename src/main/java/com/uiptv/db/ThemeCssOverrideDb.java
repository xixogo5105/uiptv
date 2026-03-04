package com.uiptv.db;

import com.uiptv.model.ThemeCssOverride;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static com.uiptv.db.DatabaseUtils.DbTable.THEME_CSS_OVERRIDE_TABLE;
import static com.uiptv.db.DatabaseUtils.insertTableSql;
import static com.uiptv.db.DatabaseUtils.updateTableSql;
import static com.uiptv.db.SQLConnection.connect;

public class ThemeCssOverrideDb extends BaseDb {
    private static ThemeCssOverrideDb instance;

    public static synchronized ThemeCssOverrideDb get() {
        if (instance == null) {
            instance = new ThemeCssOverrideDb();
        }
        return instance;
    }

    private ThemeCssOverrideDb() {
        super(THEME_CSS_OVERRIDE_TABLE);
    }

    @Override
    ThemeCssOverride populate(ResultSet resultSet) {
        ThemeCssOverride override = new ThemeCssOverride();
        override.setDbId(nullSafeString(resultSet, "id"));
        override.setLightThemeCssName(nullSafeString(resultSet, "lightThemeCssName"));
        override.setLightThemeCssContent(nullSafeString(resultSet, "lightThemeCssContent"));
        override.setDarkThemeCssName(nullSafeString(resultSet, "darkThemeCssName"));
        override.setDarkThemeCssContent(nullSafeString(resultSet, "darkThemeCssContent"));
        override.setUpdatedAt(nullSafeString(resultSet, "updatedAt"));
        return override;
    }

    public ThemeCssOverride read() {
        List<ThemeCssOverride> overrides = super.getAll();
        return overrides != null && !overrides.isEmpty() ? overrides.get(0) : new ThemeCssOverride();
    }

    public void save(ThemeCssOverride override) {
        ThemeCssOverride sanitized = override == null ? new ThemeCssOverride() : override;
        if (sanitized.getUpdatedAt() == null || sanitized.getUpdatedAt().isBlank()) {
            sanitized.setUpdatedAt(String.valueOf(System.currentTimeMillis()));
        }

        List<ThemeCssOverride> existing = super.getAll();
        if (existing != null && !existing.isEmpty()) {
            ThemeCssOverride current = existing.get(0);
            String updateQuery = updateTableSql(THEME_CSS_OVERRIDE_TABLE);
            try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(updateQuery)) {
                setParameters(statement, sanitized);
                statement.setString(6, current.getDbId());
                statement.execute();
            } catch (SQLException e) {
                throw new RuntimeException("Unable to execute update query", e);
            }
            return;
        }

        String insertQuery = insertTableSql(THEME_CSS_OVERRIDE_TABLE);
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(insertQuery)) {
            setParameters(statement, sanitized);
            statement.execute();
        } catch (SQLException e) {
            throw new RuntimeException("Unable to execute insert query", e);
        }
    }

    private void setParameters(PreparedStatement statement, ThemeCssOverride override) throws SQLException {
        statement.setString(1, override.getLightThemeCssName());
        statement.setString(2, override.getLightThemeCssContent());
        statement.setString(3, override.getDarkThemeCssName());
        statement.setString(4, override.getDarkThemeCssContent());
        statement.setString(5, override.getUpdatedAt());
    }
}

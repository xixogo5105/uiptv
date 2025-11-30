package com.uiptv.db;

import com.uiptv.api.JsonCompliant;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.uiptv.db.DatabaseUtils.*;
import static com.uiptv.db.SQLConnection.connect;
import static com.uiptv.util.StringUtils.SPACE;
import static com.uiptv.util.StringUtils.isBlank;

public abstract class BaseDb {
    private DatabaseUtils.DbTable table;

    public BaseDb(DatabaseUtils.DbTable table) {
        this.table = table;
    }

    abstract <T extends JsonCompliant> T populate(ResultSet resultSet);

    public <T extends JsonCompliant> List<T> getAll(String extendedSql, String[] parameters) {
        ArrayList<T> t = new ArrayList<>();
        String sql = selectAllSql(table) + SPACE + extendedSql;
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql)) {
            AtomicInteger i = new AtomicInteger(1);
            Arrays.stream(parameters).forEach(s -> {
                try {
                    statement.setString(Integer.valueOf(i.getAndIncrement()), s);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                t.add(populate(resultSet));
            }
            resultSet.close();
        } catch (SQLException e) {
            throw new RuntimeException("Unable to execute query");
        }
        return t;
    }

    public <T extends JsonCompliant> List<T> getAll() {
        return getAll("", new String[]{});
    }

    public <T extends JsonCompliant> T getById(String id) {
        return getById(id, "");
    }

    public <T extends JsonCompliant> T getById(String id, String extendedSql) {
        T t = null;
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(selectByIdSql(table, id) + extendedSql)) {
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                t = populate(resultSet);
            }
            resultSet.close();
        } catch (SQLException e) {
            throw new RuntimeException("Unable to execute query");
        }
        return t;
    }

    public void delete(String id) {
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(deleteByIdSql(table, id))) {
            statement.execute();
        } catch (SQLException e) {
            throw new RuntimeException("Unable to execute delete query");
        }
    }


    public String nullSafeString(ResultSet resultSet, String column) {
        try {
            return resultSet.getString(column);
        } catch (SQLException e) {
            return null;
        }
    }

    public static int safeInteger(ResultSet resultSet, String column) {
        try {
            return isBlank(resultSet.getString(column)) ? 0 : Integer.parseInt(resultSet.getString(column));
        } catch (SQLException e) {
            return 0;
        }
    }

    public static boolean safeBoolean(ResultSet resultSet, String column) {
        try {
            if(isBlank(resultSet.getString(column))) return false;
            return (Integer.parseInt(resultSet.getString(column)) > 0);
        } catch (SQLException ignored) {
            return false;
        }
    }

}

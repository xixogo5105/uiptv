package com.uiptv.db;

import com.uiptv.application.EpgNameRules;
import java.sql.*;
import java.util.*;

/** Read rules once per query/batch; never query the database once per channel or token. */
public final class EpgNameRulesDb {
    public EpgNameRules read() {
        try (Connection connection = SQLConnection.connect()) { return read(connection); }
        catch (SQLException error) { throw new DatabaseAccessException("Unable to read EPG name exclusions", error); }
    }
    public EpgNameRules read(Connection connection) throws SQLException {
        List<EpgNameRules.Rule> rules = new ArrayList<>();
        try (var statement = connection.prepareStatement("SELECT term,scope FROM epg_name_exclusion WHERE enabled=1 ORDER BY term,scope");
             var rows = statement.executeQuery()) {
            while (rows.next()) {
                String term = rows.getString(1);
                String scope = rows.getString(2);
                if (term != null && !term.isBlank() && term.length() <= 128 && Set.of("annotation","quality").contains(scope))
                    rules.add(new EpgNameRules.Rule(term,scope));
            }
        }
        return new EpgNameRules(rules);
    }
}

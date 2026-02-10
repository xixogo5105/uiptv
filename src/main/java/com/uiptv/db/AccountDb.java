package com.uiptv.db;

import com.uiptv.model.Account;
import com.uiptv.util.AccountType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static com.uiptv.db.DatabaseUtils.DbTable.ACCOUNT_TABLE;
import static com.uiptv.db.DatabaseUtils.insertTableSql;
import static com.uiptv.db.DatabaseUtils.updateTableSql;
import static com.uiptv.db.SQLConnection.connect;
import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;

public class AccountDb extends BaseDb {
    private static AccountDb instance;


    public static synchronized AccountDb get() {
        if (instance == null) {
            instance = new AccountDb();
        }
        return instance;
    }

    public AccountDb() {
        super(ACCOUNT_TABLE);
    }

    @Override
    Account populate(ResultSet resultSet) {
        Account account = new Account(nullSafeString(resultSet, "accountName"), nullSafeString(resultSet, "username"), nullSafeString(resultSet, "password"), nullSafeString(resultSet, "url"), nullSafeString(resultSet, "macAddress"),nullSafeString(resultSet, "macAddressList"), nullSafeString(resultSet, "serialNumber"), nullSafeString(resultSet, "deviceId1"), nullSafeString(resultSet, "deviceId2"), nullSafeString(resultSet, "signature"), isNotBlank(nullSafeString(resultSet, "type")) ? AccountType.valueOf(nullSafeString(resultSet, "type")) : AccountType.STALKER_PORTAL, nullSafeString(resultSet, "epg"), nullSafeString(resultSet, "m3u8Path"), safeBoolean(resultSet, "pinToTop"));
        account.setDbId(nullSafeString(resultSet, "id"));
        account.setServerPortalUrl(nullSafeString(resultSet, "serverPortalUrl"));
        return account;
    }

    public List<Account> getAccounts() {
        return getAll("order by pinToTop desc, id", new String[]{});
    }

    public Account getAccountById(String id) {
        return getById(id);
    }

    public Account getAccountByName(String accountName) {
        List<Account> accounts = getAll(" WHERE accountName=?", new String[]{accountName});
        return accounts != null && !accounts.isEmpty() ? accounts.get(0) : null;
    }

    public void save(Account account) {
        Account dbAccount = getAccountByName(account.getAccountName());
        boolean accountExist = dbAccount != null;
        String saveQuery = accountExist ? updateTableSql(ACCOUNT_TABLE) : insertTableSql(ACCOUNT_TABLE);
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(saveQuery)) {
            statement.setString(1, account.getAccountName());
            statement.setString(2, account.getUsername());
            statement.setString(3, account.getPassword());
            statement.setString(4, account.getUrl());
            statement.setString(5, account.getMacAddress());
            statement.setString(6, account.getMacAddressList());
            statement.setString(7, account.getSerialNumber());
            statement.setString(8, account.getDeviceId1());
            statement.setString(9, account.getDeviceId2());
            statement.setString(10, account.getSignature());
            statement.setString(11, account.getEpg());
            statement.setString(12, account.getM3u8Path());
            statement.setString(13, account.getType().name());
            statement.setString(14, account.getServerPortalUrl());
            statement.setString(15, account.isPinToTop() ? "1" : "0");

            if (accountExist) {
                statement.setInt(16, Integer.valueOf(dbAccount.getDbId()));
            }
            statement.execute();
        } catch (SQLException e) {
            throw new RuntimeException("Unable to execute query");
        }
    }

    public void saveServerPortalUrl(Account account) {
        if (isBlank(account.getDbId())) {
            return;
        }
        save(account);
    }
}

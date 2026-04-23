package com.uiptv.db;

import com.uiptv.model.AccountInfo;
import com.uiptv.model.AccountStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static com.uiptv.db.DatabaseUtils.DbTable.ACCOUNT_INFO_TABLE;
import static com.uiptv.db.DatabaseUtils.insertTableSql;
import static com.uiptv.db.SQLConnection.connect;
import static com.uiptv.util.StringUtils.isBlank;

public class AccountInfoDb extends BaseDb {
    private static AccountInfoDb instance;

    public static synchronized AccountInfoDb get() {
        if (instance == null) {
            instance = new AccountInfoDb();
        }
        return instance;
    }

    public AccountInfoDb() {
        super(ACCOUNT_INFO_TABLE);
    }

    @Override
    AccountInfo populate(ResultSet resultSet) {
        AccountInfo info = new AccountInfo();
        info.setDbId(nullSafeString(resultSet, "id"));
        info.setAccountId(nullSafeString(resultSet, "accountId"));
        info.setExpireDate(nullSafeString(resultSet, "expireDate"));
        info.setAccountStatus(AccountStatus.fromValue(nullSafeString(resultSet, "accountStatus")));
        info.setAccountBalance(nullSafeString(resultSet, "accountBalance"));
        info.setTariffName(nullSafeString(resultSet, "tariffName"));
        info.setTariffPlan(nullSafeString(resultSet, "tariffPlan"));
        info.setDefaultTimezone(nullSafeString(resultSet, "defaultTimezone"));
        info.setProfileJson(nullSafeString(resultSet, "profileJson"));
        info.setPassHash(nullSafeString(resultSet, "passHash"));
        info.setParentPasswordHash(nullSafeString(resultSet, "parentPasswordHash"));
        info.setPasswordHash(nullSafeString(resultSet, "passwordHash"));
        info.setSettingsPasswordHash(nullSafeString(resultSet, "settingsPasswordHash"));
        info.setAccountPagePasswordHash(nullSafeString(resultSet, "accountPagePasswordHash"));
        info.setAllowedStbTypesJson(nullSafeString(resultSet, "allowedStbTypesJson"));
        info.setAllowedStbTypesForLocalRecordingJson(nullSafeString(resultSet, "allowedStbTypesForLocalRecordingJson"));
        info.setPreferredStbType(nullSafeString(resultSet, "preferredStbType"));
        return info;
    }

    public AccountInfo getByAccountId(String accountId) {
        if (isBlank(accountId)) {
            return null;
        }
        List<AccountInfo> infos = getAll(" WHERE accountId=?", new String[]{accountId});
        return infos != null && !infos.isEmpty() ? infos.get(0) : null;
    }

    public void save(AccountInfo info) {
        if (info == null || isBlank(info.getAccountId())) {
            return;
        }
        AccountInfo existing = getByAccountId(info.getAccountId());
        if (existing == null) {
            insert(info);
        } else {
            update(info);
        }
    }

    public void deleteByAccountId(String accountId) {
        if (isBlank(accountId)) {
            return;
        }
        String sql = "DELETE FROM " + ACCOUNT_INFO_TABLE.getTableName() + " WHERE accountId=?";
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, accountId);
            statement.executeUpdate();
        } catch (SQLException sqlException) {
            throw new IllegalStateException("Unable to execute delete query", sqlException);
        }
    }

    private void insert(AccountInfo info) {
        String insertQuery = insertTableSql(ACCOUNT_INFO_TABLE);
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(insertQuery)) {
            statement.setString(1, info.getAccountId());
            statement.setString(2, info.getExpireDate());
            statement.setString(3, statusToString(info.getAccountStatus()));
            statement.setString(4, info.getAccountBalance());
            statement.setString(5, info.getTariffName());
            statement.setString(6, info.getTariffPlan());
            statement.setString(7, info.getDefaultTimezone());
            statement.setString(8, info.getProfileJson());
            statement.setString(9, info.getPassHash());
            statement.setString(10, info.getParentPasswordHash());
            statement.setString(11, info.getPasswordHash());
            statement.setString(12, info.getSettingsPasswordHash());
            statement.setString(13, info.getAccountPagePasswordHash());
            statement.setString(14, info.getAllowedStbTypesJson());
            statement.setString(15, info.getAllowedStbTypesForLocalRecordingJson());
            statement.setString(16, info.getPreferredStbType());
            statement.execute();
        } catch (SQLException e) {
            throw new DatabaseAccessException("Unable to execute query", e);
        }
    }

    private void update(AccountInfo info) {
        String sql = "UPDATE " + ACCOUNT_INFO_TABLE.getTableName()
                + " SET expireDate=?, accountStatus=?, accountBalance=?, tariffName=?, tariffPlan=?, defaultTimezone=?, profileJson=?,"
                + " passHash=?, parentPasswordHash=?, passwordHash=?, settingsPasswordHash=?, accountPagePasswordHash=?"
                + ", allowedStbTypesJson=?, allowedStbTypesForLocalRecordingJson=?, preferredStbType=?"
                + " WHERE accountId=?";
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, info.getExpireDate());
            statement.setString(2, statusToString(info.getAccountStatus()));
            statement.setString(3, info.getAccountBalance());
            statement.setString(4, info.getTariffName());
            statement.setString(5, info.getTariffPlan());
            statement.setString(6, info.getDefaultTimezone());
            statement.setString(7, info.getProfileJson());
            statement.setString(8, info.getPassHash());
            statement.setString(9, info.getParentPasswordHash());
            statement.setString(10, info.getPasswordHash());
            statement.setString(11, info.getSettingsPasswordHash());
            statement.setString(12, info.getAccountPagePasswordHash());
            statement.setString(13, info.getAllowedStbTypesJson());
            statement.setString(14, info.getAllowedStbTypesForLocalRecordingJson());
            statement.setString(15, info.getPreferredStbType());
            statement.setString(16, info.getAccountId());
            statement.execute();
        } catch (SQLException e) {
            throw new DatabaseAccessException("Unable to update account info", e);
        }
    }

    private String statusToString(AccountStatus status) {
        return status != null ? status.name() : null;
    }
}

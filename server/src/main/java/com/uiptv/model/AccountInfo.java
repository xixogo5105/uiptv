package com.uiptv.model;

import com.uiptv.shared.BaseJson;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class AccountInfo extends BaseJson {
    private String dbId;
    private String accountId;
    private String expireDate;
    private AccountStatus accountStatus;
    private String accountBalance;
    private String tariffName;
    private String tariffPlan;
    private String defaultTimezone;
    private String profileJson;
    private String passHash;
    private String parentPasswordHash;
    private String passwordHash;
    private String settingsPasswordHash;
    private String accountPagePasswordHash;
    private String allowedStbTypesJson;
    private String allowedStbTypesForLocalRecordingJson;
    private String preferredStbType;
}

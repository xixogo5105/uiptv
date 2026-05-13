package com.uiptv.model;

import com.uiptv.shared.BaseJson;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class VodWatchState extends BaseJson {
    private String dbId;
    private String accountId;
    private String categoryId;
    private String vodId;
    private String vodName;
    private String vodCmd;
    private String vodLogo;
    private long updatedAt;
}

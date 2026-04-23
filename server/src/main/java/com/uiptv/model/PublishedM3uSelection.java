package com.uiptv.model;

import com.uiptv.shared.BaseJson;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class PublishedM3uSelection extends BaseJson {
    private String dbId;
    private String accountId;

    public PublishedM3uSelection(String accountId) {
        this.accountId = accountId;
    }
}

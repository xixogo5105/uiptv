package com.uiptv.model;

import com.uiptv.shared.BaseJson;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class PublishedM3uCategorySelection extends BaseJson {
    private String dbId;
    private String accountId;
    private String categoryName;
    private boolean selected;

    public PublishedM3uCategorySelection(String accountId, String categoryName, boolean selected) {
        this.accountId = accountId;
        this.categoryName = categoryName;
        this.selected = selected;
    }
}

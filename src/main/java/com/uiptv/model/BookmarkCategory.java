package com.uiptv.model;

import com.uiptv.shared.BaseJson;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class BookmarkCategory extends BaseJson {
    private String id;
    private String name;

    public BookmarkCategory(String id, String name) {
        this.id = id;
        this.name = name;
    }
}

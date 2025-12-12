package com.uiptv.model;

import com.uiptv.shared.BaseJson;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class BookmarkCategory extends BaseJson {
    private String id;
    private String name;

    public BookmarkCategory(String id, String name) {
        this.id = id;
        this.name = name;
    }
}

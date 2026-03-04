package com.uiptv.model;

import com.uiptv.shared.BaseJson;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ThemeCssOverride extends BaseJson {
    private String dbId;
    private String lightThemeCssName;
    private String lightThemeCssContent;
    private String darkThemeCssName;
    private String darkThemeCssContent;
    private String updatedAt;
}

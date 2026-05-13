package com.uiptv.model;

import com.uiptv.shared.BaseJson;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class ThemeCssOverride extends BaseJson {
    private String dbId;
    private String lightThemeCssName;
    private String lightThemeCssContent;
    private String darkThemeCssName;
    private String darkThemeCssContent;
    private String updatedAt;
}

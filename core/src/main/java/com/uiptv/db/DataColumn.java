package com.uiptv.db;

public class DataColumn {
    private String columnName;
    private String typeAndDefault;

    public DataColumn(String columnName, String typeAndDefault) {
        this.columnName = columnName;
        this.typeAndDefault = typeAndDefault;
    }

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public String getTypeAndDefault() {
        return typeAndDefault;
    }

    public void setTypeAndDefault(String typeAndDefault) {
        this.typeAndDefault = typeAndDefault;
    }
}

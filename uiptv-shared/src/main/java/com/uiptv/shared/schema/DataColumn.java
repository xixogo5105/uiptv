package com.uiptv.shared.schema;

public record DataColumn(String name, String typeAndDefault) {
    public DataColumn {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Column name is required");
        }
        typeAndDefault = typeAndDefault == null ? "" : typeAndDefault;
    }
}

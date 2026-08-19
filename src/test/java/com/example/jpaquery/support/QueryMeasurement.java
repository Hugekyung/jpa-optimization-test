package com.example.jpaquery.support;

import java.util.List;

public record QueryMeasurement(
    String strategy,
    int resultCount,
    long queryCount,
    List<String> sqlStatements
) {

    public boolean containsJoin() {
        return sqlStatements.stream()
            .map(String::toUpperCase)
            .anyMatch(sql -> sql.contains(" JOIN "));
    }

    public boolean containsInClause() {
        return sqlStatements.stream()
            .map(String::toUpperCase)
            .anyMatch(sql -> sql.contains(" IN ("));
    }

    public String sqlSummary() {
        return sqlStatements.stream()
            .map(String::strip)
            .reduce((left, right) -> left + " | " + right)
            .orElse("-");
    }
}


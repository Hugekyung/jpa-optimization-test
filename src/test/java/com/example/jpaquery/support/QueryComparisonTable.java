package com.example.jpaquery.support;

import java.util.List;

public final class QueryComparisonTable {

    private QueryComparisonTable() {
    }

    public static void print(List<QueryMeasurement> measurements) {
        System.out.println("\n[Query Comparison]");
        System.out.println("| 방식 | 결과 건수 | Query 횟수 | SQL 횟수 | JOIN | IN (...) |");
        System.out.println("|---|---:|---:|---:|:---:|:---:|");
        measurements.forEach(measurement -> System.out.printf(
            "| %s | %d | %d | %d | %s | %s |%n",
            measurement.strategy(),
            measurement.resultCount(),
            measurement.queryCount(),
            measurement.sqlStatements().size(),
            measurement.containsJoin() ? "Y" : "N",
            measurement.containsInClause() ? "Y" : "N"
        ));

        System.out.println("\n[SQL]");
        measurements.forEach(measurement -> System.out.printf(
            "- %s: %s%n",
            measurement.strategy(),
            measurement.sqlSummary()
        ));
    }
}


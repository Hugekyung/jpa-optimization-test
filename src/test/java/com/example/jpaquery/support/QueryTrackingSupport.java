package com.example.jpaquery.support;

import com.example.jpaquery.config.SqlQueryTracker;
import org.hibernate.stat.Statistics;

import java.util.List;

public final class QueryTrackingSupport {

    private QueryTrackingSupport() {
    }

    public static void resetTracking(Statistics statistics) {
        statistics.clear();
        SqlQueryTracker.clear();
    }

    public static QueryMeasurement snapshot(
        String strategy,
        int resultCount,
        Statistics statistics,
        long startedAt
    ) {
        return new QueryMeasurement(
            strategy,
            resultCount,
            statistics.getQueryExecutionCount(),
            System.nanoTime() - startedAt,
            SqlQueryTracker.snapshot()
        );
    }

    public static long countSql(List<String> sqlStatements, String fragment) {
        return sqlStatements.stream()
            .map(String::toLowerCase)
            .filter(sql -> sql.contains(fragment))
            .count();
    }
}

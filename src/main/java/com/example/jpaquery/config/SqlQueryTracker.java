package com.example.jpaquery.config;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SqlQueryTracker implements StatementInspector {

    private static final ConcurrentLinkedQueue<String> SQL_STATEMENTS = new ConcurrentLinkedQueue<>();

    @Override
    public String inspect(String sql) {
        SQL_STATEMENTS.add(sql);
        return sql;
    }

    public static void clear() {
        SQL_STATEMENTS.clear();
    }

    public static List<String> snapshot() {
        return List.copyOf(SQL_STATEMENTS);
    }
}


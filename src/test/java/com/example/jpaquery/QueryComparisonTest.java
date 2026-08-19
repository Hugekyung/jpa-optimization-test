package com.example.jpaquery;

import com.example.jpaquery.config.SqlQueryTracker;
import com.example.jpaquery.domain.Order;
import com.example.jpaquery.support.QueryComparisonTable;
import com.example.jpaquery.support.QueryMeasurement;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnit;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class QueryComparisonTest {

    @Autowired
    private EntityManager entityManager;

    @PersistenceUnit
    private EntityManagerFactory entityManagerFactory;

    @Test
    void printsQueryCountAndSqlShapeTable() {
        QueryMeasurement baseline = measure(
            "orders-baseline",
            () -> entityManager.createQuery(
                "select o from Order o order by o.id", Order.class
            ).getResultList()
        );
        QueryMeasurement repeated = measure(
            "orders-baseline-repeat",
            () -> entityManager.createQuery(
                "select o from Order o order by o.id", Order.class
            ).getResultList()
        );

        QueryComparisonTable.print(List.of(baseline, repeated));

        assertEquals(100, baseline.resultCount());
        assertEquals(baseline.resultCount(), repeated.resultCount());
        assertTrue(baseline.queryCount() > 0);
        assertTrue(baseline.sqlStatements().stream().anyMatch(sql -> sql.contains("select")));
    }

    private QueryMeasurement measure(String strategy, Supplier<List<Order>> query) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        SqlQueryTracker.clear();

        long startedAt = System.nanoTime();
        List<Order> orders = query.get();

        return new QueryMeasurement(
            strategy,
            orders.size(),
            statistics.getQueryExecutionCount(),
            System.nanoTime() - startedAt,
            SqlQueryTracker.snapshot()
        );
    }
}

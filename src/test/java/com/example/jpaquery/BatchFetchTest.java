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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
@DisplayName("JPA Batch Fetch 동작 검증")
class BatchFetchTest {

    private static final String ORDER_QUERY = "select o from Order o order by o.id";

    @Autowired
    private EntityManager entityManager;

    @PersistenceUnit
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("LAZY 연관관계 접근 시 여러 SELECT를 IN 쿼리로 묶는다")
    void batchesLazyAssociationQueries() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        // When: 동일 조건으로 Order 목록만 조회한다.
        resetTracking(statistics);
        long orderOnlyStartedAt = System.nanoTime();
        List<Order> ordersWithoutAssociationAccess = findOrders();
        QueryMeasurement orderOnly = snapshot(
            "Batch Fetch - 연관관계 미접근",
            ordersWithoutAssociationAccess.size(),
            statistics,
            orderOnlyStartedAt
        );

        // Then: 연관관계에 접근하지 않으면 Order 조회 SQL만 실행되어야 한다.
        // Result (assert): Order 100건과 SQL 1회를 기준 결과로 확인한다.
        assertEquals(100, ordersWithoutAssociationAccess.size());
        assertEquals(1, orderOnly.sqlStatements().size());

        entityManager.clear();

        // When: 같은 조건으로 조회한 뒤 모든 User와 OrderItem에 접근한다.
        resetTracking(statistics);
        long batchStartedAt = System.nanoTime();
        List<Order> ordersWithAssociationAccess = findOrders();
        List<String> userNames = ordersWithAssociationAccess.stream()
            .map(order -> order.getUser().getName())
            .toList();
        int totalItemCount = ordersWithAssociationAccess.stream()
            .mapToInt(order -> order.getItems().size())
            .sum();
        QueryMeasurement batchFetch = snapshot(
            "Batch Fetch - 연관관계 전체 접근",
            ordersWithAssociationAccess.size(),
            statistics,
            batchStartedAt
        );

        // Then: LAZY 접근은 유지하면서 연관관계 SELECT가 배치 단위로 실행되어야 한다.
        // Result (assert): 결과는 동일하고 User 1회, OrderItem 10회의 IN 쿼리를 확인한다.
        assertEquals(100, userNames.size());
        assertEquals(500, totalItemCount);
        assertEquals(100, ordersWithAssociationAccess.size());
        assertEquals(12, batchFetch.sqlStatements().size());
        assertEquals(1, countSql(batchFetch.sqlStatements(), "from orders"));
        assertEquals(1, countSql(batchFetch.sqlStatements(), "from users"));
        assertEquals(10, countSql(batchFetch.sqlStatements(), "from order_items"));
        assertTrue(batchFetch.containsInClause());

        QueryComparisonTable.print(List.of(orderOnly, batchFetch));
    }

    private List<Order> findOrders() {
        return entityManager.createQuery(ORDER_QUERY, Order.class).getResultList();
    }

    private void resetTracking(Statistics statistics) {
        statistics.clear();
        SqlQueryTracker.clear();
    }

    private QueryMeasurement snapshot(
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

    private long countSql(List<String> sqlStatements, String fragment) {
        return sqlStatements.stream()
            .map(String::toLowerCase)
            .filter(sql -> sql.contains(fragment))
            .count();
    }
}

package com.example.jpaquery;

import com.example.jpaquery.config.SqlQueryTracker;
import com.example.jpaquery.domain.Order;
import com.example.jpaquery.support.QueryComparisonTable;
import com.example.jpaquery.support.QueryMeasurement;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnit;
import jakarta.persistence.PersistenceUnitUtil;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
@DisplayName("JPA Lazy Loading 동작 검증")
class LazyLoadingTest {

    @Autowired
    private EntityManager entityManager;

    @PersistenceUnit
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("Order 목록 조회 후 연관 Entity 접근 시점에만 추가 SQL을 실행한다")
    void doesNotLoadAssociationsBeforeAccess() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        PersistenceUnitUtil persistenceUnitUtil = entityManagerFactory.getPersistenceUnitUtil();

        // When: Order 목록만 조회하고 User와 OrderItem에는 접근하지 않는다.
        resetTracking(statistics);
        long orderListStartedAt = System.nanoTime();
        List<Order> orders = entityManager.createQuery(
            "select o from Order o order by o.id", Order.class
        ).getResultList();
        QueryMeasurement orderList = snapshot("Order 목록 조회", orders.size(), statistics, orderListStartedAt);

        // Then: 목록 조회 시 Order의 연관 Entity는 아직 로딩되지 않아야 한다.
        // Result (assert): Order SELECT 1회만 발생하고 User, OrderItem은 미로딩 상태인지 확인한다.
        assertEquals(100, orders.size());
        assertEquals(1, orderList.sqlStatements().size());
        assertFalse(orderList.sqlSummary().toLowerCase().contains("join"));
        assertFalse(persistenceUnitUtil.isLoaded(orders.get(0), "user"));
        assertFalse(persistenceUnitUtil.isLoaded(orders.get(0), "items"));

        // When: 첫 번째 Order의 User 이름에 접근한다.
        resetTracking(statistics);
        long userAccessStartedAt = System.nanoTime();
        String userName = orders.get(0).getUser().getName();
        QueryMeasurement userAccess = snapshot("User 접근", 1, statistics, userAccessStartedAt);

        // Then: User에 실제로 접근한 시점에 User 조회 SQL이 실행되어야 한다.
        // Result (assert): users 테이블을 조회하는 추가 SQL 1회가 발생했는지 확인한다.
        assertNotNull(userName);
        assertEquals(1, userAccess.sqlStatements().size());
        assertTrue(userAccess.sqlSummary().toLowerCase().contains("from users"));

        // When: 첫 번째 Order의 OrderItem 목록 크기에 접근한다.
        resetTracking(statistics);
        long itemAccessStartedAt = System.nanoTime();
        int itemCount = orders.get(0).getItems().size();
        QueryMeasurement itemAccess = snapshot("OrderItem 접근", itemCount, statistics, itemAccessStartedAt);

        // Then: OrderItem 컬렉션에 실제로 접근한 시점에 OrderItem 조회 SQL이 실행되어야 한다.
        // Result (assert): order_items 테이블을 조회하는 추가 SQL 1회가 발생했는지 확인한다.
        assertEquals(5, itemCount);
        assertEquals(1, itemAccess.sqlStatements().size());
        assertTrue(itemAccess.sqlSummary().toLowerCase().contains("from order_items"));

        QueryComparisonTable.print(List.of(orderList, userAccess, itemAccess));
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
}

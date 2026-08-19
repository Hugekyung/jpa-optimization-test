package com.example.jpaquery;

import com.example.jpaquery.domain.Order;
import com.example.jpaquery.support.QueryComparisonTable;
import com.example.jpaquery.support.QueryMeasurement;
import com.example.jpaquery.support.QueryTrackingSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnit;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource(properties = "spring.jpa.properties.hibernate.default_batch_fetch_size=0")
@Transactional
@DisplayName("JPA N+1 문제 재현")
class NPlusOneTest {

    private static final String ORDER_QUERY = "select o from Order o order by o.id";

    @Autowired
    private EntityManager entityManager;

    @PersistenceUnit
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("Order 목록 조회 후 연관 Entity에 접근하면 N+1 SQL이 발생한다")
    void reproducesNPlusOneWhenAccessingAssociations() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        // When: 동일한 조건으로 Order 목록만 조회한다.
        QueryTrackingSupport.resetTracking(statistics);
        long orderOnlyStartedAt = System.nanoTime();
        List<Order> ordersWithoutAssociationAccess = findOrders();
        QueryMeasurement orderOnly = QueryTrackingSupport.snapshot(
            "연관관계 미접근",
            ordersWithoutAssociationAccess.size(),
            statistics,
            orderOnlyStartedAt
        );
        List<Long> orderIds = orderIds(ordersWithoutAssociationAccess);

        // Then: Order 목록만 조회하면 Order SELECT 1회만 실행되어야 한다.
        // Result (assert): 기준 결과 건수와 SQL 실행 횟수를 확인한다.
        assertEquals(100, ordersWithoutAssociationAccess.size());
        assertEquals(1, orderOnly.sqlStatements().size());
        assertEquals(1, QueryTrackingSupport.countSql(orderOnly.sqlStatements(), "from orders"));

        entityManager.clear();

        // When: 같은 조건으로 조회한 뒤 모든 Order의 User와 OrderItem에 접근한다.
        QueryTrackingSupport.resetTracking(statistics);
        long nPlusOneStartedAt = System.nanoTime();
        List<Order> ordersWithAssociationAccess = findOrders();
        List<String> userNames = ordersWithAssociationAccess.stream()
            .map(order -> order.getUser().getName())
            .toList();
        int totalItemCount = ordersWithAssociationAccess.stream()
            .mapToInt(order -> order.getItems().size())
            .sum();
        QueryMeasurement nPlusOne = QueryTrackingSupport.snapshot(
            "연관관계 전체 접근(N+1)",
            ordersWithAssociationAccess.size(),
            statistics,
            nPlusOneStartedAt
        );

        // Then: 연관관계에 접근하면 User와 OrderItem을 위한 추가 SELECT가 반복되어야 한다.
        // Result (assert): 동일한 결과를 유지하면서 1 + User 10 + OrderItem 100 SQL을 확인한다.
        assertEquals(orderIds, orderIds(ordersWithAssociationAccess));
        assertEquals(100, userNames.size());
        assertEquals(500, totalItemCount);
        assertEquals(100, ordersWithAssociationAccess.size());
        assertEquals(111, nPlusOne.sqlStatements().size());
        assertEquals(1, QueryTrackingSupport.countSql(nPlusOne.sqlStatements(), "from orders"));
        assertEquals(10, QueryTrackingSupport.countSql(nPlusOne.sqlStatements(), "from users"));
        assertEquals(100, QueryTrackingSupport.countSql(nPlusOne.sqlStatements(), "from order_items"));

        QueryComparisonTable.print(List.of(orderOnly, nPlusOne));
        assertTrue(nPlusOne.sqlSummary().contains("from users"));
        assertTrue(nPlusOne.sqlSummary().contains("from order_items"));
    }

    private List<Order> findOrders() {
        return entityManager.createQuery(ORDER_QUERY, Order.class).getResultList();
    }

    private List<Long> orderIds(List<Order> orders) {
        return orders.stream()
            .map(Order::getId)
            .toList();
    }
}

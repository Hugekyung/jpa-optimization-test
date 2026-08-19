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
class LazyLoadingTest {

    @Autowired
    private EntityManager entityManager;

    @PersistenceUnit
    private EntityManagerFactory entityManagerFactory;

    @Test
    void doesNotLoadAssociationsBeforeAccess() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        PersistenceUnitUtil persistenceUnitUtil = entityManagerFactory.getPersistenceUnitUtil();

        resetTracking(statistics);
        List<Order> orders = entityManager.createQuery(
            "select o from Order o order by o.id", Order.class
        ).getResultList();
        QueryMeasurement orderList = snapshot("Order 목록 조회", orders.size(), statistics);

        assertEquals(100, orders.size());
        assertEquals(1, orderList.sqlStatements().size());
        assertFalse(orderList.sqlSummary().toLowerCase().contains("join"));
        assertFalse(persistenceUnitUtil.isLoaded(orders.get(0), "user"));
        assertFalse(persistenceUnitUtil.isLoaded(orders.get(0), "items"));

        resetTracking(statistics);
        String userName = orders.get(0).getUser().getName();
        QueryMeasurement userAccess = snapshot("User 접근", 1, statistics);

        assertNotNull(userName);
        assertEquals(1, userAccess.sqlStatements().size());
        assertTrue(userAccess.sqlSummary().toLowerCase().contains("from users"));

        resetTracking(statistics);
        int itemCount = orders.get(0).getItems().size();
        QueryMeasurement itemAccess = snapshot("OrderItem 접근", itemCount, statistics);

        assertEquals(5, itemCount);
        assertEquals(1, itemAccess.sqlStatements().size());
        assertTrue(itemAccess.sqlSummary().toLowerCase().contains("from order_items"));

        QueryComparisonTable.print(List.of(orderList, userAccess, itemAccess));
    }

    private void resetTracking(Statistics statistics) {
        statistics.clear();
        SqlQueryTracker.clear();
    }

    private QueryMeasurement snapshot(String strategy, int resultCount, Statistics statistics) {
        return new QueryMeasurement(
            strategy,
            resultCount,
            statistics.getQueryExecutionCount(),
            SqlQueryTracker.snapshot()
        );
    }
}


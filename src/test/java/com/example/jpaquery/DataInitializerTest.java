package com.example.jpaquery;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class DataInitializerTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    void createsExpectedSampleData() {
        Long userCount = entityManager.createQuery(
            "select count(u) from User u", Long.class
        ).getSingleResult();
        Long orderCount = entityManager.createQuery(
            "select count(o) from Order o", Long.class
        ).getSingleResult();
        Long orderItemCount = entityManager.createQuery(
            "select count(i) from OrderItem i", Long.class
        ).getSingleResult();
        List<Long> ordersPerUser = entityManager.createQuery(
            "select count(o) from Order o group by o.user.id order by o.user.id", Long.class
        ).getResultList();
        List<Long> itemsPerOrder = entityManager.createQuery(
            "select count(i) from OrderItem i group by i.order.id order by i.order.id", Long.class
        ).getResultList();

        assertEquals(10L, userCount);
        assertEquals(100L, orderCount);
        assertEquals(500L, orderItemCount);
        assertEquals(List.of(10L, 10L, 10L, 10L, 10L, 10L, 10L, 10L, 10L, 10L), ordersPerUser);
        assertEquals(100, itemsPerOrder.size());
        assertTrue(itemsPerOrder.stream().allMatch(count -> count == 5L));
    }
}

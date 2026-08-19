package com.example.jpaquery.config;

import com.example.jpaquery.domain.Order;
import com.example.jpaquery.domain.OrderItem;
import com.example.jpaquery.domain.OrderStatus;
import com.example.jpaquery.domain.User;
import jakarta.persistence.EntityManager;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final int USER_COUNT = 10;
    private static final int ORDERS_PER_USER = 10;
    private static final int ITEMS_PER_ORDER = 5;
    private static final LocalDateTime BASE_CREATED_AT = LocalDateTime.of(2026, 1, 1, 0, 0);

    private final EntityManager entityManager;

    public DataInitializer(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public void run(String... args) {
        for (int userIndex = 1; userIndex <= USER_COUNT; userIndex++) {
            User user = new User("User " + userIndex);
            entityManager.persist(user);

            for (int orderIndex = 1; orderIndex <= ORDERS_PER_USER; orderIndex++) {
                Order order = new Order(
                    OrderStatus.PENDING,
                    BASE_CREATED_AT.plusDays(orderIndex - 1)
                );
                user.addOrder(order);
                entityManager.persist(order);

                for (int itemIndex = 1; itemIndex <= ITEMS_PER_ORDER; itemIndex++) {
                    OrderItem item = new OrderItem(
                        "Product " + itemIndex,
                        BigDecimal.valueOf(itemIndex * 1000L),
                        itemIndex
                    );
                    order.addItem(item);
                }
            }
        }
    }
}


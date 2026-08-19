package com.example.jpaquery.service;

import com.example.jpaquery.domain.Order;
import com.example.jpaquery.repository.OrderRepository;
import com.example.jpaquery.repository.projection.OrderSummaryProjection;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class OrderQueryService {

    private final OrderRepository orderRepository;
    private final EntityManager entityManager;

    public OrderQueryService(OrderRepository orderRepository, EntityManager entityManager) {
        this.orderRepository = orderRepository;
        this.entityManager = entityManager;
    }

    public List<Order> findOrdersWithUser() {
        setFetchBatchSize(10);
        return orderRepository.findAllWithUser();
    }

    public List<Order> findOrdersWithItems() {
        setFetchBatchSize(10);
        return orderRepository.findAllWithItems();
    }

    @Transactional(readOnly = true)
    public List<Order> findOrdersWithNPlusOne() {
        setFetchBatchSize(0);
        return findOrdersWithAssociationAccess();
    }

    @Transactional(readOnly = true)
    public List<Order> findOrdersWithBatchFetch() {
        setFetchBatchSize(10);
        return findOrdersWithAssociationAccess();
    }

    public List<OrderSummaryProjection> findOrderSummaries() {
        setFetchBatchSize(10);
        return orderRepository.findOrderSummaries();
    }

    private void setFetchBatchSize(int batchSize) {
        entityManager.unwrap(Session.class).setFetchBatchSize(batchSize);
    }

    private List<Order> findOrdersWithAssociationAccess() {
        List<Order> orders = orderRepository.findAll();
        orders.forEach(order -> {
            order.getUser().getName();
            order.getItems().size();
        });
        return orders;
    }
}

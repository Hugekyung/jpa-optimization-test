package com.example.jpaquery.service;

import com.example.jpaquery.api.dto.OrderSummaryResponse;
import com.example.jpaquery.domain.Order;
import com.example.jpaquery.repository.OrderRepository;
import com.example.jpaquery.repository.projection.OrderSummaryProjection;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

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

    public Page<OrderSummaryResponse> findPage(Pageable pageable) {
        setFetchBatchSize(0);
        return orderRepository.findAll(pageable).map(OrderSummaryResponse::from);
    }

    public Slice<OrderSummaryResponse> findSlice(Pageable pageable) {
        setFetchBatchSize(0);
        return orderRepository.findOrderSummarySlice(pageable)
            .map(OrderSummaryResponse::from);
    }

    public Page<OrderSummaryResponse> findPageWithUser(Pageable pageable) {
        setFetchBatchSize(0);
        return orderRepository.findPageWithUser(pageable).map(OrderSummaryResponse::from);
    }

    public List<OrderSummaryResponse> findPageWithItems(Pageable pageable) {
        setFetchBatchSize(0);
        return orderRepository.findPageWithItems(pageable).stream()
            .map(OrderSummaryResponse::from)
            .toList();
    }

    public Page<OrderSummaryResponse> findPageWithBatchFetch(Pageable pageable) {
        setFetchBatchSize(10);
        return orderRepository.findAll(pageable).map(OrderSummaryResponse::from);
    }

    public Page<OrderSummaryResponse> findPageWithProjection(Pageable pageable) {
        setFetchBatchSize(10);
        return orderRepository.findOrderSummaryPage(pageable)
            .map(OrderSummaryResponse::from);
    }

    private void setFetchBatchSize(int batchSize) {
        entityManager.unwrap(Session.class).setFetchBatchSize(batchSize);
    }

    private List<Order> findOrdersWithAssociationAccess() {
        List<Order> orders = orderRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
        orders.forEach(order -> {
            order.getUser().getName();
            order.getItems().size();
        });
        return orders;
    }
}

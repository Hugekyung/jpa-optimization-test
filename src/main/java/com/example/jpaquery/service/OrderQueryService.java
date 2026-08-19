package com.example.jpaquery.service;

import com.example.jpaquery.domain.Order;
import com.example.jpaquery.repository.OrderRepository;
import com.example.jpaquery.repository.projection.OrderSummaryProjection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class OrderQueryService {

    private final OrderRepository orderRepository;

    public OrderQueryService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public List<Order> findOrdersWithUser() {
        return orderRepository.findAllWithUser();
    }

    public List<Order> findOrdersWithItems() {
        return orderRepository.findAllWithItems();
    }

    @Transactional(readOnly = true)
    public List<Order> findOrdersWithNPlusOne() {
        return findOrdersWithAssociationAccess();
    }

    @Transactional(readOnly = true)
    public List<Order> findOrdersWithBatchFetch() {
        return findOrdersWithAssociationAccess();
    }

    public List<OrderSummaryProjection> findOrderSummaries() {
        return orderRepository.findOrderSummaries();
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

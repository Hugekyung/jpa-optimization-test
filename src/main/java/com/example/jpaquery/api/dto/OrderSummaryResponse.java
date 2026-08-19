package com.example.jpaquery.api.dto;

import com.example.jpaquery.domain.Order;
import com.example.jpaquery.domain.OrderStatus;
import com.example.jpaquery.repository.projection.OrderSummaryProjection;

public record OrderSummaryResponse(
    Long orderId,
    String userName,
    OrderStatus status
) {

    public static OrderSummaryResponse from(OrderSummaryProjection projection) {
        return new OrderSummaryResponse(
            projection.orderId(),
            projection.userName(),
            projection.status()
        );
    }

    public static OrderSummaryResponse from(Order order) {
        return new OrderSummaryResponse(
            order.getId(),
            order.getUser().getName(),
            order.getStatus()
        );
    }
}

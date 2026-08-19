package com.example.jpaquery.api.dto;

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
}

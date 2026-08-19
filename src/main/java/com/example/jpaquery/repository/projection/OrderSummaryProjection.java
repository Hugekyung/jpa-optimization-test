package com.example.jpaquery.repository.projection;

import com.example.jpaquery.domain.OrderStatus;

public record OrderSummaryProjection(
    Long orderId,
    String userName,
    OrderStatus status
) {
}

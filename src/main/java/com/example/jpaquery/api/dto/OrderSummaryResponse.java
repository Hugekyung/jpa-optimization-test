package com.example.jpaquery.api.dto;

import com.example.jpaquery.domain.OrderStatus;

public record OrderSummaryResponse(
    Long orderId,
    String userName,
    OrderStatus status
) {
}

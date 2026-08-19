package com.example.jpaquery.api.dto;

import com.example.jpaquery.domain.Order;
import com.example.jpaquery.domain.OrderStatus;

public record OrderFetchJoinResponse(
    Long orderId,
    String userName,
    OrderStatus status
) {

    public static OrderFetchJoinResponse from(Order order) {
        return new OrderFetchJoinResponse(
            order.getId(),
            order.getUser().getName(),
            order.getStatus()
        );
    }
}


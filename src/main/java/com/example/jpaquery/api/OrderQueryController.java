package com.example.jpaquery.api;

import com.example.jpaquery.api.dto.OrderFetchJoinResponse;
import com.example.jpaquery.api.dto.OrderSummaryResponse;
import com.example.jpaquery.service.OrderQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderQueryController {

    private final OrderQueryService orderQueryService;

    public OrderQueryController(OrderQueryService orderQueryService) {
        this.orderQueryService = orderQueryService;
    }

    @GetMapping("/fetch-join")
    public List<OrderFetchJoinResponse> findOrdersWithFetchJoin() {
        return orderQueryService.findOrdersWithUser().stream()
            .map(OrderFetchJoinResponse::from)
            .toList();
    }

    @GetMapping("/n-plus-one")
    public List<OrderSummaryResponse> findOrdersWithNPlusOne() {
        return orderQueryService.findOrdersWithNPlusOne().stream()
            .map(OrderSummaryResponse::from)
            .toList();
    }

    @GetMapping("/batch-fetch")
    public List<OrderSummaryResponse> findOrdersWithBatchFetch() {
        return orderQueryService.findOrdersWithBatchFetch().stream()
            .map(OrderSummaryResponse::from)
            .toList();
    }

    @GetMapping("/projection")
    public List<OrderSummaryResponse> findOrderSummaries() {
        return orderQueryService.findOrderSummaries().stream()
            .map(OrderSummaryResponse::from)
            .toList();
    }
}

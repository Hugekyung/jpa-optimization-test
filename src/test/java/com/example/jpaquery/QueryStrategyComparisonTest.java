package com.example.jpaquery;

import com.example.jpaquery.api.dto.OrderSummaryResponse;
import com.example.jpaquery.service.OrderQueryService;
import com.example.jpaquery.support.QueryComparisonTable;
import com.example.jpaquery.support.QueryMeasurement;
import com.example.jpaquery.support.QueryTrackingSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnit;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
@DisplayName("조회 전략별 SQL 비교 검증")
class QueryStrategyComparisonTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private OrderQueryService orderQueryService;

    @PersistenceUnit
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("동일 조건으로 네 조회 전략의 결과와 SQL을 비교한다")
    void comparesAllQueryStrategies() {
        StrategyResult nPlusOne = measure(
            "N+1",
            () -> orderQueryService.findOrdersWithNPlusOne().stream()
                .map(OrderSummaryResponse::from)
                .toList()
        );
        StrategyResult fetchJoin = measure(
            "Fetch Join",
            () -> orderQueryService.findOrdersWithUser().stream()
                .map(OrderSummaryResponse::from)
                .toList()
        );
        StrategyResult batchFetch = measure(
            "Batch Fetch",
            () -> orderQueryService.findOrdersWithBatchFetch().stream()
                .map(OrderSummaryResponse::from)
                .toList()
        );
        StrategyResult projection = measure(
            "DTO Projection",
            () -> orderQueryService.findOrderSummaries().stream()
                .map(OrderSummaryResponse::from)
                .toList()
        );

        // Result (assert): 같은 조회 조건에서 응답 데이터가 모두 일치하는지 확인한다.
        assertEquals(100, nPlusOne.responses().size());
        assertEquals(nPlusOne.responses(), fetchJoin.responses());
        assertEquals(nPlusOne.responses(), batchFetch.responses());
        assertEquals(nPlusOne.responses(), projection.responses());

        // Result (assert): 전략별 SQL 횟수와 JOIN, IN 발생 여부를 확인한다.
        assertEquals(111, nPlusOne.measurement().sqlStatements().size());
        assertEquals(1, fetchJoin.measurement().sqlStatements().size());
        assertEquals(12, batchFetch.measurement().sqlStatements().size());
        assertEquals(1, projection.measurement().sqlStatements().size());
        assertTrue(fetchJoin.measurement().containsJoin());
        assertTrue(batchFetch.measurement().containsInClause());

        QueryComparisonTable.print(List.of(
            nPlusOne.measurement(),
            fetchJoin.measurement(),
            batchFetch.measurement(),
            projection.measurement()
        ));
    }

    private StrategyResult measure(
        String strategy,
        Supplier<List<OrderSummaryResponse>> query
    ) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        entityManager.clear();
        QueryTrackingSupport.resetTracking(statistics);

        long startedAt = System.nanoTime();
        List<OrderSummaryResponse> responses = query.get();

        QueryMeasurement measurement = QueryTrackingSupport.snapshot(
            strategy,
            responses.size(),
            statistics,
            startedAt
        );
        return new StrategyResult(responses, measurement);
    }

    private record StrategyResult(
        List<OrderSummaryResponse> responses,
        QueryMeasurement measurement
    ) {
    }
}

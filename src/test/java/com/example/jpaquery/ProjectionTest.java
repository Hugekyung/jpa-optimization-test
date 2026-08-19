package com.example.jpaquery;

import com.example.jpaquery.api.dto.OrderSummaryResponse;
import com.example.jpaquery.domain.Order;
import com.example.jpaquery.repository.OrderRepository;
import com.example.jpaquery.repository.projection.OrderSummaryProjection;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("JPA DTO Projection 동작 검증")
class ProjectionTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private MockMvc mockMvc;

    @PersistenceUnit
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("Entity 조회와 같은 결과를 필요한 컬럼만 DTO로 조회한다")
    void projectsOrderSummaryColumns() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        // When: Order와 User Entity를 Fetch Join으로 조회해 응답을 만든다.
        QueryTrackingSupport.resetTracking(statistics);
        long entityStartedAt = System.nanoTime();
        List<OrderSummaryResponse> entityResponses = entityManager.createQuery(
                "select o from Order o join fetch o.user order by o.id", Order.class
            ).getResultList().stream()
            .map(OrderSummaryResponse::from)
            .toList();
        QueryMeasurement entityQuery = QueryTrackingSupport.snapshot(
            "Entity Fetch Join",
            entityResponses.size(),
            statistics,
            entityStartedAt
        );

        // Then: Entity 조회는 Order와 User Entity에 필요한 컬럼을 함께 조회해야 한다.
        // Result (assert): 기준 응답 100건과 Entity 컬럼 조회 SQL을 확인한다.
        assertEquals(100, entityResponses.size());
        assertEquals(1, entityQuery.sqlStatements().size());
        assertTrue(entityQuery.sqlSummary().contains("created_at"));

        entityManager.clear();

        // When: 같은 조건으로 필요한 컬럼만 DTO Projection으로 조회한다.
        QueryTrackingSupport.resetTracking(statistics);
        long projectionStartedAt = System.nanoTime();
        List<OrderSummaryProjection> projections = orderRepository.findOrderSummaries();
        QueryMeasurement projection = QueryTrackingSupport.snapshot(
            "DTO Projection",
            projections.size(),
            statistics,
            projectionStartedAt
        );

        // Then: Constructor Expression은 Entity를 만들지 않고 선택한 컬럼만 반환해야 한다.
        // Result (assert): 응답 데이터가 같고 불필요한 created_at 컬럼이 빠지는지 확인한다.
        assertEquals(100, projections.size());
        assertEquals(
            entityResponses.stream()
                .map(response -> new OrderSummaryResponse(
                    response.orderId(), response.userName(), response.status()
                ))
                .toList(),
            projections.stream()
                .map(OrderSummaryResponse::from)
                .toList()
        );
        assertEquals(1, projection.sqlStatements().size());
        assertFalse(projection.sqlSummary().contains("created_at"));
        assertTrue(projection.sqlSummary().contains("user_id"));

        QueryComparisonTable.print(List.of(entityQuery, projection));
    }

    @Test
    @DisplayName("Projection 조회 API가 주문 요약 목록을 반환한다")
    void projectionApiReturnsOrderSummaries() throws Exception {
        mockMvc.perform(get("/api/orders/projection"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(100)))
            .andExpect(jsonPath("$[0].orderId").isNumber())
            .andExpect(jsonPath("$[0].userName").value("User 1"))
            .andExpect(jsonPath("$[0].status").value("PENDING"));
    }
}

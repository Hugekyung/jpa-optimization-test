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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Pagination 조회 전략 검증")
class PaginationTest {

    private static final Pageable FIRST_PAGE = PageRequest.of(
        0,
        20,
        Sort.by(Sort.Direction.ASC, "id")
    );

    @Autowired
    private OrderQueryService orderQueryService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @PersistenceUnit
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("기본 API는 Page 메타데이터와 20건의 주문을 반환한다")
    void returnsPageMetadata() throws Exception {
        // When: 첫 번째 페이지를 Page 방식으로 요청한다.
        mockMvc.perform(get("/api/orders")
                .param("page", "0")
                .param("size", "20"))
            // Then: Page는 전체 건수와 전체 페이지 수를 계산해 반환한다.
            .andExpect(status().isOk())
            // Result (assert): 주문 20건, 전체 100건, 전체 페이지 5개를 확인한다.
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content.length()").value(20))
            .andExpect(jsonPath("$.totalElements").value(100))
            .andExpect(jsonPath("$.totalPages").value(5))
            .andExpect(jsonPath("$.first").value(true));
    }

    @Test
    @DisplayName("Slice API는 다음 페이지 존재 여부만 확인하고 전체 Count를 조회하지 않는다")
    void returnsSliceWithoutCountMetadata() throws Exception {
        // When: 첫 번째 페이지를 Slice 방식으로 요청한다.
        mockMvc.perform(get("/api/orders/slice")
                .param("page", "0")
                .param("size", "20"))
            // Then: Slice는 다음 페이지 존재 여부를 기준으로 응답한다.
            .andExpect(status().isOk())
            // Result (assert): 20건과 다음 페이지 존재 여부를 확인하고 totalElements는 반환하지 않는다.
            .andExpect(jsonPath("$.content.length()").value(20))
            .andExpect(jsonPath("$.last").value(false))
            .andExpect(jsonPath("$.totalElements").doesNotExist());
    }

    @Test
    @DisplayName("같은 첫 페이지에서 조회 전략별 SQL 횟수와 결과를 비교한다")
    void comparesPaginatedQueryStrategies() {
        StrategyResult lazy = measure(
            "Pagination - LAZY",
            () -> orderQueryService.findPage(FIRST_PAGE).getContent()
        );
        StrategyResult fetchJoin = measure(
            "Pagination - User Fetch Join",
            () -> orderQueryService.findPageWithUser(FIRST_PAGE).getContent()
        );
        StrategyResult collectionFetchJoin = measure(
            "Pagination - Collection Fetch Join",
            () -> orderQueryService.findPageWithItems(FIRST_PAGE)
        );
        StrategyResult batchFetch = measure(
            "Pagination - Batch Fetch",
            () -> orderQueryService.findPageWithBatchFetch(FIRST_PAGE).getContent()
        );
        StrategyResult projection = measure(
            "Pagination - DTO Projection",
            () -> orderQueryService.findPageWithProjection(FIRST_PAGE).getContent()
        );

        // Result (assert): 같은 페이지 조건에서 모든 전략의 주문 결과가 일치하는지 확인한다.
        assertEquals(20, lazy.responses().size());
        assertEquals(lazy.responses(), fetchJoin.responses());
        assertEquals(lazy.responses(), collectionFetchJoin.responses());
        assertEquals(lazy.responses(), batchFetch.responses());
        assertEquals(lazy.responses(), projection.responses());

        // Result (assert): Page의 Count Query를 포함한 전략별 SQL 차이를 확인한다.
        assertEquals(4, lazy.measurement().sqlStatements().size());
        assertEquals(2, fetchJoin.measurement().sqlStatements().size());
        assertEquals(3, collectionFetchJoin.measurement().sqlStatements().size());
        assertEquals(3, batchFetch.measurement().sqlStatements().size());
        assertEquals(2, projection.measurement().sqlStatements().size());
        assertTrue(fetchJoin.measurement().containsJoin());
        assertTrue(collectionFetchJoin.measurement().containsJoin());
        assertTrue(batchFetch.measurement().containsInClause());
        assertFalse(collectionFetchJoin.measurement().sqlSummary().toLowerCase().contains("fetch first"));

        QueryComparisonTable.print(List.of(
            lazy.measurement(),
            fetchJoin.measurement(),
            collectionFetchJoin.measurement(),
            batchFetch.measurement(),
            projection.measurement()
        ));
    }

    private StrategyResult measure(
        String strategy,
        java.util.function.Supplier<List<OrderSummaryResponse>> query
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

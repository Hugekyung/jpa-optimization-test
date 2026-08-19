package com.example.jpaquery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("조회 전략 비교 API 검증")
class QueryStrategyApiTest {

    private static final List<String> STRATEGY_PATHS = List.of(
        "/api/orders/n-plus-one",
        "/api/orders/fetch-join",
        "/api/orders/batch-fetch",
        "/api/orders/projection"
    );

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("네 가지 조회 전략 API가 동일한 주문 요약 응답을 반환한다")
    void returnsSameOrderSummaryFromEveryStrategy() throws Exception {
        List<String> responses = STRATEGY_PATHS.stream()
            .map(this::requestOrderSummaries)
            .toList();

        // Result (assert): 동일 조건의 네 API가 같은 응답 본문을 반환하는지 확인한다.
        assertEquals(responses.get(0), responses.get(1));
        assertEquals(responses.get(0), responses.get(2));
        assertEquals(responses.get(0), responses.get(3));
    }

    private String requestOrderSummaries(String path) {
        try {
            MvcResult result = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(100)))
                .andExpect(jsonPath("$[0].orderId").isNumber())
                .andExpect(jsonPath("$[0].userName").value("User 1"))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andReturn();
            return result.getResponse().getContentAsString();
        } catch (Exception exception) {
            throw new IllegalStateException("조회 전략 API 요청에 실패했습니다: " + path, exception);
        }
    }
}

package com.example.jpaquery;

import com.example.jpaquery.api.dto.OrderFetchJoinResponse;
import com.example.jpaquery.domain.Order;
import com.example.jpaquery.domain.OrderStatus;
import com.example.jpaquery.domain.User;
import com.example.jpaquery.repository.OrderRepository;
import com.example.jpaquery.support.QueryComparisonTable;
import com.example.jpaquery.support.QueryMeasurement;
import com.example.jpaquery.support.QueryTrackingSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnit;
import jakarta.persistence.PersistenceUnitUtil;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.jpa.properties.hibernate.default_batch_fetch_size=0")
@Transactional
@DisplayName("JPA Fetch Join 동작 검증")
class FetchJoinTest {

    private static final String ORDER_QUERY = "select o from Order o order by o.id";

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private MockMvc mockMvc;

    @PersistenceUnit
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("기본 Lazy 조회보다 User Fetch Join이 SQL을 줄인다")
    void fetchJoinLoadsUserWithSingleQuery() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        // When: 동일 조건으로 Order를 조회하고 User 이름에 접근한다.
        QueryTrackingSupport.resetTracking(statistics);
        long lazyStartedAt = System.nanoTime();
        List<OrderFetchJoinResponse> lazyResponses = entityManager.createQuery(
                ORDER_QUERY, Order.class
            ).getResultList().stream()
            .map(OrderFetchJoinResponse::from)
            .toList();
        QueryMeasurement lazy = QueryTrackingSupport.snapshot("Lazy + User 접근", lazyResponses.size(), statistics, lazyStartedAt);

        // Then: Lazy 방식은 User 접근 때 추가 SELECT를 발생시켜야 한다.
        // Result (assert): 기준 응답과 SQL 11회를 확인한다.
        assertEquals(100, lazyResponses.size());
        assertEquals(11, lazy.sqlStatements().size());
        assertEquals(10, QueryTrackingSupport.countSql(lazy.sqlStatements(), "from users"));

        entityManager.clear();

        // When: 같은 조건으로 User Fetch Join 조회 후 같은 응답을 만든다.
        QueryTrackingSupport.resetTracking(statistics);
        long fetchJoinStartedAt = System.nanoTime();
        List<OrderFetchJoinResponse> fetchJoinResponses = orderRepository.findAllWithUser().stream()
            .map(OrderFetchJoinResponse::from)
            .toList();
        QueryMeasurement fetchJoin = QueryTrackingSupport.snapshot(
            "User Fetch Join",
            fetchJoinResponses.size(),
            statistics,
            fetchJoinStartedAt
        );

        // Then: JOIN으로 User를 함께 조회해 추가 SELECT가 없어야 한다.
        // Result (assert): 응답은 같고 SQL은 1회이며 JOIN이 포함되는지 확인한다.
        assertEquals(lazyResponses, fetchJoinResponses);
        assertEquals(1, fetchJoin.sqlStatements().size());
        assertTrue(fetchJoin.containsJoin());
        assertEquals(0, QueryTrackingSupport.countSql(fetchJoin.sqlStatements(), "from order_items"));

        QueryComparisonTable.print(List.of(lazy, fetchJoin));
    }

    @Test
    @DisplayName("Collection Fetch Join은 distinct로 Order 중복을 제거한다")
    void collectionFetchJoinRemovesDuplicateOrders() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        PersistenceUnitUtil persistenceUnitUtil = entityManagerFactory.getPersistenceUnitUtil();

        // When: Order와 OrderItem을 Collection Fetch Join으로 조회한다.
        QueryTrackingSupport.resetTracking(statistics);
        long startedAt = System.nanoTime();
        List<Order> orders = orderRepository.findAllWithItems();
        int totalItemCount = orders.stream()
            .mapToInt(order -> order.getItems().size())
            .sum();
        QueryMeasurement collectionFetchJoin = QueryTrackingSupport.snapshot(
            "Collection Fetch Join",
            orders.size(),
            statistics,
            startedAt
        );

        // Then: SQL Row는 Item 수만큼 늘어날 수 있지만 Entity 결과는 distinct로 중복 제거되어야 한다.
        // Result (assert): Order 100개, Item 500개, 컬렉션 초기화, JOIN 1회를 확인한다.
        assertEquals(100, orders.size());
        assertEquals(500, totalItemCount);
        assertTrue(persistenceUnitUtil.isLoaded(orders.get(0), "items"));
        assertEquals(1, collectionFetchJoin.sqlStatements().size());
        assertTrue(collectionFetchJoin.containsJoin());

        QueryComparisonTable.print(List.of(collectionFetchJoin));
    }

    @Test
    @DisplayName("Collection Fetch Join은 Item이 없는 Order도 조회한다")
    void collectionFetchJoinIncludesOrderWithoutItems() {
        User user = new User("User without items");
        Order orderWithoutItems = new Order(OrderStatus.CANCELLED, LocalDateTime.of(2026, 2, 1, 0, 0));
        user.addOrder(orderWithoutItems);
        entityManager.persist(user);
        entityManager.persist(orderWithoutItems);
        entityManager.flush();
        entityManager.clear();

        // When: OrderItem이 없는 Order를 Collection Fetch Join으로 조회한다.
        List<Order> orders = orderRepository.findAllWithItems();

        // Then: LEFT JOIN은 연관 데이터가 없어도 Order를 유지해야 한다.
        // Result (assert): 새 Order가 조회되고 빈 Collection으로 초기화되는지 확인한다.
        Order fetchedOrder = orders.stream()
            .filter(order -> order.getId().equals(orderWithoutItems.getId()))
            .findFirst()
            .orElseThrow();
        assertTrue(fetchedOrder.getItems().isEmpty());
        assertTrue(entityManagerFactory.getPersistenceUnitUtil().isLoaded(fetchedOrder, "items"));
    }

    @Test
    @DisplayName("Fetch Join 조회 API가 주문 목록을 반환한다")
    void fetchJoinApiReturnsOrders() throws Exception {
        mockMvc.perform(get("/api/orders/fetch-join"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(100)))
            .andExpect(jsonPath("$[0].orderId").isNumber())
            .andExpect(jsonPath("$[0].userName").isString())
            .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

}

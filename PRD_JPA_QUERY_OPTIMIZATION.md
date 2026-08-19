# PRD — JPA 조회 성능 최적화 실습

## 1. 프로젝트 목적

Spring Data JPA의 조회 성능과 관련된 핵심 개념을 작은 예제 프로젝트에서 직접 재현하고 비교한다.

단순히 기능을 구현하는 것이 아니라, 동일한 데이터를 여러 방식으로 조회하면서 실제 발생하는 SQL을 확인하고 다음 개념을 코드 수준에서 이해하는 것이 목표다.

- LAZY Loading / EAGER Loading
- N+1 문제
- Fetch Join
- Batch Fetching
- DTO Projection
- Persistence Context와 Entity 조회 비용
- 조회 방식에 따른 SQL 수와 조회 데이터 차이

최종 목표는 다음 질문에 코드와 SQL 로그를 근거로 답할 수 있는 상태가 되는 것이다.

> "JPA에서 연관 Entity를 조회할 때 왜 N+1이 발생하고, Fetch Join / Batch Fetch / DTO Projection 중 어떤 방식을 어떤 상황에 선택해야 하는가?"

---

## 2. 기술 스택

- Java 21
- Spring Boot 3.x
- Spring Data JPA
- Hibernate
- PostgreSQL 또는 H2
- Gradle
- JUnit 5
- IntelliJ IDEA 권장

학습 목적이라면 초기 구현은 H2로 진행해도 된다.

---

## 3. 핵심 도메인

관계는 아래처럼 단순하게 구성한다.

```text
User
  1
  |
  N
Order
  1
  |
  N
OrderItem
```

### User

```text
id
name
```

### Order

```text
id
user_id
status
created_at
```

### OrderItem

```text
id
order_id
product_name
price
quantity
```

연관관계:

```text
User 1 : N Order
Order 1 : N OrderItem
```

JPA Entity에서는 기본적으로 연관관계를 `LAZY`로 설정한다.

---

## 4. 샘플 데이터

성능 차이를 쉽게 확인할 수 있도록 애플리케이션 시작 시 테스트 데이터를 생성한다.

예시:

```text
User 10명

각 User
→ Order 10개

각 Order
→ OrderItem 5개
```

총 데이터:

```text
User       10개
Order     100개
OrderItem 500개
```

N+1을 SQL 로그에서 명확하게 확인하기 위한 데이터이므로 너무 많은 데이터를 만들 필요는 없다.

---

## 5. SQL 로그 설정

각 실습 단계에서 실제 SQL을 반드시 확인한다.

`application.yml` 예시:

```yaml
spring:
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true

logging:
  level:
    org.hibernate.SQL: debug
    org.hibernate.orm.jdbc.bind: trace
```

각 단계에서 다음 내용을 기록한다.

- 최초 조회 SQL
- 추가 SELECT 발생 여부
- 전체 SQL 실행 횟수
- JOIN 여부
- `IN (...)` 쿼리 발생 여부
- 조회된 Entity 범위

---

## 6. Phase 1 — 기본 LAZY Loading 구현

### 목적

JPA 연관관계와 LAZY Loading의 기본 동작을 확인한다.

### Entity 구성

예시:

```java
@Entity
public class Order {

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private User user;

    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
    private List<OrderItem> items = new ArrayList<>();
}
```

### 구현 API

```text
GET /api/orders
```

Order 목록만 조회한다.

### 확인 사항

- `findAll()` 실행 시 어떤 SQL이 발생하는가?
- `Order.user`는 즉시 조회되는가?
- `Order.items`는 즉시 조회되는가?
- LAZY 연관관계는 실제로 언제 SQL을 발생시키는가?

### 완료 조건

SQL 로그를 보고 `Order` 조회 시 연관 Entity가 아직 조회되지 않았음을 설명할 수 있다.

---

## 7. Phase 2 — N+1 문제 직접 발생시키기

### 목적

LAZY Loading 환경에서 N+1 문제가 왜 발생하는지 직접 확인한다.

### 구현

```java
List<Order> orders = orderRepository.findAll();

for (Order order : orders) {
    String userName = order.getUser().getName();
}
```

또는 DTO 변환 과정에서 User를 접근한다.

```java
return orders.stream()
    .map(order -> new OrderResponse(
        order.getId(),
        order.getUser().getName()
    ))
    .toList();
```

### 예상 SQL

```text
Order 목록 조회
→ SELECT 1회

각 Order의 User 조회
→ 추가 SELECT N회
```

주의:

동일한 User가 여러 Order에서 재사용되는 경우 Persistence Context의 1차 캐시 때문에 실제 추가 SQL 횟수가 Order 개수와 정확히 같지 않을 수 있다.

따라서 테스트 데이터를 설계할 때 이 점도 확인한다.

### 추가 실습

OrderItem도 접근한다.

```java
for (Order order : orders) {
    order.getItems().size();
}
```

### 확인 사항

- User 접근 시 SQL이 몇 번 발생하는가?
- OrderItem 접근 시 SQL이 몇 번 발생하는가?
- 코드에서는 단순 Getter처럼 보이는데 왜 SQL이 발생하는가?
- Persistence Context의 1차 캐시가 쿼리 수에 어떤 영향을 미치는가?

### 완료 조건

N+1을 다음과 같이 설명할 수 있다.

```text
부모 Entity를 1번 조회한 후
각 Entity의 LAZY 연관관계에 접근하면서
추가 SELECT가 반복적으로 발생하는 문제
```

---

## 8. Phase 3 — Fetch Join으로 N+1 해결

### 목적

특정 조회에서 필요한 연관 Entity를 한 번에 조회하는 방법을 확인한다.

### Repository

```java
@Query("""
    select o
    from Order o
    join fetch o.user
""")
List<Order> findAllWithUser();
```

### 구현 API

```text
GET /api/orders/fetch-join
```

### 확인 사항

기존:

```text
Order SELECT
+
User 추가 SELECT
```

Fetch Join:

```text
Order JOIN User
→ SELECT 1회
```

SQL 로그에서 JOIN이 발생하는지 확인한다.

### 추가 실습 — Collection Fetch Join

```java
@Query("""
    select distinct o
    from Order o
    join fetch o.items
""")
List<Order> findAllWithItems();
```

확인할 내용:

- OneToMany Fetch Join 시 결과 Row가 왜 증가하는가?
- 동일 Order가 SQL 결과에서 여러 Row로 나타날 수 있는 이유는 무엇인가?
- `distinct`가 필요한 상황은 무엇인가?
- Collection Fetch Join과 Pagination을 함께 사용할 때 어떤 문제가 생길 수 있는가?

### 완료 조건

Fetch Join을 다음과 같이 설명할 수 있다.

> Entity의 기본 연관관계는 LAZY로 유지하면서 특정 조회에서 필요한 연관 Entity만 선택적으로 함께 조회하는 방법.

---

## 9. Phase 4 — Batch Fetching 적용

### 목적

Fetch Join을 사용하지 않고도 LAZY Loading에서 발생하는 여러 SELECT를 묶어 처리하는 방법을 확인한다.

### 설정

```yaml
spring:
  jpa:
    properties:
      hibernate:
        default_batch_fetch_size: 100
```

### 구현

기존 N+1 코드 자체는 유지한다.

```java
List<Order> orders = orderRepository.findAll();

for (Order order : orders) {
    order.getItems().size();
}
```

### 예상 SQL 변화

기존:

```sql
SELECT * FROM order_item WHERE order_id = 1;
SELECT * FROM order_item WHERE order_id = 2;
SELECT * FROM order_item WHERE order_id = 3;
...
```

Batch Fetch:

```sql
SELECT *
FROM order_item
WHERE order_id IN (?, ?, ?, ...);
```

### 확인 사항

- N개의 쿼리가 몇 개로 줄어드는가?
- Fetch Join과 SQL 모양이 어떻게 다른가?
- Collection 조회에서 Batch Fetch가 왜 유용한가?
- Pagination과 함께 사용할 때 Fetch Join보다 유리할 수 있는 이유는 무엇인가?

### 완료 조건

다음 차이를 설명할 수 있다.

```text
Fetch Join
→ 처음부터 JOIN으로 같이 조회

Batch Fetch
→ LAZY Loading은 유지
→ 필요해진 연관 Entity들을 IN 쿼리로 묶어서 조회
```

---

## 10. Phase 5 — DTO Projection 적용

### 목적

조회 API에서 Entity 전체가 필요하지 않은 경우 필요한 데이터만 직접 조회하는 방법을 확인한다.

### Response DTO

```java
public record OrderSummaryResponse(
    Long orderId,
    String userName,
    OrderStatus status
) {
}
```

### Repository

JPQL Constructor Expression 예시:

```java
@Query("""
    select new com.example.api.OrderSummaryResponse(
        o.id,
        u.name,
        o.status
    )
    from Order o
    join o.user u
""")
List<OrderSummaryResponse> findOrderSummaries();
```

### 구현 API

```text
GET /api/orders/projection
```

### 확인 사항

- Entity 조회와 DTO Projection의 SELECT 컬럼 차이
- Persistence Context에서 Entity를 관리할 필요가 있는가?
- 단순 조회 API에서는 왜 Projection이 유리할 수 있는가?
- 수정이 필요한 비즈니스 로직에서는 왜 Entity 조회가 필요할 수 있는가?

### 완료 조건

다음 판단을 할 수 있어야 한다.

```text
Entity의 상태 변경이 필요한 로직
→ Entity 조회

조회 전용 API
→ DTO Projection 고려
```

---

## 11. Phase 6 — 조회 방식 비교 API

최종적으로 동일한 주문 목록을 여러 방식으로 조회할 수 있도록 API를 구성한다.

```text
GET /api/orders/n-plus-one
GET /api/orders/fetch-join
GET /api/orders/batch-fetch
GET /api/orders/projection
```

각 API는 가능한 한 동일한 형태의 응답을 반환하도록 한다.

이를 통해 조회 방식만 바꾸고 SQL 차이를 비교할 수 있게 한다.

---

## 12. 비교 결과 문서화

실습 완료 후 `README.md` 또는 `JPA_QUERY_OPTIMIZATION.md`에 결과를 정리한다.

비교 예시:

| 방식 | SQL 특징 | 장점 | 주의점 |
|---|---|---|---|
| LAZY + 단순 조회 | 추가 SELECT 발생 가능 | 단순함 | N+1 |
| Fetch Join | JOIN으로 한 번에 조회 | N+1 해결 | Collection/Pagination 주의 |
| Batch Fetch | IN 쿼리 | Collection 처리에 유용 | 추가 쿼리는 존재 |
| DTO Projection | 필요한 컬럼 직접 조회 | 조회 API 효율적 | Entity 기능 사용 불가 |

실제 SQL 횟수도 직접 기록한다.

```text
N+1
SQL: ___ 회

Fetch Join
SQL: ___ 회

Batch Fetch
SQL: ___ 회

DTO Projection
SQL: ___ 회
```

---

## 13. 선택 과제 — Pagination 비교

핵심 실습을 완료한 이후 진행한다.

### 구현

```text
GET /api/orders?page=0&size=20
```

다음 방법을 각각 적용해본다.

- 기본 LAZY
- Collection Fetch Join
- Batch Fetch
- DTO Projection

### 확인 사항

- Fetch Join + OneToMany + Pagination의 문제
- Batch Fetch + Pagination의 동작
- `Page`와 `Slice` 차이
- Count Query 발생 여부

이 단계까지 진행하면 실무 조회 API 설계와 상당히 가까운 형태가 된다.

---

## 14. 선택 과제 — Persistence Context 확인

N+1 실습 이후 동일 Entity를 다시 조회한다.

```java
Order order1 = orderRepository.findById(1L).orElseThrow();
Order order2 = orderRepository.findById(1L).orElseThrow();
```

같은 트랜잭션 안에서 SQL이 몇 번 발생하는지 확인한다.

목표:

```text
Persistence Context
→ 1차 캐시
→ 동일 Entity 식별자 조회
→ 이미 관리 중이면 DB 재조회가 생략될 수 있음
```

이 실습을 통해 Persistence Context와 애플리케이션 전역 캐시가 다르다는 점도 확인한다.

---

## 15. 테스트 코드

각 조회 전략에 대해 최소 하나의 통합 테스트를 작성한다.

예시:

```java
@SpringBootTest
@Transactional
class OrderQueryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void nPlusOne() {
        List<Order> orders = orderRepository.findAll();

        for (Order order : orders) {
            order.getItems().size();
        }
    }
}
```

초기에는 SQL 로그를 직접 확인해도 된다.

추가 학습이 필요하면 Hibernate Statistics 또는 datasource-proxy/P6Spy 등을 이용해 쿼리 수를 자동 검증한다.

---

## 16. 권장 패키지 구조

```text
src/main/java/com/example/jpaquery
├── domain
│   ├── User.java
│   ├── Order.java
│   ├── OrderItem.java
│   └── OrderStatus.java
│
├── repository
│   ├── UserRepository.java
│   ├── OrderRepository.java
│   └── OrderItemRepository.java
│
├── service
│   └── OrderQueryService.java
│
├── api
│   ├── OrderQueryController.java
│   └── dto
│       ├── OrderResponse.java
│       └── OrderSummaryResponse.java
│
└── config
    └── DataInitializer.java
```

---

## 17. 구현 순서

### Step 1

Spring Boot 프로젝트 생성 및 JPA/H2 연결.

### Step 2

`User`, `Order`, `OrderItem` Entity와 연관관계 구현.

### Step 3

샘플 데이터 생성.

### Step 4

SQL 로그 활성화.

### Step 5

기본 LAZY 조회 구현.

### Step 6

연관 Entity 접근을 통해 N+1 의도적으로 발생.

### Step 7

SQL 로그를 통해 N+1 확인.

### Step 8

Fetch Join Repository 메서드 구현 후 SQL 비교.

### Step 9

Batch Fetch 설정 후 동일 코드의 SQL 변화 확인.

### Step 10

DTO Projection 조회 구현.

### Step 11

네 가지 조회 전략을 API 또는 테스트 코드에서 비교.

### Step 12

결과와 각 방식의 Trade-off를 문서화.

### Step 13 — 선택

Pagination / Persistence Context / Query Count 자동 검증 추가.

---

## 18. 학습 완료 체크리스트

- [ ] JPA의 LAZY Loading이 언제 SQL을 발생시키는지 설명할 수 있다.
- [ ] EAGER를 기본 전략으로 사용하는 것이 위험할 수 있는 이유를 설명할 수 있다.
- [ ] N+1 문제를 직접 재현할 수 있다.
- [ ] Hibernate SQL 로그에서 N+1을 발견할 수 있다.
- [ ] Fetch Join으로 N+1을 해결할 수 있다.
- [ ] Fetch Join과 일반 Join의 차이를 설명할 수 있다.
- [ ] OneToMany Fetch Join의 주의점을 설명할 수 있다.
- [ ] Batch Fetching의 `IN` 쿼리를 SQL 로그에서 확인할 수 있다.
- [ ] Fetch Join과 Batch Fetch의 선택 기준을 설명할 수 있다.
- [ ] DTO Projection을 구현할 수 있다.
- [ ] Entity 조회와 DTO Projection의 차이를 설명할 수 있다.
- [ ] Persistence Context가 전역 캐시가 아니라는 점을 설명할 수 있다.
- [ ] 조회 성능 문제를 단순히 "N+1 = Fetch Join"으로 판단하지 않고 조회 요구사항에 맞춰 전략을 선택할 수 있다.

---

## 19. 최종적으로 정리해야 할 핵심 개념

프로젝트를 완료한 뒤 아래 흐름을 자신의 말로 설명할 수 있으면 학습 목표를 달성한 것이다.

```text
Entity 연관관계를 LAZY로 설계
→ 실제 조회 API 구현
→ 연관 Entity 접근
→ 추가 SQL 발생 여부 확인
→ N+1 발견

→ 필요한 관계를 한 번에 조회해야 하면 Fetch Join
→ Collection/Pagination 등의 이유로 Fetch Join이 불편하면 Batch Fetch 고려
→ Entity 자체가 필요 없는 조회 API라면 DTO Projection 고려

→ SQL 로그와 실제 요구사항을 기준으로 조회 전략 선택
```

핵심 원칙:

> JPA 조회 성능 최적화의 목적은 모든 연관 데이터를 미리 가져오는 것이 아니라, 각 API가 실제로 필요한 데이터를 최소한의 쿼리와 적절한 메모리 비용으로 조회하도록 설계하는 것이다.

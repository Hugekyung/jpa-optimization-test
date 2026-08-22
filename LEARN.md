# JPA 조회 전략 학습 내용

## 1. 이번 PoC의 목적

같은 주문 데이터를 여러 JPA 조회 방식으로 조회하면서 다음을 비교한다.

- 조회 결과가 같은지
- SQL이 몇 번 실행되는지
- 어떤 Entity 또는 컬럼을 조회하는지
- Pagination이 DB에서 적용되는지
- 연관관계 조회 방식별 장단점과 문제점

핵심은 SQL 실행 횟수가 가장 적은 방식을 고르는 것이 아니라, 조회 목적과 데이터 규모에 맞는 방식을 선택하는 것이다.

## 2. LAZY 조회

```java
@Query("""
    select o
    from Order o
    order by o.id
    """)
Page<Order> findAll(Pageable pageable);
```

`Order.user`가 LAZY라면 처음에는 Order만 조회한다. 이후 다음 코드가 실행될 때 User 조회 SQL이 추가된다.

```java
order.getUser().getName();
```

### 장점

- 실제로 사용하지 않는 연관 데이터를 처음부터 조회하지 않는다.
- 기본 Entity 구조를 유지하기 쉽다.

### 주의점

- 반복문에서 `getUser()`나 `getItems()`를 호출하면 N+1이 발생할 수 있다.
- DTO 변환 과정에서도 지연 로딩이 실행될 수 있다.
- LAZY 설정만으로 N+1이 해결되지는 않는다.

## 3. To-One Fetch Join

```java
@Query("""
    select o
    from Order o
    join fetch o.user
    order by o.id
    """)
Page<Order> findPageWithUser(Pageable pageable);
```

Order와 User를 JOIN으로 한 번에 조회하므로 Order마다 User를 따로 조회하지 않는다.

### 장점

- N+1 방지에 효과적이다.
- To-One 관계에서는 Pagination과 함께 사용하기 비교적 안전하다.

### 주의점

- 실제 응답에서 사용하는 연관관계만 Fetch Join해야 한다.
- 필요하지 않은 연관 데이터까지 조회하면 낭비가 된다.
- 여러 연관관계를 한 번에 Fetch Join하면 조회 데이터가 커질 수 있다.

## 4. Collection Fetch Join

```java
@Query("""
    select distinct o
    from Order o
    left join fetch o.items
    order by o.id
    """)
List<Order> findAllWithItems();
```

Order 하나에 Item이 3개라면 SQL 결과는 Order가 3개의 Row로 반복된다. `distinct`는 JPA Entity 결과의 Order 중복을 제거한다.

### 장점

- Order와 Item을 한 번에 조회한다.
- Item 접근으로 인한 추가 SELECT를 줄일 수 있다.

### 주의점

- Item 개수만큼 JOIN 결과 Row가 증가한다.
- 데이터가 많으면 DB 조회량과 메모리 사용량이 증가한다.
- 여러 Collection을 동시에 Fetch Join하면 결과가 곱집합처럼 커질 수 있다.

### Collection Fetch Join과 Pagination

```java
@Query("""
    select distinct o
    from Order o
    left join fetch o.items
    order by o.id
    """)
List<Order> findPageWithItems(Pageable pageable);
```

페이지 크기가 20이면 DB에서 Order 20개만 조회할 것으로 생각하기 쉽다. 하지만 Hibernate가 Collection Fetch Join과 Pageable을 함께 처리하면 다음과 같이 동작할 수 있다.

```text
DB: 전체 Order + Item Join 결과 조회
Hibernate: 결과를 메모리에 적재한 뒤 메모리에서 페이지 적용
```

따라서 다음 문제가 발생할 수 있다.

- DB가 전체 Join 결과를 조회한다.
- DB에서 서버로 전달되는 데이터가 증가한다.
- Hibernate 메모리 사용량이 증가한다.
- 응답 시간이 늘어난다.
- 데이터가 매우 크면 OutOfMemoryError가 발생할 수 있다.

이 문제는 테스트 환경에서만 발생하는 것이 아니라 실제 서버에서도 발생할 수 있다.

이번 PoC의 Collection Fetch Join 방식은 이 현상을 재현하고 비교하기 위한 구현이다. 운영용 기본 조회 방식으로 사용하는 코드는 아니다.

## 5. Batch Fetch

```java
@Query("""
    select o
    from Order o
    order by o.id
    """)
Page<Order> findPage(Pageable pageable);
```

LAZY 구조를 유지하면서 여러 연관관계 조회를 `IN` 쿼리로 묶는다.

```sql
select *
from users
where id in (?, ?, ?, ...)
```

### 장점

- LAZY 구조를 유지한다.
- N+1을 완화한다.
- Pagination과 함께 사용하기 좋다.
- Collection Fetch Join처럼 JOIN 결과가 크게 증가하지 않는다.

### 주의점

- SQL이 완전히 1번으로 줄어드는 것은 아니다.
- Batch 크기에 따라 실행 횟수가 달라진다.
- 실제로 연관관계에 접근해야 Batch Fetch가 실행된다.

## 6. DTO Projection

```java
@Query("""
    select new com.example.jpaquery.repository.projection.OrderSummaryProjection(
        o.id,
        u.name,
        o.status
    )
    from Order o
    join o.user u
    order by o.id
    """)
Page<OrderSummaryProjection> findOrderSummaryPage(Pageable pageable);
```

Entity 전체가 아니라 화면에 필요한 컬럼만 조회한다.

### 장점

- 필요한 컬럼만 조회한다.
- 불필요한 Entity 생성을 줄인다.
- 연관관계 접근으로 인한 N+1을 방지한다.
- Pagination과 잘 맞는다.
- 목록 API와 읽기 전용 조회에 적합하다.

### 주의점

- Entity 변경 감지나 도메인 로직에는 적합하지 않다.
- Projection 생성자와 JPQL 컬럼 순서가 맞아야 한다.
- 응답 필드가 바뀌면 쿼리도 함께 수정해야 한다.

## 7. Page와 Slice

### Page

```java
Page<Order> findAll(Pageable pageable);
```

Page는 데이터 조회와 함께 전체 개수를 조회한다.

```text
현재 페이지 데이터 조회
전체 데이터 개수 조회
전체 페이지 수 계산
```

전체 페이지 수가 필요한 화면에 적합하지만 Count Query 비용이 추가될 수 있다.

### Slice

```java
Slice<OrderSummaryProjection> findOrderSummarySlice(Pageable pageable);
```

Slice는 전체 개수를 세지 않고 다음 페이지가 있는지만 확인한다.

```text
size가 20이면 21개 조회
21번째 데이터가 있으면 hasNext = true
응답에는 20개만 반환
```

무한 스크롤처럼 전체 개수가 필요 없는 화면에 적합하다.

## 8. Pagination과 Fetch Join의 실무 기준

### 비교적 안전한 조합

```text
To-One Fetch Join + Pagination
DTO Projection + Pagination
Batch Fetch + Pagination
```

### 주의가 필요한 조합

```text
Collection Fetch Join + Pagination
```

Collection이 포함된 페이지 조회에서는 다음 방식을 우선 검토한다.

- Batch Fetch
- DTO Projection
- 2단계 조회

## 9. 2단계 조회 방식

### 1단계: 페이지 대상 ID 조회

```java
@Query("""
    select o.id
    from Order o
    order by o.id
    """)
Page<Long> findOrderIds(Pageable pageable);
```

### 2단계: 현재 페이지의 Collection 조회

```java
@Query("""
    select distinct o
    from Order o
    left join fetch o.items
    where o.id in :orderIds
    order by o.id
    """)
List<Order> findOrdersWithItems(@Param("orderIds") List<Long> orderIds);
```

먼저 Order ID를 페이지 단위로 제한한 뒤, 해당 ID에 대해서만 Item을 Fetch Join한다.

### 장점

- 전체 Collection을 메모리에 올리는 문제를 완화한다.
- DB에서 페이지 범위를 먼저 제한한다.
- Collection 데이터를 포함하면서 Pagination을 처리할 수 있다.

### 주의점

- 쿼리가 2번 이상 실행된다.
- `IN` 조건의 ID 수를 적절히 관리해야 한다.
- 조회 결과 순서를 다시 맞춰야 할 수 있다.
- 트랜잭션 범위와 데이터 일관성을 고려해야 한다.

2단계 조회는 실제 운영 코드에는 적합하지만, 현재 PoC의 목적은 Collection Fetch Join + Pagination에서 메모리 페이징이 발생할 수 있음을 확인하는 것이다. 따라서 현재 비교 대상 코드를 2단계 조회로 교체하면 안 된다.

## 10. 성능 측정 시 주의점

현재처럼 `System.nanoTime()`으로 한 번 실행한 시간을 측정하는 방식은 정밀한 벤치마크가 아니다.

```java
long startedAt = System.nanoTime();
List<OrderSummaryResponse> responses = query.get();
long elapsed = System.nanoTime() - startedAt;
```

### 한계

- JVM 워밍업이 없다.
- 반복 실행과 평균·중앙값 계산이 없다.
- 실행 순서와 시스템 상태의 영향을 받는다.
- 테스트 환경과 운영 환경의 차이가 크다.

정밀한 측정이 필요하면 워밍업, 반복 실행, 평균 또는 중앙값 계산을 적용하고 JMH나 별도 부하 테스트 도구를 고려한다.

현재 PoC에서는 SQL 횟수와 결과 일치 여부가 핵심이므로 실행 시간은 참고 지표로 봐도 된다.

## 11. 공정한 조회 비교 조건

조회 방식별 비교에서는 다음 조건을 동일하게 유지해야 한다.

- 동일한 테스트 데이터
- 동일한 조회 조건
- 동일한 정렬 조건
- 동일한 페이지 번호와 크기
- 동일한 응답 필드
- 동일한 트랜잭션 범위
- 동일한 영속성 컨텍스트 상태

이전 조회에서 이미 Entity가 영속성 컨텍스트에 들어가 있으면 SQL이 줄어들 수 있다. 따라서 비교 전 다음과 같이 초기화하는 것이 중요하다.

```java
entityManager.clear();
```

## 12. SQL 실행 횟수만으로 판단하면 안 되는 이유

SQL이 1번이라고 항상 좋은 것은 아니다.

```text
SQL 1번 + 매우 많은 Row와 컬럼 조회
```

는 다음보다 비효율적일 수 있다.

```text
SQL 3번 + 필요한 데이터만 작은 범위로 조회
```

다음 항목을 함께 확인해야 한다.

- SQL 실행 횟수
- 조회 Row 수
- 조회 컬럼 수
- Join으로 인한 중복 Row
- DB 부하
- 네트워크 전송량
- 애플리케이션 메모리 사용량
- 응답 시간
- Pagination 적용 위치
- 유지보수성

## 13. Entity 조회와 DTO 조회의 선택 기준

### Entity 조회

```java
@Query("""
    select o
    from Order o
    join fetch o.user
    where o.id = :orderId
    """)
Order findOrderWithUser(@Param("orderId") Long orderId);
```

도메인 로직을 수행하거나 수정 후 저장해야 하는 경우에 적합하다.

### DTO 조회

```java
@Query("""
    select new com.example.jpaquery.repository.projection.OrderSummaryProjection(
        o.id,
        u.name,
        o.status
    )
    from Order o
    join o.user u
    where o.id = :orderId
    """)
OrderSummaryProjection findSummary(@Param("orderId") Long orderId);
```

목록 API, 검색 결과, 읽기 전용 화면처럼 필요한 필드가 명확한 경우에 적합하다.

## 14. 핵심 정리

- LAZY는 불필요한 조회를 줄이지만 연관관계 접근 시 N+1이 발생할 수 있다.
- To-One Fetch Join은 N+1 방지와 Pagination에 비교적 적합하다.
- Collection Fetch Join은 JOIN 결과 Row와 메모리 사용량이 증가할 수 있다.
- Collection Fetch Join + Pagination은 메모리 페이징 위험이 있다.
- 메모리 페이징은 테스트 환경뿐 아니라 실제 서버에서도 발생할 수 있다.
- Batch Fetch는 Pagination과 연관 데이터 조회를 함께 처리할 때 유용하다.
- DTO Projection은 목록 API와 읽기 전용 조회에 적합하다.
- Page는 Count Query 비용을 고려해야 한다.
- Slice는 전체 개수가 필요 없는 화면에 적합하다.
- SQL 실행 횟수만으로 최적 방식을 판단하면 안 된다.
- 조회 Row, 컬럼, 메모리, DB 부하, 응답 시간을 함께 확인해야 한다.
- 대용량 Collection Pagination은 2단계 조회나 Batch Fetch를 우선 검토한다.
- 현재 PoC의 Collection Fetch Join 방식은 운영용 기본 구현이 아니라 문제 재현과 비교를 위한 구현이다.

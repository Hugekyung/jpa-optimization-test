# JPA Query Optimization Test

JPA 연관관계 조회 전략에 따른 SQL 실행 차이를 확인하는 실습 프로젝트입니다.

## 테스트 환경

- Java 21
- Spring Boot 3.5.5
- Spring Data JPA / Hibernate
- H2
- 테스트 데이터: User 10명, Order 100개, OrderItem 500개
- 동일 조회 조건: `Order` 전체 조회, `id ASC` 정렬, Order 100건 응답

## 조회 전략 비교

`QueryStrategyComparisonTest`에서 같은 조건과 응답 범위로 네 가지 방식을 비교합니다.

| 방식 | 결과 건수 | SQL 횟수 | 실행 시간(ns)* | SQL 특징 | 장점 | 주의점 |
|---|---:|---:|---:|---|---|---|
| N+1 | 100 | 111 | 9,877,959 | Order 1회 + User/OrderItem 반복 SELECT | 동작이 단순하고 기본 LAZY 구조 유지 | 연관관계 접근마다 추가 SQL 발생 |
| Fetch Join | 100 | 1 | 1,910,417 | `JOIN`으로 User 함께 조회 | N+1을 한 번의 SQL로 줄임 | Collection Fetch Join의 중복 Row와 Pagination 주의 |
| Batch Fetch | 100 | 12 | 7,383,709 | 연관관계를 `IN (...)`으로 묶음 | LAZY 구조를 유지하면서 SQL 감소 | 여러 SELECT는 남아 있고 Batch 크기 조정 필요 |
| DTO Projection | 100 | 1 | 1,666,334 | 필요한 컬럼만 `SELECT` | Entity 생성 및 불필요한 컬럼 조회 감소 | Entity 변경 감지나 연관관계 기능 사용 불가 |

\* 실행 시간은 동일 환경에서 1회 실행한 측정 예시이며, 실행 환경에 따라 달라질 수 있습니다.

### SQL 형태

```text
N+1
  select ... from orders order by id
  select ... from users where id = ?
  select ... from order_items where order_id = ?
  ...

Fetch Join
  select ...
  from orders
  join users on ...
  order by id

Batch Fetch
  select ... from orders order by id
  select ... from users where id in (?, ...)
  select ... from order_items where order_id in (?, ...)

DTO Projection
  select order.id, user.name, order.status
  from orders
  join users on ...
  order by id
```

## API

네 API는 동일한 주문 요약 응답을 반환합니다.

```json
{
  "orderId": 1,
  "userName": "User 1",
  "status": "PENDING"
}
```

- `GET /api/orders/n-plus-one`
- `GET /api/orders/fetch-join`
- `GET /api/orders/batch-fetch`
- `GET /api/orders/projection`

## 선택 기준

- 연관관계가 항상 필요하고 To-One 관계라면 Fetch Join 고려
- Collection과 Pagination 제약을 피하면서 LAZY를 유지하려면 Batch Fetch 고려
- 조회 전용 응답이고 필요한 컬럼이 제한적이면 DTO Projection 고려
- 연관관계 접근이 반복되면 기본 LAZY 조회에서 N+1이 발생할 수 있으므로 SQL 로그로 확인

## Pagination 선택 과제

### API

- `GET /api/orders?page=0&size=20`: `Page` 기반 기본 LAZY 조회
- `GET /api/orders/slice?page=0&size=20`: `Slice` 기반 DTO Projection 조회

### 첫 페이지 20건 조회 비교

`PaginationTest`에서 동일한 첫 페이지 조건(`id ASC`, `page=0`, `size=20`)으로 결과와 SQL을 비교했습니다.

| 방식 | 결과 건수 | SQL 횟수 | Count Query | SQL 특징 | 주의점 |
|---|---:|---:|:---:|---|---|
| LAZY | 20 | 4 | O | Order 조회 + User 2회 | 페이지 안의 User 수만큼 추가 SELECT |
| To-One Fetch Join | 20 | 2 | O | Order와 User JOIN | To-One 관계에서는 Page와 함께 사용 가능 |
| Collection Fetch Join | 20 | 3 | X | Collection 전체 Row 조회 후 메모리 페이징 | `limit` 없이 전체 Collection을 읽을 수 있음 |
| Batch Fetch | 20 | 3 | O | 페이지 Order 조회 + User `IN` | 연관관계 추가 조회는 남음 |
| DTO Projection | 20 | 2 | O | 필요한 컬럼만 조회 | Entity 상태 변경에는 사용할 수 없음 |

### Page와 Slice

```text
Page  = content 조회 + 전체 Count Query
        전체 페이지 수, totalElements 제공

Slice = 다음 페이지 존재 확인을 위한 content 조회
        Count Query 없음, totalElements 미제공
```

Collection Fetch Join에 Pagination을 함께 적용하면 DB가 20개의 Order만 자르는 대신 Collection Join 결과 전체를 읽고 애플리케이션 메모리에서 페이징할 수 있습니다. Pagination이 필요한 Collection 조회는 Batch Fetch 또는 `Order ID`를 먼저 페이지 조회한 뒤 Collection을 별도로 조회하는 방식을 우선 고려합니다.

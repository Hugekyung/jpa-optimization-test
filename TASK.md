# JPA 조회 성능 최적화 실습 TASK

## 프로젝트 목표

동일한 주문 데이터를 여러 JPA 조회 방식으로 조회하면서 실제 SQL을 비교한다. 이를 통해 N+1 문제가 발생하는 이유와 상황별 최적 조회 전략을 이해한다.

최종적으로 다음 기준으로 조회 방식을 선택할 수 있어야 한다.

- 연관관계는 기본적으로 `LAZY`로 설계한다.
- 필요한 연관 데이터를 한 번에 조회하면 `Fetch Join`을 고려한다.
- Collection 조회나 Pagination 때문에 Fetch Join이 부담되면 `Batch Fetch`를 고려한다.
- Entity 상태 변경이 필요 없는 조회 전용 API는 `DTO Projection`을 고려한다.

## 기본 도메인

- [x] `User` Entity 구현: `id`, `name`
- [x] `Order` Entity 구현: `id`, `user`, `status`, `createdAt`
- [x] `OrderItem` Entity 구현: `id`, `order`, `productName`, `price`, `quantity`
- [x] `User 1:N Order`, `Order 1:N OrderItem` 연관관계 구현
- [x] 연관관계의 기본 Fetch 전략을 `LAZY`로 설정

## 구현 순서

### 1. 프로젝트 구성

- [x] Spring Boot, Spring Data JPA, H2, Gradle 설정
- [x] JUnit 5 테스트 환경 구성 및 Spring context 로딩 테스트 통과

### 2. 테스트 데이터 생성

- [x] 애플리케이션 시작 시 User 10명 생성
- [x] User마다 Order 10개 생성
- [x] Order마다 OrderItem 5개 생성
- [x] 총 User 10개, Order 100개, OrderItem 500개 확인

### 3. SQL 로그 설정

- [x] Hibernate SQL 로그 활성화
- [x] 바인딩 파라미터와 포맷팅된 SQL 확인
- [ ] 각 실습에서 SQL 횟수, JOIN 여부, `IN (...)` 발생 여부 기록

#### Task 3 추가 작업

- [x] Hibernate Statistics 활성화
- [x] `StatementInspector` 기반 SQL 추적기 추가
- [x] 조회 결과 건수, Query 횟수, SQL 횟수, JOIN 여부, `IN (...)` 여부를 수집하는 테스트 도우미 추가
- [x] 수집 결과를 Markdown 표와 SQL 목록으로 출력하는 테스트 코드 추가
- [x] 동일 조건의 주문 조회 결과와 수집 결과 출력 검증

### 4. LAZY Loading 확인

- [x] `GET /api/orders` 또는 동일한 테스트 코드로 Order 목록 조회
- [x] User와 OrderItem에 접근하지 않을 때 추가 SELECT가 발생하지 않는지 확인
- [x] 연관 Entity에 실제로 접근하는 시점을 확인

### 5. N+1 문제 재현

- [x] Order 목록을 먼저 조회
- [x] 각 Order의 User 이름에 접근해 추가 SELECT 발생 확인
- [x] 각 Order의 OrderItem에 접근해 추가 SELECT 발생 확인
- [x] 부모 조회 1회와 연관 조회 N회가 발생하는 구조를 설명
- [x] Persistence Context의 1차 캐시 때문에 실제 쿼리 수가 달라질 수 있음을 확인

### 6. Fetch Join 적용

- [x] Order와 User를 Fetch Join으로 조회하는 Repository 메서드 구현
- [x] `GET /api/orders/fetch-join` 구현
- [x] JOIN으로 N+1이 줄어드는지 SQL 로그로 확인
- [x] Collection Fetch Join과 `distinct` 동작 확인
- [x] OneToMany Fetch Join의 중복 Row와 Pagination 주의점 기록

> Collection Fetch Join은 Collection 요소 수만큼 SQL Row가 늘어날 수 있으며, `distinct`로 Entity 결과의 중복을 제거한다. Collection Fetch Join과 Pagination을 함께 사용하면 joined Row 기준 페이징으로 결과가 왜곡되거나 메모리 페이징이 발생할 수 있으므로 주의한다.

### 7. Batch Fetch 적용

- [x] Hibernate `default_batch_fetch_size` 설정
- [x] 기존 LAZY 접근 코드를 유지한 채 SQL 변화 확인
- [x] 여러 개의 SELECT가 `IN (...)` 쿼리로 묶이는지 확인
- [x] Fetch Join과 Batch Fetch의 SQL 차이와 선택 기준 기록

> `default_batch_fetch_size: 10` 설정 후 동일한 Order 100건과 User, OrderItem 전체 접근 조건에서 SQL이 111회에서 12회로 줄고, 연관관계 조회가 `IN (...)` 쿼리로 묶였다. Fetch Join은 한 번의 JOIN으로 즉시 조회하지만 조회 경로와 Pagination 제약이 있고, Batch Fetch는 LAZY 구조를 유지하면서 여러 SELECT를 묶으므로 화면별 연관관계 접근이 선택적인 경우에 적합하다.

### 8. DTO Projection 적용

- [ ] `OrderSummaryResponse` DTO 구현
- [ ] 필요한 컬럼만 조회하는 JPQL Constructor Expression 구현
- [ ] `GET /api/orders/projection` 구현
- [ ] Entity 조회와 DTO Projection의 SELECT 컬럼 차이 확인
- [ ] 조회 전용 API에서 Projection이 유리한 이유 기록

### 9. 조회 전략 비교 API 구성

- [ ] `GET /api/orders/n-plus-one` 구현
- [ ] `GET /api/orders/fetch-join` 구현
- [ ] `GET /api/orders/batch-fetch` 구현
- [ ] `GET /api/orders/projection` 구현
- [ ] 가능한 한 동일한 응답 형태로 네 방식 비교

### 10. 테스트와 결과 기록

- [ ] 각 조회 전략별 통합 테스트 작성
- [ ] 각 방식의 실제 SQL 실행 횟수 기록
- [ ] SQL 모양, 장점, 주의점을 비교표로 정리
- [ ] `README.md` 또는 별도 문서에 실습 결과 작성

## 선택 과제

- [ ] Pagination 적용: `GET /api/orders?page=0&size=20`
- [ ] 기본 LAZY, Collection Fetch Join, Batch Fetch, DTO Projection 비교
- [ ] Fetch Join과 Pagination의 문제 확인
- [ ] `Page`와 `Slice`의 차이 및 Count Query 발생 여부 확인
- [ ] 동일 트랜잭션에서 같은 Entity를 다시 조회해 1차 캐시 동작 확인
- [ ] Hibernate Statistics, datasource-proxy 또는 P6Spy로 쿼리 수 자동 검증

## 최종 비교표

| 방식 | 확인할 SQL 특징 | 장점 | 주의점 |
|---|---|---|---|
| LAZY + 단순 조회 | 연관 접근 시 추가 SELECT | 단순하고 기본값으로 적합 | N+1 발생 가능 |
| Fetch Join | JOIN으로 연관 데이터 함께 조회 | N+1 해결에 효과적 | Collection, 중복 Row, Pagination 주의 |
| Batch Fetch | 여러 연관 조회를 `IN` 쿼리로 묶음 | Collection과 Pagination에 유용 | 추가 쿼리는 남을 수 있음 |
| DTO Projection | 필요한 컬럼만 직접 SELECT | 조회 API 효율적 | Entity 기능과 상태 변경 사용 불가 |

## 완료 기준

- [ ] LAZY Loading이 언제 SQL을 발생시키는지 설명할 수 있다.
- [ ] N+1 문제를 직접 재현하고 SQL 로그에서 찾을 수 있다.
- [ ] Fetch Join으로 N+1을 줄일 수 있다.
- [ ] Batch Fetch의 `IN` 쿼리를 확인할 수 있다.
- [ ] DTO Projection과 Entity 조회의 차이를 설명할 수 있다.
- [ ] Persistence Context가 전역 캐시가 아님을 설명할 수 있다.
- [ ] API 요구사항에 따라 조회 전략을 선택할 수 있다.

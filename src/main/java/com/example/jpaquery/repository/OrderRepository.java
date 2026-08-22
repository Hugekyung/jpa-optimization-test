package com.example.jpaquery.repository;

import com.example.jpaquery.domain.Order;
import com.example.jpaquery.repository.projection.OrderSummaryProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("select o from Order o join fetch o.user order by o.id")
    List<Order> findAllWithUser();

    @Query("select distinct o from Order o left join fetch o.items order by o.id")
    List<Order> findAllWithItems();

    @Query("select o from Order o join fetch o.user order by o.id")
    Page<Order> findPageWithUser(Pageable pageable);

    @Query("select distinct o from Order o left join fetch o.items order by o.id")
    List<Order> findPageWithItems(Pageable pageable);

    @Query("""
        select new com.example.jpaquery.repository.projection.OrderSummaryProjection(o.id, u.name, o.status)
        from Order o
        join o.user u
        order by o.id
        """)
    List<OrderSummaryProjection> findOrderSummaries();

    @Query("""
        select new com.example.jpaquery.repository.projection.OrderSummaryProjection(o.id, u.name, o.status)
        from Order o
        join o.user u
        order by o.id
        """)
    Page<OrderSummaryProjection> findOrderSummaryPage(Pageable pageable);

    @Query("""
        select new com.example.jpaquery.repository.projection.OrderSummaryProjection(o.id, u.name, o.status)
        from Order o
        join o.user u
        order by o.id
        """)
    Slice<OrderSummaryProjection> findOrderSummarySlice(Pageable pageable);
}

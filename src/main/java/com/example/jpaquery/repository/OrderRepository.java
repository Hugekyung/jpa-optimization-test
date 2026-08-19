package com.example.jpaquery.repository;

import com.example.jpaquery.domain.Order;
import com.example.jpaquery.repository.projection.OrderSummaryProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("select o from Order o join fetch o.user order by o.id")
    List<Order> findAllWithUser();

    @Query("select distinct o from Order o left join fetch o.items order by o.id")
    List<Order> findAllWithItems();

    @Query("""
        select new com.example.jpaquery.repository.projection.OrderSummaryProjection(o.id, u.name, o.status)
        from Order o
        join o.user u
        order by o.id
        """)
    List<OrderSummaryProjection> findOrderSummaries();
}

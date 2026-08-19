package com.example.jpaquery.repository;

import com.example.jpaquery.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("select o from Order o join fetch o.user order by o.id")
    List<Order> findAllWithUser();

    @Query("select distinct o from Order o left join fetch o.items order by o.id")
    List<Order> findAllWithItems();
}

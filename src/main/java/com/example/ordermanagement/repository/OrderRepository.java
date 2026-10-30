package com.example.ordermanagement.repository;

import com.example.ordermanagement.entity.CustomerOrder;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<CustomerOrder, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CustomerOrder o where o.id = :id")
    Optional<CustomerOrder> findByIdForUpdate(@Param("id") Long id);

    @Query("select distinct o from CustomerOrder o left join fetch o.items where o.id = :id")
    Optional<CustomerOrder> findDetailedById(@Param("id") Long id);
}

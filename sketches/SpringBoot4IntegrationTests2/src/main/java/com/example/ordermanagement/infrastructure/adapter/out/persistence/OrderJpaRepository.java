package com.example.ordermanagement.infrastructure.adapter.out.persistence;

import com.example.ordermanagement.infrastructure.adapter.out.persistence.entity.OrderJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, UUID> {

    List<OrderJpaEntity> findByCustomerId(String customerId);
}

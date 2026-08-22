package com.trading.signals;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByPositionId(Long positionId);
    List<Order> findByUserId(Long userId);
    Page<Order> findByUserIdOrderByPlacedAtDesc(Long userId, Pageable pageable);
    java.util.Optional<Order> findFirstByPositionIdAndType(Long positionId, OrderType type);
}

package com.trading.signals;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PositionRepository extends JpaRepository<Position, Long> {
    List<Position> findByUserId(Long userId);
    List<Position> findByUserIdAndStatus(Long userId, PositionStatus status);
    boolean existsBySignalIdAndStatusIn(Long signalId, List<PositionStatus> statuses);
}

package com.trading.signals;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PositionRepository extends JpaRepository<Position, Long> {
    List<Position> findByUserId(Long userId);
    List<Position> findByUserIdAndStatus(Long userId, PositionStatus status);
    List<Position> findByUserIdAndStatusIn(Long userId, List<PositionStatus> statuses);
    List<Position> findByStatus(PositionStatus status);
    long countByUserIdAndStatusIn(Long userId, List<PositionStatus> statuses);
    boolean existsBySignalIdAndStatusIn(Long signalId, List<PositionStatus> statuses);
    boolean existsByUserIdAndSignalIdAndStatusIn(Long userId, Long signalId, List<PositionStatus> statuses);
    List<Position> findByUserIdAndStatusInAndClosedAtAfter(Long userId, List<PositionStatus> statuses, LocalDateTime after);
}

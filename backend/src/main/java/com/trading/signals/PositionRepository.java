package com.trading.signals;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Query("SELECT p FROM Position p LEFT JOIN FETCH p.signal WHERE p.status = :status")
    List<Position> findByStatusFetchSignal(@Param("status") PositionStatus status);

    @Query("SELECT p FROM Position p LEFT JOIN FETCH p.signal s WHERE p.status = 'ACTIVE' AND s.closingBasis = :basis")
    List<Position> findActiveBySignalClosingBasis(@Param("basis") StopLossBasis basis);
}

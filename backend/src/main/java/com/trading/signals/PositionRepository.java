package com.trading.signals;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

    @Query("SELECT p FROM Position p LEFT JOIN FETCH p.signal WHERE p.id = :id")
    Optional<Position> findByIdFetchSignal(@Param("id") Long id);

    @Query("SELECT p FROM Position p LEFT JOIN FETCH p.signal s WHERE p.status = 'ACTIVE' AND s.closingBasis = :basis")
    List<Position> findActiveBySignalClosingBasis(@Param("basis") StopLossBasis basis);

    /**
     * Atomic conditional update: only applies if the position's quantity still matches
     * {@code expectedQuantity} at write time. Returns the number of rows updated (0 or 1) so the
     * caller can detect a concurrent modification (e.g. a scheduler-triggered exit/partial-exit
     * racing a manual add/remove) instead of silently overwriting it.
     */
    @Modifying
    @Query("UPDATE Position p SET p.quantity = :newQuantity, p.avgEntryPrice = :newAvgPrice, " +
           "p.gttOrderId = :gttId, p.gttQuantity = :gttQuantity " +
           "WHERE p.id = :id AND p.quantity = :expectedQuantity")
    int updateQuantityIfUnchanged(@Param("id") Long id, @Param("expectedQuantity") int expectedQuantity,
                                  @Param("newQuantity") int newQuantity, @Param("newAvgPrice") BigDecimal newAvgPrice,
                                  @Param("gttId") String gttId, @Param("gttQuantity") Integer gttQuantity);
}

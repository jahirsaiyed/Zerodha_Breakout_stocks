package com.trading.portfolio;

import com.trading.signals.*;
import com.trading.users.UserConfig;
import com.trading.users.UserConfigRepository;
import com.trading.users.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Encapsulates all @Transactional DB operations for the portfolio engine.
 * Exists as a separate bean so PortfolioEngine can call @Transactional methods
 * through the Spring proxy (avoiding self-invocation issues).
 */
@Service
@RequiredArgsConstructor
public class PortfolioDbService {

    private static final List<PositionStatus> ACTIVE_STATUSES =
            List.of(PositionStatus.PENDING_ENTRY, PositionStatus.ACTIVE);

    private final UserConfigRepository userConfigRepository;
    private final UserRepository userRepository;
    private final SignalRepository signalRepository;
    private final PositionRepository positionRepository;
    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public List<UserConfig> getConnectedUsers() {
        return userConfigRepository.findByZerodhaConnectedTrue();
    }

    @Transactional(readOnly = true)
    public Optional<UserConfig> getUserConfigByUserId(Long userId) {
        return userConfigRepository.findByUser_Id(userId);
    }

    @Transactional(readOnly = true)
    public long countActivePositions(Long userId) {
        return positionRepository.countByUserIdAndStatusIn(userId, ACTIVE_STATUSES);
    }

    @Transactional(readOnly = true)
    public Set<String> getOccupiedSymbols(Long userId) {
        return positionRepository.findByUserIdAndStatusIn(userId, ACTIVE_STATUSES)
                .stream().map(Position::getSymbol).collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public List<Signal> getCandidateSignals(Long userId, Set<String> occupiedSymbols) {
        return signalRepository.findByStatus(SignalStatus.ACTIVE).stream()
                .filter(s -> !occupiedSymbols.contains(s.getSymbol()))
                .filter(s -> !positionRepository.existsByUserIdAndSignalIdAndStatusIn(
                        userId, s.getId(), ACTIVE_STATUSES))
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean hasActivePosition(Long userId, Long signalId) {
        return positionRepository.existsByUserIdAndSignalIdAndStatusIn(
                userId, signalId, ACTIVE_STATUSES);
    }

    @Transactional
    public Long createPendingPosition(UserConfig config, Signal signal, int quantity) {
        Position position = Position.builder()
                .user(config.getUser())
                .signal(signal)
                .symbol(signal.getSymbol())
                .quantity(quantity)
                .status(PositionStatus.PENDING_ENTRY)
                .build();
        return positionRepository.save(position).getId();
    }

    @Transactional
    public void recordEntryOrder(Long positionId, UserConfig config, Signal signal,
                                 String zerodhaOrderId, int quantity) {
        Position position = positionRepository.findById(positionId).orElseThrow();

        Order order = Order.builder()
                .user(config.getUser())
                .position(position)
                .zerodhaOrderId(zerodhaOrderId)
                .type(OrderType.ENTRY)
                .orderKind(OrderKind.LIMIT)
                .symbol(signal.getSymbol())
                .quantity(quantity)
                .price(signal.getEntryPrice())
                .status(OrderStatus.PENDING)
                .build();
        orderRepository.save(order);

        position.setEntryOrderId(zerodhaOrderId);
        positionRepository.save(position);
    }

    @Transactional
    public void cancelPosition(Long positionId) {
        positionRepository.findById(positionId).ifPresent(p -> {
            p.setStatus(PositionStatus.CANCELLED);
            positionRepository.save(p);
        });
    }

    @Transactional(readOnly = true)
    public List<Position> getPendingEntryPositions() {
        return positionRepository.findByStatusFetchSignal(PositionStatus.PENDING_ENTRY);
    }

    @Transactional(readOnly = true)
    public List<Position> getActivePositions() {
        return positionRepository.findByStatus(PositionStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<Position> getActivePositionsByBasis(StopLossBasis basis) {
        return positionRepository.findActiveBySignalClosingBasis(basis);
    }

    /**
     * Records a 50% partial exit at target: reduces qty, sets breakeven SL, clears GTT id.
     * Position status stays ACTIVE so the closing-basis scheduler can handle the remainder.
     */
    @Transactional
    public void partialExitPosition(Long positionId, int remainingQty, BigDecimal breakevenSl) {
        Position position = positionRepository.findById(positionId).orElseThrow();
        position.setQuantity(remainingQty);
        position.setBreakevenSl(breakevenSl);
        position.setGttOrderId(null);
        positionRepository.save(position);
    }

    @Transactional
    public void activatePosition(Long positionId, int filledQty,
                                 BigDecimal avgPrice, String gttId) {
        Position position = positionRepository.findById(positionId).orElseThrow();
        position.setStatus(PositionStatus.ACTIVE);
        position.setQuantity(filledQty);
        position.setAvgEntryPrice(avgPrice);
        position.setGttOrderId(gttId);
        position.setOpenedAt(LocalDateTime.now());
        positionRepository.save(position);

        // Mark entry order as filled
        orderRepository.findFirstByPositionIdAndType(positionId, OrderType.ENTRY)
                .ifPresent(o -> { o.setStatus(OrderStatus.FILLED); orderRepository.save(o); });
    }

    @Transactional
    public void markPositionCancelled(Long positionId) {
        Position position = positionRepository.findById(positionId).orElseThrow();
        position.setStatus(PositionStatus.CANCELLED);
        positionRepository.save(position);

        orderRepository.findFirstByPositionIdAndType(positionId, OrderType.ENTRY)
                .ifPresent(o -> { o.setStatus(OrderStatus.CANCELLED); orderRepository.save(o); });
    }

    @Transactional
    public void recordManualExitOrder(Long positionId, String zerodhaOrderId) {
        Position position = positionRepository.findById(positionId).orElseThrow();
        Order order = Order.builder()
                .user(position.getUser())
                .position(position)
                .zerodhaOrderId(zerodhaOrderId)
                .type(OrderType.EXIT_MANUAL)
                .orderKind(OrderKind.MARKET)
                .symbol(position.getSymbol())
                .quantity(position.getQuantity())
                .status(OrderStatus.PENDING)
                .build();
        orderRepository.save(order);
    }

    @Transactional
    public void closePosition(Long positionId, PositionStatus closeStatus, BigDecimal realisedPnl) {
        Position position = positionRepository.findById(positionId).orElseThrow();
        position.setStatus(closeStatus);
        position.setRealisedPnl(realisedPnl);
        position.setClosedAt(LocalDateTime.now());
        positionRepository.save(position);
    }

    @Transactional(readOnly = true)
    public Optional<Order> getEntryOrder(Long positionId) {
        return orderRepository.findFirstByPositionIdAndType(positionId, OrderType.ENTRY);
    }

    @Transactional(readOnly = true)
    public List<Position> getAllPositionsForUser(Long userId) {
        return positionRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<Position> getPositionsByStatus(Long userId, PositionStatus status) {
        return positionRepository.findByUserIdAndStatus(userId, status);
    }

    @Transactional(readOnly = true)
    public Long getUserIdByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found: " + email))
                .getId();
    }

    @Transactional(readOnly = true)
    public Optional<Signal> getSignalById(Long signalId) {
        return signalRepository.findById(signalId);
    }

    @Transactional(readOnly = true)
    public Optional<Position> getPositionById(Long positionId) {
        return positionRepository.findByIdFetchSignal(positionId);
    }
}

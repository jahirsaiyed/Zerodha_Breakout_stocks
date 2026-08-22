package com.trading.signals;

import com.trading.signals.dto.CreateSignalRequest;
import com.trading.signals.dto.SignalResponse;
import com.trading.signals.dto.SyncLogResponse;
import com.trading.signals.dto.UpdateSignalRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SignalService {

    private static final List<PositionStatus> ACTIVE_POSITION_STATUSES =
            List.of(PositionStatus.PENDING_ENTRY, PositionStatus.ACTIVE);
    private static final int SYNC_LOG_PAGE_SIZE = 50;

    private final SignalRepository signalRepository;
    private final PositionRepository positionRepository;
    private final SignalSyncLogRepository syncLogRepository;
    private final InstrumentCacheService instrumentCacheService;

    @Transactional(readOnly = true)
    public List<SignalResponse> list(SignalStatus status) {
        List<Signal> signals = (status == null)
                ? signalRepository.findAll()
                : signalRepository.findByStatus(status);
        return signals.stream().map(SignalResponse::from).toList();
    }

    @Transactional
    public SignalResponse create(CreateSignalRequest req) {
        String symbol = req.symbol().toUpperCase().trim();
        if (!instrumentCacheService.isValidNseSymbol(symbol)) {
            throw new IllegalArgumentException("'" + symbol + "' is not a valid NSE equity symbol.");
        }
        validatePrices(req.entryPrice(), req.stopLoss(), req.target());
        BigDecimal rrr = computeRiskReward(req.entryPrice(), req.stopLoss(), req.target());
        Signal signal = Signal.builder()
                .symbol(symbol)
                .entryPrice(req.entryPrice())
                .stopLoss(req.stopLoss())
                .target(req.target())
                .riskRewardRatio(rrr)
                .source(SignalSource.MANUAL)
                .status(SignalStatus.ACTIVE)
                .notes(req.notes())
                .build();
        return SignalResponse.from(signalRepository.save(signal));
    }

    @Transactional
    public SignalResponse update(Long id, UpdateSignalRequest req) {
        Signal signal = findActiveSignal(id);
        guardNoActivePosition(id);

        BigDecimal entryPrice  = req.entryPrice()  != null ? req.entryPrice()  : signal.getEntryPrice();
        BigDecimal stopLoss    = req.stopLoss()     != null ? req.stopLoss()    : signal.getStopLoss();
        BigDecimal target      = req.target()       != null ? req.target()      : signal.getTarget();

        validatePrices(entryPrice, stopLoss, target);

        signal.setEntryPrice(entryPrice);
        signal.setStopLoss(stopLoss);
        signal.setTarget(target);
        signal.setRiskRewardRatio(computeRiskReward(entryPrice, stopLoss, target));
        if (req.notes() != null) {
            signal.setNotes(req.notes());
        }
        return SignalResponse.from(signalRepository.save(signal));
    }

    @Transactional
    public SignalResponse cancel(Long id) {
        Signal signal = findActiveSignal(id);
        guardNoActivePosition(id);
        signal.setStatus(SignalStatus.CANCELLED);
        return SignalResponse.from(signalRepository.save(signal));
    }

    @Transactional(readOnly = true)
    public List<SyncLogResponse> getSyncLog() {
        return syncLogRepository
                .findAllByOrderBySyncedAtDesc(PageRequest.of(0, SYNC_LOG_PAGE_SIZE))
                .stream()
                .map(SyncLogResponse::from)
                .toList();
    }

    // --- helpers ---

    private Signal findActiveSignal(Long id) {
        Signal signal = signalRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Signal not found: " + id));
        if (signal.getStatus() != SignalStatus.ACTIVE) {
            throw new IllegalArgumentException("Signal " + id + " is not ACTIVE");
        }
        return signal;
    }

    private void guardNoActivePosition(Long signalId) {
        if (positionRepository.existsBySignalIdAndStatusIn(signalId, ACTIVE_POSITION_STATUSES)) {
            throw new IllegalArgumentException(
                    "Signal " + signalId + " has an active or pending position — cannot modify");
        }
    }

    private void validatePrices(BigDecimal entryPrice, BigDecimal stopLoss, BigDecimal target) {
        if (entryPrice.compareTo(stopLoss) <= 0) {
            throw new IllegalArgumentException("entry_price must be greater than stop_loss");
        }
        if (target.compareTo(entryPrice) <= 0) {
            throw new IllegalArgumentException("target must be greater than entry_price");
        }
    }

    private BigDecimal computeRiskReward(BigDecimal entry, BigDecimal sl, BigDecimal target) {
        BigDecimal reward = target.subtract(entry);
        BigDecimal risk   = entry.subtract(sl);
        return reward.divide(risk, 4, RoundingMode.HALF_UP);
    }
}

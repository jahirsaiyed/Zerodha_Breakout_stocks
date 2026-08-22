package com.trading.signals;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Diffs Google Sheet rows against the DB, persists changes, and writes a sync log entry.
 *
 * <p>Identity key: {@code sourceRef} = "{rowNumber}:{SYMBOL}" — stable while a row stays
 * in the same sheet position. Rows that move position get a new sourceRef and will be
 * treated as remove + add on the next sync.
 *
 * <p>Price change on a signal that has an active/pending position is logged as a warning
 * but not applied (Telegram alerting deferred to the notifications module).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SheetSyncService {

    private static final List<PositionStatus> BLOCKING_STATUSES =
            List.of(PositionStatus.PENDING_ENTRY, PositionStatus.ACTIVE);

    private final GoogleSheetsService googleSheetsService;
    private final SignalRepository signalRepository;
    private final PositionRepository positionRepository;
    private final SignalSyncLogRepository syncLogRepository;

    @Transactional
    public SyncResult sync() {
        List<SheetRow> sheetRows = googleSheetsService.fetchRows();

        // Index sheet rows by sourceRef
        Map<String, SheetRow> sheetMap = sheetRows.stream()
                .collect(Collectors.toMap(SheetRow::sourceRef, Function.identity()));

        // Fetch all currently ACTIVE signals that came from Google Sheet
        List<Signal> dbSignals = signalRepository.findBySourceAndStatus(
                SignalSource.GOOGLE_SHEET, SignalStatus.ACTIVE);
        Map<String, Signal> dbMap = dbSignals.stream()
                .collect(Collectors.toMap(Signal::getSourceRef, Function.identity()));

        int added = 0, modified = 0, removed = 0, skipped = 0;

        // --- additions and modifications ---
        for (SheetRow row : sheetRows) {
            Signal existing = dbMap.get(row.sourceRef());
            if (existing == null) {
                // New signal
                signalRepository.save(buildSignal(row));
                added++;
                log.debug("Sheet sync — added signal: {}", row.sourceRef());
            } else {
                // Check if prices changed
                if (pricesChanged(existing, row)) {
                    if (hasActivePosition(existing.getId())) {
                        // Cannot auto-update — active position exists
                        log.warn("Sheet sync — signal {} price changed but has active position; skipping update. " +
                                "Entry: {} -> {}, SL: {} -> {}, Target: {} -> {}",
                                row.sourceRef(),
                                existing.getEntryPrice(), row.entryPrice(),
                                existing.getStopLoss(), row.stopLoss(),
                                existing.getTarget(), row.target());
                        skipped++;
                    } else {
                        applyUpdate(existing, row);
                        signalRepository.save(existing);
                        modified++;
                        log.debug("Sheet sync — modified signal: {}", row.sourceRef());
                    }
                }
                // Notes-only update (always safe)
                else if (notesChanged(existing, row)) {
                    existing.setNotes(row.notes());
                    signalRepository.save(existing);
                    modified++;
                }
            }
        }

        // --- removals: in DB but not in sheet ---
        for (Signal signal : dbSignals) {
            if (!sheetMap.containsKey(signal.getSourceRef())) {
                if (hasActivePosition(signal.getId())) {
                    log.warn("Sheet sync — signal {} removed from sheet but has active position; skipping cancel",
                            signal.getSourceRef());
                    skipped++;
                } else {
                    signal.setStatus(SignalStatus.CANCELLED);
                    signalRepository.save(signal);
                    removed++;
                    log.debug("Sheet sync — removed signal: {}", signal.getSourceRef());
                }
            }
        }

        SyncResult result = new SyncResult(added, modified, removed, skipped);
        writeSyncLog(result);
        log.info("Sheet sync complete — added={} modified={} removed={} skipped={}",
                added, modified, removed, skipped);
        return result;
    }

    // --- helpers ---

    private Signal buildSignal(SheetRow row) {
        return Signal.builder()
                .symbol(row.symbol())
                .entryPrice(row.entryPrice())
                .stopLoss(row.stopLoss())
                .target(row.target())
                .riskRewardRatio(rrr(row.entryPrice(), row.stopLoss(), row.target()))
                .source(SignalSource.GOOGLE_SHEET)
                .sourceRef(row.sourceRef())
                .status(SignalStatus.ACTIVE)
                .notes(row.notes())
                .build();
    }

    private void applyUpdate(Signal signal, SheetRow row) {
        signal.setEntryPrice(row.entryPrice());
        signal.setStopLoss(row.stopLoss());
        signal.setTarget(row.target());
        signal.setRiskRewardRatio(rrr(row.entryPrice(), row.stopLoss(), row.target()));
        signal.setNotes(row.notes());
    }

    private boolean pricesChanged(Signal signal, SheetRow row) {
        return signal.getEntryPrice().compareTo(row.entryPrice()) != 0
                || signal.getStopLoss().compareTo(row.stopLoss()) != 0
                || signal.getTarget().compareTo(row.target()) != 0;
    }

    private boolean notesChanged(Signal signal, SheetRow row) {
        String dbNotes   = signal.getNotes()  == null ? "" : signal.getNotes();
        String rowNotes  = row.notes()        == null ? "" : row.notes();
        return !dbNotes.equals(rowNotes);
    }

    private boolean hasActivePosition(Long signalId) {
        return positionRepository.existsBySignalIdAndStatusIn(signalId, BLOCKING_STATUSES);
    }

    private BigDecimal rrr(BigDecimal entry, BigDecimal sl, BigDecimal target) {
        return target.subtract(entry).divide(entry.subtract(sl), 4, RoundingMode.HALF_UP);
    }

    private void writeSyncLog(SyncResult result) {
        String notes = result.skipped() > 0
                ? result.skipped() + " row(s) skipped due to active positions"
                : null;
        syncLogRepository.save(SignalSyncLog.builder()
                .source(SignalSource.GOOGLE_SHEET)
                .signalsAdded(result.added())
                .signalsModified(result.modified())
                .signalsRemoved(result.removed())
                .notes(notes)
                .build());
    }
}

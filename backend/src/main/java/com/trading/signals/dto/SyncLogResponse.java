package com.trading.signals.dto;

import com.trading.signals.SignalSource;
import com.trading.signals.SignalSyncLog;

import java.time.LocalDateTime;

public record SyncLogResponse(
        Long id,
        LocalDateTime syncedAt,
        SignalSource source,
        int signalsAdded,
        int signalsModified,
        int signalsRemoved,
        String notes
) {
    public static SyncLogResponse from(SignalSyncLog log) {
        return new SyncLogResponse(
                log.getId(),
                log.getSyncedAt(),
                log.getSource(),
                log.getSignalsAdded(),
                log.getSignalsModified(),
                log.getSignalsRemoved(),
                log.getNotes()
        );
    }
}

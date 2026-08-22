package com.trading.signals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SheetSyncServiceTest {

    @Mock GoogleSheetsService googleSheetsService;
    @Mock SignalRepository signalRepository;
    @Mock PositionRepository positionRepository;
    @Mock SignalSyncLogRepository syncLogRepository;

    private SheetSyncService service;

    @BeforeEach
    void setUp() {
        service = new SheetSyncService(
                googleSheetsService, signalRepository, positionRepository, syncLogRepository);
    }

    private SheetRow row(String sourceRef, String symbol, String entry, String sl, String target) {
        return new SheetRow(sourceRef, symbol, new BigDecimal(entry), new BigDecimal(sl),
                new BigDecimal(target), null);
    }

    private Signal activeSheetSignal(Long id, String sourceRef, String symbol,
                                     String entry, String sl, String target) {
        return Signal.builder()
                .id(id).symbol(symbol).sourceRef(sourceRef)
                .entryPrice(new BigDecimal(entry))
                .stopLoss(new BigDecimal(sl))
                .target(new BigDecimal(target))
                .riskRewardRatio(new BigDecimal("2.0000"))
                .source(SignalSource.GOOGLE_SHEET)
                .status(SignalStatus.ACTIVE)
                .build();
    }

    // --- add ---

    @Test
    @DisplayName("New row in sheet → signal added")
    void sync_newRow_signalAdded() {
        SheetRow newRow = row("2:RELIANCE", "RELIANCE", "100", "90", "120");
        when(googleSheetsService.fetchRows()).thenReturn(List.of(newRow));
        when(signalRepository.findBySourceAndStatus(SignalSource.GOOGLE_SHEET, SignalStatus.ACTIVE))
                .thenReturn(Collections.emptyList());
        when(signalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(syncLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SyncResult result = service.sync();

        assertThat(result.added()).isEqualTo(1);
        assertThat(result.modified()).isZero();
        assertThat(result.removed()).isZero();

        ArgumentCaptor<Signal> captor = ArgumentCaptor.forClass(Signal.class);
        verify(signalRepository, atLeastOnce()).save(captor.capture());
        Signal saved = captor.getAllValues().stream()
                .filter(s -> s.getSource() == SignalSource.GOOGLE_SHEET).findFirst().orElseThrow();
        assertThat(saved.getSymbol()).isEqualTo("RELIANCE");
        assertThat(saved.getSourceRef()).isEqualTo("2:RELIANCE");
        assertThat(saved.getRiskRewardRatio()).isEqualByComparingTo("2.0000");
    }

    // --- modify ---

    @Test
    @DisplayName("Row price changed, no active position → signal updated")
    void sync_priceChanged_noPosition_signalUpdated() {
        Signal existing = activeSheetSignal(1L, "2:TCS", "TCS", "100", "90", "120");
        SheetRow updated = row("2:TCS", "TCS", "105", "92", "130");

        when(googleSheetsService.fetchRows()).thenReturn(List.of(updated));
        when(signalRepository.findBySourceAndStatus(SignalSource.GOOGLE_SHEET, SignalStatus.ACTIVE))
                .thenReturn(List.of(existing));
        when(positionRepository.existsBySignalIdAndStatusIn(eq(1L), any())).thenReturn(false);
        when(signalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(syncLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SyncResult result = service.sync();

        assertThat(result.modified()).isEqualTo(1);
        assertThat(result.added()).isZero();
        assertThat(existing.getEntryPrice()).isEqualByComparingTo("105");
        assertThat(existing.getStopLoss()).isEqualByComparingTo("92");
    }

    @Test
    @DisplayName("Row price changed, active position exists → skipped, not updated")
    void sync_priceChanged_hasPosition_skipped() {
        Signal existing = activeSheetSignal(1L, "2:INFY", "INFY", "100", "90", "120");
        SheetRow updated = row("2:INFY", "INFY", "110", "95", "140");

        when(googleSheetsService.fetchRows()).thenReturn(List.of(updated));
        when(signalRepository.findBySourceAndStatus(SignalSource.GOOGLE_SHEET, SignalStatus.ACTIVE))
                .thenReturn(List.of(existing));
        when(positionRepository.existsBySignalIdAndStatusIn(eq(1L), any())).thenReturn(true);
        when(syncLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SyncResult result = service.sync();

        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.modified()).isZero();
        // Signal must NOT be saved with new prices
        verify(signalRepository, never()).save(existing);
        assertThat(existing.getEntryPrice()).isEqualByComparingTo("100"); // unchanged
    }

    // --- remove ---

    @Test
    @DisplayName("Row removed from sheet, no active position → signal cancelled")
    void sync_rowRemoved_noPosition_signalCancelled() {
        Signal existing = activeSheetSignal(1L, "3:HDFC", "HDFC", "200", "180", "240");

        when(googleSheetsService.fetchRows()).thenReturn(Collections.emptyList());
        when(signalRepository.findBySourceAndStatus(SignalSource.GOOGLE_SHEET, SignalStatus.ACTIVE))
                .thenReturn(List.of(existing));
        when(positionRepository.existsBySignalIdAndStatusIn(eq(1L), any())).thenReturn(false);
        when(signalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(syncLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SyncResult result = service.sync();

        assertThat(result.removed()).isEqualTo(1);
        assertThat(existing.getStatus()).isEqualTo(SignalStatus.CANCELLED);
    }

    @Test
    @DisplayName("Row removed from sheet, active position exists → skipped")
    void sync_rowRemoved_hasPosition_skipped() {
        Signal existing = activeSheetSignal(1L, "3:HDFC", "HDFC", "200", "180", "240");

        when(googleSheetsService.fetchRows()).thenReturn(Collections.emptyList());
        when(signalRepository.findBySourceAndStatus(SignalSource.GOOGLE_SHEET, SignalStatus.ACTIVE))
                .thenReturn(List.of(existing));
        when(positionRepository.existsBySignalIdAndStatusIn(eq(1L), any())).thenReturn(true);
        when(syncLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SyncResult result = service.sync();

        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.removed()).isZero();
        assertThat(existing.getStatus()).isEqualTo(SignalStatus.ACTIVE); // unchanged
    }

    // --- sync log ---

    @Test
    @DisplayName("Sync always writes a log entry")
    void sync_alwaysWritesSyncLog() {
        when(googleSheetsService.fetchRows()).thenReturn(Collections.emptyList());
        when(signalRepository.findBySourceAndStatus(any(), any())).thenReturn(Collections.emptyList());
        when(syncLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.sync();

        ArgumentCaptor<SignalSyncLog> logCaptor = ArgumentCaptor.forClass(SignalSyncLog.class);
        verify(syncLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getSource()).isEqualTo(SignalSource.GOOGLE_SHEET);
    }

    @Test
    @DisplayName("Empty sheet, empty DB → zero counts, sync log written")
    void sync_emptySheetEmptyDb_zerosInLog() {
        when(googleSheetsService.fetchRows()).thenReturn(Collections.emptyList());
        when(signalRepository.findBySourceAndStatus(any(), any())).thenReturn(Collections.emptyList());
        when(syncLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SyncResult result = service.sync();

        assertThat(result).isEqualTo(new SyncResult(0, 0, 0, 0));
    }
}

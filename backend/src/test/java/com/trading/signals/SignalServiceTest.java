package com.trading.signals;

import com.trading.signals.dto.CreateSignalRequest;
import com.trading.signals.dto.SignalResponse;
import com.trading.signals.dto.UpdateSignalRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SignalServiceTest {

    @Mock SignalRepository signalRepository;
    @Mock PositionRepository positionRepository;
    @Mock SignalSyncLogRepository syncLogRepository;

    private SignalService signalService;

    @BeforeEach
    void setUp() {
        signalService = new SignalService(signalRepository, positionRepository, syncLogRepository);
    }

    // --- create ---

    @Test
    @DisplayName("create: valid input saves signal with correct R:R and uppercased symbol")
    void create_validInput_savesSignalWithCorrectRRR() {
        CreateSignalRequest req = new CreateSignalRequest(
                "reliance", new BigDecimal("100"), new BigDecimal("90"), new BigDecimal("120"), null);

        Signal saved = Signal.builder()
                .id(1L).symbol("RELIANCE")
                .entryPrice(new BigDecimal("100")).stopLoss(new BigDecimal("90"))
                .target(new BigDecimal("120")).riskRewardRatio(new BigDecimal("2.0000"))
                .source(SignalSource.MANUAL).status(SignalStatus.ACTIVE)
                .build();
        when(signalRepository.save(any())).thenReturn(saved);

        SignalResponse result = signalService.create(req);

        assertThat(result.symbol()).isEqualTo("RELIANCE");
        assertThat(result.riskRewardRatio()).isEqualByComparingTo("2.0000");
        verify(signalRepository).save(any(Signal.class));
    }

    @Test
    @DisplayName("create: entry <= stopLoss throws IllegalArgumentException")
    void create_entryLeThanStopLoss_throws() {
        CreateSignalRequest req = new CreateSignalRequest(
                "INFY", new BigDecimal("90"), new BigDecimal("100"), new BigDecimal("120"), null);

        assertThatThrownBy(() -> signalService.create(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entry_price must be greater than stop_loss");
    }

    @Test
    @DisplayName("create: target <= entry throws IllegalArgumentException")
    void create_targetLeEntry_throws() {
        CreateSignalRequest req = new CreateSignalRequest(
                "INFY", new BigDecimal("100"), new BigDecimal("90"), new BigDecimal("95"), null);

        assertThatThrownBy(() -> signalService.create(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("target must be greater than entry_price");
    }

    // --- update ---

    @Test
    @DisplayName("update: ACTIVE signal with no active position is updated")
    void update_activeSignalNoPosition_updated() {
        Signal signal = Signal.builder()
                .id(1L).symbol("TCS")
                .entryPrice(new BigDecimal("100")).stopLoss(new BigDecimal("90"))
                .target(new BigDecimal("120")).riskRewardRatio(new BigDecimal("2.0000"))
                .status(SignalStatus.ACTIVE).source(SignalSource.MANUAL).build();
        when(signalRepository.findById(1L)).thenReturn(Optional.of(signal));
        when(positionRepository.existsBySignalIdAndStatusIn(eq(1L), any())).thenReturn(false);
        when(signalRepository.save(any())).thenReturn(signal);

        UpdateSignalRequest req = new UpdateSignalRequest(
                new BigDecimal("105"), new BigDecimal("92"), new BigDecimal("130"), "updated");
        signalService.update(1L, req);

        assertThat(signal.getEntryPrice()).isEqualByComparingTo("105");
        assertThat(signal.getNotes()).isEqualTo("updated");
        verify(signalRepository).save(signal);
    }

    @Test
    @DisplayName("update: signal not found throws")
    void update_signalNotFound_throws() {
        when(signalRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> signalService.update(99L,
                new UpdateSignalRequest(null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Signal not found");
    }

    @Test
    @DisplayName("update: signal with active position throws")
    void update_signalHasActivePosition_throws() {
        Signal signal = Signal.builder().id(1L).status(SignalStatus.ACTIVE).build();
        when(signalRepository.findById(1L)).thenReturn(Optional.of(signal));
        when(positionRepository.existsBySignalIdAndStatusIn(eq(1L), any())).thenReturn(true);

        assertThatThrownBy(() -> signalService.update(1L,
                new UpdateSignalRequest(null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("active or pending position");
    }

    // --- cancel ---

    @Test
    @DisplayName("cancel: ACTIVE signal with no position set to CANCELLED")
    void cancel_activeSignal_setCancelled() {
        Signal signal = Signal.builder().id(1L).status(SignalStatus.ACTIVE)
                .source(SignalSource.MANUAL).build();
        when(signalRepository.findById(1L)).thenReturn(Optional.of(signal));
        when(positionRepository.existsBySignalIdAndStatusIn(eq(1L), any())).thenReturn(false);
        when(signalRepository.save(any())).thenReturn(signal);

        signalService.cancel(1L);

        assertThat(signal.getStatus()).isEqualTo(SignalStatus.CANCELLED);
    }

    @Test
    @DisplayName("cancel: non-ACTIVE signal throws")
    void cancel_nonActiveSignal_throws() {
        Signal signal = Signal.builder().id(2L).status(SignalStatus.EXPIRED).build();
        when(signalRepository.findById(2L)).thenReturn(Optional.of(signal));

        assertThatThrownBy(() -> signalService.cancel(2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not ACTIVE");
    }

    // --- list ---

    @Test
    @DisplayName("list: null status returns all signals")
    void list_nullStatus_returnsAll() {
        Signal s = Signal.builder().id(1L).symbol("X").entryPrice(BigDecimal.TEN)
                .stopLoss(BigDecimal.ONE).target(new BigDecimal("20"))
                .riskRewardRatio(new BigDecimal("1.0000"))
                .source(SignalSource.MANUAL).status(SignalStatus.ACTIVE).build();
        when(signalRepository.findAll()).thenReturn(List.of(s));

        List<SignalResponse> result = signalService.list(null);

        assertThat(result).hasSize(1);
        verify(signalRepository).findAll();
        verify(signalRepository, never()).findByStatus(any());
    }

    @Test
    @DisplayName("list: with status filters by status")
    void list_withStatus_filtersByStatus() {
        when(signalRepository.findByStatus(SignalStatus.ACTIVE)).thenReturn(List.of());

        signalService.list(SignalStatus.ACTIVE);

        verify(signalRepository).findByStatus(SignalStatus.ACTIVE);
        verify(signalRepository, never()).findAll();
    }
}

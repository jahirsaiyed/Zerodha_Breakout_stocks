package com.trading.portfolio;

import com.trading.signals.OrderRepository;
import com.trading.signals.PositionRepository;
import com.trading.signals.SignalRepository;
import com.trading.users.UserConfigRepository;
import com.trading.users.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioDbServiceTest {

    @Mock private UserConfigRepository userConfigRepository;
    @Mock private UserRepository userRepository;
    @Mock private SignalRepository signalRepository;
    @Mock private PositionRepository positionRepository;
    @Mock private OrderRepository orderRepository;

    @InjectMocks
    private PortfolioDbService db;

    // ── adjustPositionQuantity ───────────────────────────────────────────────

    @Test
    @DisplayName("adjustPositionQuantity applies the update when the expected quantity still matches")
    void adjustPositionQuantity_expectedMatches_updates() {
        when(positionRepository.updateQuantityIfUnchanged(10L, 4, 6, BigDecimal.valueOf(106.6667), "GTT2", 3))
                .thenReturn(1);

        db.adjustPositionQuantity(10L, 4, 6, BigDecimal.valueOf(106.6667), "GTT2", 3);

        verify(positionRepository).updateQuantityIfUnchanged(10L, 4, 6, BigDecimal.valueOf(106.6667), "GTT2", 3);
    }

    @Test
    @DisplayName("adjustPositionQuantity throws when the position was modified concurrently (no row matched)")
    void adjustPositionQuantity_concurrentModification_throws() {
        when(positionRepository.updateQuantityIfUnchanged(10L, 4, 6, BigDecimal.valueOf(106.6667), "GTT2", 3))
                .thenReturn(0);

        assertThatThrownBy(() -> db.adjustPositionQuantity(10L, 4, 6, BigDecimal.valueOf(106.6667), "GTT2", 3))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("modified concurrently");
    }

    @Test
    @DisplayName("adjustPositionQuantity normalizes gttQuantity to null when gttId is null")
    void adjustPositionQuantity_nullGttId_normalizesGttQuantityToNull() {
        when(positionRepository.updateQuantityIfUnchanged(10L, 4, 3, BigDecimal.valueOf(100), null, null))
                .thenReturn(1);

        // gttQuantity=99 passed in despite gttId=null — should be normalized to null before the write.
        db.adjustPositionQuantity(10L, 4, 3, BigDecimal.valueOf(100), null, 99);

        verify(positionRepository).updateQuantityIfUnchanged(10L, 4, 3, BigDecimal.valueOf(100), null, null);
    }
}

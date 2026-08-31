package com.trading.signals;

import com.trading.users.User;
import com.trading.users.UserRepository;
import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression coverage for the bug where filled orders stayed stuck PENDING_ENTRY:
 * PortfolioDbService.getPendingEntryPositions() runs in its own short read-only
 * transaction, so a Position's lazy `signal` proxy is detached by the time
 * PortfolioEngine.handleFill() touches it afterward, throwing LazyInitializationException
 * and aborting the whole fill-check batch. findByStatusFetchSignal must eagerly load the
 * signal within that transaction so callers can safely use it after it returns.
 *
 * Propagation.NOT_SUPPORTED disables @DataJpaTest's usual test-wrapping transaction so
 * each repository call opens and closes its own session, mirroring production.
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PositionRepositoryFetchJoinTest {

    @Autowired private PositionRepository positionRepository;
    @Autowired private SignalRepository signalRepository;
    @Autowired private UserRepository userRepository;

    @Test
    void plainFindByStatus_signalProxyIsDetached_throwsOutsideTransaction() {
        seedPendingPosition();

        Position pos = positionRepository.findByStatus(PositionStatus.PENDING_ENTRY).get(0);

        assertThatThrownBy(() -> pos.getSignal().getTarget())
                .isInstanceOf(LazyInitializationException.class);
    }

    @Test
    void findByStatusFetchSignal_signalIsAccessible_afterTransactionCloses() {
        seedPendingPosition();

        Position pos = positionRepository.findByStatusFetchSignal(PositionStatus.PENDING_ENTRY).get(0);

        assertThat(pos.getSignal().getTarget()).isEqualByComparingTo("150.00");
    }

    private void seedPendingPosition() {
        User user = userRepository.save(User.builder()
                .name("Test User")
                .email("test-" + System.nanoTime() + "@example.com")
                .passwordHash("hash")
                .build());
        Signal signal = signalRepository.save(Signal.builder()
                .symbol("BALRAMCHIN")
                .entryPrice(new BigDecimal("100.00"))
                .stopLoss(new BigDecimal("95.00"))
                .target(new BigDecimal("150.00"))
                .riskRewardRatio(new BigDecimal("10.00"))
                .build());
        positionRepository.save(Position.builder()
                .user(user)
                .signal(signal)
                .symbol("BALRAMCHIN")
                .quantity(10)
                .status(PositionStatus.PENDING_ENTRY)
                .build());
    }
}

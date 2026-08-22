package com.trading.portfolio;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives the portfolio engine on a schedule.
 *
 * <ul>
 *   <li>Core loop — every 15 min during market hours</li>
 *   <li>Fill check — every 5 min during market hours</li>
 *   <li>GTT reconcile — every 30 min during market hours</li>
 *   <li>Unmanaged detection — once per day at market open</li>
 * </ul>
 *
 * All crons are in IST (Asia/Kolkata) to align with NSE market hours (09:15–15:30).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PortfolioScheduler {

    private final PortfolioEngine engine;

    /** Place new entry orders — every 15 min, 09:15–15:30 IST, Mon–Fri */
    @Scheduled(cron = "0 15/15 9-15 * * MON-FRI", zone = "Asia/Kolkata")
    public void runCoreLoop() {
        log.debug("Scheduler: runCoreLoop triggered");
        engine.runCoreLoop();
    }

    /** Check order fills — every 5 min, 09:15–15:30 IST, Mon–Fri */
    @Scheduled(cron = "0 15/5 9-15 * * MON-FRI", zone = "Asia/Kolkata")
    public void checkOrderFills() {
        log.debug("Scheduler: checkOrderFills triggered");
        engine.checkOrderFills();
    }

    /** Reconcile GTT exits — every 30 min, 09:00–16:00 IST, Mon–Fri */
    @Scheduled(cron = "0 0/30 9-16 * * MON-FRI", zone = "Asia/Kolkata")
    public void reconcileGttExits() {
        log.debug("Scheduler: reconcileGttExits triggered");
        engine.reconcileGttExits();
    }

    /** Detect unmanaged positions — once at 09:20 IST, Mon–Fri */
    @Scheduled(cron = "0 20 9 * * MON-FRI", zone = "Asia/Kolkata")
    public void detectUnmanagedPositions() {
        log.debug("Scheduler: detectUnmanagedPositions triggered");
        engine.detectUnmanagedPositions();
    }
}

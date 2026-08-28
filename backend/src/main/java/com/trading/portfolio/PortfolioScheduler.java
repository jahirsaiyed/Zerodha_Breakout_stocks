package com.trading.portfolio;

import com.trading.signals.StopLossBasis;
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

    /**
     * EOD fill check — runs once at 15:35 IST Mon–Fri.
     * Catches DAY-validity limit orders that Zerodha auto-cancelled at market close (15:30)
     * but may not have been detected during the last market-hours fill-check run.
     */
    @Scheduled(cron = "0 35 15 * * MON-FRI", zone = "Asia/Kolkata")
    public void checkOrderFillsEod() {
        log.debug("Scheduler: checkOrderFillsEod triggered");
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

    /**
     * Closing-basis SL check — HOURLY: runs at :02 of each market hour (09:02–15:02 IST).
     * LTP at 2 min past the hour approximates the just-closed hourly candle close.
     */
    @Scheduled(cron = "0 2 9-15 * * MON-FRI", zone = "Asia/Kolkata")
    public void checkHourlyStopLoss() {
        log.debug("Scheduler: checkHourlyStopLoss triggered");
        engine.checkClosingBasisStopLoss(StopLossBasis.HOURLY);
    }

    /**
     * Closing-basis SL check — DAILY: runs at 15:32 IST Mon–Fri (after market close).
     * LTP at this time equals the day's closing price.
     */
    @Scheduled(cron = "0 32 15 * * MON-FRI", zone = "Asia/Kolkata")
    public void checkDailyStopLoss() {
        log.debug("Scheduler: checkDailyStopLoss triggered");
        engine.checkClosingBasisStopLoss(StopLossBasis.DAILY);
    }

    /**
     * Closing-basis SL check — WEEKLY: runs at 15:32 IST on Fridays (after weekly close).
     */
    @Scheduled(cron = "0 32 15 * * FRI", zone = "Asia/Kolkata")
    public void checkWeeklyStopLoss() {
        log.debug("Scheduler: checkWeeklyStopLoss triggered");
        engine.checkClosingBasisStopLoss(StopLossBasis.WEEKLY);
    }
}

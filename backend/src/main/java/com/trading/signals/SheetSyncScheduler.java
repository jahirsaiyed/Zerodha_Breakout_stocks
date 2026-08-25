package com.trading.signals;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Triggers Google Sheet sync every 15 minutes on weekdays during market hours (IST).
 * Schedule: 9:00 AM – 4:00 PM IST, Monday–Friday.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SheetSyncScheduler {

    private final SheetSyncService sheetSyncService;
    private final SheetsProperties sheetsProperties;

    /** Every 15 min, 09:00–15:45 IST, Mon–Fri */
    @Scheduled(cron = "0 0/15 9-15 * * MON-FRI", zone = "Asia/Kolkata")
    public void syncDuringMarketHours() {
        runSync("scheduled");
    }

    /** One final sync at exactly 16:00 IST */
    @Scheduled(cron = "0 0 16 * * MON-FRI", zone = "Asia/Kolkata")
    public void syncAtMarketClose() {
        runSync("market-close");
    }

    private void runSync(String trigger) {
        if (!sheetsProperties.isEnabled()) {
            log.debug("Sheet sync skipped — integration disabled (trigger={})", trigger);
            return;
        }
        log.info("[SHEET] sync starting (trigger={})", trigger);
        try {
            SyncResult result = sheetSyncService.sync();
            log.info("[SHEET] sync done (trigger={}) — added={} modified={} removed={} skipped={}",
                    trigger, result.added(), result.modified(), result.removed(), result.skipped());
        } catch (Exception e) {
            log.error("Sheet sync failed (trigger={}): {}", trigger, e.getMessage(), e);
        }
    }
}

package com.trading.signals;

import java.math.BigDecimal;

/**
 * A parsed row from the Google Sheet.
 * sourceRef = "{rowNumber}:{SYMBOL}", e.g. "2:RELIANCE"
 * Sheet columns: A=symbol, B=entry_price, C=stop_loss, D=target, E=notes (optional)
 */
public record SheetRow(
        String sourceRef,
        String symbol,
        BigDecimal entryPrice,
        BigDecimal stopLoss,
        BigDecimal target,
        String notes
) {
    /** Returns true if prices are logically valid (entry > SL, target > entry). */
    public boolean isValid() {
        return entryPrice.compareTo(stopLoss) > 0 && target.compareTo(entryPrice) > 0;
    }
}

package com.trading.signals;

import java.math.BigDecimal;

/**
 * A parsed row from the Google Sheet.
 * sourceRef = "{rowNumber}:{SYMBOL}", e.g. "2:RELIANCE"
 * Sheet columns (0-based): B=symbol, K=entry_price, L=stop_loss, M=target, N=closing_basis, Q=notes
 */
public record SheetRow(
        String sourceRef,
        String symbol,
        BigDecimal entryPrice,
        BigDecimal stopLoss,
        BigDecimal target,
        StopLossBasis closingBasis,
        String notes
) {
    /** Returns true if prices are logically valid (entry > SL, target > entry). */
    public boolean isValid() {
        return entryPrice.compareTo(stopLoss) > 0 && target.compareTo(entryPrice) > 0;
    }
}

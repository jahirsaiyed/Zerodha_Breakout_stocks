package com.trading.signals.dto;

import java.math.BigDecimal;

public record SignalQuoteResponse(
        Long signalId,
        Integer rank,
        BigDecimal ltp,
        BigDecimal diffFromEntryPct
) {}

package com.trading.portfolio.events;

import java.math.BigDecimal;

public record OrderFilledEvent(Long positionId, String symbol, String zerodhaOrderId,
                               int filledQty, BigDecimal avgPrice, String gttId) {}

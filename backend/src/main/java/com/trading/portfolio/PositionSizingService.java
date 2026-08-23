package com.trading.portfolio;

import com.trading.users.UserConfig;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calculates the quantity to buy for a signal given the user's position sizing config.
 *
 * <pre>
 * EQUAL:      qty = floor((margin / maxPositions) / entryPrice)
 * FIXED:      qty = floor(positionSizingValue / entryPrice)
 * RISK_BASED: risk = margin × (positionSizingValue / 100)
 *             qty  = floor(risk / (entryPrice - stopLoss))
 * </pre>
 *
 * Returns 0 when the entry price is too high for minimum 1 share.
 */
@Service
public class PositionSizingService {

    public int calculate(UserConfig config, BigDecimal entryPrice,
                         BigDecimal stopLoss, BigDecimal availableMargin) {
        BigDecimal usageFraction = config.getMarginUsagePercent()
                .divide(BigDecimal.valueOf(100), 6, RoundingMode.DOWN);
        BigDecimal effectiveMargin = availableMargin.multiply(usageFraction);
        if (config.getMarginUsageFixedLimit() != null) {
            effectiveMargin = effectiveMargin.min(config.getMarginUsageFixedLimit());
        }

        BigDecimal allocated = switch (config.getPositionSizingMethod()) {
            case EQUAL -> effectiveMargin.divide(
                    BigDecimal.valueOf(config.getMaxPositions()), 2, RoundingMode.DOWN);
            case FIXED -> config.getPositionSizingValue();
            case RISK_BASED -> {
                BigDecimal riskFraction = config.getPositionSizingValue()
                        .divide(BigDecimal.valueOf(100), 6, RoundingMode.DOWN);
                BigDecimal riskAmount = effectiveMargin.multiply(riskFraction);
                BigDecimal riskPerShare = entryPrice.subtract(stopLoss);
                if (riskPerShare.compareTo(BigDecimal.ZERO) <= 0) yield BigDecimal.ZERO;
                // For RISK_BASED, allocated = riskAmount / riskPerShare gives qty directly
                yield riskAmount.divide(riskPerShare, 0, RoundingMode.DOWN);
            }
        };

        if (config.getPositionSizingMethod() == com.trading.users.PositionSizingMethod.RISK_BASED) {
            // For RISK_BASED, allocated is already the quantity
            return allocated.intValue();
        }

        if (entryPrice.compareTo(BigDecimal.ZERO) <= 0) return 0;
        return allocated.divide(entryPrice, 0, RoundingMode.DOWN).intValue();
    }
}

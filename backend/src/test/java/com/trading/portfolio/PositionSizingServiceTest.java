package com.trading.portfolio;

import com.trading.users.PositionSizingMethod;
import com.trading.users.UserConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PositionSizingServiceTest {

    private PositionSizingService sizingService;

    @BeforeEach
    void setUp() {
        sizingService = new PositionSizingService();
    }

    private UserConfig configWithMethod(PositionSizingMethod method, double value, int maxPos) {
        return UserConfig.builder()
                .positionSizingMethod(method)
                .positionSizingValue(BigDecimal.valueOf(value))
                .maxPositions(maxPos)
                .build();
    }

    @Test
    @DisplayName("EQUAL method divides margin equally and floors")
    void calculate_equal_dividesMarginByMaxPositions() {
        UserConfig config = configWithMethod(PositionSizingMethod.EQUAL, 0, 5);
        BigDecimal entry = BigDecimal.valueOf(500);
        BigDecimal sl    = BigDecimal.valueOf(450);
        BigDecimal margin = BigDecimal.valueOf(100_000);

        // allocated = 100000 / 5 = 20000; qty = floor(20000 / 500) = 40
        int qty = sizingService.calculate(config, entry, sl, margin);
        assertThat(qty).isEqualTo(40);
    }

    @Test
    @DisplayName("FIXED method uses positionSizingValue and floors")
    void calculate_fixed_usesFixedValue() {
        UserConfig config = configWithMethod(PositionSizingMethod.FIXED, 15_000, 5);
        BigDecimal entry = BigDecimal.valueOf(300);

        // qty = floor(15000 / 300) = 50
        int qty = sizingService.calculate(config, entry, BigDecimal.valueOf(250), BigDecimal.valueOf(100_000));
        assertThat(qty).isEqualTo(50);
    }

    @Test
    @DisplayName("RISK_BASED method sizes by risk percentage")
    void calculate_riskBased_sizesByRiskPerShare() {
        UserConfig config = configWithMethod(PositionSizingMethod.RISK_BASED, 2.0, 5); // 2% risk
        BigDecimal entry  = BigDecimal.valueOf(200);
        BigDecimal sl     = BigDecimal.valueOf(190); // riskPerShare=10
        BigDecimal margin = BigDecimal.valueOf(100_000);

        // riskAmount = 100000 * 2/100 = 2000; qty = floor(2000 / 10) = 200
        int qty = sizingService.calculate(config, entry, sl, margin);
        assertThat(qty).isEqualTo(200);
    }

    @Test
    @DisplayName("returns 0 when entry price is zero (FIXED)")
    void calculate_zeroPriceFixed_returnsZero() {
        UserConfig config = configWithMethod(PositionSizingMethod.FIXED, 10_000, 5);
        int qty = sizingService.calculate(config, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(50_000));
        assertThat(qty).isZero();
    }

    @Test
    @DisplayName("RISK_BASED returns 0 when entry == stop loss (zero risk per share)")
    void calculate_riskBasedZeroRisk_returnsZero() {
        UserConfig config = configWithMethod(PositionSizingMethod.RISK_BASED, 2.0, 5);
        BigDecimal entry = BigDecimal.valueOf(200);
        // sl == entry → riskPerShare = 0
        int qty = sizingService.calculate(config, entry, entry, BigDecimal.valueOf(100_000));
        assertThat(qty).isZero();
    }

    @Test
    @DisplayName("EQUAL respects marginUsagePercent cap")
    void calculate_equal_respectsMarginUsagePercent() {
        UserConfig config = UserConfig.builder()
                .positionSizingMethod(PositionSizingMethod.EQUAL)
                .positionSizingValue(BigDecimal.ZERO)
                .maxPositions(5)
                .marginUsagePercent(new BigDecimal("50"))
                .build();
        BigDecimal entry = BigDecimal.valueOf(500);
        BigDecimal margin = BigDecimal.valueOf(100_000);

        // effectiveMargin = 100000 * 50% = 50000; allocated = 50000 / 5 = 10000; qty = floor(10000 / 500) = 20
        int qty = sizingService.calculate(config, entry, BigDecimal.valueOf(450), margin);
        assertThat(qty).isEqualTo(20);
    }

    @Test
    @DisplayName("RISK_BASED respects marginUsagePercent cap")
    void calculate_riskBased_respectsMarginUsagePercent() {
        UserConfig config = UserConfig.builder()
                .positionSizingMethod(PositionSizingMethod.RISK_BASED)
                .positionSizingValue(BigDecimal.valueOf(2.0))
                .maxPositions(5)
                .marginUsagePercent(new BigDecimal("50"))
                .build();
        BigDecimal entry  = BigDecimal.valueOf(200);
        BigDecimal sl     = BigDecimal.valueOf(190); // riskPerShare=10
        BigDecimal margin = BigDecimal.valueOf(100_000);

        // effectiveMargin = 50000; riskAmount = 50000 * 2/100 = 1000; qty = floor(1000 / 10) = 100
        int qty = sizingService.calculate(config, entry, sl, margin);
        assertThat(qty).isEqualTo(100);
    }

    @Test
    @DisplayName("fixed limit alone caps effective margin (percent=100)")
    void calculate_fixedLimit_capsMargin() {
        UserConfig config = UserConfig.builder()
                .positionSizingMethod(PositionSizingMethod.EQUAL)
                .positionSizingValue(BigDecimal.ZERO)
                .maxPositions(1)
                .marginUsagePercent(new BigDecimal("100"))
                .marginUsageFixedLimit(new BigDecimal("30000"))
                .build();
        BigDecimal margin = BigDecimal.valueOf(100_000);

        // effectiveMargin = min(100000, 30000) = 30000; qty = floor(30000 / 500) = 60
        int qty = sizingService.calculate(config, BigDecimal.valueOf(500), BigDecimal.valueOf(450), margin);
        assertThat(qty).isEqualTo(60);
    }

    @Test
    @DisplayName("fixed limit is tighter than percent cap — fixed limit wins")
    void calculate_fixedLimitTighterThanPercent() {
        UserConfig config = UserConfig.builder()
                .positionSizingMethod(PositionSizingMethod.EQUAL)
                .positionSizingValue(BigDecimal.ZERO)
                .maxPositions(1)
                .marginUsagePercent(new BigDecimal("50"))
                .marginUsageFixedLimit(new BigDecimal("30000"))
                .build();
        BigDecimal margin = BigDecimal.valueOf(100_000);

        // pct cap = 50000; fixed cap = 30000 → effective = 30000; qty = floor(30000 / 500) = 60
        int qty = sizingService.calculate(config, BigDecimal.valueOf(500), BigDecimal.valueOf(450), margin);
        assertThat(qty).isEqualTo(60);
    }

    @Test
    @DisplayName("percent cap is tighter than fixed limit — percent cap wins")
    void calculate_percentCapTighterThanFixedLimit() {
        UserConfig config = UserConfig.builder()
                .positionSizingMethod(PositionSizingMethod.EQUAL)
                .positionSizingValue(BigDecimal.ZERO)
                .maxPositions(1)
                .marginUsagePercent(new BigDecimal("50"))
                .marginUsageFixedLimit(new BigDecimal("80000"))
                .build();
        BigDecimal margin = BigDecimal.valueOf(100_000);

        // pct cap = 50000; fixed cap = 80000 → effective = 50000; qty = floor(50000 / 500) = 100
        int qty = sizingService.calculate(config, BigDecimal.valueOf(500), BigDecimal.valueOf(450), margin);
        assertThat(qty).isEqualTo(100);
    }

    @Test
    @DisplayName("EQUAL floors down when margin is not cleanly divisible")
    void calculate_equalNonDivisible_floorsDown() {
        UserConfig config = configWithMethod(PositionSizingMethod.EQUAL, 0, 3);
        // margin=10000, maxPos=3 → allocated≈3333.33; entry=1000 → qty=floor(3333.33/1000)=3
        int qty = sizingService.calculate(config,
                BigDecimal.valueOf(1000),
                BigDecimal.valueOf(900),
                BigDecimal.valueOf(10_000));
        assertThat(qty).isEqualTo(3);
    }
}

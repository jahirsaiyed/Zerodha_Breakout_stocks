package com.trading.portfolio;

import com.trading.signals.Signal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SignalScoringServiceTest {

    private SignalScoringService scoringService;

    @BeforeEach
    void setUp() {
        ScoringProperties props = new ScoringProperties();
        props.setProximityWeight(0.6);
        props.setRiskRewardWeight(0.4);
        scoringService = new SignalScoringService(props);
    }

    private Signal signal(long id, String symbol, double entry, double sl, double target) {
        Signal s = new Signal();
        s.setId(id);
        s.setSymbol(symbol);
        s.setEntryPrice(BigDecimal.valueOf(entry));
        s.setStopLoss(BigDecimal.valueOf(sl));
        s.setTarget(BigDecimal.valueOf(target));
        // R:R = (target - entry) / (entry - sl)
        BigDecimal risk = BigDecimal.valueOf(entry - sl);
        BigDecimal reward = BigDecimal.valueOf(target - entry);
        s.setRiskRewardRatio(reward.divide(risk, 4, java.math.RoundingMode.HALF_UP));
        return s;
    }

    @Test
    @DisplayName("empty candidates returns empty list")
    void rank_emptyCandidates_returnsEmpty() {
        var result = scoringService.rank(List.of(), Map.of());
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("signal with no quote is included with proximity=1.0 (RRR-only ranking)")
    void rank_noQuote_signalIncludedWithMaxProximity() {
        Signal s = signal(1, "RELIANCE", 2400, 2300, 2600);
        // No live quote available — signal should still be ranked using proximity=1.0
        var result = scoringService.rank(List.of(s), Map.of());
        assertThat(result).hasSize(1);
        // Single signal → rrNorm=1.0, proximity=1.0 → score = 0.6*1.0 + 0.4*1.0 = 1.0
        assertThat(result.get(0).score()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    @DisplayName("signal with ltp at or below stop-loss is disqualified")
    void rank_ltpBelowStopLoss_disqualified() {
        Signal s = signal(1, "RELIANCE", 2400, 2300, 2600);
        Map<String, BigDecimal> quotes = Map.of("RELIANCE", BigDecimal.valueOf(2300));
        var result = scoringService.rank(List.of(s), quotes);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("single valid signal is scored and returned")
    void rank_singleValidSignal_returnsSingleResult() {
        Signal s = signal(1, "RELIANCE", 2400, 2300, 2600);
        // ltp = 2450 (above entry → price pulled back toward entry, good)
        Map<String, BigDecimal> quotes = Map.of("RELIANCE", BigDecimal.valueOf(2450));
        var result = scoringService.rank(List.of(s), quotes);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).signal().getSymbol()).isEqualTo("RELIANCE");
        assertThat(result.get(0).score()).isPositive();
    }

    @Test
    @DisplayName("higher proximity signal is ranked first")
    void rank_higherProximity_rankedFirst() {
        // Signal A: entry=100, sl=90, target=120; ltp=101 (very close to entry → high proximity)
        Signal a = signal(1, "AAA", 100, 90, 120);
        // Signal B: entry=100, sl=90, target=120; ltp=110 (far above entry → low proximity)
        Signal b = signal(2, "BBB", 100, 90, 120);

        Map<String, BigDecimal> quotes = Map.of(
                "AAA", BigDecimal.valueOf(101),
                "BBB", BigDecimal.valueOf(110)
        );
        var result = scoringService.rank(List.of(a, b), quotes);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).signal().getSymbol()).isEqualTo("AAA");
    }

    @Test
    @DisplayName("price below entry gives proximity capped at 1.0")
    void rank_ltpBelowEntry_proximityCappedAtOne() {
        Signal s = signal(1, "XYZ", 100, 90, 120);
        // ltp=95 (below entry) → deviation is negative → proximity > 1.0 → capped at 1.0
        Map<String, BigDecimal> quotes = Map.of("XYZ", BigDecimal.valueOf(95));
        var result = scoringService.rank(List.of(s), quotes);
        assertThat(result).hasSize(1);
        // score = 0.6 * 1.0 + 0.4 * 1.0 (single signal, rr normalized=1) = 1.0
        assertThat(result.get(0).score()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    @DisplayName("when all signals have same RRR, rr_score=1.0 for all")
    void rank_sameRrr_allGetMaxRrScore() {
        Signal a = signal(1, "AAA", 100, 90, 120);
        Signal b = signal(2, "BBB", 200, 180, 240);
        // Both have RRR = 2.0

        Map<String, BigDecimal> quotes = Map.of(
                "AAA", BigDecimal.valueOf(95),
                "BBB", BigDecimal.valueOf(195)
        );
        var result = scoringService.rank(List.of(a, b), quotes);
        assertThat(result).hasSize(2);
        // Both get rr_score=1.0; both proximity=1.0 → score=1.0 for both
        result.forEach(ss ->
                assertThat(ss.score()).isEqualByComparingTo(BigDecimal.ONE));
    }

    @Test
    @DisplayName("results are sorted descending by score")
    void rank_resultsSortedDescending() {
        Signal a = signal(1, "AAA", 100, 90, 200); // high RRR=10
        Signal b = signal(2, "BBB", 100, 90, 120); // low RRR=2

        // Give both same ltp (at entry); proximity=1.0 for both, so rr wins
        Map<String, BigDecimal> quotes = Map.of(
                "AAA", BigDecimal.valueOf(100),
                "BBB", BigDecimal.valueOf(100)
        );
        var result = scoringService.rank(List.of(a, b), quotes);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).score())
                .isGreaterThanOrEqualTo(result.get(1).score());
        // Higher RRR (AAA) should rank first
        assertThat(result.get(0).signal().getSymbol()).isEqualTo("AAA");
    }
}

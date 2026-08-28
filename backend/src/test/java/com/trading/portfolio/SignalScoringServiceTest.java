package com.trading.portfolio;

import com.trading.market.GoogleFinancePriceService;
import com.trading.signals.Signal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignalScoringServiceTest {

    @Mock
    private GoogleFinancePriceService googleFinancePriceService;

    private SignalScoringService scoringService;

    @BeforeEach
    void setUp() {
        ScoringProperties props = new ScoringProperties();
        props.setProximityWeight(0.6);
        props.setRiskRewardWeight(0.4);
        scoringService = new SignalScoringService(props, googleFinancePriceService);
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
    @DisplayName("no broker quote and Google Finance also empty — signal skipped")
    void rank_noQuote_googleFinanceEmpty_signalSkipped() {
        when(googleFinancePriceService.getPrices(anyList())).thenReturn(Map.of());
        Signal s = signal(1, "RELIANCE", 2400, 2300, 2600);
        var result = scoringService.rank(List.of(s), Map.of());
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("no broker quote but Google Finance resolves price — signal scored with that LTP")
    void rank_noQuote_googleFinanceReturnsPrice_signalScored() {
        // ltp=2350 is below entry=2400 → passes the new entry-price guard
        when(googleFinancePriceService.getPrices(anyList()))
                .thenReturn(Map.of("RELIANCE", BigDecimal.valueOf(2350)));
        Signal s = signal(1, "RELIANCE", 2400, 2300, 2600);
        var result = scoringService.rank(List.of(s), Map.of());
        assertThat(result).hasSize(1);
        assertThat(result.get(0).signal().getSymbol()).isEqualTo("RELIANCE");
        assertThat(result.get(0).score()).isPositive();
    }

    @Test
    @DisplayName("no broker quote but Google Finance returns price at or below SL — signal disqualified")
    void rank_noQuote_googleFinancePriceBelowSl_signalDisqualified() {
        when(googleFinancePriceService.getPrices(anyList()))
                .thenReturn(Map.of("RELIANCE", BigDecimal.valueOf(2300)));
        Signal s = signal(1, "RELIANCE", 2400, 2300, 2600);
        var result = scoringService.rank(List.of(s), Map.of());
        assertThat(result).isEmpty();
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
        // ltp=2380: below entry=2400 and above sl=2300 — valid candidate
        Map<String, BigDecimal> quotes = Map.of("RELIANCE", BigDecimal.valueOf(2380));
        var result = scoringService.rank(List.of(s), quotes);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).signal().getSymbol()).isEqualTo("RELIANCE");
        assertThat(result.get(0).score()).isPositive();
    }

    @Test
    @DisplayName("with ltp at or below entry (proximity=1.0 for all), higher RRR signal is ranked first")
    void rank_higherRrr_rankedFirstWhenProximityEqual() {
        // Both signals have ltp at entry → proximity capped at 1.0 for both
        // AAA has higher RRR (target=130 → RRR=3) vs BBB (target=120 → RRR=2)
        Signal a = signal(1, "AAA", 100, 90, 130); // RRR=3
        Signal b = signal(2, "BBB", 100, 90, 120); // RRR=2

        Map<String, BigDecimal> quotes = Map.of(
                "AAA", BigDecimal.valueOf(100), // ltp == entry → proximity=1.0
                "BBB", BigDecimal.valueOf(100)  // ltp == entry → proximity=1.0
        );
        var result = scoringService.rank(List.of(a, b), quotes);

        assertThat(result).hasSize(2);
        // Higher RRR (AAA) ranks first when proximity is equal
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

    // ── New guard: LTP above entry ────────────────────────────────────────────

    @Test
    @DisplayName("signal disqualified when LTP is strictly above entry price")
    void rank_ltpAboveEntry_disqualified() {
        // entry=168, sl=160, ltp=174 — the exact scenario from the bug report
        Signal s = signal(1, "EXAMPLE", 168, 160, 185);
        Map<String, BigDecimal> quotes = Map.of("EXAMPLE", BigDecimal.valueOf(174));
        var result = scoringService.rank(List.of(s), quotes);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("signal disqualified when LTP is just one rupee above entry")
    void rank_ltpOneAboveEntry_disqualified() {
        Signal s = signal(1, "XYZ", 100, 90, 120);
        Map<String, BigDecimal> quotes = Map.of("XYZ", BigDecimal.valueOf(101));
        // LTP=101 > entry=100 → must be disqualified
        var result = scoringService.rank(List.of(s), quotes);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("signal included when LTP equals entry price (boundary)")
    void rank_ltpExactlyAtEntry_included() {
        Signal s = signal(1, "XYZ", 100, 90, 120);
        Map<String, BigDecimal> quotes = Map.of("XYZ", BigDecimal.valueOf(100));
        // LTP == entry → at entry, limit order would fill — must include with proximity=1.0
        var result = scoringService.rank(List.of(s), quotes);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).score()).isPositive();
    }

    @Test
    @DisplayName("signal included when LTP is below entry price (pullback scenario)")
    void rank_ltpBelowEntry_included() {
        Signal s = signal(1, "XYZ", 100, 90, 120);
        Map<String, BigDecimal> quotes = Map.of("XYZ", BigDecimal.valueOf(97));
        // LTP < entry → price pulled back below entry → best case, proximity capped at 1.0
        var result = scoringService.rank(List.of(s), quotes);
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("only signals at or below entry are ranked; above-entry signals filtered out")
    void rank_mixedLtps_onlyAtOrBelowEntryRanked() {
        // AAA: ltp=95 < entry=100 → included
        Signal a = signal(1, "AAA", 100, 90, 120);
        // BBB: ltp=100 == entry=100 → included
        Signal b = signal(2, "BBB", 100, 90, 120);
        // CCC: ltp=105 > entry=100 → disqualified
        Signal c = signal(3, "CCC", 100, 90, 120);

        Map<String, BigDecimal> quotes = Map.of(
                "AAA", BigDecimal.valueOf(95),
                "BBB", BigDecimal.valueOf(100),
                "CCC", BigDecimal.valueOf(105)
        );
        var result = scoringService.rank(List.of(a, b, c), quotes);
        assertThat(result).hasSize(2);
        assertThat(result.stream().map(ss -> ss.signal().getSymbol()).toList())
                .containsExactlyInAnyOrder("AAA", "BBB")
                .doesNotContain("CCC");
    }
}

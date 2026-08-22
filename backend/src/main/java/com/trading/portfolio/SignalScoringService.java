package com.trading.portfolio;

import com.trading.signals.Signal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Scores and ranks candidate signals using:
 * <pre>
 *   score = (proximityWeight × proximity_score) + (riskRewardWeight × risk_reward_score)
 *
 *   proximity_score = 1 - ((ltp - entry) / (entry - sl))  [capped at 1.0]
 *   Disqualified if ltp ≤ stop_loss.
 *
 *   risk_reward_score = min-max normalised RRR across valid candidates
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignalScoringService {

    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);

    private final ScoringProperties props;

    public List<ScoredSignal> rank(List<Signal> candidates, Map<String, BigDecimal> quotes) {
        // Step 1: filter invalid and compute proximity
        record Candidate(Signal signal, BigDecimal ltp, BigDecimal proximity) {}
        List<Candidate> valid = new ArrayList<>();

        for (Signal signal : candidates) {
            BigDecimal ltp = quotes.get(signal.getSymbol());
            if (ltp == null) {
                log.debug("No quote for {} — skipping", signal.getSymbol());
                continue;
            }
            // Disqualify: price already at or below stop-loss
            if (ltp.compareTo(signal.getStopLoss()) <= 0) {
                log.debug("Signal {} disqualified: ltp={} ≤ sl={}", signal.getId(), ltp, signal.getStopLoss());
                continue;
            }

            BigDecimal risk      = signal.getEntryPrice().subtract(signal.getStopLoss(), MC);
            BigDecimal deviation = ltp.subtract(signal.getEntryPrice(), MC);
            BigDecimal proximity = BigDecimal.ONE.subtract(deviation.divide(risk, MC), MC);
            // Cap at 1.0 (price below entry is best case — no need to distinguish further)
            if (proximity.compareTo(BigDecimal.ONE) > 0) proximity = BigDecimal.ONE;

            valid.add(new Candidate(signal, ltp, proximity));
        }

        if (valid.isEmpty()) return Collections.emptyList();

        // Step 2: normalise RRR across valid candidates
        BigDecimal minRrr = valid.stream().map(c -> c.signal().getRiskRewardRatio())
                .min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal maxRrr = valid.stream().map(c -> c.signal().getRiskRewardRatio())
                .max(BigDecimal::compareTo).orElse(BigDecimal.ONE);
        boolean sameRrr = maxRrr.compareTo(minRrr) == 0;

        BigDecimal pw = BigDecimal.valueOf(props.getProximityWeight());
        BigDecimal rw = BigDecimal.valueOf(props.getRiskRewardWeight());

        List<ScoredSignal> scored = new ArrayList<>();
        for (Candidate c : valid) {
            BigDecimal rrNorm = sameRrr
                    ? BigDecimal.ONE
                    : c.signal().getRiskRewardRatio().subtract(minRrr, MC)
                            .divide(maxRrr.subtract(minRrr, MC), MC);

            BigDecimal score = pw.multiply(c.proximity(), MC).add(rw.multiply(rrNorm, MC), MC);
            scored.add(new ScoredSignal(c.signal(), score));
            log.debug("Signal {} {} prox={} rrNorm={} score={}",
                    c.signal().getId(), c.signal().getSymbol(),
                    c.proximity().setScale(4, RoundingMode.HALF_UP),
                    rrNorm.setScale(4, RoundingMode.HALF_UP),
                    score.setScale(4, RoundingMode.HALF_UP));
        }

        Collections.sort(scored); // descending by score
        return scored;
    }
}

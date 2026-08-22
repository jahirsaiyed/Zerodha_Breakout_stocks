package com.trading.portfolio;

import com.trading.signals.Signal;

import java.math.BigDecimal;

public record ScoredSignal(Signal signal, BigDecimal score) implements Comparable<ScoredSignal> {
    @Override
    public int compareTo(ScoredSignal other) {
        // Higher score = better; sort descending
        return other.score().compareTo(this.score());
    }
}

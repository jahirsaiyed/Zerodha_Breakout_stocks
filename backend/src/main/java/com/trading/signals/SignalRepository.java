package com.trading.signals;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SignalRepository extends JpaRepository<Signal, Long> {
    List<Signal> findByStatus(SignalStatus status);
    List<Signal> findBySymbolIgnoreCase(String symbol);
    List<Signal> findBySourceAndStatus(SignalSource source, SignalStatus status);
    boolean existsBySourceRefAndStatus(String sourceRef, SignalStatus status);
}

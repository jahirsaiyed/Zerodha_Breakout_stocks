package com.trading.signals;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SignalSyncLogRepository extends JpaRepository<SignalSyncLog, Long> {
    List<SignalSyncLog> findAllByOrderBySyncedAtDesc(Pageable pageable);
}

package com.trading.signals;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "signal_sync_log")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class SignalSyncLog {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    @Column(name = "synced_at", nullable = false, updatable = false)
    private LocalDateTime syncedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SignalSource source;

    @Builder.Default
    @Column(name = "signals_added", nullable = false)
    private Integer signalsAdded = 0;

    @Builder.Default
    @Column(name = "signals_modified", nullable = false)
    private Integer signalsModified = 0;

    @Builder.Default
    @Column(name = "signals_removed", nullable = false)
    private Integer signalsRemoved = 0;

    private String notes;
}

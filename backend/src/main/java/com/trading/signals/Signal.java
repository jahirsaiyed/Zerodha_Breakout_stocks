package com.trading.signals;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "signals")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Signal {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String symbol;

    @Column(name = "entry_price", nullable = false)
    private BigDecimal entryPrice;

    @Column(name = "stop_loss", nullable = false)
    private BigDecimal stopLoss;

    @Column(nullable = false)
    private BigDecimal target;

    @Column(name = "risk_reward_ratio", nullable = false)
    private BigDecimal riskRewardRatio;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SignalSource source = SignalSource.MANUAL;

    @Column(name = "source_ref")
    private String sourceRef;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SignalStatus status = SignalStatus.ACTIVE;

    private String notes;

    @CreationTimestamp
    @Column(name = "added_at", nullable = false, updatable = false)
    private LocalDateTime addedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

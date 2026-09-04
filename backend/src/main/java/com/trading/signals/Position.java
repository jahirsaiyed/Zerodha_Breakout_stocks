package com.trading.signals;

import com.trading.users.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "positions")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Position {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signal_id")
    private Signal signal;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "avg_entry_price")
    private BigDecimal avgEntryPrice;

    @Column(name = "entry_order_id")
    private String entryOrderId;

    @Column(name = "gtt_order_id")
    private String gttOrderId;

    @Column(name = "breakeven_sl")
    private BigDecimal breakevenSl;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PositionStatus status = PositionStatus.PENDING_ENTRY;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "entry_source", nullable = false)
    private EntrySource entrySource = EntrySource.AUTO;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "realised_pnl")
    private BigDecimal realisedPnl;
}

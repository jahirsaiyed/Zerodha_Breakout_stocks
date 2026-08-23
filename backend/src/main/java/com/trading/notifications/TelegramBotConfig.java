package com.trading.notifications;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "telegram_bot_config")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class TelegramBotConfig {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** AES-GCM encrypted bot token. Null when no bot is configured. */
    @Column(name = "bot_token", length = 2000)
    private String botToken;

    /** Telegram bot username without the @ prefix (e.g. "MyTradingBot"). */
    @Column(name = "bot_username")
    private String botUsername;

    /** Telegram bot display name (e.g. "My Trading Bot"). */
    @Column(name = "bot_name")
    private String botName;

    @Builder.Default
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = false;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

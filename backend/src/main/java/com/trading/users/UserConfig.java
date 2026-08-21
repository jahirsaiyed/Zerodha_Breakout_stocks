package com.trading.users;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_configs")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class UserConfig {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "max_positions", nullable = false) private Integer maxPositions = 5;

    @Enumerated(EnumType.STRING)
    @Column(name = "position_sizing_method", nullable = false)
    private PositionSizingMethod positionSizingMethod = PositionSizingMethod.FIXED;

    @Column(name = "position_sizing_value", nullable = false)
    private BigDecimal positionSizingValue = new BigDecimal("10000");

    @Column(name = "order_expiry_days", nullable = false) private Integer orderExpiryDays = 5;
    @Column(name = "zerodha_api_key") private String zerodhaApiKey;
    @Column(name = "zerodha_api_secret") private String zerodhaApiSecret;     // stored encrypted
    @Column(name = "zerodha_access_token") private String zerodhaAccessToken; // stored encrypted
    @Column(name = "zerodha_totp_secret") private String zerodhaTotpSecret;   // stored encrypted
    @Column(name = "telegram_chat_id") private String telegramChatId;
    @Column(name = "zerodha_connected", nullable = false) private Boolean zerodhaConnected = false;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt = LocalDateTime.now();

    public enum PositionSizingMethod { EQUAL, FIXED, RISK_BASED }
}

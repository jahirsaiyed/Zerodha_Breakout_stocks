package com.trading.notifications;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TelegramBotConfigRepository extends JpaRepository<TelegramBotConfig, Long> {

    /** Returns the single config row seeded by the migration. */
    Optional<TelegramBotConfig> findFirstBy();
}

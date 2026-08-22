package com.trading.users.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * System health snapshot returned by GET /api/admin/health.
 */
public record HealthResponse(
        int instrumentCacheSize,
        boolean instrumentCacheLoaded,
        LocalDateTime lastSyncAt,
        int lastSyncAdded,
        int lastSyncModified,
        List<UserZerodhaStatus> zerodhaStatuses
) {
    public record UserZerodhaStatus(Long userId, String email, boolean connected) {}
}

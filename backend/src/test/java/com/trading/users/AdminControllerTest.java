package com.trading.users;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.auth.JwtUtil;
import com.trading.portfolio.PortfolioDbService;
import com.trading.portfolio.PortfolioEngine;
import com.trading.signals.InstrumentCacheService;
import com.trading.signals.Position;
import com.trading.signals.PositionStatus;
import com.trading.signals.SignalSyncLog;
import com.trading.signals.SignalSyncLogRepository;
import com.trading.users.dto.UserResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
@Import(com.trading.config.SecurityConfig.class)
@TestPropertySource(properties = {
        "cors.allowed-origins=http://localhost:3000",
        "jwt.secret=test-secret-key-that-is-at-least-64-characters-long-for-hmac-sha"
})
class AdminControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean UserService userService;
    @MockBean UserConfigRepository userConfigRepository;
    @MockBean SignalSyncLogRepository syncLogRepository;
    @MockBean InstrumentCacheService instrumentCacheService;
    @MockBean PortfolioEngine portfolioEngine;
    @MockBean PortfolioDbService portfolioDbService;
    @MockBean JwtUtil jwtUtil;

    // ── GET /admin/health ─────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    @DisplayName("GET /admin/health returns health snapshot")
    void health_returnsSnapshot() throws Exception {
        SignalSyncLog log = new SignalSyncLog();
        log.setSyncedAt(LocalDateTime.of(2026, 8, 22, 9, 0));
        log.setSignalsAdded(5);
        log.setSignalsModified(2);

        when(syncLogRepository.findAllByOrderBySyncedAtDesc(any(Pageable.class))).thenReturn(List.of(log));
        when(instrumentCacheService.getCacheSize()).thenReturn(2000);
        when(userConfigRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.instrumentCacheSize").value(2000))
                .andExpect(jsonPath("$.data.instrumentCacheLoaded").value(true))
                .andExpect(jsonPath("$.data.lastSyncAdded").value(5))
                .andExpect(jsonPath("$.data.lastSyncModified").value(2));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    @DisplayName("GET /admin/health returns safe defaults when no sync log exists")
    void health_noSyncLog_returnsDefaults() throws Exception {
        when(syncLogRepository.findAllByOrderBySyncedAtDesc(any(Pageable.class))).thenReturn(List.of());
        when(instrumentCacheService.getCacheSize()).thenReturn(0);
        when(userConfigRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.instrumentCacheLoaded").value(false))
                .andExpect(jsonPath("$.data.lastSyncAt").isEmpty())
                .andExpect(jsonPath("$.data.lastSyncAdded").value(0));
    }

    @Test
    @DisplayName("GET /admin/health requires authentication")
    void health_unauthenticated_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/health"))
                .andExpect(status().isForbidden());
    }

    // ── GET /admin/users ──────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    @DisplayName("GET /admin/users returns user list")
    void listUsers_returnsUsers() throws Exception {
        when(userService.getAllUsers()).thenReturn(List.of(
                new UserResponse(1L, "Alice", "alice@test.com", "USER", true)));

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].email").value("alice@test.com"));
    }

    @Test
    @DisplayName("GET /admin/users requires authentication")
    void listUsers_unauthenticated_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());
    }

    // ── PATCH /admin/users/{id}/status ────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    @DisplayName("PATCH /admin/users/{id}/status toggles user active status")
    void setStatus_validRequest_returns200() throws Exception {
        doNothing().when(userService).setUserActive(1L, false);

        mockMvc.perform(patch("/api/admin/users/1/status").param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(userService).setUserActive(1L, false);
    }

    // ── POST /admin/portfolio/positions/{id}/confirm-fill ───────────────────────

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    @DisplayName("POST /admin/portfolio/positions/{id}/confirm-fill activates the position")
    void confirmFill_validRequest_returnsUpdatedPosition() throws Exception {
        Position updated = Position.builder()
                .id(10L).symbol("BALRAMCHIN").quantity(4)
                .avgEntryPrice(BigDecimal.valueOf(410.5))
                .status(PositionStatus.ACTIVE)
                .build();
        when(portfolioDbService.getPositionById(10L)).thenReturn(Optional.of(updated));

        mockMvc.perform(post("/api/admin/portfolio/positions/10/confirm-fill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":4,\"avgPrice\":410.50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.symbol").value("BALRAMCHIN"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        verify(portfolioEngine).confirmManualFill(10L, 4, new BigDecimal("410.50"));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    @DisplayName("POST /admin/portfolio/positions/{id}/confirm-fill rejects non-positive quantity")
    void confirmFill_invalidQuantity_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/portfolio/positions/10/confirm-fill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":0,\"avgPrice\":410.50}"))
                .andExpect(status().isBadRequest());

        verify(portfolioEngine, never()).confirmManualFill(any(), anyInt(), any());
    }

    @Test
    @DisplayName("POST /admin/portfolio/positions/{id}/confirm-fill requires authentication")
    void confirmFill_unauthenticated_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/portfolio/positions/10/confirm-fill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":4,\"avgPrice\":410.50}"))
                .andExpect(status().isForbidden());
    }
}

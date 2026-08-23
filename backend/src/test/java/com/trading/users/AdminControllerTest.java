package com.trading.users;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.auth.JwtUtil;
import com.trading.notifications.TelegramBotConfigDto;
import com.trading.notifications.TelegramBotConfigService;
import com.trading.portfolio.PortfolioEngine;
import com.trading.signals.InstrumentCacheService;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
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
    @MockBean JwtUtil jwtUtil;
    @MockBean TelegramBotConfigService telegramBotConfigService;

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

    // ── GET /admin/telegram/bot ───────────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    @DisplayName("GET /admin/telegram/bot returns current bot config")
    void getBotConfig_returnsConfig() throws Exception {
        TelegramBotConfigDto dto = new TelegramBotConfigDto("My Bot", "mybot", true, true);
        when(telegramBotConfigService.getConfig()).thenReturn(dto);

        mockMvc.perform(get("/api/admin/telegram/bot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.botName").value("My Bot"))
                .andExpect(jsonPath("$.data.botUsername").value("mybot"))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.hasToken").value(true));
    }

    @Test
    @DisplayName("GET /admin/telegram/bot requires authentication")
    void getBotConfig_unauthenticated_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/telegram/bot"))
                .andExpect(status().isForbidden());
    }

    // ── POST /admin/telegram/bot ──────────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    @DisplayName("POST /admin/telegram/bot connects bot and returns config with bot name")
    void connectBot_validToken_returnsConnectedConfig() throws Exception {
        TelegramBotConfigDto dto = new TelegramBotConfigDto("Trading Bot", "tradingbot", true, true);
        when(telegramBotConfigService.connectBot("valid-token-123")).thenReturn(dto);

        mockMvc.perform(post("/api/admin/telegram/bot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"botToken\":\"valid-token-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.botName").value("Trading Bot"))
                .andExpect(jsonPath("$.data.botUsername").value("tradingbot"))
                .andExpect(jsonPath("$.data.enabled").value(true));

        verify(telegramBotConfigService).connectBot("valid-token-123");
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    @DisplayName("POST /admin/telegram/bot returns 400 when token is rejected by Telegram")
    void connectBot_invalidToken_returns400() throws Exception {
        when(telegramBotConfigService.connectBot("bad-token"))
                .thenThrow(new IllegalArgumentException("Telegram rejected the token"));

        mockMvc.perform(post("/api/admin/telegram/bot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"botToken\":\"bad-token\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    @DisplayName("POST /admin/telegram/bot returns 400 when botToken is blank")
    void connectBot_blankToken_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/telegram/bot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"botToken\":\"\"}"))
                .andExpect(status().isBadRequest());

        verify(telegramBotConfigService, never()).connectBot(any());
    }

    @Test
    @DisplayName("POST /admin/telegram/bot requires authentication")
    void connectBot_unauthenticated_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/telegram/bot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"botToken\":\"token\"}"))
                .andExpect(status().isForbidden());
    }

    // ── DELETE /admin/telegram/bot ────────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    @DisplayName("DELETE /admin/telegram/bot disconnects the bot")
    void disconnectBot_returns200() throws Exception {
        doNothing().when(telegramBotConfigService).disconnectBot();

        mockMvc.perform(delete("/api/admin/telegram/bot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(telegramBotConfigService).disconnectBot();
    }

    @Test
    @DisplayName("DELETE /admin/telegram/bot requires authentication")
    void disconnectBot_unauthenticated_returns403() throws Exception {
        mockMvc.perform(delete("/api/admin/telegram/bot"))
                .andExpect(status().isForbidden());
    }
}

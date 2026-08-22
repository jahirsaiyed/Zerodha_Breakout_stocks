package com.trading.signals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.signals.dto.CreateSignalRequest;
import com.trading.signals.dto.SignalResponse;
import com.trading.signals.dto.SyncLogResponse;
import com.trading.signals.dto.UpdateSignalRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SignalController.class)
@Import(com.trading.config.SecurityConfig.class)
@TestPropertySource(properties = {
        "cors.allowed-origins=http://localhost:3000",
        "jwt.secret=test-secret-key-that-is-at-least-64-characters-long-for-hmac-sha"
})
class SignalControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean SignalService signalService;
    @MockBean SheetSyncService sheetSyncService;
    @MockBean com.trading.auth.JwtUtil jwtUtil;

    private static final SignalResponse SAMPLE = new SignalResponse(
            1L, "RELIANCE",
            new BigDecimal("100"), new BigDecimal("90"), new BigDecimal("120"),
            new BigDecimal("2.0000"),
            SignalSource.MANUAL, null, SignalStatus.ACTIVE, null,
            LocalDateTime.now(), LocalDateTime.now());

    @Test
    @WithMockUser
    @DisplayName("GET /api/signals returns 200 with list")
    void list_returns200() throws Exception {
        when(signalService.list(null)).thenReturn(List.of(SAMPLE));

        mockMvc.perform(get("/api/signals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].symbol").value("RELIANCE"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/signals?status=ACTIVE filters by status")
    void list_withStatus_returns200() throws Exception {
        when(signalService.list(SignalStatus.ACTIVE)).thenReturn(List.of(SAMPLE));

        mockMvc.perform(get("/api/signals").param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/signals valid request returns 201")
    void create_valid_returns201() throws Exception {
        CreateSignalRequest req = new CreateSignalRequest(
                "RELIANCE", new BigDecimal("100"), new BigDecimal("90"), new BigDecimal("120"), null);
        when(signalService.create(any())).thenReturn(SAMPLE);

        mockMvc.perform(post("/api/signals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/signals missing symbol returns 400")
    void create_missingSymbol_returns400() throws Exception {
        String body = "{\"entryPrice\":100,\"stopLoss\":90,\"target\":120}";

        mockMvc.perform(post("/api/signals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/signals business rule violation returns 400")
    void create_invalidPrices_returns400() throws Exception {
        CreateSignalRequest req = new CreateSignalRequest(
                "X", new BigDecimal("90"), new BigDecimal("100"), new BigDecimal("120"), null);
        when(signalService.create(any())).thenThrow(
                new IllegalArgumentException("entry_price must be greater than stop_loss"));

        mockMvc.perform(post("/api/signals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("entry_price must be greater than stop_loss"));
    }

    @Test
    @WithMockUser
    @DisplayName("PUT /api/signals/{id} returns 200 with updated signal")
    void update_returns200() throws Exception {
        UpdateSignalRequest req = new UpdateSignalRequest(
                new BigDecimal("105"), new BigDecimal("92"), new BigDecimal("130"), null);
        when(signalService.update(eq(1L), any())).thenReturn(SAMPLE);

        mockMvc.perform(put("/api/signals/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser
    @DisplayName("DELETE /api/signals/{id} returns 200 with cancelled signal")
    void cancel_returns200() throws Exception {
        SignalResponse cancelled = new SignalResponse(
                1L, "RELIANCE", new BigDecimal("100"), new BigDecimal("90"), new BigDecimal("120"),
                new BigDecimal("2.0000"), SignalSource.MANUAL, null, SignalStatus.CANCELLED,
                null, LocalDateTime.now(), LocalDateTime.now());
        when(signalService.cancel(1L)).thenReturn(cancelled);

        mockMvc.perform(delete("/api/signals/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/signals/sync-log returns 200 with list")
    void syncLog_returns200() throws Exception {
        SyncLogResponse entry = new SyncLogResponse(
                1L, LocalDateTime.now(), SignalSource.MANUAL, 2, 0, 0, null);
        when(signalService.getSyncLog()).thenReturn(List.of(entry));

        mockMvc.perform(get("/api/signals/sync-log"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].signalsAdded").value(2));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/signals/sync returns 200 with sync result")
    void syncNow_returns200() throws Exception {
        when(sheetSyncService.sync()).thenReturn(new SyncResult(1, 0, 0, 0));

        mockMvc.perform(post("/api/signals/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.added").value(1));
    }

    @Test
    @DisplayName("GET /api/signals unauthenticated returns 401 or 403")
    void list_unauthenticated_returns401or403() throws Exception {
        mockMvc.perform(get("/api/signals"))
                .andExpect(status().is4xxClientError());
    }
}

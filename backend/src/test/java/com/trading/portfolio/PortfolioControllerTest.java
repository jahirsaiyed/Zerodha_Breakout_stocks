package com.trading.portfolio;

import com.trading.portfolio.dto.PositionResponse;
import com.trading.signals.Position;
import com.trading.signals.PositionStatus;
import com.trading.users.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PortfolioController.class)
@Import(com.trading.config.SecurityConfig.class)
@TestPropertySource(properties = {
        "cors.allowed-origins=http://localhost:3000",
        "jwt.secret=test-secret-key-that-is-at-least-64-characters-long-for-hmac-sha"
})
class PortfolioControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean PortfolioDbService db;
    @MockBean PortfolioEngine engine;
    @MockBean com.trading.broker.BrokerAdapterFactory brokerAdapterFactory;
    @MockBean com.trading.signals.OrderRepository orderRepository;
    @MockBean com.trading.auth.JwtUtil jwtUtil;

    @Test
    @WithMockUser(username = "user@example.com")
    @DisplayName("GET /api/portfolio/positions returns all positions for user")
    void getPositions_authenticated_returnsPositions() throws Exception {
        when(db.getUserIdByEmail("user@example.com")).thenReturn(1L);
        when(db.getAllPositionsForUser(1L)).thenReturn(List.of(buildPosition()));

        mockMvc.perform(get("/api/portfolio/positions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("RELIANCE"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    @WithMockUser(username = "user@example.com")
    @DisplayName("GET /api/portfolio/positions?status=ACTIVE filters by status")
    void getPositions_withStatusFilter_filtersCorrectly() throws Exception {
        when(db.getUserIdByEmail("user@example.com")).thenReturn(1L);
        when(db.getPositionsByStatus(1L, PositionStatus.ACTIVE)).thenReturn(List.of(buildPosition()));

        mockMvc.perform(get("/api/portfolio/positions").param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));

        verify(db).getPositionsByStatus(1L, PositionStatus.ACTIVE);
        verify(db, never()).getAllPositionsForUser(anyLong());
    }

    @Test
    @DisplayName("GET /api/portfolio/positions requires authentication")
    void getPositions_unauthenticated_returns403() throws Exception {
        mockMvc.perform(get("/api/portfolio/positions"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "user@example.com")
    @DisplayName("POST /api/portfolio/positions/{id}/exit triggers manual exit")
    void manualExit_validPosition_returns200() throws Exception {
        Position pos = buildPosition();
        pos.setId(10L);

        when(db.getUserIdByEmail("user@example.com")).thenReturn(1L);
        when(db.getActivePositions()).thenReturn(List.of(pos));
        doNothing().when(engine).manualExit(10L);

        Position closed = buildPosition();
        closed.setId(10L);
        closed.setStatus(PositionStatus.CLOSED_MANUAL);
        when(db.getAllPositionsForUser(1L)).thenReturn(List.of(closed));

        mockMvc.perform(post("/api/portfolio/positions/10/exit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED_MANUAL"));

        verify(engine).manualExit(10L);
    }

    @Test
    @WithMockUser(username = "user@example.com")
    @DisplayName("POST /api/portfolio/positions/{id}/exit returns 400 for non-existent position")
    void manualExit_positionNotFound_returns400() throws Exception {
        when(db.getUserIdByEmail("user@example.com")).thenReturn(1L);
        when(db.getActivePositions()).thenReturn(List.of());

        mockMvc.perform(post("/api/portfolio/positions/99/exit"))
                .andExpect(status().isBadRequest());
    }

    private Position buildPosition() {
        User user = User.builder()
                .id(1L).email("user@example.com").name("Test").passwordHash("x").build();
        Position p = new Position();
        p.setId(10L);
        p.setUser(user);
        p.setSymbol("RELIANCE");
        p.setQuantity(5);
        p.setAvgEntryPrice(BigDecimal.valueOf(2400));
        p.setStatus(PositionStatus.ACTIVE);
        p.setOpenedAt(LocalDateTime.now());
        return p;
    }
}

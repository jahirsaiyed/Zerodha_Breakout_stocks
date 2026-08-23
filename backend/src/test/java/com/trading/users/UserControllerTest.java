package com.trading.users;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.auth.JwtUtil;
import com.trading.broker.BrokerAdapterFactory;
import com.trading.notifications.NotificationService;
import com.trading.portfolio.PortfolioDbService;
import com.trading.users.dto.UserResponse;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import(com.trading.config.SecurityConfig.class)
@TestPropertySource(properties = {
        "cors.allowed-origins=http://localhost:3000",
        "jwt.secret=test-secret-key-that-is-at-least-64-characters-long-for-hmac-sha"
})
class UserControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean UserService userService;
    @MockBean NotificationService notificationService;
    @MockBean BrokerAdapterFactory brokerAdapterFactory;
    @MockBean PortfolioDbService portfolioDbService;
    @MockBean JwtUtil jwtUtil;

    // ── POST /me/password ─────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "alice@test.com")
    @DisplayName("POST /me/password returns 200 on valid current password")
    void changePassword_valid_returns200() throws Exception {
        doNothing().when(userService).changePassword("alice@test.com", "oldPass1", "newPass123");

        mockMvc.perform(post("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"oldPass1\",\"newPassword\":\"newPass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(userService).changePassword("alice@test.com", "oldPass1", "newPass123");
    }

    @Test
    @WithMockUser(username = "alice@test.com")
    @DisplayName("POST /me/password returns 400 when newPassword is too short")
    void changePassword_shortPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"oldPass1\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());

        verify(userService, never()).changePassword(any(), any(), any());
    }

    @Test
    @WithMockUser(username = "alice@test.com")
    @DisplayName("POST /me/password returns 400 when currentPassword is blank")
    void changePassword_blankCurrentPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"\",\"newPassword\":\"newPass123\"}"))
                .andExpect(status().isBadRequest());

        verify(userService, never()).changePassword(any(), any(), any());
    }

    @Test
    @WithMockUser(username = "alice@test.com")
    @DisplayName("POST /me/password returns 400 when current password is wrong")
    void changePassword_wrongCurrentPassword_returns400() throws Exception {
        doThrow(new IllegalArgumentException("Current password is incorrect"))
                .when(userService).changePassword("alice@test.com", "wrongPass", "newPass123");

        mockMvc.perform(post("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrongPass\",\"newPassword\":\"newPass123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /me/password requires authentication")
    void changePassword_unauthenticated_returns403() throws Exception {
        mockMvc.perform(post("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old\",\"newPassword\":\"newPass123\"}"))
                .andExpect(status().isForbidden());
    }

    // ── POST /me/telegram/test ────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "alice@test.com")
    @DisplayName("POST /me/telegram/test returns 200 and sends notification")
    void testTelegram_authenticated_returns200() throws Exception {
        UserResponse userResp = new UserResponse(1L, "Alice", "alice@test.com", "USER", true);
        when(userService.getUserByEmail("alice@test.com")).thenReturn(userResp);
        doNothing().when(notificationService).notifyUser(eq(1L), anyString());

        mockMvc.perform(post("/api/users/me/telegram/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(notificationService).notifyUser(eq(1L), contains("Telegram notifications are working"));
    }

    @Test
    @DisplayName("POST /me/telegram/test requires authentication")
    void testTelegram_unauthenticated_returns403() throws Exception {
        mockMvc.perform(post("/api/users/me/telegram/test"))
                .andExpect(status().isForbidden());
    }
}

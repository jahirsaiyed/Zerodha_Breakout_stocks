package com.trading.zerodha;

import com.trading.broker.ZerodhaProperties;
import com.trading.portfolio.PortfolioDbService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ZerodhaAuthController.class)
@Import(com.trading.config.SecurityConfig.class)
@TestPropertySource(properties = {
        "cors.allowed-origins=http://localhost:3000",
        "jwt.secret=test-secret-key-that-is-at-least-64-characters-long-for-hmac-sha"
})
class ZerodhaAuthControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean ZerodhaAuthService zerodhaAuthService;
    @MockBean ZerodhaProperties zerodhaProperties;
    @MockBean PortfolioDbService portfolioDbService;
    @MockBean com.trading.auth.JwtUtil jwtUtil;

    @Test
    @WithMockUser(username = "user@example.com")
    @DisplayName("GET /api/zerodha/login redirects to Zerodha login URL")
    void login_authenticated_redirectsToZerodha() throws Exception {
        when(portfolioDbService.getUserIdByEmail("user@example.com")).thenReturn(1L);
        when(zerodhaAuthService.initiate(1L))
                .thenReturn(new ZerodhaAuthService.OAuthInitResult("nonce123",
                        "https://kite.zerodha.com/connect/login?v=3&api_key=testKey"));

        mockMvc.perform(get("/api/zerodha/login"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location",
                        "https://kite.zerodha.com/connect/login?v=3&api_key=testKey"));
    }

    @Test
    @WithMockUser(username = "user@example.com")
    @DisplayName("GET /api/zerodha/login redirects to error page when initiation fails")
    void login_initiationFails_redirectsToError() throws Exception {
        when(portfolioDbService.getUserIdByEmail("user@example.com")).thenReturn(1L);
        when(zerodhaAuthService.initiate(1L)).thenThrow(new IllegalStateException("API key not configured"));
        when(zerodhaProperties.getFrontendUrl()).thenReturn("http://localhost:5173");

        mockMvc.perform(get("/api/zerodha/login"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location",
                        "http://localhost:5173/settings?zerodha=error&reason=init_failed"));
    }

    @Test
    @DisplayName("GET /api/zerodha/login requires authentication")
    void login_unauthenticated_returns403() throws Exception {
        mockMvc.perform(get("/api/zerodha/login"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/zerodha/callback success → redirects to frontend settings connected")
    void callback_successStatus_redirectsToFrontendConnected() throws Exception {
        when(zerodhaProperties.getFrontendUrl()).thenReturn("http://localhost:5173");
        doNothing().when(zerodhaAuthService).complete(anyString(), anyString());

        mockMvc.perform(get("/api/zerodha/callback")
                        .param("status", "success")
                        .param("request_token", "tok123")
                        .cookie(new Cookie("zerodha_oauth_nonce", "nonce123")))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location",
                        "http://localhost:5173/settings?zerodha=connected"));
    }

    @Test
    @DisplayName("GET /api/zerodha/callback non-success → redirects to error")
    void callback_nonSuccessStatus_redirectsToError() throws Exception {
        when(zerodhaProperties.getFrontendUrl()).thenReturn("http://localhost:5173");

        mockMvc.perform(get("/api/zerodha/callback")
                        .param("status", "failure")
                        .param("request_token", "tok123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location",
                        "http://localhost:5173/settings?zerodha=error"));
    }

    @Test
    @DisplayName("GET /api/zerodha/callback missing nonce → redirects with session_expired")
    void callback_missingNonce_redirectsWithSessionExpired() throws Exception {
        when(zerodhaProperties.getFrontendUrl()).thenReturn("http://localhost:5173");

        mockMvc.perform(get("/api/zerodha/callback")
                        .param("status", "success")
                        .param("request_token", "tok123"))
                // no nonce cookie
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location",
                        "http://localhost:5173/settings?zerodha=error&reason=session_expired"));
    }

    @Test
    @DisplayName("GET /api/zerodha/callback with mobile User-Agent redirects to deep link")
    void callback_redirectsToDeepLinkForMobileClient() throws Exception {
        Cookie nonce = new Cookie("zerodha_oauth_nonce", "valid-nonce");
        doNothing().when(zerodhaAuthService).complete(eq("valid-nonce"), eq("req-token-abc"));

        mockMvc.perform(get("/api/zerodha/callback")
                        .param("request_token", "req-token-abc")
                        .param("status", "success")
                        .cookie(nonce)
                        .header("User-Agent", "ZerodhaBreakoutMobile/1.0"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "zbs://zerodha-callback?status=connected"));
    }

    @Test
    @WithMockUser(username = "user@example.com")
    @DisplayName("GET /api/zerodha/status returns connected flag")
    void status_authenticated_returnsConnectedStatus() throws Exception {
        when(portfolioDbService.getUserIdByEmail("user@example.com")).thenReturn(1L);
        when(zerodhaAuthService.isConnected(1L)).thenReturn(true);

        mockMvc.perform(get("/api/zerodha/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected").value(true));
    }

    @Test
    @WithMockUser(username = "user@example.com")
    @DisplayName("DELETE /api/zerodha/disconnect disconnects user and returns 200")
    void disconnect_authenticated_returns200() throws Exception {
        when(portfolioDbService.getUserIdByEmail("user@example.com")).thenReturn(1L);
        doNothing().when(zerodhaAuthService).disconnect(1L);

        mockMvc.perform(delete("/api/zerodha/disconnect"))
                .andExpect(status().isOk());

        verify(zerodhaAuthService).disconnect(1L);
    }
}

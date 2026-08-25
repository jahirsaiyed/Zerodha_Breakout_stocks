package com.trading.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.auth.dto.LoginRequest;
import com.trading.auth.dto.RefreshRequest;
import com.trading.auth.dto.TokenResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MobileAuthController.class)
@Import(com.trading.config.SecurityConfig.class)
@TestPropertySource(properties = {
    "cors.allowed-origins=http://localhost:3000",
    "jwt.secret=test-secret-key-that-is-at-least-64-characters-long-for-hmac-sha"
})
class MobileAuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean MobileAuthService mobileAuthService;
    @MockBean JwtUtil jwtUtil;

    @Test
    void token_returnsAccessAndRefreshTokenOnValidCredentials() throws Exception {
        var pair = new TokenResponse("access.jwt.token", "raw-refresh-uuid");
        when(mobileAuthService.login(any())).thenReturn(pair);

        mockMvc.perform(post("/api/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest("alice@test.com", "pw"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access.jwt.token"))
                .andExpect(jsonPath("$.data.refreshToken").value("raw-refresh-uuid"));
    }

    @Test
    void token_returns401OnBadCredentials() throws Exception {
        when(mobileAuthService.login(any())).thenThrow(new BadCredentialsException("bad"));

        mockMvc.perform(post("/api/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest("x@x.com", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void refresh_returnsNewTokenPairOnValidToken() throws Exception {
        var pair = new TokenResponse("new.access.token", "new-refresh-uuid");
        when(mobileAuthService.refresh(eq("old-refresh-uuid"))).thenReturn(pair);

        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshRequest("old-refresh-uuid"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("new.access.token"));
    }

    @Test
    void revoke_returns200AndDelegates() throws Exception {
        mockMvc.perform(post("/api/auth/revoke")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshRequest("some-uuid"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verify(mobileAuthService).revoke("some-uuid");
    }
}

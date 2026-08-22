package com.trading.zerodha;

import com.trading.broker.ZerodhaProperties;
import com.trading.common.ApiResponse;
import com.trading.portfolio.PortfolioDbService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

/**
 * Zerodha OAuth endpoints.
 *
 * <h3>Flow</h3>
 * <ol>
 *   <li>{@code GET /api/zerodha/login} — authenticated; sets a short-lived nonce cookie
 *       and redirects the browser to the Zerodha login page.</li>
 *   <li>Zerodha redirects back to {@code GET /api/zerodha/callback?request_token=xxx&status=success}</li>
 *   <li>Backend reads the nonce cookie, exchanges the request_token, stores the encrypted
 *       access_token, then redirects to the frontend settings page.</li>
 * </ol>
 */
@Slf4j
@RestController
@RequestMapping("/api/zerodha")
@RequiredArgsConstructor
@Tag(name = "Zerodha", description = "Zerodha OAuth and token management")
public class ZerodhaAuthController {

    private static final String NONCE_COOKIE = "zerodha_oauth_nonce";
    private static final int    NONCE_TTL_S  = 600; // 10 minutes

    private final ZerodhaAuthService zerodhaAuthService;
    private final ZerodhaProperties  zerodhaProperties;
    private final PortfolioDbService portfolioDbService;

    // ── GET /api/zerodha/login ───────────────────────────────────────────────

    @GetMapping("/login")
    @Operation(summary = "Initiate Zerodha OAuth login",
               description = "Sets a nonce cookie and redirects the user to the Zerodha login page")
    public void login(@AuthenticationPrincipal UserDetails principal,
                      HttpServletResponse response) throws IOException {
        Long userId = portfolioDbService.getUserIdByEmail(principal.getUsername());
        ZerodhaAuthService.OAuthInitResult result = zerodhaAuthService.initiate(userId);

        // Store nonce in a short-lived HttpOnly cookie so we can read it on callback
        Cookie nonceCookie = new Cookie(NONCE_COOKIE, result.nonce());
        nonceCookie.setHttpOnly(true);
        nonceCookie.setSecure(false); // set to true in production behind HTTPS
        nonceCookie.setPath("/api/zerodha");
        nonceCookie.setMaxAge(NONCE_TTL_S);
        response.addCookie(nonceCookie);

        response.sendRedirect(result.loginUrl());
    }

    // ── GET /api/zerodha/callback ────────────────────────────────────────────

    @GetMapping("/callback")
    @Operation(summary = "Zerodha OAuth callback (public)",
               description = "Receives request_token from Zerodha, exchanges for access_token, redirects to frontend")
    public void callback(@RequestParam(required = false) String request_token,
                         @RequestParam(required = false) String status,
                         HttpServletRequest  request,
                         HttpServletResponse response) throws IOException {
        String frontendBase = zerodhaProperties.getFrontendUrl();

        if (!"success".equalsIgnoreCase(status) || request_token == null) {
            log.warn("Zerodha callback with non-success status: {}", status);
            response.sendRedirect(frontendBase + "/settings?zerodha=error");
            return;
        }

        String nonce = extractNonceCookie(request);
        if (nonce == null) {
            log.warn("Zerodha callback: nonce cookie missing");
            response.sendRedirect(frontendBase + "/settings?zerodha=error&reason=session_expired");
            return;
        }

        // Clear the nonce cookie
        Cookie cleared = new Cookie(NONCE_COOKIE, "");
        cleared.setMaxAge(0);
        cleared.setPath("/api/zerodha");
        response.addCookie(cleared);

        try {
            zerodhaAuthService.complete(nonce, request_token);
            response.sendRedirect(frontendBase + "/settings?zerodha=connected");
        } catch (Exception e) {
            log.error("Zerodha OAuth callback error: {}", e.getMessage(), e);
            response.sendRedirect(frontendBase + "/settings?zerodha=error&reason=token_exchange_failed");
        }
    }

    // ── GET /api/zerodha/status ──────────────────────────────────────────────

    @GetMapping("/status")
    @Operation(summary = "Check Zerodha connection status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> status(
            @AuthenticationPrincipal UserDetails principal) {
        Long userId = portfolioDbService.getUserIdByEmail(principal.getUsername());
        boolean connected = zerodhaAuthService.isConnected(userId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("connected", connected)));
    }

    // ── DELETE /api/zerodha/disconnect ───────────────────────────────────────

    @DeleteMapping("/disconnect")
    @Operation(summary = "Disconnect Zerodha — clears the stored access token")
    public ResponseEntity<ApiResponse<Void>> disconnect(
            @AuthenticationPrincipal UserDetails principal) {
        Long userId = portfolioDbService.getUserIdByEmail(principal.getUsername());
        zerodhaAuthService.disconnect(userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ── GET /api/zerodha/totp ────────────────────────────────────────────────

    @GetMapping("/totp")
    @Operation(summary = "Generate current TOTP code",
               description = "Returns the 6-digit TOTP code for the user's stored TOTP secret. "
                           + "Returns null if no TOTP secret is configured.")
    public ResponseEntity<ApiResponse<Map<String, String>>> totp(
            @AuthenticationPrincipal UserDetails principal) {
        Long userId = portfolioDbService.getUserIdByEmail(principal.getUsername());
        String code = zerodhaAuthService.generateTotp(userId);
        return ResponseEntity.ok(ApiResponse.success(
                code != null ? Map.of("code", code) : Map.of()));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String extractNonceCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        return Arrays.stream(cookies)
                .filter(c -> NONCE_COOKIE.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}

package com.trading.common;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("IllegalArgumentException → 400 with message")
    void handleIllegalArgument_returns400() {
        ResponseEntity<ApiResponse<Void>> res =
                handler.handleIllegalArgument(new IllegalArgumentException("bad input"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().success()).isFalse();
        assertThat(res.getBody().error()).isEqualTo("bad input");
    }

    @Test
    @DisplayName("BadCredentialsException → 401 with message")
    void handleBadCredentials_returns401() {
        ResponseEntity<ApiResponse<Void>> res =
                handler.handleBadCredentials(new BadCredentialsException("Invalid credentials"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(res.getBody().success()).isFalse();
        assertThat(res.getBody().error()).isEqualTo("Invalid credentials");
    }

    @Test
    @DisplayName("AccessDeniedException → 403 with generic message")
    void handleAccessDenied_returns403() {
        ResponseEntity<ApiResponse<Void>> res =
                handler.handleAccessDenied(new AccessDeniedException("forbidden"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(res.getBody().error()).isEqualTo("Access denied");
    }

    @Test
    @DisplayName("MethodArgumentNotValidException → 400 with field error message")
    void handleValidation_returns400WithFieldMessage() throws Exception {
        var bindingResult = new BeanPropertyBindingResult(new Object(), "obj");
        bindingResult.addError(new FieldError("obj", "name", "must not be blank"));
        var ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<ApiResponse<Void>> res = handler.handleValidation(ex);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().error()).contains("must not be blank");
    }

    @Test
    @DisplayName("MethodArgumentNotValidException with multiple errors joins messages")
    void handleValidation_multipleErrors_joinsMessages() throws Exception {
        var bindingResult = new BeanPropertyBindingResult(new Object(), "obj");
        bindingResult.addError(new FieldError("obj", "name", "must not be blank"));
        bindingResult.addError(new FieldError("obj", "email", "invalid email"));
        var ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<ApiResponse<Void>> res = handler.handleValidation(ex);

        assertThat(res.getBody().error()).contains("must not be blank");
        assertThat(res.getBody().error()).contains("invalid email");
    }

    @Test
    @DisplayName("Generic Exception → 500 with safe message")
    void handleGeneral_returns500() {
        ResponseEntity<ApiResponse<Void>> res =
                handler.handleGeneral(new RuntimeException("sensitive internal detail"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(res.getBody().success()).isFalse();
        assertThat(res.getBody().error()).isEqualTo("Internal server error");
        // Must not leak internal detail
        assertThat(res.getBody().error()).doesNotContain("sensitive internal detail");
    }
}

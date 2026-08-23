package com.trading.notifications;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelegramApiClientTest {

    @Mock RestTemplate restTemplate;
    @Mock TelegramBotConfigService botConfigService;

    private TelegramApiClient buildClient(boolean enabled, String token) {
        TelegramProperties props = new TelegramProperties();
        props.setBaseUrl("https://api.telegram.org");

        if (!enabled || token == null || token.isBlank()) {
            lenient().when(botConfigService.getActiveToken()).thenReturn(Optional.empty());
        } else {
            lenient().when(botConfigService.getActiveToken()).thenReturn(Optional.of(token));
        }

        TelegramApiClient client = new TelegramApiClient(botConfigService, props);
        ReflectionTestUtils.setField(client, "restTemplate", restTemplate);
        return client;
    }

    @Test
    @DisplayName("sendMessage does nothing when telegram is disabled")
    void sendMessage_disabled_skips() {
        TelegramApiClient client = buildClient(false, "some-token");
        assertThat(client.sendMessage("12345", "Hello")).isNull();
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("sendMessage does nothing when chatId is blank")
    void sendMessage_blankChatId_skips() {
        TelegramApiClient client = buildClient(true, "some-token");
        assertThat(client.sendMessage("", "Hello")).isNull();
        assertThat(client.sendMessage(null, "Hello")).isNull();
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("sendMessage does nothing when bot token is blank")
    void sendMessage_noToken_skips() {
        TelegramApiClient client = buildClient(true, "");
        assertThat(client.sendMessage("12345", "Hello")).isNull();
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("sendMessage returns original chatId on success")
    void sendMessage_success_returnsOriginalChatId() {
        TelegramApiClient client = buildClient(true, "token");
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"ok\":true}");

        assertThat(client.sendMessage("12345", "Hello")).isEqualTo("12345");
    }

    @Test
    @DisplayName("sendMessage returns null on non-migration error")
    void sendMessage_genericError_returnsNull() {
        TelegramApiClient client = buildClient(true, "token");
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenThrow(new RuntimeException("network timeout"));

        assertThat(client.sendMessage("12345", "Hello")).isNull();
    }

    @Test
    @DisplayName("sendMessage retries with migrated ID and returns it on supergroup migration")
    void sendMessage_supergroupMigration_retriesAndReturnsMigratedId() {
        TelegramApiClient client = buildClient(true, "token");

        String errorBody = """
                {"ok":false,"error_code":400,
                 "description":"Bad Request: group chat was upgraded to a supergroup chat",
                 "parameters":{"migrate_to_chat_id":-1004474684836}}
                """;
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY, errorBody.getBytes(), StandardCharsets.UTF_8))
                .thenReturn("{\"ok\":true}");

        String result = client.sendMessage("-4772311755", "Alert");

        assertThat(result).isEqualTo("-1004474684836");
        verify(restTemplate, times(2)).postForObject(anyString(), any(), eq(String.class));
    }

    @Test
    @DisplayName("sendMessage returns null when retry after migration also fails")
    void sendMessage_migrationRetryFails_returnsNull() {
        TelegramApiClient client = buildClient(true, "token");

        String errorBody = """
                {"ok":false,"error_code":400,
                 "description":"Bad Request: group chat was upgraded to a supergroup chat",
                 "parameters":{"migrate_to_chat_id":-1004474684836}}
                """;
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY, errorBody.getBytes(), StandardCharsets.UTF_8))
                .thenThrow(new RuntimeException("still failing"));

        assertThat(client.sendMessage("-4772311755", "Alert")).isNull();
    }

    @Test
    @DisplayName("sendMessage returns null for 400 without migrate_to_chat_id")
    void sendMessage_badRequestNoMigration_returnsNull() {
        TelegramApiClient client = buildClient(true, "token");

        String errorBody = "{\"ok\":false,\"error_code\":400,\"description\":\"Bad Request: chat not found\"}";
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY, errorBody.getBytes(), StandardCharsets.UTF_8));

        assertThat(client.sendMessage("99999", "Hello")).isNull();
        verify(restTemplate, times(1)).postForObject(anyString(), any(), eq(String.class));
    }
}

package com.trading.notifications;

import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelegramApiClientTest {

    @Mock RestTemplate restTemplate;

    TelegramApiClient client;

    @BeforeEach
    void setUp() {
        TelegramProperties props = new TelegramProperties();
        props.setBaseUrl("https://api.telegram.org");
        client = new TelegramApiClient(props);
        ReflectionTestUtils.setField(client, "restTemplate", restTemplate);
    }

    @Test
    @DisplayName("sendMessage returns null when token is blank")
    void sendMessage_blankToken_returnsNull() {
        assertThat(client.sendMessage("", "12345", "Hello")).isNull();
        assertThat(client.sendMessage(null, "12345", "Hello")).isNull();
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("sendMessage returns null when chatId is blank")
    void sendMessage_blankChatId_returnsNull() {
        assertThat(client.sendMessage("token", "", "Hello")).isNull();
        assertThat(client.sendMessage("token", null, "Hello")).isNull();
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("sendMessage returns original chatId on success")
    void sendMessage_success_returnsOriginalChatId() {
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"ok\":true}");

        assertThat(client.sendMessage("token", "12345", "Hello")).isEqualTo("12345");
    }

    @Test
    @DisplayName("sendMessage returns null on generic network error")
    void sendMessage_networkError_returnsNull() {
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenThrow(new RuntimeException("network timeout"));

        assertThat(client.sendMessage("token", "12345", "Hello")).isNull();
    }

    @Test
    @DisplayName("sendMessage retries with migrated ID on supergroup migration and returns new id")
    void sendMessage_supergroupMigration_retriesAndReturnsMigratedId() {
        String errorBody = """
                {"ok":false,"error_code":400,
                 "description":"Bad Request: group chat was upgraded to a supergroup chat",
                 "parameters":{"migrate_to_chat_id":-1004474684836}}
                """;
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY,
                        errorBody.getBytes(), StandardCharsets.UTF_8))
                .thenReturn("{\"ok\":true}");

        String result = client.sendMessage("token", "-4772311755", "Alert");

        assertThat(result).isEqualTo("-1004474684836");
        verify(restTemplate, times(2)).postForObject(anyString(), any(), eq(String.class));
    }

    @Test
    @DisplayName("sendMessage returns null when retry after migration also fails")
    void sendMessage_migrationRetryFails_returnsNull() {
        String errorBody = """
                {"ok":false,"error_code":400,
                 "description":"Bad Request: group chat was upgraded to a supergroup chat",
                 "parameters":{"migrate_to_chat_id":-1004474684836}}
                """;
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY,
                        errorBody.getBytes(), StandardCharsets.UTF_8))
                .thenThrow(new RuntimeException("still failing"));

        assertThat(client.sendMessage("token", "-4772311755", "Alert")).isNull();
    }

    @Test
    @DisplayName("sendMessage returns null for 400 without migrate_to_chat_id")
    void sendMessage_badRequestNoMigration_returnsNull() {
        String errorBody = "{\"ok\":false,\"error_code\":400,\"description\":\"Bad Request: chat not found\"}";
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY,
                        errorBody.getBytes(), StandardCharsets.UTF_8));

        assertThat(client.sendMessage("token", "99999", "Hello")).isNull();
        verify(restTemplate, times(1)).postForObject(anyString(), any(), eq(String.class));
    }
}

package com.trading.broker;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "zerodha")
public class ZerodhaProperties {

    private String apiKey;
    private String apiSecret;
    private String baseUrl = "https://api.kite.trade";
    private String loginBaseUrl = "https://kite.zerodha.com/connect/login?v=3&api_key=";
    private String frontendUrl = "http://localhost:5173";
    private int connectTimeoutMs = 10_000;
    private int readTimeoutMs = 30_000;
    private String orderBaseUrl;

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getApiSecret() { return apiSecret; }
    public void setApiSecret(String apiSecret) { this.apiSecret = apiSecret; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getLoginBaseUrl() { return loginBaseUrl; }
    public void setLoginBaseUrl(String loginBaseUrl) { this.loginBaseUrl = loginBaseUrl; }

    public String getFrontendUrl() { return frontendUrl; }
    public void setFrontendUrl(String frontendUrl) { this.frontendUrl = frontendUrl; }

    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }

    public String getOrderBaseUrl() { return orderBaseUrl; }
    public void setOrderBaseUrl(String orderBaseUrl) { this.orderBaseUrl = orderBaseUrl; }

}

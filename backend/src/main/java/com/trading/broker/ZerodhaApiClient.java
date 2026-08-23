package com.trading.broker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Supplier;

/**
 * Low-level Zerodha Kite Connect REST client.
 * One instance per user — holds that user's API key + access token.
 *
 * <p>Applies:
 * <ul>
 *   <li>Rate limiting: 10 req/s (Guava RateLimiter)</li>
 *   <li>Retry: up to 3 attempts with exponential backoff for {@link BrokerNetworkException}</li>
 *   <li>Exception mapping: Zerodha error_type → typed {@link BrokerException} subclass</li>
 * </ul>
 */
@Slf4j
public class ZerodhaApiClient {

    private static final String KITE_VERSION = "3";
    private static final int MAX_RETRIES = 3;
    private static final long BASE_BACKOFF_MS = 1_000;

    private final String apiKey;
    private final String accessToken;
    private final String baseUrl;
    private final String orderBaseUrl;
    private final RestTemplate restTemplate;
    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    ZerodhaApiClient(String apiKey, String accessToken, String baseUrl,
                     int connectTimeoutMs, int readTimeoutMs) {
        this(apiKey, accessToken, baseUrl, null, connectTimeoutMs, readTimeoutMs);
    }

    ZerodhaApiClient(String apiKey, String accessToken, String baseUrl, String orderBaseUrl,
                     int connectTimeoutMs, int readTimeoutMs) {
        this.apiKey = apiKey;
        this.accessToken = accessToken;
        this.baseUrl = baseUrl;
        this.orderBaseUrl = (orderBaseUrl != null && !orderBaseUrl.isBlank()) ? orderBaseUrl : baseUrl;
        this.rateLimiter = RateLimiter.create(10.0); // 10 req/s per Zerodha limits
        this.restTemplate = buildRestTemplate(connectTimeoutMs, readTimeoutMs);
    }

    // ── Orders ──────────────────────────────────────────────────────────────

    public String placeLimitOrder(String symbol, int quantity, BigDecimal price, String tag) {
        return executeWithRetry(() -> {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("exchange", "NSE");
            form.add("tradingsymbol", symbol);
            form.add("transaction_type", "BUY");
            form.add("quantity", String.valueOf(quantity));
            form.add("price", price.toPlainString());
            form.add("order_type", "LIMIT");
            form.add("product", "CNC");
            form.add("validity", "DAY");
            form.add("tag", tag);

            JsonNode data = postForm(orderBaseUrl, "/orders/regular", form);
            return data.path("order_id").asText();
        });
    }

    public String placeMarketSellOrder(String symbol, int quantity, String tag) {
        return executeWithRetry(() -> {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("exchange", "NSE");
            form.add("tradingsymbol", symbol);
            form.add("transaction_type", "SELL");
            form.add("quantity", String.valueOf(quantity));
            form.add("order_type", "MARKET");
            form.add("product", "CNC");
            form.add("validity", "DAY");
            form.add("tag", tag);

            JsonNode data = postForm("/orders/regular", form);
            return data.path("order_id").asText();
        });
    }

    public String placeGttOcoOrder(String symbol, int quantity,
                                   BigDecimal stopLoss, BigDecimal target,
                                   BigDecimal lastPrice, String tag) {
        return executeWithRetry(() -> {
            Map<String, Object> condition = Map.of(
                    "exchange", "NSE",
                    "tradingsymbol", symbol,
                    "last_price", lastPrice.doubleValue(),
                    "trigger_values", List.of(stopLoss.doubleValue(), target.doubleValue())
            );
            List<Map<String, Object>> orders = List.of(
                    Map.of("exchange", "NSE", "tradingsymbol", symbol,
                            "transaction_type", "SELL", "quantity", quantity,
                            "order_type", "SLM", "product", "CNC", "price", 0),
                    Map.of("exchange", "NSE", "tradingsymbol", symbol,
                            "transaction_type", "SELL", "quantity", quantity,
                            "order_type", "LIMIT", "product", "CNC",
                            "price", target.doubleValue())
            );
            Map<String, Object> body = Map.of(
                    "type", "two-leg",
                    "condition", condition,
                    "orders", orders
            );

            JsonNode data = postJson("/gtt/triggers", body);
            return data.path("trigger_id").asText();
        });
    }

    public void cancelOrder(String orderId) {
        executeWithRetry(() -> {
            delete("/orders/regular/" + orderId);
            return null;
        });
    }

    public void cancelGttOrder(String gttId) {
        executeWithRetry(() -> {
            delete("/gtt/triggers/" + gttId);
            return null;
        });
    }

    // ── Order detail ─────────────────────────────────────────────────────────

    public BrokerOrderDetail getOrderDetail(String orderId) {
        return executeWithRetry(() -> {
            JsonNode data = get("/orders/" + orderId);
            if (data.isArray()) {
                for (JsonNode order : data) {
                    if (orderId.equals(order.path("order_id").asText())) {
                        BrokerOrderStatus status = mapOrderStatus(order.path("status").asText());
                        int filled = order.path("filled_quantity").asInt(0);
                        BigDecimal avgPrice = new BigDecimal(
                                order.path("average_price").asText("0"));
                        return new BrokerOrderDetail(status, filled, avgPrice);
                    }
                }
            }
            return new BrokerOrderDetail(BrokerOrderStatus.UNKNOWN, 0, BigDecimal.ZERO);
        });
    }

    // ── GTT status ───────────────────────────────────────────────────────────

    public GttStatusResult getGttStatus(String gttId) {
        return executeWithRetry(() -> {
            JsonNode data = get("/gtt/triggers/" + gttId);
            String status = data.path("status").asText("");
            if ("triggered".equalsIgnoreCase(status)) {
                // orders[0] is the leg that fired
                JsonNode orders = data.path("orders");
                BigDecimal fillPrice = BigDecimal.ZERO;
                if (orders.isArray() && orders.size() > 0) {
                    fillPrice = new BigDecimal(
                            orders.get(0).path("price").asText("0"));
                }
                return new GttStatusResult(true, fillPrice);
            }
            return GttStatusResult.active();
        });
    }

    // ── Portfolio ────────────────────────────────────────────────────────────

    public List<Holding> getHoldings() {
        return executeWithRetry(() -> {
            JsonNode data = get("/portfolio/holdings");
            List<Holding> result = new ArrayList<>();
            if (data.isArray()) {
                for (JsonNode h : data) {
                    result.add(new Holding(
                            h.path("tradingsymbol").asText(),
                            h.path("quantity").asInt(),
                            new BigDecimal(h.path("average_price").asText("0")),
                            new BigDecimal(h.path("last_price").asText("0"))
                    ));
                }
            }
            return result;
        });
    }

    public BigDecimal getAvailableMargin() {
        return executeWithRetry(() -> {
            JsonNode data = get("/user/margins/equity");
            return new BigDecimal(data.path("available").path("cash").asText("0"));
        });
    }

    // ── Market data ──────────────────────────────────────────────────────────

    public Map<String, BigDecimal> getQuotes(List<String> symbols) {
        return executeWithRetry(() -> {
            // Build query: ?i=NSE:RELIANCE&i=NSE:TCS
            StringBuilder url = new StringBuilder(baseUrl + "/quote");
            for (int i = 0; i < symbols.size(); i++) {
                url.append(i == 0 ? "?" : "&").append("i=NSE:").append(symbols.get(i));
            }

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    url.toString(), HttpMethod.GET,
                    new HttpEntity<>(authHeaders()), JsonNode.class);

            JsonNode data = parseResponse(response);
            Map<String, BigDecimal> result = new LinkedHashMap<>();
            data.fields().forEachRemaining(entry -> {
                // key is "NSE:RELIANCE" → strip "NSE:" prefix
                String symbol = entry.getKey().contains(":")
                        ? entry.getKey().substring(entry.getKey().indexOf(':') + 1)
                        : entry.getKey();
                result.put(symbol,
                        new BigDecimal(entry.getValue().path("last_price").asText("0")));
            });
            return result;
        });
    }

    // ── Token refresh ────────────────────────────────────────────────────────

    public String refreshAccessToken(String apiKey, String apiSecret, String requestToken) {
        String checksum = sha256(apiKey + requestToken + apiSecret);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("api_key", apiKey);
        form.add("request_token", requestToken);
        form.add("checksum", checksum);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("X-Kite-Version", KITE_VERSION);

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl + "/session/token", HttpMethod.POST,
                new HttpEntity<>(form, headers), JsonNode.class);

        JsonNode data = parseResponse(response);
        return data.path("access_token").asText();
    }

    // ── HTTP helpers ─────────────────────────────────────────────────────────

    private JsonNode get(String path) {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl + path, HttpMethod.GET,
                new HttpEntity<>(authHeaders()), JsonNode.class);
        return parseResponse(response);
    }

    private JsonNode postForm(String path, MultiValueMap<String, String> form) {
        return postForm(baseUrl, path, form);
    }

    private JsonNode postForm(String base, String path, MultiValueMap<String, String> form) {
        HttpHeaders headers = authHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                base + path, HttpMethod.POST,
                new HttpEntity<>(form, headers), JsonNode.class);
        return parseResponse(response);
    }

    private JsonNode postJson(String path, Object body) {
        HttpHeaders headers = authHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl + path, HttpMethod.POST,
                new HttpEntity<>(body, headers), JsonNode.class);
        return parseResponse(response);
    }

    private void delete(String path) {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl + path, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders()), JsonNode.class);
        parseResponse(response);
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "token " + apiKey + ":" + accessToken);
        headers.set("X-Kite-Version", KITE_VERSION);
        return headers;
    }

    private JsonNode parseResponse(ResponseEntity<JsonNode> response) {
        JsonNode body = response.getBody();
        if (body == null) {
            throw new BrokerNetworkException("Empty response from Zerodha API");
        }
        String status = body.path("status").asText();
        if ("error".equals(status)) {
            String errorType = body.path("error_type").asText("GeneralException");
            String message   = body.path("message").asText("Unknown error");
            throw mapZerodhaError(errorType, message);
        }
        return body.path("data");
    }

    private BrokerException mapZerodhaError(String errorType, String message) {
        return switch (errorType) {
            case "TokenException", "PermissionException" -> new BrokerTokenException(message);
            case "OrderException", "InputException"      -> new BrokerOrderException(message);
            case "NetworkException" -> isPermanentNetworkError(message)
                    // "No static IP set for the app" and similar permanent config errors must not
                    // be retried — treat them as order-level (non-retryable) failures.
                    ? new BrokerOrderException("Zerodha config error [" + errorType + "]: " + message)
                    : new BrokerNetworkException("Zerodha [" + errorType + "]: " + message);
            default -> new BrokerNetworkException("Zerodha [" + errorType + "]: " + message);
        };
    }

    private boolean isPermanentNetworkError(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase();
        return lower.contains("no static ip") || lower.contains("static ip not set");
    }

    // ── Retry ────────────────────────────────────────────────────────────────

    private <T> T executeWithRetry(Supplier<T> action) {
        int attempt = 0;
        while (true) {
            try {
                rateLimiter.acquire();
                return action.get();
            } catch (BrokerNetworkException | ResourceAccessException e) {
                attempt++;
                if (attempt >= MAX_RETRIES) {
                    throw (e instanceof BrokerNetworkException be) ? be
                            : new BrokerNetworkException("Network error after retries", e);
                }
                long backoffMs = BASE_BACKOFF_MS * (1L << (attempt - 1)); // 1s, 2s
                log.warn("Zerodha API attempt {}/{} failed, retrying in {}ms: {}",
                        attempt, MAX_RETRIES, backoffMs, e.getMessage());
                sleep(backoffMs);
            }
            // BrokerTokenException and BrokerOrderException propagate immediately
        }
    }

    // ── Utilities ────────────────────────────────────────────────────────────

    private BrokerOrderStatus mapOrderStatus(String status) {
        return switch (status.toUpperCase()) {
            case "COMPLETE"  -> BrokerOrderStatus.COMPLETE;
            case "CANCELLED" -> BrokerOrderStatus.CANCELLED;
            case "REJECTED"  -> BrokerOrderStatus.REJECTED;
            case "OPEN", "TRIGGER PENDING", "OPEN PENDING",
                 "VALIDATION PENDING", "PUT ORDER REQ RECEIVED",
                 "MODIFY VALIDATION PENDING", "MODIFY PENDING",
                 "AFTER MARKET ORDER REQ RECEIVED"
                              -> BrokerOrderStatus.PENDING;
            default          -> BrokerOrderStatus.UNKNOWN;
        };
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new BrokerNetworkException("Interrupted during retry backoff", ie);
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 failed", e);
        }
    }

    private RestTemplate buildRestTemplate(int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        RestTemplate rt = new RestTemplate(factory);
        // Suppress default error handler — we parse errors from JSON body ourselves
        rt.setErrorHandler(new org.springframework.web.client.DefaultResponseErrorHandler() {
            @Override public boolean hasError(org.springframework.http.client.ClientHttpResponse r) {
                return false;
            }
        });
        return rt;
    }
}

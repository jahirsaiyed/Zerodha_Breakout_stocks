package com.trading.market;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches last-traded prices for NSE equity symbols from Google Finance.
 *
 * <p>Uses the public {@code https://www.google.com/finance/quote/SYMBOL:NSE} page and
 * extracts the last-traded price from the {@code AF_initDataCallback} JSON embedded in the
 * server-rendered HTML. All symbols are fetched in parallel using virtual threads; individual
 * failures are logged at DEBUG and silently skipped so a single unavailable symbol never blocks
 * the rest.
 */
@Slf4j
@Service
public class GoogleFinancePriceService {

    private static final String QUOTE_URL = "https://www.google.com/finance/quote/%s:NSE";
    // Google Finance is a JS SPA; price is embedded in AF_initDataCallback script blocks as
    // [...,"INR",[<price>,<change>,<changePct>,2,2,...],...] — the trailing ,2,2 distinguishes it.
    private static final Pattern PRICE_PATTERN =
            Pattern.compile("\\[([0-9]+\\.?[0-9]*),(?:-?[0-9]+\\.?[0-9]*),(?:-?[0-9]+\\.?[0-9]*),2,2");
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    private static final Executor VIRTUAL_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final RestTemplate restTemplate;

    public GoogleFinancePriceService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(6_000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * Returns a map of symbol → last price for every symbol that could be fetched.
     * Symbols where the fetch or parse fails are absent from the result (not null-valued).
     */
    public Map<String, BigDecimal> getPrices(List<String> symbols) {
        if (symbols.isEmpty()) return Map.of();

        List<CompletableFuture<Map.Entry<String, BigDecimal>>> futures = symbols.stream()
                .map(symbol -> CompletableFuture.supplyAsync(
                        () -> Map.entry(symbol, fetchOne(symbol)), VIRTUAL_EXECUTOR))
                .toList();

        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (CompletableFuture<Map.Entry<String, BigDecimal>> future : futures) {
            try {
                Map.Entry<String, BigDecimal> entry = future.get();
                if (entry.getValue() != null) {
                    result.put(entry.getKey(), entry.getValue());
                }
            } catch (Exception e) {
                log.debug("Price fetch interrupted: {}", e.getMessage());
            }
        }
        return result;
    }

    private BigDecimal fetchOne(String symbol) {
        try {
            String url = String.format(QUOTE_URL, symbol);
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            if (response.getBody() != null) {
                return parsePrice(response.getBody());
            }
        } catch (Exception e) {
            log.debug("Could not fetch Google Finance price for {}: {}", symbol, e.getMessage());
        }
        return null;
    }

    // package-private for unit testing
    BigDecimal parsePrice(String html) {
        Matcher m = PRICE_PATTERN.matcher(html);
        if (m.find()) {
            try {
                return new BigDecimal(m.group(1));
            } catch (NumberFormatException e) {
                log.debug("Unparseable price value: '{}'", m.group(1));
            }
        }
        return null;
    }
}

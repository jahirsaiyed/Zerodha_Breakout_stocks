package com.trading.signals;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.StringReader;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches the NSE instrument list from Zerodha's public instruments endpoint.
 *
 * <p>Downloaded at startup and refreshed daily at 8:00 AM IST on trading days.
 * Provides symbol validation for manual signal entry. If the download fails
 * (e.g. Zerodha is unreachable at startup), validation is skipped (fail-open)
 * so users can still add signals.
 */
@Slf4j
@Service
public class InstrumentCacheService {

    private static final String INSTRUMENTS_URL = "https://api.kite.trade/instruments/NSE";

    /** Tradingsymbol values from the NSE instrument list. */
    private final Set<String> nseSymbols = ConcurrentHashMap.newKeySet();

    private volatile Instant lastRefreshed = null;
    private final RestTemplate restTemplate;

    public InstrumentCacheService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        this.restTemplate = new RestTemplate(factory);
    }

    @PostConstruct
    public void init() {
        refresh();
    }

    @Scheduled(cron = "0 0 8 * * MON-FRI", zone = "Asia/Kolkata")
    public void refresh() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(INSTRUMENTS_URL, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Set<String> loaded = parseCsv(response.getBody());
                nseSymbols.clear();
                nseSymbols.addAll(loaded);
                lastRefreshed = Instant.now();
                log.info("NSE instrument cache refreshed — {} symbols loaded", nseSymbols.size());
            } else {
                log.warn("NSE instrument download returned status {}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.warn("NSE instrument cache refresh failed (validation will be skipped): {}", e.getMessage());
        }
    }

    /**
     * Returns true if the symbol is a valid NSE instrument.
     * Always returns true when the cache is empty (fail-open).
     */
    public boolean isValidNseSymbol(String symbol) {
        if (nseSymbols.isEmpty()) return true; // cache not loaded yet — allow any symbol
        return nseSymbols.contains(symbol.toUpperCase());
    }

    public Instant getLastRefreshed() {
        return lastRefreshed;
    }

    public int getCacheSize() {
        return nseSymbols.size();
    }

    // ── CSV parsing ───────────────────────────────────────────────────────────

    /**
     * Parses the Zerodha instruments CSV.
     *
     * <p>CSV format (header row):
     * {@code instrument_token,exchange_token,tradingsymbol,name,last_price,expiry,
     * strike,tick_size,lot_size,instrument_type,segment,exchange}
     *
     * <p>We only extract {@code tradingsymbol} (column index 2) for EQ instruments
     * ({@code instrument_type == "EQ"}).
     */
    private Set<String> parseCsv(String csv) {
        Set<String> symbols = ConcurrentHashMap.newKeySet();
        try (BufferedReader reader = new BufferedReader(new StringReader(csv))) {
            String line = reader.readLine(); // skip header
            while ((line = reader.readLine()) != null) {
                String[] cols = line.split(",", -1);
                if (cols.length >= 11) {
                    String tradingSymbol   = cols[2].trim().toUpperCase();
                    String instrumentType  = cols[9].trim();
                    if ("EQ".equalsIgnoreCase(instrumentType) && !tradingSymbol.isEmpty()) {
                        symbols.add(tradingSymbol);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error parsing NSE instruments CSV: {}", e.getMessage());
        }
        return symbols;
    }
}

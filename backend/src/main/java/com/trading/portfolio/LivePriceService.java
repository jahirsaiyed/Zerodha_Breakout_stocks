package com.trading.portfolio;

import com.trading.broker.BrokerAdapter;
import com.trading.broker.BrokerAdapterFactory;
import com.trading.broker.BrokerTokenException;
import com.trading.users.UserConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Fetches last-traded prices for a user's positions from their connected broker.
 *
 * <p>Sources prices from holdings + day positions rather than {@link BrokerAdapter#getQuotes},
 * so a symbol shows a price as soon as it is held — no separate quote subscription needed.
 * Falls back to an empty map (never throws) when the user has no valid broker connection
 * or the broker call fails, so callers can treat missing prices as "unavailable" rather
 * than an error.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LivePriceService {

    private final BrokerAdapterFactory brokerAdapterFactory;

    public Map<String, BigDecimal> getLivePrices(UserConfig config, Set<String> symbols) {
        if (symbols.isEmpty()) return Map.of();

        try {
            BrokerAdapter adapter = brokerAdapterFactory.forUser(config);
            Map<String, BigDecimal> prices = new HashMap<>();
            adapter.getHoldings().forEach(h -> {
                if (symbols.contains(h.symbol())) prices.put(h.symbol(), h.lastPrice());
            });
            adapter.getDayPositions().forEach(p -> {
                if (symbols.contains(p.symbol())) prices.put(p.symbol(), p.lastPrice());
            });
            return prices;
        } catch (BrokerTokenException e) {
            log.debug("Live prices unavailable for user config id={} (token/permission issue): {}",
                    config.getId(), e.getMessage());
            return Map.of();
        } catch (Exception e) {
            log.debug("Live prices unavailable for user config id={} ({}): {}",
                    config.getId(), e.getClass().getSimpleName(), e.getMessage());
            return Map.of();
        }
    }
}

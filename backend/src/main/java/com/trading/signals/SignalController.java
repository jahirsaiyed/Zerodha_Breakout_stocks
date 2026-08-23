package com.trading.signals;

import com.trading.common.ApiResponse;
import com.trading.market.GoogleFinancePriceService;
import com.trading.portfolio.ScoredSignal;
import com.trading.portfolio.SignalScoringService;
import com.trading.signals.dto.CreateSignalRequest;
import com.trading.signals.dto.SignalQuoteResponse;
import com.trading.signals.dto.SignalResponse;
import com.trading.signals.dto.SyncLogResponse;
import com.trading.signals.dto.UpdateSignalRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/signals")
@RequiredArgsConstructor
public class SignalController {

    private final SignalService signalService;
    private final SheetSyncService sheetSyncService;
    private final GoogleFinancePriceService googleFinancePriceService;
    private final SignalScoringService scoringService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SignalResponse>>> list(
            @RequestParam(required = false) SignalStatus status) {
        return ResponseEntity.ok(ApiResponse.success(signalService.list(status)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SignalResponse>> create(
            @Valid @RequestBody CreateSignalRequest request) {
        SignalResponse created = signalService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SignalResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSignalRequest request) {
        return ResponseEntity.ok(ApiResponse.success(signalService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<SignalResponse>> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(signalService.cancel(id)));
    }

    @GetMapping("/quotes")
    public ResponseEntity<ApiResponse<List<SignalQuoteResponse>>> getQuotes() {
        List<Signal> active = signalService.findActiveSignals();
        if (active.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success(List.of()));
        }

        List<String> symbols = active.stream().map(Signal::getSymbol).distinct().toList();
        Map<String, BigDecimal> prices = googleFinancePriceService.getPrices(symbols);

        List<ScoredSignal> ranked = scoringService.rank(active, prices);
        Map<Long, Integer> rankMap = new HashMap<>();
        for (int i = 0; i < ranked.size(); i++) {
            rankMap.put(ranked.get(i).signal().getId(), i + 1);
        }

        List<SignalQuoteResponse> result = active.stream().map(sig -> {
            BigDecimal ltp = prices.get(sig.getSymbol());
            BigDecimal diff = null;
            if (ltp != null) {
                diff = ltp.subtract(sig.getEntryPrice())
                        .divide(sig.getEntryPrice(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP);
            }
            return new SignalQuoteResponse(sig.getId(), rankMap.get(sig.getId()), ltp, diff);
        }).toList();

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/sync-log")
    public ResponseEntity<ApiResponse<List<SyncLogResponse>>> syncLog() {
        return ResponseEntity.ok(ApiResponse.success(signalService.getSyncLog()));
    }

    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<SyncResult>> syncNow() {
        SyncResult result = sheetSyncService.sync();
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}

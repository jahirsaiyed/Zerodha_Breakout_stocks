package com.trading.signals;

import com.trading.common.ApiResponse;
import com.trading.signals.dto.CreateSignalRequest;
import com.trading.signals.dto.SignalResponse;
import com.trading.signals.dto.SyncLogResponse;
import com.trading.signals.dto.UpdateSignalRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/signals")
@RequiredArgsConstructor
public class SignalController {

    private final SignalService signalService;
    private final SheetSyncService sheetSyncService;

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

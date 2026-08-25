package com.trading.signals;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleSheetsService {

    private static final String APPLICATION_NAME = "Trading Portfolio System";
    private static final GsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    private final SheetsProperties props;

    /**
     * Fetches all data rows from the configured Google Sheet.
     * Returns an empty list if the integration is disabled or credentials are missing.
     * Row numbers are 1-indexed; data starts at row 2 (row 1 is the header).
     */
    public List<SheetRow> fetchRows() {
        if (!props.isEnabled()) {
            log.debug("Google Sheets sync disabled — skipping fetch");
            return Collections.emptyList();
        }
        if (props.getCredentialsPath().isBlank() || props.getSpreadsheetId().isBlank()) {
            log.warn("Google Sheets credentials-path or spreadsheet-id not configured — skipping fetch");
            return Collections.emptyList();
        }

        try {
            Sheets sheetsClient = buildClient();
            ValueRange response = sheetsClient.spreadsheets().values()
                    .get(props.getSpreadsheetId(), props.getRange())
                    .execute();

            List<List<Object>> rawRows = response.getValues();
            if (rawRows == null || rawRows.isEmpty()) {
                log.info("Google Sheet is empty or no data in range {}", props.getRange());
                return Collections.emptyList();
            }

            return parseRows(rawRows);
        } catch (Exception e) {
            log.error("Failed to fetch Google Sheet data: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    // Column indices in "NSE Stock break out Ideas" sheet (0-based, range A:R)
    private static final int COL_SYMBOL        = 1;  // B
    private static final int COL_ENTRY         = 10; // K
    private static final int COL_SL            = 11; // L
    private static final int COL_TARGET        = 12; // M
    private static final int COL_CLOSING_BASIS = 13; // N
    private static final int COL_NOTES         = 16; // Q

    private List<SheetRow> parseRows(List<List<Object>> rawRows) {
        // Range starts at A2, so row 2 in the sheet = index 0 in rawRows
        int sheetRowNumber = 2;
        List<SheetRow> result = new ArrayList<>();

        for (List<Object> row : rawRows) {
            try {
                if (row.size() <= COL_TARGET) {
                    log.debug("Skipping sheet row {} — not enough columns ({})", sheetRowNumber, row.size());
                    sheetRowNumber++;
                    continue;
                }

                String symbol     = cell(row, COL_SYMBOL).toUpperCase();
                BigDecimal entry  = decimal(row, COL_ENTRY);
                BigDecimal sl     = decimal(row, COL_SL);
                BigDecimal target = decimal(row, COL_TARGET);
                StopLossBasis basis = parseClosingBasis(row);
                String notes      = row.size() > COL_NOTES ? cell(row, COL_NOTES) : null;
                String sourceRef  = sheetRowNumber + ":" + symbol;

                SheetRow sheetRow = new SheetRow(sourceRef, symbol, entry, sl, target, basis, notes);
                if (!sheetRow.isValid()) {
                    log.warn("Skipping invalid row {}: entry={} sl={} target={}", sheetRowNumber, entry, sl, target);
                    sheetRowNumber++;
                    continue;
                }
                result.add(sheetRow);

            } catch (Exception e) {
                log.warn("Skipping unparseable sheet row {}: {}", sheetRowNumber, e.getMessage());
            }
            sheetRowNumber++;
        }
        return result;
    }

    private Sheets buildClient() throws Exception {
        GoogleCredentials credentials;
        try (FileInputStream in = new FileInputStream(props.getCredentialsPath())) {
            credentials = GoogleCredentials.fromStream(in)
                    .createScoped(SheetsScopes.SPREADSHEETS_READONLY);
        }
        return new Sheets.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JSON_FACTORY,
                new HttpCredentialsAdapter(credentials))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    private StopLossBasis parseClosingBasis(List<Object> row) {
        if (row.size() <= COL_CLOSING_BASIS) return StopLossBasis.DAILY;
        String raw = cell(row, COL_CLOSING_BASIS).toUpperCase();
        try {
            return StopLossBasis.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return StopLossBasis.DAILY;
        }
    }

    private String cell(List<Object> row, int idx) {
        return row.get(idx).toString().trim();
    }

    private BigDecimal decimal(List<Object> row, int idx) {
        return new BigDecimal(cell(row, idx));
    }
}

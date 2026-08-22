package com.trading.signals;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "sheets")
public class SheetsProperties {

    private boolean enabled = false;
    private String spreadsheetId = "";
    private String credentialsPath = "";
    private String range = "Sheet1!A2:E";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getSpreadsheetId() { return spreadsheetId; }
    public void setSpreadsheetId(String spreadsheetId) { this.spreadsheetId = spreadsheetId; }

    public String getCredentialsPath() { return credentialsPath; }
    public void setCredentialsPath(String credentialsPath) { this.credentialsPath = credentialsPath; }

    public String getRange() { return range; }
    public void setRange(String range) { this.range = range; }
}

package com.trading.signals;

public record SyncResult(int added, int modified, int removed, int skipped) {
    public static SyncResult empty() { return new SyncResult(0, 0, 0, 0); }
}

package com.trading.broker;

/** Invalid or expired Zerodha access token — user must re-authenticate. */
public class BrokerTokenException extends BrokerException {
    public BrokerTokenException(String message) { super(message); }
}

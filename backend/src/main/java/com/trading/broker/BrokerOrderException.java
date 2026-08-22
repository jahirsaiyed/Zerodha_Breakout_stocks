package com.trading.broker;

/** Order rejected or invalid input — not retryable. */
public class BrokerOrderException extends BrokerException {
    public BrokerOrderException(String message) { super(message); }
}

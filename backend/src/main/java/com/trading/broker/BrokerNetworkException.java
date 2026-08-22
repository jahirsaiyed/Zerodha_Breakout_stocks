package com.trading.broker;

/** Transient network / timeout error — eligible for retry. */
public class BrokerNetworkException extends BrokerException {
    public BrokerNetworkException(String message) { super(message); }
    public BrokerNetworkException(String message, Throwable cause) { super(message, cause); }
}

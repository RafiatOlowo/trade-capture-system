package com.technicalchallenge.exception;

import java.util.List;

public class TradeValidationException extends RuntimeException {

    public TradeValidationException(String message) {
        super(message);
    }

    public TradeValidationException(List<String> errors) {
        super("Trade validation failed: " + String.join("; ", errors));
    }
}
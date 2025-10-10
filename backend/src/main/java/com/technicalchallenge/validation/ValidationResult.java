package com.technicalchallenge.validation;

import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates the result of a validation process, holding the overall status
 * and a list of specific error messages if validation fails.
 */
public class ValidationResult {
    private boolean successful;
    private final List<String> errors;

    public ValidationResult() {
        this.successful = true;
        this.errors = new ArrayList<>();
    }

    /**
     * Adds an error message and sets the result to unsuccessful.
     * @param error The error message to add.
     */
    public void addError(String error) {
        this.successful = false;
        this.errors.add(error);
    }

    /**
     * Adds a list of error messages and sets the result to unsuccessful if the list is not empty.
     * @param newErrors The list of error messages to add.
     */
    public void addErrors(List<String> newErrors) {
        if (newErrors != null && !newErrors.isEmpty()) {
            this.successful = false;
            this.errors.addAll(newErrors);
        }
    }

    // Getters
    public boolean isSuccessful() {
        return successful;
    }

    public List<String> getErrors() {
        return errors;
    }

    /**
     * Helper method to format errors into a single string.
     */
    public String toErrorMessage() {
        if (successful) {
            return "Validation successful.";
        }
        return String.join("; ", errors);
    }
}
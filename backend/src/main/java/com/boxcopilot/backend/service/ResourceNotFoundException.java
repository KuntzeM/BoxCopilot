package com.boxcopilot.backend.service;

/**
 * Custom exception for resource not found scenarios.
 * Used to indicate that a requested resource does not exist.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

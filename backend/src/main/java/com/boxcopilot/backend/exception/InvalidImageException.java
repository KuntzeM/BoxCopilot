package com.boxcopilot.backend.exception;

/** An uploaded image was rejected (empty, wrong type, unreadable, too large): the client's fault, answered with 400. */
public class InvalidImageException extends FileStorageException {

    public InvalidImageException(String message) {
        super(message);
    }
}

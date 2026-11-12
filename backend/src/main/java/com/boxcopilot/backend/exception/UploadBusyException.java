package com.boxcopilot.backend.exception;

/** All image-processing slots are taken: a temporary condition, answered with 503 + Retry-After. */
public class UploadBusyException extends FileStorageException {

    public UploadBusyException(String message) {
        super(message);
    }
}

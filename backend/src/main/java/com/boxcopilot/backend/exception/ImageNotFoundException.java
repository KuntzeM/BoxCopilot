package com.boxcopilot.backend.exception;

/** The image file does not exist (as opposed to a storage failure). */
public class ImageNotFoundException extends FileStorageException {

    public ImageNotFoundException(String message) {
        super(message);
    }
}

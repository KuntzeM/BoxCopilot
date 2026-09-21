package com.boxcopilot.backend.mcp;

import java.util.Locale;

/** Variant of an item image that can be requested through MCP. */
public enum ImageSize {
    THUMBNAIL,
    LARGE;

    /**
     * @throws IllegalArgumentException for values other than {@code thumbnail} or {@code large} (any case)
     */
    public static ImageSize fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Image size must not be null");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown image size", e);
        }
    }
}

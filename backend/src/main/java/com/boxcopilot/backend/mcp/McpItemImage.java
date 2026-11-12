package com.boxcopilot.backend.mcp;

import java.util.Arrays;
import java.util.Objects;

/** JPEG bytes of an item image together with the item name and the number of its box. */
public record McpItemImage(byte[] content, String name, int boxNumber) {

    public McpItemImage {
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof McpItemImage that
                && boxNumber == that.boxNumber
                && Objects.equals(name, that.name)
                && Arrays.equals(content, that.content);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(name, boxNumber) + Arrays.hashCode(content);
    }

    @Override
    public String toString() {
        return "McpItemImage[name=" + name + ", boxNumber=" + boxNumber + ", bytes=" + content.length + "]";
    }
}

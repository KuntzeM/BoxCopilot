package com.boxcopilot.backend.mcp;

/** Normalises the configured base URLs so that a path can simply be appended to them. */
public final class McpUrls {

    private McpUrls() {
    }

    public static String withoutTrailingSlash(String url) {
        if (url == null || !url.endsWith("/")) {
            return url;
        }
        return url.substring(0, url.length() - 1);
    }
}

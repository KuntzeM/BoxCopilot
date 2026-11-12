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

    /** Lower-cased {@code scheme://host[:port]} with a default port omitted; the path is ignored. */
    public static String origin(String url) {
        java.net.URI uri = java.net.URI.create(url.trim());
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(java.util.Locale.ROOT);
        int port = uri.getPort();
        boolean defaultPort = port == -1 || (scheme.equals("https") && port == 443)
                || (scheme.equals("http") && port == 80);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(java.util.Locale.ROOT);
        return scheme + "://" + host + (defaultPort ? "" : ":" + port);
    }
}

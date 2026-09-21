package com.boxcopilot.backend.mcp.security;

import com.boxcopilot.backend.mcp.McpProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Protected-resource metadata (RFC 9728) for the MCP endpoint. The document is static, computed once from the
 * configured backend URL, and readable without a token so that a client can discover where to authenticate.
 */
@RestController
@ConditionalOnProperty(prefix = "app.mcp", name = "enabled", havingValue = "true")
class McpProtectedResourceMetadataController {

    static final String PATH = "/.well-known/oauth-protected-resource/mcp";

    private final Map<String, Object> metadata;

    McpProtectedResourceMetadataController(McpProperties properties) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("resource", properties.backendUrl() + McpSecurityConfig.MCP_ENDPOINT);
        document.put("authorization_servers", List.of(properties.backendUrl()));
        document.put("scopes_supported", List.of(McpOAuth2Attributes.SCOPE_READ));
        document.put("bearer_methods_supported", List.of("header"));
        this.metadata = Collections.unmodifiableMap(document);
    }

    @GetMapping(path = PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    Map<String, Object> protectedResourceMetadata() {
        return metadata;
    }
}

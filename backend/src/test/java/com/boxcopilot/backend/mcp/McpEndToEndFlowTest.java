package com.boxcopilot.backend.mcp;

import com.boxcopilot.backend.domain.Box;
import com.boxcopilot.backend.domain.Item;
import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.mcp.authserver.McpAuthorizationServerEndpoints;
import com.boxcopilot.backend.mcp.security.McpOAuth2Attributes;
import com.boxcopilot.backend.repository.BoxRepository;
import com.boxcopilot.backend.repository.ItemRepository;
import com.boxcopilot.backend.service.ImageStorageService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The whole feature in one script, as living documentation: a user is granted MCP access, provisions a client
 * in the SPA dialog, walks the authorization-code flow with PKCE through login session, consent page and token
 * endpoint, speaks MCP over the issued token - initialize, tools/list, all four tools against seeded inventory -
 * refreshes the connection and finally revokes it, after which nothing that was issued still works.
 *
 * <p>Every step is covered somewhere on its own; the value here is the sequence. A change that breaks the seam
 * between two steps - a code that the token endpoint no longer accepts, a token the resource server no longer
 * introspects, a revocation that leaves the rotated token alive - passes the focused tests and fails this one.
 * The tool calls therefore assert the seeded data coming back, not merely that the call did not error.
 */
class McpEndToEndFlowTest extends McpIntegrationTestBase {

    private static final String CLIENT_ENDPOINT = "/api/v1/me/mcp-client";
    private static final String CONNECTIONS_ENDPOINT = "/api/v1/me/mcp-connections";
    private static final String CLAUDE_REDIRECT_URI = "https://claude.ai/api/mcp/auth_callback";
    private static final String CODE_VERIFIER = "s12-end-to-end-code-verifier-that-is-long-enough-0123456789";
    private static final Pattern CSRF_INPUT = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

    /** Far outside the range any other test seeds, so the shared database stays predictable for everyone. */
    private static final int CELLAR_BOX_NUMBER = 8120;
    private static final int ATTIC_BOX_NUMBER = 8121;
    private static final String MARKER = "s12flow";

    @Autowired
    private JsonMapper objectMapper;
    @Autowired
    private BoxRepository boxRepository;
    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private ImageStorageService imageStorageService;

    private final List<Long> seededBoxIds = new java.util.ArrayList<>();

    /**
     * The inventory is committed, so it is removed again rather than rolled back. Plain SQL, because loading a
     * box through the repository outside a transaction would have to read its large-object description.
     */
    @AfterEach
    void removeSeededInventory() {
        seededBoxIds.forEach(boxId -> {
            jdbcTemplate.update("DELETE FROM items WHERE box_id = ?", boxId);
            jdbcTemplate.update("DELETE FROM boxes WHERE id = ?", boxId);
        });
        seededBoxIds.clear();
    }

    @Test
    void aUserProvisionsAClientAuthorizesReadsTheInventoryRefreshesAndRevokes() throws Exception {
        // 1 - an enabled user with MCP access, and inventory for the tools to find
        User user = createUser(true, true);
        MockHttpSession session = sessionFor(user);
        Box cellar = seedBox(CELLAR_BOX_NUMBER, "Cellar", "Cellar", MARKER + " winter things");
        Item lamp = seedItem(cellar, MARKER + " reading lamp", true);
        Item blanket = seedItem(cellar, MARKER + " wool blanket", false);
        seedItem(seedBox(ATTIC_BOX_NUMBER, "Attic", "Attic", MARKER + " decorations"), MARKER + " candle", false);

        // 2 - the SPA dialog provisions the personal client
        String clientId = provisionClient(session);
        assertThat(registeredClientRepository.findByClientId(clientId).getClientSettings()
                .<String>getSetting(McpOAuth2Attributes.CLIENT_USER_ID)).isEqualTo(String.valueOf(user.getId()));

        // 3 to 5 - authorize, consent page, consent POST, and the code out of the redirect
        String code = authorizeWithConsent(clientId, session);

        // 6 - the token exchange, with the PKCE verifier
        JsonNode tokens = redeem(code, clientId);
        String accessToken = tokens.get("access_token").asText();
        String refreshToken = tokens.get("refresh_token").asText();
        assertThat(tokens.get("token_type").asText()).isEqualTo("Bearer");
        assertThat(tokens.get("scope").asText()).isEqualTo(McpOAuth2Attributes.SCOPE_READ);
        assertThat(accessToken).as("an opaque reference token, not a JWT").doesNotContain(".");

        // 7 - the MCP handshake
        JsonNode initialize = rpc(accessToken, """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18",
                "capabilities":{},"clientInfo":{"name":"s12-end-to-end","version":"1.0"}}}""");
        assertThat(initialize.at("/result/serverInfo/name").asText()).isNotBlank();
        assertThat(initialize.at("/result/capabilities/tools").isMissingNode()).isFalse();

        // 8 - the catalogue: four read-only tools
        JsonNode tools = rpc(accessToken, toolsListRequest()).at("/result/tools");
        assertThat(tools).hasSize(4);
        assertThat(tools.findValuesAsString("name"))
                .containsExactlyInAnyOrder("search_items", "get_item_image", "search_boxes", "get_box_contents");
        tools.forEach(tool -> assertThat(tool.at("/annotations/readOnlyHint").asBoolean())
                .as("%s is announced as read-only", tool.get("name").asText()).isTrue());

        // 9 - every tool, against the seeded inventory
        JsonNode items = toolPayload(accessToken, "search_items", Map.of("query", MARKER, "limit", 10));
        assertThat(items.at("/items").findValuesAsString("name"))
                .contains(lamp.getName(), blanket.getName()).hasSize(3);
        assertThat(items.at("/items/0").has("boxUuid")).isFalse();
        assertThat(items.toString()).doesNotContain("imageToken", "imageUrl");

        JsonNode boxes = toolPayload(accessToken, "search_boxes", Map.of("query", MARKER, "limit", 10));
        assertThat(boxes.at("/boxes").findValuesAsString("boxNumber"))
                .contains(String.valueOf(CELLAR_BOX_NUMBER), String.valueOf(ATTIC_BOX_NUMBER));

        JsonNode contents = toolPayload(accessToken, "get_box_contents",
                Map.of("boxNumber", CELLAR_BOX_NUMBER));
        assertThat(contents.at("/box/boxNumber").asInt()).isEqualTo(CELLAR_BOX_NUMBER);
        assertThat(contents.at("/items").findValuesAsString("name"))
                .containsExactlyInAnyOrder(lamp.getName(), blanket.getName());

        JsonNode imageCall = toolCall(accessToken, "get_item_image",
                Map.of("itemId", lamp.getId(), "size", "thumbnail"));
        assertThat(imageCall.at("/result/isError").asBoolean()).isFalse();
        assertThat(objectMapper.readTree(imageCall.at("/result/content/0/text").asText()).at("/name").asText())
                .isEqualTo(lamp.getName());
        JsonNode image = imageCall.at("/result/content/1");
        assertThat(image.at("/type").asText()).isEqualTo("image");
        assertThat(image.at("/mimeType").asText()).isEqualTo("image/jpeg");
        assertThat(Base64.getDecoder().decode(image.at("/data").asText()))
                .as("real JPEG bytes").startsWith((byte) 0xFF, (byte) 0xD8);

        // 10 - the refresh rotates both tokens, and the new access token keeps working
        JsonNode refreshed = objectMapper.readTree(tokenEndpoint(Map.of(
                OAuth2ParameterNames.GRANT_TYPE, "refresh_token",
                OAuth2ParameterNames.REFRESH_TOKEN, refreshToken,
                OAuth2ParameterNames.CLIENT_ID, clientId)).getResponse().getContentAsString());
        String rotatedAccessToken = refreshed.get("access_token").asText();
        String rotatedRefreshToken = refreshed.get("refresh_token").asText();
        assertThat(rotatedAccessToken).isNotEqualTo(accessToken);
        assertThat(rotatedRefreshToken).isNotEqualTo(refreshToken);
        assertThat(rpc(rotatedAccessToken, toolsListRequest()).at("/result/tools")).hasSize(4);
        assertThat(tokenStatus(Map.of(OAuth2ParameterNames.GRANT_TYPE, "refresh_token",
                OAuth2ParameterNames.REFRESH_TOKEN, refreshToken,
                OAuth2ParameterNames.CLIENT_ID, clientId)))
                .as("the rotated-away refresh token is dead").isNotEqualTo(200);

        // 11 - the user revokes every connection from the dialog; the client survives, the tokens do not
        mockMvc.perform(delete(CONNECTIONS_ENDPOINT).session(session).with(csrf()))
                .andReturn();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM oauth2_authorization", Long.class)).isZero();
        assertThat(registeredClientRepository.findByClientId(clientId)).isNotNull();

        assertThat(status(mcpRequest(rotatedAccessToken, toolsListRequest()))).isEqualTo(401);
        assertThat(status(mcpRequest(accessToken, toolsListRequest()))).isEqualTo(401);
        assertThat(tokenStatus(Map.of(OAuth2ParameterNames.GRANT_TYPE, "refresh_token",
                OAuth2ParameterNames.REFRESH_TOKEN, rotatedRefreshToken,
                OAuth2ParameterNames.CLIENT_ID, clientId))).isNotEqualTo(200);
    }

    // ---------------------------------------------------------------- inventory

    private Box seedBox(int number, String currentRoom, String targetRoom, String description) {
        Box box = new Box(UUID.randomUUID().toString(), currentRoom, targetRoom, description);
        box.setBoxNumber(number);
        Box saved = boxRepository.save(box);
        seededBoxIds.add(saved.getId());
        return saved;
    }

    private Item seedItem(Box box, String name, boolean withImage) {
        Item item = itemRepository.save(new Item(box, name));
        if (withImage) {
            imageStorageService.saveImage(item.getId(), new MockMultipartFile(
                    "file", "item.jpg", MediaType.IMAGE_JPEG_VALUE, jpegBytes()));
            item.setImagePath(item.getId() + "_thumb.jpg");
            item.setImageToken(UUID.randomUUID().toString());
            item = itemRepository.save(item);
        }
        return item;
    }

    private static byte[] jpegBytes() {
        BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.ORANGE);
        graphics.fillRect(0, 0, 40, 40);
        graphics.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "jpg", bytes);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot build the test image", e);
        }
        return bytes.toByteArray();
    }

    // ---------------------------------------------------------------- the steps

    private String provisionClient(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(post(CLIENT_ENDPOINT).session(session).with(csrf())).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("clientId").asText();
    }

    /** Authorize, consent page and consent POST exactly as a browser walks them; returns the code. */
    private String authorizeWithConsent(String clientId, MockHttpSession session) throws Exception {
        MvcResult authorize = mockMvc.perform(get(authorizeUri(clientId)).session(session)).andReturn();
        assertThat(authorize.getResponse().getStatus()).isEqualTo(302);
        String consentState = queryParameter(location(authorize), OAuth2ParameterNames.STATE);

        MvcResult consentPage = mockMvc.perform(get(McpAuthorizationServerEndpoints.CONSENT)
                .param(OAuth2ParameterNames.STATE, consentState).session(session)).andReturn();
        assertThat(consentPage.getResponse().getStatus()).isEqualTo(200);
        assertThat(consentPage.getResponse().getContentAsString()).contains(McpOAuth2Attributes.SCOPE_READ);

        MvcResult approved = mockMvc.perform(post(McpAuthorizationServerEndpoints.AUTHORIZE)
                        .session(session)
                        .cookie(csrfCookie(consentPage))
                        .param(OAuth2ParameterNames.CLIENT_ID, clientId)
                        .param(OAuth2ParameterNames.STATE, consentState)
                        .param(OAuth2ParameterNames.SCOPE, McpOAuth2Attributes.SCOPE_READ)
                        .param("_csrf", csrfToken(consentPage)))
                .andReturn();
        assertThat(approved.getResponse().getStatus()).isEqualTo(302);
        assertThat(location(approved)).startsWith(CLAUDE_REDIRECT_URI).contains("state=s12-client-state");
        return queryParameter(location(approved), OAuth2ParameterNames.CODE);
    }

    private JsonNode redeem(String code, String clientId) throws Exception {
        MvcResult result = tokenEndpoint(Map.of(
                OAuth2ParameterNames.GRANT_TYPE, "authorization_code",
                OAuth2ParameterNames.CODE, code,
                OAuth2ParameterNames.REDIRECT_URI, CLAUDE_REDIRECT_URI,
                OAuth2ParameterNames.CLIENT_ID, clientId,
                "code_verifier", CODE_VERIFIER));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private MvcResult tokenEndpoint(Map<String, String> parameters) throws Exception {
        MockHttpServletRequestBuilder request = post(McpAuthorizationServerEndpoints.TOKEN)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED);
        parameters.forEach(request::param);
        return mockMvc.perform(request).andReturn();
    }

    private int tokenStatus(Map<String, String> parameters) throws Exception {
        return tokenEndpoint(parameters).getResponse().getStatus();
    }

    // ---------------------------------------------------------------- MCP calls

    private MvcResult mcpRequest(String accessToken, String body) throws Exception {
        return mockMvc.perform(post(McpEndpoints.MCP)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(HttpHeaders.ACCEPT, ACCEPT)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    private JsonNode rpc(String accessToken, String body) throws Exception {
        MvcResult result = mcpRequest(accessToken, body);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode toolCall(String accessToken, String tool, Map<String, Object> arguments) throws Exception {
        return rpc(accessToken, objectMapper.writeValueAsString(Map.of(
                "jsonrpc", "2.0", "id", 42, "method", "tools/call",
                "params", Map.of("name", tool, "arguments", arguments))));
    }

    /** The JSON a tool answers with, unwrapped from the text content of a successful call. */
    private JsonNode toolPayload(String accessToken, String tool, Map<String, Object> arguments) throws Exception {
        JsonNode response = toolCall(accessToken, tool, arguments);
        assertThat(response.at("/result/isError").asBoolean()).as("%s failed: %s", tool, response).isFalse();
        return objectMapper.readTree(response.at("/result/content/0/text").asText());
    }

    // ---------------------------------------------------------------- plumbing

    private static int status(MvcResult result) {
        return result.getResponse().getStatus();
    }

    private static String location(MvcResult result) {
        return result.getResponse().getHeader(HttpHeaders.LOCATION);
    }

    /** A ready-made URI: MockMvc would encode a template a second time and double the percent signs. */
    private static URI authorizeUri(String clientId) {
        return URI.create(McpAuthorizationServerEndpoints.AUTHORIZE
                + "?response_type=code"
                + "&client_id=" + encode(clientId)
                + "&state=s12-client-state"
                + "&redirect_uri=" + encode(CLAUDE_REDIRECT_URI)
                + "&scope=" + encode(McpOAuth2Attributes.SCOPE_READ)
                + "&code_challenge=" + encode(RegisteredClientFixtures.codeChallenge(CODE_VERIFIER))
                + "&code_challenge_method=S256");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String queryParameter(String url, String name) {
        String value = UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst(name);
        return value == null ? null : URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String csrfToken(MvcResult consentPage) throws Exception {
        Matcher matcher = CSRF_INPUT.matcher(consentPage.getResponse().getContentAsString());
        assertThat(matcher.find()).as("consent page carries a CSRF token").isTrue();
        return matcher.group(1).replace("&#39;", "'").replace("&quot;", "\"")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
    }

    private static Cookie csrfCookie(MvcResult consentPage) {
        Cookie cookie = consentPage.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).as("consent page issues the CSRF cookie").isNotNull();
        return cookie;
    }
}

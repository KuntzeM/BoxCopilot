package com.boxcopilot.backend.dto;

import com.boxcopilot.backend.controller.testutil.WithMockOidcUser;
import com.boxcopilot.backend.domain.AuthProvider;
import com.boxcopilot.backend.domain.Role;
import com.boxcopilot.backend.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The JSON the API produces must equal the Spring Boot 3.5 baseline captured in
 * {@code docs/superpowers/plans/artifacts/baseline-3.5}, copied to {@code src/test/resources/baseline-3.5}. Timestamps and UUIDs are normalised to the
 * baseline placeholders; compared as trees, so property order is not part of the contract.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JsonContractTest {

    private static final String BASELINE = "baseline-3.5/";
    private static final Instant INSTANT = Instant.parse("2026-01-02T03:04:05.678901Z");
    private static final LocalDateTime LDT = LocalDateTime.of(2026, 1, 2, 3, 4, 5, 678_000_000);

    @Autowired
    private JsonMapper json;
    @Autowired
    private MockMvc mockMvc;

    private JsonNode baseline(String file) throws Exception {
        return json.readTree(new String(new org.springframework.core.io.ClassPathResource(BASELINE + file).getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).get("response").get("body");
    }

    /** Serialises with the application mapper, then maps ISO timestamps to the baseline placeholder. */
    private JsonNode actual(Object value) {
        // round-trip through text: valueToTree keeps Long/Integer node types, the wire does not
        return normalise(json.readTree(json.writeValueAsString(value)));
    }

    private JsonNode normalise(JsonNode n) {
        if (n.isObject()) {
            ObjectNode o = (ObjectNode) n;
            o.propertyNames().forEach(k -> o.set(k, normalise(o.get(k))));
        } else if (n.isArray()) {
            for (int i = 0; i < n.size(); i++) {
                ((tools.jackson.databind.node.ArrayNode) n).set(i, normalise(n.get(i)));
            }
        } else if (n.isString() && n.stringValue().matches("\\d{4}-\\d\\d-\\d\\dT[\\d:.]+Z?")) {
            return json.getNodeFactory().stringNode("<timestamp>");
        }
        return n;
    }

    @Test
    void timestampsAreIsoStringsNotNumbers() {
        assertThat(json.writeValueAsString(Map.of("i", INSTANT, "l", LDT)))
                .contains("\"2026-01-02T03:04:05.678901Z\"")
                .contains("\"2026-01-02T03:04:05.678\"");
    }

    @Test
    void boxResponse() throws Exception {
        BoxResponseDTO box = new BoxResponseDTO(1L, "<uuid>", "Cellar", "Kitchen", "Winter things", INSTANT);
        box.setBoxNumber(1);
        box.setIsFragile(true);
        assertThat(actual(box)).isEqualTo(baseline("38-boxes-create.json"));
    }

    @Test
    void itemResponse() throws Exception {
        ItemResponseDTO item = new ItemResponseDTO(1L, "Reading lamp", 1L, "<uuid>", "Cellar", "Kitchen");
        JsonNode expected = baseline("55-items-list.json").get(1);
        assertThat(actual(item)).isEqualTo(expected);
    }

    @Test
    void userDto() throws Exception {
        UserDTO u = new UserDTO(1L, "admin", "Administrator", AuthProvider.LOCAL, Role.ADMIN, true, LDT, LDT, 0, null, true);
        u.setMcpAllowed(false);
        assertThat(actual(u)).isEqualTo(baseline("15-admin-users-list.json").get(0));
    }

    @Test
    void magicLinkResponse() throws Exception {
        MagicLinkResponseDTO m = new MagicLinkResponseDTO("<redacted>", "<redacted>", LDT);
        assertThat(actual(m)).isEqualTo(baseline("27-admin-users-magic-link-create.json"));
    }

    @Test
    void boxPreview() throws Exception {
        BoxPreviewDTO.ItemDTO item = new BoxPreviewDTO.ItemDTO(1L, "Reading lamp");
        item.setImageUrl("/api/v1/public/items/<token>/image?t=<ts>");
        BoxPreviewDTO p = new BoxPreviewDTO(1L, "<uuid>", "Cellar", "Kitchen", "Winter things (updated)", List.of(item));
        p.setBoxNumber(1);
        p.setIsFragile(true);
        p.setNoStack(true);
        assertThat(actual(p)).isEqualTo(baseline("71-public-box-preview.json"));
    }

    @Test
    void principalOmitsMcpFieldsWhenNull_andIncludesWhenSet() throws Exception {
        UserPrincipalDTO anon = new UserPrincipalDTO(null, null, null, null, "<redacted>", false);
        assertThat(actual(anon)).isEqualTo(baseline("02-anon-me.json"));

        UserPrincipalDTO admin = new UserPrincipalDTO(1L, "admin", "Administrator", Role.ADMIN, AuthProvider.LOCAL, "<redacted>", true);
        admin.setMcpAllowed(false);
        admin.setMcpPending(false);
        assertThat(actual(admin)).isEqualTo(baseline("12-admin-me.json"));
    }

    @Test
    void errorBodies() throws Exception {
        assertThat(actual(new GlobalExceptionHandler.ErrorResponse(404, "Box not found with UUID: <uuid>", INSTANT)))
                .isEqualTo(baseline("45-err-boxes-get-unknown.json"));
        assertThat(actual(new GlobalExceptionHandler.ValidationErrorResponse(400, "Validation failed", INSTANT,
                Map.of("boxId", "Box ID is required", "name", "Name is required"))))
                .isEqualTo(baseline("52-err-items-create-empty-body.json"));
    }

    @Test
    void anonymousMe_overHttp() throws Exception {
        String body = mockMvc.perform(get("/api/v1/me")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        ObjectNode actual = (ObjectNode) json.readTree(body);
        actual.put("csrfToken", "<redacted>");
        assertThat(actual).isEqualTo(baseline("02-anon-me.json"));
    }

    @Test
    @WithMockOidcUser
    void requestBodyErrors_overHttp() throws Exception {
        // malformed JSON and wrong type both answer the same 400 body as 3.5
        for (String bad : new String[] {"{not json", "{\"boxId\":\"abc\",\"name\":\"x\"}"}) {
            String body = mockMvc.perform(post("/api/v1/items").contentType(MediaType.APPLICATION_JSON).content(bad))
                    .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
            assertThat(normalise(json.readTree(body))).isEqualTo(baseline("53-err-items-create-wrong-type.json"));
        }
        String empty = mockMvc.perform(post("/api/v1/items").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(normalise(json.readTree(empty))).isEqualTo(baseline("52-err-items-create-empty-body.json"));
    }
}

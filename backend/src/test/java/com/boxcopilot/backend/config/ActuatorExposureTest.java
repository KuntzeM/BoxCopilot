package com.boxcopilot.backend.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The real application (dev profile, so H2 and no Redis) behind the real filter chains: {@code /actuator/health} is
 * public and shows nothing but the status, every other actuator path is not reachable, and the status follows the
 * database. The prod-only Redis contributor is covered by ActuatorProdHealthTest.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:actuatorexposure;DB_CLOSE_DELAY=-1",
        "app.image.storage.path=target/web-runtime-images"})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(ActuatorExposureTest.OidcSupport.class)
class ActuatorExposureTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ApplicationContext context;

    @Autowired
    DataSource dataSource;

    @Test
    void healthIsPublicAndShowsOnlyTheStatus() throws Exception {
        MvcResult result = mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}", true))
                .andReturn();
        // a probe must not leave a session behind
        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(result.getResponse().getHeaders("Set-Cookie")).isEmpty();
    }

    @Test
    void healthSubPathsShowNoComponentsEither() throws Exception {
        mockMvc.perform(get("/actuator/health/db"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("jdbc"))));
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isNotFound());
    }

    @Test
    void everyOtherActuatorPathIsUnreachable() throws Exception {
        for (String path : new String[] {"/actuator", "/actuator/env", "/actuator/beans", "/actuator/metrics",
                "/actuator/info", "/actuator/heapdump", "/actuator/loggers", "/actuator/mappings"}) {
            int status = mockMvc.perform(get(path)).andReturn().getResponse().getStatus();
            // anonymous callers are sent away by the chain (302 to the frontend); none of them gets a 200
            assertThat(status).as(path).isIn(302, 401, 403, 404);
        }
    }

    @Test
    void postToHealthIsNotPublic() throws Exception {
        int status = mockMvc.perform(post("/actuator/health")).andReturn().getResponse().getStatus();
        assertThat(status).isIn(302, 401, 403);
    }

    @Test
    void devRunsWithoutRedisAndChecksTheDatabase() {
        assertThat(context.containsBean("dbHealthContributor")).isTrue();
        assertThat(context.containsBean("redisHealthContributor")).isFalse();
    }

    /** Last on purpose: the pool is closed, so the context is discarded afterwards. */
    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void healthIsDownWith503WhenTheDatabaseIsUnusable() throws Exception {
        ((HikariDataSource) dataSource).close();

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json("{\"status\":\"DOWN\"}", true));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class OidcSupport {
        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("nextcloud")
                    .clientId("test-client").clientSecret("test-secret")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .redirectUri("http://localhost:8080/login/oauth2/code/{registrationId}")
                    .scope("openid")
                    .authorizationUri("http://localhost/authorize").tokenUri("http://localhost/token")
                    .jwkSetUri("http://localhost/jwks").issuerUri("http://localhost")
                    .userNameAttributeName(IdTokenClaimNames.SUB).build());
        }
    }
}

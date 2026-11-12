package com.boxcopilot.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Under the real prod profile the health status includes Redis. Nothing listens on the Redis host here, so the
 * probe must say DOWN (503) - and still reveal neither the host nor any component.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:prodhealth;DB_CLOSE_DELAY=-1",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.security.oauth2.client.registration.nextcloud.client-id=x",
        "spring.security.oauth2.client.registration.nextcloud.client-secret=x",
        "spring.security.oauth2.client.registration.nextcloud.redirect-uri=http://localhost/login/oauth2/code/nextcloud",
        "spring.security.oauth2.client.registration.nextcloud.provider=offline",
        "spring.security.oauth2.client.provider.offline.authorization-uri=http://localhost/authorize",
        "spring.security.oauth2.client.provider.offline.token-uri=http://localhost/token",
        "spring.security.oauth2.client.provider.offline.user-info-uri=http://localhost/userinfo",
        "spring.security.oauth2.client.provider.offline.jwk-set-uri=http://localhost/jwks",
        "spring.data.redis.host=redis-secret-host.invalid",
        "spring.data.redis.port=6379",
        "spring.data.redis.password=",
        "spring.data.redis.timeout=500ms",
        "spring.data.redis.connect-timeout=500ms",
        "frontend.url=http://localhost:3000",
        "nextcloud.logout-url=http://localhost/logout",
        "app.image.storage.path=target/web-runtime-images",
        "app.admin.password=prod-health-test-password"})
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class ActuatorProdHealthTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ApplicationContext context;

    @Test
    void prodChecksRedisAndTheDatabase() {
        assertThat(context.containsBean("redisHealthContributor")).isTrue();
        assertThat(context.containsBean("dbHealthContributor")).isTrue();
    }

    @Test
    void healthIsDownWithoutLeakingTheRedisHost() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json("{\"status\":\"DOWN\"}", true));
    }
}

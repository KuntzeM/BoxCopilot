package com.boxcopilot.backend.mcp;

import com.boxcopilot.backend.mcp.abuse.McpRateLimitAudit;
import com.boxcopilot.backend.mcp.abuse.McpRateLimits;
import com.boxcopilot.backend.mcp.observability.McpAuditLogger;
import com.boxcopilot.backend.mcp.observability.McpToolMetrics;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.DefaultServerTransportSecurityValidator;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStatelessServerTransport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.ClassUtils;
import org.springframework.web.servlet.function.RouterFunction;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Proves with the real {@code application.yml} and the real Spring AI auto-configurations that the flag
 * {@code app.mcp.enabled} is what creates the MCP server: off, nothing MCP exists.
 */
class McpFlagOffTest {

    private static final String MCP_SERVER_PACKAGE = "org.springframework.ai.mcp.server.";

    /**
     * Every MCP server auto-configuration the starter registers, read from its {@code AutoConfiguration.imports}
     * rather than listed here: that includes the SSE one Spring AI 2 deprecated for removal (it still ships and
     * still has to stay off) and whatever a later version adds.
     */
    static final List<String> STARTER_AUTO_CONFIGURATIONS =
            ImportCandidates.load(AutoConfiguration.class, McpFlagOffTest.class.getClassLoader()).getCandidates()
                    .stream().filter(name -> name.startsWith(MCP_SERVER_PACKAGE)).toList();

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            // Registered in META-INF/spring.factories; a context runner does not run environment post-processors
            .withInitializer(context -> new McpStarterSwitch().postProcessEnvironment(context.getEnvironment(), null))
            .withPropertyValues("spring.profiles.active=mcp-test")
            .withConfiguration(AutoConfigurations.of(Stream.concat(
                    Stream.of(JacksonAutoConfiguration.class),
                    STARTER_AUTO_CONFIGURATIONS.stream().map(name -> ClassUtils.resolveClassName(name, null)))
                    .toArray(Class<?>[]::new)))
            .withBean(McpReadService.class, () -> mock(McpReadService.class))
            .withBean(McpUserGate.class, () -> mock(McpUserGate.class))
            .withUserConfiguration(McpConfiguration.class, McpTransportConfiguration.class, McpStartupGuard.class,
                    McpRateLimits.class, McpRateLimitAudit.class, McpAuditLogger.class, McpToolMetrics.class,
                    BoxPilotMcpTools.class);

    @Test
    void flagDefaultsToOffAndNoMcpBeanExists() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertNoMcpBeans(context);
        });
    }

    @Test
    void theStarterAutoConfigurationsAreTheOnesThisTestExpects() {
        assertThat(STARTER_AUTO_CONFIGURATIONS).contains(
                MCP_SERVER_PACKAGE + "common.autoconfigure.McpServerAutoConfiguration",
                MCP_SERVER_PACKAGE + "common.autoconfigure.McpServerStatelessAutoConfiguration",
                MCP_SERVER_PACKAGE + "webmvc.autoconfigure.McpServerStatelessWebMvcAutoConfiguration",
                MCP_SERVER_PACKAGE + "webmvc.autoconfigure.McpServerStreamableHttpWebMvcAutoConfiguration",
                MCP_SERVER_PACKAGE + "webmvc.autoconfigure.McpServerSseWebMvcAutoConfiguration");
    }

    /**
     * The kill switch is {@code app.mcp.enabled}; it has to win even when the starter's own switch is set
     * directly (as a developer profile or an old {@code SPRING_AI_MCP_SERVER_ENABLED} may do).
     */
    @Test
    void disabledFlagWinsOverTheStarterSwitch() {
        runner.withPropertyValues(
                        "app.mcp.enabled=false",
                        "spring.ai.mcp.server.enabled=true",
                        "app.mcp.backend-url=https://boxpilot.example.com")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertNoMcpBeans(context);
                });
    }

    @Test
    void explicitlyDisabledFlagCreatesNoMcpBeanEvenWithCompleteConfiguration() {
        runner.withPropertyValues(
                        "app.mcp.enabled=false",
                        "app.mcp.backend-url=https://boxpilot.example.com")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertNoMcpBeans(context);
                });
    }

    @Test
    void starterFollowsTheFlagThroughTheYamlBinding() {
        runner.run(context -> {
            assertThat(context.getEnvironment().getProperty("spring.ai.mcp.server.enabled")).isEqualTo("false");
            assertThat(context.getEnvironment().getProperty("spring.ai.mcp.server.protocol")).isEqualTo("STATELESS");
            assertThat(context.getEnvironment().getProperty("spring.ai.mcp.server.streamable-http.mcp-endpoint"))
                    .isEqualTo("/mcp");
        });
        runner.withPropertyValues("app.mcp.enabled=true", "app.mcp.backend-url=https://boxpilot.example.com")
                .run(context -> assertThat(context.getEnvironment().getProperty("spring.ai.mcp.server.enabled"))
                        .isEqualTo("true"));
    }

    @Test
    void enabledFlagCreatesServerTransportRouteAndTools() {
        runner.withPropertyValues(
                        "app.mcp.enabled=true",
                        "app.mcp.backend-url=https://boxpilot.example.com")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(McpStatelessSyncServer.class);
                    assertThat(context).hasSingleBean(WebMvcStatelessServerTransport.class);
                    // Ours, not the starter's (which backs off): it carries our extractor and the host check
                    WebMvcStatelessServerTransport transport = context.getBean(WebMvcStatelessServerTransport.class);
                    assertThat(ReflectionTestUtils.getField(transport, "contextExtractor"))
                            .isInstanceOf(McpTransportContextExtractor.class);
                    assertThat(ReflectionTestUtils.getField(transport, "securityValidator"))
                            .isInstanceOf(DefaultServerTransportSecurityValidator.class);
                    assertThat(context).hasBean("webMvcStatelessServerRouterFunction");
                    assertThat(context).hasSingleBean(BoxPilotMcpTools.class);
                    assertThat(context).hasSingleBean(McpProperties.class);
                    assertThat(context.getBean(McpProperties.class).redirectUris())
                            .containsExactly("https://claude.ai/api/mcp/auth_callback",
                                    "http://127.0.0.1:41000/callback");
                });
    }

    @Test
    void enabledFlagWithoutUserGateBeanFailsStartup() {
        new ApplicationContextRunner()
                .withPropertyValues("app.mcp.enabled=true", "app.mcp.backend-url=https://boxpilot.example.com",
                        "app.mcp.redirect-uris=https://claude.ai/cb")
                .withBean(McpReadService.class, () -> mock(McpReadService.class))
                .withUserConfiguration(McpConfiguration.class, McpTransportConfiguration.class,
                        McpStartupGuard.class, McpRateLimits.class, McpRateLimitAudit.class,
                        McpAuditLogger.class, McpToolMetrics.class, BoxPilotMcpTools.class)
                .run(context -> assertThat(context.getStartupFailure())
                        .hasMessageContaining(McpUserGate.class.getName()));
    }

    private static void assertNoMcpBeans(AssertableApplicationContext context) {
        assertThat(context).doesNotHaveBean(McpStatelessSyncServer.class);
        assertThat(context).doesNotHaveBean(WebMvcStatelessServerTransport.class);
        assertThat(context).doesNotHaveBean("webMvcStatelessServerRouterFunction");
        assertThat(context.getBeansOfType(RouterFunction.class)).isEmpty();
        assertThat(context).doesNotHaveBean(BoxPilotMcpTools.class);
        assertThat(context).doesNotHaveBean(McpProperties.class);
        assertThat(context).doesNotHaveBean(McpStartupGuard.class);
        assertThat(context).doesNotHaveBean(McpTransportContextExtractor.class);
        assertThat(context).doesNotHaveBean(McpRateLimits.class);
        assertThat(context).doesNotHaveBean(McpRateLimitAudit.class);
        assertThat(context).doesNotHaveBean(McpAuditLogger.class);
        assertThat(context).doesNotHaveBean(McpToolMetrics.class);
        assertThat(context.getBeanNamesForType(io.modelcontextprotocol.server.McpServer.class)).isEmpty();
    }
}

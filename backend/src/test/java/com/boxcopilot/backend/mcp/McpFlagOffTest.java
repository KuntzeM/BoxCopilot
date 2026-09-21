package com.boxcopilot.backend.mcp;

import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcStatelessServerTransport;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.server.autoconfigure.McpServerSseWebMvcAutoConfiguration;
import org.springframework.ai.mcp.server.autoconfigure.McpServerStatelessWebMvcAutoConfiguration;
import org.springframework.ai.mcp.server.autoconfigure.McpServerStreamableHttpWebMvcAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerObjectMapperAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerStatelessAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.StatelessToolCallbackConverterAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.ToolCallbackConverterAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerAnnotationScannerAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerSpecificationFactoryAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.StatelessServerSpecificationFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.servlet.function.RouterFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Proves with the real {@code application.yml} and the real Spring AI auto-configurations that the flag
 * {@code app.mcp.enabled} is what creates the MCP server: off, nothing MCP exists.
 */
class McpFlagOffTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withPropertyValues("spring.profiles.active=mcp-test")
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    McpServerObjectMapperAutoConfiguration.class,
                    McpServerAutoConfiguration.class,
                    McpServerStatelessAutoConfiguration.class,
                    ToolCallbackConverterAutoConfiguration.class,
                    StatelessToolCallbackConverterAutoConfiguration.class,
                    McpServerAnnotationScannerAutoConfiguration.class,
                    McpServerSpecificationFactoryAutoConfiguration.class,
                    StatelessServerSpecificationFactoryAutoConfiguration.class,
                    McpServerStatelessWebMvcAutoConfiguration.class,
                    McpServerStreamableHttpWebMvcAutoConfiguration.class,
                    McpServerSseWebMvcAutoConfiguration.class))
            .withBean(McpReadService.class, () -> mock(McpReadService.class))
            .withBean(McpUserGate.class, () -> mock(McpUserGate.class))
            .withUserConfiguration(McpConfiguration.class, McpTransportConfiguration.class, McpStartupGuard.class,
                    BoxPilotMcpTools.class);

    @Test
    void flagDefaultsToOffAndNoMcpBeanExists() {
        runner.run(context -> {
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
                    assertThat(context).hasBean("webMvcStatelessServerRouterFunction");
                    assertThat(context).hasSingleBean(BoxPilotMcpTools.class);
                    assertThat(context).hasSingleBean(McpProperties.class);
                    assertThat(context.getBean(McpProperties.class).redirectUris())
                            .containsExactly("https://claude.ai/api/mcp/auth_callback");
                });
    }

    @Test
    void enabledFlagWithoutUserGateBeanFailsStartup() {
        new ApplicationContextRunner()
                .withPropertyValues("app.mcp.enabled=true", "app.mcp.backend-url=https://boxpilot.example.com",
                        "app.mcp.redirect-uris=https://claude.ai/cb")
                .withBean(McpReadService.class, () -> mock(McpReadService.class))
                .withUserConfiguration(McpConfiguration.class, McpTransportConfiguration.class, McpStartupGuard.class,
                    BoxPilotMcpTools.class)
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
        assertThat(context.getBeanNamesForType(io.modelcontextprotocol.server.McpServer.class)).isEmpty();
    }
}

package com.boxcopilot.backend.mcp;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest(properties = "spring.flyway.enabled=false")
@ActiveProfiles("test")
@Import(DefaultMcpReadServiceContract.ServiceConfig.class)
class DefaultMcpReadServiceH2Test extends DefaultMcpReadServiceContract {
}

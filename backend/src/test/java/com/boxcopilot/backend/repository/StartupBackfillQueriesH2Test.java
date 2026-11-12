package com.boxcopilot.backend.repository;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest(properties = "spring.flyway.enabled=false")
@ActiveProfiles("test")
class StartupBackfillQueriesH2Test extends StartupBackfillQueriesContract {
}

# Spring Boot 3.5.16 baseline fixtures (test input)

Captures taken from the last Spring Boot 3.5.16 build (see `docs/superpowers/plans/artifacts/baseline-3.5`, where the
full set and the capture scripts live). The tests load these from the classpath:

- `NN-*.json`: response captures read by `JsonContractTest` and `McpResourceServerIntegrationTest` (`85-...`).
- `redis-session-fixture.txt`: a 3.5-written Redis session read by `RedisSessionCompatibilityTest`.

They are throwaway captures of disposable containers. Do NOT regenerate them with a 4.x build: their whole point is
to be what 3.5 produced.

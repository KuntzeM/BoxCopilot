package com.boxcopilot.backend.mcp;

import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.dto.UpdateUserDTO;
import com.boxcopilot.backend.mcp.authserver.McpClientProvisioningService;
import com.boxcopilot.backend.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Provisioning against a real Postgres from several threads at once. Both cases are check-then-act races that a
 * single-threaded test cannot see: two requests provisioning the same user, and a request provisioning while
 * the account loses MCP access beside it.
 */
class McpProvisioningConcurrencyIntegrationTest extends McpIntegrationTestBase {

    private static final int THREADS = 4;
    private static final int ATTEMPTS = 5;

    @Autowired
    private McpClientProvisioningService provisioning;
    @Autowired
    private UserService userService;

    private final ExecutorService executor = Executors.newFixedThreadPool(THREADS);

    @AfterEach
    void stopThreads() {
        executor.shutdownNow();
    }

    /** Without the per-user row lock both callers see "no client yet" and each inserts one. */
    @Test
    void concurrentProvisioningCreatesExactlyOneClient() throws Exception {
        User user = createUser(true, true);

        List<String> clientIds = inParallel(THREADS, () -> provisioning.provision(user.getId()));

        assertThat(clientIds).hasSize(THREADS);
        assertThat(new HashSet<>(clientIds)).as("all callers got the same client id").hasSize(1);
        assertThat(clientRows()).isEqualTo(1);
    }

    /**
     * A provisioning request that races the withdrawal of MCP access must not leave a client behind: either it
     * wins and its client is torn down by the revocation, or it waits and is then refused.
     */
    @Test
    void aProvisioningThatRacesTheRevocationLeavesNoClientBehind() throws Exception {
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            User user = createUser(true, true);
            provisioning.provision(user.getId());

            inParallel(List.of(
                    () -> provisionQuietly(user.getId()),
                    () -> {
                        UpdateUserDTO withdraw = new UpdateUserDTO();
                        withdraw.setMcpAllowed(false);
                        userService.updateUser(user.getId(), withdraw);
                        return "revoked";
                    }));

            assertThat(userRepository.findById(user.getId()).orElseThrow().isMcpAllowed())
                    .as("attempt %s", attempt).isFalse();
            assertThat(clientRows()).as("attempt %s: no client survives the revocation", attempt).isZero();
        }
    }

    /** A refusal is one of the two legal outcomes of the race, so it is not an error here. */
    private String provisionQuietly(long userId) {
        try {
            return provisioning.provision(userId);
        } catch (McpAccessDeniedException e) {
            return null;
        }
    }

    private List<String> inParallel(int count, Callable<String> task) throws Exception {
        return inParallel(java.util.Collections.nCopies(count, task));
    }

    /** Starts every task on its own thread and releases them together, so they really overlap. */
    private List<String> inParallel(List<Callable<String>> tasks) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        for (Callable<String> task : tasks) {
            futures.add(executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return task.call();
            }));
        }
        start.countDown();
        List<String> results = new ArrayList<>();
        for (Future<String> future : futures) {
            results.add(future.get(30, TimeUnit.SECONDS));
        }
        return results;
    }

    private int clientRows() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM oauth2_registered_client", Integer.class);
    }
}

package com.boxcopilot.backend.mcp;

import com.boxcopilot.backend.domain.Role;
import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.dto.UpdateUserDTO;
import com.boxcopilot.backend.mcp.authserver.McpAccessRevokedListener;
import com.boxcopilot.backend.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What happens to a client and its connections when the account changes: withdrawing MCP access, disabling and
 * deleting tear everything down, a lock and a role change leave it alone.
 */
class McpAccessRevocationIntegrationTest extends McpIntegrationTestBase {

    @Autowired
    private UserService userService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void withdrawingMcpAccessRemovesTheClientAndEveryConnection() {
        User user = connectedUser();

        userService.updateUser(user.getId(), mcpAllowed(false));

        assertThat(clientRows()).isZero();
        assertThat(authorizationRows()).isZero();
    }

    @Test
    void disablingTheAccountRemovesTheClientAndEveryConnection() {
        User user = connectedUser();
        UpdateUserDTO update = new UpdateUserDTO();
        update.setEnabled(false);

        userService.updateUser(user.getId(), update);

        assertThat(clientRows()).isZero();
        assertThat(authorizationRows()).isZero();
    }

    @Test
    void deletingTheAccountRemovesTheClientAndEveryConnection() {
        User user = connectedUser();

        userService.deleteUser(user.getId(), "someone-else");

        assertThat(clientRows()).isZero();
        assertThat(authorizationRows()).isZero();
    }

    /** A temporary lock denies access while it lasts, but it must not destroy the connection. */
    @Test
    void lockingTheAccountKeepsTheClientAndItsConnections() {
        User user = connectedUser();

        user.setLockedUntil(LocalDateTime.now().plusHours(1));
        userRepository.save(user);

        assertThat(clientRows()).isEqualTo(1);
        assertThat(authorizationRows()).isEqualTo(1);
    }

    @Test
    void changingTheRoleKeepsTheClientAndItsConnections() {
        User user = connectedUser();
        UpdateUserDTO update = new UpdateUserDTO();
        update.setRole(Role.ADMIN);

        userService.updateUser(user.getId(), update);

        assertThat(clientRows()).isEqualTo(1);
        assertThat(authorizationRows()).isEqualTo(1);
    }

    @Test
    void grantingMcpAccessAgainTouchesNothing() {
        User user = createUser(true, false);
        RegisteredClient client = createClient(user.getId());
        saveAuthorization(client, user.getId(), "still-valid");

        userService.updateUser(user.getId(), mcpAllowed(true));

        assertThat(clientRows()).isEqualTo(1);
        assertThat(authorizationRows()).isEqualTo(1);
    }

    /**
     * The teardown belongs to the transaction of the user change: when that transaction rolls back, the user
     * keeps MCP access and the client is still there. Nothing is deleted ahead of, or beside, the commit.
     */
    @Test
    void aRolledBackUserChangeLeavesTheClientInPlace() {
        User user = connectedUser();
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        transactionTemplate.execute(status -> {
            userService.updateUser(user.getId(), mcpAllowed(false));
            status.setRollbackOnly();
            return null;
        });

        assertThat(userRepository.findById(user.getId()).orElseThrow().isMcpAllowed()).isTrue();
        assertThat(clientRows()).isEqualTo(1);
        assertThat(authorizationRows()).isEqualTo(1);
    }

    /** The phase is what makes the teardown atomic with the user change; an after-commit phase would not be. */
    @Test
    void theListenerRunsBeforeTheCommitOfTheUserChange() throws NoSuchMethodException {
        Method method = McpAccessRevokedListener.class.getMethod("onMcpAccessRevoked",
                com.boxcopilot.backend.event.McpAccessRevokedEvent.class);

        assertThat(method.getAnnotation(TransactionalEventListener.class).phase())
                .isEqualTo(TransactionPhase.BEFORE_COMMIT);
    }

    private User connectedUser() {
        User user = createUser(true, true);
        RegisteredClient client = createClient(user.getId());
        saveAuthorization(client, user.getId(), "access-token-" + user.getId());
        assertThat(clientRows()).isEqualTo(1);
        assertThat(authorizationRows()).isEqualTo(1);
        return user;
    }

    private static UpdateUserDTO mcpAllowed(boolean allowed) {
        UpdateUserDTO update = new UpdateUserDTO();
        update.setMcpAllowed(allowed);
        return update;
    }

    private int clientRows() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM oauth2_registered_client", Integer.class);
    }

    private int authorizationRows() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM oauth2_authorization", Integer.class);
    }
}

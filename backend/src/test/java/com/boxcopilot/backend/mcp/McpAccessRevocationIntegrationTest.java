package com.boxcopilot.backend.mcp;

import com.boxcopilot.backend.domain.Role;
import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.dto.UpdateUserDTO;
import com.boxcopilot.backend.mcp.authserver.McpAccessRevokedListener;
import com.boxcopilot.backend.mcp.authserver.McpSessionUserResolver;
import com.boxcopilot.backend.mcp.authserver.McpUserClients;
import com.boxcopilot.backend.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * What happens to a client and its connections when the account changes: withdrawing MCP access, disabling and
 * deleting tear everything down, a lock and a role change leave it alone.
 */
class McpAccessRevocationIntegrationTest extends McpIntegrationTestBase {

    @Autowired
    private UserService userService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private DataSource dataSource;
    @MockitoSpyBean
    private McpUserClients userClients;
    @MockitoSpyBean
    private McpSessionUserResolver sessionUserResolver;

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

    /**
     * The gate has to read the user from the database, not from a persistence context that the request opened
     * earlier. The controller resolves the session user through JPA before the provisioning transaction even
     * starts; with an open session in view, the gate's own {@code findById} would be answered from the
     * first-level cache of that first read and would still see the access this test withdraws in between.
     *
     * <p>The withdrawal is committed on a separate connection while the request is running, exactly as a
     * concurrent administrator would. The request has to end in 403 and must not leave a client behind.
     */
    @Test
    void theGateSeesAWithdrawalThatCommitsWhileTheRequestIsAlreadyRunning() throws Exception {
        User user = createUser(true, true);
        doAnswer(invocation -> {
            Object resolved = invocation.callRealMethod();
            withdrawMcpAccessOnItsOwnConnection(user.getId());
            return resolved;
        }).when(sessionUserResolver).resolve(org.mockito.ArgumentMatchers.any());

        int status = mockMvc.perform(post("/api/v1/me/mcp-client").session(sessionFor(user)).with(csrf()))
                .andReturn().getResponse().getStatus();

        assertThat(status).as("the gate must not decide on state read before the withdrawal").isEqualTo(403);
        assertThat(clientRows()).isZero();
    }

    /** A commit of its own, on a connection of its own: nothing of the running request can see it as its own. */
    private void withdrawMcpAccessOnItsOwnConnection(long userId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection
                     .prepareStatement("UPDATE users SET mcp_allowed = false WHERE id = ?")) {
            connection.setAutoCommit(true);
            statement.setLong(1, userId);
            statement.executeUpdate();
        }
    }

    /**
     * The other direction of the same atomicity: when the teardown fails, the user change goes with it. Nobody
     * ends up without MCP access while a working client is still registered.
     */
    @Test
    void aFailingTeardownRollsTheUserChangeBack() {
        User user = connectedUser();
        doThrow(new DataAccessResourceFailureException("client store unavailable"))
                .when(userClients).deleteByUserId(user.getId());

        assertThatThrownBy(() -> userService.updateUser(user.getId(), mcpAllowed(false)))
                .isInstanceOf(DataAccessResourceFailureException.class);

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

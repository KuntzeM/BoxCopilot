package com.boxcopilot.backend.mcp.authserver;

import com.boxcopilot.backend.event.McpAccessRevokedEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Tears the MCP access of a user down when the account loses it: MCP withdrawn, account disabled, account
 * deleted. A temporary lock publishes no event and therefore deletes nothing.
 *
 * <p>The listener runs in {@link TransactionPhase#BEFORE_COMMIT}, so the deletion of the client and of every
 * connection is part of the very transaction that changes the user: either both happen or neither does. An
 * after-commit phase would open a second transaction, in which a failure would leave a user without MCP access
 * still holding a working connection.
 */
@Component
@ConditionalOnProperty(prefix = "app.mcp", name = "enabled", havingValue = "true")
public class McpAccessRevokedListener {

    private final McpClientProvisioningService provisioning;

    public McpAccessRevokedListener(McpClientProvisioningService provisioning) {
        this.provisioning = provisioning;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onMcpAccessRevoked(McpAccessRevokedEvent event) {
        provisioning.revokeAllAccess(event.userId());
    }
}

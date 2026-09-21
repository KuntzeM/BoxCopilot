package com.boxcopilot.backend.service;

import com.boxcopilot.backend.domain.AuthProvider;
import com.boxcopilot.backend.domain.Role;
import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.dto.UpdateUserDTO;
import com.boxcopilot.backend.event.McpAccessRevokedEvent;
import com.boxcopilot.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@RecordApplicationEvents
class UserMcpAccessRevocationIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ApplicationEvents events;

    private User user;

    @BeforeEach
    void setUp() {
        User candidate = new User("mcp-user", "Mcp User", AuthProvider.LOCAL, Role.USER);
        candidate.setMcpAllowed(true);
        user = userRepository.save(candidate);
    }

    @AfterEach
    void tearDown() {
        userRepository.findById(user.getId()).ifPresent(userRepository::delete);
    }

    @Test
    void mcpAllowedIsPersistedAndReturnedByTheService() {
        assertThat(userService.getUserById(user.getId()).getMcpAllowed()).isTrue();
        assertThat(userRepository.findById(user.getId()).orElseThrow().isMcpAllowed()).isTrue();
    }

    @Test
    void newUsersAreNotMcpAllowedByDefault() {
        assertThat(new User().isMcpAllowed()).isFalse();
        assertThat(new User("plain", "Plain", AuthProvider.LOCAL, Role.USER).isMcpAllowed()).isFalse();
    }

    @Test
    void revokingMcpAllowedPersistsTheChangeAndPublishesTheEvent() {
        UpdateUserDTO dto = new UpdateUserDTO();
        dto.setMcpAllowed(false);

        userService.updateUser(user.getId(), dto);

        assertThat(userRepository.findById(user.getId()).orElseThrow().isMcpAllowed()).isFalse();
        assertThat(revocationsFor(user.getId())).isEqualTo(1);
    }

    @Test
    void disablingTheUserPublishesTheEvent() {
        UpdateUserDTO dto = new UpdateUserDTO();
        dto.setEnabled(false);

        userService.updateUser(user.getId(), dto);

        assertThat(userRepository.findById(user.getId()).orElseThrow().getEnabled()).isFalse();
        assertThat(revocationsFor(user.getId())).isEqualTo(1);
    }

    @Test
    void deletingTheUserPublishesTheEvent() {
        userService.deleteUser(user.getId(), "some-admin");

        assertThat(userRepository.findById(user.getId())).isEmpty();
        assertThat(revocationsFor(user.getId())).isEqualTo(1);
    }

    @Test
    void lockingAndRoleChangeDoNotPublishTheEvent() {
        for (int attempt = 0; attempt < 5; attempt++) {
            userService.recordFailedLogin("mcp-user");
        }
        UpdateUserDTO dto = new UpdateUserDTO();
        dto.setRole(Role.ADMIN);
        userService.updateUser(user.getId(), dto);

        User stored = userRepository.findById(user.getId()).orElseThrow();
        assertThat(stored.isAccountLocked()).isTrue();
        assertThat(stored.getRole()).isEqualTo(Role.ADMIN);
        assertThat(stored.isMcpAllowed()).isTrue();
        assertThat(revocationsFor(user.getId())).isZero();
    }

    private long revocationsFor(long userId) {
        return events.stream(McpAccessRevokedEvent.class)
                .filter(event -> event.userId() == userId)
                .count();
    }
}

package com.boxcopilot.backend.service;

import com.boxcopilot.backend.domain.AuthProvider;
import com.boxcopilot.backend.domain.Role;
import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.dto.CreateUserDTO;
import com.boxcopilot.backend.dto.UpdateUserDTO;
import com.boxcopilot.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User("testuser", "Test User", AuthProvider.LOCAL, Role.USER);
        testUser.setPasswordHash("hashedPassword");
    }



    @Test
    void testCreateUser_DuplicateUsername() {
        // Arrange
        CreateUserDTO dto = new CreateUserDTO("existinguser", "Existing User", "password123", Role.USER);
        when(userRepository.existsByUsername("existinguser")).thenReturn(true);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> userService.createUser(dto));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testResolveUsernameConflict() {
        // Arrange
        when(userRepository.existsByUsername("username")).thenReturn(true);
        when(userRepository.existsByUsername("username_nc")).thenReturn(true);
        when(userRepository.existsByUsername("username_nc2")).thenReturn(false);

        // Act - Use OIDC user creation to test username conflict resolution
        User oidcUser = new User();
        oidcUser.setOidcSubject("oidc123");
        oidcUser.setUsername("resolved_username");
        oidcUser.setName("Test User");
        oidcUser.setAuthProvider(AuthProvider.NEXTCLOUD);
        oidcUser.setRole(Role.USER);

        when(userRepository.findByOidcSubject("oidc123")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(oidcUser);

        var result = userService.createOrUpdateOidcUser("oidc123", "username", "Test User", "test@example.com");

        // Assert
        assertNotNull(result);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void testUpdateUser_Success() {
        // Arrange
        UpdateUserDTO dto = new UpdateUserDTO();
        dto.setName("Updated Name");
        dto.setRole(Role.ADMIN);

        testUser.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Act
        var result = userService.updateUser(1L, dto);

        // Assert
        assertNotNull(result);
        verify(userRepository).save(testUser);
    }

    @Test
    void testUpdateUser_CannotChangeNextcloudUsername() {
        // Arrange
        User nextcloudUser = new User("ncuser", "NC User", AuthProvider.NEXTCLOUD, Role.USER);
        nextcloudUser.setId(1L);

        UpdateUserDTO dto = new UpdateUserDTO();
        dto.setUsername("newusername");

        when(userRepository.findById(1L)).thenReturn(Optional.of(nextcloudUser));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> userService.updateUser(1L, dto));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testUnlockUser() {
        // Arrange
        testUser.setId(1L);
        testUser.incrementFailedAttempts();
        testUser.incrementFailedAttempts();
        testUser.incrementFailedAttempts();
        testUser.incrementFailedAttempts();
        testUser.incrementFailedAttempts();
        assertTrue(testUser.isAccountLocked());

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Act
        userService.unlockUser(1L);

        // Assert
        assertFalse(testUser.isAccountLocked());
        assertEquals(0, testUser.getFailedLoginAttempts());
        verify(userRepository).save(testUser);
    }

    @Test
    void testDeleteUser_CannotDeleteSelf() {
        // Arrange
        testUser.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> userService.deleteUser(1L, "testuser"));
        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    void testRecordFailedLogin() {
        // Arrange
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Act
        userService.recordFailedLogin("testuser");

        // Assert
        assertEquals(1, testUser.getFailedLoginAttempts());
        verify(userRepository).save(testUser);
    }

    @Test
    void testAccountLockAfterFiveAttempts() {
        // Act
        testUser.incrementFailedAttempts();
        testUser.incrementFailedAttempts();
        testUser.incrementFailedAttempts();
        testUser.incrementFailedAttempts();
        assertFalse(testUser.isAccountLocked());

        testUser.incrementFailedAttempts();

        // Assert
        assertTrue(testUser.isAccountLocked());
        assertEquals(5, testUser.getFailedLoginAttempts());
        assertNotNull(testUser.getLockedUntil());
    }

    @Test
    void testCreateUser_PasswordlessAccount() {
        // Arrange
        CreateUserDTO dto = new CreateUserDTO("passwordlessuser", "Passwordless User", null, Role.USER);
        User passwordlessUser = new User("passwordlessuser", "Passwordless User", AuthProvider.LOCAL, Role.USER);
        passwordlessUser.setPasswordHash(null);
        
        when(userRepository.existsByUsername("passwordlessuser")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(passwordlessUser);

        // Act
        var result = userService.createUser(dto);

        // Assert
        assertNotNull(result);
        verify(userRepository).save(any(User.class));
        verify(passwordEncoder, never()).encode(anyString());
    }
}

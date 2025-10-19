package stirling.software.proprietary.security.service;

import java.sql.SQLException;
import java.util.Optional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.security.crypto.password.PasswordEncoder;

import stirling.software.common.model.ApplicationProperties;
import stirling.software.common.model.enumeration.Role;
import stirling.software.proprietary.model.Team;
import stirling.software.proprietary.security.database.repository.AuthorityRepository;
import stirling.software.proprietary.security.database.repository.UserRepository;
import stirling.software.proprietary.security.model.AuthenticationType;
import stirling.software.proprietary.security.model.User;
import stirling.software.proprietary.security.repository.TeamRepository;
import stirling.software.proprietary.security.session.SessionPersistentRegistry;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;

    @Mock private TeamRepository teamRepository;

    @Mock private AuthorityRepository authorityRepository;

    @Mock private PasswordEncoder passwordEncoder;

    @Mock private MessageSource messageSource;

    @Mock private SessionPersistentRegistry sessionPersistentRegistry;

    @Mock private DatabaseServiceInterface databaseService;

    @Mock private ApplicationProperties.Security.OAUTH2 oauth2Properties;

    @InjectMocks private UserService userService;

    private Team mockTeam;
    private User mockUser;

    @BeforeEach
    void setUp() {
        mockTeam = new Team();
        mockTeam.setId(1L);
        mockTeam.setName("Test Team");

        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setUsername("testuser");
        mockUser.setEnabled(true);
    }

    @Test
    void testSaveUser_WithUsernameAndAuthenticationType_Success() throws Exception {
        // Given
        String username = "testuser";
        AuthenticationType authType = AuthenticationType.WEB;

        Mockito.when(teamRepository.findByName("Default")).thenReturn(Optional.of(mockTeam));
        Mockito.when(userRepository.save(ArgumentMatchers.any(User.class))).thenReturn(mockUser);
        Mockito.doNothing().when(databaseService).exportDatabase();

        // When
        userService.saveUser(username, authType);

        // Then
        Mockito.verify(userRepository).save(ArgumentMatchers.any(User.class));
        Mockito.verify(databaseService).exportDatabase();
    }

    @Test
    void testSaveUser_WithUsernamePasswordAndTeamId_Success() throws Exception {
        // Given
        String username = "testuser";
        String password = "password123";
        Long teamId = 1L;
        String encodedPassword = "encodedPassword123";

        Mockito.when(passwordEncoder.encode(password)).thenReturn(encodedPassword);
        Mockito.when(teamRepository.findById(teamId)).thenReturn(Optional.of(mockTeam));
        Mockito.when(userRepository.save(ArgumentMatchers.any(User.class))).thenReturn(mockUser);
        Mockito.doNothing().when(databaseService).exportDatabase();

        // When
        User result = userService.saveUser(username, password, teamId);

        // Then
        Assertions.assertNotNull(result);
        Mockito.verify(passwordEncoder).encode(password);
        Mockito.verify(teamRepository).findById(teamId);
        Mockito.verify(userRepository).save(ArgumentMatchers.any(User.class));
        Mockito.verify(databaseService).exportDatabase();
    }

    @Test
    void testSaveUser_WithTeamAndRole_Success() throws Exception {
        // Given
        String username = "testuser";
        String password = "password123";
        String role = Role.ADMIN.getRoleId();
        boolean firstLogin = true;
        String encodedPassword = "encodedPassword123";

        Mockito.when(passwordEncoder.encode(password)).thenReturn(encodedPassword);
        Mockito.when(userRepository.save(ArgumentMatchers.any(User.class))).thenReturn(mockUser);
        Mockito.doNothing().when(databaseService).exportDatabase();

        // When
        User result = userService.saveUser(username, password, mockTeam, role, firstLogin);

        // Then
        Assertions.assertNotNull(result);
        Mockito.verify(passwordEncoder).encode(password);
        Mockito.verify(userRepository).save(ArgumentMatchers.any(User.class));
        Mockito.verify(databaseService).exportDatabase();
    }

    @Test
    void testSaveUser_WithInvalidUsername_ThrowsException() throws Exception {
        // Given
        String invalidUsername = "ab"; // Too short (less than 3 characters)
        AuthenticationType authType = AuthenticationType.WEB;

        // When & Then
        IllegalArgumentException exception =
                Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> userService.saveUser(invalidUsername, authType));

        Mockito.verify(userRepository, Mockito.never()).save(ArgumentMatchers.any(User.class));
        Mockito.verify(databaseService, Mockito.never()).exportDatabase();
    }

    @Test
    void testSaveUser_WithNullPassword_Success() throws Exception {
        // Given
        String username = "testuser";
        Long teamId = 1L;

        Mockito.when(teamRepository.findById(teamId)).thenReturn(Optional.of(mockTeam));
        Mockito.when(userRepository.save(ArgumentMatchers.any(User.class))).thenReturn(mockUser);
        Mockito.doNothing().when(databaseService).exportDatabase();

        // When
        User result = userService.saveUser(username, null, teamId);

        // Then
        Assertions.assertNotNull(result);
        Mockito.verify(passwordEncoder, Mockito.never()).encode(ArgumentMatchers.anyString());
        Mockito.verify(userRepository).save(ArgumentMatchers.any(User.class));
        Mockito.verify(databaseService).exportDatabase();
    }

    @Test
    void testSaveUser_WithEmptyPassword_Success() throws Exception {
        // Given
        String username = "testuser";
        String emptyPassword = "";
        Long teamId = 1L;

        Mockito.when(teamRepository.findById(teamId)).thenReturn(Optional.of(mockTeam));
        Mockito.when(userRepository.save(ArgumentMatchers.any(User.class))).thenReturn(mockUser);
        Mockito.doNothing().when(databaseService).exportDatabase();

        // When
        User result = userService.saveUser(username, emptyPassword, teamId);

        // Then
        Assertions.assertNotNull(result);
        Mockito.verify(passwordEncoder, Mockito.never()).encode(ArgumentMatchers.anyString());
        Mockito.verify(userRepository).save(ArgumentMatchers.any(User.class));
        Mockito.verify(databaseService).exportDatabase();
    }

    @Test
    void testSaveUser_WithValidEmail_Success() throws Exception {
        // Given
        String emailUsername = "test@example.com";
        AuthenticationType authType = AuthenticationType.OAUTH2;

        Mockito.when(teamRepository.findByName("Default")).thenReturn(Optional.of(mockTeam));
        Mockito.when(userRepository.save(ArgumentMatchers.any(User.class))).thenReturn(mockUser);
        Mockito.doNothing().when(databaseService).exportDatabase();

        // When
        userService.saveUser(emailUsername, authType);

        // Then
        Mockito.verify(userRepository).save(ArgumentMatchers.any(User.class));
        Mockito.verify(databaseService).exportDatabase();
    }

    @Test
    void testSaveUser_WithReservedUsername_ThrowsException() throws Exception {
        // Given
        String reservedUsername = "all_users";
        AuthenticationType authType = AuthenticationType.WEB;

        // When & Then
        IllegalArgumentException exception =
                Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> userService.saveUser(reservedUsername, authType));

        Mockito.verify(userRepository, Mockito.never()).save(ArgumentMatchers.any(User.class));
        Mockito.verify(databaseService, Mockito.never()).exportDatabase();
    }

    @Test
    void testSaveUser_WithAnonymousUser_ThrowsException() throws Exception {
        // Given
        String anonymousUsername = "anonymoususer";
        AuthenticationType authType = AuthenticationType.WEB;

        // When & Then
        IllegalArgumentException exception =
                Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> userService.saveUser(anonymousUsername, authType));

        Mockito.verify(userRepository, Mockito.never()).save(ArgumentMatchers.any(User.class));
        Mockito.verify(databaseService, Mockito.never()).exportDatabase();
    }

    @Test
    void testSaveUser_DatabaseExportThrowsException_StillSavesUser() throws Exception {
        // Given
        String username = "testuser";
        String password = "password123";
        Long teamId = 1L;
        String encodedPassword = "encodedPassword123";

        Mockito.when(passwordEncoder.encode(password)).thenReturn(encodedPassword);
        Mockito.when(teamRepository.findById(teamId)).thenReturn(Optional.of(mockTeam));
        Mockito.when(userRepository.save(ArgumentMatchers.any(User.class))).thenReturn(mockUser);
        Mockito.doThrow(new SQLException("Database export failed"))
                .when(databaseService)
                .exportDatabase();

        // When & Then
        Assertions.assertThrows(
                SQLException.class, () -> userService.saveUser(username, password, teamId));

        // Verify user was still saved before the exception
        Mockito.verify(userRepository).save(ArgumentMatchers.any(User.class));
        Mockito.verify(databaseService).exportDatabase();
    }

    @Test
    void testSaveUser_WithFirstLoginFlag_Success() throws Exception {
        // Given
        String username = "testuser";
        String password = "password123";
        Long teamId = 1L;
        boolean firstLogin = true;
        boolean enabled = false;
        String encodedPassword = "encodedPassword123";

        Mockito.when(passwordEncoder.encode(password)).thenReturn(encodedPassword);
        Mockito.when(teamRepository.findById(teamId)).thenReturn(Optional.of(mockTeam));
        Mockito.when(userRepository.save(ArgumentMatchers.any(User.class))).thenReturn(mockUser);
        Mockito.doNothing().when(databaseService).exportDatabase();

        // When
        userService.saveUser(username, password, teamId, firstLogin, enabled);

        // Then
        Mockito.verify(passwordEncoder).encode(password);
        Mockito.verify(userRepository).save(ArgumentMatchers.any(User.class));
        Mockito.verify(databaseService).exportDatabase();
    }

    @Test
    void testSaveUser_WithCustomRole_Success() throws Exception {
        // Given
        String username = "testuser";
        String password = "password123";
        Long teamId = 1L;
        String customRole = Role.LIMITED_API_USER.getRoleId();
        String encodedPassword = "encodedPassword123";

        Mockito.when(passwordEncoder.encode(password)).thenReturn(encodedPassword);
        Mockito.when(teamRepository.findById(teamId)).thenReturn(Optional.of(mockTeam));
        Mockito.when(userRepository.save(ArgumentMatchers.any(User.class))).thenReturn(mockUser);
        Mockito.doNothing().when(databaseService).exportDatabase();

        // When
        userService.saveUser(username, password, teamId, customRole);

        // Then
        Mockito.verify(passwordEncoder).encode(password);
        Mockito.verify(userRepository).save(ArgumentMatchers.any(User.class));
        Mockito.verify(databaseService).exportDatabase();
    }
}

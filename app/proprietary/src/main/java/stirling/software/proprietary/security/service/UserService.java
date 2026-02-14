package stirling.software.proprietary.security.service;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.security.core.Authentication;

import stirling.software.common.model.exception.UnsupportedProviderException;
import stirling.software.common.service.UserServiceInterface;
import stirling.software.proprietary.model.Team;
import stirling.software.proprietary.security.model.AuthenticationType;
import stirling.software.proprietary.security.model.Authority;
import stirling.software.proprietary.security.model.User;

public interface UserService extends UserServiceInterface {

    void processSSOPostLogin(
            String username,
            String ssoProviderId,
            String ssoProvider,
            boolean autoCreateUser,
            AuthenticationType type)
            throws IllegalArgumentException, SQLException, UnsupportedProviderException;

    Authentication getAuthentication(String apiKey);

    User addApiKeyToUser(String username);

    User refreshApiKeyForUser(String username);

    @Override
    String getApiKeyForUser(String username);

    boolean isValidApiKey(String apiKey);

    Optional<User> getUserByApiKey(String apiKey);

    Optional<User> loadUserByApiKey(String apiKey);

    boolean validateApiKeyForUser(String username, String apiKey);

    void deleteUser(String username);

    boolean usernameExists(String username);

    boolean usernameExistsIgnoreCase(String username);

    boolean hasUsers();

    void updateUserSettings(String username, Map<String, String> updates)
            throws SQLException, UnsupportedProviderException;

    Optional<User> findByUsername(String username);

    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByUsernameIgnoreCaseWithSettings(String username);

    Authority findRole(User user);

    void changeUsername(User user, String newUsername)
            throws IllegalArgumentException, SQLException, UnsupportedProviderException;

    void changePassword(User user, String newPassword)
            throws SQLException, UnsupportedProviderException;

    void changeFirstUse(User user, boolean firstUse)
            throws SQLException, UnsupportedProviderException;

    void changeRole(User user, String newRole) throws SQLException, UnsupportedProviderException;

    void changeUserEnabled(User user, Boolean enbeled)
            throws SQLException, UnsupportedProviderException;

    void changeUserTeam(User user, Team team) throws SQLException, UnsupportedProviderException;

    boolean isPasswordCorrect(User user, String currentPassword);

    User saveUserCore(SaveUserRequest request)
            throws IllegalArgumentException, SQLException, UnsupportedProviderException;

    boolean isUsernameValid(String username);

    boolean hasPassword(String username);

    boolean isSsoAuthenticationTypeByUsername(String username);

    boolean isAuthenticationTypeByUsername(String username, AuthenticationType authenticationType);

    boolean isUserDisabled(String username);

    void invalidateUserSessions(String username);

    @Override
    String getCurrentUsername();

    @Override
    boolean isCurrentUserAdmin();

    @Override
    boolean isCurrentUserFirstLogin();

    void syncCustomApiUser(String customApiKey);

    @Override
    long getTotalUsersCount();

    List<User> getUsersWithoutTeam();

    void saveAll(List<User> users);

    long countOAuthUsers();

    long countGrandfatheredOAuthUsers();

    int grandfatherAllOAuthUsers();

    int grandfatherPendingSsoUsersWithoutSession();
}

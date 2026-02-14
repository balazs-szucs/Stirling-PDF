package stirling.software.proprietary.service;

import stirling.software.proprietary.model.UserLicenseSettings;
import stirling.software.proprietary.security.model.User;

public interface UserLicenseSettingsService {

    UserLicenseSettings getOrCreateSettings();

    void initializeGrandfatheredCount();

    void updateLicenseMaxUsers();

    void grandfatherExistingOAuthUsers();

    void validateSettingsIntegrity();

    int calculateMaxAllowedUsers();

    boolean isOAuthEligible(User user);

    boolean isSamlEligible(User user);

    boolean wouldExceedLimit(int newUsersCount);

    long getAvailableUserSlots();

    int getDisplayGrandfatheredCount();

    UserLicenseSettings getSettings();
}

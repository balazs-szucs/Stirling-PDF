package stirling.software.proprietary.security.service;

import java.sql.SQLException;

import stirling.software.common.model.exception.UnsupportedProviderException;
import stirling.software.proprietary.security.model.User;

public interface MfaService {

    String MFA_ENABLED_KEY = "mfaEnabled";
    String MFA_SECRET_KEY = "mfaSecret";
    String MFA_LAST_USED_STEP_KEY = "mfaLastUsedStep";
    String MFA_REQUIRED_KEY = "mfaRequired";

    boolean isMfaEnabled(User user);

    String getSecret(User user);

    void setSecret(User user, String secret) throws SQLException, UnsupportedProviderException;

    void enableMfa(User user) throws SQLException, UnsupportedProviderException;

    void clearPendingSecret(User user) throws SQLException, UnsupportedProviderException;

    void disableMfa(User user) throws SQLException, UnsupportedProviderException;

    boolean isTotpStepUsable(User user, long timeStep);

    boolean markTotpStepUsed(User user, long timeStep)
            throws SQLException, UnsupportedProviderException;

    boolean isMfaRequired(User user);

    void setMfaRequired(User user, boolean required)
            throws SQLException, UnsupportedProviderException;
}

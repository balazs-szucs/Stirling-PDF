package stirling.software.proprietary.security.session;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.security.core.session.SessionRegistry;

import stirling.software.proprietary.security.model.SessionEntity;

public interface SessionPersistentRegistry extends SessionRegistry {

    List<SessionEntity> getAllSessionsNotExpired();

    List<SessionEntity> getAllSessions();

    void expireSession(String sessionId);

    int getMaxInactiveInterval();

    SessionEntity getSessionEntity(String sessionId);

    void updateSessionByPrincipalName(String principalName, boolean expired, Date lastRequest);

    Optional<SessionEntity> findLatestSession(String principalName);
}

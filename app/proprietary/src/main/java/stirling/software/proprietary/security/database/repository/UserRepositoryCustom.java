package stirling.software.proprietary.security.database.repository;

import java.util.Optional;

import stirling.software.proprietary.security.model.User;

/**
 * Lookups that must go through the identifier/natural-id cache rather than a query, so a warm node
 * answers them with no SQL.
 */
public interface UserRepositoryCustom {

    /**
     * Natural-id lookup on username. Resolves through the natural-id cache and the entity cache,
     * unlike the derived {@code findByUsername} query, whose SELECT executes even when the entity
     * is cached.
     */
    Optional<User> findByUsernameNatural(String username);
}

package stirling.software.proprietary.repository;

import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import stirling.software.proprietary.model.ToolChainStat;

/** See {@link ToolChainStatRepositoryCustom}. */
class ToolChainStatRepositoryCustomImpl implements ToolChainStatRepositoryCustom {

    @PersistenceContext private EntityManager entityManager;

    @Override
    @Transactional
    public void insertCount(
            String principal, String chainKey, long epochDay, int chainLength, long delta) {
        entityManager.persist(new ToolChainStat(principal, chainKey, epochDay, chainLength, delta));
        // Flush here so a concurrent racer's primary-key violation surfaces, and the caller's catch
        // can fall back to incrementing the winner's row, instead of failing at an unrelated
        // commit.
        entityManager.flush();
        // Mirrors @Modifying(clearAutomatically = true) on the statement this replaced.
        entityManager.clear();
    }
}

package stirling.software.proprietary.repository;

import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import stirling.software.proprietary.model.ToolUsageStat;

/** See {@link ToolUsageStatRepositoryCustom}. */
class ToolUsageStatRepositoryCustomImpl implements ToolUsageStatRepositoryCustom {

    @PersistenceContext private EntityManager entityManager;

    @Override
    @Transactional
    public void insertCount(
            String principal, String fromTool, String toolKey, long epochDay, long delta) {
        entityManager.persist(new ToolUsageStat(principal, fromTool, toolKey, epochDay, delta));
        // Flush here so a concurrent racer's primary-key violation surfaces, and the caller's catch
        // can fall back to incrementing the winner's row, instead of failing at an unrelated
        // commit.
        entityManager.flush();
        // Mirrors @Modifying(clearAutomatically = true) on the statement this replaced.
        entityManager.clear();
    }
}

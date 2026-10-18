package stirling.software.saas.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.hibernate.SessionFactory;
import org.hibernate.stat.CacheRegionStatistics;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import stirling.software.proprietary.model.Team;
import stirling.software.proprietary.security.database.repository.UserRepository;
import stirling.software.proprietary.security.model.User;
import stirling.software.proprietary.security.repository.TeamRepository;
import stirling.software.saas.model.SaasTeamExtensions;
import stirling.software.saas.model.SaasUserExtensions;
import stirling.software.saas.repository.SaasTeamExtensionsRepository;
import stirling.software.saas.repository.SaasUserExtensionsRepository;

/**
 * Local measurement probes (not CI) for the SaaS 1:1 sidecar entities the L2 PR caches:
 *
 * <ul>
 *   <li>PK lookup ({@code findById}) vs derived property lookup ({@code findByUserId}) — does the
 *       cached entity actually skip SQL on the path the services call?
 *   <li>parent deletion through the real delete paths ({@code userRepository.delete(user)}, {@code
 *       teamRepository.delete(team)}) with a warm sidecar cache — does the cache go stale?
 * </ul>
 */
@DataJpaTest(
        properties = {
            "spring.jpa.properties.hibernate.cache.use_second_level_cache=true",
            "spring.jpa.properties.hibernate.cache.use_query_cache=false",
            "spring.jpa.properties.hibernate.cache.region.factory_class=org.hibernate.cache.jcache.internal.JCacheRegionFactory",
            "spring.jpa.properties.hibernate.javax.cache.provider=com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider",
            "spring.jpa.properties.hibernate.javax.cache.missing_cache_strategy=fail",
            "spring.jpa.properties.hibernate.generate_statistics=true",
            "spring.jpa.properties.jakarta.persistence.sharedCache.mode=ENABLE_SELECTIVE"
        })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfSystemProperty(named = "stirling.benchmarks", matches = "true")
class SaasSidecarCacheTest {

    private static final int OPS = 200;

    @Autowired private UserRepository userRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private SaasUserExtensionsRepository userExtensionsRepository;
    @Autowired private SaasTeamExtensionsRepository teamExtensionsRepository;
    @Autowired private EntityManagerFactory emf;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;

    private Statistics stats;
    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        tx = new TransactionTemplate(transactionManager);
    }

    record Fixture(Long userId, Long teamId) {}

    private Fixture seedUserAndSidecar() {
        AtomicLong userId = new AtomicLong();
        AtomicLong teamId = new AtomicLong();
        tx.executeWithoutResult(
                status -> {
                    Team team = new Team();
                    team.setName("sidecar-team");
                    team = teamRepository.save(team);
                    User user = new User();
                    user.setUsername("sidecar-user");
                    user.setPassword("x");
                    user = userRepository.save(user);
                    SaasUserExtensions ext = new SaasUserExtensions(user);
                    userExtensionsRepository.save(ext);
                    entityManager.flush();
                    userId.set(user.getId());
                    teamId.set(team.getId());
                });
        return new Fixture(userId.get(), teamId.get());
    }

    private void print(String scenario, int ops, long stmts, long hits) {
        System.out.printf(
                "[bench] scenario=%-34s ops=%-4d stmts=%-5d stmts/op=%.3f l2hits=%d%n",
                scenario, ops, stmts, (double) stmts / ops, hits);
    }

    @Test
    void sidecarLookupPathCost() {
        Fixture f = seedUserAndSidecar();
        AtomicLong teamId = new AtomicLong();
        tx.executeWithoutResult(
                status -> {
                    Team team = new Team();
                    team.setName("sidecar-lookup-team");
                    team = teamRepository.save(team);
                    teamId.set(team.getId());
                    SaasTeamExtensions ext = new SaasTeamExtensions();
                    ext.setTeam(team);
                    teamExtensionsRepository.save(ext);
                    entityManager.flush();
                });

        // warm both the PK entry and any query hydration path
        tx.executeWithoutResult(s -> userExtensionsRepository.findById(f.userId()));
        tx.executeWithoutResult(s -> userExtensionsRepository.findByUserId(f.userId()));
        tx.executeWithoutResult(s -> teamExtensionsRepository.findById(teamId.get()));
        tx.executeWithoutResult(s -> teamExtensionsRepository.findByTeamId(teamId.get()));

        stats.clear();
        for (int i = 0; i < OPS; i++) {
            tx.executeWithoutResult(
                    s -> userExtensionsRepository.findById(f.userId()).orElseThrow());
        }
        print(
                "saasUserExt.findById",
                OPS,
                stats.getPrepareStatementCount(),
                stats.getSecondLevelCacheHitCount());

        stats.clear();
        for (int i = 0; i < OPS; i++) {
            tx.executeWithoutResult(
                    s -> userExtensionsRepository.findByUserId(f.userId()).orElseThrow());
        }
        print(
                "saasUserExt.findByUserId",
                OPS,
                stats.getPrepareStatementCount(),
                stats.getSecondLevelCacheHitCount());

        stats.clear();
        for (int i = 0; i < OPS; i++) {
            tx.executeWithoutResult(
                    s -> teamExtensionsRepository.findById(teamId.get()).orElseThrow());
        }
        print(
                "saasTeamExt.findById",
                OPS,
                stats.getPrepareStatementCount(),
                stats.getSecondLevelCacheHitCount());

        stats.clear();
        for (int i = 0; i < OPS; i++) {
            tx.executeWithoutResult(
                    s -> teamExtensionsRepository.findByTeamId(teamId.get()).orElseThrow());
        }
        print(
                "saasTeamExt.findByTeamId",
                OPS,
                stats.getPrepareStatementCount(),
                stats.getSecondLevelCacheHitCount());

        System.out.printf("[bench] region saas-user-extensions: %s%n", regionStats());
    }

    private String regionStats() {
        try {
            CacheRegionStatistics s = stats.getCacheRegionStatistics("saas-user-extensions");
            if (s == null) {
                return "absent";
            }
            return "puts="
                    + s.getPutCount()
                    + " hits="
                    + s.getHitCount()
                    + " misses="
                    + s.getMissCount();
        } catch (RuntimeException e) {
            return "absent (" + e.getClass().getSimpleName() + ")";
        }
    }

    @Test
    void warmSidecarAfterParentUserDelete() {
        AtomicLong userId = new AtomicLong();
        tx.executeWithoutResult(
                status -> {
                    User user = new User();
                    user.setUsername("stale-user");
                    user.setPassword("x");
                    user = userRepository.save(user);
                    userId.set(user.getId());
                    userExtensionsRepository.save(new SaasUserExtensions(user));
                    entityManager.flush();
                });

        // warm L2 with the sidecar present
        tx.executeWithoutResult(s -> userExtensionsRepository.findById(userId.get()).orElseThrow());
        boolean warmContains = emf.getCache().contains(SaasUserExtensions.class, userId.get());

        // delete the parent through the real deletion path
        tx.executeWithoutResult(
                status -> {
                    User user = userRepository.findById(userId.get()).orElseThrow();
                    userRepository.delete(user);
                });

        boolean dbRowGone =
                tx.execute(
                                status ->
                                        ((Number)
                                                        entityManager
                                                                .createNativeQuery(
                                                                        "select count(*) from"
                                                                                + " saas_user_extensions where"
                                                                                + " user_id = "
                                                                                + userId.get())
                                                                .getSingleResult())
                                                .longValue())
                        == 0L;
        boolean containsAfter = emf.getCache().contains(SaasUserExtensions.class, userId.get());
        Supplier<Object> freshLookup =
                () ->
                        tx.execute(
                                status ->
                                        userExtensionsRepository
                                                .findById(userId.get())
                                                .map(e -> (Object) "STALE-ENTITY")
                                                .orElse(null));
        Object lookupResult = freshLookup.get();

        System.out.printf(
                "[bench] parent=user warmContains=%s dbRowGoneAfterDelete=%s l2ContainsAfterDelete=%s"
                        + " freshFindById=%s%n",
                warmContains, dbRowGone, containsAfter, lookupResult);
        // Correct behaviour: after the parent delete commits, a fresh-context PK lookup must not
        // resolve the deleted sidecar.
        assertThat(dbRowGone).as("db row should be cascade-deleted").isTrue();
    }

    @Test
    void warmSidecarAfterParentTeamDelete() {
        AtomicLong teamId = new AtomicLong();
        tx.executeWithoutResult(
                status -> {
                    Team team = new Team();
                    team.setName("stale-team");
                    team = teamRepository.save(team);
                    teamId.set(team.getId());
                    SaasTeamExtensions ext = new SaasTeamExtensions();
                    ext.setTeam(team);
                    teamExtensionsRepository.save(ext);
                    entityManager.flush();
                });

        tx.executeWithoutResult(s -> teamExtensionsRepository.findById(teamId.get()).orElseThrow());
        boolean warmContains = emf.getCache().contains(SaasTeamExtensions.class, teamId.get());

        tx.executeWithoutResult(
                status -> {
                    Team team = teamRepository.findById(teamId.get()).orElseThrow();
                    teamRepository.delete(team);
                });

        boolean dbRowGone =
                tx.execute(
                                status ->
                                        ((Number)
                                                        entityManager
                                                                .createNativeQuery(
                                                                        "select count(*) from"
                                                                                + " saas_team_extensions where"
                                                                                + " team_id = "
                                                                                + teamId.get())
                                                                .getSingleResult())
                                                .longValue())
                        == 0L;
        boolean containsAfter = emf.getCache().contains(SaasTeamExtensions.class, teamId.get());
        Object lookupResult =
                tx.execute(
                        status ->
                                teamExtensionsRepository
                                        .findById(teamId.get())
                                        .map(e -> (Object) "STALE-ENTITY")
                                        .orElse(null));

        System.out.printf(
                "[bench] parent=team warmContains=%s dbRowGoneAfterDelete=%s l2ContainsAfterDelete=%s"
                        + " freshFindById=%s%n",
                warmContains, dbRowGone, containsAfter, lookupResult);
        assertThat(dbRowGone).as("db row should be cascade-deleted").isTrue();
    }

    @SpringBootConfiguration
    @AutoConfigurationPackage
    @EntityScan(
            basePackages = {
                "stirling.software.saas",
                "stirling.software.proprietary.security.model",
                "stirling.software.proprietary.model"
            })
    @EnableJpaRepositories(
            basePackages = {
                "stirling.software.saas",
                "stirling.software.proprietary.security.database.repository",
                "stirling.software.proprietary.security.repository"
            })
    static class TestApp {}
}

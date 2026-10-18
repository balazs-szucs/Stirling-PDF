package stirling.software.proprietary.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.hibernate.KeyType;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.annotations.NaturalId;
import org.hibernate.stat.CacheRegionStatistics;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import stirling.software.common.model.enumeration.Role;
import stirling.software.proprietary.model.Team;
import stirling.software.proprietary.policy.store.PolicyEntity;
import stirling.software.proprietary.policy.store.PolicyRepository;
import stirling.software.proprietary.repository.ToolChainStatRepository;
import stirling.software.proprietary.repository.ToolUsageStatRepository;
import stirling.software.proprietary.security.database.repository.UserRepository;
import stirling.software.proprietary.security.model.Authority;
import stirling.software.proprietary.security.model.User;
import stirling.software.proprietary.security.repository.TeamRepository;

/**
 * Local benchmark harness (not CI): measures the SQL/ORM cost of the hot user/team/policy paths on
 * real PostgreSQL. Runs unchanged against main (no L2 annotations -> every read hits the database)
 * and against the hibernate-l2-cache branch (L2 hot). Numbers are printed as {@code [bench]} lines.
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
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfSystemProperty(named = "stirling.benchmarks", matches = "true")
class L2CacheBenchmarkTest {

    private static final int USERS = 400;
    private static final int TEAMS = 10;
    private static final int POLICIES = 20;
    private static final int OPS = 500;
    private static final int WARMUP = 100;
    private static final int TIMED_ROUNDS = 3;

    private static final AtomicBoolean SEEDED = new AtomicBoolean(false);

    @Container
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.default_batch_fetch_size", () -> "100");
    }

    @Autowired private UserRepository userRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private PolicyRepository policyRepository;
    @Autowired private ToolUsageStatRepository usageRepository;
    @Autowired private ToolChainStatRepository chainRepository;
    @Autowired private EntityManagerFactory emf;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;

    private SessionFactory sessionFactory;
    private Statistics stats;
    private TransactionTemplate tx;
    private List<Long> userIds;
    private Long teamId;

    @BeforeEach
    void setUp() {
        sessionFactory = emf.unwrap(SessionFactory.class);
        stats = sessionFactory.getStatistics();
        stats.setStatisticsEnabled(true);
        tx = new TransactionTemplate(transactionManager);
        if (SEEDED.compareAndSet(false, true)) {
            seed();
        }
        if (userIds == null) {
            userIds = userRepository.findAll().stream().map(User::getId).sorted().toList();
        }
        if (teamId == null) {
            teamId = teamRepository.findAll().get(0).getId();
        }
    }

    private void seed() {
        tx.executeWithoutResult(
                status -> {
                    List<Team> teams = new ArrayList<>();
                    for (int i = 0; i < TEAMS; i++) {
                        Team team = new Team();
                        team.setName("bench-team-" + i);
                        teams.add(team);
                    }
                    List<Team> savedTeams = teamRepository.saveAll(teams);
                    entityManager.flush();

                    List<User> users = new ArrayList<>(USERS);
                    for (int i = 0; i < USERS; i++) {
                        User user = new User();
                        user.setUsername("bench-user-" + i);
                        user.setPassword("x");
                        user.setEnabled(true);
                        user.setTeam(savedTeams.get(i % TEAMS));
                        new Authority(
                                i == 0 ? Role.ADMIN.getRoleId() : Role.USER.getRoleId(), user);
                        Map<String, String> settings = new HashMap<>();
                        settings.put("language", "en");
                        if (i % 5 == 0) {
                            settings.put("mfaSecret", "s-" + i);
                        }
                        user.setSettings(settings);
                        users.add(user);
                    }
                    userRepository.saveAll(users);
                    entityManager.flush();

                    List<PolicyEntity> policies = new ArrayList<>(POLICIES);
                    for (int i = 0; i < POLICIES; i++) {
                        PolicyEntity policy = new PolicyEntity();
                        policy.setId("bench-policy-" + i);
                        policy.setName("policy " + i);
                        policy.setEnabled(true);
                        policy.setOwner("admin");
                        policies.add(policy);
                    }
                    policyRepository.saveAll(policies);
                    entityManager.flush();
                    entityManager.clear();
                });
    }

    private boolean naturalIdMapped() {
        try {
            return User.class.getDeclaredField("username").isAnnotationPresent(NaturalId.class);
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    private boolean l2Active() {
        return stats.getSecondLevelCacheRegionNames().length > 0;
    }

    /** Warm pass, then three measured rounds; prints the median round. */
    private void bench(String scenario, int ops, Runnable op) {
        for (int i = 0; i < WARMUP; i++) {
            op.run();
        }
        double[] roundMs = new double[TIMED_ROUNDS];
        long roundStmts = 0;
        long roundHits = 0;
        long roundMisses = 0;
        long roundNatHits = 0;
        long roundLoads = 0;
        long roundCollLoads = 0;
        for (int round = 0; round < TIMED_ROUNDS; round++) {
            stats.clear();
            long t0 = System.nanoTime();
            for (int i = 0; i < ops; i++) {
                op.run();
            }
            long nanos = System.nanoTime() - t0;
            roundMs[round] = nanos / 1_000_000.0 / ops;
            if (round == 0) {
                roundStmts = stats.getPrepareStatementCount();
                roundHits = stats.getSecondLevelCacheHitCount();
                roundMisses = stats.getSecondLevelCacheMissCount();
                roundNatHits = stats.getNaturalIdCacheHitCount();
                roundLoads = stats.getEntityLoadCount();
                roundCollLoads = stats.getCollectionLoadCount();
            }
        }
        java.util.Arrays.sort(roundMs);
        double median = roundMs[TIMED_ROUNDS / 2];
        System.out.printf(
                "[bench] scenario=%-26s ops=%-4d stmts=%-5d stmts/op=%.3f l2active=%-5s l2hits=%-5d"
                        + " l2miss=%-5d natHits=%-5d entityLoads=%-5d collLoads=%-5d"
                        + " ms/op.med=%.4f ms/op.min=%.4f%n",
                scenario,
                ops,
                roundStmts,
                (double) roundStmts / ops,
                l2Active(),
                roundHits,
                roundMisses,
                roundNatHits,
                roundLoads,
                roundCollLoads,
                median,
                roundMs[0]);
    }

    private void printRegions() {
        for (String region : new String[] {"users", "usersNaturalId", "teams", "policies"}) {
            try {
                if (!List.of(stats.getSecondLevelCacheRegionNames()).contains(region)) {
                    continue;
                }
                CacheRegionStatistics s = stats.getCacheRegionStatistics(region);
                if (s != null) {
                    System.out.printf(
                            "[bench]   region=%-16s puts=%-5d hits=%-5d misses=%-5d%n",
                            region, s.getPutCount(), s.getHitCount(), s.getMissCount());
                }
            } catch (RuntimeException ignored) {
                // region not configured on this revision
            }
        }
    }

    @Test
    void repeatedUserByIdLookup() {
        System.out.println(
                "[bench] === entity lookup by PK: user (authorities + settings EAGER) ===");
        long id = userIds.get(11);
        bench(
                "user.findById.sameId",
                OPS,
                () ->
                        tx.executeWithoutResult(
                                status -> {
                                    User u = userRepository.findById(id).orElseThrow();
                                    assertThat(u.getUsername()).startsWith("bench-user-");
                                }));
        printRegions();
    }

    @Test
    void repeatedUsernameQueryLookup() {
        System.out.println(
                "[bench] === derived query findByUsername (SELECT runs, query cache off) ===");
        bench(
                "user.findByUsername",
                OPS,
                () ->
                        tx.executeWithoutResult(
                                status -> {
                                    User u =
                                            userRepository
                                                    .findByUsername("bench-user-123")
                                                    .orElseThrow();
                                    assertThat(u.getUsername()).isEqualTo("bench-user-123");
                                }));
        printRegions();
    }

    @Test
    void repeatedNaturalIdLookup() {
        System.out.println(
                "[bench] === natural-id lookup on username (branch: UserService.findByUsername) ===");
        if (!naturalIdMapped()) {
            System.out.println(
                    "[bench] naturalIdLookup SKIPPED: User.username has no @NaturalId on this revision");
            return;
        }
        bench(
                "user.naturalId.username",
                OPS,
                () ->
                        tx.executeWithoutResult(
                                status -> {
                                    Session session = entityManager.unwrap(Session.class);
                                    User u =
                                            session.find(
                                                    User.class, "bench-user-123", KeyType.NATURAL);
                                    assertThat(u).isNotNull();
                                }));
        printRegions();
    }

    @Test
    void repeatedNaturalIdLookupViaRepository() {
        System.out.println(
                "[bench] === natural-id lookup via UserRepositoryCustom (CustomUserDetailsService"
                        + " login/JWT path) ===");
        if (!naturalIdMapped()) {
            System.out.println("[bench] repository natural-id SKIPPED: no @NaturalId mapping");
            return;
        }
        bench(
                "userRepo.findByUsernameNatural",
                OPS,
                () ->
                        tx.executeWithoutResult(
                                status -> {
                                    User u =
                                            userRepository
                                                    .findByUsernameNatural("bench-user-123")
                                                    .orElseThrow();
                                    assertThat(u.getUsername()).isEqualTo("bench-user-123");
                                }));
        printRegions();
    }

    @Test
    void repeatedTeamByIdLookup() {
        System.out.println("[bench] === entity lookup by PK: team ===");
        long id = teamId;
        bench(
                "team.findById.sameId",
                OPS,
                () ->
                        tx.executeWithoutResult(
                                status -> {
                                    Team t = teamRepository.findById(id).orElseThrow();
                                    assertThat(t.getName()).startsWith("bench-team-");
                                }));
        printRegions();
    }

    @Test
    void repeatedPolicyByIdLookup() {
        System.out.println("[bench] === entity lookup by PK: policy ===");
        bench(
                "policy.findById.sameId",
                OPS,
                () ->
                        tx.executeWithoutResult(
                                status -> {
                                    PolicyEntity p =
                                            policyRepository
                                                    .findById("bench-policy-3")
                                                    .orElseThrow();
                                    assertThat(p.getName()).isEqualTo("policy 3");
                                }));
        printRegions();
    }

    @Test
    void mixedReadsAndWrites() {
        System.out.println(
                "[bench] === mixed workload: 90% user reads by PK + 10% user updates (write then re-read) ===");
        tx.executeWithoutResult(status -> userRepository.findById(userIds.get(7)).orElseThrow());

        stats.clear();
        long t0 = System.nanoTime();
        for (int i = 0; i < OPS; i++) {
            int idx = i % 40;
            if (i % 10 == 9) {
                String email = "bench-" + i + "@example.com";
                tx.executeWithoutResult(
                        status -> {
                            User u = userRepository.findById(userIds.get(idx)).orElseThrow();
                            u.setEmail(email);
                            userRepository.save(u);
                        });
                // The write evicts this user from L2; the immediate re-read must both observe the
                // write and pay the (re)load cost. A cached stale read here would be a bug.
                tx.executeWithoutResult(
                        status -> {
                            User u = userRepository.findById(userIds.get(idx)).orElseThrow();
                            assertThat(u.getEmail()).isEqualTo(email);
                        });
            } else {
                tx.executeWithoutResult(
                        status -> {
                            User u = userRepository.findById(userIds.get(idx)).orElseThrow();
                            assertThat(u.getUsername()).startsWith("bench-user-");
                        });
            }
        }
        long nanos = System.nanoTime() - t0;
        System.out.printf(
                "[bench] scenario=%-26s ops=%-4d stmts=%-5d stmts/op=%.3f l2active=%-5s l2hits=%-5d"
                        + " l2miss=%-5d entityLoads=%-5d updates=%-4d ms/op=%.4f%n",
                "mixed.90r10w",
                OPS,
                stats.getPrepareStatementCount(),
                (double) stats.getPrepareStatementCount() / OPS,
                l2Active(),
                stats.getSecondLevelCacheHitCount(),
                stats.getSecondLevelCacheMissCount(),
                stats.getEntityLoadCount(),
                stats.getEntityUpdateCount(),
                nanos / 1_000_000.0 / OPS);
    }

    @Test
    void cacheSurvivalUnderWrites() {
        System.out.println(
                "[bench] === cache survival: warm user/team/policy, run one writer, re-read ===");
        long day = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toEpochDay();
        record Mutation(String name, Runnable body) {}
        List<Mutation> mutations =
                List.of(
                        new Mutation(
                                "read.nativeSelectControl",
                                () ->
                                        tx.executeWithoutResult(
                                                s ->
                                                        entityManager
                                                                .createNativeQuery("select 1")
                                                                .getSingleResult())),
                        new Mutation(
                                "native.auditInsert",
                                () ->
                                        tx.executeWithoutResult(
                                                s ->
                                                        entityManager
                                                                .createNativeQuery(
                                                                        "insert into audit_events"
                                                                                + " (timestamp, principal,"
                                                                                + " type, data, source) values"
                                                                                + " (CURRENT_TIMESTAMP, 'bench',"
                                                                                + " 'bench-type', 'bench-data',"
                                                                                + " 'bench')")
                                                                .executeUpdate())),
                        new Mutation(
                                "orm.toolUsageInsert",
                                () ->
                                        tx.executeWithoutResult(
                                                s ->
                                                        usageRepository.insertCount(
                                                                "bench-user-0",
                                                                "compare",
                                                                "ocr",
                                                                day,
                                                                1))),
                        new Mutation(
                                "native.toolUsageInsertSynced",
                                () ->
                                        tx.executeWithoutResult(
                                                s ->
                                                        entityManager
                                                                .createNativeQuery(
                                                                        "INSERT INTO"
                                                                                + " tool_usage_stats"
                                                                                + " (principal, from_tool,"
                                                                                + " tool_key, epoch_day, count)"
                                                                                + " VALUES"
                                                                                + " ('bench-user-synced','compare','ocr',"
                                                                                + day
                                                                                + ", 1)")
                                                                .unwrap(
                                                                        org.hibernate.query
                                                                                .NativeQuery.class)
                                                                .addSynchronizedQuerySpace(
                                                                        "tool_usage_stats")
                                                                .executeUpdate())),
                        new Mutation(
                                "orm.toolChainInsert",
                                () ->
                                        tx.executeWithoutResult(
                                                s ->
                                                        chainRepository.insertCount(
                                                                "bench-user-0",
                                                                "compare>ocr",
                                                                day,
                                                                2,
                                                                1))),
                        new Mutation(
                                "jpql.toolUsageIncrement",
                                () ->
                                        tx.executeWithoutResult(
                                                s ->
                                                        usageRepository.incrementCount(
                                                                "bench-user-0",
                                                                "compare",
                                                                "ocr",
                                                                day,
                                                                1))),
                        new Mutation(
                                "orm.mfaDeleteSettings",
                                () ->
                                        tx.executeWithoutResult(
                                                s -> {
                                                    User u =
                                                            userRepository
                                                                    .findByIdWithSettings(
                                                                            userIds.get(0))
                                                                    .orElseThrow();
                                                    u.getSettings().remove("language");
                                                    userRepository.flush();
                                                })),
                        new Mutation(
                                "jpql.userTeamMove",
                                () ->
                                        tx.executeWithoutResult(
                                                s ->
                                                        userRepository.updateUserTeamId(
                                                                userIds.get(1), teamId))));

        for (Mutation mutation : mutations) {
            warmReads();
            long beforeUser =
                    readStmts(() -> userRepository.findById(userIds.get(0)).orElseThrow());
            long beforeTeam = readStmts(() -> teamRepository.findById(teamId).orElseThrow());
            long beforePolicy =
                    readStmts(() -> policyRepository.findById("bench-policy-0").orElseThrow());
            String containsBefore = containsFlags();
            mutation.body().run();
            String containsAfter = containsFlags();
            long afterUser = readStmts(() -> userRepository.findById(userIds.get(0)).orElseThrow());
            long afterTeam = readStmts(() -> teamRepository.findById(teamId).orElseThrow());
            long afterPolicy =
                    readStmts(() -> policyRepository.findById("bench-policy-0").orElseThrow());
            System.out.printf(
                    "[bench] mutation=%-26s stmtsBefore(user/team/policy)=%d/%d/%d"
                            + " containsBefore=%s containsAfter=%s stmtsAfter=%d/%d/%d%n",
                    mutation.name(),
                    beforeUser,
                    beforeTeam,
                    beforePolicy,
                    containsBefore,
                    containsAfter,
                    afterUser,
                    afterTeam,
                    afterPolicy);
            if ("orm.mfaDeleteSettings".equals(mutation.name())) {
                Boolean[] languagePresent = new Boolean[1];
                tx.executeWithoutResult(
                        status -> {
                            User u = userRepository.findById(userIds.get(0)).orElseThrow();
                            languagePresent[0] = u.getSettings().containsKey("language");
                        });
                System.out.printf(
                        "[bench] settingsAfterDelete languagePresent=%s (expected false: collection"
                                + " invalidation must follow the narrowed space)%n",
                        languagePresent[0]);
                assertThat(languagePresent[0])
                        .as("deleted setting must not be served from the cached collection")
                        .isFalse();
            }
        }
    }

    private String containsFlags() {
        boolean u = emf.getCache().contains(User.class, userIds.get(0));
        boolean t = emf.getCache().contains(Team.class, teamId);
        boolean p = emf.getCache().contains(PolicyEntity.class, "bench-policy-0");
        return "u=" + (u ? "Y" : "n") + " t=" + (t ? "Y" : "n") + " p=" + (p ? "Y" : "n");
    }

    private void warmReads() {
        tx.executeWithoutResult(status -> userRepository.findById(userIds.get(0)).orElseThrow());
        tx.executeWithoutResult(status -> userRepository.findById(userIds.get(0)).orElseThrow());
        tx.executeWithoutResult(status -> teamRepository.findById(teamId).orElseThrow());
        tx.executeWithoutResult(status -> teamRepository.findById(teamId).orElseThrow());
        tx.executeWithoutResult(
                status -> policyRepository.findById("bench-policy-0").orElseThrow());
        tx.executeWithoutResult(
                status -> policyRepository.findById("bench-policy-0").orElseThrow());
    }

    private long readStmts(Runnable read) {
        stats.clear();
        read.run();
        return stats.getPrepareStatementCount();
    }

    @Test
    void naturalIdAfterRename() {
        System.out.println("[bench] === natural-id after rename (mutable @NaturalId username) ===");
        if (!naturalIdMapped()) {
            System.out.println("[bench] naturalIdAfterRename SKIPPED: no @NaturalId mapping");
            return;
        }
        String stamp = Long.toString(System.nanoTime() % 1_000_000);
        String oldName = "rename-old-" + stamp;
        String newName = "rename-new-" + stamp;
        java.util.concurrent.atomic.AtomicLong idRef = new java.util.concurrent.atomic.AtomicLong();
        tx.executeWithoutResult(
                status -> {
                    User user = new User();
                    user.setUsername(oldName);
                    user.setPassword("x");
                    idRef.set(userRepository.save(user).getId());
                });

        // warm natural-id + entity caches under the old name
        tx.executeWithoutResult(
                status -> {
                    Session session = entityManager.unwrap(Session.class);
                    assertThat(session.find(User.class, oldName, KeyType.NATURAL)).isNotNull();
                });

        // rename: exactly what UserService.changeUsername persists (setUsername + save)
        tx.executeWithoutResult(
                status -> {
                    User user = userRepository.findById(idRef.get()).orElseThrow();
                    user.setUsername(newName);
                    userRepository.save(user);
                });

        String oldLookup =
                tx.execute(
                        status -> {
                            Session session = entityManager.unwrap(Session.class);
                            User u = session.find(User.class, oldName, KeyType.NATURAL);
                            return u == null ? "null" : "resolved:" + u.getUsername();
                        });
        String newLookup =
                tx.execute(
                        status -> {
                            Session session = entityManager.unwrap(Session.class);
                            User u = session.find(User.class, newName, KeyType.NATURAL);
                            return u == null ? "null" : "id=" + u.getId();
                        });

        // a different user claims the old name; the old name must resolve to them now
        java.util.concurrent.atomic.AtomicLong claimedId =
                new java.util.concurrent.atomic.AtomicLong();
        tx.executeWithoutResult(
                status -> {
                    User claimer = new User();
                    claimer.setUsername(oldName);
                    claimer.setPassword("x");
                    claimedId.set(userRepository.save(claimer).getId());
                });
        String reclaimedLookup =
                tx.execute(
                        status -> {
                            Session session = entityManager.unwrap(Session.class);
                            User u = session.find(User.class, oldName, KeyType.NATURAL);
                            return u == null ? "null" : "id=" + u.getId();
                        });

        System.out.printf(
                "[bench] rename: oldLookup=%s newLookup=%s (expected id=%d) reclaimedOldName=%s"
                        + " (expected id=%d) natHits=%d natMiss=%d%n",
                oldLookup,
                newLookup,
                idRef.get(),
                reclaimedLookup,
                claimedId.get(),
                stats.getNaturalIdCacheHitCount(),
                stats.getNaturalIdCacheMissCount());
    }

    @SpringBootConfiguration
    @EntityScan(
            basePackages = {
                "stirling.software.proprietary.security.model",
                "stirling.software.proprietary.model",
                "stirling.software.proprietary.policy.store"
            })
    @EnableJpaRepositories(
            basePackages = {
                "stirling.software.proprietary.security.database.repository",
                "stirling.software.proprietary.security.repository",
                "stirling.software.proprietary.policy.store",
                "stirling.software.proprietary.repository"
            })
    static class TestApp {}
}

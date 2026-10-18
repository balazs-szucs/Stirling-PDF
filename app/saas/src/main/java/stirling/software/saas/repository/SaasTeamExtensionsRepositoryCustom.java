package stirling.software.saas.repository;

/**
 * The atomic seat claim. Kept as declared SQL because the predicate spans three tables ({@code
 * saas_team_extensions}, {@code linked_instance}, {@code procurement_deal}), mixes {@code
 * GREATEST}/aggregate/EXISTS with database-timezone timestamp comparisons, and must stay a single
 * conditional UPDATE: returning 0 rows is the capacity signal, and no row lock is taken. An ORM
 * version would have to be a locked read-modify-write, changing the concurrency profile.
 *
 * <p>The statement declares its physical table so the commit scopes cache invalidation to {@code
 * saas_team_extensions} instead of discarding every region.
 */
public interface SaasTeamExtensionsRepositoryCustom {

    /**
     * The seat-claim SQL, exposed so tests can execute the exact statement the repository runs
     * against a standalone database without booting Hibernate.
     */
    String INCREMENT_SEATS_SQL =
            "UPDATE stirling_pdf.saas_team_extensions e SET seats_used = seats_used + 1 WHERE"
                    + " team_id = :teamId AND ("
                    + SaasTeamExtensionsRepository.FLEET_SEATS
                    + " < max_seats OR "
                    + SaasTeamExtensionsRepository.ENTERPRISE_ACCESS
                    + ")";

    int incrementSeatsUsed(Long teamId);
}

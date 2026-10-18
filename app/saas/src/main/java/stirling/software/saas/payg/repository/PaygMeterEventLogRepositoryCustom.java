package stirling.software.saas.payg.repository;

import java.util.UUID;

/**
 * The idempotent meter-log insert. Kept as declared SQL because no ORM equivalent preserves its
 * semantics: {@code ON CONFLICT (idempotency_key) DO NOTHING} makes the two meter triggers and
 * reconcile retries collapse to exactly one row without an exception, while an ORM insert would
 * surface every duplicate as a constraint violation and abort the enclosing batch transaction.
 *
 * <p>The statement declares its physical table so the commit scopes cache invalidation to {@code
 * payg_meter_event_log} instead of discarding every region - a billing insert must not clear
 * unrelated cached users and teams.
 */
public interface PaygMeterEventLogRepositoryCustom {

    void insertPending(Long teamId, UUID jobId, String key, int units);
}

package stirling.software.proprietary.repository;

/**
 * The day's-first tool-usage insert. Kept as a fragment (rather than a plain {@code save()}) so a
 * concurrent first-run-of-day loses the primary key race instead of silently overwriting the
 * winner: {@code save()} merges an entity with a non-null assigned id, which would reset the
 * winner's count.
 */
public interface ToolUsageStatRepositoryCustom {

    void insertCount(String principal, String fromTool, String toolKey, long epochDay, long delta);
}

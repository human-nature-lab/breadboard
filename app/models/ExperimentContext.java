package models;

import java.io.File;

/**
 * Immutable per-engine experiment/instance info, bound into the gremlin-groovy engine as
 * {@code experimentContext}. It lets the (pure-groovy) DSL read the experiment/instance identifiers
 * and locate per-experiment files without reaching back into Play/Java itself -- which also keeps
 * the groovy side runnable under the Play-free test harness.
 *
 * <p>Its current consumer is the group-id sequence in {@code groups.groovy}, which uses
 * {@link #dataDir} to persist a per-experiment monotonic counter so group ids stay unique across
 * reloads and instances. A {@code null} {@code dataDir} means "no experiment-scoped storage" (the
 * test harness, or an engine started without an experiment), in which case the sequence runs purely
 * in memory.
 */
public final class ExperimentContext {

    public final Long experimentId;
    public final Long instanceId;
    /** Per-experiment writable directory (for files like the group-id sequence); null when none. */
    public final File dataDir;

    public ExperimentContext(Long experimentId, Long instanceId, File dataDir) {
        this.experimentId = experimentId;
        this.instanceId = instanceId;
        this.dataDir = dataDir;
    }

    public Long getExperimentId() { return experimentId; }
    public Long getInstanceId() { return instanceId; }
    public File getDataDir() { return dataDir; }
}

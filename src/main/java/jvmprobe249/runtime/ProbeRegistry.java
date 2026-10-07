package jvmprobe249.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import jvmprobe249.MethodId;
import jvmprobe249.MethodStats;

/**
 * Holds the per-method accumulators. A single monitor guards every update and
 * every snapshot/reset so snapshots are globally consistent and no update is
 * ever lost under concurrent readers and writers.
 */
public final class ProbeRegistry {

    private final Map<MethodId, MethodAccumulator> accumulators = new LinkedHashMap<>();

    MethodAccumulator accumulatorFor(MethodId id) {
        synchronized (this) {
            return accumulators.computeIfAbsent(id, MethodAccumulator::new);
        }
    }

    void record(MethodAccumulator accumulator, long inclusiveNanos,
                long selfNanos, boolean exceptional) {
        synchronized (this) {
            accumulator.record(inclusiveNanos, selfNanos, exceptional);
        }
    }

    /** Returns an immutable, point-in-time consistent snapshot. */
    public Map<MethodId, MethodStats> snapshot() {
        synchronized (this) {
            Map<MethodId, MethodStats> copy = new LinkedHashMap<>();
            for (MethodAccumulator accumulator : accumulators.values()) {
                copy.put(accumulator.id, accumulator.snapshot());
            }
            return Collections.unmodifiableMap(copy);
        }
    }

    /** Clears all statistics; legal only when no call is in flight. */
    public void reset(int inFlightCount) {
        synchronized (this) {
            if (inFlightCount != 0) {
                throw new IllegalStateException(
                        "cannot reset profiling data while " + inFlightCount
                                + " call(s) are in flight");
            }
            accumulators.clear();
        }
    }
}

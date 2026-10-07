package jvmprobe249.runtime;

import jvmprobe249.MethodId;
import jvmprobe249.MethodStats;

/**
 * Mutable counters for one (class, method, descriptor) triple.
 * All mutation and snapshotting happens while holding the registry lock.
 */
final class MethodAccumulator {

    final MethodId id;

    long completed;
    long abnormal;
    long totalInclusiveNanos;
    long totalSelfNanos;
    long maxInclusiveNanos;

    MethodAccumulator(MethodId id) {
        this.id = id;
    }

    void record(long inclusiveNanos, long selfNanos, boolean exceptional) {
        completed++;
        if (exceptional) {
            abnormal++;
        }
        totalInclusiveNanos += inclusiveNanos;
        totalSelfNanos += selfNanos;
        if (inclusiveNanos > maxInclusiveNanos) {
            maxInclusiveNanos = inclusiveNanos;
        }
    }

    MethodStats snapshot() {
        return new MethodStats(completed, abnormal, totalInclusiveNanos,
                totalSelfNanos, maxInclusiveNanos);
    }
}

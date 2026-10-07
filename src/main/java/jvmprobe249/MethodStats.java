package jvmprobe249;

/**
 * Immutable statistics for one method at the moment a snapshot was taken:
 * completed (exited) invocations, abnormal exits, total inclusive time,
 * total self time and the maximum inclusive time. Times are nanoseconds
 * measured with {@link System#nanoTime()}.
 */
public record MethodStats(long completedCount,
                          long abnormalCount,
                          long totalInclusiveNanos,
                          long totalSelfNanos,
                          long maxInclusiveNanos) {
}

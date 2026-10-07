package jvmprobe249.demo;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jvmprobe249.MethodId;
import jvmprobe249.MethodStats;
import jvmprobe249.Probe;

/** Loads transformed sample classes and demonstrates recursion, exceptions and threads. */
public final class DemoMain {

    public static void main(String[] args) throws Exception {
        Set<String> targets = Set.of("jvmprobe249.demo.SampleMethods");
        TransformedLoader loader = new TransformedLoader(DemoMain.class.getClassLoader(), targets);
        Class<?> sampleClass = loader.loadClass("jvmprobe249.demo.SampleMethods");
        jvmprobe249.demo.SampleApi sample = (jvmprobe249.demo.SampleApi)
                sampleClass.getConstructor().newInstance();

        sample.fib(10);
        sample.overload(7);
        sample.overload("hi");
        sampleClass.getMethod("staticSum", int[].class)
                .invoke(null, (Object) new int[]{1, 2, 3, 4});
        sampleClass.getMethod("asDouble", double.class).invoke(sample, 3.0d);
        sampleClass.getMethod("maybeNull", boolean.class).invoke(sample, true);
        sampleClass.getMethod("waitFor", long.class).invoke(sample, 5L);
        sampleClass.getMethod("parentWork", long.class).invoke(sample, 5L);

        try {
            sampleClass.getMethod("throwAlways").invoke(sample);
        } catch (java.lang.reflect.InvocationTargetException e) {
            System.out.println("propagated: " + e.getCause());
        }
        Object caught = sampleClass.getMethod("catchInternally").invoke(sample);
        System.out.println("internally caught -> " + caught);

        List<Thread> threads = new java.util.ArrayList<>();
        for (int t = 0; t < 4; t++) {
            final int depth = 8 + t;
            Thread thread = new Thread(() -> {
                sample.fib(depth);
                sample.syncCounter(1000);
            });
            threads.add(thread);
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }

        System.out.println("in-flight after work: " + Probe.inFlightCount());
        System.out.printf("%-52s %8s %8s %12s %12s %12s%n",
                "method", "done", "error", "inclNs", "selfNs", "maxInclNs");
        Probe.snapshot().entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getKey().toString()))
                .forEach(DemoMain::printRow);
    }

    private static void printRow(Map.Entry<MethodId, MethodStats> entry) {
        MethodId id = entry.getKey();
        MethodStats s = entry.getValue();
        System.out.printf("%-52s %8d %8d %12d %12d %12d%n",
                id.methodName() + id.descriptor(),
                s.completedCount(), s.abnormalCount(),
                s.totalInclusiveNanos(), s.totalSelfNanos(), s.maxInclusiveNanos());
    }

    private DemoMain() {
    }
}

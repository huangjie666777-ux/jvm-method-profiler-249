package jvmprobe249;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import jvmprobe249.demo.SampleApi;
import jvmprobe249.demo.TransformedLoader;
import jvmprobe249.transform.ClassTransformException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ProbeSdkTest {

    private static final String SAMPLE = "jvmprobe249.demo.SampleMethods";
    private static final String SAMPLE_INTERNAL = "jvmprobe249/demo/SampleMethods";

    @AfterEach
    void clearStats() {
        Probe.reset();
    }

    private byte[] sampleBytes() throws IOException {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream(SAMPLE.replace('.', '/') + ".class")) {
            assertNotNull(in);
            return in.readAllBytes();
        }
    }

    private SampleApi loadSample() throws Exception {
        TransformedLoader loader = new TransformedLoader(
                getClass().getClassLoader(), Set.of(SAMPLE));
        Class<?> type = loader.loadClass(SAMPLE);
        return (SampleApi) type.getConstructor().newInstance();
    }

    private MethodStats stats(String method, String descriptor) {
        MethodStats stats = Probe.snapshot().get(new MethodId(SAMPLE_INTERNAL, method, descriptor));
        assertNotNull(stats);
        return stats;
    }

    @Test
    void transformedClassPassesVerificationAndRuns() throws Exception {
        byte[] bytes = Probe.transform(sampleBytes(), getClass().getClassLoader());
        assertNotNull(bytes);
        SampleApi sample = loadSample();
        assertEquals(1, sample.constructedValue());
        assertEquals(55L, sample.fib(10));
        assertEquals(8, sample.overload(7));
        assertEquals("hi!", sample.overload("hi"));
        assertEquals(10L, jvmprobe249.demo.SampleMethods.staticSum(new int[]{1, 2, 3, 4}));
        assertEquals(7.5d, sample.asDouble(3.0d), 0.0001d);
        assertNull(sample.maybeNull(true));
        assertEquals("object", sample.maybeNull(false));
    }

    @Test
    void recursionSettlesEveryLevel() throws Exception {
        SampleApi sample = loadSample();
        assertEquals(21L, sample.fib(8));
        MethodStats stats = stats("fib", "(I)J");
        assertEquals(67, stats.completedCount());
        assertEquals(0, stats.abnormalCount());
        assertTrue(stats.totalInclusiveNanos() >= stats.totalSelfNanos());
        assertTrue(stats.maxInclusiveNanos() >= 0);
    }

    @Test
    void overloadedMethodsAreKeyedByDescriptor() throws Exception {
        SampleApi sample = loadSample();
        sample.overload(1);
        sample.overload(1);
        sample.overload("x");
        assertEquals(2, stats("overload", "(I)I").completedCount());
        assertEquals(1, stats("overload", "(Ljava/lang/String;)Ljava/lang/String;")
                .completedCount());
    }

    @Test
    void constructorsAreNotProbed() throws Exception {
        loadSample();
        assertFalse(Probe.snapshot().containsKey(
                new MethodId(SAMPLE_INTERNAL, "<init>", "()V")));
    }

    @Test
    void propagatedAndCaughtExceptionsAreClassifiedCorrectly() throws Exception {
        SampleApi sample = loadSample();
        IllegalStateException error = assertThrows(IllegalStateException.class, sample::throwAlways);
        assertEquals("boom", error.getMessage());
        assertEquals("caught:boom", sample.catchInternally());

        MethodStats throwing = stats("throwAlways", "()V");
        assertEquals(2, throwing.completedCount());
        assertEquals(2, throwing.abnormalCount());

        MethodStats catching = stats("catchInternally", "()Ljava/lang/String;");
        assertEquals(1, catching.completedCount());
        assertEquals(0, catching.abnormalCount());
    }

    @Test
    void selfTimeExcludesOnlyDirectInstrumentedChildren() throws Exception {
        SampleApi sample = loadSample();
        sample.parentWork(3L);
        MethodStats parent = stats("parentWork", "(J)J");
        MethodStats child = stats("waitFor", "(J)J");
        assertEquals(1, parent.completedCount());
        assertEquals(2, child.completedCount());
        long expectedSelf = parent.totalInclusiveNanos() - child.totalInclusiveNanos();
        assertEquals(expectedSelf, parent.totalSelfNanos());
    }

    @Test
    void waitTimeIsIncluded() throws Exception {
        SampleApi sample = loadSample();
        sample.waitFor(20);
        assertTrue(stats("waitFor", "(J)J").totalInclusiveNanos() >= 18_000L);
    }

    @Test
    void synchronizedMethodsKeepWorking() throws Exception {
        SampleApi sample = loadSample();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit(() -> {
                    for (int j = 0; j < 200; j++) {
                        assertEquals(499500, sample.syncCounter(1000));
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1600, stats("syncCounter", "(I)I").completedCount());
        assertEquals(0, Probe.inFlightCount());
    }

    @Test
    void concurrentThreadsDoNotLoseCountsOrMixStacks() throws Exception {
        SampleApi sample = loadSample();
        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new java.util.ArrayList<>();
        for (int t = 0; t < threads; t++) {
            futures.add(pool.submit(() -> {
                start.await();
                for (int i = 0; i < 50; i++) {
                    sample.fib(10);
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertEquals(threads * 50L * 177L, stats("fib", "(I)J").completedCount());
        assertEquals(0, Probe.inFlightCount());
    }

    @Test
    void resetRejectedWhileCallInFlightAndAllowedAfterwards() throws Exception {
        SampleApi sample = loadSample();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread worker = new Thread(() -> {
            entered.countDown();
            sample.blocking(release);
        });
        worker.start();
        entered.await();
        Thread.sleep(20);
        assertTrue(Probe.inFlightCount() >= 1);
        assertThrows(IllegalStateException.class, Probe::reset);
        release.countDown();
        worker.join();
        Probe.reset();
        assertTrue(Probe.snapshot().isEmpty());
    }

    @Test
    void repeatedTransformIsIdempotent() throws Exception {
        byte[] original = sampleBytes();
        byte[] once = Probe.transform(original, getClass().getClassLoader());
        byte[] twice = Probe.transform(once, getClass().getClassLoader());
        assertArrayEquals(once, twice);

        SampleApi sample = loadSample();
        sample.fib(3);
        assertEquals(5, stats("fib", "(I)J").completedCount());
    }

    @Test
    void invalidAndUnsupportedClassesAreRejected() {
        assertThrows(ClassTransformException.class,
                () -> Probe.transform(new byte[]{1, 2, 3}, getClass().getClassLoader()));
        assertThrows(ClassTransformException.class,
                () -> Probe.transform(new byte[0], getClass().getClassLoader()));

        byte[] interfaceBytes = compileSource(
                "Marker.java",
                "package jvmprobe249.demo; interface Marker {}\n");
        ClassTransformException error = assertThrows(ClassTransformException.class,
                () -> Probe.transform(interfaceBytes, getClass().getClassLoader()));
        assertTrue(error.getMessage().contains("interface"));
    }

    @Test
    void abstractClassWithAbstractAndNativeMethodsTransformsOnlyConcreteMethods() {
        byte[] bytes = compileSource(
                "AbstractTarget.java",
                "package jvmprobe249.demo; abstract class AbstractTarget {\n"
                        + "    abstract int abstractMethod();\n"
                        + "    native int nativeMethod();\n"
                        + "    int concrete() { return 42; }\n"
                        + "}\n");
        byte[] transformed = Probe.transform(bytes, getClass().getClassLoader());
        assertNotNull(transformed);
        java.util.concurrent.atomic.AtomicInteger concrete =
                new java.util.concurrent.atomic.AtomicInteger();
        new org.objectweb.asm.ClassReader(transformed).accept(
                new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
                    @Override
                    public org.objectweb.asm.MethodVisitor visitMethod(
                            int access, String name, String descriptor,
                            String signature, String[] exceptions) {
                        if ("concrete".equals(name)) {
                            concrete.incrementAndGet();
                        }
                        return null;
                    }
                }, 0);
        assertEquals(1, concrete.get());
    }

    @Test
    void sdkClassesArePassedThroughUnchanged() throws Exception {
        byte[] runtimeBytes;
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("jvmprobe249/Probe.class")) {
            assertNotNull(in);
            runtimeBytes = in.readAllBytes();
        }
        assertArrayEquals(runtimeBytes,
                Probe.transform(runtimeBytes, getClass().getClassLoader()));
    }

    private byte[] compileSource(String simpleName, String source) {
        try {
            Path dir = Files.createTempDirectory("probe-compile");
            Path file = dir.resolve("jvmprobe249/demo/" + simpleName);
            Files.createDirectories(file.getParent());
            Files.writeString(file, source);
            javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
            int result = compiler.run(null, null, null, "-d", dir.toString(), file.toString());
            assertEquals(0, result);
            Path classFile = dir.resolve("jvmprobe249/demo/"
                    + simpleName.substring(0, simpleName.length() - 5) + ".class");
            return Files.readAllBytes(classFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

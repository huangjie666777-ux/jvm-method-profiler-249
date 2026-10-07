package jvmprobe249.demo;

import java.util.concurrent.CountDownLatch;

/** Plain demo/target class; it never references the profiling API itself. */
public class SampleMethods implements SampleApi {

    private int constructed;

    public SampleMethods() {
        this.constructed = 1;
    }

    public int constructedValue() {
        return constructed;
    }

    public long fib(int n) {
        if (n < 2) {
            return n;
        }
        return fib(n - 1) + fib(n - 2);
    }

    public int overload(int value) {
        return value + 1;
    }

    public String overload(String value) {
        return value + "!";
    }

    public static long staticSum(int[] values) {
        long sum = 0;
        for (int value : values) {
            sum += value;
        }
        return sum;
    }

    public double asDouble(double value) {
        return value * 2.5;
    }

    public Object maybeNull(boolean returnNull) {
        return returnNull ? null : "object";
    }

    public long waitFor(long millis) throws InterruptedException {
        long start = System.nanoTime();
        Thread.sleep(millis);
        return System.nanoTime() - start;
    }

    public void throwAlways() {
        throw new IllegalStateException("boom");
    }

    public String catchInternally() {
        try {
            throwAlways();
            return "unreachable";
        } catch (IllegalStateException e) {
            return "caught:" + e.getMessage();
        }
    }

    public synchronized int syncCounter(int loops) {
        int total = 0;
        for (int i = 0; i < loops; i++) {
            total += i;
        }
        return total;
    }

    public void blocking(CountDownLatch release) {
        try {
            release.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public long parentWork(long millis) throws InterruptedException {
        long own = waitFor(millis);
        long own2 = waitFor(millis);
        return own + own2;
    }
}

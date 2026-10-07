package jvmprobe249.demo;

import java.util.concurrent.CountDownLatch;

/** Parent-loader interface so transformed instances remain directly callable. */
public interface SampleApi {

    int constructedValue();

    long fib(int n);

    int overload(int value);

    String overload(String value);

    double asDouble(double value);

    Object maybeNull(boolean returnNull);

    long waitFor(long millis) throws InterruptedException;

    void throwAlways();

    String catchInternally();

    int syncCounter(int loops);

    void blocking(CountDownLatch release);

    long parentWork(long millis) throws InterruptedException;
}

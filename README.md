# jvmprobe249

基于 Java 17 与 ASM 9.7 的字节码方法剖析 SDK，区分方法的**包含耗时（inclusive）**
与**自身耗时（self）**。无前端、无 HTTP，业务代码无需手写任何计时逻辑。

## 能力

- 输入 class 字节和用于解析类型的 `ClassLoader`，输出插桩后的字节；转换过程不加载、不执行类。
- 仅处理 Java 17（class 文件主版本 61）的普通类（含 enum/record/抽象类），
  插桩其实例方法与静态方法（包括重载、`synchronized` 方法）。
- 跳过：构造器 `<init>`、类初始化器 `<clinit>`、`abstract`、`native`、SDK 自身。
- 拒绝：非法字节码、接口、注解类型、`module-info`、非 Java 17 版本，
  统一抛出 `jvmprobe249.transform.ClassTransformException`。
- 重复转换幂等：已插桩类实现标记接口 `jvmprobe249.runtime.Probed`，再次转换原样返回。
- 方法入口由字节码调用 `CallStack.enter`，所有出口（正常 return、隐式/显式异常）
  恰好结算一次；方法内部 catch 住的异常不算异常退出；原异常对象、返回值、
  catch/finally 语义完全保留；`synchronized` 方法的 monitor 释放语义不变。
- 计时使用 `System.nanoTime()`，包含线程等待/阻塞时间。
- 每个线程独立调用栈（`ThreadLocal`），递归与相互递归逐层结算；跨线程不串栈。
- 自身耗时 = 本次包含耗时 − 直接**已插桩**子调用的包含耗时之和；
  未插桩调用的时间仍计入自身；孙调用的时间只随直接子调用扣减一次，不重复扣。
- 按 `内部类名 + 方法名 + JVM 描述符` 汇总：完成次数、异常退出次数、
  包含总耗时、自身总耗时、最大包含耗时。
- 快照不可变且全局一致；并发调用/读取不丢计数；仅在无在途调用时允许清空。

## 接入

```java
import jvmprobe249.MethodId;
import jvmprobe249.MethodStats;
import jvmprobe249.Probe;

// 1. 在自定义 ClassLoader 的 defineClass 之前转换（或用 Instrumentation/JVMTI 回调）
byte[] instrumented = Probe.transform(classBytes, resolvingClassLoader);
defineClass(name, instrumented, 0, instrumented.length);

// 2. 任意时刻取一致快照
Map<MethodId, MethodStats> snapshot = Probe.snapshot();
snapshot.forEach((id, s) -> System.out.println(
        id + " done=" + s.completedCount()
           + " error=" + s.abnormalCount()
           + " inclusiveNs=" + s.totalInclusiveNanos()
           + " selfNs=" + s.totalSelfNanos()
           + " maxInclusiveNs=" + s.maxInclusiveNanos()));

// 3. 清空（有在途调用时抛 IllegalStateException）
Probe.reset();
Probe.inFlightCount(); // 当前所有线程的在途方法调用数
```

说明：

- 指标只统计**已退出**的调用；在途调用不计入快照。
- 只有经过 `Probe.transform` 后加载的类会被统计，且只有这些类之间的调用会形成父子关系。
- 统计进程级单例（`jvmprobe249.runtime.CallStack` 的静态注册表）。

## 构建、自测与演示

```bash
mvn package          # 编译 + JUnit 自测，产物 target/jvmprobe249-0.1.0.jar

# 运行示例：加载转换后的 SampleMethods，演示递归、传播/内部捕获异常、4 线程并发
CP="target/jvmprobe249-0.1.0.jar:target/test-classes:"
CP+=".m2/repository/org/ow2/asm/asm/9.7/asm-9.7.jar:"
CP+=".m2/repository/org/ow2/asm/asm-tree/9.7/asm-tree-9.7.jar:"
CP+=".m2/repository/org/ow2/asm/asm-commons/9.7/asm-commons-9.7.jar"
java -cp "$CP" jvmprobe249.demo.DemoMain
```

演示输出示例（耗时随机器变化，次数固定）：

```
propagated: java.lang.IllegalStateException: boom
internally caught -> caught:boom
in-flight after work: 0
fib(I)J                                                   817        0 ...
throwAlways()V                                              2        2 ...
catchInternally()...                                        1        0 ...
```

## 设计与边界

- `transform/ProbeClassTransformer`：解析校验、版本/类别过滤、幂等标记、调用方法级插桩。
- `transform/MethodInstrumenter`：方法体外层包一个 catch-all（try 范围不覆盖 enter/exit 本身）；
  每个 `*return` 前先暂存返回值、正常结算再恢复返回值；异常路径用 `ATHROW` 前结算和
  catch-all 双保险，结算以 frame 上的 `settled` 标记保证最多一次。
- `runtime/CallStack`：`ThreadLocal` 调用栈、在途计数、进入/退出结算。
- `runtime/ProbeRegistry`：单锁保护所有更新与快照，快照为不可变 Map。
- `SafeClassWriter`：COMPUTE_FRAMES 通过传入的 ClassLoader 解析公共父类，解析失败保守回退 Object。
- 剖析自身有固定开销（每次进入/退出两次 `nanoTime`、少量对象/字符串），纳秒级绝对值含此开销。
- 不覆盖：接口默认方法、`<init>`/`<clinit>`、非 61 版本类；调用未插桩类的时间归入自身耗时。

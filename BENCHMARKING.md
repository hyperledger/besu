# Running JMH Benchmarks in Besu

Besu includes [JMH](https://openjdk.org/projects/code-tools/jmh/) microbenchmarks for performance testing. This guide explains how to build and run them using Gradle.

## 🛠️ Prerequisites

- Java 21+ (ensure `JAVA_HOME` is set)
- Gradle (you can use the wrapper: `./gradlew`)
- Optional: [Async Profiler](https://github.com/jvm-profiling-tools/async-profiler) for low-overhead profiling

---

## 🏃 Running Benchmarks

JMH tasks are defined in various modules such as `ethereum:core`, `ethereum:eth`, and `ethereum:rlp`.

### 🔁 Run All Benchmarks

```bash
./gradlew :ethereum:core:jmh
```

This runs all available benchmarks in the `core` module using default settings.

---

## 🎯 Filter Benchmarks by Name

To run a specific benchmark class, use the `-Pincludes` parameter:

```bash
./gradlew :ethereum:core:jmh -Pincludes=SomeBenchmark
```

This uses a regex pattern so other kinds of regexes can be used.

---

## ⚙️ Benchmark Configuration Options

For other configuration options run:

```bash
./gradlew help --task jmh
```

---

## 🔥 Async Profiler Integration (Optional)

To profile benchmarks with [Async Profiler](https://github.com/jvm-profiling-tools/async-profiler):

```bash
./gradlew :ethereum:core:jmh \
  -Pincludes=SomeBenchmark \
  -PasyncProfiler=/path/to/libasyncProfiler.so \
  -PasyncProfilerOptions="flamegraph;output=flames.svg"
```

This will generate a `flames.svg` file after the benchmark run.

---

## 🧪 Sample Output

```
Benchmark                                Mode  Cnt  Score   Error  Units
SomeBenchmark.benchmark                  avgt   20  3.893 ± 0.021  ns/op
SomeBenchmark.benchmark                  avgt   20  8.951 ± 0.149  ns/op
```

---

## 📚 References

- [JMH Documentation](https://openjdk.org/projects/code-tools/jmh/)
- [Async Profiler GitHub](https://github.com/jvm-profiling-tools/async-profiler)

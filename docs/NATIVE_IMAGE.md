# GraalVM Native Image Guide for Stirling-PDF

This guide explains how to build, test, optimize, and benchmark the GraalVM Native Image version of Stirling-PDF.

## Prerequisites
- **OS**: Linux (Ubuntu 22.04+ recommended) or macOS (Apple Silicon supported)
- **JDK**: GraalVM for JDK 25+ (Community or Oracle Enterprise)
- **Memory**: At least 16GB RAM is recommended for compilation
- **Disk**: 10GB+ free space

## Quick Reference

| Task | Command |
|------|---------|
| Quick dev build | `./gradlew :stirling-pdf:nativeCompile -Pnative.profile=quick -PnoSpotless` |
| Standard build | `./gradlew :stirling-pdf:nativeCompile -PnoSpotless` |
| Production build | `./gradlew :stirling-pdf:nativeCompile -Pnative.profile=production -PnoSpotless` |
| Full pipeline | `./scripts/test-native.sh` |
| PGO workflow | `./scripts/test-native.sh --pgo` |
| Benchmark | `./scripts/benchmark-native.sh` |
| Docker build | `docker build -f docker/Dockerfile.native -t stirling-pdf-native .` |

## Build Profiles

Three build profiles are available via `-Pnative.profile=PROFILE`:

| Profile | Opt Level | Debug Info | Build Time | Use Case |
|---------|-----------|------------|------------|----------|
| `quick` | `-Ob` | Kept | ~2-3 min | Development iteration |
| `standard` | `-O2` | Stripped | ~5-10 min | Default, CI builds |
| `production` | `-O2` | Stripped | ~8-15 min | Release builds |

```bash
# Fast development builds (minimal optimization, keeps debug info)
./gradlew :stirling-pdf:nativeCompile -Pnative.profile=quick -PnoSpotless

# Standard build (default if omitted)
./gradlew :stirling-pdf:nativeCompile -PnoSpotless

# Full production optimization
./gradlew :stirling-pdf:nativeCompile -Pnative.profile=production -PnoSpotless
```

## CPU Architecture

The default architecture is `compatibility` (runs on any x86_64 CPU). Override with:

```bash
# Modern servers (Intel Haswell+ / AMD Excavator+, enables AVX2, BMI2)
./gradlew :stirling-pdf:nativeCompile -Pnative.arch=x86-64-v3 -PnoSpotless

# Current machine's CPU (not portable — use only for local deployments)
./gradlew :stirling-pdf:nativeCompile -Pnative.arch=native -PnoSpotless
```

## Profile-Guided Optimization (PGO)

PGO teaches the compiler which code paths are actually hot, enabling aggressive
inlining and branch optimization. Typically yields ~15-25% throughput improvement.

### Automated PGO (recommended)
```bash
./scripts/test-native.sh --pgo
```

### Manual PGO Steps

**Step 1: Build with instrumentation**
```bash
./gradlew :stirling-pdf:nativeCompile -Pnative.pgo.instrument -PnoSpotless
```

**Step 2: Run and exercise the application**
```bash
./app/core/build/native/nativeCompile/stirling-pdf -Dserver.port=8080 &
# Perform representative operations: merge, split, OCR, compress, etc.
# Stop the application — this flushes the profile data to default.iprof
kill %1
```

**Step 3: Rebuild with the profile**
```bash
./gradlew :stirling-pdf:nativeCompile -Pnative.pgo.profile=default.iprof -PnoSpotless
```

## Runtime Configuration

### Heap Size
Heap limits are NOT baked into the binary — set them at runtime for flexibility:

```bash
# Recommended for most deployments
./stirling-pdf -Xms128m -Xmx512m -Dserver.port=8080

# Low-memory environments
./stirling-pdf -Xms64m -Xmx256m -Dserver.port=8080

# Heavy workloads (large PDFs, batch processing)
./stirling-pdf -Xms256m -Xmx1g -Dserver.port=8080
```

### Docker
```bash
docker build -f docker/Dockerfile.native -t stirling-pdf-native .
docker run -p 8080:8080 stirling-pdf-native
```

## Benchmarking

Use the benchmark script to measure performance:

```bash
# Basic benchmark
./scripts/benchmark-native.sh

# With custom heap and more iterations
./scripts/benchmark-native.sh --heap=512m --iterations=50

# Save results to CSV (for comparing before/after optimizations)
./scripts/benchmark-native.sh --output=results.csv
```

## Garbage Collection

The build uses **Serial GC** (GraalVM Community Edition default):
- Lowest memory footprint
- Single-threaded collection
- Best for services with moderate allocation rates

G1 GC is available in Oracle GraalVM Enterprise but not in Community Edition.
If using Enterprise, change `--gc=serial` to `--gc=G1` in `build.gradle`.

## Configuration Architecture

Native image configuration uses two complementary approaches:

1. **Java Runtime Hints** (`NativeImageRuntimeHints.java`):
   - Programmatic, refactor-safe, IDE-friendly
   - Registers reflection, JNI, resources, and proxy hints via Spring AOT
   - Preferred for application-specific classes

2. **JSON Config Files** (`META-INF/native-image/stirling-pdf/`):
   - Auto-discovered by GraalVM (no experimental flags needed)
   - Generated/augmented by the tracing agent
   - Best for third-party library classes (AWT JNI, etc.)

### Using the Tracing Agent

To capture reflection/resource/proxy configs from actual runtime behavior:

```bash
java -agentlib:native-image-agent=config-output-dir=app/core/src/main/resources/META-INF/native-image/stirling-pdf \
     -jar app/core/build/libs/stirling-pdf-*.jar
# Exercise the application with representative workloads, then stop it.
# The agent writes/updates the JSON config files.
```

## Build-Time vs Runtime Initialization

| Initialized At | Classes | Why |
|----------------|---------|-----|
| **Build time** | `Standard14Fonts`, `Standard14Fonts$FontName` | Pure enum/static data, safe |
| **Runtime** | `sun.awt.*`, `sun.java2d.*`, `sun.font.*`, `java.awt.*` | Native JNI state |
| **Runtime** | `org.hibernate.*`, `javax.persistence.*`, `org.h2.*` | Database connections |

**Rule of thumb**: If a class's static initializer touches files, sockets, RNG, or
native libraries, it MUST be initialized at runtime.

## Known Limitations

- **First-run Latency**: Some PDF operations may be slower on the very first
  execution (no JIT warmup in native image).
- **Dynamic Class Loading**: All classes must be known at build time. Plugins or
  dynamic extensions are not supported in native mode.
- **AWT**: Requires system libraries (`libawt.so`, `libfontmanager.so`, etc.)
  which are bundled in the Docker container at `/app/jdk-libs/`.
- **G1 GC**: Only available in Oracle GraalVM, not Community Edition.

## Troubleshooting

### "Class Verification Failed"
Check `reflect-config.json` and ensure the class is registered, or add it to
`NativeImageRuntimeHints.java`.

### "Resource not found"
Add the resource pattern to `resource-config.json` or register it in
`NativeImageRuntimeHints.registerResourceHints()`.

### "No instances of ... are allowed in the image heap"
A class was initialized at build time but holds a file handle, socket, or native
state. Add it to `--initialize-at-run-time` in `build.gradle`.

### Build warnings about experimental options
All experimental flags (`-H:+UnlockExperimentalVMOptions`,
`-H:ReflectionConfigurationFiles=...`) have been removed. JSON configs are
auto-discovered from `META-INF/native-image/` on the classpath.

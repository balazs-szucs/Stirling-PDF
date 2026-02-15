# GraalVM Native Image Guide for Stirling-PDF

This guide explains how to build, test, and optimize the GraalVM Native Image version of Stirling-PDF.

## Prerequisites
- **OS**: Linux (Ubuntu 22.04+ recommended) or macOS (Apple Silicon supported)
- **JDK**: GraalVM for JDK 25+ (Community or Oracle Enterprise)
- **Memory**: At least 16GB RAM is recommended for compilation
- **Disk**: 10GB+ free space

## Build Commands

### 1. Build Native Binary locally
```bash
./gradlew :stirling-pdf:nativeCompile -PnoSpotless
```
The binary will be at: `app/core/build/native/nativeCompile/stirling-pdf`

### 2. Run the full pipeline
We provide a script to run the full build pipeline (JVM tests -> AOT check -> Native Build -> Integration Test):
```bash
./scripts/test-native.sh
```

### 3. Build Docker Image
```bash
docker build -f docker/Dockerfile.native -t stirling-pdf-native .
docker run -p 8080:8080 stirling-pdf-native
```

## Profile-Guided Optimization (PGO)
PGO can improve runtime performance by ~15-20%.

### Step 1: Build with instrumentation
```bash
./gradlew :stirling-pdf:nativeCompile -Pnative.pgo-instrument -PnoSpotless
```

### Step 2: Run and Collect Profile
Run the instrumented binary and perform typical tasks (merge, split, OCR) to generate profile data.
```bash
./app/core/build/native/nativeCompile/stirling-pdf &
# ... perform tasks ...
# Stop the application to flush profile
```

### Step 3: Rebuild with Profile
```bash
./gradlew :stirling-pdf:nativeCompile -Pnative.pgo-profile=default.iprof -PnoSpotless
```

## Known Limitations
- **First-run Latency**: Some PDF operations using heavy reflection may be slower on the very first execution.
- **Dynamic Class Loading**: All classes must be known at build time. Plugins or dynamic extensions are not supported in native mode.
- **AWT**: Requires system libraries (`libawt.so`, etc.) which are bundled in our Docker container.

## Troubleshooting

### "Class Verification Failed"
If you see class verification errors, check `reflect-config.json` and ensure the class is registered.

### "Resource not found"
Add the resource pattern to `resource-config.json`.

### "No instances of ... are allowed in the image heap"
This means a class was initialized at build time but holds a file handle or socket. Add it to `--initialize-at-run-time` in `build.gradle`.

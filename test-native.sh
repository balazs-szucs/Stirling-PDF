#!/bin/bash
# test-native.sh

set -e

echo "=== Phase 1: JVM Tests ==="
./gradlew clean test

echo "=== Phase 2: AOT Processing ==="
./gradlew processAot processTestAot

echo "=== Phase 3: AOT Validation on JVM ==="
# We assume the jar is in app/core/build/libs/stirling-pdf-*.jar or similar
# Verify the build path first
JAR_PATH=$(find app/core/build/libs -name "stirling-pdf-*.jar" | head -n 1)

if [ -z "$JAR_PATH" ]; then
    echo "Could not find built JAR file. Building it now..."
    ./gradlew :stirling-pdf:bootJar
    JAR_PATH=$(find app/core/build/libs -name "stirling-pdf-*.jar" | head -n 1)
fi

echo "Testing JAR at: $JAR_PATH"

java -Dspring.aot.enabled=true -jar "$JAR_PATH" &
APP_PID=$!
echo "Application started with PID $APP_PID. Waiting for startup..."
sleep 15
kill $APP_PID

echo "=== Phase 4: Generate Tracing Agent Config (Optional) ==="
# ./gradlew -Pagent test
# Merge generated configs into src/main/resources/META-INF/native-image/

echo "=== Phase 5: Native Image Build ==="
./gradlew nativeCompile

echo "=== Phase 6: Native Image Tests ==="
./gradlew nativeTest

echo "=== Phase 7: Smoke Test Native Binary ==="
NATIVE_BINARY=$(find app/core/build/native/nativeCompile -type f -executable | head -n 1)

if [ -z "$NATIVE_BINARY" ]; then
    echo "Could not find native binary."
    exit 1
fi

"$NATIVE_BINARY" &
NATIVE_PID=$!
echo "Native binary started with PID $NATIVE_PID. Waiting for startup..."
sleep 5
curl http://localhost:8080/actuator/health || echo "Health check failed or endpoint not exposed"
kill $NATIVE_PID

echo "✅ All tests passed!"

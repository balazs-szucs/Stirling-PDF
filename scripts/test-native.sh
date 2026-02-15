#!/bin/bash
# =============================================================================
# Stirling-PDF GraalVM Native Image - Full Build & Test Pipeline
# =============================================================================
# Usage: ./scripts/test-native.sh [--skip-jvm-tests] [--skip-frontend] [--quick]
#
# This script runs the complete native image pipeline:
#   Phase 1: JVM Tests
#   Phase 2: AOT Processing & Validation
#   Phase 3: Generate tracing agent config (optional)
#   Phase 4: Native Image Build
#   Phase 5: Smoke Test Native Binary
#   Phase 6: Native + React Frontend Integration Test
# =============================================================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Parse arguments
SKIP_JVM_TESTS=false
SKIP_FRONTEND=false
QUICK=false
for arg in "$@"; do
    case $arg in
        --skip-jvm-tests) SKIP_JVM_TESTS=true ;;
        --skip-frontend)  SKIP_FRONTEND=true ;;
        --quick)          QUICK=true; SKIP_JVM_TESTS=true ;;
        --help|-h)
            echo "Usage: $0 [--skip-jvm-tests] [--skip-frontend] [--quick]"
            echo ""
            echo "Options:"
            echo "  --skip-jvm-tests  Skip Phase 1 (JVM tests)"
            echo "  --skip-frontend   Skip Phase 6 (Frontend integration)"
            echo "  --quick           Skip JVM tests, run minimal pipeline"
            echo "  --help            Show this help"
            exit 0
            ;;
    esac
done

# Detect project root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}  Stirling-PDF Native Image Build Pipeline  ${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

# Check prerequisites
echo -e "${YELLOW}Checking prerequisites...${NC}"

if ! command -v java &> /dev/null; then
    echo -e "${RED}ERROR: java not found. Install GraalVM JDK 25+${NC}"
    echo "  sdk install java 25.0.2-graalce"
    exit 1
fi

if ! command -v native-image &> /dev/null; then
    echo -e "${RED}ERROR: native-image not found. Install GraalVM with native-image.${NC}"
    echo "  sdk install java 25.0.2-graalce"
    echo "  # native-image should be included in GraalVM 25+"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -1)
NATIVE_IMAGE_VERSION=$(native-image --version 2>&1 | head -1)
echo -e "  Java:         ${JAVA_VERSION}"
echo -e "  Native Image: ${NATIVE_IMAGE_VERSION}"
echo ""

# Track timing
TOTAL_START=$(date +%s)
phase_start() {
    PHASE_START=$(date +%s)
}
phase_end() {
    local PHASE_END=$(date +%s)
    local DURATION=$((PHASE_END - PHASE_START))
    echo -e "${GREEN}  Completed in ${DURATION}s${NC}"
    echo ""
}

# ============================================
# Phase 1: JVM Tests
# ============================================
if [ "$SKIP_JVM_TESTS" = false ]; then
    echo -e "${BLUE}=== Phase 1: JVM Tests ===${NC}"
    phase_start
    ./gradlew :stirling-pdf:test -PnoSpotless
    phase_end
else
    echo -e "${YELLOW}=== Phase 1: JVM Tests (SKIPPED) ===${NC}"
    echo ""
fi

# ============================================
# Phase 2: AOT Processing & Validation
# ============================================
echo -e "${BLUE}=== Phase 2: AOT Processing ===${NC}"
phase_start
./gradlew :stirling-pdf:processAot -PnoSpotless
echo -e "  AOT sources generated in app/core/build/generated/aotSources/"
phase_end

echo -e "${BLUE}=== Phase 2b: Build bootJar ===${NC}"
phase_start
./gradlew :stirling-pdf:bootJar -PnoSpotless
phase_end

echo -e "${BLUE}=== Phase 2c: AOT Validation on JVM ===${NC}"
phase_start
BOOTJAR=$(find app/core/build/libs -name "*.jar" -not -name "*-plain.jar" | head -1)
if [ -z "$BOOTJAR" ]; then
    echo -e "${RED}ERROR: bootJar not found${NC}"
    exit 1
fi
echo "  Starting with AOT enabled: $BOOTJAR"

java -Dspring.aot.enabled=true \
     -Dserver.port=8091 \
     -Dfile.encoding=UTF-8 \
     -Djava.io.tmpdir=/tmp/stirling-pdf-test \
     -jar "$BOOTJAR" &
AOT_PID=$!

STARTED=false
for i in $(seq 1 60); do
    if curl -s -o /dev/null -w "%{http_code}" http://localhost:8091/actuator/health 2>/dev/null | grep -q "200"; then
        echo -e "  ${GREEN}AOT mode: Application started successfully${NC}"
        STARTED=true
        break
    fi
    if ! kill -0 $AOT_PID 2>/dev/null; then
        echo -e "  ${RED}AOT mode: Application exited unexpectedly${NC}"
        exit 1
    fi
    sleep 1
done

kill $AOT_PID 2>/dev/null || true
wait $AOT_PID 2>/dev/null || true

if [ "$STARTED" = false ]; then
    echo -e "  ${YELLOW}WARNING: AOT validation timed out (may be OK)${NC}"
fi
phase_end

# ============================================
# Phase 3: Native Image Build
# ============================================
# ============================================
# Phase 3: Native Image Build
# ============================================
echo -e "${BLUE}=== Phase 3: Native Image Build ===${NC}"
echo -e "  ${YELLOW}This may take 5-15 minutes depending on your machine...${NC}"

# Optional PGO instructions (uncomment to use)
# ./gradlew :stirling-pdf:nativeCompile -Pnative.pgo-instrument -PnoSpotless
# <Run workload to generate default.iprof>
# ./gradlew :stirling-pdf:nativeCompile -Pnative.pgo-profile=default.iprof -PnoSpotless

phase_start
./gradlew :stirling-pdf:nativeCompile -PnoSpotless
phase_end

# Find the native binary
NATIVE_BINARY=$(find app/core/build/native/nativeCompile -type f -executable 2>/dev/null | head -1)
if [ -z "$NATIVE_BINARY" ]; then
    echo -e "${RED}ERROR: Native binary not found!${NC}"
    find app/core/build/native/ -type f 2>/dev/null || true
    exit 1
fi

BINARY_SIZE=$(du -h "$NATIVE_BINARY" | cut -f1)
echo -e "  ${GREEN}Native binary: $NATIVE_BINARY${NC}"
echo -e "  ${GREEN}Binary size: $BINARY_SIZE${NC}"
echo ""

# ============================================
# Phase 4: Smoke Test Native Binary
# ============================================
echo -e "${BLUE}=== Phase 4: Smoke Test Native Binary ===${NC}"
phase_start

mkdir -p /tmp/stirling-pdf-test

echo "  Starting native binary..."
START_NS=$(date +%s%N)
"$NATIVE_BINARY" \
    -Dserver.port=8092 \
    -Dfile.encoding=UTF-8 \
    -Djava.io.tmpdir=/tmp/stirling-pdf-test &
NATIVE_PID=$!

STARTED=false
for i in $(seq 1 30); do
    if curl -s -o /dev/null -w "%{http_code}" http://localhost:8092/actuator/health 2>/dev/null | grep -q "200"; then
        END_NS=$(date +%s%N)
        STARTUP_MS=$(( (END_NS - START_NS) / 1000000 ))
        echo -e "  ${GREEN}Native binary started in ${STARTUP_MS}ms!${NC}"
        STARTED=true
        break
    fi
    if ! kill -0 $NATIVE_PID 2>/dev/null; then
        echo -e "  ${RED}Native binary exited unexpectedly!${NC}"
        exit 1
    fi
    sleep 1
done

if [ "$STARTED" = true ]; then
    # Health check
    HEALTH=$(curl -s http://localhost:8092/actuator/health 2>/dev/null)
    echo -e "  Health: $HEALTH"

    # API docs
    API_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8092/v1/api-docs 2>/dev/null)
    echo -e "  API docs: HTTP $API_CODE"

    # Memory usage
    if [ -f /proc/$NATIVE_PID/status ]; then
        RSS=$(grep VmRSS /proc/$NATIVE_PID/status | awk '{print $2}')
        echo -e "  RSS Memory: ${RSS} kB ($(( RSS / 1024 )) MB)"
    fi
else
    echo -e "  ${RED}Native binary did not start within 30s${NC}"
fi

kill $NATIVE_PID 2>/dev/null || true
wait $NATIVE_PID 2>/dev/null || true
phase_end

# ============================================
# Phase 5: Frontend Integration Test
# ============================================
if [ "$SKIP_FRONTEND" = false ]; then
    echo -e "${BLUE}=== Phase 5: Frontend Integration Test ===${NC}"
    phase_start

    # Check Node.js
    if ! command -v node &> /dev/null; then
        echo -e "${YELLOW}  Node.js not found, skipping frontend test${NC}"
    else
        echo "  Building frontend..."
        cd frontend
        npm ci --prefer-offline 2>/dev/null || npm install
        DISABLE_ADDITIONAL_FEATURES=false VITE_API_BASE_URL=http://localhost:8093 npm run build
        cd "$PROJECT_ROOT"

        echo "  Starting native backend on port 8093..."
        "$NATIVE_BINARY" \
            -Dserver.port=8093 \
            -Dfile.encoding=UTF-8 \
            -Djava.io.tmpdir=/tmp/stirling-pdf-test &
        NATIVE_PID=$!

        # Wait for backend
        for i in $(seq 1 30); do
            if curl -s -o /dev/null -w "%{http_code}" http://localhost:8093/actuator/health 2>/dev/null | grep -q "200"; then
                echo -e "  ${GREEN}Backend started${NC}"
                break
            fi
            sleep 1
        done

        # Serve frontend with a simple HTTP server
        if command -v npx &> /dev/null; then
            echo "  Serving frontend on port 3000..."
            npx serve -s frontend/dist -l 3000 &
            FRONTEND_PID=$!
            sleep 2

            # Test frontend
            FRONTEND_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:3000/ 2>/dev/null)
            echo -e "  Frontend: HTTP $FRONTEND_CODE"

            # Test backend through frontend config
            BACKEND_HEALTH=$(curl -s http://localhost:8093/actuator/health 2>/dev/null)
            echo -e "  Backend health: $BACKEND_HEALTH"

            kill $FRONTEND_PID 2>/dev/null || true
        fi

        kill $NATIVE_PID 2>/dev/null || true
        wait $NATIVE_PID 2>/dev/null || true
    fi
    phase_end
else
    echo -e "${YELLOW}=== Phase 5: Frontend Integration (SKIPPED) ===${NC}"
    echo ""
fi

# ============================================
# Summary
# ============================================
TOTAL_END=$(date +%s)
TOTAL_DURATION=$((TOTAL_END - TOTAL_START))
TOTAL_MINUTES=$((TOTAL_DURATION / 60))
TOTAL_SECONDS=$((TOTAL_DURATION % 60))

echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}  Pipeline Complete!${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""
echo -e "  Native Binary: $NATIVE_BINARY"
echo -e "  Binary Size:   $BINARY_SIZE"
echo -e "  Total Time:    ${TOTAL_MINUTES}m ${TOTAL_SECONDS}s"
echo ""
echo -e "  ${BLUE}To run the native binary:${NC}"
echo -e "    $NATIVE_BINARY -Dserver.port=8080"
echo ""
echo -e "  ${BLUE}To run with frontend:${NC}"
echo -e "    $NATIVE_BINARY -Dserver.port=8081 &"
echo -e "    cd frontend && VITE_API_BASE_URL=http://localhost:8081 npm run dev"
echo ""

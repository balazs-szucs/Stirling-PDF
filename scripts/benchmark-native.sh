#!/bin/bash
# =============================================================================
# Stirling-PDF Native Image Benchmark
# =============================================================================
# Measures startup time, memory footprint, and API response times for the
# native binary. Use this to compare before/after optimizations (PGO, -O levels,
# architecture flags, etc.).
#
# Usage:
#   ./scripts/benchmark-native.sh [PATH_TO_BINARY] [OPTIONS]
#
# Options:
#   --port=PORT       Port to use (default: 8095)
#   --iterations=N    Number of API call iterations (default: 10)
#   --heap=SIZE       Set max heap, e.g. --heap=512m (default: no limit)
#   --output=FILE     Write results to file (CSV format)
#   --help            Show this help
#
# Examples:
#   ./scripts/benchmark-native.sh
#   ./scripts/benchmark-native.sh app/core/build/native/nativeCompile/stirling-pdf
#   ./scripts/benchmark-native.sh --iterations=50 --heap=256m
#   ./scripts/benchmark-native.sh --output=benchmark-results.csv
# =============================================================================

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# Defaults
BINARY=""
PORT=8095
ITERATIONS=10
HEAP_SIZE=""
OUTPUT_FILE=""

# Parse arguments
for arg in "$@"; do
    case $arg in
        --port=*)       PORT="${arg#*=}" ;;
        --iterations=*) ITERATIONS="${arg#*=}" ;;
        --heap=*)       HEAP_SIZE="${arg#*=}" ;;
        --output=*)     OUTPUT_FILE="${arg#*=}" ;;
        --help|-h)
            echo "Usage: $0 [PATH_TO_BINARY] [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --port=PORT       Port to use (default: 8095)"
            echo "  --iterations=N    Number of API iterations (default: 10)"
            echo "  --heap=SIZE       Max heap size, e.g. 512m (default: no limit)"
            echo "  --output=FILE     Write CSV results to file"
            echo "  --help            Show this help"
            exit 0
            ;;
        -*)
            echo "Unknown option: $arg"
            exit 1
            ;;
        *)
            BINARY="$arg"
            ;;
    esac
done

# Find binary if not specified
if [ -z "$BINARY" ]; then
    BINARY=$(find app/core/build/native/nativeCompile -type f -executable 2>/dev/null | head -1)
    if [ -z "$BINARY" ]; then
        echo -e "${RED}ERROR: Native binary not found. Build first with:${NC}"
        echo "  ./gradlew :stirling-pdf:nativeCompile -PnoSpotless"
        exit 1
    fi
fi

if [ ! -x "$BINARY" ]; then
    echo -e "${RED}ERROR: $BINARY is not executable${NC}"
    exit 1
fi

BINARY_SIZE=$(du -h "$BINARY" | cut -f1)

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}  Stirling-PDF Native Image Benchmark${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""
echo -e "  Binary:     $BINARY"
echo -e "  Size:       $BINARY_SIZE"
echo -e "  Port:       $PORT"
echo -e "  Iterations: $ITERATIONS"
if [ -n "$HEAP_SIZE" ]; then
echo -e "  Max Heap:   $HEAP_SIZE"
fi
echo ""

# Cleanup function
cleanup() {
    if [ -n "$BENCH_PID" ] && kill -0 "$BENCH_PID" 2>/dev/null; then
        kill "$BENCH_PID" 2>/dev/null || true
        wait "$BENCH_PID" 2>/dev/null || true
    fi
}
trap cleanup EXIT

# Build runtime args
RUNTIME_ARGS="-Dserver.port=$PORT -Dfile.encoding=UTF-8 -Djava.io.tmpdir=/tmp/stirling-pdf-bench"
if [ -n "$HEAP_SIZE" ]; then
    RUNTIME_ARGS="$RUNTIME_ARGS -Xmx$HEAP_SIZE"
fi

mkdir -p /tmp/stirling-pdf-bench

# ============================================
# Benchmark 1: Startup Time
# ============================================
echo -e "${CYAN}--- Startup Time ---${NC}"

START_NS=$(date +%s%N)
$BINARY $RUNTIME_ARGS &
BENCH_PID=$!

STARTED=false
for i in $(seq 1 60); do
    if curl -s -o /dev/null -w "%{http_code}" "http://localhost:$PORT/actuator/health" 2>/dev/null | grep -q "200"; then
        END_NS=$(date +%s%N)
        STARTUP_MS=$(( (END_NS - START_NS) / 1000000 ))
        echo -e "  Startup:  ${GREEN}${STARTUP_MS}ms${NC}"
        STARTED=true
        break
    fi
    if ! kill -0 $BENCH_PID 2>/dev/null; then
        echo -e "  ${RED}Binary exited unexpectedly!${NC}"
        exit 1
    fi
    sleep 0.5
done

if [ "$STARTED" = false ]; then
    echo -e "  ${RED}Binary did not start within 30s${NC}"
    kill $BENCH_PID 2>/dev/null || true
    exit 1
fi

# Let it stabilize
sleep 2

# ============================================
# Benchmark 2: Memory Footprint
# ============================================
echo -e "${CYAN}--- Memory Footprint ---${NC}"

if [ -f /proc/$BENCH_PID/status ]; then
    RSS_KB=$(grep VmRSS /proc/$BENCH_PID/status | awk '{print $2}')
    RSS_MB=$(( RSS_KB / 1024 ))
    VSZ_KB=$(grep VmSize /proc/$BENCH_PID/status | awk '{print $2}')
    VSZ_MB=$(( VSZ_KB / 1024 ))
    THREADS=$(grep Threads /proc/$BENCH_PID/status | awk '{print $2}')
    echo -e "  RSS:      ${GREEN}${RSS_MB}MB${NC} (${RSS_KB} kB)"
    echo -e "  VSZ:      ${VSZ_MB}MB"
    echo -e "  Threads:  ${THREADS}"
else
    RSS_MB="N/A"
    VSZ_MB="N/A"
    THREADS="N/A"
    echo -e "  ${YELLOW}(Memory info not available on this OS)${NC}"
fi

# ============================================
# Benchmark 3: API Response Times
# ============================================
echo -e "${CYAN}--- API Response Times (${ITERATIONS} iterations) ---${NC}"

# Health endpoint
HEALTH_TIMES=()
for i in $(seq 1 $ITERATIONS); do
    TIME=$(curl -s -o /dev/null -w "%{time_total}" "http://localhost:$PORT/actuator/health" 2>/dev/null)
    HEALTH_TIMES+=("$TIME")
done
HEALTH_AVG=$(printf '%s\n' "${HEALTH_TIMES[@]}" | awk '{sum+=$1} END {printf "%.3f", sum/NR}')
HEALTH_MIN=$(printf '%s\n' "${HEALTH_TIMES[@]}" | sort -n | head -1)
HEALTH_MAX=$(printf '%s\n' "${HEALTH_TIMES[@]}" | sort -n | tail -1)
echo -e "  /actuator/health:  avg=${GREEN}${HEALTH_AVG}s${NC}  min=${HEALTH_MIN}s  max=${HEALTH_MAX}s"

# API docs endpoint
API_TIMES=()
for i in $(seq 1 $ITERATIONS); do
    TIME=$(curl -s -o /dev/null -w "%{time_total}" "http://localhost:$PORT/v1/api-docs" 2>/dev/null)
    API_TIMES+=("$TIME")
done
API_AVG=$(printf '%s\n' "${API_TIMES[@]}" | awk '{sum+=$1} END {printf "%.3f", sum/NR}')
API_MIN=$(printf '%s\n' "${API_TIMES[@]}" | sort -n | head -1)
API_MAX=$(printf '%s\n' "${API_TIMES[@]}" | sort -n | tail -1)
echo -e "  /v1/api-docs:      avg=${GREEN}${API_AVG}s${NC}  min=${API_MIN}s  max=${API_MAX}s"

# Root endpoint
ROOT_TIMES=()
for i in $(seq 1 $ITERATIONS); do
    TIME=$(curl -s -o /dev/null -w "%{time_total}" "http://localhost:$PORT/" 2>/dev/null)
    ROOT_TIMES+=("$TIME")
done
ROOT_AVG=$(printf '%s\n' "${ROOT_TIMES[@]}" | awk '{sum+=$1} END {printf "%.3f", sum/NR}')
ROOT_MIN=$(printf '%s\n' "${ROOT_TIMES[@]}" | sort -n | head -1)
ROOT_MAX=$(printf '%s\n' "${ROOT_TIMES[@]}" | sort -n | tail -1)
echo -e "  /:                 avg=${GREEN}${ROOT_AVG}s${NC}  min=${ROOT_MIN}s  max=${ROOT_MAX}s"

# ============================================
# Benchmark 4: Memory After Load
# ============================================
echo -e "${CYAN}--- Memory After Load ---${NC}"

if [ -f /proc/$BENCH_PID/status ]; then
    RSS_AFTER_KB=$(grep VmRSS /proc/$BENCH_PID/status | awk '{print $2}')
    RSS_AFTER_MB=$(( RSS_AFTER_KB / 1024 ))
    echo -e "  RSS:      ${GREEN}${RSS_AFTER_MB}MB${NC} (${RSS_AFTER_KB} kB)"
    echo -e "  Growth:   $(( RSS_AFTER_MB - RSS_MB ))MB from baseline"
fi

# Cleanup
kill $BENCH_PID 2>/dev/null || true
wait $BENCH_PID 2>/dev/null || true
BENCH_PID=""

# ============================================
# Summary
# ============================================
echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}  Benchmark Results Summary${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""
echo -e "  Binary Size:    $BINARY_SIZE"
echo -e "  Startup Time:   ${STARTUP_MS}ms"
if [ "$RSS_MB" != "N/A" ]; then
echo -e "  Memory (idle):  ${RSS_MB}MB"
echo -e "  Memory (load):  ${RSS_AFTER_MB}MB"
echo -e "  Threads:        ${THREADS}"
fi
echo -e "  Health avg:     ${HEALTH_AVG}s"
echo -e "  API docs avg:   ${API_AVG}s"
echo ""

# Write CSV output if requested
if [ -n "$OUTPUT_FILE" ]; then
    # Write header if file doesn't exist
    if [ ! -f "$OUTPUT_FILE" ]; then
        echo "timestamp,binary_size,startup_ms,rss_idle_mb,rss_load_mb,threads,health_avg_s,api_avg_s,root_avg_s" > "$OUTPUT_FILE"
    fi
    echo "$(date -Iseconds),$BINARY_SIZE,$STARTUP_MS,$RSS_MB,$RSS_AFTER_MB,$THREADS,$HEALTH_AVG,$API_AVG,$ROOT_AVG" >> "$OUTPUT_FILE"
    echo -e "  ${GREEN}Results appended to: $OUTPUT_FILE${NC}"
fi

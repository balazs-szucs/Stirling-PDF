#!/bin/bash
# =============================================================================
# Stirling-PDF — Auto-generate GraalVM Native Image Config
# =============================================================================
# Runs the application under GraalVM's native-image-agent tracing agent,
# exercises key API endpoints, and merges the generated reflection/resource/
# JNI/proxy/serialization config into META-INF/native-image/stirling-pdf/.
#
# Usage:
#   ./scripts/generate-native-config.sh [OPTIONS]
#
# Options:
#   --merge           Merge with existing config (default: overwrite)
#   --validate        Also run with MissingRegistrationReportingMode=Exit
#   --port=PORT       Port to run on (default: 8099)
#   --timeout=SECS    Timeout in seconds (default: 120)
#   --skip-build      Skip bootJar build (use existing jar)
#   --exercise-only   Skip build + agent setup; just exercise endpoints on PORT
#   --keep-resources  Don't strip resource-config.json (default: strip it)
#   --help            Show this help
#
# Prerequisites:
#   - GraalVM JDK 25+ with native-image-agent
#   - Project must compile (./gradlew bootJar)
#
# IMPORTANT: resource-config.json is EXCLUDED by default because the agent
# records every static file (JS, CSS, fonts, images), which inflates the
# native binary from ~120 MB to ~400 MB. Resources are managed manually
# in NativeImageRuntimeHints.java and the existing resource-config.json.
# =============================================================================

set -euo pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Defaults
MERGE_MODE=false
VALIDATE_MODE=false
PORT=8099
TIMEOUT=120
SKIP_BUILD=false
EXERCISE_ONLY=false
KEEP_RESOURCES=false

# Parse arguments
for arg in "$@"; do
    case $arg in
        --merge)          MERGE_MODE=true ;;
        --validate)       VALIDATE_MODE=true ;;
        --port=*)         PORT="${arg#*=}" ;;
        --timeout=*)      TIMEOUT="${arg#*=}" ;;
        --skip-build)     SKIP_BUILD=true ;;
        --exercise-only)  EXERCISE_ONLY=true ;;
        --keep-resources) KEEP_RESOURCES=true ;;
        --help|-h)
            sed -n '2,/^$/p' "$0" | sed 's/^# \?//'
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $arg${NC}"
            echo "Use --help for usage"
            exit 1
            ;;
    esac
done

# Detect project root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

AGENT_OUTPUT_DIR="app/core/build/native/agent-output"
AGENT_MERGED_DIR="app/core/build/native/agent-merged"
CONFIG_DIR="app/core/src/main/resources/META-INF/native-image/stirling-pdf"
TMPDIR_AGENT="/tmp/stirling-pdf-agent"
APP_PID=""
CREATED_SAMPLE=false

echo -e "${BLUE}=================================================${NC}"
echo -e "${BLUE}  Native Image Config Generator (Tracing Agent)  ${NC}"
echo -e "${BLUE}=================================================${NC}"
echo ""
echo -e "  Mode:           $([ "$MERGE_MODE" = true ] && echo "MERGE" || echo "OVERWRITE")"
echo -e "  Port:           ${PORT}"
echo -e "  Timeout:        ${TIMEOUT}s"
echo -e "  Keep resources: ${KEEP_RESOURCES}"
echo ""

# ─────────────────────────────────────────────────────────────────────────────
# Cleanup handler — runs on EXIT, ERR, INT, TERM
# ─────────────────────────────────────────────────────────────────────────────
cleanup() {
    local exit_code=$?
    if [ -n "$APP_PID" ] && kill -0 "$APP_PID" 2>/dev/null; then
        echo -e "\n${YELLOW}  Stopping app (PID ${APP_PID})...${NC}"
        kill -TERM "$APP_PID" 2>/dev/null || true
        # Wait for graceful shutdown so the agent flushes config
        for i in $(seq 1 15); do
            if ! kill -0 "$APP_PID" 2>/dev/null; then break; fi
            sleep 1
        done
        kill -KILL "$APP_PID" 2>/dev/null || true
    fi
    if [ "$CREATED_SAMPLE" = true ]; then
        rm -f "${TMPDIR_AGENT}/minimal.pdf" 2>/dev/null || true
    fi
    rm -rf "${TMPDIR_AGENT}/check" 2>/dev/null || true
    exit "$exit_code"
}
trap cleanup EXIT INT TERM

# ─────────────────────────────────────────────────────────────────────────────
# Utility: wait for the app to be healthy
# ─────────────────────────────────────────────────────────────────────────────
wait_for_health() {
    local port=$1
    local max_wait=$2
    local label=${3:-"Application"}

    for i in $(seq 1 "$max_wait"); do
        # Try actuator first, fall back to root
        if curl -sf -o /dev/null "http://localhost:${port}/actuator/health" 2>/dev/null ||
           curl -sf -o /dev/null "http://localhost:${port}/" 2>/dev/null; then
            echo -e "  ${GREEN}${label} is healthy (took ${i}s)${NC}"
            return 0
        fi
        # Show progress every 10 seconds
        if (( i % 10 == 0 )); then
            echo -e "  Waiting... (${i}/${max_wait}s)"
        fi
        sleep 1
    done
    echo -e "  ${RED}${label} did not become healthy within ${max_wait}s${NC}"
    return 1
}

# ─────────────────────────────────────────────────────────────────────────────
# Utility: exercise API endpoints to trigger reflection/resource paths
# ─────────────────────────────────────────────────────────────────────────────
exercise_endpoints() {
    local port=$1
    local base="http://localhost:${port}"
    local pass=0
    local fail=0

    echo -e "${BLUE}  Exercising API endpoints...${NC}"

    hit() {
        local method=$1
        local path=$2
        local description=$3
        shift 3
        # Remaining args are extra curl flags

        local code
        code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 30 \
            -X "$method" "$@" "${base}${path}" 2>/dev/null || echo "000")

        if [[ "$code" =~ ^(200|204|301|302|400|401|403|405|415|422) ]]; then
            pass=$((pass + 1))
        else
            fail=$((fail + 1))
            echo -e "    ${YELLOW}WARN: ${method} ${path} → HTTP ${code} (${description})${NC}"
        fi
    }

    # ── Core health & metadata ──
    hit GET "/actuator/health"        "Health check"
    hit GET "/v1/api-docs"            "OpenAPI docs"
    hit GET "/swagger-ui/index.html"  "Swagger UI"
    hit GET "/"                       "Root / frontend"
    hit GET "/api/v1/info/status"     "App status"

    # ── Locate or create a real PDF for testing ──
    local sample_pdf=""
    if [ -f "testing/cucumber/exampleFiles/example.pdf" ]; then
        sample_pdf="testing/cucumber/exampleFiles/example.pdf"
    else
        mkdir -p "$TMPDIR_AGENT"
        sample_pdf="${TMPDIR_AGENT}/minimal.pdf"
        # Create a minimal but valid PDF with actual content
        python3 -c "
try:
    from reportlab.pdfgen import canvas
    c = canvas.Canvas('${sample_pdf}')
    c.drawString(72, 720, 'Stirling-PDF Agent Test')
    c.save()
except ImportError:
    # Fallback: hand-crafted minimal PDF
    import sys
    pdf = b'''%PDF-1.4
1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj
2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj
3 0 obj<</Type/Page/MediaBox[0 0 612 792]/Parent 2 0 R/Resources<</Font<</F1 4 0 R>>>>/Contents 5 0 R>>endobj
4 0 obj<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>endobj
5 0 obj<</Length 44>>stream
BT /F1 12 Tf 72 720 Td (Test page) Tj ET
endstream
endobj
xref
0 6
0000000000 65535 f
0000000010 00000 n
0000000060 00000 n
0000000117 00000 n
0000000236 00000 n
0000000311 00000 n
trailer<</Size 6/Root 1 0 R>>
startxref
407
%%EOF'''
    with open('${sample_pdf}', 'wb') as f:
        f.write(pdf)
" 2>/dev/null
        CREATED_SAMPLE=true
        echo -e "  Created test PDF: ${sample_pdf}"
    fi

    # ── Exercise with real PDF to trigger actual code paths ──
    if [ -n "$sample_pdf" ] && [ -f "$sample_pdf" ]; then
        echo -e "  ${BLUE}Exercising with PDF: ${sample_pdf}${NC}"
        hit POST "/api/v1/misc/compress-pdf"          "Compress"        -F "fileInput=@${sample_pdf}" -F "optimizeLevel=1"
        hit POST "/api/v1/general/rotate-pdf"         "Rotate"          -F "fileInput=@${sample_pdf}" -F "angle=90"
        hit POST "/api/v1/general/split-pages"        "Split"           -F "fileInput=@${sample_pdf}" -F "pages=1"
        hit POST "/api/v1/misc/flatten"               "Flatten"         -F "fileInput=@${sample_pdf}"
        hit POST "/api/v1/convert/pdf/img"            "PDF→IMG"         -F "fileInput=@${sample_pdf}" -F "imageFormat=png" -F "singleOrMultiple=single"
        hit POST "/api/v1/security/get-info-on-pdf"   "PDF info"        -F "fileInput=@${sample_pdf}"
        hit POST "/api/v1/misc/add-page-numbers"      "Page numbers"    -F "fileInput=@${sample_pdf}"
        hit POST "/api/v1/misc/repair"                "Repair"          -F "fileInput=@${sample_pdf}"
        hit POST "/api/v1/security/sanitize-pdf"      "Sanitize"        -F "fileInput=@${sample_pdf}"
        hit POST "/api/v1/misc/extract-images"        "Extract images"  -F "fileInput=@${sample_pdf}"
        hit POST "/api/v1/general/merge-pdfs"         "Merge"           -F "fileInput=@${sample_pdf}"
        hit POST "/api/v1/security/add-password"      "Add password"    -F "fileInput=@${sample_pdf}" -F "password=test123"
        hit POST "/api/v1/misc/show-javascript"       "Show JS"         -F "fileInput=@${sample_pdf}"
        hit POST "/api/v1/general/pdf-to-single-page" "Single page"     -F "fileInput=@${sample_pdf}"
        hit POST "/api/v1/misc/auto-rename"           "Auto-rename"     -F "fileInput=@${sample_pdf}"
    else
        echo -e "  ${YELLOW}WARN: No test PDF available — endpoint coverage will be limited${NC}"
        # Fall back to triggering class loading with empty requests
        hit POST "/api/v1/misc/compress-pdf"          "Compress (no file)"    -F "fileInput=@/dev/null"
        hit POST "/api/v1/general/split-pages"        "Split (no file)"      -F "fileInput=@/dev/null"
        hit POST "/api/v1/general/merge-pdfs"         "Merge (no file)"      -F "fileInput=@/dev/null"
    fi

    echo -e "  ${GREEN}Endpoints exercised: ${pass} OK, ${fail} warnings${NC}"
    echo ""
}

# ─────────────────────────────────────────────────────────────────────────────
# Phase 0: Exercise-only mode
# ─────────────────────────────────────────────────────────────────────────────
if [ "$EXERCISE_ONLY" = true ]; then
    echo -e "${YELLOW}=== Exercise-only mode — skipping build + agent ===${NC}"
    exercise_endpoints "$PORT"
    echo -e "${GREEN}Done.${NC}"
    exit 0
fi

# ─────────────────────────────────────────────────────────────────────────────
# Phase 1: Build bootJar
# ─────────────────────────────────────────────────────────────────────────────
if [ "$SKIP_BUILD" = false ]; then
    echo -e "${BLUE}=== Phase 1: Building bootJar ===${NC}"
    ./gradlew :stirling-pdf:bootJar -PnoSpotless \
        -x test -x collectReachabilityMetadata \
        -x npmInstall -x npmBuild \
        -x spotlessCheck -x sonarqube \
        --no-daemon
    echo -e "${GREEN}  Build complete${NC}"
    echo ""
fi

BOOTJAR=$(find app/core/build/libs -name "*.jar" -not -name "*-plain.jar" | sort -r | head -1)
if [ -z "$BOOTJAR" ]; then
    echo -e "${RED}ERROR: bootJar not found in app/core/build/libs/${NC}"
    echo "  Run: ./gradlew :stirling-pdf:bootJar -PnoSpotless"
    exit 1
fi
echo -e "  Using jar: ${BOOTJAR}"
echo ""

# ─────────────────────────────────────────────────────────────────────────────
# Phase 2: Run under tracing agent
# ─────────────────────────────────────────────────────────────────────────────
echo -e "${BLUE}=== Phase 2: Running under native-image-agent ===${NC}"

# Verify GraalVM agent is available
mkdir -p "${TMPDIR_AGENT}/check"
if ! java -agentlib:native-image-agent=config-output-dir="${TMPDIR_AGENT}/check" \
     -version >/dev/null 2>&1; then
    echo -e "${RED}ERROR: native-image-agent not available.${NC}"
    echo -e "  Current JVM: $(java -version 2>&1 | head -1)"
    echo ""
    echo -e "  ${YELLOW}Install GraalVM or run via Docker:${NC}"
    echo -e "  docker build -f docker/Dockerfile.native --build-arg RUN_AGENT=true -t stirling-native ."
    exit 1
fi
rm -rf "${TMPDIR_AGENT}/check"

# Prepare output directory
if [ "$MERGE_MODE" = true ]; then
    OUTPUT_DIR="$AGENT_MERGED_DIR"
    AGENT_OPT="config-merge-dir"
    echo -e "  Mode: merge (preserving existing agent output)"
else
    OUTPUT_DIR="$AGENT_OUTPUT_DIR"
    AGENT_OPT="config-output-dir"
    rm -rf "$OUTPUT_DIR"
    echo -e "  Mode: overwrite (clean output)"
fi
mkdir -p "$OUTPUT_DIR" "$TMPDIR_AGENT"

# Construct agent args
AGENT_ARGS="-agentlib:native-image-agent=${AGENT_OPT}=${PWD}/${OUTPUT_DIR}"

# Add caller/access filters if they exist
AGENT_FILTER_DIR="${CONFIG_DIR}"
if [ -f "${AGENT_FILTER_DIR}/agent-caller-filter.json" ]; then
    AGENT_ARGS="${AGENT_ARGS},caller-filter-file=${PWD}/${AGENT_FILTER_DIR}/agent-caller-filter.json"
    echo -e "  Using caller filter: agent-caller-filter.json"
fi
if [ -f "${AGENT_FILTER_DIR}/agent-access-filter.json" ]; then
    AGENT_ARGS="${AGENT_ARGS},access-filter-file=${PWD}/${AGENT_FILTER_DIR}/agent-access-filter.json"
    echo -e "  Using access filter: agent-access-filter.json"
fi

echo -e "  Starting app on port ${PORT}..."

java \
    "${AGENT_ARGS}" \
    -Dserver.port="${PORT}" \
    -Dfile.encoding=UTF-8 \
    -Djava.io.tmpdir="${TMPDIR_AGENT}" \
    -Djava.awt.headless=true \
    -Xmx4G \
    -jar "$BOOTJAR" &
APP_PID=$!

# Wait for the app to become healthy
if ! wait_for_health "$PORT" "$TIMEOUT" "Agent-traced app"; then
    echo -e "${RED}App did not start. Last 30 lines of output:${NC}"
    exit 1
fi

# ─────────────────────────────────────────────────────────────────────────────
# Phase 3: Exercise endpoints
# ─────────────────────────────────────────────────────────────────────────────
echo -e "${BLUE}=== Phase 3: Exercising Endpoints ===${NC}"
exercise_endpoints "$PORT"

# Let the app settle (write any lazy-init reflection data)
echo -e "  Waiting 5s for lazy initialization..."
sleep 5

# ─────────────────────────────────────────────────────────────────────────────
# Phase 4: Stop app (flushes agent config)
# ─────────────────────────────────────────────────────────────────────────────
echo -e "${BLUE}=== Phase 4: Stopping App (Agent Flush) ===${NC}"

kill -TERM "$APP_PID" 2>/dev/null || true

EXITED=false
for i in $(seq 1 30); do
    if ! kill -0 "$APP_PID" 2>/dev/null; then
        EXITED=true
        break
    fi
    sleep 1
done

if [ "$EXITED" = false ]; then
    echo -e "  ${YELLOW}App did not exit gracefully, sending SIGKILL${NC}"
    kill -KILL "$APP_PID" 2>/dev/null || true
    sleep 1
fi
APP_PID=""  # Prevent cleanup from trying to stop again

echo -e "  ${GREEN}Agent config written to: ${OUTPUT_DIR}/${NC}"

# ─────────────────────────────────────────────────────────────────────────────
# Phase 4b: STRIP resource-config.json (THE KEY FIX)
# ─────────────────────────────────────────────────────────────────────────────
if [ "$KEEP_RESOURCES" = false ]; then
    RESOURCE_CFG="${OUTPUT_DIR}/resource-config.json"
    if [ -f "$RESOURCE_CFG" ]; then
        RESOURCE_ENTRIES=$(python3 -c "
import json, sys
data = json.load(open('${RESOURCE_CFG}'))
# Count resource patterns
if isinstance(data, dict):
    includes = data.get('resources', {}).get('includes', [])
    bundles = data.get('bundles', [])
    print(f'{len(includes)} resource patterns, {len(bundles)} bundles')
elif isinstance(data, list):
    print(f'{len(data)} entries')
else:
    print('unknown format')
" 2>/dev/null || echo "unknown")
        RESOURCE_SIZE=$(du -h "$RESOURCE_CFG" | cut -f1)

        echo ""
        echo -e "  ${YELLOW}╔══════════════════════════════════════════════════════════╗${NC}"
        echo -e "  ${YELLOW}║  STRIPPING resource-config.json from agent output       ║${NC}"
        echo -e "  ${YELLOW}║                                                          ║${NC}"
        echo -e "  ${YELLOW}║  The agent recorded ${RESOURCE_ENTRIES}${NC}"
        echo -e "  ${YELLOW}║  (${RESOURCE_SIZE} on disk)${NC}"
        echo -e "  ${YELLOW}║                                                          ║${NC}"
        echo -e "  ${YELLOW}║  These would be EMBEDDED in the native binary, adding    ║${NC}"
        echo -e "  ${YELLOW}║  100-250 MB. Resources are served from the filesystem    ║${NC}"
        echo -e "  ${YELLOW}║  and managed in NativeImageRuntimeHints.java instead.    ║${NC}"
        echo -e "  ${YELLOW}║                                                          ║${NC}"
        echo -e "  ${YELLOW}║  Use --keep-resources to override this behavior.         ║${NC}"
        echo -e "  ${YELLOW}╚══════════════════════════════════════════════════════════╝${NC}"
        echo ""

        # Save a backup for debugging, then remove
        cp "$RESOURCE_CFG" "${OUTPUT_DIR}/resource-config.json.agent-backup"
        rm "$RESOURCE_CFG"
        echo -e "  ${GREEN}Backup saved: resource-config.json.agent-backup${NC}"
    fi
else
    echo -e "  ${YELLOW}WARN: --keep-resources set — resource-config.json will be merged${NC}"
    echo -e "  ${YELLOW}      This WILL inflate the native binary significantly!${NC}"
fi

# Report what was generated
echo ""
echo -e "${BLUE}=== Generated Config Files ===${NC}"
for f in "$OUTPUT_DIR"/*.json; do
    [ -f "$f" ] || continue
    name=$(basename "$f")
    # Skip backup files
    [[ "$name" == *.agent-backup ]] && continue
    entries=$(python3 -c "
import json
d = json.load(open('$f'))
print(len(d) if isinstance(d, list) else 'N/A')
" 2>/dev/null || echo "?")
    size=$(du -h "$f" | cut -f1)
    echo -e "  ${name}: ${entries} entries (${size})"
done
echo ""

# ─────────────────────────────────────────────────────────────────────────────
# Phase 5: Merge into META-INF/native-image/
# ─────────────────────────────────────────────────────────────────────────────
echo -e "${BLUE}=== Phase 5: Merging Config ===${NC}"
./gradlew :stirling-pdf:nativeAgentCopyConfig -PnoSpotless --no-daemon
echo ""

# ─────────────────────────────────────────────────────────────────────────────
# Phase 6: Validate config
# ─────────────────────────────────────────────────────────────────────────────
echo -e "${BLUE}=== Phase 6: Validating Config ===${NC}"
./gradlew :stirling-pdf:nativeConfigValidate -PnoSpotless --no-daemon
echo ""

# ─────────────────────────────────────────────────────────────────────────────
# Phase 7: Optional — validate with MissingRegistrationReportingMode
# ─────────────────────────────────────────────────────────────────────────────
if [ "$VALIDATE_MODE" = true ]; then
    echo -e "${BLUE}=== Phase 7: Missing Registration Validation ===${NC}"
    echo -e "  Running with -XX:MissingRegistrationReportingMode=Exit..."

    VALIDATE_PORT=$((PORT + 1))
    java \
        -agentlib:native-image-agent=config-merge-dir="${PWD}/${AGENT_MERGED_DIR}" \
        -XX:MissingRegistrationReportingMode=Exit \
        -Dserver.port="${VALIDATE_PORT}" \
        -Dfile.encoding=UTF-8 \
        -Djava.io.tmpdir="${TMPDIR_AGENT}" \
        -Djava.awt.headless=true \
        -jar "$BOOTJAR" &
    VALIDATE_PID=$!

    VALIDATE_OK=false
    for i in $(seq 1 "$TIMEOUT"); do
        if ! kill -0 "$VALIDATE_PID" 2>/dev/null; then
            echo -e "  ${RED}App exited — missing registrations detected!${NC}"
            echo -e "  Check log output above for MissingReflectionRegistrationError."
            echo -e "  New config merged into: ${AGENT_MERGED_DIR}/"
            break
        fi

        if curl -sf -o /dev/null "http://localhost:${VALIDATE_PORT}/actuator/health" 2>/dev/null; then
            echo -e "  ${GREEN}App started successfully — no missing registrations!${NC}"
            VALIDATE_OK=true
            exercise_endpoints "$VALIDATE_PORT"
            break
        fi
        sleep 1
    done

    kill -TERM "$VALIDATE_PID" 2>/dev/null || true
    wait "$VALIDATE_PID" 2>/dev/null || true

    if [ "$VALIDATE_OK" = true ]; then
        # Strip resource-config again from validation output
        if [ "$KEEP_RESOURCES" = false ] && [ -f "${AGENT_MERGED_DIR}/resource-config.json" ]; then
            rm "${AGENT_MERGED_DIR}/resource-config.json"
        fi
        ./gradlew :stirling-pdf:nativeAgentCopyConfig -PnoSpotless --no-daemon
    fi
    echo ""
fi

# ─────────────────────────────────────────────────────────────────────────────
# Summary
# ─────────────────────────────────────────────────────────────────────────────
echo -e "${GREEN}=================================================${NC}"
echo -e "${GREEN}  Config generation complete!${NC}"
echo -e "${GREEN}=================================================${NC}"
echo ""
echo -e "  Config location: ${CONFIG_DIR}/"
echo ""

# Show final config sizes
echo -e "  ${BLUE}Config files:${NC}"
for f in "${CONFIG_DIR}"/*.json; do
    [ -f "$f" ] || continue
    name=$(basename "$f")
    size=$(du -h "$f" | cut -f1)
    echo -e "    ${name}: ${size}"
done
echo ""

echo -e "  ${BLUE}Next steps:${NC}"
echo -e "  1. Review the generated config files"
echo -e "  2. Commit the updated configs"
echo -e "  3. Build native image:"
echo -e "     ./gradlew :stirling-pdf:nativeCompile -PnoSpotless"
echo ""

#!/bin/bash
# =============================================================================
# Stirling-PDF Native Image Entrypoint
# =============================================================================
# Adapted from docker/unified/entrypoint.sh for GraalVM native binary.
# Key difference: runs /app/stirling-pdf (native binary) instead of java -jar
# No JVM options needed - the binary runs without a JVM!
#
# IMPORTANT: The native binary ships with companion .so files (libawt.so, etc.)
# produced by GraalVM native-image. They live in /app/ alongside the binary and
# are resolved automatically via loadLibraryRelative().
# =============================================================================

set -e

# Default MODE to BOTH if not set
MODE=${MODE:-BOTH}

# Setup native library path for AWT support.
# GraalVM native images resolve companion .so files (libawt.so, liblcms.so, etc.)
# relative to the binary's own directory.  We also add /app to LD_LIBRARY_PATH
# so the dynamic linker can satisfy any transitive shared-lib dependencies.
export LD_LIBRARY_PATH="/app:${LD_LIBRARY_PATH:-}"

# Runtime heap configuration (set via environment variables)
# Heap limits are NOT baked into the binary — this allows tuning per deployment.
# Defaults: -Xms128m -Xmx512m (suitable for most workloads)
NATIVE_HEAP_MIN=${NATIVE_HEAP_MIN:-128m}
NATIVE_HEAP_MAX=${NATIVE_HEAP_MAX:-512m}

echo "==================================="
echo "Stirling-PDF Native Image Container"
echo "MODE: $MODE"
echo "Heap: -Xms${NATIVE_HEAP_MIN} -Xmx${NATIVE_HEAP_MAX}"
echo "==================================="

# Function to setup OCR (from init.sh)
setup_ocr() {
    echo "Setting up OCR languages..."

    TESSDATA_DIR="/usr/share/tessdata"
    mkdir -p "$TESSDATA_DIR"

    if [ -d /usr/share/tessdata-original ]; then
        echo "Restoring system tessdata from backup..."
        cp -rn /usr/share/tessdata-original/* "$TESSDATA_DIR"/ 2>/dev/null || true
    fi

    if [ -n "$TESSERACT_LANGS" ]; then
        SPACE_SEPARATED_LANGS=$(echo $TESSERACT_LANGS | tr ',' ' ')
        for LANG in $SPACE_SEPARATED_LANGS; do
            case "$LANG" in
                [a-zA-Z][a-zA-Z]|[a-zA-Z][a-zA-Z][a-zA-Z]|[a-zA-Z][a-zA-Z][a-zA-Z][a-zA-Z]|[a-zA-Z][a-zA-Z]_[a-zA-Z][a-zA-Z]|[a-zA-Z][a-zA-Z][a-zA-Z]_[a-zA-Z][a-zA-Z][a-zA-Z]|[a-zA-Z][a-zA-Z][a-zA-Z][a-zA-Z]_[a-zA-Z][a-zA-Z][a-zA-Z][a-zA-Z])
                    apt-get update -qq && apt-get install -y --no-install-recommends "tesseract-ocr-$LANG" 2>/dev/null || true
                    ;;
            esac
        done
    fi

    export TESSDATA_PREFIX="$TESSDATA_DIR"
    echo "Using TESSDATA_PREFIX=$TESSDATA_PREFIX"
}

# Function to setup user permissions
setup_permissions() {
    echo "Setting up user permissions..."

    # NOTE: No JAVA_TOOL_OPTIONS needed for native image!
    # Native binaries don't use JVM options.

    if [ ! -z "$PUID" ] && [ "$PUID" != "$(id -u stirlingpdfuser)" ]; then
        usermod -o -u "$PUID" stirlingpdfuser || true
    fi

    if [ ! -z "$PGID" ] && [ "$PGID" != "$(getent group stirlingpdfgroup | cut -d: -f3)" ]; then
        groupmod -o -g "$PGID" stirlingpdfgroup || true
    fi

    umask "$UMASK" || true

    if [[ -n "$LANGS" ]]; then
        /scripts/installFonts.sh $LANGS
    fi

    mkdir -p /tmp/stirling-pdf || true

    # NOTE: Do NOT chown -R /app here — the 480 MB binary already has correct
    # ownership from COPY --chown in the Dockerfile. Re-chowning would be very
    # slow and wasteful on overlay filesystems.
    chown -R stirlingpdfuser:stirlingpdfgroup \
        $HOME /logs /scripts /usr/share/fonts/opentype/noto \
        /configs /customFiles /pipeline /tmp/stirling-pdf \
        2>/dev/null || echo "[WARN] Some chown operations failed, may run as host user"

    chmod -R 755 /logs /scripts /usr/share/fonts/opentype/noto \
        /configs /customFiles /pipeline /tmp/stirling-pdf 2>/dev/null || true
}

# ---------- XDG_RUNTIME_DIR ----------
# Required by LibreOffice for IPC sockets and by dbus.
setup_xdg_runtime() {
    local ruid
    if id -u stirlingpdfuser >/dev/null 2>&1; then
        ruid="$(id -u stirlingpdfuser)"
    else
        ruid="$(id -u)"
    fi
    export XDG_RUNTIME_DIR="/tmp/xdg-${ruid}"
    mkdir -p "${XDG_RUNTIME_DIR}" || true
    if [ "$(id -u)" -eq 0 ]; then
        chown stirlingpdfuser:stirlingpdfgroup "${XDG_RUNTIME_DIR}" 2>/dev/null || true
    fi
    chmod 700 "${XDG_RUNTIME_DIR}" 2>/dev/null || true
    echo "XDG_RUNTIME_DIR=${XDG_RUNTIME_DIR}"
}

# ---------- Xvfb ----------
# Virtual framebuffer required by LibreOffice and Calibre's Qt WebEngine.
start_xvfb() {
    if command -v Xvfb >/dev/null 2>&1; then
        echo "Starting Xvfb on :99"
        Xvfb :99 -screen 0 1024x768x24 -ac +extension GLX +render -noreset > /dev/null 2>&1 &
        export DISPLAY=:99
        sleep 1
    else
        echo "[WARN] Xvfb not installed; LibreOffice/Calibre may fail"
    fi
}

# ---------- LibreOffice profile ----------
# Pre-create user profile dir so LibreOffice doesn't crash on first launch.
setup_libreoffice_profile() {
    local profile_dir="${HOME}/.config/libreoffice/4/user"
    mkdir -p "${profile_dir}" 2>/dev/null || true
    if [ "$(id -u)" -eq 0 ]; then
        chown -R stirlingpdfuser:stirlingpdfgroup "${HOME}/.config" 2>/dev/null || true
    fi
}

# Function to run as user or root
run_as_user() {
    if [ "$(id -u)" = "0" ]; then
        su-exec stirlingpdfuser "$@"
    else
        exec "$@"
    fi
}

run_with_timeout() {
    local secs=$1; shift
    if command -v timeout >/dev/null 2>&1; then
        timeout "${secs}s" "$@"
    else
        "$@"
    fi
}

run_as_user_with_timeout() {
    local secs=$1; shift
    if command -v timeout >/dev/null 2>&1; then
        run_as_user timeout "${secs}s" "$@"
    else
        run_as_user "$@"
    fi
}

tcp_port_check() {
    local host=$1
    local port=$2
    local timeout_secs=${3:-5}
    if command -v nc >/dev/null 2>&1; then
        run_with_timeout "$timeout_secs" nc -z "$host" "$port" 2>/dev/null
        return $?
    fi
    if [ -n "${BASH_VERSION:-}" ] && command -v bash >/dev/null 2>&1; then
        run_with_timeout "$timeout_secs" bash -c "exec 3<>/dev/tcp/${host}/${port}" 2>/dev/null
        local result=$?
        exec 3>&- 2>/dev/null || true
        return $result
    fi
    return 2
}

CONFIG_FILE=${CONFIG_FILE:-/configs/settings.yml}
UNOSERVER_PIDS=()
UNOSERVER_PORTS=()
UNOSERVER_UNO_PORTS=()

read_setting_value() {
    local key=$1
    if [ ! -f "$CONFIG_FILE" ]; then
        return
    fi
    awk -F: -v key="$key" '
        $1 ~ "^[[:space:]]*"key"[[:space:]]*$" {
            val=$2
            sub(/#.*/, "", val)
            gsub(/^[[:space:]]+|[[:space:]]+$/, "", val)
            gsub(/^["'"'"']|["'"'"']$/, "", val)
            print val
            exit
        }
    ' "$CONFIG_FILE"
}

get_unoserver_auto() {
    if [ -n "${PROCESS_EXECUTOR_AUTO_UNO_SERVER:-}" ]; then
        echo "$PROCESS_EXECUTOR_AUTO_UNO_SERVER"; return
    fi
    if [ -n "${UNO_SERVER_AUTO:-}" ]; then
        echo "$UNO_SERVER_AUTO"; return
    fi
    read_setting_value "autoUnoServer"
}

get_unoserver_count() {
    if [ -n "${PROCESS_EXECUTOR_SESSION_LIMIT_LIBRE_OFFICE_SESSION_LIMIT:-}" ]; then
        echo "$PROCESS_EXECUTOR_SESSION_LIMIT_LIBRE_OFFICE_SESSION_LIMIT"; return
    fi
    if [ -n "${UNO_SERVER_COUNT:-}" ]; then
        echo "$UNO_SERVER_COUNT"; return
    fi
    read_setting_value "libreOfficeSessionLimit"
}

start_unoserver_instance() {
    local port=$1
    local uno_port=$2
    run_as_user /opt/venv/bin/unoserver --port "$port" --interface 127.0.0.1 --uno-port "$uno_port" &
    LAST_UNOSERVER_PID=$!
}

start_unoserver_watchdog() {
    local interval=${UNO_SERVER_HEALTH_INTERVAL:-30}
    case "$interval" in
        ''|*[!0-9]*) interval=30 ;;
    esac
    (
        while true; do
            local i=0
            while [ "$i" -lt "${#UNOSERVER_PIDS[@]}" ]; do
                local pid=${UNOSERVER_PIDS[$i]}
                local port=${UNOSERVER_PORTS[$i]}
                local uno_port=${UNOSERVER_UNO_PORTS[$i]}
                local needs_restart=false

                if [ -z "$pid" ] || ! kill -0 "$pid" 2>/dev/null; then
                    echo "unoserver PID ${pid} not found for port ${port}"
                    needs_restart=true
                else
                    local health_ok=false
                    if command -v unoping >/dev/null 2>&1; then
                        if run_as_user_with_timeout 5 unoping --host 127.0.0.1 --port "$port" >/dev/null 2>&1; then
                            health_ok=true
                        fi
                    fi
                    if [ "$health_ok" = false ]; then
                        tcp_port_check "127.0.0.1" "$port" 5
                        local tcp_rc=$?
                        if [ $tcp_rc -eq 0 ]; then
                            health_ok=true
                        elif [ $tcp_rc -eq 2 ]; then
                            health_ok=true
                        else
                            needs_restart=true
                        fi
                    fi
                fi

                if [ "$needs_restart" = true ]; then
                    echo "Restarting unoserver on 127.0.0.1:${port} (uno-port ${uno_port})"
                    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
                        kill -TERM "$pid" 2>/dev/null || true
                        sleep 1
                        kill -KILL "$pid" 2>/dev/null || true
                    fi
                    start_unoserver_instance "$port" "$uno_port"
                    UNOSERVER_PIDS[$i]=$LAST_UNOSERVER_PID
                fi
                i=$((i + 1))
            done
            sleep "$interval"
        done
    ) &
}

start_unoserver_pool() {
    local auto
    auto="$(get_unoserver_auto)"
    auto="${auto,,}"
    if [ -z "$auto" ]; then auto="true"; fi
    if [ "$auto" != "true" ]; then
        echo "Skipping local unoserver pool (autoUnoServer=$auto)"
        return
    fi

    local count
    count="$(get_unoserver_count)"
    case "$count" in
        ''|*[!0-9]*) count=1 ;;
    esac
    if [ "$count" -le 0 ]; then count=1; fi

    local i=0
    while [ "$i" -lt "$count" ]; do
        local port=$((2003 + (i * 2)))
        local uno_port=$((2004 + (i * 2)))
        echo "Starting unoserver on 127.0.0.1:${port} (uno-port ${uno_port})"
        UNOSERVER_PORTS+=("$port")
        UNOSERVER_UNO_PORTS+=("$uno_port")
        start_unoserver_instance "$port" "$uno_port"
        UNOSERVER_PIDS+=("$LAST_UNOSERVER_PID")
        i=$((i + 1))
    done

    start_unoserver_watchdog
}

# Setup OCR, permissions, runtime dirs, and virtual display
setup_ocr
setup_permissions
setup_xdg_runtime
setup_libreoffice_profile
start_xvfb

# Frontend is EMBEDDED in the native binary - no MODE needed!
echo "Starting Stirling-PDF Native Binary..."

if [ ! -f "/app/stirling-pdf" ]; then
    echo "ERROR: Native binary not found at /app/stirling-pdf"
    exit 1
fi

if [ ! -x "/app/stirling-pdf" ]; then
    chmod +x /app/stirling-pdf
fi

# Start UnoServer pool FIRST so it's ready when the app needs it.
start_unoserver_pool

# Give unoserver a moment to initialise before starting the app.
# The watchdog will auto-restart it if it still isn't healthy.
sleep 2

# Start the native binary directly on port 8080
# Companion .so files are in /app/ alongside the binary — GraalVM finds them automatically.
run_as_user sh -c "/app/stirling-pdf \
    -Xms${NATIVE_HEAP_MIN} -Xmx${NATIVE_HEAP_MAX} \
    -Djava.awt.headless=true \
    -Dfile.encoding=UTF-8 \
    -Djava.io.tmpdir=/tmp/stirling-pdf \
    -Dserver.port=8080" &
BACKEND_PID=$!

echo "==================================="
echo "✓ NATIVE IMAGE - Ultra-fast startup!"
echo "✓ Frontend & Backend at: http://localhost:8080"
echo "✓ Backend API at: http://localhost:8080/api"
echo "✓ Swagger UI at: http://localhost:8080/swagger-ui/index.html"
echo "✓ No JVM overhead - minimal memory footprint"
echo "==================================="

# Wait for all background processes
wait

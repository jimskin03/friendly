#!/usr/bin/env bash
# Start (or reuse) Xvfb + Openbox on DISPLAY from .env (default :99).
# Does NOT start the Control API — run uvicorn separately.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [[ -f "$ROOT/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env"
  set +a
fi

DISPLAY_NUM="${DISPLAY:-:99}"
# Normalize to :N
if [[ "$DISPLAY_NUM" != :* ]]; then
  DISPLAY_NUM=":${DISPLAY_NUM}"
fi
export DISPLAY="$DISPLAY_NUM"

WIDTH="${SCREEN_WIDTH:-1280}"
HEIGHT="${SCREEN_HEIGHT:-720}"
DEPTH="${SCREEN_DEPTH:-24}"

LOG_DIR="$ROOT/logs"
mkdir -p "$LOG_DIR" "$ROOT/data"
PID_DIR="$ROOT/data/pids"
mkdir -p "$PID_DIR"

display_up() {
  if command -v xdpyinfo >/dev/null 2>&1; then
    DISPLAY="$DISPLAY_NUM" xdpyinfo >/dev/null 2>&1 && return 0
  fi
  N="${DISPLAY_NUM#:}"
  N="${N%%.*}"
  [[ -S "/tmp/.X11-unix/X${N}" ]]
}

if display_up; then
  echo "DISPLAY=$DISPLAY_NUM already available — skipping Xvfb"
else
  if ! command -v Xvfb >/dev/null 2>&1; then
    echo "ERROR: Xvfb not installed. Run scripts/bootstrap-host.sh first." >&2
    exit 1
  fi
  echo "Starting Xvfb on $DISPLAY_NUM (${WIDTH}x${HEIGHT}x${DEPTH})"
  Xvfb "$DISPLAY_NUM" -screen 0 "${WIDTH}x${HEIGHT}x${DEPTH}" -ac \
    >"$LOG_DIR/xvfb.log" 2>&1 &
  echo $! >"$PID_DIR/xvfb.pid"
  # Wait for socket
  for i in $(seq 1 50); do
    if display_up; then
      break
    fi
    sleep 0.1
  done
  if ! display_up; then
    echo "ERROR: Xvfb failed to start — see $LOG_DIR/xvfb.log" >&2
    exit 1
  fi
  echo "Xvfb ready (pid $(cat "$PID_DIR/xvfb.pid"))"
fi

# Openbox
if command -v openbox >/dev/null 2>&1; then
  if pgrep -f "openbox" >/dev/null 2>&1 && display_up; then
    # Heuristic: if openbox already running on this display, skip
    if DISPLAY="$DISPLAY_NUM" xprop -root _NET_SUPPORTING_WM_CHECK >/dev/null 2>&1; then
      echo "Openbox already managing $DISPLAY_NUM"
    else
      echo "Starting Openbox on $DISPLAY_NUM"
      OPENBOX_ARGS=()
      if [[ -f "$ROOT/config/openbox/rc.xml" ]]; then
        OPENBOX_ARGS=(--config-file "$ROOT/config/openbox/rc.xml")
      fi
      DISPLAY="$DISPLAY_NUM" openbox "${OPENBOX_ARGS[@]}" \
        >"$LOG_DIR/openbox.log" 2>&1 &
      echo $! >"$PID_DIR/openbox.pid"
    fi
  else
    echo "Starting Openbox on $DISPLAY_NUM"
    OPENBOX_ARGS=()
    if [[ -f "$ROOT/config/openbox/rc.xml" ]]; then
      OPENBOX_ARGS=(--config-file "$ROOT/config/openbox/rc.xml")
    fi
    DISPLAY="$DISPLAY_NUM" openbox "${OPENBOX_ARGS[@]}" \
      >"$LOG_DIR/openbox.log" 2>&1 &
    echo $! >"$PID_DIR/openbox.pid"
    sleep 0.3
  fi
else
  echo "WARN: openbox not found — continuing without WM"
fi

echo ""
echo "Idle stack up."
echo "  DISPLAY=$DISPLAY_NUM"
echo "  Start API:  source $ROOT/agent/.venv/bin/activate && cd $ROOT/agent && \\"
echo "              uvicorn app.main:app --host \${API_HOST:-127.0.0.1} --port \${API_PORT:-8787}"

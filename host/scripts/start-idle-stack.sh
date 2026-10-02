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
  mkdir -p "$HOME/.config/openbox"
  # Always sync Friendly WM config so decoration/mouse fixes apply without a
  # manual wipe of ~/.config/openbox.
  if [[ -f "$ROOT/config/openbox/rc.xml" ]]; then
    cp "$ROOT/config/openbox/rc.xml" "$HOME/.config/openbox/rc.xml"
  fi
  if [[ -f "$ROOT/config/openbox/menu.xml" ]]; then
    cp "$ROOT/config/openbox/menu.xml" "$HOME/.config/openbox/menu.xml"
  fi

  OPENBOX_ARGS=()
  if [[ -f "$ROOT/config/openbox/rc.xml" ]]; then
    OPENBOX_ARGS=(--config-file "$ROOT/config/openbox/rc.xml")
  fi

  if pgrep -f "openbox" >/dev/null 2>&1 && display_up; then
    # Heuristic: if openbox already running on this display, reconfigure it
    if DISPLAY="$DISPLAY_NUM" xprop -root _NET_SUPPORTING_WM_CHECK >/dev/null 2>&1; then
      echo "Openbox already managing $DISPLAY_NUM — reconfigure with Friendly rc.xml"
      # --reconfigure reloads the config file openbox was started with (and
      # ~/.config/openbox which we just synced).
      DISPLAY="$DISPLAY_NUM" openbox --reconfigure >/dev/null 2>&1 \
        || DISPLAY="$DISPLAY_NUM" openbox --restart >/dev/null 2>&1 \
        || true
    else
      echo "Starting Openbox on $DISPLAY_NUM"
      DISPLAY="$DISPLAY_NUM" openbox "${OPENBOX_ARGS[@]}" \
        >"$LOG_DIR/openbox.log" 2>&1 &
      echo $! >"$PID_DIR/openbox.pid"
    fi
  else
    echo "Starting Openbox on $DISPLAY_NUM"
    DISPLAY="$DISPLAY_NUM" openbox "${OPENBOX_ARGS[@]}" \
      >"$LOG_DIR/openbox.log" 2>&1 &
    echo $! >"$PID_DIR/openbox.pid"
    sleep 0.3
  fi
else
  echo "WARN: openbox not found — continuing without WM"
fi

# Tint2 Desktop Panel (Taskbar & Start Menu)
if command -v tint2 >/dev/null 2>&1 && display_up; then
  if ! pgrep -f "tint2" >/dev/null 2>&1; then
    echo "Starting tint2 panel on $DISPLAY_NUM"
    DISPLAY="$DISPLAY_NUM" tint2 >"$LOG_DIR/tint2.log" 2>&1 &
    echo $! >"$PID_DIR/tint2.pid"
  else
    echo "tint2 panel already running"
  fi
fi

# Chrome on the left, terminal on the right. Safe to run repeatedly.
if [[ -x "$ROOT/agent/.venv/bin/python" ]]; then
  echo "Preparing Chrome and terminal on $DISPLAY_NUM"
  (
    cd "$ROOT/agent"
    DISPLAY="$DISPLAY_NUM" "$ROOT/agent/.venv/bin/python" -c "from app.desktop import prepare_workspace; print(prepare_workspace())"
  ) || echo "WARN: desktop prepare failed — see API logs after the next stream start"
fi

echo ""
echo "Idle stack up."
echo "  DISPLAY=$DISPLAY_NUM"
echo "  Start API:  source $ROOT/agent/.venv/bin/activate && cd $ROOT/agent && \\"
echo "              uvicorn app.main:app --host \${API_HOST:-127.0.0.1} --port \${API_PORT:-8787}"

#!/usr/bin/env bash
# ==============================================================================
# Friendly Host — Turnkey Headless Ubuntu Setup Script
#
# Sets up Xvfb, Openbox, Chromium, x11vnc, noVNC/websockify, and the FastAPI
# Control API with systemd services for 24/7 background operation.
#
# Supported OS: Ubuntu 20.04 LTS, 22.04 LTS, 24.04 LTS, Debian 11/12
# Run with: sudo ./scripts/setup-ubuntu-headless.sh [--serve-api]
#
# The host is installed to /opt/friendly-host (FRIENDLY_INSTALL_DIR), owned by
# the service user, so the units never depend on a private home directory.
# Safe to re-run: code is re-synced, the venv and API token are kept, units are
# rewritten in place.
#
#   --serve-api   also run `tailscale serve --bg --https=8444 http://127.0.0.1:8787`
#                 (never touches 443 / Funnel). Without it, the command is printed.
# ==============================================================================

set -euo pipefail

# 1. Root / Sudo check
if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
  if command -v sudo >/dev/null 2>&1; then
    SUDO=(sudo)
  else
    echo "ERROR: This script must be run as root or with sudo." >&2
    exit 1
  fi
else
  SUDO=()
fi

SERVE_API=false
for arg in "$@"; do
  case "$arg" in
    --serve-api) SERVE_API=true ;;
    -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
    *) echo "ERROR: unknown argument: $arg" >&2; exit 2 ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SOURCE_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
HOST_ROOT="${FRIENDLY_INSTALL_DIR:-/opt/friendly-host}"
API_SERVE_PORT="${FRIENDLY_API_SERVE_PORT:-8444}"

echo "=========================================================="
echo "  Friendly Host — Headless Ubuntu Turnkey Setup"
echo "  Source:  $SOURCE_ROOT"
echo "  Install: $HOST_ROOT"
echo "=========================================================="

export DEBIAN_FRONTEND=noninteractive

# 2. Install apt packages
echo "==> [1/6] Updating apt repositories and installing packages..."
"${SUDO[@]}" apt-get update -y
"${SUDO[@]}" apt-get install -y --no-install-recommends \
  xvfb \
  openbox \
  xterm \
  xdotool \
  scrot \
  imagemagick \
  x11-utils \
  x11-xserver-utils \
  x11vnc \
  novnc \
  websockify \
  python3 \
  python3-venv \
  python3-pip \
  curl \
  ca-certificates \
  fonts-dejavu-core \
  net-tools \
  tint2 \
  rsync \
  openssl \
  || true

# Browser: snap Chromium does not run under a systemd service user on Xvfb,
# so install a .deb browser. amd64: Google Chrome. arm64: a non-snap Chromium
# if the distro has one (Debian), else print what to do.
is_snap_browser() {
  local p real; p="$(command -v "$1" 2>/dev/null)" || return 1
  real="$(readlink -f "$p")"
  [[ "$p" == /snap/* || "$real" == /snap/* ]] && return 0
  head -c 4096 "$real" 2>/dev/null | grep -qaE '/snap/bin/|snap (run|install)'
}
have_deb_browser() {
  local b; for b in google-chrome-stable google-chrome chromium chromium-browser; do
    command -v "$b" >/dev/null 2>&1 && ! is_snap_browser "$b" && return 0
  done; return 1
}
if ! have_deb_browser; then
  ARCH="$(dpkg --print-architecture 2>/dev/null || uname -m)"
  if [[ "$ARCH" == "amd64" || "$ARCH" == "x86_64" ]]; then
    echo "==> Installing Google Chrome (.deb; snap Chromium can't run as a service)..."
    CHROME_DEB="$(mktemp --suffix=.deb)"
    if curl -fsSL -o "$CHROME_DEB" https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb; then
      chmod 644 "$CHROME_DEB"
      "${SUDO[@]}" apt-get install -y "$CHROME_DEB" || echo "  [WARNING] Google Chrome install failed"
    else
      echo "  [WARNING] Could not download Google Chrome"
    fi
    rm -f "$CHROME_DEB"
  else
    # Debian ships a real chromium .deb; Ubuntu's is a snap transitional package.
    if ! grep -qi ubuntu /etc/os-release; then
      "${SUDO[@]}" apt-get install -y --no-install-recommends chromium || true
    fi
    have_deb_browser || echo "  [WARNING] No non-snap browser for $ARCH. Install a .deb Chromium (Debian package or a PPA) and set CHROMIUM_BIN in /etc/friendly-host.env."
  fi
fi

# 3. Create assistant user if not exists
SERVICE_USER="${FRIENDLY_USER:-assistant}"
if ! id "$SERVICE_USER" >/dev/null 2>&1; then
  echo "==> [2/6] Creating service user '$SERVICE_USER'..."
  "${SUDO[@]}" useradd -m -s /bin/bash "$SERVICE_USER" || true
fi

# 3.1 Install (re-sync) the host code into HOST_ROOT, owned by the service user.
# Runtime state (.venv, data, logs, .env) in HOST_ROOT is kept across re-runs.
echo "==> Installing host files to $HOST_ROOT..."
"${SUDO[@]}" mkdir -p "$HOST_ROOT"
if [[ "$(realpath "$SOURCE_ROOT")" != "$(realpath "$HOST_ROOT")" ]]; then
  if command -v rsync >/dev/null 2>&1; then
    "${SUDO[@]}" rsync -a --delete \
      --exclude '.venv/' --exclude 'data/' --exclude 'logs/' --exclude '.env' \
      --exclude '__pycache__/' --exclude '.pytest_cache/' \
      "$SOURCE_ROOT/" "$HOST_ROOT/"
  else
    (cd "$SOURCE_ROOT" && tar --exclude='./agent/.venv' --exclude='./data' --exclude='./logs' \
      --exclude='./.env' --exclude='__pycache__' -cf - .) | "${SUDO[@]}" tar -xf - -C "$HOST_ROOT"
  fi
fi
"${SUDO[@]}" chown -R "$SERVICE_USER:$SERVICE_USER" "$HOST_ROOT"
"${SUDO[@]}" chmod 755 "$HOST_ROOT"

# 4. Set up Python virtual environment
echo "==> [3/6] Setting up Python virtual environment & dependencies..."
VENV_PATH="$HOST_ROOT/agent/.venv"
AS_SERVICE=("${SUDO[@]}" -u "$SERVICE_USER" -H)
[[ ${#SUDO[@]} -eq 0 ]] && AS_SERVICE=(sudo -u "$SERVICE_USER" -H)
if [[ ! -x "$VENV_PATH/bin/python" ]]; then
  "${AS_SERVICE[@]}" python3 -m venv "$VENV_PATH"
fi
"${AS_SERVICE[@]}" "$VENV_PATH/bin/pip" install --upgrade pip --quiet
"${AS_SERVICE[@]}" "$VENV_PATH/bin/pip" install -r "$HOST_ROOT/agent/requirements.txt" --quiet

# 5. Environment configuration (.env & /etc/friendly-host.env)
echo "==> [4/6] Configuring environment and API authentication token..."
"${SUDO[@]}" mkdir -p "$HOST_ROOT/data" "$HOST_ROOT/logs"

# Keep the existing token: /etc first, then the installed and old (repo) .env.
EXISTING_TOKEN=""
for env_file in /etc/friendly-host.env "$HOST_ROOT/.env" "$SOURCE_ROOT/.env"; do
  if [[ -z "$EXISTING_TOKEN" ]] && "${SUDO[@]}" test -f "$env_file"; then
    EXISTING_TOKEN="$("${SUDO[@]}" grep -E '^API_TOKEN=' "$env_file" | cut -d= -f2- | tr -d ' "\r\n' || true)"
  fi
done
# Keep optional operator settings across re-runs.
EXTRA_ENV=""
if "${SUDO[@]}" test -f /etc/friendly-host.env; then
  EXTRA_ENV="$("${SUDO[@]}" grep -E '^(TAILSCALE_SERVE_PORT|TAILSCALE_SERVE_FALLBACK_PORTS|TAILSCALE_VIEWER_URL|TUNNEL_MODE|CHROMIUM_BIN)=' /etc/friendly-host.env || true)"
fi

if [[ -z "$EXISTING_TOKEN" || "$EXISTING_TOKEN" == *"change-me"* ]]; then
  GENERATED_TOKEN="$(openssl rand -hex 24 2>/dev/null || head -c 48 /dev/urandom | xxd -p -c 48)"
else
  GENERATED_TOKEN="$EXISTING_TOKEN"
fi

ENV_CONTENT="# Friendly Host Environment Configuration
API_TOKEN=$GENERATED_TOKEN
API_HOST=0.0.0.0
API_PORT=8787
DISPLAY=:99
SCREEN_WIDTH=1280
SCREEN_HEIGHT=720
SCREEN_DEPTH=24
CHROMIUM_PROFILE_DIR=$HOST_ROOT/data/chromium-profile
VNC_BIND=127.0.0.1
VNC_PORT=5999
NOVNC_PORT=6099
STREAM_TTL_SECONDS=1800
"
if [[ "$EXTRA_ENV" != *TUNNEL_MODE=* ]]; then ENV_CONTENT+="TUNNEL_MODE=auto
"; fi
[[ -n "$EXTRA_ENV" ]] && ENV_CONTENT+="$EXTRA_ENV
"

printf '%s' "$ENV_CONTENT" | "${SUDO[@]}" tee /etc/friendly-host.env >/dev/null
"${SUDO[@]}" chmod 600 /etc/friendly-host.env
"${SUDO[@]}" chown root:root /etc/friendly-host.env
"${SUDO[@]}" rm -f "$HOST_ROOT/.env"   # single source of truth: /etc/friendly-host.env
# 5.0 Resolve the browser exactly like the agent does (non-snap only).
BROWSER_BIN="$(cd "$HOST_ROOT/agent" && "${AS_SERVICE[@]}" env CHROMIUM_BIN="${CHROMIUM_BIN:-google-chrome-stable}" "$VENV_PATH/bin/python" -m app.browser 2>/tmp/friendly-browser.err || true)"
if [[ -z "$BROWSER_BIN" ]]; then
  echo "  [WARNING] $(cat /tmp/friendly-browser.err 2>/dev/null)"
  BROWSER_BIN="google-chrome-stable"
fi
rm -f /tmp/friendly-browser.err
echo "==> Desktop browser: $BROWSER_BIN"
# Launchers (menu + taskbar) use the resolved binary, never the snap wrapper.
"${SUDO[@]}" sed -i "s#<command>chromium --no-sandbox</command>#<command>$BROWSER_BIN --no-sandbox</command>#" "$HOST_ROOT/config/openbox/menu.xml"
"${SUDO[@]}" mkdir -p /usr/local/share/applications
printf '[Desktop Entry]\nType=Application\nName=Browser\nExec=%s --no-sandbox %%U\nIcon=google-chrome\nCategories=Network;WebBrowser;\n' "$BROWSER_BIN" \
  | "${SUDO[@]}" tee /usr/local/share/applications/friendly-browser.desktop >/dev/null
TINT2RC="/home/$SERVICE_USER/.config/tint2/tint2rc"
"${SUDO[@]}" mkdir -p "$(dirname "$TINT2RC")"
if ! "${SUDO[@]}" test -f "$TINT2RC" || "${SUDO[@]}" grep -q 'friendly-managed' "$TINT2RC"; then
  {
    [[ -f /etc/xdg/tint2/tint2rc ]] && sed '/^launcher_item_app/d' /etc/xdg/tint2/tint2rc
    echo "# friendly-managed launchers (rewritten by setup-ubuntu-headless.sh)"
    echo "launcher_item_app = /usr/local/share/applications/friendly-browser.desktop"
    echo "launcher_item_app = /usr/share/applications/debian-xterm.desktop"
  } | "${SUDO[@]}" tee "$TINT2RC" >/dev/null
fi

# 5.1 Configure Openbox application menus and theme
echo "==> Configuring Openbox application menu and taskbar..."
"${SUDO[@]}" mkdir -p "/home/$SERVICE_USER/.config/openbox" "/etc/xdg/openbox"
if [[ -f "$HOST_ROOT/config/openbox/rc.xml" ]]; then
  "${SUDO[@]}" cp "$HOST_ROOT/config/openbox/rc.xml" "/home/$SERVICE_USER/.config/openbox/rc.xml"
  "${SUDO[@]}" cp "$HOST_ROOT/config/openbox/rc.xml" "/etc/xdg/openbox/rc.xml"
fi
if [[ -f "$HOST_ROOT/config/openbox/menu.xml" ]]; then
  "${SUDO[@]}" cp "$HOST_ROOT/config/openbox/menu.xml" "/home/$SERVICE_USER/.config/openbox/menu.xml"
  "${SUDO[@]}" cp "$HOST_ROOT/config/openbox/menu.xml" "/etc/xdg/openbox/menu.xml"
fi
"${SUDO[@]}" chown -R "$SERVICE_USER:$SERVICE_USER" "/home/$SERVICE_USER/.config" 2>/dev/null || true
"${SUDO[@]}" chown -R "$SERVICE_USER:$SERVICE_USER" "$HOST_ROOT/data" "$HOST_ROOT/logs"

# 6. Configure systemd units
echo "==> [5/6] Installing and configuring systemd units..."

"${SUDO[@]}" bash -c "cat > /etc/systemd/system/friendly-xvfb.service" <<EOF
[Unit]
Description=Friendly Host Xvfb Display Server (:99)
After=network.target

[Service]
Type=simple
User=$SERVICE_USER
EnvironmentFile=-/etc/friendly-host.env
Environment=DISPLAY=:99
ExecStart=/usr/bin/Xvfb :99 -screen 0 1280x720x24 -ac
Restart=on-failure
RestartSec=2

[Install]
WantedBy=multi-user.target
EOF

"${SUDO[@]}" bash -c "cat > /etc/systemd/system/friendly-openbox.service" <<EOF
[Unit]
Description=Friendly Host Openbox Window Manager
After=friendly-xvfb.service
Requires=friendly-xvfb.service

[Service]
Type=simple
User=$SERVICE_USER
EnvironmentFile=-/etc/friendly-host.env
Environment=DISPLAY=:99
ExecStart=/usr/bin/openbox --config-file $HOST_ROOT/config/openbox/rc.xml
Restart=on-failure
RestartSec=2

[Install]
WantedBy=multi-user.target
EOF

"${SUDO[@]}" bash -c "cat > /etc/systemd/system/friendly-tint2.service" <<EOF
[Unit]
Description=Friendly Host Tint2 Desktop Taskbar
After=friendly-openbox.service
Requires=friendly-openbox.service
ConditionPathExists=/usr/bin/tint2

[Service]
Type=simple
User=$SERVICE_USER
EnvironmentFile=-/etc/friendly-host.env
Environment=DISPLAY=:99
ExecStart=/usr/bin/tint2 -c /home/$SERVICE_USER/.config/tint2/tint2rc
Restart=on-failure
RestartSec=2

[Install]
WantedBy=multi-user.target
EOF

"${SUDO[@]}" bash -c "cat > /etc/systemd/system/friendly-agent.service" <<EOF
[Unit]
Description=Friendly Host Control API & MCP Server
After=network.target friendly-xvfb.service friendly-openbox.service
Wants=friendly-xvfb.service friendly-openbox.service

[Service]
Type=simple
User=$SERVICE_USER
WorkingDirectory=$HOST_ROOT/agent
EnvironmentFile=-/etc/friendly-host.env
Environment=DISPLAY=:99
ExecStart=$VENV_PATH/bin/uvicorn app.main:app --host 0.0.0.0 --port 8787
Restart=on-failure
RestartSec=3

[Install]
WantedBy=multi-user.target
EOF

# Preflight: the service user must reach WorkingDirectory and every ExecStart
# binary (a 750 home directory makes systemd fail with status=200/CHDIR).
preflight_fail=false
check_as_service() {
  local kind="$1" path="$2"
  if ! "${AS_SERVICE[@]}" test "$kind" "$path"; then
    echo "ERROR: service user '$SERVICE_USER' cannot access $path" >&2
    preflight_fail=true
  fi
}
check_as_service -x "$HOST_ROOT/agent"
check_as_service -r "$HOST_ROOT/agent/app/main.py"
check_as_service -x "$VENV_PATH/bin/uvicorn"
check_as_service -r "$HOST_ROOT/config/openbox/rc.xml"
check_as_service -x /usr/bin/Xvfb
check_as_service -x /usr/bin/openbox
if [[ "$preflight_fail" == true ]]; then
  echo "ERROR: preflight failed. Fix ownership/permissions above (or set FRIENDLY_INSTALL_DIR to a" >&2
  echo "       directory '$SERVICE_USER' can traverse) and re-run this script." >&2
  exit 1
fi

# Reload and enable services
"${SUDO[@]}" systemctl daemon-reload
"${SUDO[@]}" systemctl enable friendly-xvfb friendly-openbox friendly-tint2 friendly-agent || true
"${SUDO[@]}" systemctl restart friendly-xvfb friendly-openbox friendly-tint2 friendly-agent || true

echo "==> [6/6] Verifying services..."
sleep 2

# Probe health
HEALTH_OK=false
for i in {1..10}; do
  if curl -sfS http://127.0.0.1:8787/health >/dev/null 2>&1; then
    HEALTH_OK=true
    break
  fi
  sleep 1
done

# IP discovery
TAILSCALE_IP=""
TS_DNS=""
if command -v tailscale >/dev/null 2>&1; then
  TAILSCALE_IP="$(tailscale ip -4 2>/dev/null | head -n1 || true)"
  TS_DNS="$(tailscale status --json 2>/dev/null | python3 -c 'import json,sys; print(json.load(sys.stdin)["Self"]["DNSName"].rstrip("."))' 2>/dev/null || true)"
fi
API_SERVE_CMD="sudo tailscale serve --bg --https=${API_SERVE_PORT} http://127.0.0.1:8787"
if [[ "$SERVE_API" == true && -n "$TS_DNS" ]]; then
  echo "==> Exposing the Control API on Tailscale HTTPS :${API_SERVE_PORT} (443/Funnel untouched)..."
  "${SUDO[@]}" tailscale serve --bg --https="${API_SERVE_PORT}" http://127.0.0.1:8787 || \
    echo "  [WARNING] tailscale serve failed; run manually: $API_SERVE_CMD"
fi

LOCAL_IP="$(hostname -I 2>/dev/null | awk '{print $1}' || echo "127.0.0.1")"

echo ""
echo "=========================================================="
if [ "$HEALTH_OK" = true ]; then
  echo "  [SUCCESS] Friendly Host is RUNNING and HEALTHY!"
else
  echo "  [WARNING] Host services started; health probe timed out."
  echo "  Check logs: sudo journalctl -u friendly-agent -n 40"
fi
echo "=========================================================="
echo ""
echo "  Installed to: $HOST_ROOT   (re-run this script after git pull to update)"
echo ""
echo "  Configuration for Friendly Android App:"
echo "  ----------------------------------------"
if [[ -n "$TS_DNS" ]]; then
  echo "  Base URL (Tailscale HTTPS, recommended): https://${TS_DNS}:${API_SERVE_PORT}"
  if [[ "$SERVE_API" != true ]]; then
    echo "    -> first expose it (does not touch 443/Funnel):"
    echo "       $API_SERVE_CMD"
  fi
fi
if [[ -n "$TAILSCALE_IP" ]]; then
  echo "  Base URL (Tailscale IP, nightly only):   http://${TAILSCALE_IP}:8787"
fi
echo "  Base URL (LAN/Local, nightly only):      http://${LOCAL_IP}:8787"
echo "  API Bearer Token:  sudo grep API_TOKEN /etc/friendly-host.env"
echo ""
echo "  Desktop viewer: served by the agent on Tailscale HTTPS :8443 (next free port if taken)."
echo "  If the viewer is black/unreachable, set TAILSCALE_VIEWER_URL in /etc/friendly-host.env."
echo "=========================================================="

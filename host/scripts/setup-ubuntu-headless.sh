#!/usr/bin/env bash
# ==============================================================================
# Friendly Host — Turnkey Headless Ubuntu Setup Script
#
# Sets up Xvfb, Openbox, Chromium, x11vnc, noVNC/websockify, and the FastAPI
# Control API with systemd services for 24/7 background operation.
#
# Supported OS: Ubuntu 20.04 LTS, 22.04 LTS, 24.04 LTS, Debian 11/12
# Run with: sudo ./scripts/setup-ubuntu-headless.sh
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

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HOST_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "=========================================================="
echo "  Friendly Host — Headless Ubuntu Turnkey Setup"
echo "  Target directory: $HOST_ROOT"
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
  || true

# Chromium package detection (Ubuntu 24.04 uses snap or deb, fallback gracefully)
if ! command -v chromium >/dev/null 2>&1 && ! command -v chromium-browser >/dev/null 2>&1 && ! command -v google-chrome >/dev/null 2>&1; then
  echo "==> Installing Chromium browser..."
  "${SUDO[@]}" apt-get install -y --no-install-recommends chromium || \
  "${SUDO[@]}" apt-get install -y --no-install-recommends chromium-browser || true
fi

# 3. Create assistant user if not exists
SERVICE_USER="${FRIENDLY_USER:-assistant}"
if ! id "$SERVICE_USER" >/dev/null 2>&1; then
  echo "==> [2/6] Creating service user '$SERVICE_USER'..."
  "${SUDO[@]}" useradd -m -s /bin/bash "$SERVICE_USER" || true
fi

# 4. Set up Python virtual environment
echo "==> [3/6] Setting up Python virtual environment & dependencies..."
VENV_PATH="$HOST_ROOT/agent/.venv"
if [[ ! -d "$VENV_PATH" ]]; then
  python3 -m venv "$VENV_PATH"
fi
"$VENV_PATH/bin/pip" install --upgrade pip --quiet
"$VENV_PATH/bin/pip" install -r "$HOST_ROOT/agent/requirements.txt" --quiet

# 5. Environment configuration (.env & /etc/friendly-host.env)
echo "==> [4/6] Configuring environment and API authentication token..."
mkdir -p "$HOST_ROOT/data" "$HOST_ROOT/logs"

EXISTING_TOKEN=""
if [[ -f "$HOST_ROOT/.env" ]]; then
  EXISTING_TOKEN="$(grep -E '^API_TOKEN=' "$HOST_ROOT/.env" | cut -d= -f2- | tr -d ' "\r\n' || true)"
fi
if [[ -z "$EXISTING_TOKEN" && -f "/etc/friendly-host.env" ]]; then
  EXISTING_TOKEN="$(grep -E '^API_TOKEN=' "/etc/friendly-host.env" | cut -d= -f2- | tr -d ' "\r\n' || true)"
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
TUNNEL_MODE=auto
"

echo "$ENV_CONTENT" > "$HOST_ROOT/.env"
"${SUDO[@]}" cp "$HOST_ROOT/.env" /etc/friendly-host.env
"${SUDO[@]}" chmod 600 "$HOST_ROOT/.env" /etc/friendly-host.env
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
"${SUDO[@]}" chown -R "$SERVICE_USER:$SERVICE_USER" "$HOST_ROOT" 2>/dev/null || true

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
ExecStart=/usr/bin/tint2
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
if command -v tailscale >/dev/null 2>&1; then
  TAILSCALE_IP="$(tailscale ip -4 2>/dev/null || true)"
fi

LOCAL_IP="$(hostname -I 2>/dev/null | awk '{print $1}' || echo "127.0.0.1")"

echo ""
echo "=========================================================="
if [ "$HEALTH_OK" = true ]; then
  echo "  [SUCCESS] Friendly Host is RUNNING and HEALTHY!"
else
  echo "  [WARNING] Host services started; health probe timed out."
  echo "  Check logs: sudo journalctl -u friendly-agent -n 20"
fi
echo "=========================================================="
echo ""
echo "  Configuration for Friendly Android App:"
echo "  ----------------------------------------"
if [[ -n "$TAILSCALE_IP" ]]; then
  echo "  Base URL (Tailscale):  http://${TAILSCALE_IP}:8787"
fi
echo "  Base URL (LAN/Local):  http://${LOCAL_IP}:8787"
echo "  API Bearer Token:      $GENERATED_TOKEN"
echo ""
echo "  Next Steps in Friendly App:"
echo "  1. Tap 💻 Computer button or go to Settings -> Preferences -> Network"
echo "  2. Enter the Base URL and Bearer Token shown above"
echo "  3. Ready! In-app desktop streaming and AI tools are fully active"
echo "=========================================================="

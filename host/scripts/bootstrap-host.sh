#!/usr/bin/env bash
# Install packages for the Friendly companion host idle stack (Phase 1).
# Safe to re-run. Requires root (sudo) for apt packages.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
echo "==> Friendly host bootstrap (root=$ROOT)"

if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
  if command -v sudo >/dev/null 2>&1; then
    SUDO=(sudo)
  else
    echo "ERROR: run as root or install sudo" >&2
    exit 1
  fi
else
  SUDO=()
fi

export DEBIAN_FRONTEND=noninteractive

echo "==> apt update + install display / input / browser deps"
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
  chromium \
  x11vnc \
  novnc \
  websockify \
  python3 \
  python3-venv \
  python3-pip \
  curl \
  ca-certificates \
  fonts-dejavu-core \
  tint2 \
  || "${SUDO[@]}" apt-get install -y --no-install-recommends \
    xvfb openbox xterm xdotool scrot imagemagick x11-utils x11-xserver-utils \
    chromium-browser x11vnc novnc websockify \
    python3 python3-venv python3-pip curl ca-certificates fonts-dejavu-core tint2

echo "==> stream packages (x11vnc / novnc / websockify) required for Phase 3"

echo "==> Python venv + Control API deps"
VENV="$ROOT/agent/.venv"
python3 -m venv "$VENV"
# shellcheck disable=SC1091
source "$VENV/bin/activate"
pip install --upgrade pip
pip install -r "$ROOT/agent/requirements.txt"

if [[ ! -f "$ROOT/.env" ]]; then
  cp "$ROOT/.env.example" "$ROOT/.env"
  # Generate a random token if still default
  if grep -q 'change-me-to-a-long-random-secret' "$ROOT/.env"; then
    TOKEN="$(openssl rand -hex 24 2>/dev/null || head -c 48 /dev/urandom | xxd -p -c 48)"
    sed -i "s/change-me-to-a-long-random-secret/$TOKEN/" "$ROOT/.env"
    echo "==> wrote random API_TOKEN into $ROOT/.env"
  fi
else
  echo "==> .env already exists — leaving unchanged"
fi

# Profile dir for Chromium (may need sudo)
PROFILE_DIR="${CHROMIUM_PROFILE_DIR:-/var/lib/assistant/chromium-profile}"
if [[ ! -d "$PROFILE_DIR" ]]; then
  "${SUDO[@]}" mkdir -p "$PROFILE_DIR" 2>/dev/null || mkdir -p "$ROOT/data/chromium-profile"
  if [[ -d "$PROFILE_DIR" ]]; then
    "${SUDO[@]}" chown "$(id -u):$(id -g)" "$PROFILE_DIR" 2>/dev/null || true
  else
    # Fall back to project-local profile and patch .env
    LOCAL_PROFILE="$ROOT/data/chromium-profile"
    mkdir -p "$LOCAL_PROFILE"
    if grep -q '^CHROMIUM_PROFILE_DIR=' "$ROOT/.env"; then
      sed -i "s|^CHROMIUM_PROFILE_DIR=.*|CHROMIUM_PROFILE_DIR=$LOCAL_PROFILE|" "$ROOT/.env"
    else
      echo "CHROMIUM_PROFILE_DIR=$LOCAL_PROFILE" >> "$ROOT/.env"
    fi
    echo "==> using local Chromium profile: $LOCAL_PROFILE"
  fi
fi

mkdir -p "$ROOT/data" "$ROOT/logs"

echo ""
echo "Bootstrap complete."
echo "  Next:  $ROOT/scripts/start-idle-stack.sh"
echo "  Then:  source $VENV/bin/activate && cd $ROOT/agent && uvicorn app.main:app --host 127.0.0.1 --port 8787"
echo "  Smoke: $ROOT/scripts/smoke-test-api.sh"

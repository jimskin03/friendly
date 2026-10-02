# Friendly Host — Headless Ubuntu Setup Guide

This guide explains how to set up the **Friendly Linux Host** on any remote headless Ubuntu machine (cloud VPS, dedicated server, or home lab) to enable the **Computer Use** feature in the Friendly Android app.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                       Ubuntu Server                         │
│                                                             │
│   ┌──────────────┐      ┌─────────────┐     ┌───────────┐   │
│   │ Xvfb (:99)   │ ───> │   Openbox   │ ──> │ Chromium  │   │
│   │ Virtual Disp │      │ Window Mgr  │     │  Browser  │   │
│   └──────────────┘      └─────────────┘     └───────────┘   │
│          ▲                     ▲                            │
│          │                     │                            │
│   ┌──────────────┐      ┌─────────────┐                     │
│   │   x11vnc     │      │   xdotool   │                     │
│   │  (port 5999) │      │  (Actions)  │                     │
│   └──────────────┘      └─────────────┘                     │
│          │                     ▲                            │
│          ▼                     │                            │
│   ┌──────────────┐      ┌─────────────┐                     │
│   │ websockify + │      │   FastAPI   │                     │
│   │    noVNC     │ <─── │ Control API │ <── HTTP REST / MCP │
│   │  (port 6099) │      │ (port 8787) │                     │
│   └──────────────┘      └─────────────┘                     │
└────────────────────────────────▲────────────────────────────┘
                                 │ Tailscale / Tunnel / LAN
                                 │
                     ┌───────────────────────┐
                     │ Friendly Android App  │
                     │  - In-App Live Stream │
                     │  - Grok-bot AI Tools  │
                     └───────────────────────┘
```

The stack operates in **Idle mode** (very low CPU usage ~0.1%) until you or the AI interacts with it:
- **Xvfb**: Virtual display running in memory (`DISPLAY=:99`, 1280x720).
- **Openbox**: Lightweight X11 window manager with Applications Menu (Right-Click, `Super` key, or Menu quick action).
- **tint2**: Sleek bottom desktop taskbar displaying active application windows and launcher.
- **FastAPI Control API (`:8787`)**: Exposes REST endpoints (`/v1/actions/*`, `/v1/desktop/launch`) and MCP tools (`/mcp`) secured with a Bearer Token.
- **On-Demand Streaming (`:6099`)**: Starts `x11vnc` and `noVNC/websockify` on demand when you view the desktop, shutting down automatically after inactivity to save bandwidth.

---

## Server Recommendations

Any fresh Ubuntu installation will work:
- **Ubuntu 22.04 LTS (Jammy)** or **Ubuntu 24.04 LTS (Noble)** (x86_64 or ARM64)
- **Minimum Specs**: 1 vCPU, 1 GB RAM (2 GB recommended if browsing modern web pages in Chromium)
- **Providers**:
  - **Oracle Cloud Free Tier** (4 Ampere ARM cores + 24GB RAM free forever)
  - **Hetzner Cloud** (CX22 / CAX11 ~€3.50/mo)
  - **DigitalOcean / Linode / Vultr** ($4-$5/mo droplet)
  - **AWS EC2** (t4g.small / t3.small)
  - **Local Home Server / Proxmox VM / Raspberry Pi 4/5**

---

## Option 1: Turnkey Automated Setup (Recommended)

The easiest method is using the turnkey setup script.

### 1. Clone repository & run script
SSH into your Ubuntu server and run:

```bash
# Clone the repository
git clone https://github.com/jimskin03/friendly.git
cd friendly/host

# Run the automated installer with sudo
sudo ./scripts/setup-ubuntu-headless.sh
```

### 2. What the script does automatically
1. Installs all required packages (`xvfb`, `openbox`, `xdotool`, `scrot`, `imagemagick`, `x11vnc`, `novnc`, `websockify`, `chromium-browser`, `python3-venv`).
2. Configures a Python virtual environment and installs agent dependencies.
3. Generates a secure random 48-character `API_TOKEN`.
4. Creates and starts systemd background services (`friendly-xvfb`, `friendly-openbox`, `friendly-agent`) so they stay running across reboots.
5. Performs a self-test and prints your **API Base URL** and **API Bearer Token**.

---

## Option 2: Manual Step-by-Step Setup

If you prefer to configure everything manually:

### 1. Install system packages
```bash
sudo apt-get update -y
sudo apt-get install -y --no-install-recommends \
  xvfb openbox xterm xdotool scrot imagemagick \
  x11-utils x11-xserver-utils x11vnc novnc websockify \
  chromium-browser python3 python3-venv python3-pip curl ca-certificates fonts-dejavu-core
```

### 2. Set up Python environment
```bash
cd friendly/host
python3 -m venv agent/.venv
source agent/.venv/bin/activate
pip install -r agent/requirements.txt
```

### 3. Configure environment
```bash
cp .env.example .env
# Edit .env and set your own secure API_TOKEN
nano .env
```

### 4. Start the stack
In separate terminals or using tmux/systemd:
```bash
# Terminal 1: Display & Window Manager
./scripts/start-idle-stack.sh

# Terminal 2: Control API
source agent/.venv/bin/activate
cd agent
set -a && source ../.env && set +a
uvicorn app.main:app --host 0.0.0.0 --port 8787
```

---

## Option 3: Docker Deployment

If you prefer Docker, you can run the host stack in a container:

```bash
cd friendly/host

# Copy and edit .env
cp .env.example .env

# Start with Docker Compose
docker compose up -d
```

---

## Networking: Connecting Phone to Remote Server

To allow Friendly on your Android phone to reach the server securely without exposing unauthenticated ports to the public internet, use one of the following methods:

### Method A: Tailscale (Strongly Recommended — 100% Free & Zero-Config)

Tailscale creates a private, encrypted peer-to-peer wireguard mesh between your phone and your server. It requires **zero port forwarding** and works behind NAT/firewalls.

1. **Install Tailscale on Ubuntu**:
   ```bash
   curl -fsSL https://tailscale.com/install.sh | sh
   sudo tailscale up
   ```
   Follow the link in your terminal to log in (Google, GitHub, or Microsoft account).

2. **Install Tailscale on Android**:
   - Download **Tailscale** from Google Play Store or F-Droid.
   - Log into the **same** account.
   - Tap "Connect".

3. **Get your Server's Tailscale IP**:
   On your server run:
   ```bash
   tailscale ip -4
   # Example output: 100.105.42.18
   ```

4. **Your Friendly Configuration**:
   - **Control API Base URL**: `http://100.105.42.18:8787`
   - **Bearer Token**: The `API_TOKEN` generated during setup.

---

### Method B: Cloudflare Tunnel (Custom Domain)

If you have a domain managed by Cloudflare:
```bash
# Install cloudflared
curl -L --output cloudflared.deb https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64.deb
sudo dpkg -i cloudflared.deb

# Authenticate & configure tunnel pointing to http://localhost:8787
cloudflared tunnel login
cloudflared tunnel create friendly-host
cloudflared tunnel route dns friendly-host desktop.yourdomain.com
cloudflared tunnel run --url http://127.0.0.1:8787 friendly-host
```
Then use `https://desktop.yourdomain.com` in Friendly.

---

### Method C: Direct Public IP / Reverse Proxy

If your VPS has a public IP and you wish to use a domain with Caddy (automatic HTTPS):

1. **Install Caddy**:
   ```bash
   sudo apt install -y caddy
   ```
2. **Configure `/etc/caddy/Caddyfile`**:
   ```caddy
   desktop.yourdomain.com {
       reverse_proxy 127.0.0.1:8787
   }
   ```
3. **Restart Caddy**:
   ```bash
   sudo systemctl restart caddy
   ```

---

## Configuring Friendly Android App

Once the host is running and reachable:

1. Open **Friendly 2.0** on your Android phone.
2. In any chat, tap the **💻 Computer** button beside the input field (or go to **Settings → Preferences → Network**).
3. If not yet configured, enter:
   - **Control API Base URL**: e.g., `http://100.x.y.z:8787` (Tailscale) or `http://<your-server-ip>:8787`
   - **Bearer Token**: Paste your generated `API_TOKEN`
4. Tap **Test Connection** — a green checkmark will confirm that the Linux VM is reachable and healthy.
5. Tap **Connect**!

---

## Enabling AI Computer Use (Grok-bot Mode)

To allow an assistant to autonomously control the Linux VM:

1. Go to **Assistants** page and tap on the assistant you want to use (e.g. *Friendly* or *Coding Assistant*).
2. Tap **Local Tools**.
3. Toggle on **Desktop Control (Linux VM)**.
4. Now in chat, you can prompt the AI naturally:
   - *"Open https://github.com/jimskin03/friendly in the browser and check the latest commits."*
   - *"Take a screenshot of the desktop and tell me what windows are open."*
   - *"Click on the search bar in the browser, type 'Antigravity AI', and press Enter."*

The assistant will autonomously invoke `desktop_click`, `desktop_type`, `desktop_hotkey`, and `desktop_screenshot`, rendering visual action cards in the chat while you watch the live stream in real time!

---

## Verifying & Smoke Testing

### Test Control API via curl
```bash
TOKEN=$(grep -E '^API_TOKEN=' /etc/friendly-host.env | cut -d= -f2-)

# 1. Health check
curl -s http://127.0.0.1:8787/health | jq .

# 2. Desktop status
curl -s -H "Authorization: Bearer $TOKEN" http://127.0.0.1:8787/v1/desktop/status | jq .

# 3. Take a screenshot
curl -s -H "Authorization: Bearer $TOKEN" -X POST http://127.0.0.1:8787/v1/actions/screenshot | jq .

# 4. Open a website in Chromium
curl -s -H "Authorization: Bearer $TOKEN" -X POST http://127.0.0.1:8787/v1/browser/open \
  -H "Content-Type: application/json" \
  -d '{"url": "https://news.ycombinator.com"}' | jq .
```

---

## Troubleshooting & FAQ

### 1. How do I view systemd logs?
```bash
# View Control API logs:
sudo journalctl -u friendly-agent -f

# View virtual display logs:
sudo journalctl -u friendly-xvfb -f

# View window manager logs:
sudo journalctl -u friendly-openbox -f
```

### 2. Chromium fails to launch with sandbox error
If running as root or under certain Ubuntu 24.04 setups:
Friendly Host automatically passes `--no-sandbox` and `--disable-infobars` when launching Chromium under Xvfb. Ensure Chromium is installed:
```bash
which chromium || which chromium-browser || which google-chrome
```

### 3. Server has low RAM (e.g. 1GB VPS)
Add a 2GB swap file so Chromium and Python run comfortably:
```bash
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

### 4. Restarting all services
```bash
sudo systemctl restart friendly-xvfb friendly-openbox friendly-agent
```

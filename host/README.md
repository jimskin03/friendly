# Friendly Host — Linux companion Control API

Headless remote-desktop host for the [Friendly](https://github.com/jimskin03/friendly) Android assistant.

**Idle stack:** Xvfb (`DISPLAY=:99`) + Openbox + Chromium  
**Control plane:** Python FastAPI (`agent/`) — screenshot / click / type / hotkey / browser / desktop  
**MCP:** Streamable HTTP at `/mcp` wrapping desktop tools  
**Stream:** on-demand x11vnc + noVNC; Tailscale/CF tunnel when available, else localhost viewer

This tree lives at `friendly/host/` in the monorepo (Android app under `app/`).

- [`SETUP_UBUNTU.md`](SETUP_UBUNTU.md) — **Complete headless Ubuntu setup guide (turnkey installer, systemd, Tailscale)**
- [`docs/architecture.md`](docs/architecture.md) — defaults summary / pointer
- [`examples/DesktopControlClient.kt`](examples/DesktopControlClient.kt) — host-side mirror of the Android Control API client (canonical: `app/.../data/remote/DesktopControlClient.kt`)

---

## Quick start

```bash
cd host   # from repo root: /workspace/friendly/host

# 1) Install OS packages + Python venv (needs sudo for apt)
./scripts/bootstrap-host.sh

# 2) Start Xvfb + Openbox
./scripts/start-idle-stack.sh

# 3) Run Control API
source agent/.venv/bin/activate
cd agent
set -a && source ../.env && set +a
uvicorn app.main:app --host "${API_HOST:-127.0.0.1}" --port "${API_PORT:-8787}"

# 4) Smoke (other terminal)
./scripts/smoke-test-api.sh
./scripts/smoke-test-mcp.sh
./scripts/smoke-test-stream.sh
```

Copy `.env.example` → `.env` if bootstrap did not already. Set a strong `API_TOKEN`.

---

## Environment

| Variable | Default | Meaning |
|---|---|---|
| `API_TOKEN` | (required) | Bearer token for all `/v1/*` routes and `/mcp`. Startup fails if it is empty or the `.env.example` placeholder |
| `DISPLAY` | `:99` | Xvfb display |
| `API_HOST` / `API_PORT` | `127.0.0.1` / `8787` | Bind address |
| `SCREEN_WIDTH` / `HEIGHT` / `DEPTH` | `1280` / `720` / `24` | Xvfb geometry |
| `CHROMIUM_BIN` / `CHROMIUM_PROFILE_DIR` | `chromium` / `/var/lib/assistant/...` | Browser |
| `STREAM_TTL_SECONDS` | `900` | Viewer session TTL (~15 min) |
| `VNC_PORT` / `NOVNC_PORT` | `5999` / `6099` | Localhost x11vnc / websockify+noVNC |
| `TUNNEL_MODE` | `auto` | `localhost` / `tailscale` / `cloudflare` / `cloudflare_quick` |
| `TAILSCALE_SERVE_PORT` | `8443` | Private viewer HTTPS port (`8443` or `10000`; never Funnel-backed `443`) |
| `STREAM_JWT_SECRET` | (from API_TOKEN) | Viewer JWT signing secret |
| `MCP_ALLOWED_HOSTS` | (empty) | Extra Host allowlist for MCP DNS-rebinding (Tailscale IP/hostname) |

---

## Control API

All `/v1/*` routes and `/mcp` require `Authorization: Bearer <API_TOKEN>`.  
`GET /health` is open (for probes).

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/health` | — | `{ ok, display… }` |
| GET | `/v1/desktop/status` | — | display + tool availability |
| POST | `/v1/actions/screenshot` | `{}` | `{ image_b64, mime }` |
| POST | `/v1/actions/click` | `{ x, y, button? }` | `{ ok, x, y, button }` |
| POST | `/v1/actions/type` | `{ text }` | `{ ok, chars }` |
| POST | `/v1/actions/hotkey` | `{ keys: ["ctrl","t"], repeat?: 1..256 }` | `{ ok, keys }` |
| POST | `/v1/browser/open` | `{ url }` | `{ ok, url, pid }` |
| POST | `/v1/desktop/prepare` | — | `{ ok }` (layout helper) |
| POST | `/v1/desktop/launch` | `{ app }` | `{ ok, app }` |
| POST | `/v1/desktop/close` | — | close focused window (WM close / Alt+F4) |
| POST | `/v1/desktop/kill` | `{ target }` | force-kill `focused` / `browser` / `terminal` |
| POST | `/v1/stream/start` | `{ mode? }` | `{ viewer_url, session_id, expires_at, token, tunnel }` |
| POST | `/v1/stream/stop` | `{ session_id? }` | `{ stopped, session_id }` |
| GET | `/v1/stream/status` | — | `{ active, expires_at, mode, viewer_url, … }` |
| GET | `/v1/stream/viewer` | `?token=` | JWT gate (no Bearer) → session URLs |

Example:

```bash
TOKEN=$(grep ^API_TOKEN= .env | cut -d= -f2-)
curl -sS -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -X POST http://127.0.0.1:8787/v1/actions/screenshot | jq -r .image_b64 | base64 -d > /tmp/desk.png
```

Without a live display, action routes return **503** with `{ error: "no_display", message: "…" }`.

---

---

## MCP server (Phase 2)

Same uvicorn process as the Control API. Streamable HTTP endpoint:

```
http://127.0.0.1:8787/mcp
```

Clients may see a redirect to `/mcp/`; keep the Bearer header on follow-up requests. Friendly's Streamable HTTP client should use the `/mcp` URL above.

(Use your Tailscale IP / CF Access URL when the phone is not on localhost.)

### Tools

| Tool | Role |
|---|---|
| `screenshot` | PNG via MCP `ImageContent` |
| `click` | `{ x, y, button? }` |
| `type` | `{ text }` |
| `hotkey` | `{ keys: string[] }` |
| `browser_open` | `{ url }` |
| `stream_status` | idle/active stub |
| `stream_start` / `stream_stop` | **Privileged** — `meta.needsApproval=true`; starts/stops real noVNC session |

In Friendly chat, tools appear as `mcp__friendly_desktop__screenshot` etc. (sanitized server id).

### Register in Friendly (Android)

1. Reachability: phone can HTTPS/HTTP to the host Control API (prefer **Tailscale** on phone + host, or Cloudflare Access in front of the API). Do **not** expose VNC.
2. **Settings → MCP** (or Assistant MCP page): add server:
   - **Name / id:** `friendly-desktop`
   - **Transport:** Streamable HTTP (`McpServerConfig.StreamableHTTPServer`)
   - **URL:** `http://<tailscale-ip>:8787/mcp` (or your Access URL ending in `/mcp`)
   - **Headers:** `Authorization` = `Bearer <API_TOKEN>` (same token as `.env`)
3. Open the **PC assistant** profile → bind that MCP server in `Assistant.mcpServers`.
4. Start a chat; confirm tools appear. Ask the model to take a screenshot / open a URL.

`stream_start` should stay approval-gated in Friendly (`McpTool.needsApproval`). Prefer the in-app **Open desktop** control (via `DesktopControlClient`) over letting the model open tunnels.

### Smoke

```bash
./scripts/smoke-test-mcp.sh
./scripts/smoke-test-stream.sh
```

---

---

## Open desktop / streaming

**Idle:** Xvfb + Openbox + Control API/MCP — no viewer URL, no public tunnel.  
**Open desktop:** `POST /v1/stream/start` → starts **x11vnc** (localhost) + **websockify/noVNC** → returns `viewer_url` + JWT `token` (TTL ~15 min, one session).  
**Stop:** `POST /v1/stream/stop` → kills our VNC/noVNC processes and removes only the app-owned route on its dedicated Tailscale port; other Serve/Funnel config is preserved.

### Localhost smoke (this box)

Default ports **5999** (VNC) / **6099** (noVNC) avoid clashing with other lab displays on 5900/6080.

```bash
./scripts/smoke-test-stream.sh
# viewer: http://127.0.0.1:6099/vnc.html?autoconnect=1&...
```

### Tunnel hooks (no fake public URLs)

| `TUNNEL_MODE` | Behavior |
|---|---|
| `auto` (default) | Try Tailscale if binary present, else Cloudflare if `cloudflared` present, else **localhost** |
| `localhost` | Always return `http://127.0.0.1:NOVNC_PORT/vnc.html?...` |
| `tailscale` | Serve on `TAILSCALE_SERVE_PORT` (default `8443`) using HTTPS, tailnet-only; refuse Funnel or conflicting listeners and leave them untouched |
| `cloudflare` | Named tunnel via `CLOUDFLARED_TOKEN` / config; else localhost |
| `cloudflare_quick` | Ephemeral `cloudflared tunnel --url` (parses trycloudflare.com when possible) |

The Tailscale viewer uses a dedicated port so it does not replace a Funnel or another service on `:443`. Cleanup removes only the app-owned root route on that port; it never runs `tailscale serve reset`.

### Friendly Android client notes

The production client lives in the app:

- Canonical: [`app/src/main/java/me/rerere/rikkahub/data/remote/DesktopControlClient.kt`](../app/src/main/java/me/rerere/rikkahub/data/remote/DesktopControlClient.kt)
- Host mirror (for docs / API parity): [`examples/DesktopControlClient.kt`](examples/DesktopControlClient.kt)

All `/v1/*` calls use `Authorization: Bearer <API_TOKEN>` (same token as host `.env`). `GET /health` is unauthenticated.

1. Register the MCP server (above) for agent control tools.
2. Point the app Network settings at the host (`desktopControlBaseUrl` + `desktopControlApiToken`).
3. **Open desktop** → `POST /v1/stream/start` → open `viewer_url` in Custom Tabs / WebView; **Stop** → `POST /v1/stream/stop` (see also `status`, `screenshot`, `click`, `type`, `hotkey`, `browser/open`, `desktop/prepare`, `desktop/launch`, `desktop/status`).
4. Keep `stream_start` MCP tool `needsApproval`; prefer the human Open desktop control for tunnels/viewers.
5. Reachability: Tailscale on phone+host (or CF Access) for API + viewer — never publish raw VNC.

## Layout

```
host/
├── README.md
├── .env.example
├── docker-compose.yml          # optional lab stack
├── agent/
│   ├── pyproject.toml
│   ├── requirements.txt
│   ├── Dockerfile
│   └── app/
│       ├── main.py             # FastAPI routes
│       ├── auth.py             # Bearer API_TOKEN
│       ├── config.py
│       ├── desktop.py          # screenshot / xdotool
│       ├── browser.py          # Chromium open URL
│       ├── stream.py           # x11vnc + noVNC sessions
│       └── mcp_server.py
├── examples/
│   └── DesktopControlClient.kt # mirror of app Control API client
├── scripts/
│   ├── bootstrap-host.sh
│   ├── start-idle-stack.sh
│   └── smoke-test-api.sh
├── systemd/                    # unit templates
├── config/
├── viewer/novnc/
└── docs/architecture.md
```

---

## systemd (production-ish)

Templates under `systemd/` assume install path `/opt/friendly-host` and user `assistant`. Copy env to `/etc/friendly-host.env`, adjust paths, then:

```bash
sudo cp systemd/*.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now xvfb openbox agent-api
```

---

## Status

| Area | Status |
|---|---|
| Xvfb + Openbox + Control API actions | Implemented |
| MCP server wrapping actions | Done — `/mcp` Streamable HTTP |
| x11vnc + noVNC + JWT + localhost viewer | Done; Tailscale/CF when binaries+creds exist |
| Friendly Android Control API client | In app (`app/.../DesktopControlClient.kt`); host mirror at `examples/DesktopControlClient.kt` |

Do not expose the API or VNC on a public interface without Tailscale / Cloudflare Access.

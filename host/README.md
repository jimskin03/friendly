# Friendly Host — Linux companion Control API

Headless remote-desktop host for the [Friendly](https://github.com/jimskin03/friendly) Android assistant.

**Idle stack:** Xvfb (`DISPLAY=:99`) + Openbox + Chromium  
**Control plane:** Python FastAPI (`agent/`) — screenshot / click / type / hotkey / browser open  
**MCP:** Streamable HTTP at `/mcp` (Phase 2) wrapping desktop tools  
**Stream:** on-demand x11vnc + noVNC (Phase 3); Tailscale/CF tunnel when available, else localhost viewer

This tree is a **standalone host** (`friendly-host/`). It can later sit as `friendly/host/` in a monorepo; the layout matches the plan either way.

Related docs:

- [`docs/architecture.md`](docs/architecture.md) — defaults summary / pointer
- `/workspace/friendly-assistant-plan.md` — phased plan
- `/workspace/assistant-remote-desktop-architecture.md` — full architecture

---

## Quick start

```bash
cd /workspace/friendly-host

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
| `API_TOKEN` | (required) | Bearer token for all `/v1/*` routes |
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
| POST | `/v1/actions/hotkey` | `{ keys: ["ctrl","t"] }` | `{ ok, keys }` |
| POST | `/v1/browser/open` | `{ url }` | `{ ok, url, pid }` |
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

`stream_start` should stay approval-gated in Friendly (`McpTool.needsApproval`). Prefer the future **Open desktop** button (Phase 3) over letting the model open tunnels.

### Smoke

```bash
./scripts/smoke-test-mcp.sh
./scripts/smoke-test-stream.sh
```

---

---

## Open desktop / streaming (Phase 3)

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

1. Add MCP server (Phase 2) for agent control tools.
2. Add `DesktopControlClient` (see [`examples/DesktopControlClient.kt`](examples/DesktopControlClient.kt)):
   - **Open desktop** button → `POST /v1/stream/start` → open `viewer_url` in Custom Tabs / WebView.
   - While `GET /v1/stream/status` is active → show **Stop** → `POST /v1/stream/stop`.
3. Keep `stream_start` MCP tool `needsApproval`; prefer the human button for tunnels/viewers.
4. Reachability: Tailscale on phone+host (or CF Access) for API + viewer — never publish raw VNC.

## Layout

```
friendly-host/
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
│       └── stream.py           # Phase 3 stub
├── scripts/
│   ├── bootstrap-host.sh
│   ├── start-idle-stack.sh
│   └── smoke-test-api.sh
├── systemd/                    # unit templates
├── config/
├── viewer/novnc/               # Phase 3 placeholder
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

## What is stubbed

| Area | Phase | Status |
|---|---|---|
| Xvfb + Openbox + Control API actions | **1** | Implemented |
| MCP server wrapping actions | **2** | Done — `/mcp` Streamable HTTP |
| x11vnc + noVNC + JWT + localhost viewer | **3** | Done; Tailscale/CF when binaries+creds exist |
| Friendly Android Open desktop client | **3** | See `examples/DesktopControlClient.kt` |

Do not expose the API or VNC on a public interface without Tailscale / Cloudflare Access.

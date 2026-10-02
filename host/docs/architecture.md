# Friendly Host — Architecture defaults (pointer)

Full write-up: `/workspace/assistant-remote-desktop-architecture.md`  
Implementation plan: `/workspace/friendly-assistant-plan.md`

## Mental model

> **Idle:** Xvfb `:99` + Openbox + Chromium + FastAPI agent on a locked-down Linux host.  
> **One click (Phase 3):** API starts localhost VNC → noVNC → ephemeral Tailscale/Cloudflare HTTPS URL with short JWT → user watches/controls.  
> **Done:** revoke token, drop tunnel, keep desktop warm for the assistant.

**Design rule:** The assistant controls the machine through the Control API. The stream is a human viewer overlay, not the primary control path.

## Stack defaults

| Layer | Choice |
|---|---|
| Display | Xvfb `DISPLAY=:99`, 1280×720×24 |
| WM | Openbox |
| Browser | Chromium (dedicated profile) |
| Control | Python FastAPI, Bearer `API_TOKEN` |
| OS input | xdotool + ImageMagick `import` / scrot |
| Stream (Phase 3) | x11vnc (127.0.0.1) + noVNC + Tailscale Serve or CF Tunnel |
| Auth (later) | OIDC / CF Access; stream JWT TTL ~15 min |

## MCP endpoint

- Transport: **Streamable HTTP**
- URL: `http://<host>:8787/mcp`
- Auth: `Authorization: Bearer <API_TOKEN>` (same as Control API)
- Tools: `screenshot`, `click`, `type`, `hotkey`, `browser_open`, `stream_status`, `stream_start`/`stream_stop` (privileged / Phase 3 stub)

## Control API surface

```
POST /v1/actions/screenshot  → { image_b64 }
POST /v1/actions/click       → { x, y, button }
POST /v1/actions/type        → { text }
POST /v1/actions/hotkey      → { keys[] }
POST /v1/browser/open        → { url }
POST /v1/stream/start        → { viewer_url, session_id, expires_at }   # Phase 3
POST /v1/stream/stop         → { session_id }
GET  /v1/stream/status       → { active, expires_at, mode }
```

## Phase map

| Phase | Deliverable |
|---|---|
| **1** | Idle stack + Control API actions; stream routes stubbed |
| **2** | Host MCP Streamable HTTP at `/mcp`; register `friendly-desktop` in Friendly |
| **3** (this tree) | x11vnc + noVNC + JWT; localhost viewer; Tailscale/CF hooks; Open desktop client stub |
| **4** | Hardening, OIDC, UX polish |

## Security (MVP)

- Bind Control API to `127.0.0.1` or Tailscale IP only.
- Never publish VNC publicly; localhost + tunnel only.
- Strong `API_TOKEN`; rotate via `.env` / systemd `EnvironmentFile`.
- Stream start is privileged (human button); do not let the model open tunnels without approval.

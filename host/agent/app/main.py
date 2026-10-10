"""Friendly host Control API — FastAPI entrypoint (+ mounted MCP)."""

from __future__ import annotations

import logging
from contextlib import asynccontextmanager
from typing import Literal

from fastapi import Depends, FastAPI, HTTPException, Request, status
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from . import browser, desktop, stream
from .auth import BearerAuthASGIMiddleware, require_bearer
from .config import Settings, get_settings, require_usable_api_token
from .desktop import DesktopError
from .mcp_server import build_mcp_starlette_app, mcp as desktop_mcp
from .stream import StreamError, StreamNotImplemented, verify_viewer_token

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
)
logger = logging.getLogger("friendly-host")

# Build MCP Starlette app at import time so session_manager exists for lifespan.
_mcp_asgi = build_mcp_starlette_app()
_mcp_authed = BearerAuthASGIMiddleware(_mcp_asgi)


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Refuse to serve REST or MCP with an empty or publicly known token.
    require_usable_api_token(get_settings())
    # Mounted sub-app lifespans do not run — enter session manager here.
    async with desktop_mcp.session_manager.run():
        logger.info("MCP session manager started (streamable HTTP at /mcp)")
        yield
    logger.info("MCP session manager stopped")


app = FastAPI(
    title="Friendly Host Control API",
    version="0.3.0",
    description=(
        "Headless desktop control plane for the Friendly assistant companion host. "
        "REST under /v1/*; MCP Streamable HTTP under /mcp."
    ),
    lifespan=lifespan,
)


# --- models ---


class ClickBody(BaseModel):
    x: int
    y: int
    button: Literal["left", "middle", "right", "1", "2", "3"] = "left"


class TypeBody(BaseModel):
    text: str = Field(..., min_length=1)


class HotkeyBody(BaseModel):
    keys: list[str] = Field(..., min_length=1)
    repeat: int = Field(default=1, ge=1, le=256)


class BrowserOpenBody(BaseModel):
    url: str


class LaunchAppBody(BaseModel):
    app: str = "menu"


class KillAppBody(BaseModel):
    target: str = "focused"


class StreamStartBody(BaseModel):
    mode: Literal["view", "interactive"] = "view"


class StreamStopBody(BaseModel):
    session_id: str | None = None


# --- health (no auth) ---


@app.get("/health")
def health(settings: Settings = Depends(get_settings)) -> dict:
    info = desktop.status_info(settings)
    return {
        "ok": True,
        "service": "friendly-host-control-api",
        "version": "0.3.0",
        "mcp": {
            "transport": "streamable-http",
            "path": "/mcp",
            "server_name": "friendly-desktop",
        },
        "display": info,
        "browser": browser.browser_status(settings),
    }


@app.get("/v1/desktop/status")
def desktop_status(
    _: None = Depends(require_bearer),
    settings: Settings = Depends(get_settings),
) -> dict:
    return desktop.status_info(settings)


# --- actions ---


@app.post("/v1/actions/screenshot")
def action_screenshot(
    _: None = Depends(require_bearer),
    settings: Settings = Depends(get_settings),
) -> dict:
    try:
        b64 = desktop.screenshot_b64(settings)
    except DesktopError as e:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE
            if e.code == "no_display"
            else status.HTTP_400_BAD_REQUEST,
            detail={"error": e.code, "message": e.message},
        ) from e
    return {"image_b64": b64, "mime": "image/png"}


@app.post("/v1/actions/click")
def action_click(
    body: ClickBody,
    _: None = Depends(require_bearer),
    settings: Settings = Depends(get_settings),
) -> dict:
    try:
        desktop.click(body.x, body.y, body.button, settings)
    except DesktopError as e:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE
            if e.code == "no_display"
            else status.HTTP_400_BAD_REQUEST,
            detail={"error": e.code, "message": e.message},
        ) from e
    return {"ok": True, "x": body.x, "y": body.y, "button": body.button}


@app.post("/v1/actions/type")
def action_type(
    body: TypeBody,
    _: None = Depends(require_bearer),
    settings: Settings = Depends(get_settings),
) -> dict:
    try:
        desktop.type_text(body.text, settings)
    except DesktopError as e:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE
            if e.code == "no_display"
            else status.HTTP_400_BAD_REQUEST,
            detail={"error": e.code, "message": e.message},
        ) from e
    return {"ok": True, "chars": len(body.text)}


@app.post("/v1/actions/hotkey")
def action_hotkey(
    body: HotkeyBody,
    _: None = Depends(require_bearer),
    settings: Settings = Depends(get_settings),
) -> dict:
    try:
        desktop.hotkey(body.keys, settings, repeat=body.repeat)
    except DesktopError as e:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE
            if e.code == "no_display"
            else status.HTTP_400_BAD_REQUEST,
            detail={"error": e.code, "message": e.message},
        ) from e
    return {"ok": True, "keys": body.keys}


# --- browser ---


@app.post("/v1/browser/open")
def browser_open(
    body: BrowserOpenBody,
    _: None = Depends(require_bearer),
    settings: Settings = Depends(get_settings),
) -> dict:
    try:
        result = browser.open_url(body.url, settings)
    except DesktopError as e:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE
            if e.code in ("viewer_unreachable", "no_display", "missing_tool", "browser_launch")
            else status.HTTP_400_BAD_REQUEST,
            detail={"error": e.code, "message": e.message},
        ) from e
    return {"ok": True, **result}


@app.post("/v1/desktop/launch")
def desktop_launch(
    body: LaunchAppBody,
    _: None = Depends(require_bearer),
    settings: Settings = Depends(get_settings),
) -> dict:
    try:
        result = desktop.launch_app(body.app, settings)
    except DesktopError as e:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE
            if e.code in ("viewer_unreachable", "no_display", "missing_tool")
            else status.HTTP_400_BAD_REQUEST,
            detail={"error": e.code, "message": e.message},
        ) from e
    return {"ok": True, **result}


@app.post("/v1/desktop/prepare")
def desktop_prepare(
    _: None = Depends(require_bearer),
    settings: Settings = Depends(get_settings),
) -> dict:
    """Make sure the desktop is showing Chrome and a terminal."""
    try:
        result = desktop.prepare_workspace(settings)
    except DesktopError as e:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE
            if e.code in ("viewer_unreachable", "no_display", "missing_tool")
            else status.HTTP_400_BAD_REQUEST,
            detail={"error": e.code, "message": e.message},
        ) from e
    return {"ok": True, **result}


@app.post("/v1/desktop/close")
def desktop_close(
    _: None = Depends(require_bearer),
    settings: Settings = Depends(get_settings),
) -> dict:
    """Close the focused window via the window manager (Alt+F4 / windowclose)."""
    try:
        result = desktop.close_focused_window(settings)
    except DesktopError as e:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE
            if e.code in ("viewer_unreachable", "no_display", "missing_tool")
            else status.HTTP_400_BAD_REQUEST,
            detail={"error": e.code, "message": e.message},
        ) from e
    return result


@app.post("/v1/desktop/kill")
def desktop_kill(
    body: KillAppBody,
    _: None = Depends(require_bearer),
    settings: Settings = Depends(get_settings),
) -> dict:
    """Force-kill chrome, terminal, or the focused window."""
    try:
        result = desktop.kill_app(body.target, settings)
    except DesktopError as e:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE
            if e.code in ("viewer_unreachable", "no_display", "missing_tool")
            else status.HTTP_400_BAD_REQUEST,
            detail={"error": e.code, "message": e.message},
        ) from e
    return result


# --- stream ---


@app.post("/v1/stream/start")
def stream_start(
    request: Request,
    body: StreamStartBody | None = None,
    _: None = Depends(require_bearer),
    settings: Settings = Depends(get_settings),
) -> JSONResponse:
    mode = body.mode if body else "view"
    try:
        client = (request.client.host if request.client else "") or ""
        remote = client not in ("127.0.0.1", "::1", "localhost")
        data = stream.stream_manager.start(settings, mode=mode, remote_client=remote)
    except StreamNotImplemented as e:
        return JSONResponse(
            status_code=status.HTTP_501_NOT_IMPLEMENTED,
            content={"error": e.code, "message": e.message, "phase": 3},
        )
    except StreamError as e:
        code = (
            status.HTTP_409_CONFLICT
            if e.code == "already_active"
            else status.HTTP_503_SERVICE_UNAVAILABLE
            if e.code in ("viewer_unreachable", "no_display", "missing_tool", "missing_novnc", "vnc_start_failed", "novnc_start_failed")
            else status.HTTP_400_BAD_REQUEST
        )
        return JSONResponse(
            status_code=code,
            content={"error": e.code, "message": e.message},
        )
    return JSONResponse(content=data)


@app.post("/v1/stream/stop")
def stream_stop(
    body: StreamStopBody | None = None,
    _: None = Depends(require_bearer),
    settings: Settings = Depends(get_settings),
) -> dict:
    sid = body.session_id if body else None
    return stream.stream_manager.stop(sid, settings)


@app.get("/v1/stream/status")
def stream_status(
    _: None = Depends(require_bearer),
    settings: Settings = Depends(get_settings),
) -> dict:
    return stream.stream_manager.status(settings)


@app.get("/v1/stream/viewer")
def stream_viewer_gate(
    token: str,
    settings: Settings = Depends(get_settings),
) -> JSONResponse:
    """Validate a viewer JWT and return the local noVNC URL (Open desktop helper).

    Does not require API Bearer — the JWT is the capability token.
    """
    try:
        claims = verify_viewer_token(token, settings)
    except StreamError as e:
        return JSONResponse(
            status_code=status.HTTP_401_UNAUTHORIZED,
            content={"error": e.code, "message": e.message},
        )
    st = stream.stream_manager.status(settings)
    if not st.get("active") or st.get("session_id") != claims.get("sid"):
        return JSONResponse(
            status_code=status.HTTP_410_GONE,
            content={"error": "session_inactive", "message": "Stream session is not active"},
        )
    return JSONResponse(
        content={
            "ok": True,
            "session_id": claims.get("sid"),
            "mode": claims.get("mode"),
            "expires_at": st.get("expires_at"),
            "viewer_url": st.get("viewer_url"),
            "local_viewer_url": st.get("local_viewer_url"),
        }
    )


# Mount MCP Streamable HTTP at /mcp (Bearer required via ASGI wrapper).
# Public URL for Friendly: http://<host>:8787/mcp
app.mount("/mcp", _mcp_authed)


def create_app() -> FastAPI:
    return app


if __name__ == "__main__":
    import uvicorn

    s = get_settings()
    require_usable_api_token(s)
    uvicorn.run(
        "app.main:app",
        host=s.api_host,
        port=s.api_port,
        reload=False,
    )

"""MCP server wrapping Control API desktop actions (Phase 2).

Exposes Streamable HTTP tools for Friendly's McpServerConfig.StreamableHTTPServer.
Mounted under the same FastAPI process at ``/mcp`` (see main.py).
"""

from __future__ import annotations

import json
import logging
from typing import Any, Literal

from mcp.server.mcpserver import MCPServer
from mcp.server.transport_security import TransportSecuritySettings
from mcp.types import ImageContent, TextContent, ToolAnnotations

from . import browser, desktop, stream
from .config import Settings, get_settings
from .desktop import DesktopError
from .stream import StreamError, StreamNotImplemented

logger = logging.getLogger("friendly-host.mcp")

# Tool names match the plan / Friendly mcp__friendly_desktop__* naming.
TOOL_SCREENSHOT = "screenshot"
TOOL_CLICK = "click"
TOOL_TYPE = "type"
TOOL_HOTKEY = "hotkey"
TOOL_BROWSER_OPEN = "browser_open"
TOOL_STREAM_STATUS = "stream_status"
TOOL_STREAM_START = "stream_start"
TOOL_STREAM_STOP = "stream_stop"


def _err_text(exc: BaseException) -> list[TextContent]:
    if isinstance(exc, DesktopError):
        payload = {"error": exc.code, "message": exc.message}
    elif isinstance(exc, StreamError):
        payload = {"error": exc.code, "message": exc.message}
        if exc.code == "not_implemented":
            payload["phase"] = 3
    else:
        payload = {"error": "internal", "message": str(exc)}
    return [TextContent(type="text", text=json.dumps(payload))]


def _ok_text(data: dict[str, Any]) -> list[TextContent]:
    return [TextContent(type="text", text=json.dumps(data))]


def create_mcp_server() -> MCPServer:
    """Build the desktop MCP server with tools registered."""
    mcp = MCPServer(
        name="friendly-desktop",
        title="Friendly Desktop",
        description=(
            "Drive the Friendly companion Linux host desktop: screenshot, click, "
            "type, hotkey, open browser URLs. Stream start/stop are privileged and "
            "opens a short-lived noVNC viewer (localhost or tunnel when configured)."
        ),
        version="0.2.0",
        instructions=(
            "You control a headless Linux desktop (Xvfb). Prefer screenshot to see "
            "state, then click/type/hotkey/browser_open. Do not call stream_start "
            "unless the user explicitly asked to watch the desktop; that tool opens "
            "a public-facing viewer tunnel (Phase 3) and requires human approval."
        ),
    )

    @mcp.tool(
        name=TOOL_SCREENSHOT,
        title="Screenshot",
        description="Capture the headless desktop as a PNG image.",
        annotations=ToolAnnotations(readOnlyHint=True, destructiveHint=False),
    )
    def screenshot() -> list[TextContent | ImageContent]:
        try:
            b64 = desktop.screenshot_b64()
        except DesktopError as e:
            return _err_text(e)
        return [
            TextContent(type="text", text=json.dumps({"ok": True, "mime": "image/png"})),
            ImageContent(type="image", data=b64, mimeType="image/png"),
        ]

    @mcp.tool(
        name=TOOL_CLICK,
        title="Click",
        description="Click at desktop coordinates (pixels from top-left).",
        annotations=ToolAnnotations(readOnlyHint=False, destructiveHint=False),
    )
    def click(
        x: int,
        y: int,
        button: Literal["left", "middle", "right"] = "left",
    ) -> list[TextContent]:
        try:
            desktop.click(x, y, button)
        except DesktopError as e:
            return _err_text(e)
        return _ok_text({"ok": True, "x": x, "y": y, "button": button})

    @mcp.tool(
        name=TOOL_TYPE,
        title="Type text",
        description="Type unicode text into the focused window via xdotool.",
        annotations=ToolAnnotations(readOnlyHint=False, destructiveHint=False),
    )
    def type_text(text: str) -> list[TextContent]:
        try:
            desktop.type_text(text)
        except DesktopError as e:
            return _err_text(e)
        return _ok_text({"ok": True, "chars": len(text)})

    @mcp.tool(
        name=TOOL_HOTKEY,
        title="Hotkey",
        description='Press a key chord, e.g. keys=["ctrl","t"] or ["alt","F4"].',
        annotations=ToolAnnotations(readOnlyHint=False, destructiveHint=False),
    )
    def hotkey(keys: list[str]) -> list[TextContent]:
        try:
            desktop.hotkey(keys)
        except DesktopError as e:
            return _err_text(e)
        return _ok_text({"ok": True, "keys": keys})

    @mcp.tool(
        name=TOOL_BROWSER_OPEN,
        title="Open browser URL",
        description="Open a URL in Chromium on the headless display.",
        annotations=ToolAnnotations(
            readOnlyHint=False,
            destructiveHint=False,
            openWorldHint=True,
        ),
    )
    def browser_open(url: str) -> list[TextContent]:
        try:
            result = browser.open_url(url)
        except DesktopError as e:
            return _err_text(e)
        return _ok_text({"ok": True, **result})

    @mcp.tool(
        name=TOOL_STREAM_STATUS,
        title="Stream status",
        description="Return whether an on-demand desktop stream session is active.",
        annotations=ToolAnnotations(readOnlyHint=True, destructiveHint=False, idempotentHint=True),
    )
    def stream_status() -> list[TextContent]:
        return _ok_text(stream.stream_manager.status())

    @mcp.tool(
        name=TOOL_STREAM_START,
        title="Start desktop stream",
        description=(
            "PRIVILEGED: start on-demand noVNC viewer (x11vnc + websockify). "
            "Requires human approval in Friendly (needsApproval). "
            "Do not call unless the user asked to watch the desktop. "
            "Returns viewer_url (localhost unless Tailscale/CF tunnel provisioned)."
        ),
        annotations=ToolAnnotations(
            readOnlyHint=False,
            destructiveHint=True,
            openWorldHint=True,
        ),
        meta={
            "needsApproval": True,
            "privileged": True,
        },
    )
    def stream_start(mode: Literal["view", "interactive"] = "view") -> list[TextContent]:
        try:
            data = stream.stream_manager.start(mode=mode)
        except StreamError as e:
            return _err_text(e)
        return _ok_text(data)

    @mcp.tool(
        name=TOOL_STREAM_STOP,
        title="Stop desktop stream",
        description=(
            "PRIVILEGED: stop the on-demand stream and tear down the tunnel. "
            "Requires human approval in Friendly (needsApproval)."
        ),
        annotations=ToolAnnotations(readOnlyHint=False, destructiveHint=True),
        meta={
            "needsApproval": True,
            "privileged": True,
        },
    )
    def stream_stop(session_id: str | None = None) -> list[TextContent]:
        return _ok_text(stream.stream_manager.stop(session_id))

    return mcp


# Module-level server used by main.py mount
mcp = create_mcp_server()


def transport_security_from_settings(settings: Settings | None = None) -> TransportSecuritySettings:
    """DNS-rebinding allowlist for Streamable HTTP Host header checks."""
    s = settings or get_settings()
    # Comma-separated host[:port] patterns; always include localhost variants.
    raw = getattr(s, "mcp_allowed_hosts", None) or ""
    extra = [h.strip() for h in str(raw).split(",") if h.strip()]
    allowed = [
        "127.0.0.1",
        "127.0.0.1:*",
        "localhost",
        "localhost:*",
        "[::1]",
        "[::1]:*",
        *extra,
    ]
    # Dedupe preserving order
    seen: set[str] = set()
    hosts: list[str] = []
    for h in allowed:
        if h not in seen:
            seen.add(h)
            hosts.append(h)
    return TransportSecuritySettings(
        enable_dns_rebinding_protection=True,
        allowed_hosts=hosts,
    )


def build_mcp_starlette_app(settings: Settings | None = None):
    """
    Return the Streamable HTTP Starlette app.

    Mounted at ``/mcp`` with ``streamable_http_path="/"`` so the public URL is
    ``http://host:port/mcp`` (Friendly Streamable HTTP endpoint).
    """
    s = settings or get_settings()
    return mcp.streamable_http_app(
        streamable_http_path="/",
        # Stateless is simpler behind a single-process uvicorn; Friendly opens
        # short-lived sessions per chat turn.
        stateless_http=True,
        json_response=True,
        transport_security=transport_security_from_settings(s),
        host=s.api_host,
    )

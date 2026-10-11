"""websockify token plugin: authorize each WebSocket before connecting to VNC."""

from __future__ import annotations

import hashlib
import json
import secrets
from datetime import datetime, timezone
from pathlib import Path

from .config import get_settings
from .stream import StreamError, verify_viewer_token


class ViewerTokenPlugin:
    """Only the signed token for the current, unexpired session maps to localhost VNC."""

    def __init__(self, source: str):
        self.state_file = Path(source)

    def lookup(self, token: str | None) -> tuple[str, int] | None:
        if not token or len(token) > 4096:
            return None
        try:
            # The state file is replaced atomically on start and removed before stop.
            state = json.loads(self.state_file.read_text(encoding="utf-8"))
            claims = verify_viewer_token(token, get_settings())
            if (
                not secrets.compare_digest(hashlib.sha256(token.encode()).hexdigest(), state["token_hash"])
                or claims.get("sid") != state["session_id"]
                or claims.get("mode") != state["mode"]
                or state["mode"] not in ("view", "interactive")
                or claims.get("exp") != state["expires_at"]
                or datetime.now(timezone.utc).timestamp() >= state["expires_at"]
            ):
                return None
            port = state["vnc_port"]
            if not isinstance(port, int) or not 1 <= port <= 65535:
                return None
            return "127.0.0.1", port
        except (OSError, ValueError, TypeError, KeyError, StreamError):
            return None

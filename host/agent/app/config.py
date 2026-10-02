"""Settings loaded from environment / .env."""

from __future__ import annotations

import os
from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

# Resolve .env from friendly-host root (two levels up from this file: agent/app -> host)
_HOST_ROOT = Path(__file__).resolve().parents[2]
_ENV_FILE = _HOST_ROOT / ".env"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=str(_ENV_FILE) if _ENV_FILE.exists() else None,
        env_file_encoding="utf-8",
        extra="ignore",
    )

    api_token: str = "change-me-to-a-long-random-secret"
    display: str = ":99"
    screen_width: int = 1280
    screen_height: int = 720
    screen_depth: int = 24

    api_host: str = "127.0.0.1"
    api_port: int = 8787

    chromium_bin: str = "chromium"
    chromium_profile_dir: str = "/var/lib/assistant/chromium-profile"
    browser_start_url: str = "about:blank"

    # Stream / VNC — defaults avoid common lab ports 5900/6080 (often used by :1)
    stream_ttl_seconds: int = 900
    stream_jwt_secret: str = ""
    vnc_bind: str = "127.0.0.1"
    vnc_port: int = 5999
    novnc_port: int = 6099
    novnc_web_root: str = "/usr/share/novnc"
    # localhost | auto | tailscale | cloudflare | cloudflare_quick
    tunnel_mode: str = "auto"
    # Keep the viewer separate from any Funnel-backed HTTPS listener (usually :443).
    tailscale_serve_port: int = 8443
    cloudflared_token: str = ""
    cloudflared_config: str = ""

    # Extra Host patterns for MCP Streamable HTTP DNS-rebinding allowlist
    # (comma-separated). Localhost variants are always included.
    mcp_allowed_hosts: str = ""


@lru_cache
def get_settings() -> Settings:
    # Ensure DISPLAY is visible to child processes even if only set via Settings
    s = Settings()
    os.environ.setdefault("DISPLAY", s.display)
    return s


def clear_settings_cache() -> None:
    get_settings.cache_clear()

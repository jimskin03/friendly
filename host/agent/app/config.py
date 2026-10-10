"""Settings loaded from environment / .env."""

from __future__ import annotations

import os
from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

# Resolve .env from friendly-host root (two levels up from this file: agent/app -> host)
_HOST_ROOT = Path(__file__).resolve().parents[2]
_ENV_FILE = _HOST_ROOT / ".env"


# Placeholder shipped in .env.example and as the Settings default. It is public,
# so the host must never accept it as a credential.
PLACEHOLDER_API_TOKEN = "change-me-to-a-long-random-secret"


class ApiTokenConfigError(RuntimeError):
    """API_TOKEN is missing or still the public placeholder. Never carries the value."""


def api_token_problem(token: str | None) -> str | None:
    """Why ``token`` is unusable as the host credential, or None when it is usable.

    The returned text never contains the token itself.
    """
    value = (token or "").strip()
    if not value:
        return "API_TOKEN is empty"
    # Same rule as scripts/setup-ubuntu-headless.sh, which regenerates any "change-me" token.
    if value == PLACEHOLDER_API_TOKEN or "change-me" in value.lower():
        return "API_TOKEN is still the placeholder from .env.example"
    return None


def usable_api_token(token: str | None) -> str:
    """The configured token, or "" when it is empty or the placeholder."""
    return "" if api_token_problem(token) else (token or "")


def require_usable_api_token(settings: "Settings") -> None:
    """Fail fast at startup when the REST/MCP credential is missing or the placeholder."""
    problem = api_token_problem(settings.api_token)
    if problem:
        raise ApiTokenConfigError(
            f"{problem}. Refusing to start the Control API and MCP. Set API_TOKEN in "
            "host/.env (or /etc/friendly-host.env) to a long random value, e.g. "
            "`openssl rand -hex 32`, then restart and copy it into the Friendly app."
        )


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
    # Comma-separated ports tried when the preferred one has a foreign route.
    tailscale_serve_fallback_ports: str = "8445,8446,8447,10000"
    # Externally managed https viewer base (e.g. https://vm.tailnet.ts.net:8443).
    tailscale_viewer_url: str = ""
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

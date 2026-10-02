"""Browser helpers — open URL on the headless desktop (Phase 1: Chromium CLI)."""

from __future__ import annotations

import logging
import os
import shutil
import subprocess
from urllib.parse import urlparse

from .config import Settings, get_settings
from .desktop import DesktopError, require_display

logger = logging.getLogger(__name__)


def _find_chromium(settings: Settings) -> str:
    for name in (
        settings.chromium_bin,
        "chromium",
        "chromium-browser",
        "google-chrome",
        "google-chrome-stable",
    ):
        path = shutil.which(name)
        if path:
            return path
    raise DesktopError(
        "Chromium/Chrome not found. Run scripts/bootstrap-host.sh",
        code="missing_tool",
    )


def _validate_url(url: str) -> str:
    url = (url or "").strip()
    if not url:
        raise DesktopError("url is required", code="bad_url")
    parsed = urlparse(url)
    if parsed.scheme not in ("http", "https", "about", "file"):
        # Allow scheme-less → https
        if "://" not in url and url != "about:blank":
            url = "https://" + url
            parsed = urlparse(url)
        else:
            raise DesktopError(
                f"Unsupported URL scheme: {parsed.scheme or '(none)'}",
                code="bad_url",
            )
    return url


def open_url(url: str, settings: Settings | None = None) -> dict:
    """
    Open a URL in Chromium on the headless display.

    Phase 1: spawn Chromium with a dedicated profile (non-blocking).
    Playwright-driven control is deferred to a later phase.
    """
    s = settings or get_settings()
    disp = require_display(s)
    url = _validate_url(url)
    binary = _find_chromium(s)

    profile = s.chromium_profile_dir
    os.makedirs(profile, exist_ok=True)

    env = {**os.environ, "DISPLAY": disp}
    argv = [
        binary,
        f"--user-data-dir={profile}",
        "--no-first-run",
        "--disable-session-crashed-bubble",
        "--disable-infobars",
        f"--window-size={s.screen_width},{s.screen_height}",
        url,
    ]
    logger.info("Opening browser: %s %s", binary, url)
    try:
        proc = subprocess.Popen(
            argv,
            env=env,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            start_new_session=True,
        )
    except OSError as e:
        raise DesktopError(f"Failed to launch browser: {e}", code="browser_launch") from e

    return {"url": url, "pid": proc.pid, "binary": binary}

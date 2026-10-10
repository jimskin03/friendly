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


# Non-snap binaries first: snap Chromium generally fails under a systemd
# service user on Xvfb (confinement, no user session), often silently.
_BROWSER_CANDIDATES = (
    "google-chrome-stable",
    "google-chrome",
    "chromium",
    "chromium-browser",
)
_SNAP_FIX = (
    "Only the snap Chromium is installed; it does not run under the Friendly "
    "service user. Install Google Chrome (.deb): re-run "
    "scripts/setup-ubuntu-headless.sh, or set CHROMIUM_BIN to a non-snap browser."
)
_LAUNCH_CHECK_SECONDS = 2.0
_STDERR_TAIL = 1500


def is_snap_binary(path: str) -> bool:
    """True for /snap/... binaries and the Ubuntu wrapper scripts that exec them."""
    real = os.path.realpath(path)
    if path.startswith("/snap/") or real.startswith("/snap/"):
        return True
    try:
        with open(real, "rb") as f:
            head = f.read(4096)
    except OSError:
        return False
    if not head.startswith(b"#!"):
        return False
    return b"/snap/bin/" in head or b"snap run" in head or b"snap install" in head


def resolve_browser(settings: Settings) -> str:
    """Path of a usable (non-snap) Chrome/Chromium, or a clear DesktopError."""
    names = [settings.chromium_bin] if settings.chromium_bin else []
    names += [n for n in _BROWSER_CANDIDATES if n not in names]
    snap_only: list[str] = []
    for name in names:
        path = shutil.which(name)
        if not path:
            continue
        if is_snap_binary(path):
            snap_only.append(path)
            continue
        return path
    if snap_only:
        logger.error("Browser unusable: %s (found: %s)", _SNAP_FIX, ", ".join(snap_only))
        raise DesktopError(_SNAP_FIX, code="snap_browser_only")
    raise DesktopError(
        "Chrome/Chromium not found. Re-run scripts/setup-ubuntu-headless.sh (installs Google Chrome).",
        code="missing_tool",
    )


# Back-compat name.
_find_chromium = resolve_browser


def _log_path() -> str:
    log_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))), "logs")
    os.makedirs(log_dir, exist_ok=True)
    return os.path.join(log_dir, "browser.log")


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
    ]
    if hasattr(os, "geteuid") and os.geteuid() == 0 or os.path.exists("/.dockerenv"):
        argv.append("--no-sandbox")
    argv.append(url)
    logger.info("Opening browser: %s %s", binary, url)
    log_file = _log_path()
    try:
        with open(log_file, "ab") as log:
            proc = subprocess.Popen(
                argv,
                env=env,
                stdout=subprocess.DEVNULL,
                stderr=log,
                start_new_session=True,
            )
    except OSError as e:
        raise DesktopError(f"Failed to launch browser: {e}", code="browser_launch") from e

    # Chrome hands off to a running instance and exits 0; a quick non-zero exit
    # is a real failure (snap confinement, missing libs, bad profile ...).
    try:
        code = proc.wait(timeout=_LAUNCH_CHECK_SECONDS)
    except subprocess.TimeoutExpired:
        code = None
    if code not in (None, 0):
        tail = _stderr_tail(log_file)
        logger.error("Browser exited with %s: %s", code, tail or "(no stderr)")
        raise DesktopError(
            f"Browser {binary} exited with code {code}: {tail or 'no error output'}",
            code="browser_launch",
        )
    return {"url": url, "pid": proc.pid, "binary": binary}


def _stderr_tail(log_file: str) -> str:
    try:
        with open(log_file, "rb") as f:
            f.seek(0, os.SEEK_END)
            f.seek(max(0, f.tell() - _STDERR_TAIL))
            return f.read().decode("utf-8", "replace").strip()
    except OSError:
        return ""


def browser_status(settings: Settings | None = None) -> dict:
    """For /health: which browser will be used, or why none can."""
    try:
        return {"ok": True, "binary": resolve_browser(settings or get_settings())}
    except DesktopError as e:
        return {"ok": False, "error": e.code, "message": str(e)}


if __name__ == "__main__":  # used by setup to write the desktop menu
    import sys

    try:
        print(resolve_browser(get_settings()))
    except DesktopError as e:
        print(str(e), file=sys.stderr)
        sys.exit(1)

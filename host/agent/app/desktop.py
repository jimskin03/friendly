"""Screenshot and OS-level input against DISPLAY (Xvfb)."""

from __future__ import annotations

import base64
import logging
import os
import shutil
import subprocess
import tempfile
from pathlib import Path

from .config import Settings, get_settings

logger = logging.getLogger(__name__)


class DesktopError(Exception):
    """Raised when a desktop action cannot be performed."""

    def __init__(self, message: str, *, code: str = "desktop_error"):
        super().__init__(message)
        self.code = code
        self.message = message


def _display(settings: Settings | None = None) -> str:
    s = settings or get_settings()
    return os.environ.get("DISPLAY", s.display)


def display_available(settings: Settings | None = None) -> bool:
    """Return True if X is reachable on the configured DISPLAY."""
    disp = _display(settings)
    env = {**os.environ, "DISPLAY": disp}
    # Prefer xdpyinfo; fall back to xset
    for cmd in (["xdpyinfo"], ["xset", "q"]):
        if shutil.which(cmd[0]) is None:
            continue
        try:
            r = subprocess.run(
                cmd,
                env=env,
                capture_output=True,
                timeout=5,
                check=False,
            )
            if r.returncode == 0:
                return True
        except (OSError, subprocess.TimeoutExpired):
            continue
    # Last resort: check X socket
    if disp.startswith(":"):
        n = disp[1:].split(".")[0]
        sock = Path(f"/tmp/.X11-unix/X{n}")
        return sock.exists()
    return False


def require_display(settings: Settings | None = None) -> str:
    disp = _display(settings)
    if not display_available(settings):
        raise DesktopError(
            f"No X display available on DISPLAY={disp}. "
            "Start the idle stack (Xvfb + Openbox) first, e.g. "
            "./scripts/start-idle-stack.sh",
            code="no_display",
        )
    return disp


def _run(
    argv: list[str],
    *,
    display: str,
    timeout: float = 30,
    check: bool = True,
) -> subprocess.CompletedProcess[bytes]:
    env = {**os.environ, "DISPLAY": display}
    logger.debug("run %s DISPLAY=%s", argv, display)
    try:
        return subprocess.run(
            argv,
            env=env,
            capture_output=True,
            timeout=timeout,
            check=check,
        )
    except FileNotFoundError as e:
        raise DesktopError(
            f"Required tool not found: {argv[0]}. Install host packages via bootstrap-host.sh",
            code="missing_tool",
        ) from e
    except subprocess.TimeoutExpired as e:
        raise DesktopError(f"Command timed out: {' '.join(argv)}", code="timeout") from e
    except subprocess.CalledProcessError as e:
        err = (e.stderr or b"").decode("utf-8", errors="replace").strip()
        raise DesktopError(
            f"Command failed ({e.returncode}): {' '.join(argv)}"
            + (f" — {err}" if err else ""),
            code="command_failed",
        ) from e


def screenshot_png_bytes(settings: Settings | None = None) -> bytes:
    """Capture the full display as PNG bytes."""
    s = settings or get_settings()
    disp = require_display(s)

    with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as tmp:
        path = Path(tmp.name)

    try:
        # Prefer ImageMagick import, then scrot, then Pillow+mss-less X grab via scrot-like
        if shutil.which("import"):
            _run(["import", "-window", "root", str(path)], display=disp)
        elif shutil.which("scrot"):
            _run(["scrot", "-o", str(path)], display=disp)
        elif shutil.which("gnome-screenshot"):
            _run(["gnome-screenshot", "-f", str(path)], display=disp)
        else:
            # Pillow cannot grab X11 without extra libs; fail clearly
            raise DesktopError(
                "No screenshot tool found (need `import` from ImageMagick, or `scrot`). "
                "Run scripts/bootstrap-host.sh",
                code="missing_tool",
            )

        data = path.read_bytes()
        if not data or len(data) < 32:
            raise DesktopError("Screenshot produced empty or tiny file", code="empty_screenshot")
        return data
    finally:
        try:
            path.unlink(missing_ok=True)
        except OSError:
            pass


def screenshot_b64(settings: Settings | None = None) -> str:
    return base64.b64encode(screenshot_png_bytes(settings)).decode("ascii")


def click(
    x: int,
    y: int,
    button: str = "left",
    settings: Settings | None = None,
) -> None:
    s = settings or get_settings()
    disp = require_display(s)
    if shutil.which("xdotool") is None:
        raise DesktopError(
            "xdotool not found. Run scripts/bootstrap-host.sh",
            code="missing_tool",
        )
    btn_map = {"left": "1", "middle": "2", "right": "3", "1": "1", "2": "2", "3": "3"}
    b = btn_map.get(button.lower())
    if b is None:
        raise DesktopError(f"Unknown button: {button}", code="bad_button")
    _run(["xdotool", "mousemove", "--sync", str(int(x)), str(int(y))], display=disp)
    _run(["xdotool", "click", b], display=disp)


def type_text(text: str, settings: Settings | None = None) -> None:
    s = settings or get_settings()
    disp = require_display(s)
    if shutil.which("xdotool") is None:
        raise DesktopError(
            "xdotool not found. Run scripts/bootstrap-host.sh",
            code="missing_tool",
        )
    # --clearmodifiers avoids stuck keys; type window-independently
    _run(["xdotool", "type", "--clearmodifiers", "--", text], display=disp, timeout=60)


def hotkey(keys: list[str], settings: Settings | None = None) -> None:
    s = settings or get_settings()
    disp = require_display(s)
    if shutil.which("xdotool") is None:
        raise DesktopError(
            "xdotool not found. Run scripts/bootstrap-host.sh",
            code="missing_tool",
        )
    if not keys:
        raise DesktopError("keys must be a non-empty list", code="bad_keys")
    # xdotool key accepts chord like ctrl+alt+t
    chord = "+".join(k.strip() for k in keys if k.strip())
    if not chord:
        raise DesktopError("keys must be a non-empty list", code="bad_keys")
    _run(["xdotool", "key", "--clearmodifiers", chord], display=disp)


def status_info(settings: Settings | None = None) -> dict:
    s = settings or get_settings()
    disp = _display(s)
    return {
        "display": disp,
        "available": display_available(s),
        "tools": {
            "xdotool": shutil.which("xdotool") is not None,
            "import": shutil.which("import") is not None,
            "scrot": shutil.which("scrot") is not None,
            "chromium": shutil.which(s.chromium_bin) is not None
            or shutil.which("chromium-browser") is not None
            or shutil.which("google-chrome") is not None,
        },
        "screen": {
            "width": s.screen_width,
            "height": s.screen_height,
            "depth": s.screen_depth,
        },
    }

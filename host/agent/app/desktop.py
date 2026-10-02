"""Screenshot and OS-level input against DISPLAY (Xvfb)."""

from __future__ import annotations

import base64
import logging
import os
import shutil
import subprocess
import tempfile
import threading
import time
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
    _run(["xdotool", "mousemove", str(int(x)), str(int(y))], display=disp)
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


def launch_app(name: str, settings: Settings | None = None) -> dict:
    """Launch common desktop applications or trigger the application menu."""
    s = settings or get_settings()
    disp = require_display(s)
    clean_name = (name or "").strip().lower()

    if clean_name in ("menu", "root-menu", "app-menu"):
        if shutil.which("xdotool") is None:
            raise DesktopError("xdotool not found", code="missing_tool")
        _run(["xdotool", "key", "--clearmodifiers", "Super"], display=disp)
        return {"app": "menu", "status": "ok"}
    elif clean_name in ("terminal", "xterm", "bash", "shell"):
        term = shutil.which("xterm") or shutil.which("x-terminal-emulator")
        if not term:
            raise DesktopError("xterm terminal not found. Run scripts/bootstrap-host.sh", code="missing_tool")
        subprocess.Popen([term], env={**os.environ, "DISPLAY": disp}, start_new_session=True)
        return {"app": "terminal", "status": "ok", "binary": term}
    elif clean_name in ("browser", "chromium", "chrome"):
        from . import browser
        return browser.open_url("https://www.google.com", s)
    else:
        raise DesktopError(f"Unknown application: {name}. Supported: menu, terminal, browser", code="bad_app")


_CHROME_CLASSES = ("chromium", "chrome", "google-chrome", "Google-chrome", "Chromium-browser")
_TERMINAL_CLASSES = ("xterm", "xfce4-terminal", "gnome-terminal")
_prepare_lock = threading.Lock()


def workspace_frames(width: int, height: int, panel: int = 0) -> dict[str, tuple[int, int, int, int]]:
    """Chrome on the left, terminal on the right, above an optional panel.

    Inset slightly from screen edges so Openbox titlebars/borders stay
    visible and edge/corner resize grips remain reachable.
    """
    margin = 4
    usable_h = max(200, height - max(0, panel) - margin)
    chrome_w = max(320, int(width * 0.62))
    gap = 10
    # Leave the terminal a usable column.
    if chrome_w > width - 280:
        chrome_w = max(320, width - 280)
    term_w = max(200, width - chrome_w - gap - margin)
    return {
        "chrome": (margin, margin, chrome_w - margin, usable_h - margin),
        "terminal": (chrome_w + gap, margin, term_w, usable_h - margin),
    }


def _window_ids(display: str, class_name: str) -> list[str]:
    if shutil.which("xdotool") is None:
        return []
    result = _run(
        ["xdotool", "search", "--class", class_name],
        display=display,
        check=False,
    )
    return [
        line.strip()
        for line in (result.stdout or b"").decode("utf-8", errors="replace").splitlines()
        if line.strip().isdigit()
    ]


def _find_window(display: str, class_names: tuple[str, ...]) -> str | None:
    for name in class_names:
        ids = _window_ids(display, name)
        if ids:
            return ids[-1]
    return None


def _wait_window(display: str, class_names: tuple[str, ...], timeout: float = 8.0) -> str | None:
    deadline = time.time() + timeout
    while time.time() < deadline:
        found = _find_window(display, class_names)
        if found:
            return found
        time.sleep(0.25)
    return None


def _panel_height() -> int:
    if shutil.which("pgrep") is None:
        return 0
    try:
        result = subprocess.run(
            ["pgrep", "-x", "tint2"],
            capture_output=True,
            timeout=3,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired):
        return 0
    return 40 if result.returncode == 0 else 0


def _force_decorations(display: str, window_id: str) -> None:
    """Make Openbox draw a titlebar even when Chromium sets Motif undecorated."""
    if shutil.which("xprop") is None:
        return
    # Drop client Motif "no decorations" hints so the WM frame returns.
    _run(
        ["xprop", "-id", window_id, "-remove", "_MOTIF_WM_HINTS"],
        display=display,
        check=False,
    )
    # Explicitly request full Motif decorations (flags=DECORATIONS, decor=ALL).
    _run(
        [
            "xprop",
            "-id",
            window_id,
            "-f",
            "_MOTIF_WM_HINTS",
            "32c",
            "-set",
            "_MOTIF_WM_HINTS",
            "0x2, 0x0, 0x1, 0x0, 0x0",
        ],
        display=display,
        check=False,
    )


def _unmaximize(display: str, window_id: str) -> None:
    """Clear maximized state so windowsize/windowmove stick."""
    if shutil.which("wmctrl") is not None:
        _run(
            [
                "wmctrl",
                "-i",
                "-r",
                window_id,
                "-b",
                "remove,maximized_vert,maximized_horz,fullscreen",
            ],
            display=display,
            check=False,
        )
        return
    # Older xdotool builds lack `windowstate`; probe before calling.
    if shutil.which("xdotool") is None:
        return
    help_out = _run(["xdotool", "help"], display=display, check=False)
    help_txt = (help_out.stdout or b"").decode("utf-8", errors="replace")
    if "windowstate" not in help_txt:
        return
    _run(
        [
            "xdotool",
            "windowstate",
            "--remove",
            "MAXIMIZED_VERT",
            "--remove",
            "MAXIMIZED_HORZ",
            "--remove",
            "FULLSCREEN",
            window_id,
        ],
        display=display,
        check=False,
    )


def _place_window(display: str, window_id: str, x: int, y: int, width: int, height: int) -> None:
    if shutil.which("xdotool") is None:
        return
    _force_decorations(display, window_id)
    _unmaximize(display, window_id)
    _run(
        ["xdotool", "windowsize", window_id, str(width), str(height)],
        display=display,
        check=False,
    )
    _run(
        ["xdotool", "windowmove", window_id, str(x), str(y)],
        display=display,
        check=False,
    )


def _launch_terminal(display: str) -> None:
    term = shutil.which("xterm") or shutil.which("x-terminal-emulator")
    if not term:
        raise DesktopError(
            "xterm terminal not found. Run scripts/bootstrap-host.sh",
            code="missing_tool",
        )
    argv = [term]
    if os.path.basename(term) == "xterm":
        argv.extend(
            ["-fa", "DejaVu Sans Mono", "-fs", "11", "-bg", "#1c1c1c", "-fg", "#e6e6e6"]
        )
    subprocess.Popen(
        argv,
        env={**os.environ, "DISPLAY": display},
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        start_new_session=True,
    )


def prepare_workspace(settings: Settings | None = None) -> dict:
    """Open Chrome and a terminal side by side. Safe to call more than once."""
    with _prepare_lock:
        return _prepare_workspace_locked(settings)


def _prepare_workspace_locked(settings: Settings | None = None) -> dict:
    s = settings or get_settings()
    disp = require_display(s)
    if shutil.which("xsetroot"):
        _run(["xsetroot", "-solid", "#1e1f22"], display=disp, check=False)

    frames = workspace_frames(s.screen_width, s.screen_height, _panel_height())
    chrome_id = _find_window(disp, _CHROME_CLASSES)
    if chrome_id is None:
        from . import browser

        browser.open_url("https://www.google.com", s)
        chrome_id = _wait_window(disp, _CHROME_CLASSES)
    terminal_id = _find_window(disp, _TERMINAL_CLASSES)
    if terminal_id is None:
        _launch_terminal(disp)
        terminal_id = _wait_window(disp, _TERMINAL_CLASSES)

    # Openbox may maximize new windows; place them again after they settle.
    for _ in range(2):
        if chrome_id:
            _place_window(disp, chrome_id, *frames["chrome"])
        if terminal_id:
            _place_window(disp, terminal_id, *frames["terminal"])
        time.sleep(0.35)
        chrome_id = _find_window(disp, _CHROME_CLASSES) or chrome_id
        terminal_id = _find_window(disp, _TERMINAL_CLASSES) or terminal_id

    return {
        "chrome": chrome_id is not None,
        "terminal": terminal_id is not None,
        "frames": {name: list(rect) for name, rect in frames.items()},
    }


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


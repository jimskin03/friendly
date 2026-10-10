"""Browser resolution must skip snap Chromium and report launch failures."""

import os
import subprocess
from pathlib import Path

import pytest
from app.config import Settings

from app import browser
from app.desktop import DesktopError


def _exe(path: Path, body: str) -> str:
    path.write_text(body)
    path.chmod(0o755)
    return str(path)


def _settings(**kw) -> Settings:
    return Settings(api_token="a" * 48, **kw)


def _which(mapping: dict[str, str]):
    return lambda name: mapping.get(name)


def test_snap_wrapper_script_is_detected(tmp_path):
    wrapper = _exe(tmp_path / "chromium-browser", "#!/bin/sh\nexec /snap/bin/chromium \"$@\"\n")
    real = _exe(tmp_path / "chrome", "\x7fELF fake binary")
    assert browser.is_snap_binary(wrapper)
    assert not browser.is_snap_binary(real)
    assert browser.is_snap_binary("/snap/bin/chromium")


def test_prefers_google_chrome_over_snap_chromium(tmp_path, monkeypatch):
    snap = _exe(tmp_path / "chromium-browser", "#!/bin/sh\nexec /snap/bin/chromium \"$@\"\n")
    chrome = _exe(tmp_path / "google-chrome-stable", "\x7fELF")
    monkeypatch.setattr(browser.shutil, "which", _which({"chromium-browser": snap, "google-chrome-stable": chrome}))
    assert browser.resolve_browser(_settings(chromium_bin="chromium-browser")) == chrome


def test_only_snap_available_raises_a_clear_error(tmp_path, monkeypatch):
    snap = _exe(tmp_path / "chromium-browser", "#!/bin/sh\nexec /snap/bin/chromium \"$@\"\n")
    monkeypatch.setattr(browser.shutil, "which", _which({"chromium-browser": snap, "chromium": "/snap/bin/chromium"}))
    with pytest.raises(DesktopError) as err:
        browser.resolve_browser(_settings())
    assert err.value.code == "snap_browser_only"
    assert "Google Chrome" in str(err.value)
    status = browser.browser_status(_settings())
    assert status["ok"] is False and status["error"] == "snap_browser_only"


def test_no_browser_at_all(monkeypatch):
    monkeypatch.setattr(browser.shutil, "which", lambda name: None)
    with pytest.raises(DesktopError) as err:
        browser.resolve_browser(_settings())
    assert err.value.code == "missing_tool"


def _launch_env(monkeypatch, tmp_path, script: str):
    exe = _exe(tmp_path / "google-chrome-stable", script)
    monkeypatch.setattr(browser.shutil, "which", _which({"google-chrome-stable": exe}))
    monkeypatch.setattr(browser, "require_display", lambda s: ":99")
    monkeypatch.setattr(browser, "_log_path", lambda: str(tmp_path / "browser.log"))
    return _settings(chromium_profile_dir=str(tmp_path / "profile"))


def test_launch_failure_surfaces_stderr(tmp_path, monkeypatch, caplog):
    settings = _launch_env(monkeypatch, tmp_path, "#!/bin/sh\necho 'Missing X server or $DISPLAY' >&2\nexit 1\n")
    with caplog.at_level("ERROR"), pytest.raises(DesktopError) as err:
        browser.open_url("https://example.com", settings)
    assert err.value.code == "browser_launch"
    assert "Missing X server" in str(err.value)
    assert "Missing X server" in caplog.text


def test_handoff_exit_zero_and_long_running_are_success(tmp_path, monkeypatch):
    settings = _launch_env(monkeypatch, tmp_path, "#!/bin/sh\nexit 0\n")
    assert browser.open_url("example.com", settings)["url"] == "https://example.com"
    monkeypatch.setattr(browser, "_LAUNCH_CHECK_SECONDS", 0.2)
    settings = _launch_env(monkeypatch, tmp_path, "#!/bin/sh\nsleep 5\n")
    result = browser.open_url("https://example.com", settings)
    os.kill(result["pid"], 15)

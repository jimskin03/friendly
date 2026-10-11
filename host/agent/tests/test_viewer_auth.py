"""The viewer WebSocket gate must enforce the token and current session."""

from datetime import datetime, timedelta, timezone
from pathlib import Path
from urllib.parse import parse_qs, urlsplit
from unittest.mock import Mock

import pytest

from app import stream, viewer_token_plugin
from app.config import Settings


@pytest.fixture
def viewer_state(tmp_path: Path, monkeypatch):
    state_file = tmp_path / "viewer-session.json"
    settings = Settings(api_token="test-only-" + "z" * 40, vnc_port=5999)
    monkeypatch.setattr(stream, "PID_DIR", tmp_path)
    monkeypatch.setattr(stream, "VIEWER_STATE_FILE", state_file)
    monkeypatch.setattr(viewer_token_plugin, "get_settings", lambda: settings)
    return state_file, settings


def _session(settings: Settings, mode: str = "view"):
    expiry = datetime.now(timezone.utc) + timedelta(minutes=5)
    token = stream.mint_viewer_token(
        session_id="session-one", mode=mode, expires_at=expiry, settings=settings
    )
    stream._publish_viewer_session("session-one", mode, expiry, token, settings)
    return token


def test_websocket_requires_current_signed_session(viewer_state):
    state_file, settings = viewer_state
    plugin = viewer_token_plugin.ViewerTokenPlugin(str(state_file))
    assert plugin.lookup(None) is None
    assert plugin.lookup("invalid") is None

    first = _session(settings)
    assert plugin.lookup(first) == ("127.0.0.1", settings.vnc_port)
    assert plugin.lookup(first + "tampered") is None

    replacement = _session(settings, mode="interactive")
    assert plugin.lookup(first) is None
    assert plugin.lookup(replacement) == ("127.0.0.1", settings.vnc_port)
    stream._revoke_viewer_session()
    assert plugin.lookup(replacement) is None


def test_expired_token_never_opens_websocket(viewer_state):
    state_file, settings = viewer_state
    expiry = datetime.now(timezone.utc) - timedelta(seconds=1)
    token = stream.mint_viewer_token(
        session_id="expired", mode="view", expires_at=expiry, settings=settings
    )
    stream._publish_viewer_session("expired", "view", expiry, token, settings)
    assert viewer_token_plugin.ViewerTokenPlugin(str(state_file)).lookup(token) is None


@pytest.mark.parametrize("mode,viewonly", [("view", True), ("interactive", False)])
def test_vnc_enforces_mode_and_loopback(mode, viewonly, tmp_path, monkeypatch):
    settings = Settings(api_token="test-only-" + "z" * 40)
    popen = Mock(return_value=Mock(pid=1234))
    monkeypatch.setattr(stream, "LOG_DIR", tmp_path)
    monkeypatch.setattr(stream, "PID_DIR", tmp_path)
    monkeypatch.setattr(stream, "_which_or_raise", lambda name: name)
    monkeypatch.setattr(stream, "display_available", lambda settings: True)
    monkeypatch.setattr(stream, "_port_open", lambda host, port: False)
    monkeypatch.setattr(stream, "_wait_port", lambda host, port, timeout: True)
    monkeypatch.setattr(stream.subprocess, "Popen", popen)
    stream._start_x11vnc(settings, mode)
    args = popen.call_args.args[0]
    assert "-localhost" in args
    assert ("-viewonly" in args) is viewonly


def test_gateway_uses_token_plugin_without_fixed_backend(viewer_state, tmp_path, monkeypatch):
    state_file, settings = viewer_state
    popen = Mock(return_value=Mock(pid=1234))
    monkeypatch.setattr(stream, "LOG_DIR", tmp_path)
    monkeypatch.setattr(stream, "PID_DIR", tmp_path)
    monkeypatch.setattr(stream, "_which_or_raise", lambda name: name)
    monkeypatch.setattr(stream, "_novnc_web_root", lambda settings: tmp_path)
    monkeypatch.setattr(stream, "_port_open", lambda host, port: False)
    monkeypatch.setattr(stream, "_wait_port", lambda host, port, timeout: True)
    monkeypatch.setattr(stream.subprocess, "Popen", popen)
    stream._start_websockify(settings)
    args = popen.call_args.args[0]
    assert "--token-plugin=app.viewer_token_plugin.ViewerTokenPlugin" in args
    assert f"--token-source={state_file}" in args
    assert f"127.0.0.1:{settings.vnc_port}" not in args


def test_occupied_vnc_port_fails_closed(tmp_path, monkeypatch):
    settings = Settings(api_token="test-only-" + "z" * 40)
    monkeypatch.setattr(stream, "_which_or_raise", lambda name: name)
    monkeypatch.setattr(stream, "display_available", lambda settings: True)
    monkeypatch.setattr(stream, "_port_open", lambda host, port: True)
    with pytest.raises(stream.StreamError, match="already in use"):
        stream._start_x11vnc(settings, "view")


def test_viewer_url_carries_token_to_websocket_path(viewer_state):
    _, settings = viewer_state
    token = "test-viewer-token"
    url = stream._build_local_viewer_url(settings=settings, token=token)
    query = parse_qs(urlsplit(url).query)
    assert query["path"] == [f"websockify?token={token}"]


def test_tunnel_failure_revokes_gateway_and_stops_listeners(viewer_state, monkeypatch):
    state_file, settings = viewer_state
    settings.tunnel_mode = "tailscale"
    terminated = []
    monkeypatch.setattr(stream, "_which_or_raise", lambda name: name)
    monkeypatch.setattr(stream, "_novnc_web_root", lambda settings: Path("/tmp"))
    monkeypatch.setattr(stream, "_start_x11vnc", lambda settings, mode: 1234)
    monkeypatch.setattr(stream, "_start_websockify", lambda settings: 5678)
    monkeypatch.setattr(stream, "_terminate", lambda pid, name: terminated.append((pid, name)))

    def failed_tunnel(url, settings):
        raise RuntimeError("tunnel failed")

    monkeypatch.setattr(stream, "_try_tailscale_serve", failed_tunnel)
    with pytest.raises(RuntimeError, match="tunnel failed"):
        stream.StreamManager().start(settings, remote_client=False)
    assert not state_file.exists()
    assert terminated == [(5678, "websockify"), (1234, "x11vnc")]

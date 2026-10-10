"""Viewer route ownership on hosts that already use Tailscale Serve/Funnel elsewhere."""

import json
from pathlib import Path
from subprocess import CompletedProcess
from unittest.mock import Mock

import pytest
from app.config import Settings

from app import stream

HOST = "vm-0-6-ubuntu.tail829182.ts.net"
TARGET = "http://127.0.0.1:6099"
LOCAL_VIEWER = f"{TARGET}/vnc.html?autoconnect=1&token=viewer-secret"


def _web(port: int, proxy: str) -> dict:
    return {f"{HOST}:{port}": {"Handlers": {"/": {"Proxy": proxy}}}}


def _status(*webs: dict, funnel_443: bool = True) -> str:
    web: dict = {}
    for item in webs:
        web.update(item)
    tcp = {str(int(k.rsplit(":", 1)[1])): {"HTTPS": True} for k in web}
    doc = {"TCP": tcp, "Web": web}
    if funnel_443:
        doc["AllowFunnel"] = {f"{HOST}:443": True}
    return json.dumps(doc)


# Greg's VM: Funnel on 443 for another MCP, plus a manual Control API route on 8444.
FUNNEL = _web(443, "http://127.0.0.1:3000")
API = _web(8444, "http://127.0.0.1:8787")


def _settings(**kw) -> Settings:
    return Settings(api_token="a" * 48, novnc_port=6099, tailscale_serve_port=8443, **kw)


def _commands(run: Mock) -> list[list[str]]:
    return [c.args[0] for c in run.call_args_list]


def _use(monkeypatch, run: Mock) -> None:
    monkeypatch.setattr(stream.shutil, "which", lambda name: "/usr/bin/tailscale")
    monkeypatch.setattr(stream.subprocess, "run", run)


def test_foreign_funnel_on_443_does_not_block_the_viewer_port(monkeypatch):
    run = Mock(side_effect=[
        CompletedProcess([], 0, _status(FUNNEL, API), ""),
        CompletedProcess([], 0, "ok", ""),
        CompletedProcess([], 0, _status(FUNNEL, API, _web(8443, TARGET)), ""),
    ])
    _use(monkeypatch, run)

    result = stream._try_tailscale_serve(LOCAL_VIEWER, _settings())

    assert result["viewer_url"] == f"https://{HOST}:8443/vnc.html?autoconnect=1&token=viewer-secret"
    serve = [c for c in _commands(run) if "status" not in c]
    assert serve == [["tailscale", "serve", "--https=8443", "--set-path=/", "--bg", TARGET]]
    assert not any("443" == a.split("=")[-1] or "funnel" in a for c in _commands(run) for a in c)


def test_foreign_route_on_8443_falls_back_to_next_free_port(monkeypatch):
    foreign = _web(8443, "http://127.0.0.1:9000")
    run = Mock(side_effect=[
        CompletedProcess([], 0, _status(FUNNEL, API, foreign), ""),  # check 8443 → taken
        CompletedProcess([], 0, _status(FUNNEL, API, foreign), ""),  # check 8445 → free
        CompletedProcess([], 0, "ok", ""),
        CompletedProcess([], 0, _status(FUNNEL, API, foreign, _web(8445, TARGET)), ""),
    ])
    _use(monkeypatch, run)

    result = stream._try_tailscale_serve(LOCAL_VIEWER, _settings())

    assert result["viewer_url"].startswith(f"https://{HOST}:8445/vnc.html")
    assert result["serve_port"] == 8445
    serve = [c for c in _commands(run) if "status" not in c]
    assert serve == [["tailscale", "serve", "--https=8445", "--set-path=/", "--bg", TARGET]]


def test_no_free_port_reports_every_port_tried_without_a_viewer_url(monkeypatch):
    taken = [_web(p, "http://127.0.0.1:9000") for p in (8443, 8445)]
    run = Mock(return_value=CompletedProcess([], 0, _status(FUNNEL, *taken), ""))
    _use(monkeypatch, run)

    result = stream._try_tailscale_serve(LOCAL_VIEWER, _settings(tailscale_serve_fallback_ports="8445"))

    assert "viewer_url" not in result
    assert ":8443" in result["detail"] and ":8445" in result["detail"]
    assert all("status" in c for c in _commands(run))


def test_manual_identical_route_on_8443_is_reused_and_left_alone(monkeypatch):
    run = Mock(return_value=CompletedProcess([], 0, _status(FUNNEL, API, _web(8443, TARGET)), ""))
    _use(monkeypatch, run)

    result = stream._try_tailscale_serve(LOCAL_VIEWER, _settings())

    assert result["viewer_url"].startswith(f"https://{HOST}:8443/vnc.html")
    assert result["provisioned"] is False  # not ours, so stop() will not remove it
    assert _commands(run) == [["tailscale", "serve", "status", "--json"]]
    assert stream._teardown_tunnel(result, _settings()) is True
    assert run.call_count == 1


def test_viewer_url_override_skips_tailscale(monkeypatch):
    run = Mock()
    _use(monkeypatch, run)

    result = stream._try_tailscale_serve(LOCAL_VIEWER, _settings(tailscale_viewer_url=f"https://{HOST}:9443/"))

    assert result["viewer_url"] == f"https://{HOST}:9443/vnc.html?autoconnect=1&token=viewer-secret"
    run.assert_not_called()


@pytest.mark.parametrize("url", ["http://vm.ts.net:8443", "https://127.0.0.1:6099"])
def test_viewer_url_override_must_be_remote_https(monkeypatch, url):
    _use(monkeypatch, Mock())
    result = stream._try_tailscale_serve(LOCAL_VIEWER, _settings(tailscale_viewer_url=url))
    assert "viewer_url" not in result


@pytest.mark.parametrize("port", [443, 8444, 80])
def test_reserved_ports_are_never_used_for_the_viewer(monkeypatch, port):
    run = Mock(return_value=CompletedProcess([], 0, _status(FUNNEL), ""))
    _use(monkeypatch, run)
    result = stream._try_tailscale_serve_port(LOCAL_VIEWER, _settings(), port)
    assert "viewer_url" not in result
    run.assert_not_called()


def _stub_stack(monkeypatch, terminated: list[str]) -> None:
    monkeypatch.setattr(stream, "display_available", lambda _s: True)
    monkeypatch.setattr(stream, "_which_or_raise", lambda _n: None)
    monkeypatch.setattr(stream, "_novnc_web_root", lambda _s: Path("/tmp"))
    monkeypatch.setattr(stream, "_start_x11vnc", lambda _s, _m: 111)
    monkeypatch.setattr(stream, "_start_websockify", lambda _s: 222)
    monkeypatch.setattr(stream, "_terminate", lambda pid, name: terminated.append(name))
    monkeypatch.setattr(stream, "mint_viewer_token", lambda **_k: "viewer-secret")
    monkeypatch.setattr(stream, "_build_local_viewer_url", lambda settings, token: f"{TARGET}/vnc.html?token={token}")


def test_remote_client_gets_actionable_error_instead_of_loopback_viewer(monkeypatch):
    terminated: list[str] = []
    _stub_stack(monkeypatch, terminated)
    monkeypatch.setattr(stream, "_try_tailscale_serve", lambda local, s: {"mode": "tailscale", "provisioned": False, "detail": "Tailscale Serve :8443 is already configured for another service"})
    settings = _settings(tunnel_mode="tailscale")
    manager = stream.StreamManager()
    manager._schedule_expiry = lambda *_a: None

    with pytest.raises(stream.StreamError) as err:
        manager.start(settings, mode="view", remote_client=True)

    assert err.value.code == "viewer_unreachable"
    assert "TAILSCALE_VIEWER_URL" in err.value.message and "tailscale serve --bg --https=8443" in err.value.message
    assert "viewer-secret" not in err.value.message
    assert sorted(terminated) == ["websockify", "x11vnc"]
    assert manager._session is None


def test_local_client_may_still_use_the_loopback_viewer(monkeypatch):
    _stub_stack(monkeypatch, [])
    monkeypatch.setattr(stream, "_try_tailscale_serve", lambda local, s: {"mode": "tailscale", "provisioned": False, "detail": "x"})
    manager = stream.StreamManager()
    manager._schedule_expiry = lambda *_a: None
    result = manager.start(_settings(tunnel_mode="tailscale"), mode="view", remote_client=False)
    assert result["viewer_url"].startswith(TARGET)
    manager._session = None

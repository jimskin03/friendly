import json
import subprocess
from subprocess import CompletedProcess
from unittest.mock import Mock

import pytest
from app.config import Settings

from app import stream

HOST = "vm-0-6-ubuntu.tail829182.ts.net"
PATH = "/"
PORT = 8443
LOCAL_TARGET = "http://127.0.0.1:6099"
LOCAL_VIEWER = f"{LOCAL_TARGET}/vnc.html?autoconnect=1&token=viewer-secret"


def _status(*, include_route: bool = False, funnel_port: int | None = 443) -> str:
    web = {f"{HOST}:443": {"Handlers": {"/": {"Proxy": "http://127.0.0.1:3000"}}}}
    if include_route:
        web[f"{HOST}:{PORT}"] = {"Handlers": {PATH: {"Proxy": LOCAL_TARGET}}}
    allow_funnel = {}
    if funnel_port is not None:
        allow_funnel[f"{HOST}:{funnel_port}"] = True
    return json.dumps({"Web": web, "AllowFunnel": allow_funnel})


def _settings() -> Settings:
    return Settings(
        api_token="a" * 48,
        novnc_port=6099,
        tailscale_serve_port=PORT,
    )


def test_tailscale_viewer_uses_private_port_and_path_without_replacing_funnel(
    monkeypatch,
):
    run = Mock(
        side_effect=[
            CompletedProcess([], 0, _status(), ""),
            CompletedProcess([], 0, "Serve started", ""),
            CompletedProcess([], 0, _status(include_route=True), ""),
        ]
    )
    monkeypatch.setattr(stream.shutil, "which", lambda name: "/usr/bin/tailscale")
    monkeypatch.setattr(stream.subprocess, "run", run)

    result = stream._try_tailscale_serve(LOCAL_VIEWER, _settings())

    assert result["viewer_url"] == (
        f"https://{HOST}:{PORT}/vnc.html?autoconnect=1&token=viewer-secret"
    )
    assert result["public_base"] == f"https://{HOST}:{PORT}"
    assert result["provisioned"] is True
    assert run.call_args_list[0].args[0] == ["tailscale", "serve", "status", "--json"]
    assert run.call_args_list[1].args[0] == [
        "tailscale",
        "serve",
        f"--https={PORT}",
        f"--set-path={PATH}",
        "--bg",
        LOCAL_TARGET,
    ]
    assert run.call_args_list[2].args[0] == ["tailscale", "serve", "status", "--json"]


def test_tailscale_viewer_refuses_a_funnel_on_its_private_port(monkeypatch):
    run = Mock(return_value=CompletedProcess([], 0, _status(funnel_port=PORT), ""))
    monkeypatch.setattr(stream.shutil, "which", lambda name: "/usr/bin/tailscale")
    monkeypatch.setattr(stream.subprocess, "run", run)

    result = stream._try_tailscale_serve(LOCAL_VIEWER, _settings())

    assert result["provisioned"] is False
    assert "funnel" in result["detail"].lower()
    assert "viewer_url" not in result
    assert run.call_count == 1


def test_tailscale_viewer_refuses_to_overwrite_a_conflicting_path(monkeypatch):
    conflict = json.dumps(
        {
            "Web": {
                f"{HOST}:{PORT}": {
                    "Handlers": {PATH: {"Proxy": "http://127.0.0.1:3000"}}
                }
            },
            "AllowFunnel": {},
        }
    )
    run = Mock(return_value=CompletedProcess([], 0, conflict, ""))
    monkeypatch.setattr(stream.shutil, "which", lambda name: "/usr/bin/tailscale")
    monkeypatch.setattr(stream.subprocess, "run", run)

    result = stream._try_tailscale_serve(LOCAL_VIEWER, _settings())

    assert result["provisioned"] is False
    assert "already configured" in result["detail"].lower()
    assert "viewer_url" not in result
    assert run.call_count == 1


def test_teardown_removes_only_the_owned_private_route(monkeypatch):
    run = Mock(return_value=CompletedProcess([], 0, _status(include_route=True), ""))
    monkeypatch.setattr(stream.shutil, "which", lambda name: "/usr/bin/tailscale")
    monkeypatch.setattr(stream.subprocess, "run", run)

    stream._teardown_tunnel(
        {
            "mode": "tailscale",
            "provisioned": True,
            "serve_port": PORT,
            "serve_path": PATH,
            "local_target": LOCAL_TARGET,
        },
        _settings(),
    )

    assert run.call_args_list[0].args[0] == ["tailscale", "serve", "status", "--json"]
    assert run.call_args_list[1].args[0] == [
        "tailscale",
        "serve",
        f"--https={PORT}",
        f"--set-path={PATH}",
        "off",
    ]
    assert all("reset" not in call.args[0] for call in run.call_args_list)


def test_teardown_preserves_route_if_another_service_replaced_it(monkeypatch):
    conflict = json.dumps(
        {
            "Web": {
                f"{HOST}:{PORT}": {
                    "Handlers": {PATH: {"Proxy": "http://127.0.0.1:3000"}}
                }
            },
            "AllowFunnel": {},
        }
    )
    run = Mock(return_value=CompletedProcess([], 0, conflict, ""))
    monkeypatch.setattr(stream.shutil, "which", lambda name: "/usr/bin/tailscale")
    monkeypatch.setattr(stream.subprocess, "run", run)

    stream._teardown_tunnel(
        {
            "mode": "tailscale",
            "provisioned": True,
            "serve_port": PORT,
            "serve_path": PATH,
            "local_target": LOCAL_TARGET,
        },
        _settings(),
    )

    assert run.call_count == 1
    assert run.call_args_list[0].args[0] == ["tailscale", "serve", "status", "--json"]


def test_stream_start_does_not_log_viewer_token(monkeypatch, caplog):
    from pathlib import Path

    settings = _settings()
    settings.tunnel_mode = "localhost"
    monkeypatch.setattr(stream, "display_available", lambda _settings: True)
    monkeypatch.setattr(stream, "_which_or_raise", lambda _name: None)
    monkeypatch.setattr(stream, "_novnc_web_root", lambda _settings: Path("/tmp"))
    monkeypatch.setattr(stream, "_start_x11vnc", lambda _settings, _mode: 0)
    monkeypatch.setattr(stream, "_start_websockify", lambda _settings: 0)
    monkeypatch.setattr(
        stream,
        "mint_viewer_token",
        lambda **_kwargs: "viewer-secret",
    )
    monkeypatch.setattr(
        stream,
        "_build_local_viewer_url",
        lambda settings, token: (
            f"http://127.0.0.1:{settings.novnc_port}/vnc.html?token={token}"
        ),
    )

    manager = stream.StreamManager()
    manager._schedule_expiry = lambda session_id, expires_at: None
    with caplog.at_level("INFO"):
        result = manager.start(settings, mode="view")

    assert result["viewer_url"].endswith("token=viewer-secret")
    assert "viewer-secret" not in caplog.text
    manager._session = None


def test_tailscale_viewer_rolls_back_if_funnel_appears_during_setup(monkeypatch):
    run = Mock(
        side_effect=[
            CompletedProcess([], 0, _status(), ""),
            CompletedProcess([], 0, "Serve started", ""),
            CompletedProcess([], 0, _status(include_route=True, funnel_port=PORT), ""),
            CompletedProcess([], 0, _status(include_route=True, funnel_port=PORT), ""),
            CompletedProcess([], 0, "Serve route removed", ""),
        ]
    )
    monkeypatch.setattr(stream.shutil, "which", lambda name: "/usr/bin/tailscale")
    monkeypatch.setattr(stream.subprocess, "run", run)

    result = stream._try_tailscale_serve(LOCAL_VIEWER, _settings())

    assert result["provisioned"] is False
    assert "viewer_url" not in result
    assert run.call_args_list[-1].args[0] == [
        "tailscale",
        "serve",
        f"--https={PORT}",
        f"--set-path={PATH}",
        "off",
    ]
    assert all("reset" not in call.args[0] for call in run.call_args_list)


def test_tailscale_viewer_refuses_a_port_with_only_nonroot_handlers(monkeypatch):
    occupied = json.dumps(
        {
            "Web": {
                f"{HOST}:{PORT}": {"Handlers": {"/existing": {"Proxy": LOCAL_TARGET}}}
            },
            "AllowFunnel": {},
        }
    )
    run = Mock(return_value=CompletedProcess([], 0, occupied, ""))
    monkeypatch.setattr(stream.shutil, "which", lambda name: "/usr/bin/tailscale")
    monkeypatch.setattr(stream.subprocess, "run", run)

    result = stream._try_tailscale_serve(LOCAL_VIEWER, _settings())

    assert result["provisioned"] is False
    assert "already configured" in result["detail"].lower()
    assert "viewer_url" not in result
    assert run.call_count == 1


@pytest.mark.parametrize("timeout", [False, True])
def test_failed_serve_command_cleans_a_partially_applied_route(monkeypatch, timeout):
    command = [
        "tailscale",
        "serve",
        f"--https={PORT}",
        f"--set-path={PATH}",
        "--bg",
        LOCAL_TARGET,
    ]
    failure = (
        subprocess.TimeoutExpired(command, timeout=15)
        if timeout
        else CompletedProcess(command, 1, "", "serve failed")
    )
    run = Mock(
        side_effect=[
            CompletedProcess([], 0, _status(), ""),
            failure,
            CompletedProcess([], 0, _status(include_route=True), ""),
            CompletedProcess([], 0, "Serve route removed", ""),
        ]
    )
    monkeypatch.setattr(stream.shutil, "which", lambda name: "/usr/bin/tailscale")
    monkeypatch.setattr(stream.subprocess, "run", run)

    result = stream._try_tailscale_serve(LOCAL_VIEWER, _settings())

    assert result["provisioned"] is False
    assert "viewer_url" not in result
    assert run.call_args_list[-1].args[0] == [
        "tailscale",
        "serve",
        f"--https={PORT}",
        f"--set-path={PATH}",
        "off",
    ]
    assert all("reset" not in call.args[0] for call in run.call_args_list)


def test_tailscale_viewer_refuses_nested_nonroot_service_handler(monkeypatch):
    nested = json.dumps(
        {
            "Services": {
                "svc:existing": {
                    "TCP": {str(PORT): {"HTTPS": True}},
                    "Web": {
                        f"{HOST}:{PORT}": {
                            "Handlers": {
                                "/existing": {"Proxy": "http://127.0.0.1:3000"}
                            }
                        }
                    },
                }
            }
        }
    )
    run = Mock(return_value=CompletedProcess([], 0, nested, ""))
    monkeypatch.setattr(stream.shutil, "which", lambda name: "/usr/bin/tailscale")
    monkeypatch.setattr(stream.subprocess, "run", run)

    result = stream._try_tailscale_serve(LOCAL_VIEWER, _settings())

    assert result["provisioned"] is False
    assert "already configured" in result["detail"].lower()
    assert "viewer_url" not in result
    assert run.call_count == 1


def test_failed_serve_cleans_matching_route_from_nested_status(monkeypatch):
    service_id = "svc:friendly-viewer"
    nested_route = json.dumps(
        {
            "Services": {
                service_id: {
                    "TCP": {str(PORT): {"HTTPS": True}},
                    "Web": {
                        f"{HOST}:{PORT}": {"Handlers": {PATH: {"Proxy": LOCAL_TARGET}}}
                    },
                }
            },
            "AllowFunnel": {f"{HOST}:443": True},
        }
    )
    command = [
        "tailscale",
        "serve",
        f"--https={PORT}",
        f"--set-path={PATH}",
        "--bg",
        LOCAL_TARGET,
    ]
    run = Mock(
        side_effect=[
            CompletedProcess([], 0, _status(), ""),
            CompletedProcess(command, 1, "", "serve failed"),
            CompletedProcess([], 0, nested_route, ""),
            CompletedProcess([], 0, nested_route, ""),
            CompletedProcess([], 0, "Serve route removed", ""),
        ]
    )
    monkeypatch.setattr(stream.shutil, "which", lambda name: "/usr/bin/tailscale")
    monkeypatch.setattr(stream.subprocess, "run", run)

    result = stream._try_tailscale_serve(LOCAL_VIEWER, _settings())

    assert result["provisioned"] is False
    assert "viewer_url" not in result
    assert run.call_args_list[-1].args[0] == [
        "tailscale",
        "serve",
        f"--service={service_id}",
        f"--https={PORT}",
        f"--set-path={PATH}",
        "off",
    ]
    assert all("reset" not in call.args[0] for call in run.call_args_list)


def test_tailscale_viewer_aborts_on_empty_status_output(monkeypatch):
    run = Mock(return_value=CompletedProcess([], 0, "", ""))
    monkeypatch.setattr(stream.shutil, "which", lambda name: "/usr/bin/tailscale")
    monkeypatch.setattr(stream.subprocess, "run", run)

    result = stream._try_tailscale_serve(LOCAL_VIEWER, _settings())

    assert result["provisioned"] is False
    assert "status" in result["detail"].lower()
    assert "viewer_url" not in result
    assert run.call_count == 1


def test_tailscale_viewer_refuses_nested_funnel_on_private_port(monkeypatch):
    nested = json.dumps(
        {"Services": {"svc:existing": {"AllowFunnel": {f"{HOST}:{PORT}": True}}}}
    )
    run = Mock(return_value=CompletedProcess([], 0, nested, ""))
    monkeypatch.setattr(stream.shutil, "which", lambda name: "/usr/bin/tailscale")
    monkeypatch.setattr(stream.subprocess, "run", run)

    result = stream._try_tailscale_serve(LOCAL_VIEWER, _settings())

    assert result["provisioned"] is False
    assert "funnel" in result["detail"].lower()
    assert "viewer_url" not in result
    assert run.call_count == 1


@pytest.mark.parametrize("service", [{}, {"Handlers": {}}, {"Handlers": "malformed"}])
def test_tailscale_viewer_refuses_web_entries_without_valid_handlers(
    monkeypatch, service
):
    status = json.dumps(
        {
            "TCP": {},
            "Web": {f"{HOST}:{PORT}": service},
            "AllowFunnel": {},
        }
    )
    run = Mock(return_value=CompletedProcess([], 0, status, ""))
    monkeypatch.setattr(stream.shutil, "which", lambda name: "/usr/bin/tailscale")
    monkeypatch.setattr(stream.subprocess, "run", run)

    result = stream._try_tailscale_serve(LOCAL_VIEWER, _settings())

    assert result["provisioned"] is False
    assert "configured" in result["detail"].lower()
    assert "viewer_url" not in result
    assert run.call_count == 1


@pytest.mark.parametrize("status", ["{}", json.dumps({"Unknown": {}})])
def test_tailscale_viewer_rejects_unrecognized_status_schema(monkeypatch, status):
    run = Mock(return_value=CompletedProcess([], 0, status, ""))
    monkeypatch.setattr(stream.shutil, "which", lambda name: "/usr/bin/tailscale")
    monkeypatch.setattr(stream.subprocess, "run", run)

    result = stream._try_tailscale_serve(LOCAL_VIEWER, _settings())

    assert result["provisioned"] is False
    assert "status" in result["detail"].lower()
    assert "viewer_url" not in result
    assert run.call_count == 1


def test_tailscale_viewer_rejects_unknown_nested_service_schema(monkeypatch):
    status = json.dumps({"Services": {"svc:x": {"Unknown": {}}}})
    run = Mock(return_value=CompletedProcess([], 0, status, ""))
    monkeypatch.setattr(stream.shutil, "which", lambda name: "/usr/bin/tailscale")
    monkeypatch.setattr(stream.subprocess, "run", run)

    result = stream._try_tailscale_serve(LOCAL_VIEWER, _settings())

    assert result["provisioned"] is False
    assert "status" in result["detail"].lower()
    assert "viewer_url" not in result
    assert run.call_count == 1


def test_empty_nested_service_scope_does_not_block_private_route(monkeypatch):
    before = json.loads(_status())
    before["Services"] = {"svc:idle": {}}
    after = json.loads(_status(include_route=True))
    after["Services"] = {"svc:idle": {}}
    run = Mock(
        side_effect=[
            CompletedProcess([], 0, json.dumps(before), ""),
            CompletedProcess([], 0, "Serve started", ""),
            CompletedProcess([], 0, json.dumps(after), ""),
        ]
    )
    monkeypatch.setattr(stream.shutil, "which", lambda name: "/usr/bin/tailscale")
    monkeypatch.setattr(stream.subprocess, "run", run)

    result = stream._try_tailscale_serve(LOCAL_VIEWER, _settings())

    assert result["viewer_url"] == (
        f"https://{HOST}:{PORT}/vnc.html?autoconnect=1&token=viewer-secret"
    )
    assert result["provisioned"] is True
    assert run.call_count == 3


@pytest.mark.parametrize(
    "hostport",
    [
        pytest.param(f"{HOST}:{PORT}/unexpected", id="path"),
        pytest.param(f"{HOST}:0{PORT}", id="leading-zero-port"),
        pytest.param("host name:8443", id="whitespace-host"),
        pytest.param("a..b:8443", id="empty-dns-label"),
        pytest.param(f"{'a' * 254}:8443", id="oversized-host"),
        pytest.param("[fe80::1%eth0]:8443", id="scoped-ipv6"),
    ],
)
def test_tailscale_viewer_aborts_on_noncanonical_hostport(monkeypatch, hostport):
    status = json.dumps(
        {
            "TCP": {str(PORT): {"HTTPS": True}},
            "Web": {hostport: {"Handlers": {PATH: {"Proxy": LOCAL_TARGET}}}},
            "AllowFunnel": {},
        }
    )
    run = Mock(return_value=CompletedProcess([], 0, status, ""))
    monkeypatch.setattr(stream.shutil, "which", lambda name: "/usr/bin/tailscale")
    monkeypatch.setattr(stream.subprocess, "run", run)

    result = stream._try_tailscale_serve(LOCAL_VIEWER, _settings())

    assert result["provisioned"] is False
    assert "status" in result["detail"].lower()
    assert "viewer_url" not in result
    assert run.call_count == 1


@pytest.mark.parametrize(
    "service_id",
    [
        pytest.param("svc:bad name", id="whitespace-name"),
        pytest.param("svc:" + "a" * 254, id="oversized-name"),
        pytest.param("invalid-prefix", id="missing-svc-prefix"),
    ],
)
def test_tailscale_viewer_rejects_malformed_nested_service_id(monkeypatch, service_id):
    status = json.dumps(
        {"Services": {service_id: {"TCP": {}, "Web": {}, "AllowFunnel": {}}}}
    )
    run = Mock(return_value=CompletedProcess([], 0, status, ""))
    monkeypatch.setattr(stream.shutil, "which", lambda name: "/usr/bin/tailscale")
    monkeypatch.setattr(stream.subprocess, "run", run)

    result = stream._try_tailscale_serve(LOCAL_VIEWER, _settings())

    assert result["provisioned"] is False
    assert "status" in result["detail"].lower()
    assert "viewer_url" not in result
    assert run.call_count == 1


@pytest.mark.parametrize(
    "status_fields",
    [
        {"AllowFunnel": {f"{HOST}:0{PORT}": True}},
        {"TCP": {f"0{PORT}": {"HTTPS": True}}},
    ],
)
def test_tailscale_viewer_rejects_noncanonical_funnel_and_tcp_keys(
    monkeypatch, status_fields
):
    status = json.dumps({"Web": {}, **status_fields})
    run = Mock(return_value=CompletedProcess([], 0, status, ""))
    monkeypatch.setattr(stream.shutil, "which", lambda name: "/usr/bin/tailscale")
    monkeypatch.setattr(stream.subprocess, "run", run)

    result = stream._try_tailscale_serve(LOCAL_VIEWER, _settings())

    assert result["provisioned"] is False
    assert "status" in result["detail"].lower()
    assert "viewer_url" not in result
    assert run.call_count == 1


def test_oversized_tcp_key_fails_closed_without_raising(monkeypatch):
    status = json.dumps({"TCP": {"9" * 5000: {"HTTPS": True}}})
    run = Mock(return_value=CompletedProcess([], 0, status, ""))
    monkeypatch.setattr(stream.shutil, "which", lambda name: "/usr/bin/tailscale")
    monkeypatch.setattr(stream.subprocess, "run", run)

    result = stream._try_tailscale_serve(LOCAL_VIEWER, _settings())

    assert result["provisioned"] is False
    assert "status" in result["detail"].lower()
    assert "viewer_url" not in result
    assert run.call_count == 1


def test_stream_stop_terminates_viewers_when_status_parse_fails(monkeypatch):
    malformed = json.dumps({"Web": {}, "AllowFunnel": {f"{HOST}:0{PORT}": True}})
    run = Mock(return_value=CompletedProcess([], 0, malformed, ""))
    terminate = Mock()
    monkeypatch.setattr(stream.shutil, "which", lambda name: "/usr/bin/tailscale")
    monkeypatch.setattr(stream.subprocess, "run", run)
    monkeypatch.setattr(stream, "_terminate", terminate)

    manager = stream.StreamManager()
    manager._session = stream.StreamSession(
        session_id="active-session",
        expires_at=stream.datetime.now(stream.timezone.utc)
        + stream.timedelta(seconds=30),
        mode="view",
        active=True,
        tunnel={
            "mode": "tailscale",
            "provisioned": True,
            "serve_port": PORT,
            "serve_path": PATH,
            "serve_service": None,
            "serve_hostport": f"{HOST}:{PORT}",
            "local_target": LOCAL_TARGET,
        },
        x11vnc_pid=101,
        websockify_pid=202,
    )

    result = manager.stop("active-session")

    assert result["stopped"] is True
    assert terminate.call_args_list == [
        ((202, "websockify"), {}),
        ((101, "x11vnc"), {}),
    ]
    assert run.call_count == 1


def test_tailscale_viewer_rejects_deeply_nested_status_without_raising(monkeypatch):
    status = "[" * 5000 + "0" + "]" * 5000
    run = Mock(return_value=CompletedProcess([], 0, status, ""))
    monkeypatch.setattr(stream.shutil, "which", lambda name: "/usr/bin/tailscale")
    monkeypatch.setattr(stream.subprocess, "run", run)

    result = stream._try_tailscale_serve(LOCAL_VIEWER, _settings())

    assert result["provisioned"] is False
    assert "status" in result["detail"].lower()
    assert "viewer_url" not in result
    assert run.call_count == 1


def test_tailscale_viewer_handles_json_recursion_error(monkeypatch):
    run = Mock(return_value=CompletedProcess([], 0, "{}", ""))
    monkeypatch.setattr(stream.shutil, "which", lambda name: "/usr/bin/tailscale")
    monkeypatch.setattr(stream.subprocess, "run", run)
    monkeypatch.setattr(
        stream.json, "loads", Mock(side_effect=RecursionError("deep JSON"))
    )

    result = stream._try_tailscale_serve(LOCAL_VIEWER, _settings())

    assert result["provisioned"] is False
    assert "status" in result["detail"].lower()
    assert "viewer_url" not in result
    assert run.call_count == 1

"""On-demand desktop streaming: x11vnc + noVNC/websockify + JWT + tunnel hooks.

Idle = no stream processes we own (or left stopped). On start:
  1. Ensure x11vnc on DISPLAY bound to VNC_BIND:VNC_PORT
  2. Ensure websockify + noVNC web root on VNC_BIND:NOVNC_PORT → VNC
  3. Mint short-lived JWT viewer token
  4. Optionally provision Tailscale Serve / Cloudflare Tunnel when available
  5. Return viewer_url (localhost path always works for smoke)

Single concurrent session; TTL defaults to STREAM_TTL_SECONDS (15 min).
"""

from __future__ import annotations

import ipaddress
import json
import logging
import os
import secrets
import shutil
import signal
import subprocess
import threading
import time
import uuid
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any
from urllib.parse import urlencode, urlsplit

import jwt

from .config import Settings, get_settings
from .desktop import display_available

logger = logging.getLogger(__name__)

HOST_ROOT = Path(__file__).resolve().parents[2]
LOG_DIR = HOST_ROOT / "logs"
PID_DIR = HOST_ROOT / "data" / "pids"


class StreamError(Exception):
    """Operational stream failure."""

    def __init__(self, message: str, *, code: str = "stream_error"):
        super().__init__(message)
        self.message = message
        self.code = code


# Back-compat alias used by older callers / docs
class StreamNotImplemented(StreamError):
    def __init__(self, message: str = "Stream stack not available"):
        super().__init__(message, code="not_implemented")


@dataclass
class StreamSession:
    session_id: str
    expires_at: datetime
    mode: str = "view"  # view | interactive
    active: bool = False
    viewer_url: str | None = None
    viewer_token: str | None = None
    local_viewer_url: str | None = None
    tunnel: dict[str, Any] = field(default_factory=dict)
    meta: dict[str, Any] = field(default_factory=dict)
    x11vnc_pid: int | None = None
    websockify_pid: int | None = None


def _jwt_secret(settings: Settings) -> str:
    secret = (getattr(settings, "stream_jwt_secret", None) or "").strip()
    if secret:
        return secret
    # Derive from API token so one secret rotates together in lab setups
    return f"stream:{settings.api_token}"


def mint_viewer_token(
    *,
    session_id: str,
    mode: str,
    expires_at: datetime,
    settings: Settings,
) -> str:
    now = datetime.now(timezone.utc)
    payload = {
        "sid": session_id,
        "mode": mode,
        "iat": int(now.timestamp()),
        "exp": int(expires_at.timestamp()),
        "aud": "friendly-host-viewer",
        "jti": secrets.token_hex(8),
    }
    return jwt.encode(payload, _jwt_secret(settings), algorithm="HS256")


def verify_viewer_token(token: str, settings: Settings | None = None) -> dict[str, Any]:
    s = settings or get_settings()
    try:
        return jwt.decode(
            token,
            _jwt_secret(s),
            algorithms=["HS256"],
            audience="friendly-host-viewer",
        )
    except jwt.PyJWTError as e:
        raise StreamError(f"Invalid viewer token: {e}", code="bad_token") from e


def _which_or_raise(name: str) -> str:
    path = shutil.which(name)
    if not path:
        raise StreamError(
            f"{name} not found. Install via scripts/bootstrap-host.sh (apt: x11vnc websockify novnc).",
            code="missing_tool",
        )
    return path


def _novnc_web_root(settings: Settings) -> Path:
    configured = Path(
        getattr(settings, "novnc_web_root", "/usr/share/novnc") or "/usr/share/novnc"
    )
    if (configured / "vnc.html").is_file():
        return configured
    # Project vendor fallback
    vendor = HOST_ROOT / "viewer" / "novnc"
    if (vendor / "vnc.html").is_file():
        return vendor
    raise StreamError(
        f"noVNC web root not found at {configured}. Install package `novnc` or vendor under viewer/novnc/.",
        code="missing_novnc",
    )


def _port_open(host: str, port: int) -> bool:
    import socket

    try:
        with socket.create_connection((host, port), timeout=0.5):
            return True
    except OSError:
        return False


def _wait_port(host: str, port: int, timeout: float = 8.0) -> bool:
    deadline = time.time() + timeout
    while time.time() < deadline:
        if _port_open(host, port):
            return True
        time.sleep(0.1)
    return False


def _pid_alive(pid: int | None) -> bool:
    if not pid:
        return False
    try:
        os.kill(pid, 0)
        return True
    except OSError:
        return False


def _terminate(pid: int | None, name: str) -> None:
    if not pid:
        return
    try:
        os.kill(pid, signal.SIGTERM)
    except ProcessLookupError:
        return
    except PermissionError:
        logger.warning("No permission to signal %s pid %s", name, pid)
        return
    for _ in range(30):
        if not _pid_alive(pid):
            return
        time.sleep(0.1)
    try:
        os.kill(pid, signal.SIGKILL)
    except OSError:
        pass


def _build_local_viewer_url(
    *,
    settings: Settings,
    token: str,
) -> str:
    host = (
        settings.vnc_bind if settings.vnc_bind not in ("0.0.0.0", "::") else "127.0.0.1"
    )
    if host in ("0.0.0.0", "::"):
        host = "127.0.0.1"
    # Prefer loopback for local URLs
    if settings.vnc_bind == "127.0.0.1":
        host = "127.0.0.1"
    qs = urlencode(
        {
            "autoconnect": "1",
            "reconnect": "1",
            "resize": "scale",
            "token": token,
        }
    )
    return f"http://{host}:{settings.novnc_port}/vnc.html?{qs}"


_TAILSCALE_ROOT_PATH = "/"
_TAILSCALE_HTTPS_PORTS = {8443, 10000}


def _is_canonical_service_id(service_id: Any) -> bool:
    return (
        isinstance(service_id, str)
        and service_id.startswith("svc:")
        and len(service_id) <= 257
        and _is_canonical_dns_name(service_id[4:])
    )


def _is_canonical_dns_name(host: str) -> bool:
    if (
        not host
        or len(host) > 253
        or not host.isascii()
        or host != host.lower()
        or host.endswith(".")
    ):
        return False
    if all(char.isdigit() or char == "." for char in host):
        return False
    return all(
        1 <= len(label) <= 63
        and label[0].isalnum()
        and label[-1].isalnum()
        and all(char.isalnum() or char == "-" for char in label)
        for label in host.split(".")
    )


def _is_canonical_host(host: str) -> bool:
    if (
        not host
        or len(host) > 253
        or not host.isascii()
        or host != host.lower()
        or "%" in host
    ):
        return False
    try:
        address = ipaddress.ip_address(host)
    except ValueError:
        return _is_canonical_dns_name(host)
    return str(address) == host


def _tailscale_hostport_port(hostport: Any) -> int:
    if not isinstance(hostport, str) or not hostport:
        raise TypeError("Tailscale Serve status has an invalid Web hostport")
    try:
        parsed = urlsplit(f"//{hostport}")
        port = parsed.port
    except ValueError as e:
        raise TypeError("Tailscale Serve status has a malformed Web hostport") from e
    port_text = hostport.rsplit(":", 1)[-1]
    if hostport.startswith("["):
        closing = hostport.find("]")
        if closing < 2 or hostport[closing + 1 : closing + 2] != ":":
            raise TypeError("Tailscale Serve status has a malformed Web hostport")
        raw_host = hostport[1:closing]
        bracketed_ipv6 = ":" in raw_host
    else:
        raw_host = hostport.rsplit(":", 1)[0]
        bracketed_ipv6 = False
        if ":" in raw_host:
            raise TypeError("Tailscale Serve status has an unbracketed IPv6 host")
    if hostport.startswith("[") and not bracketed_ipv6:
        raise TypeError("Tailscale Serve status has a malformed IPv6 hostport")
    if (
        not parsed.hostname
        or raw_host != parsed.hostname
        or not _is_canonical_host(raw_host)
        or (hostport.startswith("[") and not bracketed_ipv6)
        or port is None
        or port < 1
        or port_text != str(port)
        or parsed.path
        or parsed.query
        or parsed.fragment
        or parsed.username is not None
        or parsed.password is not None
    ):
        raise TypeError("Tailscale Serve status has a malformed Web hostport")
    return port


def _tailscale_tcp_port(port_key: Any) -> int:
    if (
        not isinstance(port_key, str)
        or not port_key.isascii()
        or not port_key.isdigit()
        or len(port_key) > 5
    ):
        raise TypeError("Tailscale Serve status has a malformed TCP port")
    port = int(port_key)
    if not 1 <= port <= 65535 or str(port) != port_key:
        raise TypeError("Tailscale Serve status has a noncanonical TCP port")
    return port


def _tailscale_serve_status() -> dict[str, Any]:
    result = subprocess.run(
        ["tailscale", "serve", "status", "--json"],
        capture_output=True,
        text=True,
        timeout=10,
        check=False,
    )
    if result.returncode != 0:
        raise RuntimeError(f"tailscale serve status exited {result.returncode}")
    raw_status = result.stdout or ""
    if not raw_status.strip():
        raise RuntimeError("tailscale serve status returned empty output")
    try:
        status = json.loads(raw_status)
    except (json.JSONDecodeError, RecursionError) as e:
        raise RuntimeError(
            "tailscale serve status returned invalid or deeply nested JSON"
        ) from e
    if not isinstance(status, dict):
        raise TypeError("tailscale serve status returned an invalid document")
    recognized_fields = {"Services", "TCP", "Web", "AllowFunnel"}
    if not recognized_fields.intersection(status):
        raise TypeError("tailscale serve status has no recognized fields")
    services = status.get("Services", {})
    if not isinstance(services, dict):
        raise TypeError("tailscale serve status has invalid Services")
    service_fields = {"TCP", "Web", "AllowFunnel"}
    for service_id, service in services.items():
        if not _is_canonical_service_id(service_id):
            raise TypeError(
                f"tailscale serve status has invalid service ID {service_id}"
            )
        if not isinstance(service, dict):
            raise TypeError(f"tailscale serve status has invalid service {service_id}")
        if service and not service_fields.intersection(service):
            raise TypeError(
                f"tailscale serve status has unknown fields for service {service_id}"
            )
    scopes = [status, *services.values()]
    if any(not isinstance(scope, dict) for scope in scopes):
        raise TypeError("tailscale serve status has an invalid service scope")
    for scope in scopes:
        for key in ("Web", "TCP", "AllowFunnel"):
            if key in scope and not isinstance(scope[key], dict):
                raise TypeError(f"tailscale serve status has invalid {key}")
        for hostport in scope.get("Web") or {}:
            _tailscale_hostport_port(hostport)
        for port_key in scope.get("TCP") or {}:
            _tailscale_tcp_port(port_key)
        for hostport, enabled in (scope.get("AllowFunnel") or {}).items():
            _tailscale_hostport_port(hostport)
            if not isinstance(enabled, bool):
                raise TypeError(
                    f"tailscale serve status has invalid Funnel flag for {hostport}"
                )
    return status


def _tailscale_scopes(
    status: dict[str, Any],
) -> list[tuple[str | None, dict[str, Any]]]:
    scopes: list[tuple[str | None, dict[str, Any]]] = [(None, status)]
    services = status.get("Services") or {}
    if isinstance(services, dict):
        for service_id, service in services.items():
            if isinstance(service, dict):
                scopes.append((str(service_id), service))
    return scopes


def _tailscale_funnel_enabled(status: dict[str, Any], port: int) -> bool:
    for _service_id, scope in _tailscale_scopes(status):
        allowed = scope.get("AllowFunnel") or {}
        if isinstance(allowed, dict) and any(
            str(hostport).endswith(f":{port}") and enabled is True
            for hostport, enabled in allowed.items()
        ):
            return True
    return False


def _tailscale_web_handlers(
    status: dict[str, Any], port: int
) -> list[tuple[str | None, str, str, str | None]]:
    handlers = []
    for service_id, scope in _tailscale_scopes(status):
        web = scope.get("Web") or {}
        if not isinstance(web, dict):
            continue
        for hostport, service in web.items():
            if not str(hostport).endswith(f":{port}"):
                continue
            if not isinstance(service, dict):
                handlers.append((service_id, str(hostport), None, None))
                continue
            routes = service.get("Handlers")
            if not isinstance(routes, dict) or not routes:
                handlers.append((service_id, str(hostport), None, None))
                continue
            for path, handler in routes.items():
                proxy = handler.get("Proxy") if isinstance(handler, dict) else None
                handlers.append((service_id, str(hostport), str(path), proxy))
    return handlers


def _tailscale_root_routes(
    status: dict[str, Any], port: int
) -> list[tuple[str | None, str, str | None]]:
    return [
        (service_id, hostport, proxy)
        for service_id, hostport, path, proxy in _tailscale_web_handlers(status, port)
        if path == _TAILSCALE_ROOT_PATH
    ]


def _tailscale_tcp_scopes(status: dict[str, Any], port: int) -> list[str | None]:
    return [
        service_id
        for service_id, scope in _tailscale_scopes(status)
        if isinstance(scope.get("TCP"), dict) and str(port) in scope["TCP"]
    ]


def _try_tailscale_serve(local_http: str, settings: Settings) -> dict[str, Any]:
    """Serve noVNC privately on a dedicated Tailscale HTTPS port."""
    if not shutil.which("tailscale"):
        return {
            "mode": "tailscale",
            "provisioned": False,
            "detail": "tailscale binary not found — using localhost viewer URL",
        }

    port = settings.tailscale_serve_port
    local_target = f"http://127.0.0.1:{settings.novnc_port}"
    if port not in _TAILSCALE_HTTPS_PORTS:
        return {
            "mode": "tailscale",
            "provisioned": False,
            "detail": "Tailscale viewer port must be 8443 or 10000 (never the Funnel port 443)",
            "local_http": local_http,
        }

    try:
        before = _tailscale_serve_status()
    except (OSError, RuntimeError, TypeError, subprocess.TimeoutExpired) as e:
        return {
            "mode": "tailscale",
            "provisioned": False,
            "detail": f"tailscale serve status failed: {e}",
            "local_http": local_http,
        }

    if _tailscale_funnel_enabled(before, port):
        return {
            "mode": "tailscale",
            "provisioned": False,
            "detail": f"Funnel is enabled on :{port}; refusing to expose the viewer",
            "local_http": local_http,
        }

    web_handlers = _tailscale_web_handlers(before, port)
    routes = _tailscale_root_routes(before, port)
    matching_routes = [route for route in routes if route[2] == local_target]
    existing = matching_routes[0] if len(matching_routes) == 1 else None
    tcp_scopes = _tailscale_tcp_scopes(before, port)
    if web_handlers and (
        existing is None
        or len(matching_routes) != 1
        or len(web_handlers) != 1
        or web_handlers[0]
        != (existing[0], existing[1], _TAILSCALE_ROOT_PATH, local_target)
        or any(scope != existing[0] for scope in tcp_scopes)
    ):
        return {
            "mode": "tailscale",
            "provisioned": False,
            "detail": f"Tailscale Serve :{port} is already configured for another service",
            "local_http": local_http,
        }
    if not web_handlers and tcp_scopes:
        return {
            "mode": "tailscale",
            "provisioned": False,
            "detail": f"Tailscale Serve :{port} is already occupied",
            "local_http": local_http,
        }

    created = existing is None
    if created:
        route = {
            "mode": "tailscale",
            "provisioned": True,
            "serve_port": port,
            "serve_path": _TAILSCALE_ROOT_PATH,
            "local_target": local_target,
        }

        def failed_serve_attempt(detail: str) -> dict[str, Any]:
            cleanup_ok = _teardown_tunnel(route, settings)
            cleanup_detail = (
                "partial route cleanup verified"
                if cleanup_ok
                else "route cleanup pending"
            )
            return {
                **route,
                "provisioned": not cleanup_ok,
                "detail": f"{detail}; {cleanup_detail}",
                "local_http": local_http,
            }

        command = [
            "tailscale",
            "serve",
            f"--https={port}",
            f"--set-path={_TAILSCALE_ROOT_PATH}",
            "--bg",
            local_target,
        ]
        try:
            result = subprocess.run(
                command,
                capture_output=True,
                text=True,
                timeout=15,
                check=False,
            )
        except (OSError, subprocess.TimeoutExpired) as e:
            return failed_serve_attempt(f"tailscale serve failed: {e}")
        output = (result.stdout or "") + (result.stderr or "")
        if result.returncode != 0:
            return failed_serve_attempt(
                f"tailscale serve failed: {output.strip() or result.returncode}"
            )
        try:
            after = _tailscale_serve_status()
        except (OSError, RuntimeError, TypeError, subprocess.TimeoutExpired) as e:
            return {
                "mode": "tailscale",
                "provisioned": True,
                "serve_port": port,
                "serve_path": _TAILSCALE_ROOT_PATH,
                "local_target": local_target,
                "detail": f"Tailscale Serve started but status verification failed: {e}",
                "local_http": local_http,
            }
        if _tailscale_funnel_enabled(after, port):
            route = {
                "mode": "tailscale",
                "provisioned": True,
                "serve_port": port,
                "serve_path": _TAILSCALE_ROOT_PATH,
                "local_target": local_target,
            }
            matches = [
                item
                for item in _tailscale_root_routes(after, port)
                if item[2] == local_target
            ]
            if len(matches) == 1:
                route["serve_service"] = matches[0][0]
                route["serve_hostport"] = matches[0][1]
            removed = _teardown_tunnel(route, settings)
            return {
                **route,
                "provisioned": not removed,
                "detail": (
                    f"Funnel became enabled on :{port}; viewer route removed"
                    if removed
                    else f"Funnel became enabled on :{port}; viewer route cleanup failed"
                ),
                "local_http": local_http,
            }
        routes = _tailscale_root_routes(after, port)
        matches = [item for item in routes if item[2] == local_target]
        if len(matches) != 1:
            return {
                "mode": "tailscale",
                "provisioned": True,
                "serve_port": port,
                "serve_path": _TAILSCALE_ROOT_PATH,
                "local_target": local_target,
                "detail": f"Tailscale Serve :{port}/ did not verify against {local_target}",
                "local_http": local_http,
            }
        existing = matches[0]
        handlers = _tailscale_web_handlers(after, port)
        if handlers != [(existing[0], existing[1], _TAILSCALE_ROOT_PATH, local_target)]:
            route = {
                "mode": "tailscale",
                "provisioned": True,
                "serve_port": port,
                "serve_path": _TAILSCALE_ROOT_PATH,
                "serve_service": existing[0],
                "serve_hostport": existing[1],
                "local_target": local_target,
            }
            removed = _teardown_tunnel(route, settings)
            return {
                **route,
                "provisioned": not removed,
                "detail": f"Unexpected Tailscale Serve handlers on :{port}; owned route cleanup "
                f"{'verified' if removed else 'pending'}",
                "local_http": local_http,
            }

    if existing is None:
        return {
            "mode": "tailscale",
            "provisioned": True,
            "serve_port": port,
            "serve_path": _TAILSCALE_ROOT_PATH,
            "local_target": local_target,
            "detail": f"Tailscale Serve :{port}/ route could not be identified",
            "local_http": local_http,
        }
    service_id, hostport, _proxy = existing
    token_q = "?" + local_http.split("?", 1)[1] if "?" in local_http else ""
    public = f"https://{hostport}"
    return {
        "mode": "tailscale",
        "provisioned": created,
        "serve_port": port,
        "serve_path": _TAILSCALE_ROOT_PATH,
        "serve_service": service_id,
        "serve_hostport": hostport,
        "local_target": local_target,
        "public_base": public,
        "viewer_url": f"{public}/vnc.html{token_q}",
        "detail": "Tailscale Serve active on a tailnet-only HTTPS port",
    }


def _try_cloudflare_tunnel(local_http: str, settings: Settings) -> dict[str, Any]:
    if not shutil.which("cloudflared"):
        return {
            "mode": "cloudflare",
            "provisioned": False,
            "detail": "cloudflared binary not found — using localhost viewer URL",
        }
    # Named tunnel / quick tunnel needs credentials; do not invent URLs.
    creds = getattr(settings, "cloudflared_token", "") or os.environ.get(
        "CLOUDFLARED_TOKEN", ""
    )
    config = getattr(settings, "cloudflared_config", "") or ""
    if not creds and not (config and Path(config).is_file()):
        return {
            "mode": "cloudflare",
            "provisioned": False,
            "detail": (
                "cloudflared present but CLOUDFLARED_TOKEN / config missing — "
                "using localhost viewer URL"
            ),
            "hint": "Set CLOUDFLARED_TOKEN or TUNNEL_MODE=localhost",
            "local_http": local_http,
        }
    # Quick tunnel: cloudflared tunnel --url http://127.0.0.1:PORT
    # Run in background and scrape trycloudflare.com URL from logs if quick mode.
    try:
        log_path = LOG_DIR / "cloudflared-stream.log"
        LOG_DIR.mkdir(parents=True, exist_ok=True)
        argv = ["cloudflared"]
        if creds:
            argv += ["tunnel", "run", "--token", creds]
        else:
            argv += ["tunnel", "--config", config, "run"]
        # For ephemeral quick tunnel without token:
        # cloudflared tunnel --url http://127.0.0.1:novnc
        # Only use quick URL mode when explicitly tunnel_mode=cloudflare_quick
        mode = (settings.tunnel_mode or "").lower()
        if mode in ("cloudflare_quick", "cf_quick"):
            argv = [
                "cloudflared",
                "tunnel",
                "--url",
                f"http://127.0.0.1:{settings.novnc_port}",
            ]
        proc = subprocess.Popen(
            argv,
            stdout=open(log_path, "a"),  # noqa: SIM115
            stderr=subprocess.STDOUT,
            start_new_session=True,
        )
        # Try to scrape quick tunnel URL for a few seconds
        public = None
        if mode in ("cloudflare_quick", "cf_quick"):
            deadline = time.time() + 12
            while time.time() < deadline:
                try:
                    text = log_path.read_text(errors="replace")
                except OSError:
                    text = ""
                for line in text.splitlines()[::-1]:
                    if "trycloudflare.com" in line and "https://" in line:
                        for part in line.split():
                            if (
                                part.startswith("https://")
                                and "trycloudflare.com" in part
                            ):
                                public = part.strip().rstrip("/")
                                break
                    if public:
                        break
                if public:
                    break
                time.sleep(0.4)
        token_q = ""
        if "?" in local_http:
            token_q = "?" + local_http.split("?", 1)[1]
        result: dict[str, Any] = {
            "mode": "cloudflare",
            "provisioned": True,
            "pid": proc.pid,
            "local_http": local_http,
            "log": str(log_path),
        }
        if public:
            result["public_base"] = public
            result["viewer_url"] = f"{public}/vnc.html{token_q}"
            result["detail"] = "cloudflared quick tunnel up"
        else:
            result["detail"] = (
                "cloudflared started; public URL not parsed — check logs / CF dashboard"
            )
        return result
    except OSError as e:
        return {
            "mode": "cloudflare",
            "provisioned": False,
            "detail": f"cloudflared failed: {e}",
            "local_http": local_http,
        }


def _teardown_tunnel(tunnel: dict[str, Any], settings: Settings) -> bool:
    mode = (tunnel or {}).get("mode")
    if mode == "tailscale" and tunnel.get("provisioned"):
        if not shutil.which("tailscale"):
            return False
        try:
            port = int(tunnel.get("serve_port", settings.tailscale_serve_port))
        except (TypeError, ValueError):
            logger.warning("skipping Tailscale Serve cleanup with invalid port")
            return False
        path = tunnel.get("serve_path", _TAILSCALE_ROOT_PATH)
        target = tunnel.get("local_target", f"http://127.0.0.1:{settings.novnc_port}")
        expected_target = f"http://127.0.0.1:{settings.novnc_port}"
        service_hint = tunnel.get("serve_service")
        hostport_hint = tunnel.get("serve_hostport")
        if service_hint is not None and not _is_canonical_service_id(service_hint):
            logger.warning("skipping Tailscale Serve cleanup with invalid service id")
            return False
        if hostport_hint is not None:
            try:
                if _tailscale_hostport_port(hostport_hint) != port:
                    raise TypeError("hostport does not match listener port")
            except TypeError:
                logger.warning("skipping Tailscale Serve cleanup with invalid hostport")
                return False
        if (
            port not in _TAILSCALE_HTTPS_PORTS
            or path != _TAILSCALE_ROOT_PATH
            or target != expected_target
        ):
            logger.warning(
                "skipping unsafe Tailscale Serve cleanup for :%s%s", port, path
            )
            return False
        try:
            status = _tailscale_serve_status()
            routes = [
                route
                for route in _tailscale_root_routes(status, port)
                if route[2] == target
            ]
            if "serve_service" in tunnel:
                routes = [
                    route for route in routes if route[0] == tunnel["serve_service"]
                ]
            if "serve_hostport" in tunnel:
                routes = [
                    route for route in routes if route[1] == tunnel["serve_hostport"]
                ]
            if not routes:
                logger.info("Tailscale Serve route changed; leaving it untouched")
                return True
            if len(routes) != 1:
                logger.warning(
                    "multiple Tailscale Serve routes match; leaving them untouched"
                )
                return False
            service_id, _hostport, _proxy = routes[0]
            command = ["tailscale", "serve"]
            if service_id is not None:
                command.append(f"--service={service_id}")
            command.extend([f"--https={port}", f"--set-path={path}", "off"])
            result = subprocess.run(
                command,
                capture_output=True,
                text=True,
                timeout=10,
                check=False,
            )
            if result.returncode != 0:
                logger.warning(
                    "scoped Tailscale Serve cleanup failed: %s", result.stderr
                )
            return result.returncode == 0
        except (OSError, RuntimeError, TypeError, subprocess.TimeoutExpired):
            logger.warning("scoped Tailscale Serve cleanup failed", exc_info=True)
            return False
    if mode == "cloudflare" and tunnel.get("pid"):
        _terminate(int(tunnel["pid"]), "cloudflared")
    return True


def _start_x11vnc(settings: Settings, mode: str) -> int:
    _which_or_raise("x11vnc")
    if not display_available(settings):
        raise StreamError(
            f"No X display on DISPLAY={settings.display}. Start idle stack first.",
            code="no_display",
        )
    bind = settings.vnc_bind
    port = settings.vnc_port
    # If our port already answers and belongs to a prior session we re-use;
    # if something else holds it, fail clearly.
    if _port_open("127.0.0.1", port):
        logger.info("VNC port %s already open — reusing", port)
        return 0  # unknown pid; treated as external/reused

    LOG_DIR.mkdir(parents=True, exist_ok=True)
    PID_DIR.mkdir(parents=True, exist_ok=True)
    log = LOG_DIR / "x11vnc.log"
    argv = [
        "x11vnc",
        "-display",
        settings.display,
        "-rfbport",
        str(port),
        "-forever",
        "-shared",
        "-nopw",
        "-xkb",
        "-noxdamage",
        "-quiet",
    ]
    # Bind localhost only when configured
    if bind in ("127.0.0.1", "localhost", "::1"):
        argv.append("-localhost")

    env = {**os.environ, "DISPLAY": settings.display}
    with open(log, "ab") as lf:
        proc = subprocess.Popen(
            argv,
            env=env,
            stdout=lf,
            stderr=subprocess.STDOUT,
            start_new_session=True,
        )
    if not _wait_port("127.0.0.1", port, timeout=8):
        _terminate(proc.pid, "x11vnc")
        raise StreamError(
            f"x11vnc failed to listen on {bind}:{port} — see {log}",
            code="vnc_start_failed",
        )
    (PID_DIR / "x11vnc.pid").write_text(str(proc.pid))
    logger.info("x11vnc started pid=%s port=%s mode=%s", proc.pid, port, mode)
    return proc.pid


def _start_websockify(settings: Settings) -> int:
    _which_or_raise("websockify")
    web = _novnc_web_root(settings)
    bind = settings.vnc_bind if settings.vnc_bind not in ("0.0.0.0",) else "127.0.0.1"
    # Always prefer loopback for smoke / security default
    listen_host = "127.0.0.1" if bind in ("127.0.0.1", "localhost", "::1") else bind
    port = settings.novnc_port
    target = f"127.0.0.1:{settings.vnc_port}"

    if _port_open("127.0.0.1", port):
        logger.info("noVNC/websockify port %s already open — reusing", port)
        return 0

    LOG_DIR.mkdir(parents=True, exist_ok=True)
    PID_DIR.mkdir(parents=True, exist_ok=True)
    log = LOG_DIR / "websockify.log"
    argv = [
        "websockify",
        f"--web={web}",
        "--heartbeat=30",
        f"{listen_host}:{port}",
        target,
    ]
    with open(log, "ab") as lf:
        proc = subprocess.Popen(
            argv,
            stdout=lf,
            stderr=subprocess.STDOUT,
            start_new_session=True,
        )
    if not _wait_port("127.0.0.1", port, timeout=8):
        _terminate(proc.pid, "websockify")
        raise StreamError(
            f"websockify failed to listen on {listen_host}:{port} — see {log}",
            code="novnc_start_failed",
        )
    (PID_DIR / "websockify.pid").write_text(str(proc.pid))
    logger.info("websockify+noVNC started pid=%s port=%s web=%s", proc.pid, port, web)
    return proc.pid


class StreamManager:
    """Single-session on-demand stream controller."""

    def __init__(self) -> None:
        self._lock = threading.RLock()
        self._session: StreamSession | None = None
        self._expiry_timer: threading.Timer | None = None

    def _cancel_timer(self) -> None:
        if self._expiry_timer is not None:
            self._expiry_timer.cancel()
            self._expiry_timer = None

    def _schedule_expiry(self, session_id: str, expires_at: datetime) -> None:
        self._cancel_timer()
        delay = max(0.5, (expires_at - datetime.now(timezone.utc)).total_seconds())

        def _fire() -> None:
            logger.info("Stream TTL expired for %s — auto-stop", session_id)
            try:
                self.stop(session_id)
            except Exception:
                logger.exception("auto-stop failed")

        t = threading.Timer(delay, _fire)
        t.daemon = True
        t.start()
        self._expiry_timer = t

    def _expire_if_needed(self) -> None:
        sess = self._session
        if sess and sess.active and sess.expires_at <= datetime.now(timezone.utc):
            sid = sess.session_id
            logger.info("Session %s past TTL on status check", sid)
            self._stop_locked(sess)

    def status(self, settings: Settings | None = None) -> dict[str, Any]:
        s = settings or get_settings()
        with self._lock:
            self._expire_if_needed()
            sess = self._session
            base = {
                "implemented": True,
                "tunnel_mode": s.tunnel_mode,
                "vnc_port": s.vnc_port,
                "novnc_port": s.novnc_port,
                "tools": {
                    "x11vnc": shutil.which("x11vnc") is not None,
                    "websockify": shutil.which("websockify") is not None,
                    "novnc_web": (_novnc_web_root(s) / "vnc.html").is_file()
                    if shutil.which("websockify")
                    else False,
                },
            }
            try:
                base["tools"]["novnc_web"] = (_novnc_web_root(s) / "vnc.html").is_file()
            except StreamError:
                base["tools"]["novnc_web"] = False

            if sess is None or not sess.active:
                return {
                    **base,
                    "active": False,
                    "expires_at": None,
                    "mode": None,
                    "session_id": None,
                    "viewer_url": None,
                    "detail": "no active stream session",
                }
            return {
                **base,
                "active": True,
                "expires_at": sess.expires_at.isoformat(),
                "mode": sess.mode,
                "session_id": sess.session_id,
                "viewer_url": sess.viewer_url,
                "local_viewer_url": sess.local_viewer_url,
                "tunnel": sess.tunnel,
                "detail": "stream active",
            }

    def start(
        self, settings: Settings | None = None, mode: str = "view"
    ) -> dict[str, Any]:
        s = settings or get_settings()
        if mode not in ("view", "interactive"):
            raise StreamError(f"Invalid mode: {mode}", code="bad_mode")

        with self._lock:
            self._expire_if_needed()
            if self._session and self._session.active:
                raise StreamError(
                    f"Stream already active (session_id={self._session.session_id}). "
                    "Stop it before starting another.",
                    code="already_active",
                )

            # Preflight tools
            _which_or_raise("x11vnc")
            _which_or_raise("websockify")
            _novnc_web_root(s)

            session_id = str(uuid.uuid4())
            now = datetime.now(timezone.utc)
            expires_at = now + timedelta(seconds=s.stream_ttl_seconds)

            x11_pid = _start_x11vnc(s, mode)
            try:
                ws_pid = _start_websockify(s)
            except StreamError:
                if x11_pid:
                    _terminate(x11_pid, "x11vnc")
                raise

            token = mint_viewer_token(
                session_id=session_id,
                mode=mode,
                expires_at=expires_at,
                settings=s,
            )
            local_url = _build_local_viewer_url(settings=s, token=token)

            tunnel_mode = (s.tunnel_mode or "localhost").lower()
            if tunnel_mode in ("auto",):
                if shutil.which("tailscale"):
                    tunnel_mode = "tailscale"
                elif shutil.which("cloudflared"):
                    tunnel_mode = "cloudflare"
                else:
                    tunnel_mode = "localhost"

            if tunnel_mode in ("tailscale",):
                tunnel = _try_tailscale_serve(local_url, s)
            elif tunnel_mode in ("cloudflare", "cloudflare_quick", "cf_quick"):
                tunnel = _try_cloudflare_tunnel(local_url, s)
            else:
                tunnel = {
                    "mode": "localhost",
                    "provisioned": False,
                    "detail": (
                        "Localhost viewer only (no Tailscale/cloudflared). "
                        "Set TUNNEL_MODE=tailscale|cloudflare when credentials exist."
                    ),
                }

            viewer_url = tunnel.get("viewer_url") or local_url

            sess = StreamSession(
                session_id=session_id,
                expires_at=expires_at,
                mode=mode,
                active=True,
                viewer_url=viewer_url,
                viewer_token=token,
                local_viewer_url=local_url,
                tunnel=tunnel,
                x11vnc_pid=x11_pid or None,
                websockify_pid=ws_pid or None,
                meta={"started_at": now.isoformat()},
            )
            self._session = sess
            self._schedule_expiry(session_id, expires_at)
            logger.info(
                "Stream started session=%s mode=%s viewer=%s tunnel=%s",
                session_id,
                mode,
                viewer_url.split("?", 1)[0],
                tunnel.get("mode"),
            )
            return {
                "viewer_url": viewer_url,
                "local_viewer_url": local_url,
                "session_id": session_id,
                "expires_at": expires_at.isoformat(),
                "mode": mode,
                "tunnel": tunnel,
                "token": token,
                "implemented": True,
            }

    def _stop_locked(self, sess: StreamSession) -> dict[str, Any]:
        self._cancel_timer()
        _teardown_tunnel(sess.tunnel, get_settings())
        # Only kill processes we started (pid != 0/None). Reused ports left alone
        # unless we own the pid file matching.
        if sess.websockify_pid:
            _terminate(sess.websockify_pid, "websockify")
        else:
            # If we wrote a pid file for websockify, kill that
            pf = PID_DIR / "websockify.pid"
            if pf.exists():
                try:
                    _terminate(int(pf.read_text().strip()), "websockify")
                    pf.unlink(missing_ok=True)
                except (ValueError, OSError):
                    pass
        if sess.x11vnc_pid:
            _terminate(sess.x11vnc_pid, "x11vnc")
        else:
            pf = PID_DIR / "x11vnc.pid"
            if pf.exists():
                try:
                    _terminate(int(pf.read_text().strip()), "x11vnc")
                    pf.unlink(missing_ok=True)
                except (ValueError, OSError):
                    pass

        sid = sess.session_id
        sess.active = False
        self._session = None
        logger.info("Stream stopped session=%s", sid)
        return {
            "stopped": True,
            "session_id": sid,
            "implemented": True,
        }

    def stop(
        self, session_id: str | None = None, settings: Settings | None = None
    ) -> dict[str, Any]:
        _ = settings or get_settings()
        with self._lock:
            sess = self._session
            if sess is None or not sess.active:
                return {
                    "stopped": False,
                    "session_id": session_id,
                    "detail": "no active stream session",
                    "implemented": True,
                }
            if session_id and session_id != sess.session_id:
                return {
                    "stopped": False,
                    "session_id": session_id,
                    "detail": "session_id mismatch",
                    "implemented": True,
                }
            return self._stop_locked(sess)


# Process-wide singleton
stream_manager = StreamManager()

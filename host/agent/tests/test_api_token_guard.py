"""Startup guard and auth checks for an empty or placeholder API_TOKEN (REST and MCP)."""

from __future__ import annotations

import logging

import pytest
from fastapi import Depends, FastAPI
from fastapi.testclient import TestClient
from starlette.responses import PlainTextResponse

from app.auth import BearerAuthASGIMiddleware, require_bearer
from app.config import (
    PLACEHOLDER_API_TOKEN,
    ApiTokenConfigError,
    Settings,
    api_token_problem,
    get_settings,
    require_usable_api_token,
)

VALID = "valid-token-" + "q" * 40
BAD_TOKENS = ["", "   ", PLACEHOLDER_API_TOKEN, "change-me", "CHANGE-ME-please"]


@pytest.fixture
def env_token(monkeypatch):
    def set_token(value: str) -> None:
        monkeypatch.setenv("API_TOKEN", value)
        get_settings.cache_clear()

    yield set_token
    get_settings.cache_clear()


@pytest.mark.parametrize("token", BAD_TOKENS)
def test_problem_detected_without_echoing_token(token):
    problem = api_token_problem(token)
    assert problem
    if token.strip():
        assert token not in problem


def test_valid_token_has_no_problem():
    assert api_token_problem(VALID) is None
    require_usable_api_token(Settings(api_token=VALID))


def test_settings_default_is_rejected():
    with pytest.raises(ApiTokenConfigError):
        require_usable_api_token(Settings(_env_file=None, api_token=PLACEHOLDER_API_TOKEN))


@pytest.mark.parametrize("token", [PLACEHOLDER_API_TOKEN, "change-me-sentinel-XYZ", ""])
def test_app_startup_fails_and_never_prints_token(env_token, token, caplog):
    from app.main import app

    env_token(token)
    caplog.set_level(logging.DEBUG)
    with pytest.raises(ApiTokenConfigError) as exc:
        with TestClient(app):
            pass
    message = str(exc.value)
    assert "API_TOKEN" in message
    if token:
        assert token not in message
        assert token not in caplog.text


def _rest_app(token: str) -> TestClient:
    api = FastAPI()
    api.dependency_overrides[get_settings] = lambda: Settings(api_token=token)

    @api.get("/protected")
    def protected(_: None = Depends(require_bearer)) -> dict:
        return {"ok": True}

    return TestClient(api)


def _mcp_app(token: str) -> TestClient:
    async def inner(scope, receive, send):
        await PlainTextResponse("mcp-ok")(scope, receive, send)

    return TestClient(BearerAuthASGIMiddleware(inner, token_getter=lambda: token))


@pytest.mark.parametrize("make", [_rest_app, _mcp_app], ids=["rest", "mcp"])
@pytest.mark.parametrize("token", BAD_TOKENS)
def test_placeholder_or_empty_token_is_never_accepted(make, token):
    client = make(token)
    path = "/protected"
    r = client.get(path, headers={"Authorization": f"Bearer {token}"})
    assert r.status_code == 503
    assert not token.strip() or token not in r.text


@pytest.mark.parametrize("make", [_rest_app, _mcp_app], ids=["rest", "mcp"])
def test_valid_token_still_works(make):
    client = make(VALID)
    assert client.get("/protected", headers={"Authorization": f"Bearer {VALID}"}).status_code == 200
    assert client.get("/protected", headers={"Authorization": "Bearer wrong"}).status_code == 401
    assert client.get("/protected").status_code == 401


def test_real_app_starts_with_valid_token_and_guards_rest_and_mcp(env_token):
    from app.main import app

    env_token(VALID)
    with TestClient(app) as client:
        assert client.get("/health").status_code == 200
        assert client.get("/v1/desktop/status").status_code == 401
        assert client.post("/mcp/", json={}).status_code == 401
        r = client.post(
            "/mcp/",
            headers={
                "Authorization": f"Bearer {VALID}",
                "Accept": "application/json, text/event-stream",
            },
            json={
                "jsonrpc": "2.0",
                "id": 1,
                "method": "initialize",
                "params": {
                    "protocolVersion": "2025-03-26",
                    "capabilities": {},
                    "clientInfo": {"name": "pytest", "version": "0"},
                },
            },
        )
        assert r.status_code not in (401, 503)

"""Bearer token auth for Control API and MCP mount."""

from __future__ import annotations

import secrets
from typing import Callable

from fastapi import Depends, HTTPException, Security, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from starlette.requests import Request
from starlette.responses import JSONResponse, Response
from starlette.types import ASGIApp, Receive, Scope, Send

from .config import Settings, get_settings

_bearer = HTTPBearer(auto_error=False)


def require_bearer(
    credentials: HTTPAuthorizationCredentials | None = Security(_bearer),
    settings: Settings = Depends(get_settings),
) -> None:
    """Reject requests without a matching Authorization: Bearer <API_TOKEN>."""
    expected = settings.api_token
    if not expected:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="API_TOKEN is not configured on the host",
        )

    if credentials is None or credentials.scheme.lower() != "bearer":
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Missing or invalid Authorization header",
            headers={"WWW-Authenticate": "Bearer"},
        )

    token = credentials.credentials
    if not secrets.compare_digest(token, expected):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid API token",
            headers={"WWW-Authenticate": "Bearer"},
        )


def _extract_bearer(authorization: str | None) -> str | None:
    if not authorization:
        return None
    parts = authorization.split(None, 1)
    if len(parts) != 2 or parts[0].lower() != "bearer":
        return None
    return parts[1]


class BearerAuthASGIMiddleware:
    """ASGI middleware that enforces Bearer API_TOKEN (for mounted MCP app)."""

    def __init__(self, app: ASGIApp, token_getter: Callable[[], str] | None = None):
        self.app = app
        self._token_getter = token_getter or (lambda: get_settings().api_token)

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] not in ("http", "websocket"):
            await self.app(scope, receive, send)
            return

        expected = self._token_getter() or ""
        if not expected:
            response = JSONResponse(
                {"detail": "API_TOKEN is not configured on the host"},
                status_code=503,
            )
            await response(scope, receive, send)
            return

        headers = {
            k.decode("latin-1").lower(): v.decode("latin-1")
            for k, v in scope.get("headers") or []
        }
        token = _extract_bearer(headers.get("authorization"))
        if token is None or not secrets.compare_digest(token, expected):
            response = JSONResponse(
                {"detail": "Missing or invalid Authorization header"},
                status_code=401,
                headers={"WWW-Authenticate": "Bearer"},
            )
            await response(scope, receive, send)
            return

        await self.app(scope, receive, send)


# Keep Request-based helper for FastAPI middleware style if needed
async def bearer_http_middleware(request: Request, call_next) -> Response:
    settings = get_settings()
    expected = settings.api_token or ""
    if not expected:
        return JSONResponse(
            {"detail": "API_TOKEN is not configured on the host"},
            status_code=503,
        )
    token = _extract_bearer(request.headers.get("authorization"))
    if token is None or not secrets.compare_digest(token, expected):
        return JSONResponse(
            {"detail": "Missing or invalid Authorization header"},
            status_code=401,
            headers={"WWW-Authenticate": "Bearer"},
        )
    return await call_next(request)

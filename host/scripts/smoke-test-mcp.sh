#!/usr/bin/env bash
# Smoke-test MCP Streamable HTTP at /mcp (expects API already running).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -f "$ROOT/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env"
  set +a
fi

HOST="${API_HOST:-127.0.0.1}"
PORT="${API_PORT:-8787}"
BASE="http://${HOST}:${PORT}"
MCP_URL="${BASE}/mcp"
UNAUTH_URL="${BASE}/mcp/"
TOKEN="${API_TOKEN:-}"

if [[ -z "$TOKEN" ]]; then
  echo "ERROR: API_TOKEN not set" >&2
  exit 1
fi

VENV="$ROOT/agent/.venv"
if [[ ! -x "$VENV/bin/python" ]]; then
  echo "ERROR: venv missing — run scripts/bootstrap-host.sh" >&2
  exit 1
fi

echo "==> GET $BASE/health (mcp path advertised)"
curl -sfS "$BASE/health" | "$VENV/bin/python" -m json.tool | head -40

echo "==> MCP unauthenticated POST (expect 401 on /mcp/)"
# Starlette mount redirects /mcp → /mcp/; auth applies on the trailing-slash path.
CODE=$(curl -sS -o /tmp/friendly-mcp-unauth.json -w "%{http_code}" \
  -X POST "${BASE}/mcp/" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{}')
echo "    HTTP $CODE"
[[ "$CODE" == "401" ]] || echo "WARN: expected 401 got $CODE"

echo "==> MCP Client: list_tools + stream_status + screenshot"
"$VENV/bin/python" - <<PY
import asyncio
import json
import os
import sys

from mcp import Client
from mcp.client.streamable_http import create_mcp_http_client, streamable_http_client

URL = ${MCP_URL@Q}
TOKEN = ${TOKEN@Q}

async def main() -> int:
    async with create_mcp_http_client(
        headers={"Authorization": f"Bearer {TOKEN}"}
    ) as http:
        transport = streamable_http_client(URL, http_client=http)
        async with Client(transport) as client:
            tools = await client.list_tools()
            names = sorted(t.name for t in tools.tools)
            print("    tools:", ", ".join(names))
            expected = {
                "screenshot", "click", "type", "hotkey",
                "browser_open", "stream_status",
                "stream_start", "stream_stop",
            }
            missing = expected - set(names)
            if missing:
                print("ERROR: missing tools:", sorted(missing), file=sys.stderr)
                return 1

            # meta needsApproval on privileged tools
            by_name = {t.name: t for t in tools.tools}
            for priv in ("stream_start", "stream_stop"):
                meta = getattr(by_name[priv], "meta", None) or {}
                print(f"    {priv} meta.needsApproval = {meta.get('needsApproval')}")

            st = await client.call_tool("stream_status", {})
            texts = [c.text for c in st.content if getattr(c, "type", None) == "text"]
            print("    stream_status:", texts[0] if texts else st)

            shot = await client.call_tool("screenshot", {})
            types = [getattr(c, "type", "?") for c in shot.content]
            print("    screenshot content types:", types)
            images = [c for c in shot.content if getattr(c, "type", None) == "image"]
            if not images:
                # May fail without display — still OK if structured error text
                print("    WARN: no image content (display down?)")
            else:
                data = images[0].data or ""
                print(f"    screenshot image b64 length: {len(data)}")

            start = await client.call_tool("stream_start", {"mode": "view"})
            start_texts = [c.text for c in start.content if getattr(c, "type", None) == "text"]
            print("    stream_start:", start_texts[0] if start_texts else start)
            if start_texts and "not_implemented" not in start_texts[0] and "Phase 3" not in start_texts[0]:
                # Accept either JSON error or message mentioning phase 3
                try:
                    payload = json.loads(start_texts[0])
                    if payload.get("error") != "not_implemented":
                        print("WARN: stream_start did not report not_implemented")
                except json.JSONDecodeError:
                    print("WARN: unexpected stream_start payload")

    print("MCP smoke OK")
    return 0

raise SystemExit(asyncio.run(main()))
PY

echo ""
echo "Smoke MCP finished."

#!/usr/bin/env bash
# Smoke-test on-demand stream: start → status active → viewer HTTP → stop → inactive.
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
TOKEN="${API_TOKEN:-}"
NOVNC_PORT="${NOVNC_PORT:-6099}"

if [[ -z "$TOKEN" ]]; then
  echo "ERROR: API_TOKEN not set" >&2
  exit 1
fi

auth=(-H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json")
VENV="$ROOT/agent/.venv"
PYTHON="${VENV}/bin/python"
[[ -x "$PYTHON" ]] || PYTHON=python3

echo "==> GET $BASE/health"
curl -sfS "$BASE/health" | "$PYTHON" -m json.tool | head -30

echo "==> GET $BASE/v1/stream/status (before)"
curl -sfS "${auth[@]}" "$BASE/v1/stream/status" | "$PYTHON" -m json.tool

echo "==> POST $BASE/v1/stream/start"
START_JSON=$(curl -sfS "${auth[@]}" -X POST "$BASE/v1/stream/start" -d '{"mode":"view"}')
echo "$START_JSON" | "$PYTHON" -m json.tool
SESSION_ID=$(echo "$START_JSON" | "$PYTHON" -c "import sys,json; print(json.load(sys.stdin)['session_id'])")
VIEWER=$(echo "$START_JSON" | "$PYTHON" -c "import sys,json; print(json.load(sys.stdin)['viewer_url'])")
LOCAL=$(echo "$START_JSON" | "$PYTHON" -c "import sys,json; d=json.load(sys.stdin); print(d.get('local_viewer_url') or d['viewer_url'])")
echo "    session_id=$SESSION_ID"
echo "    viewer_url=$VIEWER"

echo "==> GET status (expect active)"
STATUS=$(curl -sfS "${auth[@]}" "$BASE/v1/stream/status")
echo "$STATUS" | "$PYTHON" -m json.tool
echo "$STATUS" | "$PYTHON" -c "import sys,json; d=json.load(sys.stdin); assert d.get('active') is True, d; print('    active OK')"

echo "==> Fetch noVNC vnc.html on localhost:${NOVNC_PORT}"
CODE=$(curl -sS -o /tmp/friendly-vnc.html -w "%{http_code}" "http://127.0.0.1:${NOVNC_PORT}/vnc.html")
echo "    HTTP $CODE"
[[ "$CODE" == "200" ]] || { echo "ERROR: expected 200 from noVNC"; exit 1; }
grep -qi 'noVNC\|novnc\|rfb' /tmp/friendly-vnc.html && echo "    noVNC HTML looks sane" || echo "WARN: unexpected HTML"

echo "==> GET /v1/stream/viewer?token=… (JWT gate)"
VTOKEN=$(echo "$START_JSON" | "$PYTHON" -c "import sys,json; print(json.load(sys.stdin)['token'])")
curl -sfS "$BASE/v1/stream/viewer?token=${VTOKEN}" | "$PYTHON" -m json.tool

echo "==> Second start should conflict (409)"
CODE=$(curl -sS -o /tmp/friendly-stream-conflict.json -w "%{http_code}" \
  "${auth[@]}" -X POST "$BASE/v1/stream/start" -d '{"mode":"view"}')
echo "    HTTP $CODE"
[[ "$CODE" == "409" ]] || echo "WARN: expected 409 got $CODE"

echo "==> POST stop"
curl -sfS "${auth[@]}" -X POST "$BASE/v1/stream/stop" \
  -d "{\"session_id\":\"${SESSION_ID}\"}" | "$PYTHON" -m json.tool

echo "==> GET status (expect inactive)"
STATUS=$(curl -sfS "${auth[@]}" "$BASE/v1/stream/status")
echo "$STATUS" | "$PYTHON" -m json.tool
echo "$STATUS" | "$PYTHON" -c "import sys,json; d=json.load(sys.stdin); assert d.get('active') is False, d; print('    inactive OK')"

echo ""
echo "Stream smoke OK"

#!/usr/bin/env bash
# Hit health + authenticated Control API routes. Expects API already running.
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

if [[ -z "$TOKEN" ]]; then
  echo "ERROR: API_TOKEN not set (create $ROOT/.env from .env.example)" >&2
  exit 1
fi

auth=(-H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json")

echo "==> GET $BASE/health"
curl -sfS "$BASE/health" | python3 -m json.tool

echo "==> GET $BASE/v1/desktop/status (auth)"
curl -sfS "${auth[@]}" "$BASE/v1/desktop/status" | python3 -m json.tool

echo "==> GET $BASE/v1/stream/status (auth)"
curl -sfS "${auth[@]}" "$BASE/v1/stream/status" | python3 -m json.tool

echo "==> POST $BASE/v1/stream/start (expect 501 stub)"
CODE=$(curl -sS -o /tmp/friendly-stream-start.json -w "%{http_code}" \
  "${auth[@]}" -X POST "$BASE/v1/stream/start" -d '{"mode":"view"}')
echo "    HTTP $CODE"
python3 -m json.tool </tmp/friendly-stream-start.json || cat /tmp/friendly-stream-start.json
if [[ "$CODE" != "501" && "$CODE" != "200" ]]; then
  echo "WARN: unexpected stream/start status $CODE"
fi

# Screenshot — may 503 if no DISPLAY
echo "==> POST $BASE/v1/actions/screenshot"
CODE=$(curl -sS -o /tmp/friendly-screenshot.json -w "%{http_code}" \
  "${auth[@]}" -X POST "$BASE/v1/actions/screenshot" -d '{}')
echo "    HTTP $CODE"
if [[ "$CODE" == "200" ]]; then
  python3 - <<'PY'
import json, base64, sys
from pathlib import Path
data = json.load(open("/tmp/friendly-screenshot.json"))
b64 = data.get("image_b64", "")
raw = base64.b64decode(b64)
out = Path("/tmp/friendly-smoke-screenshot.png")
out.write_bytes(raw)
print(f"    wrote {out} ({len(raw)} bytes)")
if raw[:8] != b"\x89PNG\r\n\x1a\n":
    print("ERROR: not a PNG", file=sys.stderr)
    sys.exit(1)
PY
else
  python3 -m json.tool </tmp/friendly-screenshot.json || cat /tmp/friendly-screenshot.json
  echo "    (screenshot failed — start idle stack if you need display actions)"
fi

echo "==> POST unauthenticated /v1/actions/click (expect 401)"
CODE=$(curl -sS -o /dev/null -w "%{http_code}" -X POST "$BASE/v1/actions/click" \
  -H "Content-Type: application/json" -d '{"x":1,"y":1}')
echo "    HTTP $CODE"
[[ "$CODE" == "401" || "$CODE" == "403" ]] || echo "WARN: expected 401"

echo ""
echo "Smoke test finished."

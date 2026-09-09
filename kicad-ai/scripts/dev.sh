#!/usr/bin/env bash
# Start backend (uvicorn, autoreload) and frontend (vite) for local development.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

if [ ! -d "$ROOT/backend/.venv" ]; then
  python3 -m venv "$ROOT/backend/.venv"
  "$ROOT/backend/.venv/bin/pip" install -r "$ROOT/backend/requirements.txt"
fi
if [ ! -d "$ROOT/frontend/node_modules" ]; then
  (cd "$ROOT/frontend" && npm install)
fi
if [ ! -f "$ROOT/.env" ]; then
  cp "$ROOT/.env.example" "$ROOT/.env"
  echo "Created .env from .env.example - set LLM_API_KEY (or LLM_PROVIDER=mock for an offline demo)."
fi

(cd "$ROOT/backend" && exec .venv/bin/python -m uvicorn app.main:app --reload --host 0.0.0.0 --port 8000) &
(cd "$ROOT/frontend" && exec npm run dev) &
echo "Backend: http://127.0.0.1:8000/docs   Frontend: http://127.0.0.1:5173"
wait

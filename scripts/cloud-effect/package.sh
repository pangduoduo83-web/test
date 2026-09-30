#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "$0")/../.." && pwd)"
cd "$ROOT"

test -f pom.xml
test -f frontend/package.json
test -f hub/pom.xml

rm -f package.tgz
tar -czf package.tgz \
  --exclude=''./.git'' \
  --exclude=''./target'' \
  --exclude=''./frontend/node_modules'' \
  --exclude=''./frontend/dist'' \
  --exclude=''./kicad-ai/backend/data'' \
  --exclude=''./kicad-ai/backend/**/__pycache__'' \
  --exclude=''./kicad-ai/frontend/node_modules'' \
  --exclude=''./kicad-ai/frontend/dist'' \
  --exclude=''./tmp'' \
  --exclude=''./output'' \
  --exclude=''./uploads'' \
  --exclude=''./.env'' \
  --exclude=''*.db'' \
  --exclude=''*.log'' \
  --exclude=''*.pdf'' \
  src pom.xml Dockerfile .dockerignore .env.example \
  docker-compose.yml docker-compose.https.yml \
  frontend hub kicad-ai scripts/cloud-effect/deploy.sh

test -s package.tgz
sha256sum package.tgz


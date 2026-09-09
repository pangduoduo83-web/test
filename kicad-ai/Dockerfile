# syntax=docker/dockerfile:1
ARG KICAD_IMAGE_TAG=10.0.5

# ---- frontend build --------------------------------------------------------
FROM node:22-alpine AS web
ARG NPM_REGISTRY=https://registry.npmjs.org/
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json* ./
RUN npm config set registry "$NPM_REGISTRY" && npm install --no-audit --no-fund
COPY frontend/ .
RUN npm run build

# ---- backend ---------------------------------------------------------------
# Official KiCad CI image: kicad-cli plus the matching stock symbol/footprint
# libraries.  KiCad 10 opens projects from older releases; the reverse is not
# true, so the server runtime follows the newest project format users upload.
FROM kicad/kicad:${KICAD_IMAGE_TAG}
USER root
ARG PIP_INDEX_URL=https://pypi.org/simple
# e.g. mirrors.aliyun.com — used for the small Python runtime layer
ARG APT_MIRROR=
ENV PYTHONUNBUFFERED=1 PIP_NO_CACHE_DIR=1 PIP_INDEX_URL=$PIP_INDEX_URL \
    DATA_DIR=/data WORKSPACE_ROOT=/data/workspaces \
    KICAD_VERSION=10.0 KICAD_APP_PATH=/usr/share/kicad \
    HOME=/root VIRTUAL_ENV=/opt/venv PATH="/opt/venv/bin:$PATH"

RUN set -eux; \
    if [ -n "$APT_MIRROR" ]; then \
        grep -rl 'deb.debian.org' /etc/apt/sources.list /etc/apt/sources.list.d 2>/dev/null \
        | xargs -r sed -i "s|deb.debian.org|$APT_MIRROR|g"; \
    fi; \
    apt-get update; \
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        python3 python3-pip python3-venv ca-certificates; \
    rm -rf /var/lib/apt/lists/*; \
    python3 -m venv "$VIRTUAL_ENV"; \
    kicad-cli version; \
    kicad-cli version | grep -q '^10\.'; \
    test -f /usr/share/kicad/symbols/Device.kicad_sym; \
    test -d /usr/share/kicad/footprints/Resistor_SMD.pretty

WORKDIR /app
COPY backend/requirements.txt backend/requirements-postgres.txt ./
RUN pip install -r requirements.txt -r requirements-postgres.txt

# Library tables (what the KiCad GUI would write on first start) + the kcaa search index,
# baked into the image so symbol/footprint search works immediately. Only this one file is
# copied here so backend code changes do not invalidate the slow index layer.
COPY backend/app/kicad/libraries.py /tmp/kicad_libraries.py
RUN python /tmp/kicad_libraries.py --ensure-tables --sync --require-ready && rm /tmp/kicad_libraries.py

COPY backend/ /app/backend/
COPY --from=web /web/dist /app/frontend/dist
WORKDIR /app/backend
EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:8000/api/health', timeout=4).status == 200 else 1)"
# The official KiCad image may define its own CLI entrypoint; the application
# runtime must always start Uvicorn instead.
ENTRYPOINT []
CMD ["python", "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--proxy-headers", "--forwarded-allow-ips", "*"]

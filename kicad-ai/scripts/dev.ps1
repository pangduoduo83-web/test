# Start backend (uvicorn, autoreload) and frontend (vite) for local development.
$root = Split-Path -Parent $PSScriptRoot

if (-not (Test-Path "$root\backend\.venv")) {
    Write-Host "Creating Python venv..." -ForegroundColor Cyan
    python -m venv "$root\backend\.venv"
    & "$root\backend\.venv\Scripts\python.exe" -m pip install -r "$root\backend\requirements.txt"
}
if (-not (Test-Path "$root\frontend\node_modules")) {
    Write-Host "Installing frontend dependencies..." -ForegroundColor Cyan
    Push-Location "$root\frontend"; npm install; Pop-Location
}
if (-not (Test-Path "$root\.env")) {
    Copy-Item "$root\.env.example" "$root\.env"
    Write-Host "Created .env from .env.example - set LLM_API_KEY (or LLM_PROVIDER=mock for an offline demo)." -ForegroundColor Yellow
}

Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$root\backend'; .\.venv\Scripts\python.exe -m uvicorn app.main:app --reload --host 0.0.0.0 --port 8000"
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$root\frontend'; npm run dev"
Write-Host "Backend: http://127.0.0.1:8000/docs   Frontend: http://127.0.0.1:5173" -ForegroundColor Green

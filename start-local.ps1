$ErrorActionPreference = "Stop"

$root = $PSScriptRoot
$envPath = Join-Path $root ".env"
$examplePath = Join-Path $root ".env.example"

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker CLI is required. Install Docker Desktop and start its Linux container engine."
}

try {
    & docker info --format '{{.ServerVersion}}' *> $null
    if ($LASTEXITCODE -ne 0) { throw "Docker engine is not available." }
}
catch {
    throw "Docker Desktop must be running before starting the local stack."
}

if (-not (Test-Path $envPath)) {
    Copy-Item $examplePath $envPath
}

$envContent = [System.IO.File]::ReadAllText($envPath)
$random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    if ($envContent -match '(?m)^POSTGRES_PASSWORD=replace-with-') {
        $bytes = New-Object byte[] 32
        $random.GetBytes($bytes)
        $password = [Convert]::ToBase64String($bytes)
        $envContent = [regex]::Replace($envContent, '(?m)^POSTGRES_PASSWORD=.*$', "POSTGRES_PASSWORD=$password")
    }
    if ($envContent -match '(?m)^JWT_SECRET=replace-with-') {
        $bytes = New-Object byte[] 48
        $random.GetBytes($bytes)
        $secret = [Convert]::ToBase64String($bytes)
        $envContent = [regex]::Replace($envContent, '(?m)^JWT_SECRET=.*$', "JWT_SECRET=$secret")
    }
    if ($envContent -match '(?m)^CIVIC_LOCAL_ADMIN_PASSWORD=\s*$') {
        $bytes = New-Object byte[] 32
        $random.GetBytes($bytes)
        $adminPassword = [Convert]::ToBase64String($bytes)
        $envContent = [regex]::Replace($envContent, '(?m)^CIVIC_LOCAL_ADMIN_PASSWORD=.*$', "CIVIC_LOCAL_ADMIN_PASSWORD=$adminPassword")
    }
    $envContent = [regex]::Replace($envContent, '(?m)^CIVIC_LOCAL_ADMIN_ENABLED=.*$', 'CIVIC_LOCAL_ADMIN_ENABLED=true')
}
finally {
    $random.Dispose()
}

[System.IO.File]::WriteAllText($envPath, $envContent, [System.Text.Encoding]::ASCII)
Write-Host "Starting Civic Air frontend, backend, and PostgreSQL. Local secrets are stored in the ignored .env file."

Push-Location $root
try {
    & docker compose --env-file $envPath up --build --detach
    if ($LASTEXITCODE -ne 0) { throw "Docker Compose could not start the stack. Inspect the error above." }
    & docker compose --env-file $envPath ps
}
finally {
    Pop-Location
}

$frontendPort = 3000
if ($envContent -match '(?m)^FRONTEND_PORT=(\d+)$') {
    $frontendPort = [int]$Matches[1]
}
Write-Host "Local admin: admin@civic-air.local (role ADMIN); password is in the ignored .env file as CIVIC_LOCAL_ADMIN_PASSWORD."
Write-Host "Open http://localhost:$frontendPort to sign in and run the upload-to-brief workflow."

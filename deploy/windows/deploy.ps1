param([string]$InstallDir = "C:\TradN", [string]$EnvFile = ".env")
$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
if (-not (Test-Path -LiteralPath (Join-Path $root $EnvFile))) { throw "Missing environment file: $EnvFile" }
foreach ($command in @("java","mvn","node","npm","nginx")) { if (-not (Get-Command $command -ErrorAction SilentlyContinue)) { throw "Required command not found: $command" } }
$javaVersion = & java -version 2>&1 | Select-Object -First 1
Write-Output "Java: $javaVersion"
Push-Location (Join-Path $root "tradn-backend"); try { & mvn clean package; if ($LASTEXITCODE -ne 0) { throw "Backend build failed" } } finally { Pop-Location }
Push-Location (Join-Path $root "tradn-web"); try { & npm ci; & npm run build; if ($LASTEXITCODE -ne 0) { throw "Frontend build failed" } } finally { Pop-Location }
New-Item -ItemType Directory -Path $InstallDir -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $InstallDir "web") -Force | Out-Null
Copy-Item (Join-Path $root "tradn-backend\target\tradn-backend-*.jar") (Join-Path $InstallDir "tradn.jar") -Force
Copy-Item (Join-Path $root "tradn-web\dist\*") (Join-Path $InstallDir "web") -Recurse -Force
Copy-Item (Join-Path $root $EnvFile) (Join-Path $InstallDir ".env") -Force
$envLines = Get-Content -LiteralPath (Join-Path $root $EnvFile) -Encoding UTF8
foreach ($line in $envLines) { if ($line -match '^\s*([^#][A-Za-z0-9_]*)=(.*)$') { [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process') } }
$existing = Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -like "*${InstallDir}\tradn.jar*" }
if ($existing) { $existing | ForEach-Object { Stop-Process -Id $_.ProcessId -Force } }
Start-Process -FilePath "java" -ArgumentList @("-jar",(Join-Path $InstallDir "tradn.jar")) -WorkingDirectory $InstallDir -WindowStyle Hidden
Write-Output "TradN backend deployed to $InstallDir. Configure nginx to serve $InstallDir\web and proxy /api to 127.0.0.1:8080."

param(
    [ValidatePattern('^[A-Za-z0-9_-]+$')]
    [string]$Instance = 'local'
)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$runtimeDir = Join-Path $projectRoot "deploy\runtime\$Instance"

foreach ($service in @('frontend', 'backend')) {
    $statePath = Join-Path $runtimeDir "$service.json"
    if (-not (Test-Path -LiteralPath $statePath)) { continue }
    $state = Get-Content -LiteralPath $statePath -Raw -Encoding UTF8 | ConvertFrom-Json
    $expectedEntry = if ($service -eq 'backend') {
        Join-Path $projectRoot 'tradn-backend\target\tradn-backend-1.0.0-SNAPSHOT.jar'
    } else {
        Join-Path $projectRoot 'tradn-web\node_modules\vite\bin\vite.js'
    }
    if ($state.entry -ne $expectedEntry) { throw 'Unexpected process entry in runtime state.' }
    $process = Get-Process -Id $state.id -ErrorAction SilentlyContinue
    if (-not $process) { continue }
    $details = Get-CimInstance Win32_Process -Filter "ProcessId=$($process.Id)"
    $sameStart = $process.StartTime.ToUniversalTime().ToString('o') -eq $state.startedAt
    $sameEntry = $details.CommandLine -and $details.CommandLine.Contains($expectedEntry)
    if (-not ($sameStart -and $sameEntry)) {
        throw "Process identity changed for $service. No process was stopped."
    }
    # 仅停止本启动脚本创建且身份匹配的前后端进程，保留共享容器及 IDEA 进程。
    Stop-Process -Id $process.Id -ErrorAction Stop
    Write-Output "Stopped $service ($($process.Id))."
}
Write-Output 'Script-owned services stopped. Docker containers were left running.'

[CmdletBinding()]
param(
    [switch]$CheckOnly,
    [switch]$SkipBuild,
    [switch]$NoBrowser,
    [string]$JavaHome,
    [int]$WebPort = 3000,
    [int]$BackendPort = 0,
    [ValidatePattern('^[A-Za-z0-9_-]+$')]
    [string]$Instance = 'local',
    [string[]]$Containers = @('mysql8.0', 'redis01', 'my_minio')
)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$backendDir = Join-Path $projectRoot 'tradn-backend'
$webDir = Join-Path $projectRoot 'tradn-web'
$runtimeDir = Join-Path $projectRoot "deploy\runtime\$Instance"
$envPath = Join-Path $projectRoot '.env'

# 只将本地配置作为进程环境读取，不执行配置内容，也不打印密码或令牌。
if (-not (Test-Path -LiteralPath $envPath)) { throw 'Missing .env in project root. Configure it before starting.' }
foreach ($line in Get-Content -LiteralPath $envPath -Encoding UTF8) {
    if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
        $name = $matches[1]
        $value = $matches[2].Trim()
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        [Environment]::SetEnvironmentVariable($name, $value, 'Process')
    }
}

function Find-Tool([string]$name) {
    $command = Get-Command $name -ErrorAction SilentlyContinue
    if (-not $command) { throw "Required tool not found: $name" }
    return $command.Source
}

# 优先使用指定 JDK，其次 JAVA_HOME，最后从 javac 定位，避开 PATH 中的独立 JRE。
if (-not $JavaHome) { $JavaHome = $env:JAVA_HOME }
if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\javac.exe'))) {
    $JavaHome = Split-Path -Parent (Split-Path -Parent (Find-Tool 'javac.exe'))
}
if (-not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\javac.exe'))) { throw 'A JDK with javac is required.' }
$env:JAVA_HOME = $JavaHome
$env:Path = "$JavaHome\bin;$env:Path"
$java = Join-Path $JavaHome 'bin\java.exe'
$maven = Find-Tool 'mvn.cmd'
$node = Find-Tool 'node.exe'
$npm = Find-Tool 'npm.cmd'
$nodeVersion = [version]((& $node --version).TrimStart('v'))
if ($nodeVersion -lt [version]'22.12.0') { throw 'Node.js 22.12.0 or later is required.' }
$apiPort = if ($BackendPort) { $BackendPort } elseif ($env:APP_PORT) { [int]$env:APP_PORT } else { 8080 }
$env:APP_PORT = [string]$apiPort
if ($apiPort -lt 1 -or $apiPort -gt 65535 -or $WebPort -lt 1 -or $WebPort -gt 65535) { throw 'Invalid port.' }
$apiUrl = "http://127.0.0.1:$apiPort"
$webUrl = "http://localhost:$WebPort"
$jar = Join-Path $backendDir 'target\tradn-backend-1.0.0-SNAPSHOT.jar'
$vite = Join-Path $webDir 'node_modules\vite\bin\vite.js'

function Test-Endpoint([string]$url, [string]$marker) {
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 3
        # Windows PowerShell 将部分 Actuator 媒体类型当作二进制，先解码再检查健康状态。
        $content = $response.Content
        if ($content -is [byte[]]) { $content = [Text.Encoding]::UTF8.GetString($content) }
        return $response.StatusCode -eq 200 -and $content -match $marker
    } catch {
        Write-Verbose "Endpoint check failed: $url ($($_.Exception.Message))"
        return $false
    }
}

function Test-Port([string]$server, [int]$port) {
    $client = New-Object Net.Sockets.TcpClient
    try {
        $task = $client.ConnectAsync($server, $port)
        return $task.Wait(1000) -and $client.Connected
    } catch { return $false } finally { $client.Dispose() }
}

function Wait-Ready([string]$url, [string]$marker, [int]$timeout = 120) {
    $deadline = (Get-Date).AddSeconds($timeout)
    while ((Get-Date) -lt $deadline) {
        if (Test-Endpoint $url $marker) { return }
        Start-Sleep -Seconds 2
    }
    throw "Service not ready: $url. See logs in $runtimeDir"
}

$apiRunning = Test-Endpoint "$apiUrl/api/actuator/health" '"status"\s*:\s*"UP"'
$webRunning = Test-Endpoint "$webUrl/@vite/client" 'vite'
Write-Output "Project: $projectRoot"
Write-Output "JDK: $JavaHome; Node: $nodeVersion"
Write-Output "Backend ready: $apiRunning; frontend ready: $webRunning"
if ($CheckOnly) {
    Write-Output "Configured containers: $($Containers -join ', ')"
    Write-Output "Check complete. No services started or stopped."
    exit 0
}

# 复用正在运行的服务。未知端口占用应报错，不误杀 IDEA 或其他项目的进程。
if (-not $apiRunning -and (Test-Port '127.0.0.1' $apiPort)) { throw "Port $apiPort is occupied but backend is not healthy." }
if (-not $webRunning -and (Test-Port '127.0.0.1' $WebPort)) { throw "Port $WebPort is occupied by another service." }
New-Item -ItemType Directory -Path $runtimeDir -Force | Out-Null

if ($Containers.Count -gt 0) {
    $docker = Find-Tool 'docker.exe'
    & $docker info --format '{{.ServerVersion}}' *> $null
    if ($LASTEXITCODE -ne 0) {
        $desktop = Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'
        if (-not (Test-Path -LiteralPath $desktop)) { throw 'Start your Docker engine, then retry.' }
        Write-Output 'Starting Docker Desktop...'
        Start-Process -FilePath $desktop -WindowStyle Hidden | Out-Null
        $deadline = (Get-Date).AddSeconds(120)
        do {
            Start-Sleep -Seconds 3
            & $docker info --format '{{.ServerVersion}}' *> $null
            $dockerReady = $LASTEXITCODE -eq 0
        } while (-not $dockerReady -and (Get-Date) -lt $deadline)
        if (-not $dockerReady) { throw 'Docker did not become ready within 120 seconds.' }
    }
    foreach ($container in $Containers) {
        # 仅启动明确列出的已有容器，不新建数据库，也不修改挂载和端口映射。
        $running = & $docker inspect --format '{{.State.Running}}' $container 2>$null
        if ($LASTEXITCODE -ne 0) { throw "Container not found: $container. Configure -Containers for this machine." }
        if ($running -ne 'true') {
            & $docker start $container | Out-Null
            if ($LASTEXITCODE -ne 0) { throw "Could not start container: $container" }
        }
    }
}

$dbHost = if ($env:MYSQL_HOST) { $env:MYSQL_HOST } else { '127.0.0.1' }
$dbPort = if ($env:MYSQL_PORT) { [int]$env:MYSQL_PORT } else { 3306 }
$redisHost = if ($env:REDIS_HOST) { $env:REDIS_HOST } else { '127.0.0.1' }
$redisPort = if ($env:REDIS_PORT) { [int]$env:REDIS_PORT } else { 6379 }
foreach ($dependency in @(@($dbHost, $dbPort), @($redisHost, $redisPort))) {
    $deadline = (Get-Date).AddSeconds(90)
    while (-not (Test-Port $dependency[0] $dependency[1])) {
        if ((Get-Date) -gt $deadline) { throw "Dependency not ready: $($dependency[0]):$($dependency[1])" }
        Start-Sleep -Seconds 2
    }
}
$minioUrl = if ($env:MINIO_ENDPOINT) { $env:MINIO_ENDPOINT.TrimEnd('/') } else { 'http://127.0.0.1:9000' }
Wait-Ready "$minioUrl/minio/health/live" '' 90

function Record-Process($process, [string]$service, [string]$entry) {
    # 保存启动时间与入口文件用于停止时核验，避免 PID 被复用后结束其他进程。
    $process.Refresh()
    @{
        id = $process.Id
        startedAt = $process.StartTime.ToUniversalTime().ToString('o')
        entry = $entry
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runtimeDir "$service.json") -Encoding UTF8
}

if (-not $apiRunning) {
    if (-not $SkipBuild -or -not (Test-Path -LiteralPath $jar)) {
        Write-Output 'Building backend...'
        Push-Location $backendDir
        try {
            & $maven package -DskipTests *> (Join-Path $runtimeDir 'backend-build.log')
            if ($LASTEXITCODE -ne 0) { throw "Backend build failed. See $runtimeDir\backend-build.log" }
        } finally { Pop-Location }
    }
    $backendProcess = Start-Process -FilePath $java -ArgumentList @('-jar', ('"' + $jar + '"')) -WorkingDirectory $backendDir -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtimeDir 'backend.log') -RedirectStandardError (Join-Path $runtimeDir 'backend-error.log')
    Record-Process $backendProcess 'backend' $jar
    Wait-Ready "$apiUrl/api/actuator/health" '"status"\s*:\s*"UP"'
} else { Write-Output 'Using existing backend. Restart it separately to apply backend code changes.' }

if (-not $webRunning) {
    if (-not (Test-Path -LiteralPath $vite)) {
        Write-Output 'Installing frontend dependencies...'
        Push-Location $webDir
        try {
            & $npm ci *> (Join-Path $runtimeDir 'frontend-install.log')
            if ($LASTEXITCODE -ne 0) { throw "npm ci failed. See $runtimeDir\frontend-install.log" }
        } finally { Pop-Location }
    }
    $env:VITE_API_PROXY_TARGET = $apiUrl
    $frontendProcess = Start-Process -FilePath $node -ArgumentList @(('"' + $vite + '"'), '--host', '127.0.0.1', '--port', $WebPort, '--strictPort') -WorkingDirectory $webDir -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtimeDir 'frontend.log') -RedirectStandardError (Join-Path $runtimeDir 'frontend-error.log')
    Record-Process $frontendProcess 'frontend' $vite
    Wait-Ready "$webUrl/@vite/client" 'vite'
}
Write-Output "TradN is ready: $webUrl"
Write-Output "Logs: $runtimeDir"
if (-not $NoBrowser) { Start-Process $webUrl }

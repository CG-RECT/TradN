[CmdletBinding()]
param(
    [switch]$NoPull
)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$envPath = Join-Path $projectRoot '.env'
$composePath = Join-Path $projectRoot 'deploy\docker\compose.local-infra.yml'
$containerNames = @('mysql8.0', 'redis01', 'my_minio')

# 只读取简单的 KEY=VALUE 配置，不执行配置内容，也不打印任何密码。
if (-not (Test-Path -LiteralPath $envPath)) {
    throw '项目根目录缺少 .env。请先复制 .env.example 为 .env，并替换所有 change_ 开头的示例值。'
}
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

$requiredVariables = @(
    'MYSQL_DATABASE', 'MYSQL_USER', 'MYSQL_PASSWORD', 'MYSQL_ROOT_PASSWORD',
    'REDIS_PASSWORD', 'MINIO_ACCESS_KEY', 'MINIO_SECRET_KEY', 'MINIO_BUCKET'
)
foreach ($name in $requiredVariables) {
    $value = [Environment]::GetEnvironmentVariable($name, 'Process')
    if ([string]::IsNullOrWhiteSpace($value) -or $value.StartsWith('change_')) {
        throw ".env 中的 $name 尚未正确配置。"
    }
}
if ($env:MYSQL_USER -ieq 'root') {
    throw '新电脑初始化请将 MYSQL_USER 设置为非 root 账号（建议 tradn）；root 密码单独使用 MYSQL_ROOT_PASSWORD。'
}
if ($env:MINIO_SECRET_KEY.Length -lt 16) {
    throw 'MINIO_SECRET_KEY 至少需要 16 个字符。'
}

$dockerCommand = Get-Command 'docker.exe' -ErrorAction SilentlyContinue
if (-not $dockerCommand) { throw '未找到 Docker 命令，请先安装 Docker Desktop。' }
$docker = $dockerCommand.Source

# Docker 未就绪时尝试启动 Docker Desktop，并在两分钟内等待引擎可用。
& $docker info --format '{{.ServerVersion}}' *> $null
if ($LASTEXITCODE -ne 0) {
    $desktop = Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'
    if (-not (Test-Path -LiteralPath $desktop)) { throw '请先启动 Docker Desktop，再重新执行安装。' }
    Write-Output '正在启动 Docker Desktop...'
    Start-Process -FilePath $desktop -WindowStyle Hidden | Out-Null
    $deadline = (Get-Date).AddSeconds(120)
    do {
        Start-Sleep -Seconds 3
        & $docker info --format '{{.ServerVersion}}' *> $null
        $dockerReady = $LASTEXITCODE -eq 0
    } while (-not $dockerReady -and (Get-Date) -lt $deadline)
    if (-not $dockerReady) { throw 'Docker Desktop 在 120 秒内未就绪。' }
}

# 允许重复执行并修复上次未完成的安装，但不接管其他项目创建的同名容器。
$foreignContainers = @()
foreach ($containerName in $containerNames) {
    $composeProject = & $docker inspect --format '{{index .Config.Labels "com.docker.compose.project"}}' $containerName 2>$null
    if ($LASTEXITCODE -eq 0 -and $composeProject -ne 'tradn-local-infra') {
        $foreignContainers += $containerName
    }
}
if ($foreignContainers.Count -gt 0) {
    throw "以下同名容器不是本安装脚本创建的，脚本不会覆盖：$($foreignContainers -join ', ')。若它们就是 TradN 的容器，请直接运行 start-local.cmd。"
}

$composeArguments = @(
    'compose', '--project-name', 'tradn-local-infra', '--env-file', $envPath,
    '-f', $composePath, 'up', '-d', '--wait', '--wait-timeout', '180'
)
if (-not $NoPull) { $composeArguments += @('--pull', 'always') }

Write-Output '正在下载并创建 MySQL 8、Redis 和 MinIO，首次执行可能需要几分钟...'
& $docker @composeArguments
if ($LASTEXITCODE -ne 0) { throw '本地中间件安装失败，请根据上方 Docker 输出排查。' }

Write-Output ''
Write-Output '本地中间件已经安装并启动：'
Write-Output '  MySQL 8: 127.0.0.1:3306（容器 mysql8.0）'
Write-Output '  Redis:    127.0.0.1:6379（容器 redis01）'
Write-Output '  MinIO:    http://127.0.0.1:9000（控制台 http://127.0.0.1:9001）'
Write-Output '数据保存在 tradn-local-* 命名卷中。现在可以运行 start-local.cmd。'

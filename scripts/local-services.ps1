#Requires -Version 7.0
[CmdletBinding()]
param(
    [ValidateSet('start', 'stop', 'status')][string]$Action = 'status',
    [ValidateSet('all', 'mysql', 'redis')][string]$Target = 'all',
    [string]$ConfigPath = (Join-Path (Split-Path -Parent $PSScriptRoot) 'runtime/local-env.json')
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (!(Test-Path -LiteralPath $ConfigPath)) {
    throw 'Missing runtime/local-env.json. See docs/development/独立数据库与账号联调.md.'
}
$config = Get-Content -Raw -LiteralPath $ConfigPath | ConvertFrom-Json

function ConvertTo-CygwinPath([string]$Path) {
    $absolute = [IO.Path]::GetFullPath($Path)
    if ($absolute -notmatch '^([A-Za-z]):\\(.*)$') { throw 'Redis requires an absolute local drive path.' }
    return '/cygdrive/' + $Matches[1].ToLowerInvariant() + '/' + $Matches[2].Replace('\', '/')
}

function Get-ServiceListener([int]$Port, [string]$Executable) {
    $listeners = @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue)
    foreach ($listener in $listeners) {
        $process = Get-Process -Id $listener.OwningProcess -ErrorAction Stop
        if ($process.Path -ne $Executable -or $listener.LocalAddress -ne '127.0.0.1') {
            throw "Port $Port is occupied by an unexpected process or address; no action taken."
        }
    }
    return $listeners.Count -gt 0
}

function Test-ConnectionReady([string]$Service) {
    if ($Service -eq 'mysql') {
        $result = & (Join-Path $config.mysqlRoot 'bin/mysql.exe') "--defaults-extra-file=$($config.mysqlClientConfig)" --batch --skip-column-names '--execute=SELECT 1;' 2>$null
        return $LASTEXITCODE -eq 0 -and $result -eq '1'
    }
    $previousAuth = $env:REDISCLI_AUTH
    try {
        $env:REDISCLI_AUTH = $config.environment.REDIS_PASSWORD
        $result = & (Join-Path $config.redisRoot 'redis-cli.exe') -h 127.0.0.1 -p 16379 ping 2>$null
        return $LASTEXITCODE -eq 0 -and $result -eq 'PONG'
    }
    finally { $env:REDISCLI_AUTH = $previousAuth }
}

$targets = if ($Target -eq 'all') { @('mysql', 'redis') } else { @($Target) }
foreach ($service in $targets) {
    $port = if ($service -eq 'mysql') { 13306 } else { 16379 }
    $executable = if ($service -eq 'mysql') { Join-Path $config.mysqlRoot 'bin/mysqld.exe' } else { Join-Path $config.redisRoot 'redis-server.exe' }
    $listening = Get-ServiceListener $port $executable
    if ($Action -eq 'status') {
        $ready = $listening -and (Test-ConnectionReady $service)
        Write-Host "$service 127.0.0.1:$port ready=$ready"
        continue
    }
    if ($Action -eq 'start') {
        if (!$listening) {
            if ($service -eq 'mysql') {
                Start-Process -FilePath $executable -ArgumentList @("--defaults-file=`"$($config.mysqlConfig)`"") -WindowStyle Hidden | Out-Null
            }
            else {
                $redisConfigPath = ConvertTo-CygwinPath $config.redisConfig
                Start-Process -FilePath $executable -ArgumentList @("`"$redisConfigPath`"") -WorkingDirectory (Join-Path $config.serviceRoot 'data/redis') -WindowStyle Hidden -RedirectStandardOutput (Join-Path $config.serviceRoot 'logs/redis-stdout.log') -RedirectStandardError (Join-Path $config.serviceRoot 'logs/redis-stderr.log') | Out-Null
            }
        }
        $ready = $false
        for ($attempt = 0; $attempt -lt 30; $attempt++) {
            if (Test-ConnectionReady $service) { $ready = $true; break }
            Start-Sleep -Seconds 1
        }
        if (!$ready) { throw "$service did not become ready; inspect dedicated logs." }
        Write-Host "$service ready at 127.0.0.1:$port"
        continue
    }
    if (!$listening) { Write-Host "$service already stopped"; continue }
    if ($service -eq 'mysql') {
        & (Join-Path $config.mysqlRoot 'bin/mysqladmin.exe') "--defaults-extra-file=$($config.mysqlClientConfig)" shutdown
        if ($LASTEXITCODE -ne 0) { throw 'MySQL graceful shutdown failed.' }
    }
    else {
        $previousAuth = $env:REDISCLI_AUTH
        try {
            $env:REDISCLI_AUTH = $config.environment.REDIS_PASSWORD
            & (Join-Path $config.redisRoot 'redis-cli.exe') -h 127.0.0.1 -p 16379 shutdown save
            if ($LASTEXITCODE -ne 0) { throw 'Redis graceful shutdown failed.' }
        }
        finally { $env:REDISCLI_AUTH = $previousAuth }
    }
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        if (!(Get-ServiceListener $port $executable)) { break }
        Start-Sleep -Seconds 1
    }
    if (Get-ServiceListener $port $executable) { throw "$service is still listening after shutdown." }
    Write-Host "$service stopped; data retained"
}

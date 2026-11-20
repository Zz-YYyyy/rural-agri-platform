#Requires -Version 7.0
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('backend', 'admin', 'h5', 'weixin')]
    [string]$Target,
    [switch]$UseLocalServices
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$previousEnvironment = @{}
$locationPushed = $false

try {
    if ($UseLocalServices) {
        if ($Target -ne 'backend') { throw '-UseLocalServices is only supported with -Target backend.' }
        $localConfigPath = Join-Path $projectRoot 'runtime/local-env.json'
        if (!(Test-Path -LiteralPath $localConfigPath)) { throw 'Missing local configuration. See docs/development/独立数据库与账号联调.md.' }
        $localConfig = Get-Content -Raw -LiteralPath $localConfigPath | ConvertFrom-Json
        foreach ($entry in $localConfig.environment.PSObject.Properties) {
            $previousEnvironment[$entry.Name] = [Environment]::GetEnvironmentVariable($entry.Name, 'Process')
            [Environment]::SetEnvironmentVariable($entry.Name, [string]$entry.Value, 'Process')
        }
    }

    switch ($Target) {
        'backend' {
            $projectDirectory = Join-Path $projectRoot 'backend'
            $executable = 'mvn.cmd'
            $commandArguments = @('-B', '-ntp', 'spring-boot:run')
        }
        'admin' {
            $projectDirectory = Join-Path $projectRoot 'frontend/admin'
            $executable = 'npm.cmd'
            $commandArguments = @('run', 'dev')
        }
        'h5' {
            $projectDirectory = Join-Path $projectRoot 'frontend/miniapp'
            $executable = 'npm.cmd'
            $commandArguments = @('run', 'dev:h5')
        }
        'weixin' {
            $projectDirectory = Join-Path $projectRoot 'frontend/miniapp'
            $executable = 'npm.cmd'
            $commandArguments = @('run', 'dev:mp-weixin')
        }
    }

    Get-Command $executable -ErrorAction Stop | Out-Null
    Push-Location -LiteralPath $projectDirectory
    $locationPushed = $true
    Write-Host "Starting $Target in $projectDirectory. Press Ctrl+C to stop."
    & $executable @commandArguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Target exited with code $LASTEXITCODE."
    }
}
finally {
    if ($locationPushed) { Pop-Location }
    foreach ($entry in $previousEnvironment.GetEnumerator()) {
        [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process')
    }
}

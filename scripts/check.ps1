#Requires -Version 7.0
[CmdletBinding()]
param(
    [ValidateSet('all', 'docs', 'backend', 'admin', 'miniapp')]
    [string]$Scope = 'all'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot

function Invoke-ProjectCheck {
    param([string]$Directory, [string]$Executable, [string[]]$CommandArguments)
    Get-Command $Executable -ErrorAction Stop | Out-Null
    Push-Location -LiteralPath (Join-Path $projectRoot $Directory)
    try {
        Write-Host "Checking $Directory : $Executable $($CommandArguments -join ' ')"
        & $Executable @CommandArguments
        if ($LASTEXITCODE -ne 0) {
            throw "Check failed in $Directory with exit code $LASTEXITCODE."
        }
    }
    finally {
        Pop-Location
    }
}

$previousPythonEncoding = $env:PYTHONIOENCODING
$previousPythonBytecode = $env:PYTHONDONTWRITEBYTECODE
try {
    $env:PYTHONIOENCODING = 'utf-8'
    $env:PYTHONDONTWRITEBYTECODE = '1'
    if ($Scope -in @('all', 'docs')) {
        Invoke-ProjectCheck '.' 'python' @('scripts/build_api_contract.py')
        Invoke-ProjectCheck '.' 'python' @('scripts/check_database_design.py')
    }
    if ($Scope -in @('all', 'backend')) {
        Invoke-ProjectCheck 'backend' 'mvn.cmd' @('-B', '-ntp', 'verify')
    }
    if ($Scope -in @('all', 'admin')) {
        # The admin build script already includes its typecheck.
        Invoke-ProjectCheck 'frontend/admin' 'npm.cmd' @('run', 'build')
    }
    if ($Scope -in @('all', 'miniapp')) {
        Invoke-ProjectCheck 'frontend/miniapp' 'npm.cmd' @('run', 'typecheck')
        Invoke-ProjectCheck 'frontend/miniapp' 'npm.cmd' @('run', 'build:h5')
        Invoke-ProjectCheck 'frontend/miniapp' 'npm.cmd' @('run', 'build:mp-weixin')
    }
    Write-Host 'Requested checks passed. Database, WeChat and business-flow validation remain separate.'
}
finally {
    $env:PYTHONIOENCODING = $previousPythonEncoding
    $env:PYTHONDONTWRITEBYTECODE = $previousPythonBytecode
}

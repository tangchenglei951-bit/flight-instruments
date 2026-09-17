# 自动递增版本号脚本
# 用法：在 android-native 目录执行  ./bump-version.ps1
# 规则：appVersionCode 每次 +1；appVersionName 最后一位 +1

$path = Join-Path $PSScriptRoot "gradle.properties"
if (-not (Test-Path $path)) {
    Write-Error "gradle.properties not found"
    exit 1
}

$content = Get-Content -Raw $path
$codeMatch = [regex]::Match($content, 'appVersionCode=(\d+)')
$nameMatch = [regex]::Match($content, 'appVersionName=([0-9.]+)')

if (-not $codeMatch.Success -or -not $nameMatch.Success) {
    Write-Error "version fields not found in gradle.properties"
    exit 1
}

$code = [int]$codeMatch.Groups[1].Value + 1
$parts = $nameMatch.Groups[1].Value.Split('.')
$parts[$parts.Length - 1] = [string]([int]$parts[$parts.Length - 1] + 1)
$newName = $parts -join '.'

$content = [regex]::Replace($content, 'appVersionCode=\d+', "appVersionCode=$code")
$content = [regex]::Replace($content, 'appVersionName=[0-9.]+', "appVersionName=$newName")

[System.IO.File]::WriteAllText($path, $content, (New-Object System.Text.UTF8Encoding($false)))
Write-Output "versionCode=$code versionName=$newName"

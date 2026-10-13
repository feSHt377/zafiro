# Dot-source this file before invoking the Gradle wrapper on Windows.
$zafiroRoot = Split-Path $PSScriptRoot -Parent
$zafiroPropertiesPath = Join-Path $zafiroRoot 'local.properties'
if (-not (Test-Path -LiteralPath $zafiroPropertiesPath)) {
    throw 'Create local.properties using local.properties.example first.'
}
$zafiroProperties = Get-Content -LiteralPath $zafiroPropertiesPath -Raw | ConvertFrom-StringData
foreach ($zafiroKey in @('java.home', 'sdk.dir', 'python.executable')) {
    if (-not $zafiroProperties[$zafiroKey] -or -not (Test-Path -LiteralPath $zafiroProperties[$zafiroKey])) {
        throw "Set $zafiroKey to an existing path in local.properties (use forward slashes)."
    }
}
$env:JAVA_HOME = $zafiroProperties['java.home']
$env:ANDROID_HOME = $zafiroProperties['sdk.dir']
$env:GRADLE_USER_HOME = Join-Path $zafiroRoot '.tools/gradle'
$env:PYTHONUTF8 = '1'
$zafiroPythonDirectory = Split-Path $zafiroProperties['python.executable'] -Parent
$env:Path = "$env:JAVA_HOME/bin;$env:ANDROID_HOME/platform-tools;$zafiroPythonDirectory;$zafiroPythonDirectory/Scripts;$env:Path"
Write-Host 'Zafiro development environment loaded for this PowerShell session.'

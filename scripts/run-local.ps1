param([switch]$Lan)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$jarPath = Join-Path $projectRoot 'target/campus-sharing-0.1.0-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $jarPath)) {
    throw 'Build first: .\mvnw.cmd package'
}
$previousPassword = $env:DB_PASSWORD
$previousUser = $env:DB_USERNAME
$previousUrl = $env:DB_URL
$password = Read-Host 'MySQL password for yanhuo (127.0.0.1:3306)' -AsSecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($password)
try {
    $env:DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    $env:DB_USERNAME = 'yanhuo'
    $env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/campus_sharing?useUnicode=true&characterEncoding=UTF-8&connectionTimeZone=Asia/Shanghai'
    $javaArguments = @('-jar', $jarPath)
    if ($Lan) {
        $javaArguments += '--server.address=0.0.0.0'
        Write-Host 'LAN mode: connect using this computer''s Wi-Fi IPv4 address, not 127.0.0.1.'
    }
    & java @javaArguments
    if ($LASTEXITCODE -ne 0) { throw "Application exited with code $LASTEXITCODE" }
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    $password.Dispose()
    $env:DB_PASSWORD = $previousPassword
    $env:DB_USERNAME = $previousUser
    $env:DB_URL = $previousUrl
}

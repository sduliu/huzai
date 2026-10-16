[CmdletBinding()]
param(
    [string]$SdkRoot = "$env:LOCALAPPDATA/Android/Sdk",
    [string]$NdkVersion = '27.0.12077973',
    [string]$CmakeVersion = '3.22.1'
)

$ErrorActionPreference = 'Stop'
$repoPath = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$ndkPath = (Resolve-Path (Join-Path $SdkRoot "ndk/$NdkVersion")).Path
$cmakePath = (Resolve-Path (Join-Path $SdkRoot "cmake/$CmakeVersion/bin")).Path
$compilerPath = Join-Path $ndkPath 'toolchains/llvm/prebuilt/windows-x86_64/bin'
$cargoTarget = Join-Path $repoPath 'app/build/doh-probe'
$jniOutput = Join-Path $repoPath 'app/build/generated/dohProbeJni/arm64-v8a'
New-Item -ItemType Directory -Force -Path $cargoTarget, $jniOutput | Out-Null
$toolchainPath = Join-Path $cargoTarget 'android-probe.cmake'
$ndkCmake = (Join-Path $ndkPath 'build/cmake/android.toolchain.cmake').Replace('\', '/')
@"
set(ANDROID_ABI arm64-v8a CACHE STRING "" FORCE)
set(ANDROID_PLATFORM android-26 CACHE STRING "" FORCE)
include("$ndkCmake")
"@ | Set-Content -LiteralPath $toolchainPath -Encoding ASCII

$overrides = @{
    'PATH' = "$cmakePath;$compilerPath;$env:PATH"
    'ANDROID_NDK_ROOT' = $ndkPath
    'CMAKE_GENERATOR' = 'Ninja'
    'CMAKE_TOOLCHAIN_FILE_aarch64_linux_android' = $toolchainPath
    'CC_aarch64_linux_android' = (Join-Path $compilerPath 'aarch64-linux-android26-clang.cmd')
    'AR_aarch64_linux_android' = (Join-Path $compilerPath 'llvm-ar.exe')
    'CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER' = (Join-Path $compilerPath 'aarch64-linux-android26-clang.cmd')
    'CARGO_TARGET_AARCH64_LINUX_ANDROID_RUSTFLAGS' = '-C link-arg=-Wl,-z,max-page-size=16384'
}
$previous = @{}
try {
    foreach ($name in $overrides.Keys) {
        $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        [Environment]::SetEnvironmentVariable($name, $overrides[$name], 'Process')
    }
    # Windows PowerShell 5 turns native stderr progress into ErrorRecord when redirected.
    # Normalize it to text and judge the process by its exit code.
    $ErrorActionPreference = 'Continue'
    & cargo build --locked --manifest-path (Join-Path $repoPath 'tools/doh-probe/Cargo.toml') `
        --target aarch64-linux-android --target-dir $cargoTarget --release --lib 2>&1 |
        ForEach-Object { $_.ToString() }
    $cargoExit = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($cargoExit -ne 0) { throw 'Android DoH probe build failed.' }
    $libraryPath = Join-Path $cargoTarget 'aarch64-linux-android/release/libkudo_doh_probe.so'
    $segments = & (Join-Path $compilerPath 'llvm-readelf.exe') -lW $libraryPath
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect probe ELF.' }
    $loads = @($segments | Select-String '^\s*LOAD\s')
    if ($loads.Count -eq 0 -or @($loads | Where-Object { $_.Line -notmatch '0x4000\s*$' }).Count -gt 0) {
        throw 'Probe ELF must use 16KB LOAD segment alignment.'
    }
    Copy-Item -LiteralPath $libraryPath -Destination (Join-Path $jniOutput 'libkudo_doh_probe.so') -Force
    Write-Output "Android test-only JNI: $jniOutput"
    Write-Output "Size: $((Get-Item -LiteralPath $libraryPath).Length) bytes; ELF LOAD alignment: 16KB"
} finally {
    foreach ($name in $previous.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
    }
}

# Builds QuickJS-ng's `qjs` for Android and installs it as app/src/main/jniLibs/<abi>/libqjs.so.
# yt-dlp needs a JavaScript runtime for YouTube; naming the executable lib*.so makes Android
# install it in the app's native-library folder, the only place an app may run a binary.
# Run:  powershell -ExecutionPolicy Bypass -File tools\build-quickjs.ps1
$ErrorActionPreference = "Stop"
$version = "0.17.0"
$abis = "arm64-v8a", "armeabi-v7a", "x86_64"
$minSdk = 29

$root = Split-Path $PSScriptRoot
$cache = Join-Path $PSScriptRoot ".cache"
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA "Android\Sdk" }
$ndk = Get-ChildItem (Join-Path $sdk "ndk") -Directory | Sort-Object Name | Select-Object -Last 1
$cmakeDir = Get-ChildItem (Join-Path $sdk "cmake") -Directory | Sort-Object Name | Select-Object -Last 1
$cmake = Join-Path $cmakeDir.FullName "bin\cmake.exe"
$ninja = Join-Path $cmakeDir.FullName "bin\ninja.exe"
"NDK $($ndk.Name), CMake $($cmakeDir.Name)"

New-Item -ItemType Directory -Force $cache | Out-Null
$src = Join-Path $cache "quickjs-$version"
if (-not (Test-Path $src)) {
    $zip = Join-Path $cache "quickjs-$version.zip"
    $ProgressPreference = "SilentlyContinue"
    Invoke-WebRequest "https://github.com/quickjs-ng/quickjs/archive/refs/tags/v$version.zip" -OutFile $zip
    Expand-Archive $zip $cache -Force
}

foreach ($abi in $abis) {
    $build = Join-Path $cache "build-$abi"
    & $cmake -S $src -B $build -G Ninja "-DCMAKE_MAKE_PROGRAM=$ninja" `
        "-DCMAKE_TOOLCHAIN_FILE=$($ndk.FullName)\build\cmake\android.toolchain.cmake" `
        "-DANDROID_ABI=$abi" "-DANDROID_PLATFORM=android-$minSdk" -DCMAKE_BUILD_TYPE=Release `
        -DBUILD_SHARED_LIBS=OFF | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "cmake configure failed for $abi" }
    & $cmake --build $build --target qjs_exe | Select-Object -Last 1
    if ($LASTEXITCODE -ne 0) { throw "build failed for $abi" }
    $out = Join-Path $root "app\src\main\jniLibs\$abi"
    New-Item -ItemType Directory -Force $out | Out-Null
    Copy-Item (Join-Path $build "qjs") (Join-Path $out "libqjs.so") -Force
    "{0,-12} -> libqjs.so  {1:N0} KB" -f $abi, ((Get-Item (Join-Path $out "libqjs.so")).Length / 1KB)
}

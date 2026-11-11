param([switch]$SkipTests)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$framework = Join-Path $root 'keiyoushi'
$revision = '4591fbc9bb571ad03aaea0f7428f7f403350e90e'

function Check-Exit([string]$step) {
    if ($LASTEXITCODE -ne 0) { throw "$step failed (exit $LASTEXITCODE)." }
}

if (-not (Test-Path (Join-Path $framework 'gradlew.bat'))) {
    git clone --depth 1 --filter=blob:none --sparse https://github.com/keiyoushi/extensions-source.git $framework
    Check-Exit 'Cloning Keiyoushi'
    git -C $framework fetch --depth 1 --filter=blob:none origin $revision
    Check-Exit 'Fetching the pinned framework'
    git -C $framework checkout $revision
    Check-Exit 'Checking out the pinned framework'
    git -C $framework sparse-checkout set common core compiler gradle lib
    Check-Exit 'Downloading build modules'
}
$actualRevision = git -C $framework rev-parse HEAD
Check-Exit 'Reading framework revision'
if ($actualRevision -ne $revision) { throw "Expected Keiyoushi revision $revision; found $actualRevision." }

if (-not $env:JAVA_HOME) {
    $portableJava = Get-ChildItem (Join-Path $root '.tools/jdk') -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($portableJava) { $env:JAVA_HOME = $portableJava.FullName }
}
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to a JDK 21 or newer installation.' }
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
if (-not $env:ANDROID_HOME -and (Test-Path (Join-Path $root '.tools/android-sdk'))) {
    $env:ANDROID_HOME = Join-Path $root '.tools/android-sdk'
}
if (-not $env:ANDROID_HOME -and -not $env:ANDROID_SDK_ROOT) {
    throw 'Set ANDROID_HOME to an Android SDK with platform 37.0 and build-tools 36.0.0.'
}
if (-not $env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME = Join-Path $root '.tools/gradle-home' }

$module = Join-Path $framework 'src/en/atlascomics'
New-Item -ItemType Directory -Force $module | Out-Null
Copy-Item -Path (Join-Path $root 'src/en/atlascomics/*') -Destination $module -Recurse -Force
$fixtureDir = Join-Path $module 'test-resources'
New-Item -ItemType Directory -Force $fixtureDir | Out-Null
Copy-Item -Path (Join-Path $root 'tests/fixtures/*.json') -Destination $fixtureDir -Force

Push-Location $framework
try {
    $tasks = @(':src:en:atlascomics:spotlessApply', ':src:en:atlascomics:assembleRelease', ':src:en:atlascomics:lintRelease')
    if (-not $SkipTests) { $tasks += ':src:en:atlascomics:testDebugUnitTest' }
    & .\gradlew.bat @tasks --console=plain
    Check-Exit 'Building and validating Atlas Comics'
    if (-not $SkipTests) {
        $testReports = Get-ChildItem (Join-Path $module 'build/test-results/testDebugUnitTest') -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue
        if (-not $testReports) { throw 'No unit-test reports were produced.' }
    }
    Copy-Item -Path (Join-Path $module 'src/*') -Destination (Join-Path $root 'src/en/atlascomics/src') -Recurse -Force
    Copy-Item -Path (Join-Path $module 'test/*') -Destination (Join-Path $root 'src/en/atlascomics/test') -Recurse -Force
    Copy-Item -LiteralPath (Join-Path $module 'build.gradle.kts') -Destination (Join-Path $root 'src/en/atlascomics/build.gradle.kts') -Force
    $dist = Join-Path $root 'dist'
    New-Item -ItemType Directory -Force $dist | Out-Null
    $releaseDir = Join-Path $module 'build/outputs/apk/release'
    $output = (Get-Content (Join-Path $releaseDir 'output-metadata.json') -Raw | ConvertFrom-Json).elements
    if ($output.Count -ne 1) { throw 'Expected one release APK.' }
    $publishedApk = Join-Path $dist "atlas-comics-v$($output.versionName).apk"
    Copy-Item -LiteralPath (Join-Path $releaseDir $output.outputFile) -Destination $publishedApk -Force
    Get-FileHash $publishedApk -Algorithm SHA256
} finally {
    Pop-Location
}

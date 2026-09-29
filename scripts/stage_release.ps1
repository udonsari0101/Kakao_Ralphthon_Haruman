#requires -Version 5.1
<#
.SYNOPSIS
Verifies and preserves one final signed release APK without overwriting a version.
.DESCRIPTION
Creates releases/<versionName>-<versionCode>/ only after all gates pass.
Does not build, sign, install, generate keys, change Git, create tags, or upload.
The QA JSON must describe the exact final APK, not a .debug/.releaseqa substitute.

Required QA JSON shape (replace every example with observed values):
{
  "sourceCommit": "<full source SHA>",
  "apkSha256": "<exact final APK SHA256>",
  "environment": {
    "kind": "physicalDevice",
    "name": "<model or AVD name; never a device serial>",
    "androidVersion": "<observed Android version>",
    "checkedAt": "<ISO-8601 timestamp with timezone>"
  },
  "checks": {
    "installed": true, "launched": true, "mainFlows": true, "restarted": true
  },
  "apiIntegration": {
    "state": "NotConfigured",
    "realApiConnected": false,
    "environment": "<API verification environment, or not-configured>"
  },
  "build": {
    "succeeded": true,
    "startedAt": "<ISO-8601 timestamp with timezone>",
    "finishedAt": "<ISO-8601 timestamp with timezone>"
  },
  "tests": { "passed": true, "summary": "<test kinds, counts, failures, evidence>" },
  "toolchain": {
    "java": "<version>", "gradle": "<version>", "agp": "<version>",
    "kotlin": "<version>", "compileSdk": 36, "buildTools": "<version>"
  },
  "knownLimitations": ["<remaining limits, API state, installation compatibility>"]
}
Use state RealApiVerified only with realApiConnected=true and QA on this exact APK.
Free-text inputs must already be sanitized for public release metadata.
Failure during staging leaves its private staging directory for inspection.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][Alias('APK')][ValidateNotNullOrEmpty()][string]$ApkPath,
    [Parameter(Mandatory = $true)][Alias('SourceFullSha')][ValidatePattern('^[0-9a-fA-F]{40}$')][string]$SourceCommit,
    [Parameter(Mandatory = $true)][ValidateNotNullOrEmpty()][string]$Tag,
    [Parameter(Mandatory = $true)][ValidatePattern('^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$')][string]$ExpectedPackage,
    [Parameter(Mandatory = $true)][ValidatePattern('^[A-Za-z0-9][A-Za-z0-9._+-]*$')][string]$ExpectedVersionName,
    [Parameter(Mandatory = $true)][ValidateRange(1, 2147483647)][int]$ExpectedVersionCode,
    [Parameter(Mandatory = $true)][Alias('CertSha256')][ValidateNotNullOrEmpty()][string]$ExpectedCertificateSha256,
    [Parameter(Mandatory = $true)][ValidateNotNullOrEmpty()][string]$ApkSigner,
    [Parameter(Mandatory = $true)][ValidateNotNullOrEmpty()][string]$ApkAnalyzer,
    [Parameter(Mandatory = $true)][Alias('QaEvidenceJson')][ValidateNotNullOrEmpty()][string]$QaEvidencePath,
    [Parameter(Mandatory = $true)][ValidateNotNullOrEmpty()][string]$QaSummary,
    [ValidateRange(1, 1000)][int]$ExpectedMinSdk = 24,
    [string]$RepositoryRoot = (Split-Path -Parent $PSScriptRoot)
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Resolve-File([string]$Path, [string]$Label) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw "$Label file was not found." }
    return (Resolve-Path -LiteralPath $Path).ProviderPath
}
function Invoke-CheckedNative([string]$Tool, [string[]]$Arguments, [int[]]$AllowedExitCodes = @(0)) {
    # Capture stderr without printing certificate DNs, internal paths, or raw logs.
    $priorPreference = $ErrorActionPreference
    $hadDebugVariable = Test-Path Env:DEBUG
    $priorDebug = $env:DEBUG
    try {
        # Android SDK .bat launchers enable command echo when DEBUG is set.
        $env:DEBUG = ''
        $ErrorActionPreference = 'Continue'
        $lines = @(& $Tool @Arguments 2>&1 | ForEach-Object { $_.ToString() })
        $nativeExit = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $priorPreference
        if ($hadDebugVariable) { $env:DEBUG = $priorDebug }
        else { Remove-Item Env:DEBUG -ErrorAction SilentlyContinue }
    }
    if ($nativeExit -notin $AllowedExitCodes) {
        throw "$([IO.Path]::GetFileName($Tool)) failed with exit code $nativeExit."
    }
    return [pscustomobject]@{ ExitCode = $nativeExit; Text = (($lines -join [Environment]::NewLine).Trim()) }
}
function Require-Property($Object, [string]$Name) {
    if ($null -eq $Object -or $null -eq $Object.PSObject.Properties[$Name]) {
        throw "QA evidence is missing '$Name'."
    }
    # Keep an empty JSON array as an array rather than losing it through pipeline unrolling.
    Write-Output -NoEnumerate $Object.PSObject.Properties[$Name].Value
}
function Require-Text($Object, [string]$Name) {
    $value = Require-Property $Object $Name
    if ($value -isnot [string] -or [string]::IsNullOrWhiteSpace($value)) {
        throw "QA evidence '$Name' must be a non-empty string."
    }
    return $value.Trim()
}
function Require-True($Object, [string]$Name) {
    $value = Require-Property $Object $Name
    if ($value -isnot [bool] -or -not $value) {
        throw "QA evidence '$Name' must be the JSON boolean true."
    }
}
function Read-EvidenceTime([string]$Value, [string]$Label) {
    if ($Value -notmatch '(Z|[+-]\d{2}:\d{2})$') { throw "$Label needs an explicit timezone." }
    $parsedTime = [DateTimeOffset]::MinValue
    if (-not [DateTimeOffset]::TryParse($Value, [ref]$parsedTime)) { throw "$Label is not a timestamp." }
    if ($parsedTime -gt [DateTimeOffset]::UtcNow.AddMinutes(5)) { throw "$Label is in the future." }
    return $parsedTime
}
function Assert-ChildPath([string]$Path, [string]$Parent) {
    $resolvedParent = [IO.Path]::GetFullPath($Parent).TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
    $resolvedChild = [IO.Path]::GetFullPath($Path)
    if (-not $resolvedChild.StartsWith($resolvedParent, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Release path escaped its intended directory.'
    }
}
function Assert-PlainDirectory([string]$Path) {
    if (Test-Path -LiteralPath $Path) {
        $item = Get-Item -LiteralPath $Path -Force
        if (-not $item.PSIsContainer -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            throw 'Release directories must be ordinary directories, not links or files.'
        }
    }
}
function Write-NewUtf8([string]$Path, [string]$Value) {
    $bytes = ([Text.UTF8Encoding]::new($false)).GetBytes($Value)
    $stream = [IO.File]::Open($Path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try { $stream.Write($bytes, 0, $bytes.Length) } finally { $stream.Dispose() }
}

$apk = Resolve-File $ApkPath 'APK'
if ([IO.Path]::GetExtension($apk) -ine '.apk') { throw 'The final artifact must be an APK.' }
$signer = Resolve-File $ApkSigner 'apksigner'
$analyzer = Resolve-File $ApkAnalyzer 'apkanalyzer'
$evidenceFile = Resolve-File $QaEvidencePath 'QA evidence'
$repo = (Resolve-Path -LiteralPath $RepositoryRoot).ProviderPath
$git = (Get-Command git -CommandType Application -ErrorAction Stop | Select-Object -First 1).Source
$SourceCommit = $SourceCommit.ToLowerInvariant()
$expectedCertificate = ($ExpectedCertificateSha256 -replace '[:\s]', '').ToLowerInvariant()
if ($expectedCertificate -notmatch '^[0-9a-f]{64}$') { throw 'Expected certificate SHA256 is invalid.' }
if ($ExpectedPackage -match '\.(debug|releaseqa|preview)$') { throw 'A QA/debug package is not the final release package.' }
if ([string]::IsNullOrWhiteSpace($QaSummary)) { throw 'QA summary must describe observed verification.' }

$releaseRoot = Join-Path $repo 'releases'
$versionFolder = "$ExpectedVersionName-$ExpectedVersionCode"
$destination = Join-Path $releaseRoot $versionFolder
$stagingRoot = Join-Path $releaseRoot 'staging'
Assert-ChildPath $destination $releaseRoot
Assert-PlainDirectory $releaseRoot
Assert-PlainDirectory $stagingRoot
if (Test-Path -LiteralPath $destination) { throw "Release version already exists: $versionFolder. Never overwrite it." }

# Read-only Git checks allow later documentation-only commits, but no app drift.
$gitRoot = (Invoke-CheckedNative $git @('-C', $repo, 'rev-parse', '--show-toplevel')).Text
if ([IO.Path]::GetFullPath($gitRoot) -ine [IO.Path]::GetFullPath($repo)) { throw 'RepositoryRoot must be the repository root.' }
$resolvedSource = (Invoke-CheckedNative $git @('-C', $repo, 'rev-parse', '--verify', "${SourceCommit}^{commit}")).Text
if ($resolvedSource -ine $SourceCommit) { throw 'SourceCommit must be the full source commit SHA.' }
$null = Invoke-CheckedNative $git @('-C', $repo, 'cat-file', '-e', "${SourceCommit}:apk-project")
$null = Invoke-CheckedNative $git @('-C', $repo, 'merge-base', '--is-ancestor', $SourceCommit, 'HEAD')
$appStatus = (Invoke-CheckedNative $git @('-C', $repo, 'status', '--porcelain=v1', '--untracked-files=all', '--', 'apk-project')).Text
if ($appStatus) { throw 'Commit all app changes before staging a release.' }
$appDiff = Invoke-CheckedNative $git @('-C', $repo, 'diff', '--quiet', $SourceCommit, '--', 'apk-project') @(0, 1)
if ($appDiff.ExitCode -ne 0) { throw 'Current app sources do not match SourceCommit.' }
$null = Invoke-CheckedNative $git @('-C', $repo, 'check-ref-format', "refs/tags/$Tag")
$tagCheck = Invoke-CheckedNative $git @('-C', $repo, 'show-ref', '--verify', '--quiet', "refs/tags/$Tag") @(0, 1)
$tagState = 'not-created'
if ($tagCheck.ExitCode -eq 0) {
    $tagCommit = (Invoke-CheckedNative $git @('-C', $repo, 'rev-parse', '--verify', "refs/tags/${Tag}^{commit}")).Text
    if ($tagCommit -ine $SourceCommit) { throw 'The existing tag points at a different source commit.' }
    $tagState = 'matches-source'
}

# A later release of the same package must increase versionCode.
if (Test-Path -LiteralPath $releaseRoot -PathType Container) {
    foreach ($directory in Get-ChildItem -LiteralPath $releaseRoot -Directory) {
        if ($directory.Name -eq 'staging') { continue }
        $previousManifest = Join-Path $directory.FullName 'manifest.json'
        if (Test-Path -LiteralPath $previousManifest -PathType Leaf) {
            $previous = Get-Content -LiteralPath $previousManifest -Raw -Encoding UTF8 | ConvertFrom-Json
            if ($previous.packageName -eq $ExpectedPackage -and [long]$previous.versionCode -ge $ExpectedVersionCode) {
                throw 'versionCode must exceed previously staged releases of this package.'
            }
        }
    }
}

$apkHash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
$qa = Get-Content -LiteralPath $evidenceFile -Raw -Encoding UTF8 | ConvertFrom-Json
if ((Require-Text $qa 'apkSha256').ToLowerInvariant() -ne $apkHash) { throw 'QA was not performed on this exact APK hash.' }
if ((Require-Text $qa 'sourceCommit').ToLowerInvariant() -ne $SourceCommit) { throw 'QA source commit does not match.' }
$environment = Require-Property $qa 'environment'
$kind = Require-Text $environment 'kind'
if ($kind -notin @('physicalDevice', 'emulator')) { throw 'Final QA requires a physical device or emulator.' }
$environmentName = Require-Text $environment 'name'
$androidVersion = Require-Text $environment 'androidVersion'
$checkedAt = Read-EvidenceTime (Require-Text $environment 'checkedAt') 'QA checkedAt'
$checks = Require-Property $qa 'checks'
foreach ($gate in @('installed', 'launched', 'mainFlows', 'restarted')) { Require-True $checks $gate }
$api = Require-Property $qa 'apiIntegration'
$apiState = Require-Text $api 'state'
$apiConnected = Require-Property $api 'realApiConnected'
if ($apiConnected -isnot [bool]) { throw 'realApiConnected must be a JSON boolean.' }
if ($apiState -notin @('NotConfigured', 'RealApiVerified')) { throw 'Release API state must be NotConfigured or RealApiVerified.' }
if (($apiState -eq 'RealApiVerified') -ne $apiConnected) { throw 'API state and realApiConnected disagree.' }
$apiEnvironment = Require-Text $api 'environment'
$build = Require-Property $qa 'build'
Require-True $build 'succeeded'
$buildStarted = Read-EvidenceTime (Require-Text $build 'startedAt') 'Build startedAt'
$buildFinished = Read-EvidenceTime (Require-Text $build 'finishedAt') 'Build finishedAt'
if ($buildFinished -lt $buildStarted -or $checkedAt -lt $buildFinished) { throw 'Build/QA timestamps are out of order.' }
$tests = Require-Property $qa 'tests'
Require-True $tests 'passed'
$testSummary = Require-Text $tests 'summary'
$toolchain = Require-Property $qa 'toolchain'
$publicToolchain = [ordered]@{}
foreach ($field in @('java', 'gradle', 'agp', 'kotlin', 'buildTools')) { $publicToolchain[$field] = Require-Text $toolchain $field }
$compileSdk = Require-Property $toolchain 'compileSdk'
if ($compileSdk -isnot [int] -and $compileSdk -isnot [long]) { throw 'compileSdk must be a JSON integer.' }
if ($compileSdk -lt $ExpectedMinSdk) { throw 'compileSdk cannot be lower than minSdk.' }
$publicToolchain['compileSdk'] = $compileSdk
$limitsValue = Require-Property $qa 'knownLimitations'
if ($null -eq $limitsValue -or $limitsValue -is [string]) { throw 'knownLimitations must be a JSON array (it may be empty).' }
$limits = @($limitsValue)
foreach ($limit in $limits) {
    if ($limit -isnot [string] -or [string]::IsNullOrWhiteSpace($limit)) { throw 'Every known limitation must be a non-empty string.' }
}

# These invoke the real SDK verification tools. No signing or APK mutation occurs.
$signature = Invoke-CheckedNative $signer @('verify', '--verbose', '--print-certs', $apk)
$certificateMatches = [regex]::Matches($signature.Text, '(?im)^Signer #\d+ certificate SHA-256 digest:\s*([0-9a-f:]+)\s*$')
if ($certificateMatches.Count -ne 1) { throw 'Expected exactly one verifiable APK signer certificate.' }
$certificate = ($certificateMatches[0].Groups[1].Value -replace ':', '').ToLowerInvariant()
if ($certificate -ne $expectedCertificate) { throw 'APK signer certificate does not match the expected release certificate.' }
if ($signature.Text -match '(?im)^Signer #\d+ certificate DN:.*CN\s*=\s*Android Debug(?:,|\s*$)') { throw 'Android Debug signing keys are not accepted for releases.' }
$packageName = (Invoke-CheckedNative $analyzer @('manifest', 'application-id', $apk)).Text
$versionName = (Invoke-CheckedNative $analyzer @('manifest', 'version-name', $apk)).Text
$versionCodeText = (Invoke-CheckedNative $analyzer @('manifest', 'version-code', $apk)).Text
$minSdkText = (Invoke-CheckedNative $analyzer @('manifest', 'min-sdk', $apk)).Text
$debuggableText = (Invoke-CheckedNative $analyzer @('manifest', 'debuggable', $apk)).Text
if ($packageName -cne $ExpectedPackage -or $versionName -cne $ExpectedVersionName) { throw 'APK package/versionName does not match.' }
if ($versionCodeText -notmatch '^\d+$' -or [long]$versionCodeText -ne $ExpectedVersionCode) { throw 'APK versionCode does not match.' }
if ($minSdkText -notmatch '^\d+$' -or [long]$minSdkText -ne $ExpectedMinSdk) { throw 'APK minSdk does not match.' }
if ($debuggableText -ine 'false') { throw 'The final release APK must have debuggable=false.' }
if ((Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant() -ne $apkHash) { throw 'APK changed during validation.' }
$finalAppStatus = (Invoke-CheckedNative $git @('-C', $repo, 'status', '--porcelain=v1', '--untracked-files=all', '--', 'apk-project')).Text
$finalAppDiff = Invoke-CheckedNative $git @('-C', $repo, 'diff', '--quiet', $SourceCommit, '--', 'apk-project') @(0, 1)
if ($finalAppStatus -or $finalAppDiff.ExitCode -ne 0) { throw 'App sources changed during verification; repeat the release checks.' }

$artifactName = "haruman-$versionFolder-release.apk"
$manifest = [ordered]@{
    schemaVersion = 1
    stagedAt = [DateTimeOffset]::UtcNow.ToString('o')
    sourceCommit = $SourceCommit
    tag = $Tag
    packageName = $packageName
    versionName = $versionName
    versionCode = $ExpectedVersionCode
    minSdk = $ExpectedMinSdk
    variant = 'release'
    artifactName = $artifactName
    sizeBytes = (Get-Item -LiteralPath $apk).Length
    apkSha256 = $apkHash
    signingCertificateSha256 = $certificate
    signatureVerified = $true
    debuggable = $false
    buildStartedAt = $buildStarted.ToString('o')
    buildFinishedAt = $buildFinished.ToString('o')
    toolchain = $publicToolchain
    tests = [ordered]@{ passed = $true; summary = $testSummary }
    sourceVerification = [ordered]@{ appTreeMatchesCommit = $true; appWorkingTreeClean = $true; localTag = $tagState }
    finalArtifactQa = [ordered]@{
        apkSha256 = $apkHash
        environment = [ordered]@{ kind = $kind; name = $environmentName; androidVersion = $androidVersion; checkedAt = $checkedAt.ToString('o') }
        installed = $true; launched = $true; mainFlows = $true; restarted = $true
        summary = $QaSummary.Trim()
    }
    apiIntegration = [ordered]@{ state = $apiState; realApiConnected = $apiConnected; environment = $apiEnvironment; verifiedApkSha256 = $apkHash }
    knownLimitations = $limits
}
$limitLines = if ($limits.Count -eq 0) { '- No additional limitations were declared in the supplied QA evidence.' } else { ($limits | ForEach-Object { "- $_" }) -join "`n" }
$notes = @"
# Haruman $Tag

- Version: $versionName ($ExpectedVersionCode)
- Package: $packageName
- Source commit: $SourceCommit
- APK SHA256: $apkHash
- Signing certificate SHA256: $certificate
- Debuggable: false
- API state: $apiState; real API connected: $apiConnected
- API verification environment: $apiEnvironment

## Changes and verified behavior

$($QaSummary.Trim())

## Final APK verification

- Exact artifact: $artifactName
- Environment: $kind / $environmentName / Android $androidVersion
- Observed at: $($checkedAt.ToString('o'))
- Installation, launch, main flows, and restart: passed on this exact APK
- Build: $($buildStarted.ToString('o')) to $($buildFinished.ToString('o'))
- Tests: $testSummary
- Source app tree and release signature: verified before staging

## Known limitations

$limitLines

A purchase decision is not proof of an order or payment.
This local stage does not create or verify a GitHub Release.
"@
$qaSummaryText = @"
# Final artifact QA

- APK SHA256: $apkHash
- Source commit: $SourceCommit
- Environment: $kind / $environmentName / Android $androidVersion
- Checked at: $($checkedAt.ToString('o'))
- Installed: true
- Launched: true
- Main flows: true
- Restarted: true
- API: $apiState ($apiEnvironment), real connection: $apiConnected

$($QaSummary.Trim())

## Tests

$testSummary

## Known limitations

$limitLines
"@

# Write to a fresh staging directory, then publish with a non-overwriting rename.
$stage = Join-Path $stagingRoot ("$versionFolder-" + [Guid]::NewGuid().ToString('N'))
Assert-ChildPath $stage $releaseRoot
Assert-ChildPath $destination $releaseRoot
if (Test-Path -LiteralPath $destination) { throw 'Release directory appeared during verification; refusing to overwrite it.' }
$null = [IO.Directory]::CreateDirectory($stage)
try {
    [IO.File]::Copy($apk, (Join-Path $stage $artifactName), $false)
    $stagedApkHash = (Get-FileHash -LiteralPath (Join-Path $stage $artifactName) -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($stagedApkHash -ne $apkHash) { throw 'Copied APK does not match the verified artifact.' }
    Write-NewUtf8 (Join-Path $stage 'manifest.json') (($manifest | ConvertTo-Json -Depth 12) + "`n")
    Write-NewUtf8 (Join-Path $stage 'release-notes.md') ($notes + "`n")
    Write-NewUtf8 (Join-Path $stage 'qa-summary.md') ($qaSummaryText + "`n")
    $sumLines = foreach ($fileName in @($artifactName, 'manifest.json', 'release-notes.md', 'qa-summary.md')) {
        $sum = (Get-FileHash -LiteralPath (Join-Path $stage $fileName) -Algorithm SHA256).Hash.ToLowerInvariant()
        "$sum  $fileName"
    }
    Write-NewUtf8 (Join-Path $stage 'SHA256SUMS.txt') (($sumLines -join "`n") + "`n")
    # Both absolute paths were checked above; Directory.Move fails if destination exists.
    Assert-PlainDirectory $releaseRoot
    Assert-PlainDirectory $stagingRoot
    [IO.Directory]::Move($stage, $destination)
} catch {
    Write-Warning "Staging failed. The incomplete staging directory was preserved: $stage"
    throw
}
[pscustomobject]@{
    ReleaseDirectory = $destination
    Apk = (Join-Path $destination $artifactName)
    ApkSha256 = $apkHash
    SourceCommit = $SourceCommit
    Tag = $Tag
    PublishedToGitHub = $false
}

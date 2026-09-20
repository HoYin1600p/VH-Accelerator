[CmdletBinding()]
param(
    [switch]$DryRun,
    [string]$ArtifactPath,
    [string]$ChangelogPath,
    [string]$DisplayName = 'VH Accelerator 1.0.14',
    [string]$ProjectId = '1629601',
    [string[]]$GameVersionIds = @('9008', '8326', '7498', '9638')
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not $ArtifactPath) {
    $ArtifactPath = Join-Path $repoRoot 'build/libs/VH-Accelerator-1.0.14.jar'
}
if (-not $ChangelogPath) {
    $ChangelogPath = Join-Path $repoRoot 'docs/curseforge/CHANGELOG-1.0.14.md'
}

$artifact = Get-Item -LiteralPath $ArtifactPath
$changelog = Get-Content -Raw -LiteralPath $ChangelogPath
if ($artifact.Name -ne 'VH-Accelerator-1.0.14.jar') {
    throw "Unexpected release artifact: $($artifact.Name)"
}
if ($changelog -notmatch 'releases/tag/v1\.0\.14') {
    throw 'CurseForge changelog does not contain the final GitHub release URL.'
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($artifact.FullName)
try {
    $modsEntry = $archive.GetEntry('META-INF/mods.toml')
    if (-not $modsEntry) { throw 'META-INF/mods.toml is missing.' }
    $reader = [IO.StreamReader]::new($modsEntry.Open())
    try { $modsToml = $reader.ReadToEnd() } finally { $reader.Dispose() }
    if ($modsToml -notmatch '(?m)^version\s*=\s*"1\.0\.14"\s*$') {
        throw 'The embedded mod version is not 1.0.14.'
    }
} finally {
    $archive.Dispose()
}

$metadata = [ordered]@{
    changelog = $changelog
    changelogType = 'markdown'
    displayName = $DisplayName
    gameVersions = @($GameVersionIds | ForEach-Object { [int]$_ })
    releaseType = 'release'
    isMarkedForManualRelease = $false
}
$sha256 = (Get-FileHash -LiteralPath $artifact.FullName -Algorithm SHA256).Hash.ToLowerInvariant()

if ($DryRun) {
    [ordered]@{
        result = 'DRY_RUN_PASS'
        projectId = $ProjectId
        artifact = [ordered]@{
            filename = $artifact.Name
            size = $artifact.Length
            sha256 = $sha256
        }
        metadata = [ordered]@{
            displayName = $metadata.displayName
            releaseType = $metadata.releaseType
            gameVersions = $metadata.gameVersions
            isMarkedForManualRelease = $metadata.isMarkedForManualRelease
            changelogType = $metadata.changelogType
            changelogHasGitHubReleaseUrl = $true
        }
    } | ConvertTo-Json -Depth 6
    exit 0
}

$token = [Environment]::GetEnvironmentVariable('CURSEFORGE_API_TOKEN', 'Process')
if (-not $token) {
    $token = [Environment]::GetEnvironmentVariable('CURSEFORGE_API_TOKEN', 'User')
}
if (-not $token) {
    throw 'CURSEFORGE_API_TOKEN is unavailable in the process and user environments.'
}

$headers = @{ 'X-Api-Token' = $token }
$form = @{
    metadata = ($metadata | ConvertTo-Json -Depth 8 -Compress)
    file = $artifact
}
$response = Invoke-RestMethod `
    -Uri "https://minecraft.curseforge.com/api/projects/$ProjectId/upload-file" `
    -Headers $headers `
    -Method Post `
    -Form $form

[ordered]@{
    result = 'UPLOADED'
    projectId = $ProjectId
    fileId = [string]$response.id
    filename = $artifact.Name
    size = $artifact.Length
    sha256 = $sha256
} | ConvertTo-Json -Depth 4

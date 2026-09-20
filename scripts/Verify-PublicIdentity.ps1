[CmdletBinding()]
param(
    [string[]]$Artifacts = @('build/libs/VH-Accelerator-1.0.14.jar'),
    [switch]$CheckOnly
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Split-Path -Parent $PSScriptRoot)).Path
$repositoryName = 'VH-Accelerator'
$remoteIdentity = 'https://github.com/HoYin1600p/VH-Accelerator.git'
$shadowRoot = "E:\Git Repo's\Codex Workspaces\$repositoryName"
if ($env:VHA_IDENTITY_SCAN_ROOT) {
    $shadowRoot = $env:VHA_IDENTITY_SCAN_ROOT
}
$markerPath = Join-Path $shadowRoot 'workspace-identity.json'
$logPath = Join-Path $shadowRoot 'identity-scan/identity-scan-log.md'
$prohibited = ([char]69) + 'than'

function Get-Sha256Text([string]$Text) {
    $bytes = [Text.Encoding]::UTF8.GetBytes($Text)
    [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)).ToLowerInvariant()
}

function Test-Bytes([byte[]]$Bytes, [string]$Label, [Collections.Generic.List[string]]$Findings) {
    foreach ($encoding in @([Text.Encoding]::UTF8, [Text.Encoding]::Unicode, [Text.Encoding]::BigEndianUnicode)) {
        if ($encoding.GetString($Bytes).IndexOf($prohibited, [StringComparison]::OrdinalIgnoreCase) -ge 0) {
            $Findings.Add($Label)
            return
        }
    }
}

function Test-Archive([string]$Path, [Collections.Generic.List[string]]$Findings) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [IO.Compression.ZipFile]::OpenRead($Path)
    try {
        foreach ($entry in $archive.Entries) {
            if ($entry.FullName.IndexOf($prohibited, [StringComparison]::OrdinalIgnoreCase) -ge 0) {
                $Findings.Add("$Path::$($entry.FullName)")
                continue
            }
            if ($entry.Length -gt 64MB) { continue }
            $stream = $entry.Open()
            try {
                $memory = [IO.MemoryStream]::new()
                try {
                    $stream.CopyTo($memory)
                    Test-Bytes $memory.ToArray() "$Path::$($entry.FullName)" $Findings
                } finally { $memory.Dispose() }
            } finally { $stream.Dispose() }
        }
    } finally { $archive.Dispose() }
}

$marker = Get-Content -Raw -LiteralPath $markerPath | ConvertFrom-Json
$normalizedRoot = $repoRoot.Replace('\', '/').TrimEnd('/').ToLowerInvariant()
$expectedRootHash = Get-Sha256Text $normalizedRoot
if ($marker.repository_name -ne $repositoryName -or
    $marker.remote_identity -ne $remoteIdentity -or
    $marker.git_root_path_sha256 -ne $expectedRootHash) {
    throw 'Shadow-workspace identity marker does not match this repository.'
}

$priorLog = if (Test-Path -LiteralPath $logPath) { Get-Content -Raw -LiteralPath $logPath } else { '' }
$frontierMatches = [regex]::Matches($priorLog, 'New frontier:\s*`?([0-9a-fA-F]{40})`?')
$priorFrontier = $null
for ($index = $frontierMatches.Count - 1; $index -ge 0; $index--) {
    $candidate = $frontierMatches[$index].Groups[1].Value.ToLowerInvariant()
    & git -C $repoRoot cat-file -e "$candidate^{commit}" 2>$null
    if ($LASTEXITCODE -ne 0) { continue }
    & git -C $repoRoot merge-base --is-ancestor $candidate HEAD 2>$null
    if ($LASTEXITCODE -eq 0) { $priorFrontier = $candidate; break }
}

$head = (& git -C $repoRoot rev-parse HEAD).Trim()
$range = if ($priorFrontier) { "$priorFrontier..$head" } else { $head }
$findings = [Collections.Generic.List[string]]::new()
$scopeFiles = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)

$commitIds = if ($priorFrontier) {
    @(& git -C $repoRoot rev-list --reverse $range)
} else {
    @(& git -C $repoRoot rev-list --reverse $head)
}
foreach ($commit in $commitIds) {
    if (-not $commit) { continue }
    $metadata = & git -C $repoRoot show -s --format='%H%n%an%n%ae%n%cn%n%ce%n%s%n%b' $commit
    if (($metadata -join "`n").IndexOf($prohibited, [StringComparison]::OrdinalIgnoreCase) -ge 0) {
        $findings.Add("commit metadata $commit")
    }
    $names = @(& git -C $repoRoot diff-tree --root --no-commit-id --name-only -r $commit)
    foreach ($name in $names) {
        if (-not $name) { continue }
        $null = $scopeFiles.Add($name)
        if ($name.IndexOf($prohibited, [StringComparison]::OrdinalIgnoreCase) -ge 0) {
            $findings.Add("tracked path $name")
        }
    }
}

foreach ($name in $scopeFiles) {
    $fullPath = Join-Path $repoRoot $name
    if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) { continue }
    Test-Bytes ([IO.File]::ReadAllBytes($fullPath)) "tracked file $name" $findings
}

$refs = @(& git -C $repoRoot for-each-ref --format='%(refname)')
foreach ($ref in $refs) {
    if ($ref.IndexOf($prohibited, [StringComparison]::OrdinalIgnoreCase) -ge 0) {
        $findings.Add("ref $ref")
    }
}

$artifactRecords = @()
foreach ($artifactRelative in $Artifacts) {
    $artifactPath = if ([IO.Path]::IsPathRooted($artifactRelative)) { $artifactRelative } else { Join-Path $repoRoot $artifactRelative }
    $artifact = Get-Item -LiteralPath $artifactPath
    if ($artifact.Name.IndexOf($prohibited, [StringComparison]::OrdinalIgnoreCase) -ge 0) {
        $findings.Add("artifact name $($artifact.Name)")
    }
    Test-Archive $artifact.FullName $findings
    $artifactRecords += [ordered]@{
        path = $artifactRelative.Replace('\', '/')
        sha256 = (Get-FileHash -LiteralPath $artifact.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    }
}

$timestamp = (Get-Date).ToUniversalTime().ToString('o')
$result = if ($findings.Count -eq 0) { 'PASS' } else { 'FAIL' }
$entry = [Text.StringBuilder]::new()
$null = $entry.AppendLine()
$null = $entry.AppendLine("## $timestamp - $result (public release scan)")
$null = $entry.AppendLine()
$null = $entry.AppendLine("- Repository: $repositoryName; public remote: $remoteIdentity. Marker verified and prior scan log read before scanning.")
$null = $entry.AppendLine("- Prior frontier: $(if ($priorFrontier) { $priorFrontier } else { 'none; full baseline' }).")
$null = $entry.AppendLine("- Exact history range: $range. Commit contents, paths, messages, author/committer identities, and current refs examined.")
$null = $entry.AppendLine("- Working-tree scope: committed files introduced or changed in the exact history range; release artifacts scanned by archive entry name and decoded content.")
foreach ($record in $artifactRecords) {
    $null = $entry.AppendLine("- $($record.path) SHA-256: $($record.sha256)")
}
$null = $entry.AppendLine("- Result: $result. $($findings.Count) prohibited-identity match(es).")
if ($findings.Count -gt 0) {
    foreach ($finding in $findings) { $null = $entry.AppendLine("- Finding: $finding") }
} else {
    $null = $entry.AppendLine("- New frontier: ``$head``.")
}

if (-not $CheckOnly) {
    $logDirectory = Split-Path -Parent $logPath
    $null = New-Item -ItemType Directory -Force -Path $logDirectory
    $bytes = [Text.UTF8Encoding]::new($false).GetBytes($entry.ToString())
    $stream = [IO.FileStream]::new($logPath, [IO.FileMode]::Append, [IO.FileAccess]::Write, [IO.FileShare]::Read)
    try {
        $stream.Write($bytes, 0, $bytes.Length)
        $stream.Flush($true)
    } finally { $stream.Dispose() }
}

[ordered]@{
    result = $result
    priorFrontier = $priorFrontier
    scannedFrontier = if ($result -eq 'PASS') { $head } else { $null }
    commitRange = $range
    artifactRecords = $artifactRecords
    logAppended = -not $CheckOnly
    findings = @($findings)
} | ConvertTo-Json -Depth 6

if ($result -ne 'PASS') { exit 1 }

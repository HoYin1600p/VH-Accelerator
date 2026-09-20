[CmdletBinding()]
param(
    [string[]]$GameVersionIds = @('9016', '8326', '7498', '9638')
)

$ErrorActionPreference = 'Stop'

$requiredTypes = [ordered]@{
    '1' = 'Minecraft'
    '2' = 'Java'
    '68441' = 'Modloader'
    '75208' = 'Environment'
}

$token = [Environment]::GetEnvironmentVariable('CURSEFORGE_API_TOKEN', 'Process')
if (-not $token) {
    $token = [Environment]::GetEnvironmentVariable('CURSEFORGE_API_TOKEN', 'User')
}
if (-not $token) {
    throw 'CURSEFORGE_API_TOKEN is unavailable in the process and user environments.'
}

$headers = @{ 'X-Api-Token' = $token }
$catalog = Invoke-RestMethod -Uri 'https://minecraft.curseforge.com/api/game/versions' -Headers $headers -Method Get
$selected = foreach ($id in $GameVersionIds) {
    $matches = @($catalog | Where-Object { [string]$_.id -eq [string]$id })
    if ($matches.Count -ne 1) {
        throw "CurseForge game-version ID $id resolved to $($matches.Count) entries."
    }
    $matches[0]
}

$duplicates = @($GameVersionIds | Group-Object | Where-Object Count -gt 1)
if ($duplicates) {
    throw "Duplicate CurseForge game-version IDs: $($duplicates.Name -join ', ')"
}

$resolvedTypes = @($selected | ForEach-Object { [string]$_.gameVersionTypeID } | Sort-Object -Unique)
foreach ($entry in $requiredTypes.GetEnumerator()) {
    if ($entry.Key -notin $resolvedTypes) {
        throw "Missing required CurseForge game-version type $($entry.Value) ($($entry.Key))."
    }
}

$sanitized = [ordered]@{
    result = 'PASS'
    entries = @($selected | ForEach-Object {
        [ordered]@{
            id = [string]$_.id
            name = [string]$_.name
            slug = [string]$_.slug
            gameVersionTypeID = [string]$_.gameVersionTypeID
            gameVersionType = $requiredTypes[[string]$_.gameVersionTypeID]
        }
    })
}
$sanitized | ConvertTo-Json -Depth 5

param(
    [string]$Tag,
    [switch]$ListOnly
)

$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$featureRoot = Join-Path $repoRoot 'src\test\resources\features'

if (-not (Test-Path $featureRoot)) {
    throw "Feature directory not found: $featureRoot"
}

$availableTags = Get-ChildItem -Path $featureRoot -Filter '*.feature' -Recurse -File |
    Get-Content |
    ForEach-Object {
        [regex]::Matches($_, '@[A-Za-z0-9_-]+') | ForEach-Object { $_.Value }
    } |
    Sort-Object -Unique

if (-not $availableTags) {
    throw 'No Cucumber tags were found under src/test/resources/features.'
}

if ($ListOnly) {
    Write-Host 'Available BDD tags:' -ForegroundColor Cyan
    $availableTags | ForEach-Object { Write-Host " - $_" }
    exit 0
}

if ([string]::IsNullOrWhiteSpace($Tag)) {
    Write-Host 'Available BDD tags:' -ForegroundColor Cyan
    for ($i = 0; $i -lt $availableTags.Count; $i++) {
        Write-Host ("[{0}] {1}" -f ($i + 1), $availableTags[$i])
    }

    $selection = Read-Host 'Select a tag by number or type the tag name'
    $selectedIndex = 0
    if ([int]::TryParse($selection, [ref]$selectedIndex) -and $selectedIndex -ge 1 -and $selectedIndex -le $availableTags.Count) {
        $Tag = $availableTags[$selectedIndex - 1]
    }
    else {
        $Tag = $selection
    }
}

if ([string]::IsNullOrWhiteSpace($Tag)) {
    throw 'A tag selection is required.'
}

if (-not $Tag.StartsWith('@')) {
    $Tag = "@$Tag"
}

$effectiveFilter = "$Tag and not @ignore"
Write-Host "Running BDD scenarios for tag filter: $effectiveFilter" -ForegroundColor Green
Push-Location $repoRoot
try {
    & mvn "-Dcucumber.filter.tags=$effectiveFilter" "-Dtest=MediaBddTest" test
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}

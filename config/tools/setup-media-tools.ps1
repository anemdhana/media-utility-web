# PowerShell script to batch download ffmpeg, yt-dlp, and rclone for Windows.
$tools = @(
    @{ Name = "ffmpeg"; Url = "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip"; Zip = "ffmpeg.zip"; ExtractDir = "ffmpeg"; Exe = "ffmpeg.exe" },
    @{ Name = "yt-dlp"; Url = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe"; Zip = "yt-dlp.exe"; ExtractDir = "yt-dlp"; Exe = "yt-dlp.exe" },
    @{ Name = "rclone"; Url = "https://downloads.rclone.org/rclone-current-windows-amd64.zip"; Zip = "rclone.zip"; ExtractDir = "rclone"; Exe = "rclone.exe" }
)

$toolsDir = "$PSScriptRoot\..\..\media-utility-web-tools"
if (!(Test-Path $toolsDir)) {
    New-Item -ItemType Directory -Path $toolsDir | Out-Null
}

foreach ($tool in $tools) {
    $zipPath = Join-Path $toolsDir $tool.Zip
    $extractPath = Join-Path $toolsDir $tool.ExtractDir

    if ($tool.Name -eq "yt-dlp") {
        if (Test-Path $zipPath) {
            Write-Host "$($tool.Name) already downloaded. Skipping."
            continue
        }
        Write-Host "Downloading $($tool.Name)..."
        Invoke-WebRequest -Uri $tool.Url -OutFile $zipPath
        Write-Host "$($tool.Name) download complete."
        continue
    }

    $existingExe = Get-ChildItem -Path $extractPath -Recurse -Filter $tool.Exe -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($existingExe) {
        Write-Host "$($tool.Name) already extracted. Skipping."
        continue
    }

    if (!(Test-Path $zipPath)) {
        Write-Host "Downloading $($tool.Name)..."
        Invoke-WebRequest -Uri $tool.Url -OutFile $zipPath
    } else {
        Write-Host "$($tool.Name) archive already exists."
    }

    Write-Host "Extracting $($tool.Name)..."
    Expand-Archive -Path $zipPath -DestinationPath $extractPath -Force
    Write-Host "$($tool.Name) extraction complete."
}

Write-Host "Tool setup complete."
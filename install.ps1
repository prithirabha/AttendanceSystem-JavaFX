# Downloads the latest Attendance System release for Windows, checks it and installs it for you.
#   irm https://raw.githubusercontent.com/prithirabha/AttendanceSystem-JavaFX/main/install.ps1 | iex
# Set $env:ATTENDANCE_VERSION = "1.2.1" first to install that version instead of the latest.
# ATTENDANCE_DOWNLOAD_URL replaces the GitHub releases address, for a mirror.
# No administrator rights are needed: the app goes to your own Programs folder.

# Everything runs inside this function, so a download cut off halfway runs nothing
function Install-AttendanceSystem {
    $ErrorActionPreference = 'Stop'
    $ProgressPreference = 'SilentlyContinue'  # The progress bar makes downloads much slower
    # Older Windows PowerShell may not offer TLS 1.2 by itself, and GitHub accepts nothing older
    [Net.ServicePointManager]::SecurityProtocol =
        [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
    $repo = 'prithirabha/AttendanceSystem-JavaFX'

    if (-not [Environment]::Is64BitOperatingSystem) {
        throw 'There is only a build for 64-bit Windows.'
    }
    $version = $env:ATTENDANCE_VERSION
    if (-not $version) {
        $version = (Invoke-RestMethod "https://api.github.com/repos/$repo/releases/latest").tag_name
    }
    $version = $version.TrimStart('v')
    $downloadBase = $env:ATTENDANCE_DOWNLOAD_URL
    if (-not $downloadBase) { $downloadBase = "https://github.com/$repo/releases/download" }
    $downloadUrl = "$downloadBase/v$version"
    $file = "AttendanceSystem-$version-windows-x64.zip"

    $workDir = Join-Path ([IO.Path]::GetTempPath()) ([IO.Path]::GetRandomFileName())
    New-Item -ItemType Directory $workDir | Out-Null
    try {
        Write-Host "Downloading $file"
        Invoke-WebRequest "$downloadUrl/$file" -OutFile "$workDir\$file" -UseBasicParsing
        Invoke-WebRequest "$downloadUrl/SHA256SUMS" -OutFile "$workDir\SHA256SUMS" -UseBasicParsing
        $line = Get-Content "$workDir\SHA256SUMS" |
            Where-Object { $_ -match "^[0-9a-f]{64}\s+\*?$([regex]::Escape($file))$" }
        if (-not $line) { throw "SHA256SUMS has no line for $file" }
        $expected = ($line -split '\s+')[0]
        if ((Get-FileHash "$workDir\$file" -Algorithm SHA256).Hash -ne $expected) {
            throw "$file doesn't match its SHA-256 checksum, so it was not installed."
        }

        $appDir = Join-Path $env:LOCALAPPDATA 'Programs\AttendanceSystem'
        $shortcut = Join-Path ([Environment]::GetFolderPath('Programs')) 'Attendance System.lnk'
        $uninstallKey = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\AttendanceSystem'

        Expand-Archive "$workDir\$file" -DestinationPath $workDir
        # An older version is replaced as a whole, so no old library is left behind
        if (Test-Path $appDir) { Remove-Item $appDir -Recurse -Force }
        New-Item -ItemType Directory (Split-Path $appDir) -Force | Out-Null
        Move-Item "$workDir\AttendanceSystem" $appDir
        Get-ChildItem $appDir -Recurse -File | Unblock-File

        $link = (New-Object -ComObject WScript.Shell).CreateShortcut($shortcut)
        $link.TargetPath = "$appDir\AttendanceSystem.exe"
        $link.WorkingDirectory = $appDir
        $link.Save()

        # Your database and photos live in another folder, so uninstalling keeps them.
        # A ' in a path (a user name like O'Brien) is doubled to stay inside the quotes.
        $quotedShortcut = $shortcut.Replace("'", "''")
        $quotedAppDir = $appDir.Replace("'", "''")
        Set-Content "$appDir\uninstall.ps1" @"
Remove-Item '$quotedShortcut' -Force -ErrorAction SilentlyContinue
Remove-Item '$uninstallKey' -Recurse -Force -ErrorAction SilentlyContinue
Set-Location `$env:TEMP
Remove-Item '$quotedAppDir' -Recurse -Force
Write-Host 'Attendance System is uninstalled.'
"@
        # Lists the app under Settings > Apps, so it can be removed from there too
        New-Item $uninstallKey -Force | Out-Null
        $uninstallCommand = "powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$appDir\uninstall.ps1`""
        Set-ItemProperty $uninstallKey DisplayName 'Attendance System'
        Set-ItemProperty $uninstallKey DisplayVersion $version
        Set-ItemProperty $uninstallKey Publisher 'BSMI'
        Set-ItemProperty $uninstallKey DisplayIcon "$appDir\AttendanceSystem.exe"
        Set-ItemProperty $uninstallKey InstallLocation $appDir
        Set-ItemProperty $uninstallKey UninstallString $uninstallCommand
        Set-ItemProperty $uninstallKey NoModify 1 -Type DWord
        Set-ItemProperty $uninstallKey NoRepair 1 -Type DWord

        Write-Host "Attendance System $version is installed in $appDir."
        Write-Host 'Start it from the Start menu. Log in as admin / admin123 to see the demo data.'
        Write-Host 'To remove it, use Settings > Apps, or run:'
        Write-Host "  powershell -ExecutionPolicy Bypass -File `"$appDir\uninstall.ps1`""
    } finally {
        Remove-Item $workDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Install-AttendanceSystem

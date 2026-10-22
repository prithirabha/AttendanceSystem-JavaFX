## Download

Pick the file for your computer. Each one brings its own Java runtime, so nothing else needs to be
installed.

| Computer | File | How to start it |
|---|---|---|
| Ubuntu, Debian | [AttendanceSystem-@VERSION@-linux-amd64.deb](@DOWNLOAD@/AttendanceSystem-@VERSION@-linux-amd64.deb) | `sudo apt install ./AttendanceSystem-@VERSION@-linux-amd64.deb`, then open it from the app menu |
| Any Linux, no install | [AttendanceSystem-@VERSION@-linux-x64.AppImage](@DOWNLOAD@/AttendanceSystem-@VERSION@-linux-x64.AppImage) | `chmod +x AttendanceSystem-@VERSION@-linux-x64.AppImage`, then run it |
| Any Linux, installed | [AttendanceSystem-@VERSION@-linux-x64.tar.gz](@DOWNLOAD@/AttendanceSystem-@VERSION@-linux-x64.tar.gz) | Unpack it and run `sudo AttendanceSystem/install.sh` (or without `sudo` for just you), then open it from the app menu |
| Windows | [AttendanceSystem-@VERSION@-windows-x64.zip](@DOWNLOAD@/AttendanceSystem-@VERSION@-windows-x64.zip) | Unzip it and run `AttendanceSystem\AttendanceSystem.exe` |
| Mac with Apple silicon | [AttendanceSystem-@VERSION@-macos-arm64.dmg](@DOWNLOAD@/AttendanceSystem-@VERSION@-macos-arm64.dmg) | Open it and drag the app to Applications |

### Linux libraries

The app needs GTK 3 and libXtst, which every Linux desktop has. On a minimal system, install them
first:

- Fedora: `sudo dnf install gtk3 libXtst`
- openSUSE: `sudo zypper install libgtk-3-0 libgthread-2_0-0 libXtst6`
- Arch: `sudo pacman -S gtk3 libxtst`

To remove an installed copy, run `/opt/AttendanceSystem/uninstall.sh` with `sudo`
(`~/.local/opt/AttendanceSystem/uninstall.sh` for a copy installed just for you). Your data is
kept.

### The first start on Windows and macOS

The apps aren't signed yet, so the first start shows a warning about an unknown developer.

- **Windows:** click **More info**, then **Run anyway**.
- **macOS:** open the app once and close the warning. Then open **System Settings**, go to
  **Privacy & Security**, scroll down and click **Open Anyway**. If that button doesn't appear, run
  `xattr -dr com.apple.quarantine /Applications/AttendanceSystem.app` in Terminal.

### Logging in

The first start creates a database with demo data. Log in as `admin` / `admin123`, open
**Attendance**, type `DEMO-1001` and press Enter to check a student in. Updating to a new version
keeps your data.

### Checking a download

These are the SHA-256 checksums of the files, also attached as `SHA256SUMS`. To check, put
`SHA256SUMS` next to the files you downloaded and run `sha256sum -c --ignore-missing SHA256SUMS`
(macOS: `shasum -a 256 -c --ignore-missing SHA256SUMS`).

```
@CHECKSUMS@
```

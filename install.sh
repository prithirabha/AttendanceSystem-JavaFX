#!/bin/sh
# Downloads the latest Attendance System release for this computer, checks it and installs it.
#   curl -fsSL https://raw.githubusercontent.com/prithirabha/AttendanceSystem-JavaFX/main/install.sh | sh
# Options, after "sh -s --":
#   --user     on Debian or Ubuntu, install just for you from the .tar.gz instead of the .deb
#   --version 1.2.1   install that version instead of the latest
# ATTENDANCE_DOWNLOAD_URL replaces the GitHub releases address, for a mirror.
set -eu

repo_url=https://github.com/prithirabha/AttendanceSystem-JavaFX

fail() {
    echo "Error: $*" >&2
    exit 1
}

latest_version() {
    # GitHub redirects /releases/latest to /releases/tag/v<version>, which needs no API call
    tag_url=$(curl -fsSLI -o /dev/null -w '%{url_effective}' "$repo_url/releases/latest") ||
        fail "could not reach $repo_url"
    case "$tag_url" in
        */tag/v*) echo "${tag_url##*/tag/v}" ;;
        *) fail "no release found at $repo_url/releases" ;;
    esac
}

sha256_of() {
    if command -v sha256sum >/dev/null; then
        sha256sum "$1" | cut -d ' ' -f 1
    else
        shasum -a 256 "$1" | cut -d ' ' -f 1
    fi
}

download_and_check() {
    file=$1
    echo "Downloading $file"
    curl -fL --progress-bar -o "$work_dir/$file" "$download_url/$file" ||
        fail "could not download $download_url/$file"
    curl -fsSL -o "$work_dir/SHA256SUMS" "$download_url/SHA256SUMS" ||
        fail "could not download $download_url/SHA256SUMS"
    expected=
    # Plain sh reads the list, as a minimal system may have no awk
    while read -r sum name; do
        if [ "$name" = "$file" ] || [ "$name" = "*$file" ]; then
            expected=$sum
        fi
    done < "$work_dir/SHA256SUMS"
    [ -n "$expected" ] || fail "SHA256SUMS has no line for $file"
    [ "$(sha256_of "$work_dir/$file")" = "$expected" ] ||
        fail "$file doesn't match its SHA-256 checksum, so it was not installed"
}

as_root() {
    if [ "$(id -u)" -eq 0 ]; then
        "$@"
    else
        sudo "$@"
    fi
}

can_use_deb() {
    command -v apt-get >/dev/null && command -v dpkg >/dev/null &&
        { [ "$(id -u)" -eq 0 ] || command -v sudo >/dev/null; }
}

install_on_linux() {
    case "$(uname -m)" in
        x86_64 | amd64) ;;
        *) fail "there is only a build for 64-bit Intel or AMD Linux, not $(uname -m)" ;;
    esac
    if [ "$for_user_only" = no ] && can_use_deb; then
        file="AttendanceSystem-$version-linux-amd64.deb"
        download_and_check "$file"
        # apt needs a path with a slash to treat the name as a file, and pulls in GTK if missing
        as_root apt-get install -y "$work_dir/$file"
        echo "Attendance System $version is installed. Start it from the app menu."
    else
        file="AttendanceSystem-$version-linux-x64.tar.gz"
        download_and_check "$file"
        tar -xzf "$work_dir/$file" -C "$work_dir"
        # As root this installs for everyone in /opt, otherwise just for you in ~/.local/opt
        sh "$work_dir/AttendanceSystem/install.sh"
    fi
}

install_on_mac() {
    [ "$(uname -m)" = arm64 ] || fail "there is only a build for Macs with Apple silicon"
    file="AttendanceSystem-$version-macos-arm64.dmg"
    download_and_check "$file"
    mount_dir="$work_dir/mount"
    mkdir "$mount_dir"
    hdiutil attach -quiet -nobrowse -readonly -mountpoint "$mount_dir" "$work_dir/$file"
    if [ -w /Applications ]; then
        apps_dir=/Applications
    else
        apps_dir="$HOME/Applications"
        mkdir -p "$apps_dir"
    fi
    rm -rf "$apps_dir/AttendanceSystem.app"
    copied=yes
    cp -R "$mount_dir/AttendanceSystem.app" "$apps_dir/" || copied=no
    hdiutil detach -quiet "$mount_dir"
    [ "$copied" = yes ] || fail "could not copy the app to $apps_dir"
    echo "Attendance System $version is installed in $apps_dir. Start it from Launchpad."
}

main() {
    for_user_only=no
    version=
    while [ $# -gt 0 ]; do
        case "$1" in
            --user) for_user_only=yes ;;
            --version)
                [ $# -ge 2 ] || fail "--version needs a version like 1.2.1"
                version=${2#v}
                shift
                ;;
            *) fail "unknown option $1 (use --user or --version <version>)" ;;
        esac
        shift
    done
    command -v curl >/dev/null || fail "curl is needed"
    [ -n "$version" ] || version=$(latest_version)
    download_url="${ATTENDANCE_DOWNLOAD_URL:-$repo_url/releases/download}/v$version"

    work_dir=$(mktemp -d)
    trap 'rm -rf "$work_dir"' EXIT
    case "$(uname -s)" in
        Linux) install_on_linux ;;
        Darwin) install_on_mac ;;
        *) fail "this script is for Linux and macOS. On Windows, run install.ps1 (see $repo_url)" ;;
    esac
    echo "Log in as admin / admin123 to see the demo data."
}

# Everything runs from here, so a download cut off halfway runs nothing
main "$@"

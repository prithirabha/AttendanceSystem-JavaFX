#!/bin/sh
# Turns the app folder from `./mvnw -Pdist package` into an AppImage, one file that runs on any
# x86-64 Linux without installing.
#   packaging/linux/build-appimage.sh <app folder> <output file>
set -eu

# Pinned with their checksums, so a changed download stops the build
tool_url=https://github.com/AppImage/appimagetool/releases/download/1.9.1/appimagetool-x86_64.AppImage
tool_sha256=ed4ce84f0d9caff66f50bcca6ff6f35aae54ce8135408b3fa33abfc3cb384eb0
runtime_url=https://github.com/AppImage/type2-runtime/releases/download/20251108/runtime-x86_64
runtime_sha256=2fca8b443c92510f1483a883f60061ad09b46b978b2631c807cd873a47ec260d

app_folder=$1
output_file=$2
script_dir=$(cd "$(dirname "$0")" && pwd)
work_dir=$(mktemp -d)
trap 'rm -rf "$work_dir"' EXIT

curl -fsSLo "$work_dir/appimagetool" "$tool_url"
curl -fsSLo "$work_dir/runtime" "$runtime_url"
printf '%s  %s\n' "$tool_sha256" "$work_dir/appimagetool" "$runtime_sha256" "$work_dir/runtime" |
    sha256sum -c --quiet
chmod +x "$work_dir/appimagetool"

# The AppImage starts AppRun and takes its menu entry and icon from the top of the folder.
# AppRun checks the system libraries first, then starts the app.
cp -R "$app_folder" "$work_dir/AppDir"
rm -f "$work_dir/AppDir/install.sh"
cp "$script_dir/AppRun" "$script_dir/check-libraries.sh" "$work_dir/AppDir/"
chmod +x "$work_dir/AppDir/AppRun"

# appimagetool is an AppImage itself; unpacking it first means it doesn't need FUSE on the runner
APPIMAGE_EXTRACT_AND_RUN=1 ARCH=x86_64 \
    "$work_dir/appimagetool" --no-appstream --runtime-file "$work_dir/runtime" \
    "$work_dir/AppDir" "$output_file"

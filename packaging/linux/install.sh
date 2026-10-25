#!/bin/sh
# Installs Attendance System from this folder, with a menu entry and an uninstall script.
#   sudo ./install.sh   for everyone, in /opt/AttendanceSystem
#   ./install.sh        for you only, in ~/.local/opt/AttendanceSystem
# The database lives in a separate per-user folder, so reinstalling or uninstalling keeps it.
set -eu

source_dir=$(cd "$(dirname "$0")" && pwd)
if [ "$(id -u)" -eq 0 ]; then
    app_dir=/opt/AttendanceSystem
    share_dir=/usr/share
    bin_dir=/usr/local/bin
else
    app_dir="$HOME/.local/opt/AttendanceSystem"
    share_dir="${XDG_DATA_HOME:-$HOME/.local/share}"
    bin_dir="$HOME/.local/bin"
fi
desktop_file="$share_dir/applications/attendancesystem.desktop"
icon_file="$share_dir/icons/hicolor/scalable/apps/attendancesystem.svg"
command_link="$bin_dir/attendancesystem"

if [ ! -x "$source_dir/bin/AttendanceSystem" ]; then
    echo "Run this script from the unpacked AttendanceSystem folder." >&2
    exit 1
fi
if [ "$source_dir" = "$app_dir" ]; then
    echo "Attendance System is already installed in $app_dir." >&2
    exit 1
fi

# An older version is replaced as a whole, so no old library is left behind
rm -rf "$app_dir"
mkdir -p "$(dirname "$app_dir")" "$(dirname "$desktop_file")" "$(dirname "$icon_file")" "$bin_dir"
cp -R "$source_dir" "$app_dir"
rm "$app_dir/install.sh"
cp "$source_dir/attendancesystem.svg" "$icon_file"
sed -e "s|^Exec=.*|Exec=\"$app_dir/bin/AttendanceSystem\"|" -e "s|^Icon=.*|Icon=$icon_file|" \
    "$source_dir/attendancesystem.desktop" > "$desktop_file"
ln -sf "$app_dir/bin/AttendanceSystem" "$command_link"

cat > "$app_dir/uninstall.sh" <<EOF
#!/bin/sh
# Removes Attendance System. Your database and photos are kept.
rm -rf "$app_dir" "$desktop_file" "$icon_file" "$command_link"
echo "Attendance System is uninstalled."
EOF
chmod +x "$app_dir/uninstall.sh"
command -v update-desktop-database >/dev/null && update-desktop-database -q "$(dirname "$desktop_file")" || true

echo "Attendance System is installed in $app_dir."
echo "Start it from the app menu, or run: attendancesystem"
echo "To remove it, run: $app_dir/uninstall.sh"

# Only a warning here: the libraries can be installed after the app
if ! message=$(sh "$source_dir/check-libraries.sh"); then
    echo
    echo "$message"
fi

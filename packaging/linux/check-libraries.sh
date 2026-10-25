#!/bin/sh
# Checks for the system libraries JavaFX draws its windows with. Every desktop has them, but a
# minimal system may not, and then Java stops with a long stack trace instead of a clear message.
#   sh check-libraries.sh   prints what to install and exits 1 when a library is missing
set -eu

libraries=$(ldconfig -p 2>/dev/null || /sbin/ldconfig -p 2>/dev/null || true)
# Without a library list (no ldconfig, or a distro without its cache) there is nothing to judge,
# and a false alarm would stop an app that works
[ -n "$libraries" ] || exit 0

missing=
for library in libgtk-3.so.0 libgthread-2.0.so.0 libXtst.so.6; do
    printf '%s\n' "$libraries" | grep -qF "$library" || missing="$missing $library"
done
[ -n "$missing" ] || exit 0

cat <<EOF
Attendance System needs these libraries, which seem to be missing:$missing
Install them with your package manager, for example:
  Debian, Ubuntu: sudo apt install libgtk-3-0 libglib2.0-0 libxtst6
  Fedora: sudo dnf install gtk3 glib2 libXtst
  openSUSE: sudo zypper install libgtk-3-0 libgthread-2_0-0 libXtst6
  Arch: sudo pacman -S gtk3 glib2 libxtst
EOF
exit 1

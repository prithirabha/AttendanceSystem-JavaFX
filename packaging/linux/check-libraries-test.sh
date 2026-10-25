#!/bin/sh
# Tests check-libraries.sh and the AppImage's AppRun against a fake ldconfig, which lists only the
# libraries in $FAKE_LIBRARIES, and a fake zenity that records the message it was given.
#   sh packaging/linux/check-libraries-test.sh
set -eu

here=$(cd "$(dirname "$0")" && pwd)
check="$here/check-libraries.sh"
all_libraries="libgtk-3.so.0 libgthread-2.0.so.0 libXtst.so.6"
failures=0

state=$(mktemp -d)
trap 'rm -rf "$state"' EXIT
export FAKE_STATE="$state" FAKE_LIBRARIES="$all_libraries"
mkdir "$state/bin"

# The same line format as the real `ldconfig -p`
cat > "$state/bin/ldconfig" <<'EOF'
#!/bin/sh
for library in $FAKE_LIBRARIES; do
    printf '\t%s (libc6,x86-64) => /usr/lib64/%s\n' "$library" "$library"
done
EOF
cat > "$state/bin/zenity" <<'EOF'
#!/bin/sh
printf '%s\n' "$@" > "$FAKE_STATE/zenity"
EOF
chmod +x "$state/bin/ldconfig" "$state/bin/zenity"
PATH="$state/bin:$PATH"

# A copy of the AppImage's folder whose app only records the arguments it got
mkdir -p "$state/AppDir/bin"
cp "$here/AppRun" "$check" "$state/AppDir/"
cat > "$state/AppDir/bin/AttendanceSystem" <<'EOF'
#!/bin/sh
printf '%s\n' "$@" > "$FAKE_STATE/app-started-with"
EOF
chmod +x "$state/AppDir/AppRun" "$state/AppDir/bin/AttendanceSystem"

pass_if() {
    name=$1
    shift
    if "$@"; then
        echo "PASS $name"
    else
        echo "FAIL $name"
        failures=$((failures + 1))
    fi
}

passes_with() {
    FAKE_LIBRARIES=$1 sh "$check" >/dev/null
}

# Fails and names the missing library and an install command
fails_naming() {
    if output=$(FAKE_LIBRARIES=$1 sh "$check"); then
        return 1
    fi
    printf '%s\n' "$output" | grep -qF "missing: $2" &&
        printf '%s\n' "$output" | grep -qF "sudo zypper install"
}

pass_if "all libraries present passes" passes_with "$all_libraries"
pass_if "no ldconfig output passes, as nothing can be judged" passes_with ""
pass_if "missing GTK 3 fails and names it" \
    fails_naming "libgthread-2.0.so.0 libXtst.so.6" libgtk-3.so.0
pass_if "missing libgthread fails and names it" \
    fails_naming "libgtk-3.so.0 libXtst.so.6" libgthread-2.0.so.0
pass_if "missing libXtst fails and names it" \
    fails_naming "libgtk-3.so.0 libgthread-2.0.so.0" libXtst.so.6
pass_if "two missing libraries are both named" \
    fails_naming "libgtk-3.so.0" "libgthread-2.0.so.0 libXtst.so.6"

sh "$state/AppDir/AppRun" --one "two words" >/dev/null 2>&1
pass_if "AppRun starts the app with its arguments when nothing is missing" \
    [ "$(cat "$state/app-started-with")" = "--one
two words" ]
pass_if "AppRun shows no window when nothing is missing" [ ! -e "$state/zenity" ]

rm "$state/app-started-with"
apprun_status=0
FAKE_LIBRARIES="libgtk-3.so.0 libXtst.so.6" sh "$state/AppDir/AppRun" \
    >/dev/null 2>"$state/apprun-errors" || apprun_status=$?
pass_if "AppRun stops with exit 1 when a library is missing" [ "$apprun_status" -eq 1 ]
pass_if "AppRun doesn't start the app then" [ ! -e "$state/app-started-with" ]
pass_if "AppRun prints the message on the terminal" \
    grep -qF "libgthread-2.0.so.0" "$state/apprun-errors"
pass_if "AppRun also shows the message in a window, without markup" \
    grep -qxF -- "--no-markup" "$state/zenity"
pass_if "the window names the missing library" grep -qF "libgthread-2.0.so.0" "$state/zenity"

ln -s "$state/AppDir/AppRun" "$state/linked-AppRun"
rm -f "$state/app-started-with"
sh "$state/linked-AppRun" >/dev/null 2>&1
pass_if "AppRun started through a link still finds its folder" [ -e "$state/app-started-with" ]

[ "$failures" -eq 0 ] || { echo "$failures failed"; exit 1; }
echo "All passed"

#!/bin/sh
# Tests prune-old-packages.sh against a fake gh that keeps its releases in a throwaway folder.
#   sh .github/scripts/prune-old-packages-test.sh
set -eu

script=$(cd "$(dirname "$0")" && pwd)/prune-old-packages.sh
repo=owner/project
note="> The downloads of this version were removed when a newer version came out. Get the \
[latest release](https://github.com/$repo/releases/latest), or build this version from its \
source code below."
failures=0

state=$(mktemp -d)
trap 'rm -rf "$state"' EXIT
export FAKE_GH_STATE="$state" GITHUB_SERVER_URL=https://github.com
mkdir "$state/bin" "$state/releases"

# Each release is a folder with a file per download and its notes in .body
cat > "$state/bin/gh" <<'EOF'
#!/bin/sh
set -eu
dir=$FAKE_GH_STATE/releases
echo "$*" >> "$FAKE_GH_STATE/calls"
case "$1 $2" in
    "release list") ls "$dir" ;;
    "release view")
        case "$*" in
            *"--json assets"*) ls "$dir/$3" ;;
            *) cat "$dir/$3/.body" ;;
        esac
        ;;
    "release edit") cp "$7" "$dir/$3/.body" ;;
    "release delete-asset") rm "$dir/$3/$4" ;;
    *) echo "fake gh can't do: $*" >&2; exit 1 ;;
esac
EOF
chmod +x "$state/bin/gh"
PATH="$state/bin:$PATH"

release() {
    tag=$1
    mkdir "$state/releases/$tag"
    printf 'Notes of %s' "$tag" > "$state/releases/$tag/.body"
    for file in "AttendanceSystem-${tag#v}-windows-x64.zip" SHA256SUMS; do
        : > "$state/releases/$tag/$file"
    done
}

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

files_of() {
    ls "$state/releases/$1" | tr '\n' ' '
}

body_is() {
    [ "$(cat "$state/releases/$1/.body")" = "$2" ]
}

prune() {
    : > "$state/calls"
    sh "$script" "$1" "$repo" > "$state/out" 2> "$state/err"
}

release v1.2.0
release v1.9.0
release v1.10.0
release nightly

prune v1.10.0
pass_if "older releases lose their downloads" \
    [ "$(files_of v1.2.0)$(files_of v1.9.0)" = "" ]
pass_if "the newest release keeps its downloads, sorted by version and not as text" \
    [ "$(files_of v1.10.0)" = "AttendanceSystem-1.10.0-windows-x64.zip SHA256SUMS " ]
pass_if "an older release's notes start with a pointer to the latest release" \
    body_is v1.9.0 "$note

Notes of v1.9.0"
pass_if "the newest release's notes stay as they are" body_is v1.10.0 "Notes of v1.10.0"
pass_if "a tag that isn't a version is left alone" \
    [ "$(files_of nightly)" = "AttendanceSystem-nightly-windows-x64.zip SHA256SUMS " ]
pass_if "each removed file is named in the log" \
    grep -q -x -F "Removing SHA256SUMS from v1.2.0" "$state/out"

prune v1.10.0
pass_if "a second run changes nothing" \
    sh -c '! grep -q -E "^release (edit|delete-asset)" "$1"' sh "$state/calls"

release v1.9.1
prune v1.9.1
pass_if "a fix for an older version removes nothing" \
    [ "$(files_of v1.9.1)$(files_of v1.10.0)" = "AttendanceSystem-1.9.1-windows-x64.zip SHA256SUMS \
AttendanceSystem-1.10.0-windows-x64.zip SHA256SUMS " ]
pass_if "it says why nothing was removed" \
    grep -q -F "v1.9.1 is older than v1.10.0, so no downloads are removed" "$state/out"

release v1.10.1
rm "$state/releases/v1.10.0/SHA256SUMS"
printf '%s\n\nNotes of v1.10.0' "$note" > "$state/releases/v1.10.0/.body"
prune v1.10.1
pass_if "a run that stopped halfway is finished without a second note" \
    [ "$(files_of v1.10.0)" = "" ]
pass_if "a run that stopped halfway leaves one note" body_is v1.10.0 "$note

Notes of v1.10.0"

pass_if "an unknown release is an error" \
    sh -c '! sh "$1" v9.9.9 "$2" 2> "$3"' sh "$script" "$repo" "$state/err"
pass_if "the error names the release" grep -q -F "no release v9.9.9" "$state/err"
pass_if "a missing argument shows the usage" \
    sh -c 'sh "$1" v1.10.1 2>&1 | grep -q Usage:' sh "$script"

[ "$failures" -eq 0 ] || { echo "$failures failed"; exit 1; }
echo "All passed"

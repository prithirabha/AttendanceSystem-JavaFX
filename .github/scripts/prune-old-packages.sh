#!/bin/sh
# Removes the downloads from every release older than this one, so only the newest release has
# packages. An old release keeps its notes and GitHub's own source code download, and gets a line
# at the top of its notes that points to the latest release.
#   sh .github/scripts/prune-old-packages.sh v1.2.3 prithirabha/AttendanceSystem-JavaFX
# gh needs a token that can write to the repository (GH_TOKEN).
set -eu

[ $# -eq 2 ] || { echo "Usage: $0 <tag> <owner/repository>" >&2; exit 2; }
tag=$1
repo=$2
server=${GITHUB_SERVER_URL:-https://github.com}
note="> The downloads of this version were removed when a newer version came out. Get the \
[latest release]($server/$repo/releases/latest), or build this version from its source code below."

# Only tags like v1.2.3 are releases; sort -V puts v1.10.0 after v1.9.0
releases=$(gh release list --repo "$repo" --exclude-drafts --limit 1000 \
    --json tagName --jq '.[].tagName' | grep -E '^v[1-9][0-9]*\.[0-9]+\.[0-9]+$' | sort -V) || true
if ! printf '%s\n' "$releases" | grep -q -x -F -- "$tag"; then
    echo "Error: no release $tag" >&2
    exit 1
fi

newest=$(printf '%s\n' "$releases" | tail -n 1)
if [ "$newest" != "$tag" ]; then
    # A fix for an older version mustn't take the downloads away from the newest one
    echo "$tag is older than $newest, so no downloads are removed"
    exit 0
fi

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

for old in $(printf '%s\n' "$releases" | grep -v -x -F -- "$tag"); do
    assets=$(gh release view "$old" --repo "$repo" --json assets --jq '.assets[].name')
    [ -n "$assets" ] || continue
    # The note goes first, so a run that stops halfway still explains the missing files
    body=$(gh release view "$old" --repo "$repo" --json body --jq .body)
    case "$body" in
        "$note"*) ;;
        *)
            printf '%s\n\n%s\n' "$note" "$body" > "$work/notes.md"
            gh release edit "$old" --repo "$repo" --notes-file "$work/notes.md" >/dev/null
            ;;
    esac
    printf '%s\n' "$assets" | while IFS= read -r asset; do
        echo "Removing $asset from $old"
        gh release delete-asset "$old" "$asset" --repo "$repo" --yes
    done
done

#!/bin/sh
# Writes the "What's new" and "Fixes" part of the release notes from the commits since the last
# release, as Markdown on stdout.
#   sh .github/scripts/release-changes.sh v1.2.2 https://github.com/prithirabha/AttendanceSystem-JavaFX
# A commit picks its section with a last line "Changelog: new" or "Changelog: fix" in its message.
# Without one, a title starting with Add goes under What's new, Fix under Fixes and Bump (Dependabot)
# under Updates. Everything else goes under Other changes.
set -eu

[ $# -eq 2 ] || { echo "Usage: $0 <tag> <repository URL>" >&2; exit 2; }
tag=$1
repo_url=$2

git rev-parse --verify --quiet "$tag^{commit}" >/dev/null || { echo "Error: no tag $tag" >&2; exit 1; }

section_of() {
    commit=$1
    title=$2
    # The first Changelog line wins; its value is read case-insensitively
    choice=$(git log -1 --format='%(trailers:key=Changelog,valueonly)' "$commit" |
        sed -n '1s/[[:space:]]*$//p' | tr '[:upper:]' '[:lower:]')
    case "$choice" in
        new | fix) echo "$choice"; return ;;
        "") ;;
        *) echo "::warning::$commit has \"Changelog: $choice\"; use new or fix. It is listed under Other changes." >&2 ;;
    esac
    case "$title" in
        "Add "*) echo new ;;
        "Fix "*) echo fix ;;
        "Bump "*) echo updates ;;
        *) echo other ;;
    esac
}

print_section() {
    heading=$1
    file=$2
    if [ -s "$file" ]; then
        printf '## %s\n\n' "$heading"
        cat "$file"
        echo
    fi
}

# The release before this one. The first release lists every commit.
if previous=$(git describe --tags --abbrev=0 --match 'v[1-9]*' "$tag^" 2>/dev/null); then
    range="$previous..$tag"
    full_list="[$previous...$tag]($repo_url/compare/$previous...$tag)"
else
    range=$tag
    full_list="[all commits up to $tag]($repo_url/commits/$tag)"
fi

lists=$(mktemp -d)
trap 'rm -rf "$lists"' EXIT
: > "$lists/new"; : > "$lists/fix"; : > "$lists/updates"; : > "$lists/other"

# Oldest first. A merge only joins commits that are listed on their own.
for commit in $(git rev-list --reverse --no-merges "$range"); do
    title=$(git log -1 --format=%s "$commit")
    short=$(git rev-parse --short "$commit")
    echo "- $title ([$short]($repo_url/commit/$commit))" >> "$lists/$(section_of "$commit" "$title")"
done

print_section "What's new" "$lists/new"
print_section "Fixes" "$lists/fix"
print_section "Updates" "$lists/updates"
print_section "Other changes" "$lists/other"
echo "Full list of changes: $full_list"

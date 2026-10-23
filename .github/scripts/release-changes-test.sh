#!/bin/sh
# Tests release-changes.sh against a throwaway git repo with known commits and tags.
#   sh .github/scripts/release-changes-test.sh
set -eu

script=$(cd "$(dirname "$0")" && pwd)/release-changes.sh
repo_url=https://example.com/repo
failures=0

repo=$(mktemp -d)
trap 'rm -rf "$repo"' EXIT
cd "$repo"
# Leave the user's own git settings (signing, hooks, default branch) out of it
export GIT_CONFIG_NOSYSTEM=1 GIT_CONFIG_GLOBAL=/dev/null
export GIT_AUTHOR_NAME=Tester GIT_AUTHOR_EMAIL=tester@example.com
export GIT_COMMITTER_NAME=Tester GIT_COMMITTER_EMAIL=tester@example.com
git init -q
main=$(git symbolic-ref --short HEAD)

commit() {
    title=$1
    shift
    git commit -q --allow-empty -m "$title" "$@"
}

# Puts the commit links in the expected text, so the cases can name commits by their title
with_links() {
    while IFS= read -r line; do
        case "$line" in
            "- "*)
                title=${line#- }
                full=$(git log --all --format=%H --fixed-strings --grep="$title" -1)
                echo "- $title ([$(git rev-parse --short "$full")]($repo_url/commit/$full))"
                ;;
            *) echo "$line" ;;
        esac
    done
}

check() {
    name=$1
    tag=$2
    expected=$(with_links)
    actual=$(sh "$script" "$tag" "$repo_url" 2>"$repo.err") || true
    if [ "$actual" = "$expected" ]; then
        echo "PASS $name"
    else
        echo "FAIL $name"
        printf '%s\n' "$expected" > "$repo.expected"
        printf '%s\n' "$actual" | diff -u "$repo.expected" - || true
        failures=$((failures + 1))
    fi
}

check_error() {
    name=$1
    wanted=$2
    shift 2
    if sh "$script" "$@" >/dev/null 2>"$repo.err"; then
        echo "FAIL $name: it succeeded"
        failures=$((failures + 1))
    elif grep -q -F -- "$wanted" "$repo.err"; then
        echo "PASS $name"
    else
        echo "FAIL $name: wanted \"$wanted\", got: $(cat "$repo.err")"
        failures=$((failures + 1))
    fi
}

commit "Start the project"
commit "Add a login screen"
git tag v1.0.0

check "the first release lists every commit" v1.0.0 <<EOF
## What's new

- Add a login screen

## Other changes

- Start the project

Full list of changes: [all commits up to v1.0.0]($repo_url/commits/v1.0.0)
EOF

commit "Show the student count" -m "Changelog: new"
# -s adds Signed-off-by after it, as the commits in this repo have
commit "Keep the table inside the window" -m "Changelog: Fix" -s
commit "Fix a typo in the README"
commit "Bump org.h2database:h2 from 2.4 to 2.5"
commit "Add a draft that is no feature" -m "Changelog: fix"
commit "Tidy the build" -m "Changelog: feature"
git checkout -q -b side
commit "Let the scanner pick another port" -m "Changelog: new"
git checkout -q "$main"
git merge -q --no-ff -m "Merge branch 'side'" side
git tag -a -m "Version 1.1.0" v1.1.0

check "a release lists only the commits since the last one, by section" v1.1.0 <<EOF
## What's new

- Show the student count
- Let the scanner pick another port

## Fixes

- Keep the table inside the window
- Fix a typo in the README
- Add a draft that is no feature

## Updates

- Bump org.h2database:h2 from 2.4 to 2.5

## Other changes

- Tidy the build

Full list of changes: [v1.0.0...v1.1.0]($repo_url/compare/v1.0.0...v1.1.0)
EOF

if grep -q -F 'has "Changelog: feature"; use new or fix' "$repo.err"; then
    echo "PASS an unknown Changelog value gives a warning"
else
    echo "FAIL an unknown Changelog value gives a warning: got $(cat "$repo.err")"
    failures=$((failures + 1))
fi

commit "Fix the exit time" -m "Changelog: fix"
git tag not-a-release
git tag v1.1.1

check "empty sections are left out, and only v tags count as releases" v1.1.1 <<EOF
## Fixes

- Fix the exit time

Full list of changes: [v1.1.0...v1.1.1]($repo_url/compare/v1.1.0...v1.1.1)
EOF

check_error "an unknown tag is an error" "no tag v9.9.9" v9.9.9 "$repo_url"
check_error "a missing argument shows the usage" "Usage:" v1.1.1

rm -f "$repo.err" "$repo.expected"
[ "$failures" -eq 0 ] || { echo "$failures failed"; exit 1; }
echo "All passed"

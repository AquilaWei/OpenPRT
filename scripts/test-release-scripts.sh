#!/usr/bin/env bash
# Tests for changelog-section.sh and check-version.sh. Each test runs a script against a
# small fixture in a temporary directory and compares its output or exit code.
#
# Usage: scripts/test-release-scripts.sh   (exits 1 if any test fails)
set -uo pipefail

scripts="$(cd "$(dirname "$0")" && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
failures=0

cat > "$work/CHANGELOG.md" <<'MD'
# Changelog

Intro text.

## [0.2.0] - 2026-10-05

### Added

- Second release

## [0.1.31] - 2026-10-04

### Fixed

- Thirty-first

## [0.1.3] - 2026-10-02

## [0.1.0] - 2026-10-01

### Added

- First release
MD

expect_output() {
    local name="$1" expected="$2" actual="$3"
    if [[ "$actual" == "$expected" ]]; then
        echo "ok   $name"
    else
        echo "FAIL $name"
        echo "  expected: $(printf '%q' "$expected")"
        echo "  actual:   $(printf '%q' "$actual")"
        failures=$((failures + 1))
    fi
}

expect_exit() {
    local name="$1" expected="$2" actual="$3"
    expect_output "$name" "exit $expected" "exit $actual"
}

# changelog-section.sh

expect_output "section_inTheMiddle_printsItsBodyUpToTheNextVersion" \
    $'### Added\n\n- Second release' \
    "$("$scripts/changelog-section.sh" 0.2.0 "$work/CHANGELOG.md")"

expect_output "section_lastInTheFile_printsItsBodyToTheEnd" \
    $'### Added\n\n- First release' \
    "$("$scripts/changelog-section.sh" 0.1.0 "$work/CHANGELOG.md")"

expect_output "section_followedByAnEmptySection_stopsAtThatHeading" \
    $'### Fixed\n\n- Thirty-first' \
    "$("$scripts/changelog-section.sh" 0.1.31 "$work/CHANGELOG.md")"

"$scripts/changelog-section.sh" 9.9.9 "$work/CHANGELOG.md" > /dev/null 2>&1
expect_exit "section_missingVersion_fails" 1 $?

"$scripts/changelog-section.sh" 0.1.3 "$work/CHANGELOG.md" > /dev/null 2>&1
expect_exit "section_emptyWhileALongerVersionStartsWithIt_failsInsteadOfUsingTheLongerOne" 1 $?

# check-version.sh

mkdir "$work/match" "$work/mismatch"
printf 'VERSION_NAME=1.2.3\nVERSION_CODE=7\n' > "$work/match/gradle.properties"
printf '# App\n\n![version](https://img.shields.io/badge/version-1.2.3-blue)\n' > "$work/match/README.md"
printf 'VERSION_NAME=1.2.4\nVERSION_CODE=8\n' > "$work/mismatch/gradle.properties"
printf '# App\n\n![version](https://img.shields.io/badge/version-1.2.3-blue)\n' > "$work/mismatch/README.md"

(cd "$work/match" && "$scripts/check-version.sh") > /dev/null 2>&1
expect_exit "checkVersion_badgeMatchesGradleVersion_passes" 0 $?

(cd "$work/mismatch" && "$scripts/check-version.sh") > /dev/null 2>&1
expect_exit "checkVersion_badgeBehindGradleVersion_fails" 1 $?

(cd "$work/match" && "$scripts/check-version.sh" v1.2.3) > /dev/null 2>&1
expect_exit "checkVersion_tagMatchesGradleVersion_passes" 0 $?

(cd "$work/match" && "$scripts/check-version.sh" v1.2.2) > /dev/null 2>&1
expect_exit "checkVersion_tagDiffersFromGradleVersion_fails" 1 $?

if [[ $failures -gt 0 ]]; then
    echo "$failures test(s) failed"
    exit 1
fi
echo "all tests passed"

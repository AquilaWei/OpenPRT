#!/usr/bin/env bash
# Prints the body of one version's section in CHANGELOG.md, for use as GitHub release notes.
#
# Usage: scripts/changelog-section.sh VERSION [CHANGELOG]
#   VERSION    without the leading "v", e.g. 0.1.31
#   CHANGELOG  defaults to CHANGELOG.md
#
# Fails (exit 1) when the version has no "## [VERSION]" heading or its section is empty,
# so a release is never published with missing notes.
set -euo pipefail

if [[ $# -lt 1 || $# -gt 2 ]]; then
    echo "usage: $0 VERSION [CHANGELOG]" >&2
    exit 2
fi
version="$1"
changelog="${2:-CHANGELOG.md}"

# The section runs from its "## [VERSION]" heading to the next "## " heading. Leading blank
# lines are dropped here; the command substitution drops the trailing ones.
section="$(awk -v heading="## [${version}]" '
    index($0, heading) == 1 { found = 1; next }
    found && /^## / { exit }
    found { print }
' "$changelog" | sed -e '/./,$!d')"

if [[ -z "$section" ]]; then
    echo "error: no notes for version ${version} in ${changelog}" >&2
    exit 1
fi
printf '%s\n' "$section"

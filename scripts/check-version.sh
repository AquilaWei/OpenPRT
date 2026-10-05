#!/usr/bin/env bash
# Checks that the README version badge matches VERSION_NAME in gradle.properties and,
# when a tag is given, that the tag is "v" followed by that same version.
#
# Usage: scripts/check-version.sh [TAG]   (run from the repository root)
#
# Exits 1 with a message naming the mismatch, so CI fails before anything is published.
set -euo pipefail

if [[ $# -gt 1 ]]; then
    echo "usage: $0 [TAG]" >&2
    exit 2
fi

version="$(sed -n 's/^VERSION_NAME=//p' gradle.properties)"
if [[ -z "$version" ]]; then
    echo "error: no VERSION_NAME in gradle.properties" >&2
    exit 1
fi

badge="$(sed -n 's#.*img.shields.io/badge/version-\([^-]*\)-.*#\1#p' README.md)"
if [[ "$badge" != "$version" ]]; then
    echo "error: README badge says '${badge}' but gradle.properties says ${version}" >&2
    exit 1
fi

if [[ $# -eq 1 && "$1" != "v${version}" ]]; then
    echo "error: tag $1 does not match gradle.properties version v${version}" >&2
    exit 1
fi
echo "version ${version} OK"

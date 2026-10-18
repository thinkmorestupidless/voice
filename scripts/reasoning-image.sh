#!/bin/sh
# Builds the reasoning service's image from its release tag in the sibling checkout, and names it as
# the compose file asks, for a machine that cannot pull it from the registry.
set -eu
cd "$(dirname "$0")/.."

tag="${VOICE_REASONING_TAG:-v0.1.1}"
checkout="${VOICE_REASONING_CHECKOUT:-../ankka-reasoning}"
work=$(mktemp -d)
trap 'git -C "$checkout" worktree remove --force "$work" >/dev/null 2>&1 || true' EXIT
git -C "$checkout" worktree add --detach "$work" "$tag" >/dev/null
(cd "$work" && sbt -batch service/Docker/publishLocal)
version="${tag#v}"
docker tag "ankka-reasoning:$version" "ghcr.io/thinkmorestupidless/ankka-reasoning:$version"
echo "reasoning-image: ghcr.io/thinkmorestupidless/ankka-reasoning:$version"

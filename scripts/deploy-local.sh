#!/bin/sh
# Tier 5 (quickstart.md): builds both images, loads them into the local kind installation, applies
# the reasoning service, sets the project secrets by name, applies voice and voice-web, and exposes
# voice-web. Needs the ankka CLI logged in to the local installation and a project selected (or
# VOICE_PROJECT). Secret values are read from the environment or the terminal, never from a file in
# this repository.
set -eu
cd "$(dirname "$0")/.."

project="${VOICE_PROJECT:-voice}"
cluster="${VOICE_KIND_CLUSTER:-ankka}"
reasoning_image="ghcr.io/thinkmorestupidless/ankka-reasoning:0.1.1"
version="$(sbt -batch -error 'print service/Docker/version' | tail -1 | tr -d '[:space:]')"
export VERSION="$version"

echo "deploy-local: voice $version into project $project on kind cluster $cluster"
if ! ankka services list -p "$project" >/dev/null 2>&1; then
  echo "deploy-local: no project '$project' on this installation; create one, or set VOICE_PROJECT" >&2
  exit 1
fi

# 1. Images.
sbt -batch service/Docker/publishLocal
docker build -t "voice-web:$version" web
if ! docker image inspect "$reasoning_image" >/dev/null 2>&1; then
  docker pull "$reasoning_image" || ./scripts/reasoning-image.sh
fi
for image in "voice:$version" "voice-web:$version" "$reasoning_image"; do
  kind load docker-image "$image" --name "$cluster"
done

# 2. The reasoning service, from its own descriptor in the sibling checkout.
ankka services apply -f "${VOICE_REASONING_DESCRIPTOR:-../ankka-reasoning/deploy/service.json}" -p "$project"

# 3. Project secrets, by name. Each value comes from the environment, or is asked for.
set_secret() {
  key="$1"
  value="$(printenv "$key" || true)"
  if [ -z "$value" ]; then
    printf '%s: ' "$key" >&2
    stty -echo 2>/dev/null || true
    read -r value
    stty echo 2>/dev/null || true
    printf '\n' >&2
  fi
  printf '%s' "$value" | ankka projects secrets set voice-secrets "$key=-" -p "$project"
}
set_secret VOICE_INVITE_CODES
set_secret VOICE_SESSION_KEY
set_secret ANTHROPIC_API_KEY

# 4. Voice and its interface. The descriptors carry ${VERSION}, ${STAFF_ISSUER} and ${STAFF_JWKS_URL};
#    they are filled here into copies outside the repository, so no installation value is committed.
: "${STAFF_ISSUER:?set STAFF_ISSUER to the installation's voice-staff realm issuer}"
: "${STAFF_JWKS_URL:?set STAFF_JWKS_URL to that realm's JWKS URL}"
rendered="$(mktemp -d)"
trap 'rm -rf "$rendered"' EXIT
render() {
  sed -e "s|\${VERSION}|$VERSION|g" -e "s|\${STAFF_ISSUER}|$STAFF_ISSUER|g" -e "s|\${STAFF_JWKS_URL}|$STAFF_JWKS_URL|g" "$1" >"$2"
}
render service.json "$rendered/voice.json"
render web/service.json "$rendered/voice-web.json"
ankka services apply -f "$rendered/voice.json" -p "$project"
ankka services apply -f "$rendered/voice-web.json" -p "$project"
ankka services expose voice-web -p "$project"
ankka services get voice-web -p "$project"

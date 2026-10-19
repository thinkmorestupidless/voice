#!/bin/sh
# Writes the schema of the ankka release the reasoning image is built on into target/reasoning-ddl,
# from the ankka-runtime jar inside that image, so its Postgres is created with the schema it reads.
set -eu
cd "$(dirname "$0")/.."

image="${VOICE_REASONING_IMAGE:-ghcr.io/thinkmorestupidless/ankka-reasoning:0.1.1}"
if ! docker image inspect "$image" >/dev/null 2>&1; then
  docker pull "$image" >/dev/null 2>&1 || {
    echo "reasoning-ddl: $image is neither here nor pullable; run ./scripts/reasoning-image.sh" >&2
    exit 1
  }
fi

out=target/reasoning-ddl
work=$(mktemp -d)
rm -rf "$out" && mkdir -p "$out"
container=$(docker create "$image")
trap 'docker rm "$container" >/dev/null; rm -rf "$work"' EXIT
# Only the runtime jar: copying the whole lib directory fails on some machines on a read-only jar.
name=$(docker run --rm --entrypoint sh "$image" -c 'ls /opt/docker/lib | grep "ankka-runtime_3-" | head -1')
docker cp "$container:/opt/docker/lib/$name" "$work/$name" >/dev/null
(cd "$work" && unzip -q -o "$name" 'ankka/ddl/*.sql')
cp "$work"/ankka/ddl/*.sql "$out"/
echo "reasoning-ddl: $(ls "$out" | tr '\n' ' ')-> $out"

#!/bin/sh
# Runs voice on :9000 against the compose file's services, with the variables in .env.local. The two
# keys are generated into .env.local the first time, and kept, so sealed fields stay readable.
set -eu
cd "$(dirname "$0")/.."

[ -f .env.local ] || cp .env.local.example .env.local
key() { openssl rand -base64 32; }
if ! grep -q '^ANKKA_SECRET_KEY=.\+' .env.local; then
  sed -i.bak "s|^ANKKA_SECRET_KEY=.*|ANKKA_SECRET_KEY=$(key)|" .env.local && rm -f .env.local.bak
fi
if ! grep -q '^VOICE_SESSION_KEY=.\+' .env.local; then
  sed -i.bak "s|^VOICE_SESSION_KEY=.*|VOICE_SESSION_KEY=$(key)|" .env.local && rm -f .env.local.bak
fi
set -a
. ./.env.local
set +a
exec sbt "-Dankka.local-services.reasoning=${VOICE_REASONING_URL:-http://localhost:9100}" service/run

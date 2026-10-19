# Thin wrappers: every recipe is one command, and everything works without `just`.

# Every suite but the live-model ones. Docker is required.
test:
    sbt test

# Voice's Postgres, the reasoning service and its Postgres, and Keycloak for staff.
up:
    ./scripts/up.sh

# The above, plus Kafka, Neo4j and the merge sink, so reasoning answers from its graph.
up-graph:
    ./scripts/up.sh graph

down:
    docker compose --profile reasoning --profile staff --profile graph down -v

# voice on :9000 against the compose file's services, with .env.local.
run:
    ./scripts/run-local.sh

# The interface through `ankka local web`, on :3000, with /api mounted to voice.
web:
    cd web && ankka local web --service voice=http://127.0.0.1:9000 -- npm run dev

# Loads seed/school-transport into Voice, which writes it to reasoning. Needs SEED_STAFF_TOKEN, a
# reviewer's token; SEED_ISSUE reuses an Issue instead of opening a new one; SEED_INVITE, an invite
# code, also replays the pack's synthetic participants through the citizen routes (needs the model).
seed:
    sbt "seed/runMain voice.seed.Seed"

# Builds both images, loads them into kind, applies reasoning, voice and voice-web, exposes voice-web.
deploy-local:
    ./scripts/deploy-local.sh

# Builds the reasoning image from the sibling checkout's release tag, for when it cannot be pulled.
reasoning-image:
    ./scripts/reasoning-image.sh

hooks:
    git config core.hooksPath .githooks

# The living features, the glossary and the specs that name them.
check:
    uvx --from "git+https://github.com/thinkmorestupidless/speckit-bdd@v0.2.0#subdirectory=checker" speckit-bdd check --root . --glossary GLOSSARY.md --features features --specs specs

# The live-model suites, on purpose: needs ANTHROPIC_API_KEY and spends tokens.
live:
    VOICE_INCLUDE_TAGS=Live sbt "service/testOnly voice.live.*"

# Tier 3: Voice against the real reasoning service from `just up`, on VOICE_REASONING_URL (default
# :9100). VOICE_GRAPH=1 with `just up-graph` also reads the graph tier.
tier3:
    VOICE_INCLUDE_TAGS=Reasoning sbt "service/testOnly voice.integration.ReasoningSuite"

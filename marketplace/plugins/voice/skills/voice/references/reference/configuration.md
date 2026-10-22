# Configuration

> Every environment variable the voice service, the seed client, the test recipes and the deploy script read, with its default and what it does, and the variables the platform supplies.

Source: https://docs.makethemlisten.uk/reference/configuration/
The service reads its settings once at start, from environment variables. Changing one is a
restart. Locally they come from `.env.local`, which `just run` writes the first time from
`.env.local.example`; in a deployment they are the descriptor's `env`, with the secret values from
the project secret `voice-secrets`.

## The service

| Variable | Default | What it is |
|---|---|---|
| `VOICE_SESSION_KEY` | none; the service refuses to start without it | the base64 of at least 32 random bytes; session tokens are signed with it |
| `VOICE_SESSION_HOURS` | `12` | how long a session token is valid |
| `VOICE_INVITE_CODES` | none | the pilot's invite codes, comma-separated; a session needs one |
| `VOICE_DORMANCY_PERIOD` | `180d` | how long since a participant's last session before they are erased for dormancy; a number and a unit, `d`, `h`, `m` or `s` |
| `VOICE_SUPPRESSION_THRESHOLD` | `5` | the fewest participants a cell of an aggregate may hold and still be shown |
| `VOICE_REASONING_SERVICE` | `reasoning` | the name of the reasoning service in the project |
| `VOICE_REASONING_URL` | none | local only: where the service named by `VOICE_REASONING_SERVICE` is, when there is no platform to resolve it; `just run` sets it from `.env.local` |
| `ANTHROPIC_API_KEY` | none | the model provider's key; without it a conversation turn ends in `CouldNotInterpret` and a research run fails |
| `ANKKA_AUTH_ISSUERS` | | `staff`: the one issuer the staff endpoint accepts |
| `ANKKA_AUTH_STAFF_ISSUER` | | the issuer URL of the staff realm, as it appears in tokens |
| `ANKKA_AUTH_STAFF_JWKS_URL` | | where the realm's signing keys are fetched from; reachable from the service |
| `ANKKA_AUTH_STAFF_AUDIENCE` | | `voice-staff`: the audience a staff token must carry |

## Supplied by the platform

`ANKKA_DB_*` for the service's database, `ANKKA_SECRET_KEY`, the key the secret store encrypts
participant keys under, `ANKKA_HTTP_PORT`, `ANKKA_CLUSTER_MODE` and the certificates. Locally
`.env.local` holds `ANKKA_SECRET_KEY`, generated once; deleting it makes every sealed field
unreadable, which is what erasure does on purpose. The web process reads `PORT` and
`ANKKA_SERVICES_URL` and nothing else.

## The seed client

| Variable | What it is |
|---|---|
| `SEED_STAFF_TOKEN` | a token from the staff issuer with the reviewer role; read by `just seed` only, never by the service |
| `SEED_ISSUE` | an issue id to add the pack's material to, instead of opening a new issue |
| `SEED_INVITE` | an invite code; when set, the pack's synthetic participants are replayed through the citizen routes, which needs the model |

## The recipes

| Variable | What it is |
|---|---|
| `VOICE_INCLUDE_TAGS` | `Live`, `Reasoning` or both: lifts a tag the build excludes from `sbt test`; `just live` and `just tier3` set it |
| `VOICE_GRAPH` | `1` runs the tier-three test that reads the reasoning service's graph tier, with `just up-graph` |
| `VOICE_MODEL_RECORD` | `1` re-records the model fixtures the feature suites answer from, with a live key |
| `VOICE_REASONING_IMAGE` | the reasoning image `just up` extracts the schema from and runs |
| `REASONING_POSTGRES_PORT` | the reasoning service's Postgres port in the compose file, `5433` |

## The deploy script

| Variable | What it is |
|---|---|
| `STAFF_ISSUER`, `STAFF_JWKS_URL` | fill the descriptor's placeholders; required |
| `VOICE_PROJECT` | the ankka project, `voice` |
| `VOICE_KIND_CLUSTER` | the kind cluster's name, `ankka` |
| `VOICE_REASONING_DESCRIPTOR` | the reasoning service's descriptor, `../ankka-reasoning/deploy/service.json` |
| `VOICE_INVITE_CODES`, `VOICE_SESSION_KEY`, `ANTHROPIC_API_KEY` | the secret values, read from the environment or asked for on the terminal, and sent to the project secret from stdin |

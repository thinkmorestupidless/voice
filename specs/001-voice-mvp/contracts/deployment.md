# Contract: deployment and environment

Two services in one project, the reasoning service beside them, and what each is given. Values are
names only; no secret value appears here or anywhere in the repository.

## Services

| Service | Kind | Image | Exposed |
|---|---|---|---|
| `voice` | ankka service, runtime 0.12.0, Scala | `voice:<version>` | no; reached through the web mount and by other services |
| `voice-web` | web-hosted, `processPort` 3000 | `voice-web:<version>` | yes: the one hostname |
| `reasoning` | ankka service, runtime 0.10.0 | `ghcr.io/thinkmorestupidless/ankka-reasoning:0.1.1` | no |
| `reasoning-graph` (optional tier) | ankka-contrib's graph sink, `database: none` | `ghcr.io/thinkmorestupidless/ankka-graph-sink-neo4j:0.1.0` | no |

### `voice` descriptor (`service.json`)

```json
{
  "name": "voice",
  "service": {
    "image": "voice:${VERSION}",
    "runtime": "0.12.0",
    "env": [
      { "name": "VOICE_REASONING_SERVICE", "value": "reasoning" },
      { "name": "VOICE_INVITE_CODES", "secretKeyRef": { "name": "voice-secrets", "key": "VOICE_INVITE_CODES" } },
      { "name": "VOICE_SESSION_KEY", "secretKeyRef": { "name": "voice-secrets", "key": "VOICE_SESSION_KEY" } },
      { "name": "ANTHROPIC_API_KEY", "secretKeyRef": { "name": "voice-secrets", "key": "ANTHROPIC_API_KEY" } },
      { "name": "ANKKA_AUTH_ISSUERS", "value": "staff" },
      { "name": "ANKKA_AUTH_STAFF_ISSUER", "value": "${STAFF_ISSUER}" },
      { "name": "ANKKA_AUTH_STAFF_JWKS_URL", "value": "${STAFF_JWKS_URL}" },
      { "name": "ANKKA_AUTH_STAFF_AUDIENCE", "value": "voice-staff" },
      { "name": "VOICE_SUPPRESSION_THRESHOLD", "value": "5" },
      { "name": "VOICE_SESSION_HOURS", "value": "12" },
      { "name": "VOICE_DORMANCY_PERIOD", "value": "180d" }
    ],
    "resources": { "instanceType": "small", "autoscaling": { "minInstances": 1 } }
  }
}
```

The platform supplies `ANKKA_DB_*`, `ANKKA_SECRET_KEY` (the key the secret store encrypts under),
`ANKKA_HTTP_PORT`, `ANKKA_CLUSTER_MODE` and the certificates. Voice declares no topic and no Kafka: it
publishes nothing to a broker in this feature.

### Project secrets

| Secret | Keys | Set by |
|---|---|---|
| `voice-secrets` | `VOICE_INVITE_CODES` (comma-separated), `VOICE_SESSION_KEY` (base64 of 32 bytes), `ANTHROPIC_API_KEY` | `ankka projects secrets set voice-secrets <KEY>=- -p <project>` from stdin, by an organization owner |
| `voice-web-secrets` | none in this feature (the web process holds no session of its own) | |

Running instances keep an old value until `ankka services restart voice`.

### `voice-web` descriptor (`web/service.json`)

```json
{
  "name": "voice-web",
  "service": {
    "image": "voice-web:${VERSION}",
    "hosting": "web",
    "processPort": 3000,
    "mounts": [ { "path": "/api", "service": "voice" } ],
    "callers": []
  }
}
```

The browser reaches `voice` only at `/api/...` on the web hostname; the proxy forwards
`Authorization` and sets `X-Ankka-Caller: internet`. `voice`'s endpoints admit that caller: the public
endpoint is `AllowAll`, the citizen and staff endpoints decide on the bearer token. Nothing else is
exposed. The web process reads `PORT` and `ANKKA_SERVICES_URL` from the platform and nothing else.

### Staff identity

A realm `voice-staff` in the installation's Keycloak (or another OIDC issuer with asymmetric
signing), with realm roles `representative`, `reviewer`, `operator`, a public client `voice-staff`
for the interface's redirect flow, and an audience `voice-staff` on access tokens. Pilot accounts
are created there by an administrator; the operator then writes each representative's authority
through `PUT /staff/accounts/{subject}`.

Locally and in CI: `compose/keycloak/voice-staff.json` imports the realm with three pilot users whose
passwords are in the import file and are not secrets (they exist only on a laptop). The issuer is
`http://localhost:8180/realms/voice-staff`.

### Reasoning

Deployed as its own release says (`ankka-reasoning/deploy/service.json`), in the same project so
`services("reasoning")` resolves by name; Voice is admitted as `service:<project>/voice`. Voice needs
only the record tier; the graph tier (Kafka topic `reasoning-graph`, Neo4j, the sink with
`ANKKA_GRAPH_SINK_TOPIC=reasoning-graph` and a `reasoning-graph` secret) is optional and adds the
answers routes.

## Local environment (`docker-compose.yml`, `Justfile`)

| Profile | Containers | Ports |
|---|---|---|
| default | `postgres` (Voice's, schema from `sbt schema`) | 5432 |
| `reasoning` | `reasoning-postgres` (ankka 0.10.0 schema, from the reasoning image's own DDL step), `reasoning` | 5433, 9100 |
| `staff` | `keycloak` with the realm import | 8180 (8080 is a kind cluster's ingress) |
| `graph` | `kafka`, `neo4j`, `graph-sink` | 9094, 7474, 7687 |

`just up` starts default, `reasoning` and `staff`; `just up-graph` adds the graph tier. `just run`
starts `voice` on 9000 with:

```
ANKKA_SECRET_KEY=<base64 of 32 bytes, generated once into .env.local, ignored by git>
VOICE_SESSION_KEY=<same>
VOICE_INVITE_CODES=pilot
VOICE_REASONING_URL=http://localhost:9100
ANKKA_AUTH_ISSUERS=staff ANKKA_AUTH_STAFF_ISSUER=http://localhost:8180/realms/voice-staff
ANKKA_AUTH_STAFF_JWKS_URL=http://localhost:8180/realms/voice-staff/protocol/openid-connect/certs
ANKKA_AUTH_STAFF_AUDIENCE=voice-staff
ANTHROPIC_API_KEY=<from the developer's environment; absent, the service starts with the recorded provider and only recorded conversations work>
SEED_STAFF_TOKEN=<a token from the local Keycloak with the reviewer and operator roles; read by `just seed` only, never by the service>
```

Locally `services("reasoning")` is resolved by `VOICE_REASONING_URL` (the service client's local
resolution; verify V4). `just web` runs `ankka local web --service voice=http://127.0.0.1:9000 -- npm run dev`
in `web/`. `just seed` loads `seed/school-transport/` into reasoning and Voice.

## Deploying

```bash
sbt service/Docker/publishLocal && (cd web && npm run build && docker build -t voice-web:<v> .)
kind load docker-image voice:<v> voice-web:<v>                 # local kind installation
ankka services apply -f target/deploy/reasoning.json -p voice   # once; the reasoning release's descriptor
ankka projects secrets set voice-secrets VOICE_SESSION_KEY=- VOICE_INVITE_CODES=- ANTHROPIC_API_KEY=- -p voice
ankka services apply -f service.json -p voice
ankka services apply -f web/service.json -p voice
ankka services expose voice-web -p voice
```

The GitHub workflows the template made (`ci.yml`: `sbt test`; `deploy.yml`: build, push, apply on
a `v*` tag) are kept; `ci.yml` gains the web app's `npm test` and the compose profiles the scenario
tiers need. Only a tag publishes.

## What is not here

Installation values, hostnames, the Keycloak admin credential and the real invite codes: these
belong to the private infrastructure repository, as the family's conventions say.

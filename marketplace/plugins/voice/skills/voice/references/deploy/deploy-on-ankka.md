# Deploy on ankka

> Deploy the reasoning service, the project secrets, the voice service and its interface to an ankka installation, with the staff realm the installation must have, by hand on kind or from a version tag.

Source: https://voice.ankka.cloud/deploy/deploy-on-ankka/
Voice deploys as two services in one [ankka](https://docs.ankka.cloud/) project, with the reasoning
service beside them: `reasoning`, from its own release; `voice`, the service; `voice-web`, the
interface, the one thing exposed. Values on this page are names only: no secret value, hostname or
installation setting is in the repository.

| Service | Kind | Image | Exposed |
|---|---|---|---|
| `voice` | ankka service, runtime `0.12.0` | `voice:<version>` | no; reached through the web mount |
| `voice-web` | web-hosted, process port `3000` | `voice-web:<version>` | yes: the one hostname |
| `reasoning` | ankka service, runtime `0.10.0` | `ghcr.io/thinkmorestupidless/ankka-reasoning:0.1.1` | no |
| `reasoning-graph` | ankka's graph sink, optional | `ghcr.io/thinkmorestupidless/ankka-graph-sink-neo4j:0.1.0` | no |

## What the installation must have

- An ankka installation, with the `ankka` CLI logged in and a project for Voice.
- An OIDC issuer with asymmetric signing, such as the installation's Keycloak, with a realm
  `voice-staff`: realm roles `representative`, `reviewer` and `operator`, a public client
  `voice-staff` for the interface, and the audience `voice-staff` on access tokens. Pilot accounts
  are created there by an administrator; the operator then writes each representative's authority
  through `PUT /staff/accounts/{subject}`.
- An Anthropic API key for the account the pilot uses, with its data terms read; see
  [Threat model](../concepts/threat-model.md).

## The descriptors

`service.json` carries three placeholders, `${VERSION}`, `${STAFF_ISSUER}` and `${STAFF_JWKS_URL}`,
filled at deploy time into a copy outside the repository:

```json title="service.json"
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

The platform supplies the database, `ANKKA_SECRET_KEY`, the HTTP port, the cluster mode and the
certificates. Voice declares no topic.

```json title="web/service.json"
{
  "name": "voice-web",
  "service": {
    "image": "voice-web:${VERSION}",
    "hosting": "web",
    "processPort": 3000,
    "mounts": [{ "path": "/api", "service": "voice" }],
    "callers": []
  }
}
```

The browser reaches `voice` only at `/api/…` on the web hostname. The web process reads `PORT` and
`ANKKA_SERVICES_URL` from the platform and nothing else.

## The secrets

One project secret, `voice-secrets`, with three keys, set from stdin by an organization owner so no
value passes through a shell history or a file:

```bash
printf '%s' "$VOICE_INVITE_CODES" | ankka projects secrets set voice-secrets VOICE_INVITE_CODES=- -p voice
printf '%s' "$VOICE_SESSION_KEY"  | ankka projects secrets set voice-secrets VOICE_SESSION_KEY=- -p voice
printf '%s' "$ANTHROPIC_API_KEY"  | ankka projects secrets set voice-secrets ANTHROPIC_API_KEY=- -p voice
```

`VOICE_INVITE_CODES` is comma-separated; `VOICE_SESSION_KEY` is the base64 of 32 random bytes,
`openssl rand -base64 32`. Running instances keep an old value until `ankka services restart voice`.
Changing the session key signs every participant out; changing nothing else touches a participant's
records, because their keys are in the service's own secret store.

## By hand, on a local kind installation

`just deploy-local` builds both images, loads them into the kind cluster, applies the reasoning
service's descriptor from a checkout beside this one, sets the three secrets from the environment
or a prompt, renders the placeholders, applies both descriptors and exposes `voice-web`:

```bash
export STAFF_ISSUER=https://<the installation's keycloak>/realms/voice-staff
export STAFF_JWKS_URL=$STAFF_ISSUER/protocol/openid-connect/certs
just deploy-local
ankka services get voice -p voice
ankka services logs voice -p voice
```

`VOICE_PROJECT`, `VOICE_KIND_CLUSTER` and `VOICE_REASONING_DESCRIPTOR` override the project, the
cluster name and the path to the reasoning descriptor. The script stops early when the project does
not exist. The JWKS URL must be reachable from inside the cluster, which a `localhost` address is not.

The same steps, one at a time:

```bash
sbt service/Docker/publishLocal && docker build -t voice-web:<version> web
kind load docker-image voice:<version> voice-web:<version> --name ankka
ankka services apply -f ../ankka-reasoning/deploy/service.json -p voice
ankka services apply -f <rendered>/voice.json -p voice
ankka services apply -f <rendered>/voice-web.json -p voice
ankka services expose voice-web -p voice
```

## From a version tag

Pushing a tag `v*` runs `.github/workflows/deploy.yml`: it builds the service image for `amd64` and
`arm64`, pushes it to the repository's GitHub Container Registry, and deploys it with
`ankka services deploy voice <image>` against the descriptor's settings. It needs the repository
secrets `ANKKA_URL`, `ANKKA_TOKEN`, `ANKKA_PROJECT` and, for an installation whose certificate is
not publicly trusted, `ANKKA_CA`; without `ANKKA_TOKEN` it declines to run rather than failing, so
the first tag is green. The interface's image and descriptor are deployed by hand, as the steps on
this page show. [Releasing](../contributing/releasing.md) is what else a tag does.

## The reasoning service

Deploy it as its own documentation says, in the same project, so `services("reasoning")` resolves by
name and Voice is admitted as `service:<project>/voice`. Voice needs only its record tier; the graph
tier, Kafka, Neo4j and the sink, is optional and adds routes Voice does not depend on.

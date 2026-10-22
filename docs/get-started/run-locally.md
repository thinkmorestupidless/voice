---
title: Run it on your machine
description: Start Voice on a laptop with its database, the reasoning service and a staff identity provider in Docker, load the seed pack, and open the interface.
kind: tutorial
related: [get-started/first-issue.md, build/seed-pack.md, reference/configuration.md, deploy/deploy-on-ankka.md]
---

# Run it on your machine

This page takes a clone of the repository to the service on `localhost:9000`, the interface on
`localhost:3000`, and one public issue to read. It takes a few minutes, most of it the first build.

## What you need

JDK 21, [sbt](https://www.scala-sbt.org/), Docker, Node.js 24, [`just`](https://just.systems/) and
the [`ankka` CLI](https://docs.ankka.cloud/get-started/install/) `0.12.0`. ankka's libraries come
from Maven Central; nothing of the platform has to be installed to run the service on a laptop.

An Anthropic API key is needed for a conversation: every turn asks the model for a reply and a
proposal, and so does a representative's request for research. Everything else on this page, and
most of [Your first issue](first-issue.md), runs without one.

## Start what stands in for a cluster

`just up` extracts the reasoning service's database schema from its image, then starts four
containers: Postgres for Voice, Postgres for the reasoning service, the reasoning service, and
Keycloak with a realm of three staff users.

```bash
git clone https://github.com/thinkmorestupidless/voice
cd voice
just up
```

```text
 Container voice-postgres-1 Healthy
 Container voice-reasoning-postgres-1 Healthy
 Container voice-reasoning-1 Started
 Container voice-keycloak-1 Started
```

The reasoning image, `ghcr.io/thinkmorestupidless/ankka-reasoning:0.1.1`, cannot be pulled
anonymously. When the pull fails, `just reasoning-image` builds it from a checkout of ankka-reasoning
beside this one, at that tag.

Keycloak listens on `8180`, not its usual `8080`, because a local ankka installation's ingress holds
`8080`. The realm `voice-staff` holds the users `representative`, `reviewer`, `operator` and `seed`,
each with the role of its name (`seed` has all three); their passwords are in
`compose/keycloak/voice-staff.json` and are not secrets, because the realm exists only on a laptop.

## Start the service

`just run` copies `.env.local.example` to `.env.local` the first time, generates the two keys it
needs into it, and starts `voice` on `9000`.

```bash
just run
```

`.env.local` is ignored by git. It holds `ANKKA_SECRET_KEY`, which the secret store encrypts
participant keys under, and `VOICE_SESSION_KEY`, which session tokens are signed with. Both are kept
between runs, so sealed fields stay readable; delete the file and every participant's records become
unreadable, which is what erasure does on purpose. Add `ANTHROPIC_API_KEY=` to it for conversations.
Every variable is on [Configuration](../reference/configuration.md).

The service is up when a public route answers:

```bash
curl -s 'localhost:9000/public/issues'
```

```text
[]
```

## Load the seed pack

`just seed` loads `seed/school-transport/` through Voice's staff routes as a reviewer, and Voice
writes the questions, sources, evidence and claims to the reasoning service. It needs a staff token
with the reviewer role, which the local realm's `seed` user gives:

```bash
export SEED_STAFF_TOKEN=$(curl -s -d grant_type=password -d client_id=voice-staff \
  -d username=seed -d password=local-seed \
  localhost:8180/realms/voice-staff/protocol/openid-connect/token | jq -r .access_token)
just seed
```

```text
issue iss-01M57PJHFPN27YMK6J1TD0T2NG: 2 questions, 7 evidence, 3 claims
```

The issue id is minted on your machine and differs from this one. Six pieces of evidence are
accepted; the seventh is a correction the pack leaves as a candidate for a reviewer to accept. The
issue is public at once:

```bash
curl -s 'localhost:9000/public/issues?q=bus' | jq '.[].title'
```

```text
"Rural school bus withdrawal"
```

## Open the interface

```bash
just web
```

This runs `ankka local web` in `web/`, which starts the app's server on `3000` behind the same
proxy a cluster runs, with `/api` mounted to `voice` at `localhost:9000`. Open
`http://localhost:3000/` and search for the issue; its page shows the two questions, the evidence
with its scope, authority and limitations, and the two options. `http://localhost:3000/me` starts a
participant's session with the invite code `pilot`; `http://localhost:3000/staff` takes a staff
token from the realm. [The interface](../build/the-interface.md) has every page.

## Stop it

```bash
just down
```

This removes the containers and their volumes. The next `just up` starts empty, and `just seed`
loads the pack again.

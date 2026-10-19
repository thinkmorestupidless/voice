# How Voice works

> The two services and the reasoning service beside them, the three endpoints with their access rules, the components behind each stage of the loop, and the rules the code holds to.

Source: https://docs.makethemlisten.uk/concepts/architecture/
Voice is one [ankka](https://docs.ankka.cloud/) service, `voice`, written in Scala 3, and one
web-hosted interface, `voice-web`, which mounts the service at `/api`. The reasoning service,
`reasoning` from [ankka-reasoning](https://reasoning.ankka.cloud/), runs beside them and holds the
evidence; Voice calls it as a service. The three run in one ankka project, and only `voice-web` is
exposed: a browser reaches `voice` through the mount and nothing else reaches it from outside.

## Three endpoints, one per audience

| Endpoint | Prefix | Who | Access rule |
|---|---|---|---|
| public | `/public` | anyone, no credential | open; no route creates anything, and no reply holds a sealed value or a participant id, which a build-time check enforces |
| citizen | `/me` | a pseudonymous participant | a session token Voice itself mints and signs with `VOICE_SESSION_KEY`; the participant is the token's subject, never a value in a request |
| staff | `/staff` | a reviewer, a representative or an operator | a person's OIDC token from the issuer named `staff` in the descriptor; the role is the token's, and each route states the role it needs |

The endpoint is the edge. It reads the clock once, reads the caller once, puts both into the
command, and seals or opens personal values; nothing deeper has a clock, a caller or a key.
[Five audiences](audiences.md) says what each may read.

## The components, by stage

Every component is registered explicitly, and a test holds the registration equal to the list on
[Records](../reference/records.md).

- **Hear.** Event sourced entities `participant`, `conversation` and `concern`; the workflow
  `conversation-turn`, which asks the `concern-extraction` agent for a reply and a proposal, off the
  request path.
- **Share.** The event sourced entity `contribution`; the workflow `disclosure-review`, which runs
  the privacy detectors and the `privacy-flagging` agent, waits for a reviewer, and publishes; the
  key value entity `issue-link`, the only map from a public copy back to its author.
- **Understand.** The event sourced entity `issue`, which holds public copies, evidence links,
  options and the chronology; the key value entities `policy-option` and `source-card`; the views
  `issue-rows` and `public-search`.
- **Deliberate.** The event sourced entity `expression`; the view `issue-aggregates`.
- **Act.** Event sourced entities `investigation`, `action`, `decision` and `outcome`; the workflow
  `research` with the `research-assistant` agent; the entity `access-log`, one per day.
- **Return.** Key value entities `interest` and `issue-followers`; the event sourced entity
  `notification`; the consumer `developments` over issue events, which starts one `update-delivery`
  workflow per development.
- **Erase.** The workflow `erasure`, keyed by participant; the key value entity
  `participant-records`, the index of every record a participant holds; the view
  `erasure-receipts`; the timed action `dormancy-check`.

## What is kept where

| Store | Holds |
|---|---|
| the service's Postgres database | every entity's journal and state, every view, the workflows' state, and the secret store with one key per participant |
| the reasoning service's database | questions, hypotheses, sources, evidence and claims, as records that are never edited |
| the interface | nothing: a session token and a staff token in memory, for one tab |

Voice declares no topic and publishes nothing to a broker. The graph tier of the reasoning service,
Kafka and Neo4j, is optional and adds answers Voice does not need; see
[Voice and the reasoning service](reasoning.md).

## Four rules the code holds to

- **No personal value in an event, a state, a view row, a log or a reply**, except inside a sealed
  field, or as the de-identified public copy a participant and a reviewer both approved.
  [Sealed fields and erasure](sealing.md).
- **No model call on a request path.** A turn and a research run are workflows; the browser polls or
  holds a reconnecting stream. [Nothing waits for a model](nothing-waits-for-a-model.md).
- **No clock and no caller in a handler.** The endpoint reads both once and puts them in the
  command, as a provenance every event carries.
- **Refusals by name.** Every rule a command can break has a name, a status and the identifiers it
  is about, and a client matches on the name. [Refusals](../reference/refusals.md).

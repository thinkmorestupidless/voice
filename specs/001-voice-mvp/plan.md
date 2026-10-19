# Implementation Plan: Voice MVP demonstrator

**Branch**: `001-voice-mvp` | **Date**: 2026-10-18 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/001-voice-mvp/spec.md`

## Summary

One ankka service, `voice`, with a web-hosted interface beside it, that carries a citizen's concern
from a private conversation through exact approval, review, a public Issue, evidence read from the
reasoning service, opinions kept apart from evidence, a representative's dated action, one truthful
update to each follower, and an erasure, asked for or by dormancy, that leaves anonymised public
knowledge standing and says why its author is gone.

Technically: an ankka application in Scala 3 on ankka 0.12.0, three sbt modules and an npm app (R18).
Every record with a personal field is an event sourced entity whose personal fields are sealed under a
key that belongs to one participant and lives in the service's secret store; erasure destroys the key
(R1). Each reaction is a workflow keyed by its cause so a repeat addresses the running one (R7). The
model is called only from workflow steps, never from a request a browser waits on (R5). Citizens hold
a session token Voice signs and a recovery credential Voice hashes (R9); staff authenticate through
OIDC with roles in the token (R10). Reasoning records are read by id over HTTP as a service caller;
the graph is optional (R4). Scenarios are living features run by `GherkinSuite` (R17).

Planning found four things the source document assumed otherwise:

- **ankka has no field-level erasure.** The source document says the platform "already supports
  field-level crypto-shredding". ankka's spec 042 is a draft with no plan, tasks or code, and
  `v0.12.0` carries only its text. Voice seals personal fields itself at the edge, under a key per
  participant in the secret store, in the shape 042 intends, so the platform feature replaces it later
  (R1). The spec's FR-054 to FR-059 are met; the spec's assumption paragraph on the platform is wrong
  and is corrected by this plan rather than by editing the spec.
- **A browser request cannot wait for the model.** The gateway ends every response at fifteen
  seconds, streams included. The conversation turn and the research request answer 202 with an id
  and run as workflows; the interface polls or holds a reconnecting event stream (R5, R15). The
  spec's scenarios are unchanged; the contract's two routes are asynchronous.
- **Reasoning answers per hypothesis, not per claim, and its graph answers need Neo4j.** "Why is this
  claim supported" is assembled from three record reads that need Postgres alone; the graph tier is a
  compose profile that adds richer reads and is required by no scenario (R4).
- **The platform issues no end-user identity and has no rate limiting at the gateway.** Pseudonymous
  citizens get a token Voice signs and verifies without I/O; the pilot's abuse control is an invite
  code, which is not identity (R9).

The clarification session of 2026-10-18 settled seven points after this plan was first written. Three
changed the design and are folded in below and in the artifacts: record-derived developments reach
followers with no human step and no draft queue, so the staff review routes for updates are gone and
only a narrative update has a route (FR-044, FR-047); the inbox, delivered immediately, is the one
channel, so the delivery preference has one value (FR-046); and a participant dormant for a
configurable period is erased by a timer through the same workflow, with the cause carried as a
category on every retained public contribution (FR-063, FR-064, R19). The other four changed no
artifact: the interfaces were already in scope, the recorded model double was already R16,
proactive clustering was never designed, and the six coined glossary terms stand.

## Technical Context

**Language/Version**: Scala 3.9.0 on JDK 21, sbt 1.12.9 as the template set (the family pins 1.12.15;
align in the first task); TypeScript 7 on Node 24 for the interface.

**Primary Dependencies**: `ankka-sdk`, `ankka-runtime`, `ankka-http`, `ankka-agent`,
`ankka-auth-oidc`, `ankka-telemetry-otlp` 0.12.0 from Maven Central (all present, checked
2026-10-18); `ankka-testkit` and munit 1.3.6 in test. Beside the service: the reasoning service
0.1.1 as a published image, and for the optional graph tier Kafka 3.9, Neo4j 5.26 and ankka-contrib's
sink 0.1.0. The interface: the `ankka init --language web` template (React 19, Vite, `node:http`).
Model: `AnthropicProvider.fromEnv()`; tests: a recorded `ModelProvider` of Voice's own (R16). No
Pekko named directly, no judgments, no broker topics in this feature.

**Storage**: the service's one Postgres, provisioned by ankka: journals, snapshots, durable state,
view tables, timers, the secret store's table. The reasoning service has its own. Nothing else.

**Testing**: `EventSourcedTestKit`, `KeyValueEntityTestKit`, workflow and agent testkits with
`TestModelProvider` for components alone; `AnkkaTestKit` with `restartService()`, a `TimerRuntime` on a short poll and a view version
bump for the whole service; `GherkinSuite` per feature file for the living features; a database dump
assertion for erasure; one tagged live-model suite. Serialised tests, Docker required, as the family
does.

**Target Platform**: an ankka installation on Kubernetes, with the reasoning service in the same
project; a laptop with Docker for everything short of that.

**Project Type**: web service (an ankka application) with a web-hosted interface and a seed client.

**Performance Goals**: pilot scale (SC-001: a first concern confirmed within ten minutes of
conversation). A model turn completes within the agent runtime's two-minute round-trip bound; a
public Issue page and a search answer within a second without the graph tier. No throughput target.

**Constraints**: no personal value in an event, state, view row, log, trace or response except as a
sealed field or a de-identified public derivative (R1, R2); no model call on a request path (R5);
no decision read from a view (R11); no clock and no caller in a handler (R12); one ACL per audience
(R10); nothing written to reasoning but as the `voice-evidence-pack` holder (R4); nothing in the
repository that is an installation value.

**Scale/Scope**: one Issue, five to fifteen sources, three to eight synthetic participants, three
staff accounts. 64 functional requirements, 7 user stories with 59 acceptance scenarios, 15 edge
cases, 23 kinds of record, 3 endpoints with about 67 routes, 5 workflows, 1 timed action, 3 agents, 7 views.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

`.specify/memory/constitution.md` is the unfilled template, as in every repository of the family, so
the gate is ankka's rules (`ankka/CLAUDE.md`, the `ankka` skill's nine rules) and the family's
principles in `../ankka-brain/principles.md`, with the source document's twelve product principles
(spec, Overview) as the domain's own.

| Rule or principle | Status | How the design honours it |
|---|---|---|
| Effects are inert data; the runtime interprets them | pass | Handlers return effects. Time and actor arrive in the command (R12); sealing and model calls happen in steps, endpoints and consumers, never in an entity or view (R1, R5). |
| Wire names are protocol | pass | Every handler's wire name is declared in its companion; the refusal catalogue names rules, not methods. |
| A query cannot persist | pass | Every public and citizen read is a `query`; the only writes on a read path are the access log (a command the endpoint sends) and the turn workflow start. |
| Registration is explicit | pass | `Main` lists every component; a test counts them against the data model. |
| Stored types evolve additively | pass | Events and states are case classes under manifests; `Sealed` is designed to be replaced by the platform's `Personal` as a type swap with the same envelope shape (R1). |
| Every endpoint declares an ACL | pass | Three endpoints, three ACLs, one audience each (R9, R10, R13). Only the web service is exposed. |
| One database per service | pass | Voice reads reasoning over HTTP only; no shared tables; no second store for personal text (R1 alternatives). |
| Consumers are at least once | pass | A consumer only starts a workflow under an id derived from its cause; notifications are keyed by participant and development (R7). The dormancy timed action reports done when a later session reset the clock or the erasure already runs, so a repeat changes nothing (R19). |
| Test at two levels | pass | Tiers 1 and 2 of the quickstart; `restartService()` in the erasure and recovery scenarios. |
| No BSL on the path | pass | Pekko through ankka; Postgres; the reasoning image; Neo4j Community and Kafka only in the optional tier. |
| Adopt, then diverge with reasons | pass | Where the source document is wrong about the platform, the plan says so and why (Summary). |
| Explicit registration, never classpath scanning | pass | as above |
| Split by lifecycle and trust | pass | Installation values, hostnames and the Keycloak admin credential are in the private infrastructure repository; `contracts/deployment.md` carries names only. |
| Be your own customer | pass | Voice uses ankka and reasoning as released, by version; what they cannot do is a request (research, "Requests"), none worked round by reaching into platform tables. |
| Only a tag publishes | pass | The template's workflows; sbt-dynver; nothing is published in this feature. |
| The honest not-implemented list | pass | The spec's Out of Scope, and the plan's statement that erasure is Voice's until 042 ships, go into `docs/limitations.md` when the docs tree exists. |
| Traps are written down the day they cost time | pass | `research.md` "Found during implementation" and the repository's `CLAUDE.md` traps section, created with the first task. |
| Same conventions across repos | pass | spec-kit with speckit-bdd, `features/`, `GLOSSARY.md`, Justfile, scalafmt hook, serialised tests, `notes/ankka-requests.md` (R17, R18). |
| *Private by default; minimise, then protect* (product) | pass | Sealed fields, the public copy as the only plaintext (R2), no participant id in a public record (R3), no cookie, no address. |
| *Human authority; truth ≠ preference; provenance* (product) | pass | Agents propose, workflows apply on a person's command (R5, R15); expressions are separate entities never read by evidence views (R8); every explanation is assembled from reasoning records with links (R4). |

**Violations to justify**: none. Two things grow beyond the simplest design and are in Complexity
Tracking.

**Re-check after Phase 1**: unchanged. The data model keeps every personal field sealed or in a
deletable entity, the contracts expose no route that opens a sealed value to the wrong audience, and
the deployment contract names no value.

## Project Structure

### Documentation (this feature)

```text
specs/001-voice-mvp/
├── plan.md                        # this file
├── research.md                    # R1–R18, fourteen things to verify first, eight requests
├── data-model.md                  # every record, its component, its sealed fields, how each is erased
├── quickstart.md                  # five tiers, from the domain alone to a kind installation, plus the live suite
├── contracts/
│   ├── http-api.md                # three endpoints, every route, identity and headers
│   ├── refusals.md                # every rule a command can break, by name
│   ├── reasoning-integration.md   # what Voice writes to and reads from reasoning, and never does
│   └── deployment.md              # descriptors, secrets by name, the local profiles, deploying
├── checklists/requirements.md
└── tasks.md                       # written by /speckit-tasks
```

### Source Code (repository root)

```text
build.sbt                          # three modules; versions in project/Dependencies.scala
project/
├── Dependencies.scala             # ankka 0.12.0 and every other version, written once
├── build.properties               # sbt, aligned with the family
└── plugins.sbt                    # sbt-native-packager, sbt-scalafmt, sbt-dynver
modules/
├── domain/                        # no runtime: ids, Sealed and its envelope, classifications, states, rules, reasoning refs
│   └── src/main/scala/voice/domain/
│       ├── Ids.scala              # prefixed ULIDs and typed references
│       ├── Sealed.scala           # Present | Erased, the envelope, the classification marker
│       ├── Refusal.scala          # the catalogue in contracts/refusals.md
│       ├── participation/         # Participant, Conversation, Concern, ConcernProposal
│       ├── disclosure/            # Contribution, DisclosureGrant, PrivacyFlags, modes and states
│       ├── issues/                # Issue, IssueLink, PolicyOption, EvidenceLink, Development, chronology states
│       ├── deliberation/          # Expression, aggregates and suppression
│       ├── accountability/        # Investigation, Action, Decision, Outcome
│       ├── interests/             # Interest, Notification
│       └── erasure/               # ErasureRequest, Receipt
├── service/                       # every component and Main
│   └── src/main/scala/voice/
│       ├── Main.scala             # the complete inventory
│       ├── sealing/               # Sealing: open and seal through the secret store; the key per participant
│       ├── application/
│       │   ├── participation/     # ParticipantEntity, ConversationEntity, ConcernEntity, ConversationTurnWorkflow, DormancyTimedAction
│       │   ├── disclosure/        # ContributionEntity, DisclosureReviewWorkflow, PrivacyDetectors
│       │   ├── issues/            # IssueEntity, IssueLinkEntity, PolicyOptionEntity, PublicSearchView
│       │   ├── deliberation/      # ExpressionEntity, IssueAggregatesView
│       │   ├── accountability/    # InvestigationEntity, ActionEntity, DecisionEntity, OutcomeEntity, ResearchWorkflow, AccessLogEntity
│       │   ├── interests/         # InterestEntity, FollowersByIssueView, NotificationEntity, InboxView, DevelopmentsConsumer, UpdateDeliveryWorkflow
│       │   ├── erasure/           # ErasureWorkflow, ErasureReceiptsView
│       │   └── reasoning/         # ReasoningClient over services("reasoning"); the reference resolvers
│       ├── agents/                # ConcernExtractionAgent, PrivacyFlaggingAgent, ResearchAgent and their tools
│       ├── identity/              # SessionTokens (HMAC), the citizen ACL, staff roles from the principal
│       └── api/                   # PublicEndpoint, CitizenEndpoint, StaffEndpoint, problem mapping, state labels
│   └── src/test/scala/voice/
│       ├── unit/                  # one suite per component, no runtime
│       ├── features/              # one GherkinSuite per feature file; the step definitions; the stub reasoning fixture
│       ├── integration/           # ReasoningSuite against the real reasoning container
│       ├── live/                  # the live-model suite, tagged
│       └── support/               # RecordedModelProvider, TestIssuer (ES256), SettableClock, TimerProbe wiring, DatabaseDump
├── seed/                          # a client: loads seed/school-transport into reasoning and Voice
web/                               # from `ankka init voice-web --language web`
├── server/server.ts               # the template's process; no routes of its own beyond health
├── src/                           # citizen and staff route trees
├── service.json                   # hosting: web, mount /api → voice
└── Dockerfile
features/                          # living features, one directory per area (R17)
├── participation/ disclosure/ evidence/ deliberation/ action/ feedback/ erasure/
GLOSSARY.md                        # every word a step uses
seed/school-transport/             # sources with licences, evidence, claims, questions, synthetic participants
compose/keycloak/voice-staff.json  # the local staff realm
docker-compose.yml                 # profiles: default, reasoning, staff, graph
Justfile                           # up, up-graph, run, web, seed, deploy-local, check
service.json                       # voice's descriptor
notes/ankka-requests.md            # the requests in research.md, with what each would remove
docs/                              # product, architecture, privacy-and-threat-model, limitations; built by ankka-docs later
```

**Structure Decision**: three sbt modules so that the domain's rules, classifications and refusals
are tested with no runtime and cannot name a component (R18); one service because everything here
must be consistent together and the pilot has one team and one deployable; the interface as the
platform's web hosting kind in `web/` (R13); the seed as a client that is not in the image. The
spec's suggested `modules/*` decomposition is honoured as packages inside `service`, not as
deployables, which the source document itself prefers for the MVP.

## Complexity Tracking

| What grows | Why needed | Simpler alternative rejected because |
|---|---|---|
| A sealing layer of Voice's own (`Sealed`, `Sealing`, a key per participant in the secret store, the erasure workflow's key destruction) | The platform has no field-level erasure yet (spec 042 is a draft) and the definition of done requires one that works on replay, snapshots and rebuilt views (R1) | Personal text in key value entities, erased by overwrite: conversations, reviews and notification streams are sequences other components react to, and backups keep old rows. Waiting for 042: no plan and no date. |
| Asynchronous turns and research runs (two workflows, two polling routes, an SSE route the interface reconnects) | The gateway cuts every response at fifteen seconds and a model turn can exceed it (R5, R15) | A synchronous agent call from the endpoint: works on a laptop, fails in a cluster. Sockets: do not pass the web proxy's mounts. |
| A `ModelProvider` that records and replays (R16) | The clarification asked for a live model with a recorded double; ankka has only a hand-scripted test provider | Scripting `TestModelProvider` by hand for every seed conversation: the scripts drift from what the model says and nobody notices. |

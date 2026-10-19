# Tasks: Voice MVP demonstrator

**Input**: Design documents from `specs/001-voice-mvp/`
**Prerequisites**: plan.md, spec.md, research.md (R1–R19, V1–V15), data-model.md, contracts/ (http-api, refusals, reasoning-integration, deployment), quickstart.md, the living features under `features/` and `GLOSSARY.md`

**Tests**: included. SC-011 requires every acceptance scenario to run automatically, the features are living Gherkin run by `GherkinSuite`, and the family tests at two levels (quickstart tiers 1 and 2). Each story's phase ends with its feature file green.

**Organization**: by user story, in the spec's priority order. Each story is a complete increment the quickstart's tiers can exercise on its own; the earlier stories' components are the only dependencies.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: US1 to US7, from spec.md
- Paths follow plan.md: `modules/domain/src/main/scala/voice/domain/…`, `modules/service/src/main/scala/voice/…`, `modules/service/src/test/scala/voice/…`, `modules/seed/…`, `web/…`

## Conventions that hold in every task

- Handlers return effects; the model is called only from workflow steps (R5); time and actor arrive in the command (R12); no personal value leaves a `Sealed` except at the edge (R1, R2).
- Every wire name, refusal name and state label comes from `contracts/`; a task that needs a name not there adds it to the contract in the same change.
- Every component is registered in `Main.scala` and counted by the inventory test (T031).
- A task that finds the platform behaving otherwise than research.md says writes it into research.md "Found during implementation" and the repository `CLAUDE.md` traps section the same day (principle 9).
- No installation value, secret or hostname in the repository; names only.

---

## Phase 1: Setup (shared infrastructure)

**Purpose**: turn the `ankka init` scaffold into the three-module build and the local environment the plan names.

- [X] T001 Align `project/build.properties` to sbt 1.12.15 (the family pin) and add sbt-dynver, sbt-scalafmt and sbt-native-packager to `project/plugins.sbt`
- [X] T002 Create `project/Dependencies.scala` with every version written once: ankka 0.12.0 (`sdk`, `runtime`, `http`, `agent`, `auth-oidc`, `telemetry-otlp`, `testkit`), munit 1.3.6, Scala 3.9.0
- [X] T003 Rewrite `build.sbt` as three modules `domain`, `service` (depends on domain, Docker packaging, main class `voice.Main`) and `seed` (depends on domain), serialised tests, scalafmt on compile; move the template's `src/` under `modules/service/src/` and delete `Item*.scala` and the three `Item*Suite.scala`
- [X] T004 [P] Add `.scalafmt.conf` and the pre-commit scalafmt hook at `.githooks/pre-commit` (enabled by `just hooks`), as the sibling repositories do
- [X] T005 [P] Write `Justfile` with `up`, `up-graph`, `down`, `run`, `web`, `seed`, `check` (the speckit-bdd checker command from `.specify/extensions/bdd/bdd-config.yml`), `deploy-local`
- [X] T006 [P] Rewrite `docker-compose.yml` with profiles `default` (postgres 5432), `reasoning` (reasoning-postgres 5433, reasoning 9100 from the 0.1.1 image), `staff` (keycloak 8080 importing `compose/keycloak/voice-staff.json`), `graph` (kafka 9094, neo4j 7474/7687, graph-sink) per contracts/deployment.md
- [X] T007 [P] Write `compose/keycloak/voice-staff.json`: realm `voice-staff`, client `voice-staff`, roles `representative`, `reviewer`, `operator`, one local user per role with a development password named in the file (local only, never an installation value)
- [X] T008 [P] Write `.env.local.example` naming `ANKKA_SECRET_KEY`, `VOICE_SESSION_KEY`, `VOICE_INVITE_CODES`, `VOICE_REASONING_URL`, `ANKKA_AUTH_*`, `ANTHROPIC_API_KEY`, `VOICE_DORMANCY_PERIOD`, `SEED_STAFF_TOKEN` with empty values, and add `.env.local`, `target/`, `web/node_modules`, `web/dist` to `.gitignore`
- [X] T009 [P] Scaffold the interface with `ankka init voice-web --language web` into `web/` (generate beside the repository and move in, as the CLI refuses an existing directory), set `web/service.json` to hosting `web`, `processPort` 3000, mount `/api` to `voice`
- [X] T010 [P] Write the repository `CLAUDE.md` (what the repo is, the three modules, how to run each quickstart tier, a dated `## Traps` section seeded with the four findings in plan.md "Summary") and `notes/ankka-requests.md` from research.md "Requests" 1–8, each with what it would remove
- [X] T011 [P] Create `seed/school-transport/README.md` describing the pack's files (`sources.json`, `evidence.json`, `questions.json`, `claims.json`, `participants.json`, `licences/`) and the one Issue, with the conflicting source pair named
- [X] T012 Update `.github/workflows/ci.yml` to run `sbt domain/test service/test` with the compose `default` and `reasoning` profiles up, and `npm ci && npm test` in `web/`; leave `deploy.yml` tag-only

**Checkpoint**: `sbt compile` passes on three empty modules; `just up` starts Postgres, reasoning and Keycloak; `just check` reports 0 findings.

---

## Phase 2: Foundational (blocking prerequisites)

**Purpose**: the pieces every story stands on: identifiers, sealing, refusals, identity, the test harness, the endpoints' shells and the interface shell.

**⚠️ CRITICAL**: no user story work begins until this phase is green.

### Domain

- [X] T013 [P] Write `modules/domain/src/main/scala/voice/domain/Ids.scala`: prefixed ULID ids (`prt-`, `cnv-`, `cnc-`, `ctb-`, `iss-`, `pub-`, `pol-`, `exp-`, `inv-`, `act-`, `dec-`, `out-`, `dev-`), typed references, never derived from a person (R3)
- [X] T014 [P] Write `modules/domain/src/main/scala/voice/domain/Sealed.scala`: `Sealed[A]` as `Present(subject, ciphertext)` / `Erased(subject)`, the JSON envelope, the `Personal`/`Restricted`/`Public` classification markers, `render` to "redacted or unavailable" (R1, FR-056)
- [X] T015 [P] Write `modules/domain/src/main/scala/voice/domain/Refusal.scala`: every rule name in contracts/refusals.md as a value with its HTTP status and the names it carries; a test in `modules/domain/src/test/scala/voice/domain/RefusalCatalogueSuite.scala` parses the contract and fails on a missing or extra name
- [X] T016 Write `modules/domain/src/test/scala/voice/domain/SealedSuite.scala` (V1: round-trip through `Codecs.serializer`, envelope shape, `Erased` renders) and `ClassificationSuite.scala` (FR-059: lists every event and state type across `voice.domain` and fails on an unclassified text field (FR-059), on an event type without `Provenance` (FR-062) and on any field shaped like an ideological, partisan or demographic profile (FR-035); grows with each story)
- [X] T017 [P] Write `modules/domain/src/main/scala/voice/domain/StateLabels.scala`: the seven chronology states of FR-042 and the contribution, investigation, action and notification state machines with their allowed transitions (contracts/refusals.md names the refused ones); and `Provenance.scala`: actor, at, causationId, correlationId, carried in command metadata and recorded on every event (FR-062)

### Service: sealing, identity, refusals at the edge

- [X] T018 Write `modules/service/src/main/scala/voice/sealing/Sealing.scala`: `seal(subject, value)` and `open(subject, sealed)` through `SecretStore` under `participant/<id>/key` (AES-256-GCM, key minted on first use, `Erased` when the key is gone); test in `modules/service/src/test/scala/voice/unit/SealingSuite.scala` against `InMemorySecretStore` and Postgres (V2)
- [X] T019 [P] Write `modules/service/src/main/scala/voice/identity/SessionTokens.scala`: mint and verify `base64url(participantId "." expiresAt) "." base64url(hmac-sha256)` under `VOICE_SESSION_KEY`, `VOICE_SESSION_HOURS` default 12, verification with no I/O (R9); test in `modules/service/src/test/scala/voice/unit/SessionTokensSuite.scala`
- [X] T020 [P] Write `modules/service/src/main/scala/voice/identity/CitizenAcl.scala` (`Acl.Authenticate` with an authenticator of Voice's own over the session token, no I/O, the participant as principal) and `StaffAcl.scala` (`Oidc.authenticate()` from `ankka-auth-oidc`, roles `representative`/`reviewer`/`operator` from the principal, `require(role)` helper) (R10)
- [X] T021 [P] Write `modules/service/src/main/scala/voice/api/Problems.scala`: map a `Refusal` to the HTTP status and problem body of contracts/refusals.md, and `Labels.scala` rendering state labels from `StateLabels` (FR-061)
- [X] T022 Write `modules/service/src/main/scala/voice/api/{PublicEndpoint,CitizenEndpoint,StaffEndpoint}.scala` as shells: paths `/public`, `/me`, `/staff`, ACLs `AllowAll`, `CitizenAcl`, `StaffAcl`, the `GET /me/notice` route (FR-003) and nothing else yet; `GET /me/notice` text in `modules/service/src/main/resources/notice.md`

### Service: cross-cutting components

- [X] T023 [P] Write `modules/service/src/main/scala/voice/application/accountability/AccessLogEntity.scala` (event sourced, id = UTC day, `AccessRecorded`) and `AccessLogView.scala`; `GET /staff/access-log` on the staff endpoint (FR-005)
- [X] T024 [P] Write `modules/service/src/main/scala/voice/application/participation/StaffAccountEntity.scala` (key value, id = issuer subject, displayName, authority, active; the role is read from the token's realm roles and never stored) and operator routes `PUT /staff/accounts/{principal}`, `GET /staff/accounts`; refusals `staff.inactive`, `staff.not-operator`
- [X] T025 [P] Write `modules/service/src/main/scala/voice/application/reasoning/ReasoningClient.scala` over `services("reasoning")` (local resolution by `VOICE_REASONING_URL`, V4): the record reads of contracts/reasoning-integration.md by id, the writes as `voice-evidence-pack`, 200 and 201 alike, 409 `id.held-by-another` surfaced, 503 `graph.unavailable` mapped to "graph tier absent"
- [X] T026 Write `modules/service/src/main/scala/voice/Main.scala` registering every component as it is written, with `TimerRuntime` and the OTLP telemetry; every client call from an endpoint, step, consumer or timed action passes the request's metadata on with `withMetadata` so `Provenance` reaches every event (FR-062); test `modules/service/src/test/scala/voice/unit/InventorySuite.scala` counts registrations against the component list in data-model.md

### Test support

- [X] T027 [P] Write `modules/service/src/test/scala/voice/support/RecordedModelProvider.scala`: a `ModelProvider` that replays `src/test/resources/model/<suite>/<sha256 of request>.json`, records under `VOICE_MODEL_RECORD=1` through `AnthropicProvider.fromEnv()`, and fails naming the request when a fixture is missing (R16, V8)
- [X] T028 [P] Write `modules/service/src/test/scala/voice/support/TestIssuer.scala`: an in-suite ES256 key, a JWKS the `Oidc` verifier accepts, `token(role, subject)` (V10)
- [X] T029 [P] Write `modules/service/src/test/scala/voice/support/{SettableClock,DatabaseDump,Timers}.scala`: a clock the endpoints are given (R12), a dump of every journal, snapshot, state and view table as text for the "no plaintext" assertions (SC-008), and a `TimerRuntime(pollInterval = 200.millis, observer = TimerProbe())` wiring (V15)
- [X] T030 [P] Write `modules/service/src/test/scala/voice/support/StubReasoning.scala`: a small HTTP fixture answering the record reads of contracts/reasoning-integration.md from `seed/school-transport/`, including the conflicting pair and an unsupported question
- [X] T031 Write `modules/service/src/test/scala/voice/features/World.scala` and `Harness.scala`: boots `AnkkaTestKit` with the recorded provider, the test issuer, the in-memory secret store, the stub reasoning and the short-poll timers; HTTP helpers per endpoint; named participants ("Alice", "Bob", "Carol") with their tokens and credentials; `restartService()` and view-version-bump helpers; one `GherkinSuite(file)` subclass per feature file is added in each story (R17); after every scenario the harness reads each public route touched and asserts no private original, private field, participant id or contact detail in body, snippet, count or error (SC-002, SC-003), so every suite runs the check
- [X] T032 Write `modules/service/src/test/scala/voice/features/CommonSteps.scala`: the Given/When/Then steps every feature shares (a participant, a session, a public issue "A", the operator, a representative, "nothing fails")

### Interface shell

- [X] T033 [P] Write `web/src/api/client.ts` (fetch wrapper for `/api/public`, `/api/me`, `/api/staff`, bearer tokens from memory, problem bodies to typed errors, SSE helper with reconnect and `after`), `web/src/app/routes.tsx` (citizen, public and staff route trees), `web/src/app/session.ts` (session token in memory only, recovery credential shown once)
- [X] T034 [P] Write `web/src/pages/Notice.tsx` (the privacy and AI notice, read from `GET /me/notice`, acknowledged before the first message, FR-003) and `web/src/components/StateLabel.tsx` rendering only the server's labels (FR-061); `npm test` (`node --test`, the template's runner) over both

**Checkpoint**: tier 1 of the quickstart runs for the domain; `AnkkaTestKit` boots the service with three empty endpoints; the interface shell builds.

---

## Phase 3: User Story 1 — Hear: a private conversation becomes a confirmed concern (Priority: P1) 🎯 MVP

**Goal**: a visitor with an invite code becomes a pseudonymous participant, talks about a concern, sees a proposal with its sources, edits and confirms it, and nothing is public; recovery by credential.

**Independent test**: `features/participation/private-conversation.feature` green through `sbt 'service/testOnly voice.features.ParticipationSuite'`; tier 4 step 1 in the browser.

### Domain

- [X] T035 [P] [US1] Write `modules/domain/src/main/scala/voice/domain/participation/Participant.scala`: state (`recoveryHash`, `createdAt`, `lastSessionAt`, `erased`), events `ParticipantCreated`, `SessionStarted`, `SessionRecovered`, `ErasureCompleted`; the invite code is checked at the edge, never stored
- [X] T036 [P] [US1] Write `modules/domain/src/main/scala/voice/domain/participation/{Conversation,ConcernProposal,Concern}.scala`: messages as `Sealed[String]`, proposals (original statement, summary, elements with source spans, confidence, suggested Issues), events `MessageReceived`, `ReplySent`, `ProposalMade`, `ProposalEdited`, `ProposalConfirmed`, `ProposalDiscarded`; `Concern` holds the edited meaning sealed and `confirmedBy = Participant` (FR-008, FR-009)

### Service

- [X] T037 [US1] Write `modules/service/src/main/scala/voice/application/participation/ParticipantEntity.scala` (commands `create`, `startSession`, `recover`, `markErased`; queries `summary`, `lastSession`) with `modules/service/src/test/scala/voice/unit/ParticipantEntitySuite.scala`
- [X] T038 [US1] Write `modules/service/src/main/scala/voice/application/participation/{ConversationEntity,ConcernEntity}.scala` with unit suites; refusals `conversation.not-owned`, `proposal.not-found`, `proposal.already-confirmed` from contracts/refusals.md
- [X] T039 [P] [US1] Write `modules/service/src/main/scala/voice/agents/ConcernExtractionAgent.scala`: `MemoryProvider.none`, the transcript passed per turn (R5), `thenReplyAs[ConcernProposalDraft]`, a clarifying-question mode that proposes no position, cause or culprit (FR-006), instructions in a message treated as content; prompt in `modules/service/src/main/resources/prompts/concern-extraction.md`
- [X] T040 [US1] Write `modules/service/src/main/scala/voice/application/participation/ConversationTurnWorkflow.scala` (id `cnv-…:turn-<seq>`, step `extract` calls the agent under a declared step timeout, V7, then `apply` records the reply or proposal on the conversation, `Thinking`/`Replied`/`Failed` state; a repeated start addresses the running turn, R7) with a workflow testkit suite using `TestModelProvider`
- [X] T041 [US1] Add the session routes to `CitizenEndpoint.scala`: `POST /me/sessions` (invite from `VOICE_INVITE_CODES`, participant created, recovery credential minted and hashed, shown once), `POST /me/sessions/recover`, `POST /me/sessions/renew`; refusals `session.invite.invalid`, `session.recovery.invalid`, `participant.erased` 410
- [X] T042 [US1] Add the conversation routes to `CitizenEndpoint.scala`: `POST /me/conversations`, `GET /me/conversations`, `GET /me/conversations/{c}`, `POST …/messages` → 202 `{turn, message}` starting the turn workflow, `GET …/turns/{turn}`, `POST …/proposals/{p}/edit|confirm|discard`, `GET /me/concerns`; sealing and opening at the edge only
- [X] T043 [US1] Add `GET /me/conversations/{c}/stream` (SSE `message`, `proposal`, `turn` events with `after=<seq>`, ended by Voice before the gateway's fifteen seconds so the client reconnects, R5) to `CitizenEndpoint.scala`

### Tests

- [X] T044 [US1] Record the model fixtures for the seed conversations into `modules/service/src/test/resources/model/participation/` with `VOICE_MODEL_RECORD=1` (needs `ANTHROPIC_API_KEY` once); commit the fixtures
- [X] T045 [US1] Write `modules/service/src/test/scala/voice/features/ParticipationSuite.scala` (`GherkinSuite("features/participation/private-conversation.feature")`) and `ParticipationSteps.scala` for its eight scenarios, including `restartService()` between confirm and recovery (V3) and the public-search assertion that nothing is found (uses the empty `public-search` view from T063 or asserts on the public endpoint's 404 until then)
- [X] T046 [P] [US1] Write `modules/service/src/test/scala/voice/live/ConversationLiveSuite.scala`, tagged `Live`, excluded by default: the seed conversations against the live model, asserting reply shape and that proposal source spans fall inside the citizen's messages (SC-012 groundwork)

### Interface

- [X] T047 [P] [US1] Write `web/src/pages/citizen/{Start,Conversation,Proposal,Recover}.tsx`: invite entry, notice acknowledgement, the conversation with the reconnecting stream, the proposal card with original statement, summary, highlighted source passages, confidence and suggested Issues, edit and confirm, the recovery credential shown once; `node --test` suites (the template's runner) for the proposal card's logic and the stream reconnect

**Checkpoint**: Story 1 demonstrable alone: tier 1 and 2 for participation, tier 4 step 1.

---

## Phase 4: User Story 2 — Share: the citizen approves exactly what is shared and with whom (Priority: P2)

**Goal**: four sharing modes with plain-language text, exact-text approval, deterministic plus agent privacy flags, reviewer moderation with a second step when flagged, association to an Issue, withdrawal; the public routes never return a private field.

**Independent test**: `features/disclosure/exact-approval.feature` green; tier 4 step 2.

### Domain

- [X] T048 [P] [US2] Write `modules/domain/src/main/scala/voice/domain/disclosure/{Contribution,DisclosureGrant,PrivacyFlags}.scala`: modes `Private`, `DeIdentified`, `PublicAttribution`, `RepresentativeOnly`; states Drafting → Approved by citizen → Under moderation → Published / Rejected → Withdrawn; the approved text sealed; the grant (mode, audience, approved text hash, at) as its own value (FR-011 to FR-018)
- [X] T049 [P] [US2] Write `modules/domain/src/main/scala/voice/domain/issues/{Issue,IssueLink}.scala`: Issue state and events `IssueOpened`, `ContributionPublished(pub, text)`, `ContributionAttributed`, `ContributionRestricted`, `ContributionWithdrawn(pub)`, `AuthorErased(pub, cause)`, counts; `IssueLink` (`pub-…` → contribution, participant) as the only join (R3); the public copy is the one plaintext (R2)

### Service

- [X] T050 [US2] Write `modules/service/src/main/scala/voice/application/disclosure/ContributionEntity.scala` with unit suite; refusals `contribution.text.changed` (exact text, FR-012), `contribution.second-step.required` 409, `contribution.not-owned`, `contribution.state.invalid`
- [X] T051 [P] [US2] Write `modules/service/src/main/scala/voice/application/disclosure/PrivacyDetectors.scala` (deterministic patterns: names, addresses, phone, email, special-category words from `modules/service/src/main/resources/privacy/patterns.txt`) and `modules/service/src/main/scala/voice/agents/PrivacyFlaggingAgent.scala` (one typed call, `thenReplyAs[PrivacyFlags]`, R6) with unit suites
- [X] T052 [US2] Write `modules/service/src/main/scala/voice/application/issues/{IssueEntity,IssueLinkEntity}.scala` with unit suites: open, publish/attribute/restrict/withdraw a copy, counts; `IssueLink` erased by overwriting with the empty link (FR-055)
- [X] T053 [US2] Write `modules/service/src/main/scala/voice/application/disclosure/DisclosureReviewWorkflow.scala` (id = contribution id; steps `check` → `awaitReviewer` → `awaitSecondStep` when flagged → `publish` or `reject`; `withdraw` from any step; reasoning first, Issue second is not involved here; the `pub-…` minted at publish) with a workflow testkit suite using the recorded or scripted provider
- [X] T054 [US2] Add the disclosure routes to `CitizenEndpoint.scala`: `GET /me/concerns/{c}/sharing`, `POST /me/concerns/{c}/contributions`, `POST /me/contributions/{ctb}/submit`, `GET /me/contributions/{ctb}`, `POST /me/contributions/{ctb}/issue` (accept a suggestion or ask for a new Issue, FR-017), `POST /me/contributions/{ctb}/withdraw`
- [X] T055 [US2] Add the reviewer routes to `StaffEndpoint.scala`: `GET /staff/reviews`, `POST /staff/reviews/{ctb}/enhanced|approve|reject`, each read of a restricted record logged to `AccessLogEntity` (FR-005), roles enforced
- [X] T056 [US2] Add the first public routes to `PublicEndpoint.scala`: `GET /public/issues` and `GET /public/issues/{issue}` (the `IssuePage` of contracts/http-api.md with contributions, attribution opened at the edge, `authorErased`, counts; evidence, options, chronology and aggregates filled by later stories), `public.not-found` 404

### Tests

- [X] T057 [US2] Record the privacy-flagging fixtures into `modules/service/src/test/resources/model/disclosure/`
- [X] T058 [US2] Write `modules/service/src/test/scala/voice/features/DisclosureSuite.scala` and `DisclosureSteps.scala` for the nine scenarios of `features/disclosure/exact-approval.feature`, relying on the harness-wide SC-002 check (T031) over the disclosure and public routes

### Interface

- [X] T059 [P] [US2] Write `web/src/pages/citizen/{Share,Contribution}.tsx` (the four modes in plain language, the exact text to approve with edit-out, submit, the review outcome and reason, Issue suggestion accept or new, withdraw) and `web/src/pages/staff/Reviews.tsx` (queue, flags, enhanced review, approve into an Issue or a new one, reject with reason)

**Checkpoint**: a confirmed concern becomes a published de-identified contribution on a public Issue, reviewed by a person; the original never appears.

---

## Phase 5: User Story 3 — Understand: explore source-backed evidence and uncertainty (Priority: P3)

**Goal**: evidence links with provenance, claim explanations assembled from reasoning records, visible contradiction, abstention on unsupported questions, keyword search for anyone without a session.

**Independent test**: `features/evidence/source-backed.feature` green against the stub; tier 3 `ReasoningSuite` against the real reasoning container; tier 4 steps 3 and 7.

### Seed pack and seed client

- [X] T060 [P] [US3] Write `seed/school-transport/{sources.json,evidence.json,questions.json,claims.json,licences/}`: five to fifteen sources with licence and attribution, one Issue's question with hypotheses, the conflicting pair of sources on one hypothesis, one unsupported question, ids per the scheme `voice.<issue>.q<n>`, `voice.src.<slug>`, `voice.<issue>.c<n>`
- [X] T061 [US3] Write `modules/seed/src/main/scala/voice/seed/Seed.scala`: loads the pack into reasoning (`POST /sources`, `/questions`, `/evidence`, `/claims` as `voice-evidence-pack`, idempotent by id) and the Issue with its question and evidence links into Voice through the staff endpoint with an operator token from `SEED_STAFF_TOKEN`; `just seed` runs it

### Domain and service

- [X] T062 [P] [US3] Write `modules/domain/src/main/scala/voice/domain/issues/{EvidenceLink,ReasoningRefs}.scala`: `QuestionRef`, `HypothesisRef`, `SourceRef`, `EvidenceRef`, `ClaimRef`, `BeliefRevisionRef` as typed wrappers; `EvidenceLink` with Voice's state (candidate, accepted, rejected, superseded), reviewer, time, and Voice's own scope, authority and limitations (FR-021, FR-029); Issue events `QuestionLinked`, `EvidenceProposed`, `EvidenceAccepted`, `EvidenceRejected`, `EvidenceSuperseded`, `ReviewPromptRaised`, `ReviewPromptResolved` (accept and supersede raise a `ReviewPrompt` for every claim and decision deriving from the evidence, FR-028)
- [X] T063 [US3] Write `modules/service/src/main/scala/voice/application/issues/PublicSearchView.scala`: keyed view over `issue`, `policy-option` and the seed's sources, declared query `search` with `to_tsvector` (R14, V13), no sealed or restricted column; the build-time test `public.search.filter` in `modules/service/src/test/scala/voice/unit/PublicSearchViewSuite.scala` over the classification table
- [X] T064 [US3] Write `modules/service/src/main/scala/voice/application/reasoning/Explanations.scala`: assemble a `ClaimExplanation` from `GET /claims/{c}`, its evidence and the claims with opposing stances on the same hypotheses (V6), with the abstention rule when nothing supports a question (FR-025); `ReasoningClient` writes for the reviewer's evidence, claims and questions, reasoning first and Issue second (contracts/reasoning-integration.md)
- [X] T065 [US3] Add the reviewer evidence routes to `StaffEndpoint.scala`: `GET /staff/issues/{issue}/evidence`, `POST …/evidence`, `POST …/evidence/{e}/accept|reject|supersede`, `GET …/review-prompts`, `POST …/review-prompts/{p}/resolve`, `POST …/claims`, `POST …/questions`; refusals `review-prompt.not-open`, `evidence.not-candidate`, `evidence.superseded-by.unknown`, `claim.derives-from.empty`
- [X] T066 [US3] Add the public evidence routes to `PublicEndpoint.scala`: `GET /public/issues/{issue}/evidence/{e}` (`EvidenceCard`), `GET /public/issues/{issue}/claims/{c}` (`ClaimExplanation` with contradiction and abstention), `GET /public/sources/{s}`, `GET /public/search` (no session, no interest created, FR-045); the optional graph read behind `learned` when the tier answers (R4)

### Tests

- [X] T067 [US3] Write `modules/service/src/test/scala/voice/features/EvidenceSuite.scala` and `EvidenceSteps.scala` for the eight scenarios of `features/evidence/source-backed.feature` against `StubReasoning`, including the review prompt raised by acceptance and by supersession, including the verbatim-passage assertion against the seed (SC-004)
- [X] T068 [US3] Write `modules/service/src/test/scala/voice/integration/ReasoningSuite.scala` (tier 3, needs the `reasoning` compose profile): V4, V5, V6; the seed pack through the real service yields the contradiction and the abstention; with the `graph` profile the `learned` read answers

### Interface

- [X] T069 [P] [US3] Write `web/src/pages/public/{Issue,Evidence,Claim,Search}.tsx`: the Issue page with evidence cards (source, version, date, scope, authority, passage, limitations), claim explanation with support and contradiction side by side, the abstention text, search without a session; `web/src/pages/staff/Evidence.tsx` for candidates, accept, reject, supersede, claims and questions

**Checkpoint**: the public Issue shows where every statement came from and where sources disagree; tier 3 passes.

---

## Phase 6: User Story 4 — Deliberate: express priorities and trade-offs as opinions, not evidence (Priority: P4)

**Goal**: one expression per participant per Issue, aggregates labelled with count, method, window and representativeness caveat, small cells suppressed, never read by evidence views; policy options with costs, benefits and open questions.

**Independent test**: `features/deliberation/opinions-not-evidence.feature` green; tier 4 step 4.

- [X] T070 [P] [US4] Write `modules/domain/src/main/scala/voice/domain/deliberation/{Expression,Aggregates}.scala` (priority 1–5, trade-offs per option, considerations sealed, withdrawn; the one plain level beside the sealed text, R8; suppression below `VOICE_SUPPRESSION_THRESHOLD`) and `modules/domain/src/main/scala/voice/domain/issues/PolicyOption.scala` (title, description, costs, benefits, open questions, FR-032)
- [X] T071 [US4] Write `modules/service/src/main/scala/voice/application/deliberation/ExpressionEntity.scala` (id `exp-…`, one per participant per Issue, commands priority, trade-offs, considerations, withdraw) with unit suite; refusals `expression.level.range`, `expression.option.unknown`, `expression.not-owned`
- [X] T072 [US4] Write `modules/service/src/main/scala/voice/application/deliberation/IssueAggregatesView.scala` over `expression`: counts per level and option, participant count, window; suppression applied at read in the endpoint (`aggregate.suppressed` is an answer) with `modules/service/src/test/scala/voice/unit/IssueAggregatesSuite.scala` over five synthetic participants
- [X] T073 [P] [US4] Write `modules/service/src/main/scala/voice/application/issues/PolicyOptionEntity.scala` (key value, `pol-…`) and `PUT /staff/issues/{issue}/options/{option}` for reviewer and representative on `StaffEndpoint.scala`; the Issue event `OptionAdded`
- [X] T074 [US4] Add the deliberation routes to `CitizenEndpoint.scala`: `GET /me/issues/{issue}/expression`, `POST …/expression/priority|trade-offs|considerations|withdraw`; add aggregates with caveat text and options to the public `IssuePage` (FR-033) and to `GET /staff/issues/{issue}` for the representative with every measure labelled and no population claim (FR-034, FR-035)
- [X] T075 [US4] Write `modules/service/src/test/scala/voice/features/DeliberationSuite.scala` and `DeliberationSteps.scala` for the seven scenarios of `features/deliberation/opinions-not-evidence.feature`, including the assertion that no evidence route or claim explanation reads an expression (SC-005)
- [X] T076 [P] [US4] Write `web/src/pages/citizen/Express.tsx` (priority, trade-offs per option, considerations, withdraw) and the aggregates panel in `web/src/pages/public/Issue.tsx` and `web/src/pages/staff/IssueDashboard.tsx` with count, method, window and caveat beside every figure

**Checkpoint**: opinions are visible, labelled and apart from evidence.

---

## Phase 7: User Story 5 — Act: a representative investigates and records an accountable action (Priority: P5)

**Goal**: research suggestions that stay suggestions, investigations and dated actions recorded only by a representative, decisions and outcomes, the seven-state chronology.

**Independent test**: `features/action/human-authority.feature` green including the role outline; tier 4 step 5.

- [X] T077 [P] [US5] Write `modules/domain/src/main/scala/voice/domain/accountability/{Investigation,Action,Decision,Outcome}.scala`: investigation states `Suggested` → `Open` → `Closed` with tasks (text, owner, due); action with authority, owner, date (not future, not before the Issue), rationale, public summary, state (never backwards); decision; outcome linked to an action with measured, limitations, `claimsResolved` false by default (FR-036 to FR-043)
- [X] T078 [US5] Write `modules/service/src/main/scala/voice/application/accountability/{InvestigationEntity,ActionEntity,DecisionEntity,OutcomeEntity}.scala` with unit suites covering every refusal in contracts/refusals.md "Investigation and action" (`action.date.future`, `action.date.before-issue`, `action.fields.required`, `action.state.backwards`, `outcome.action.not-held`, `investigation.suggested.not-approved`)
- [X] T079 [P] [US5] Write `modules/service/src/main/scala/voice/agents/ResearchAgent.scala` (read-only tools over the Issue, its evidence and the reasoning records, and nothing else: the suite asserts the tool list is exactly the public and staff read routes, FR-027; `thenReplyAs[ResearchSuggestions]` with the agent's stated reason and the records it read, R15) and `modules/service/src/main/scala/voice/application/accountability/ResearchWorkflow.scala` (id `inv-…` minted at the request; one step; records an `Investigation` in `Suggested` per suggestion) with testkit suites
- [X] T080 [US5] Add the representative routes to `StaffEndpoint.scala`: `GET /staff/queue` (new Issues, pending reviews, open review prompts, open investigations, tasks awaiting an external party), `GET /staff/issues/{issue}`, `POST /staff/issues/{issue}/research` → 202 `{run}`, `GET /staff/research/{run}`, `POST /staff/issues/{issue}/investigations`, `POST /staff/investigations/{inv}/tasks`, `POST /staff/issues/{issue}/actions`, `POST /staff/actions/{a}/state`, `POST /staff/issues/{issue}/decisions`, `POST /staff/actions/{a}/outcomes`; `investigation.not-representative` 403 for every other role (SC-006)
- [X] T081 [US5] Add the Issue events `InvestigationOpened`, `ActionRecorded`, `DecisionRecorded`, `OutcomeRecorded` to `IssueEntity.scala`, the chronology (`GET /public/issues/{issue}/chronology`, seven states, actor kind, FR-042) to `PublicEndpoint.scala`, and investigations, actions, decisions and outcomes to the `IssuePage`
- [X] T082 [US5] Record the research fixtures into `modules/service/src/test/resources/model/action/`; write `modules/service/src/test/scala/voice/features/ActionSuite.scala` and `ActionSteps.scala` for the eight scenarios of `features/action/human-authority.feature`, the Scenario Outline running once per role (reviewer, citizen, operator)
- [X] T083 [P] [US5] Write `web/src/pages/staff/{Queue,Research,Investigation,Action}.tsx` (ask for research, the suggestions marked as suggestions, open an investigation, record an action with every required field, decisions, outcomes) and the chronology on `web/src/pages/public/Issue.tsx` with the server's labels only

**Checkpoint**: a dated, attributed action exists on the public Issue, recorded by a representative and no one else.

---

## Phase 8: User Story 6 — Return: consenting followers receive one truthful update (Priority: P6)

**Goal**: following is an explicit choice, developments minted once per authoritative event, exactly one update per follower per development under redelivery and crashes, wording rendered from the record, immediate inbox delivery only.

**Independent test**: `features/feedback/one-truthful-update.feature` green with the forced redelivery and the crash between prepare and deliver (SC-007); tier 4 step 6.

- [X] T084 [P] [US6] Write `modules/domain/src/main/scala/voice/domain/interests/{Interest,Notification}.scala` (delivery `InboxImmediate` only; notification text and why sealed; states `Prepared`, `Delivered`, `Failed(reason, attempts)`, `Read`) and `modules/domain/src/main/scala/voice/domain/issues/Development.scala` (kinds `ActionRecorded`, `EvidenceAccepted`, `DecisionRecorded`, `OutcomeRecorded`, `UpdatePublished`; materiality `Material`/`Minor`)
- [X] T085 [US6] Add `developmentId` minting (once per authoritative event, `DevelopmentPublished`) and the `UpdatePublished(developmentId, text, by)` narrative event to `IssueEntity.scala`; `POST /staff/issues/{issue}/updates` for the representative on `StaffEndpoint.scala` (FR-044, `update.text.required`)
- [X] T086 [P] [US6] Write `modules/service/src/main/scala/voice/application/interests/{InterestEntity,FollowersByIssueView}.scala` (key value `participantId:issueId`, created only by the follow command, FR-045; the view rows removed on delete) with unit suite; refusals `interest.delivery.invalid`, `interest.not-owned`
- [X] T087 [US6] Write `modules/service/src/main/scala/voice/application/interests/NotificationEntity.scala` (id `participantId:developmentId`; a second `prepare` refused as `notification.duplicate`, V14) and `InboxView.scala` with unit suites
- [X] T088 [US6] Write `modules/service/src/main/scala/voice/application/interests/Renderer.scala`: deterministic wording per development kind from the record, with record links and why it was sent to this follower, never stronger than the record and never "solved" (FR-047, FR-061); unit suite over every kind
- [X] T089 [US6] Write `modules/service/src/main/scala/voice/application/interests/{DevelopmentsConsumer,UpdateDeliveryWorkflow}.scala`: consumer over `issue` events starts the workflow under the `developmentId` (R7); steps `resolveIssue` → `materiality` → `followers` (waits until `followers-by-issue` reflects interests before the development, `development.stale` retry then fall back to ids, R11) → `render` → `prepare` → `end`; testkit suite with a redelivered event
- [X] T090 [US6] Add the interest and inbox routes to `CitizenEndpoint.scala`: `GET /me/interests`, `PUT /me/interests/{issue}`, `DELETE /me/interests/{issue}`, `GET /me/inbox`, `POST /me/inbox/{dev}/read`, `GET /me/inbox/stream` (SSE `notification`)
- [X] T091 [US6] Write `modules/service/src/test/scala/voice/features/FeedbackSuite.scala` and `FeedbackSteps.scala` for the seven scenarios of `features/feedback/one-truthful-update.feature`, with a forced redelivery of the Issue event and a `restartService()` between `prepare` and delivery yielding one update (SC-007)
- [X] T092 [P] [US6] Write `web/src/pages/citizen/{Follow,Inbox}.tsx` (follow and stop following from the Issue page, the inbox with why each update was sent and its record links, live through the inbox stream) and the narrative update form in `web/src/pages/staff/IssueDashboard.tsx`

**Checkpoint**: Alice gets one update, Bob gets none, and the update says no more than the record.

---

## Phase 9: User Story 7 — Erase: a citizen withdraws and their personal data is shredded (Priority: P7)

**Goal**: erasure by request or by dormancy through one workflow; protected fields unreadable after replay, restart, snapshot restore and view rebuild; the public Issue keeps its anonymous history with the cause of each erased author; the receipt reproduces nothing erased.

**Independent test**: `features/erasure/field-erasure.feature` green including the two dormancy scenarios and the database dump assertion (SC-008); tier 4 step 8.

- [X] T093 [P] [US7] Write `modules/domain/src/main/scala/voice/domain/erasure/{ErasureRequest,Receipt}.scala`: cause `Requested`/`Dormancy`, verification `SessionVerified`/`HeldForOperator`/`OperatorVerified`/`NotRequired`, scope, retained `pub-…` references with the residue result, steps completed, `Receipt(cause, classes erased, counts, authorisedBy, at, verifiedBy)` with no free-text field; the build-time test `erasure.receipt.personal` in `modules/domain/src/test/scala/voice/domain/ReceiptSuite.scala`
- [X] T094 [US7] Write `modules/service/src/main/scala/voice/application/erasure/ErasureWorkflow.scala` (id = participantId; steps `verify` (skipped for `Dormancy`) → `inventory` → `revoke` → `withdraw` (attributed and restricted copies withdrawn; each retained de-identified copy marked `AuthorErased(pub, cause)`) → `residue` → `destroyKey` → `purgeDerived` → `complete`; every step idempotent; `HeldForOperator` pauses before `inventory`) with a testkit suite; refusals `erasure.unverified` 202, `erasure.already-requested`
- [X] T095 [US7] Write `modules/service/src/main/scala/voice/application/erasure/Residue.scala` (Issue counts and distinctive combinations checked, suppression applied, FR-057) and `ErasureReceiptsView.scala` over the workflow state, with unit suites
- [X] T096 [US7] Add `AuthorErased(pub, cause)` handling and `authorsErased` counts by cause to `IssueEntity.scala` and the `IssuePage` (`authorErased` per contribution, counts by present, withdrew, dormant, FR-064); `ContributionWithdrawn` keeps the reference and drops the text
- [X] T097 [US7] Write `modules/service/src/main/scala/voice/application/participation/DormancyTimedAction.scala` (`dormancy-check`: reads the participant's last session, `dormancy.reset` done when a later session exists, otherwise starts the erasure workflow with cause `Dormancy`; safe to repeat, R19) and schedule or replace `dormancy:<participantId>` for `VOICE_DORMANCY_PERIOD` (default 180d) at every session start and recovery in `CitizenEndpoint.scala` (FR-063, V15)
- [X] T098 [US7] Add the data-rights routes to `CitizenEndpoint.scala` (`GET /me/data`, `POST /me/erasure`, `GET /me/erasure`; `participant.erased` 410 on every later call) and the operator routes to `StaffEndpoint.scala` (`GET /staff/erasures` with cause, `POST /staff/erasures/{p}/verify`; `erasure.not-operator`)
- [X] T099 [US7] Make every edge that opens a `Sealed` render `Erased` as "redacted or unavailable" and never fail (FR-056) across `PublicEndpoint.scala`, `CitizenEndpoint.scala`, `StaffEndpoint.scala`, `Renderer.scala`; suppress update preparation for an erased participant in `UpdateDeliveryWorkflow.scala`
- [X] T100 [US7] Write `modules/service/src/test/scala/voice/features/ErasureSuite.scala` and `ErasureSteps.scala` for the ten scenarios of `features/erasure/field-erasure.feature`: Alice and Bob through Stories 1 to 6, erase Alice; replay, `restartService()`, view rebuild by version bump and the `DatabaseDump` assertion find no plaintext (SC-008); Carol erased by the dormancy timer with `VOICE_DORMANCY_PERIOD` in seconds and the cause on the retained copy; a session one poll earlier resets the timer
- [X] T101 [P] [US7] Write `web/src/pages/citizen/DataRights.tsx` (what is held, what will be erased, what is retained and why, request, status) and `web/src/pages/staff/Erasures.tsx` (held requests, verify, receipts with cause and no personal value); "redacted or unavailable" rendering in `web/src/components/SealedField.tsx`

**Checkpoint**: the definition of done's erasure step holds against everything the earlier stories created.

---

## Phase 10: Polish and cross-cutting

**Purpose**: the descriptors, the deployment tier, the documents the family requires, and the measurements the spec asks for.

- [X] T102 [P] Finish `service.json` per contracts/deployment.md (`voice:${VERSION}`, runtime 0.12.0, the env list with `secretKeyRef` to `voice-secrets`, `VOICE_DORMANCY_PERIOD`, resources) and `web/service.json`; a test in `modules/service/src/test/scala/voice/unit/DescriptorSuite.scala` reads both and asserts no value that looks like a secret or hostname
- [ ] T103 [P] Write `scripts/deploy-local.sh` behind `just deploy-local`: build both images, `kind load`, apply reasoning's descriptor, set the project secret names, apply `service.json` and `web/service.json`, expose `voice-web`; record tier 5 in quickstart.md "What was run and seen"
- [X] T104 [P] Write `docs/product.md`, `docs/architecture.md` (components, the sealing layer, the asynchronous turn, reasoning as a service caller), `docs/privacy-and-threat-model.md` (V12: the model provider's data terms before any real text; the invite code is not identity; what each audience can read) and `docs/limitations.md` (the spec's Out of Scope; erasure is Voice's until ankka spec 042 ships; search is a sequential scan; the graph tier is optional)
- [X] T105 [P] Write `modules/service/src/test/scala/voice/unit/LogHygieneSuite.scala`: capture logs and traces across one features run and assert no raw prompt, personal free text, contact detail or special-category word from the seed appears (FR-060); `modules/service/src/main/resources/logback.xml` with the fields allowed
- [X] T106 [P] Write `seed/school-transport/participants.json` (three to eight synthetic participants with their conversations) and `modules/seed` support to replay them through the citizen endpoint for tier 4, so the representative's dashboard has aggregates above the suppression threshold
- [ ] T107 Write `modules/service/src/test/scala/voice/live/ExtractionEvaluationSuite.scala`, tagged `Live`: a labelled evaluation set in `modules/service/src/test/resources/evaluation/concerns.json` scored for extraction accuracy and correction rate, written into research.md "Measurements" (SC-012); no threshold set before the measurement exists
- [ ] T108 Walk quickstart tier 4 (eight steps, three roles) and tier 5 on kind; record each tier's first pass with date, commit and numbers in quickstart.md "What was run and seen"; fill research.md "Found during implementation" and the `CLAUDE.md` traps with anything the platform did otherwise than research.md says
- [X] T109 Run `just check` and resolve every finding; settle the remaining `*Proposed.*` glossary terms that implementation has confirmed by removing the marker in `GLOSSARY.md`; update `notes/ankka-requests.md` with any request that implementation added

---

## Dependencies

**Phase order**: Setup (T001–T012) → Foundational (T013–T034) → US1 (T035–T047) → US2 (T048–T059) → US3 (T060–T069) → US4 (T070–T076) → US5 (T077–T083) → US6 (T084–T092) → US7 (T093–T101) → Polish (T102–T109).

**Story dependencies** (the data flows one way, so the stories are sequential as increments, but their internals are independent):

- US1 depends only on Foundational.
- US2 needs US1 (a confirmed concern) and introduces the Issue entity every later story writes to.
- US3 needs US2 (an Issue to link evidence to); the seed pack (T060) and seed client (T061) can start as soon as T025 exists.
- US4 needs US2 (an Issue) and US3 only for the "never read by evidence views" assertion (T075).
- US5 needs US3 (evidence to research) and US2.
- US6 needs US5 (developments to deliver) and US2.
- US7 needs everything, by design (spec: "tested against everything the earlier stories create"). Its domain (T093) and the timed action (T097) can be written any time after Foundational.

**Within a story**: domain → entities and workflows → endpoint routes → feature steps → interface. Interface tasks ([P], `web/`) never block a story's checkpoint; the feature file does.

**Blocking tasks**: T003 (the build) blocks everything; T026 (`Main`) and T031 (the harness) block every feature suite; T052 (`IssueEntity`) blocks US3 to US7.

## Parallel execution examples

- **Setup**: T004 to T011 in parallel after T003.
- **Foundational**: T013, T014, T015, T017 together; then T018 to T021 together; T023, T024, T025 together; T027 to T030 together; T033 and T034 beside all of it.
- **US1**: T035 and T036 together; T039 beside T037 and T038; T046 and T047 beside T045.
- **US3**: T060 and T062 at once; T061 as soon as T025 is in; T069 beside T067.
- **US6**: T084 and T086 together; T092 beside T091.
- **US7**: T093 and T097 can start right after Foundational; T101 beside T100.
- **Polish**: T102 to T106 together.

## Implementation strategy

1. **MVP**: Setup, Foundational and US1 (T001–T047). A stranger with an invite code is heard and gets a confirmed concern that is theirs alone, recoverable, never public. This is demonstrable and proves the sealing layer, the asynchronous turn, the recorded model double and the living-feature harness, the four riskiest decisions in the plan.
2. **Trust gate**: US2 (T048–T059). The first thing anyone else can see, reviewed by a person. After this the public Issue exists.
3. **Evidence**: US3 (T060–T069), with tier 3 against the real reasoning service.
4. **Opinion and action**: US4 then US5 (T070–T083). The representative's side of the demonstrator.
5. **Feedback and erasure**: US6 then US7 (T084–T101). The two promises the source document calls the north star, tested against everything before them.
6. **Polish**: T102–T109, then tier 5 on kind and the measurements.

Each story ends with its feature file green and the checker at 0 findings. Nothing is tagged or published in this feature.

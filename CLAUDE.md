# voice

Ankka Voice: a privacy-first civic participation service on ankka. A citizen talks pseudonymously
about a concern, confirms what Voice understood, chooses exactly what to share, and follows what a
representative does about it; their personal data can be erased while the anonymous public record
stays. The feature being built is `specs/001-voice-mvp/`; its `tasks.md` tick count is the status.

Read `../ankka-brain/` for the family before cross-repo work. ankka's own `CLAUDE.md` rules hold
here: handlers return effects, wire names are protocol, registration is explicit, events evolve
additively, every endpoint declares an ACL.

## Layout

- `modules/domain`: ids, `Sealed`, refusals, states, rules. Depends on `ankka-core` only, so it
  cannot name a component; its suites run in milliseconds.
- `modules/service`: every component, the three endpoints and `voice.Main`. The one deployable.
- `modules/seed`: a client that loads `seed/school-transport/` into reasoning and Voice.
- `web/`: the interface, a web-hosted ankka service mounting `/api` to `voice`.
- `features/`: the living Gherkin features; `GLOSSARY.md`: every word a step uses.

## Running

| What | Command |
|---|---|
| Domain and unit suites | `sbt domain/test 'service/testOnly voice.unit.*'` |
| The living features (Docker) | `sbt 'service/testOnly voice.features.*'` |
| Everything but live-model suites | `sbt test` |
| Local services | `just up` (Postgres, reasoning, Keycloak), then `just run`, `just web`, `just seed` |
| The feature checker | `just check` |

## Rules of this repository

- **No personal value in an event, state, view row, log or reply** except inside a `Sealed` or as
  the de-identified public copy a citizen and a reviewer approved. Sealing and opening happen in
  endpoints, workflow steps, consumers and timed actions, through `voice.sealing.Sealing`; never in
  an entity or a view, which have no secret store.
- **No model call on a request path.** The gateway ends every response at fifteen seconds. Turns and
  research runs are workflows; the browser polls or holds a reconnecting stream.
- **No clock and no caller in a handler.** The endpoint reads both once and puts them in the command.
- **Refusals by name.** Every refusal is a rule in `contracts/refusals.md` and in
  `voice.domain.Refusal`; a test fails on one missing from either.

## Traps

Written the day they cost time; newest first.

- 2026-10-18: a browser's `fetch` called as a method (`this.fetchImpl(...)`) throws "Illegal
  invocation"; the web suite injects its own fetch and cannot see it. Bind the default.
- 2026-10-18: munit's `--exclude-tags` wins over `--include-tags`. `build.sbt` excludes `Live` and
  `Reasoning`; lift one with `VOICE_INCLUDE_TAGS`, as `just tier3` and `just live` do.
- 2026-10-18: Keycloak is on host port 8180, because a kind installation's ingress holds 8080.
- 2026-10-18: a request body takes a state by its wire name (`proposed`), never the Scala case
  (`Proposed`).

- 2026-10-18: feature suites share one service, so a scenario that asserts a text is not public
  fails when another scenario published the same text. Each disclosure scenario uses
  `Sharing.uniqueDerivative()`.
- 2026-10-18: a workflow command's `thenReply` takes a value, not a function as an entity's does;
  use `.thenReplyState` to answer the state the command set.
- 2026-10-18: `ClassificationSuite` takes as roots the domain types whose names end in `Event`,
  `State` or `Record`; a state type with any other name escapes the check.

- 2026-10-18: an endpoint's `sse` route can send only unnamed events without ids (`SseEvent.named`
  is private). Voice puts the type in the data, `{"type":…,"data":…}`, and the interface passes its
  own resume cursor (the last message's sequence) to `VoiceClient.stream`; an id never arrives.
- 2026-10-18: ankka has no unit testkit for workflows. A workflow is tested in `AnkkaTestKit` with a
  `TestModelProvider` keyed by `whenUserSays`, so suites sharing one kit do not consume each other's
  scripted replies. `kit.eventually` takes an `Option`, not a block that asserts.
- 2026-10-18: two anonymous `given`s for `Vector[...]` codecs in one object collide on the same
  generated name; name them (`given conversationSummaries: ...`).
- 2026-10-18: `ClassificationSuite` walks `voice/domain` recursively and keys classes by simple
  name; a domain type in a new sub-package is checked without registering the package, and two
  domain types with one simple name would shadow each other.
- 2026-10-18: there is no `ANTHROPIC_API_KEY` here, so the model fixtures under
  `src/test/resources/model/` are written by hand in the recorded shape, matched by `when`
  substrings. `just live` runs the live suites when a key exists.

- 2026-10-18: the reasoning image `ghcr.io/thinkmorestupidless/ankka-reasoning:0.1.1` cannot be
  pulled anonymously. `./scripts/reasoning-image.sh` builds it from the sibling checkout's tag.
- 2026-10-18: ankka has no field-level erasure (spec 042 is a draft). Voice seals personal fields
  under a key per participant in the secret store and erases by deleting the key.
- 2026-10-18: the gateway ends every response, streams included, at fifteen seconds; nothing a
  browser waits on may wait for a model.
- 2026-10-18: reasoning answers per hypothesis, not per claim, and its answers need Neo4j; a claim's
  explanation is assembled from three record reads that need Postgres alone.
- 2026-10-18: the platform issues no end-user identity and has no rate limiting at the gateway;
  citizens hold a token Voice signs, and the pilot's abuse control is an invite code.
- 2026-10-18: `then` and `given` are keywords in Scala 3; neither can name a parameter.
- 2026-10-18: a `KeyedView` has no `rowState`; a handler reads its row with `change.rows.get(key)`.
- 2026-10-18: a feature step calls `world.reviewer("GET", "/issues/...")`; the helper adds `/staff`.
  Writing `/staff/...` gives a 404 "no route for /staff/staff/...".
- 2026-10-18: the tier-3 suite starts its service lazily; a `beforeAll` that starts a kit runs even
  when every test is excluded by tag.
- 2026-10-18: `sealed` is a keyword in Scala 3 too; a local named `sealed` does not parse.
- 2026-10-18: a scenario that loads the seed pack gets its options; adding options again in a
  Background gives four, not two.
- 2026-10-18: `GherkinSuite`'s `StepBody` stops at six values; a step with seven placeholders fails
  with "No given instance of type StepBody[...]". Give the suite its own `given` for seven.
- 2026-10-18: the access log is a view; a test that reads `GET /staff/access-log` right after a
  refusal can miss it. Wait for the entry.
- 2026-10-18: `InvestigationState` and `ActionState` already exist in `voice.domain`; a second enum of
  the same name in `accountability` makes imports ambiguous.
- 2026-10-18: a workflow's and a consumer's contexts have no view client; only an endpoint can
  `forView`. Keep a list a step must read in an entity keyed by what the step knows.
- 2026-10-18: a workflow interrupted by `restartService()` stays `Running` until something addresses
  it. A consumer that starts a workflow and `awaitEnd`s it gets the restart covered by redelivery.
- 2026-10-18: workflow step retries are immediate, with no backoff; `maxRetries(5)` is spent in
  milliseconds.
- 2026-10-18: in a step, `stepEffects.updateState(...).thenTransitionTo(step.ref)`; the command side
  is `effects.updateState(...).transitionTo(step.ref).thenReply(...)`. Mixing them does not compile.
- 2026-10-18: ankka's secret store is a table in the service's own database. A participant key
  destroyed by erasure comes back with any database backup taken before it.
- 2026-10-18: `ankka_view_versions.version` has a check of 1 or more; to force a view rebuild in a
  test, empty the view table and delete its offsets while the service is down.
- 2026-10-18: `WorkflowContext` has `workflowId`, not `entityId`. Workflow settings take
  `defaultRecovery`, not `defaultStepRecovery`.
- 2026-10-18: `Codecs.serializer[Boolean]` beside `Serializers.given` is an ambiguous given; use the
  platform's.
- 2026-10-18: the review queue reads `contribution-rows`; a row whose contribution is gone must be
  skipped, or one stale row fails the queue for every reviewer.
- 2026-10-18: a feature suite that moves the shared clock moves it for every suite after it in the
  JVM. Keep jumps short, and delete any short timer a scenario leaves.

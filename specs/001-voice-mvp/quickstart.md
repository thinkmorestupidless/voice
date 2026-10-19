# Quickstart: validating the Voice MVP demonstrator

Five tiers, each proving more with more running. Docker is required from tier 2. The routes are in
[contracts/http-api.md](contracts/http-api.md), the refusals in [contracts/refusals.md](contracts/refusals.md),
the environment in [contracts/deployment.md](contracts/deployment.md). What was run and seen is
recorded at the end as each tier is first passed.

## Prerequisites

JDK 21, sbt, Docker, Node 24, `just`, the `ankka` CLI 0.12.0. For tier 5: a kind cluster with ankka
installed from its own checkout (`ankka/kustomization/deploy-local.sh`). An `ANTHROPIC_API_KEY` is
needed only to record new model fixtures (R16) and for the live suite.

## Tier 1 — the domain and each component alone (seconds)

```bash
sbt domain/test
sbt 'service/testOnly voice.unit.*'
```

The domain module: `Sealed` round-trips through a serializer and renders as `Sealed(<subject>)`
(V1); the classification test lists every event and state type and fails on an unclassified text
field (FR-059); every refusal name in `contracts/refusals.md` exists exactly once; the state machines
of contribution, investigation, action and notification refuse the transitions the contract names.
Each entity through `EventSourcedTestKit` or `KeyValueEntityTestKit`, each workflow through its
testkit with the agent replaced by a scripted `TestModelProvider`.

Proves: the rules live where the data model says, with no runtime (US1.4, US2.1, US5.4 to 5.6, US6.3
at the entity).

To see it can fail: remove the `second-step.required` check from the contribution and the unit
suite goes red.

## Tier 2 — the whole service, recorded model, in-memory issuer (about three minutes)

```bash
sbt 'service/testOnly voice.features.*'
```

`AnkkaTestKit` boots the service against a throwaway Postgres with the `RecordedModelProvider`
answering from `src/test/resources/model/`, an in-suite ES256 issuer for staff tokens (V10), the
in-memory secret store, and a stub reasoning service (a small HTTP fixture answering the record
routes from the seed pack). The living features under `features/` run one `GherkinSuite` per file
(R17), driving the service through its three endpoints:

| Feature file | Proves |
|---|---|
| `features/participation/private-conversation.feature` | US1: a session with no identity, a turn, a proposal, confirmation; nothing public; recovery (V3 by `restartService()` between confirm and read) |
| `features/disclosure/exact-approval.feature` | US2: four modes, exact text, review, rejection with reason, enhanced review, association, withdrawal; the public routes never return a private field (SC-002, SC-003) |
| `features/evidence/source-backed.feature` | US3 against the stub: claim explanation from records, contradiction, abstention, search without a session creating no interest |
| `features/deliberation/opinions-not-evidence.feature` | US4: expressions never in claims, aggregates labelled, small cells suppressed (five synthetic participants); the representative's dashboard labels every measure and makes no population claim |
| `features/action/human-authority.feature` | US5: suggested stays suggested, only a representative records, dates refused, chronology states |
| `features/feedback/one-truthful-update.feature` | US6: Alice and Bob; a forced redelivery of the Issue event and a crash between prepare and deliver yield one update (SC-007, V14) |
| `features/erasure/field-erasure.feature` | US7: after Stories 1 to 6 for two participants, erase one; replay, `restartService()`, view rebuild by version bump, and a database dump assertion find no plaintext; the other participant and the Issue are intact (SC-008); with the dormancy period set to seconds, a third participant is erased by the timer and the retained contribution carries the cause `dormancy`, while a session one poll earlier resets the timer (FR-063, FR-064) |

Proves: every acceptance scenario in the spec, and SC-002 to SC-008 and SC-011, without a network.

To see it can fail: delete one recorded model fixture and its scenario fails naming the request;
mark a `Sealed` field plain in one event and the dump assertion fails.

## Tier 3 — the service beside the real reasoning service (about five minutes)

```bash
just up            # Voice's Postgres, reasoning and its Postgres, Keycloak
just seed          # loads seed/school-transport into reasoning and Voice
sbt 'service/testOnly voice.integration.ReasoningSuite'
```

Proves: V4 to V6; the seed pack's conflicting sources produce a claim explanation with a
contradiction and an abstention on the unsupported question, from the real records (US3.1 to 3.3).
With `just up-graph`, the `learned` read answers and the explanation gains the graph's view.

## Tier 4 — a person at the keyboard (about fifteen minutes)

```bash
just run           # voice on :9000; conversation turns and research need ANTHROPIC_API_KEY
just web           # voice-web through `ankka local web`, on :3000
```

Walk the definition of done in the browser, as the citizen, the reviewer and the representative
(the Keycloak realm import, on :8180, has one user per role, passwords in the import file):

1. Open the site with the invite code, read the notice, speak about school transport, confirm the
   proposal, copy the recovery credential; close the tab and recover.
2. Share de-identified, edit out a detail, submit; as the reviewer approve into the seeded Issue; as
   the citizen see it on the public page without the original.
3. Open the Issue, read a claim's sources, see the contradiction, ask the unsupported question.
4. Express a priority and a trade-off; as the representative see the labelled aggregate.
5. As the representative ask for research, open the suggested investigation, record an action.
6. As the citizen, follow the Issue first, then see the one update in the inbox after step 5 is
   repeated for a second action; as a second citizen who did not follow, see nothing.
7. Search for the Issue in a private window and reconstruct the action and evidence.
8. Request erasure; as the operator read the receipt; as the citizen find the session gone; as the
   public find the Issue intact.

Proves: V9, V11, SC-001, SC-009, SC-010 informally, and the interface renders the seven states from
the server's vocabulary (FR-061).

## Tier 5 — deployed on a local kind installation (about thirty minutes)

```bash
just deploy-local  # builds both images, loads them, applies reasoning, the secrets, voice and voice-web, exposes voice-web
ankka services get voice -p voice
ankka services logs voice -p voice
```

Repeat tier 4 against the exposed hostname with the installation's Keycloak realm. Proves: the
descriptors, the mount, the project secrets, `service:voice/voice` as a reasoning writer (V4), and
the gateway's fifteen-second bound against the asynchronous turn and research routes.

## Live model suite (before a release)

```bash
ANTHROPIC_API_KEY=… sbt 'service/testOnly voice.live.*'
```

The seed conversations against the live model, checking the shape of every reply and that a
proposal's source spans fall inside the citizen's messages. Re-records the fixtures with
`VOICE_MODEL_RECORD=1` when the prompt changes.

## What was run and seen

*(filled as each tier is first passed, with the date, the commit and the numbers)*

No commit yet: the repository has no git history, so each entry carries its date only.

**2026-10-18, tiers 1 and 2.** `sbt test`: 139 passed, 3 ignored (the live suites), 0 failed; the
domain suites since: 21 passed. `npm test` in `web/`: 48 passed; `tsc` clean. `just check`: 0
findings across 59 scenarios.

**2026-10-18, tier 3.** `just up` (after `./scripts/reasoning-image.sh`, the image not being
pullable), then `just tier3`: V4, V5 and V6 passed against reasoning 0.1.1; the graph test was
skipped, the graph profile not running. `just seed` opened "Rural school bus withdrawal" with 2
questions, 7 evidence (6 accepted, as the pack says) and 3 claims.

**2026-10-18, tier 4, partly.** No `ANTHROPIC_API_KEY` on this machine, so nothing that waits on the
model was walked: step 1's conversation and confirmation, step 2 (it needs a confirmed concern), and
step 5's research. Walked instead, over HTTP with tokens from the local realm, against `just run`:

- a citizen session from the invite code, the notice, a priority, and a follow of the seeded Issue;
- a reviewer refused an investigation (`investigation.not-representative`); a representative opened
  one, recorded an action (`proposed`) and moved it to `in progress`; both show on the public page
  and in the chronology;
- a representative's update reached the follower's inbox within four seconds;
- `GET /me/data` listed the citizen's classes; erasure with the recovery credential completed; the
  operator read the receipt and a representative was refused it (`erasure.not-operator`); the
  citizen's session then answered `participant.erased`; the Issue stayed public;
- keyword search found the Issue (`q=school`, `q=bus`, any case) once the view caught up, and
  `/public/search` found the seeded option.

In the browser at :3000: the public Issue page with its questions, the abstention on the unsupported
question, evidence cards with scope, authority and limitations, and a claim's explanation from the
real reasoning service. The page did not render at all until the fetch binding was fixed
(research.md F46). Staff pages were not walked in the browser.

**Tier 5: not run.** The script's descriptor path was wrong and is fixed. The only organization on
this machine's kind installation belongs to another company, and creating a Voice project there was
left to the person who owns it; the script now stops early when the project is missing. Run it once a
`voice` project exists, with `STAFF_ISSUER` matching the tokens' issuer and `STAFF_JWKS_URL`
reachable from the cluster.

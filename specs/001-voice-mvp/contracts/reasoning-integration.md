# Contract: Voice and the reasoning service

How Voice uses `ankka-reasoning` 0.1.1 for questions, sources, evidence and claims, without adding a
civic word to it. Read from the sibling checkout on 2026-10-18 (`v0.1.1`, HEAD `23c8d4a`, four commits
of specs after the tag). The decision is research R4; the data Voice keeps about it is in
[data-model.md](../data-model.md), "Evidence and reasoning".

## Who Voice is to reasoning

Every reasoning route is `Acl.Authenticate`, and a caller is admitted when it is another ankka service
(`Caller.Service(project, name)`, read from its certificate) or the local runtime (`Caller.Local`).
A person through the gateway is refused with 401 on 0.1.1. So:

- Voice calls reasoning **service to service**, from endpoints, workflow steps and consumers, as the
  writer `service:<project>/voice` (locally, `local`). It never forwards a citizen's or a
  representative's identity to reasoning, and no citizen reaches reasoning directly.
- Voice registers one holder and speaks for it: `POST /holders` `{ "id": "voice-evidence-pack",
  "kind": "person", "name": "Voice evidence pack (curated by the pilot reviewer)" }`. Claims in the
  seed pack are stated by this holder. The holder kinds are fixed (`agent`, `person`, `model`,
  `market`); Voice adds none.
- The research agent writes nothing to reasoning in this feature. Suggestions are Voice records
  (`Investigation` in state `Suggested`); a reviewer who accepts a suggested source records it through
  Voice's evidence routes, and Voice writes to reasoning as the holder above.

## Identifiers

Reasoning ids are writer-chosen, 1 to 64 characters of `[A-Za-z0-9][A-Za-z0-9._-]*`. Voice names what
it creates with a prefix that cannot collide with another writer and carries no personal data:

| Record | Id Voice chooses | Example |
|---|---|---|
| Question | `voice.<issue id>.q<n>` | `voice.iss-01J8X.q1` |
| Hypothesis | `h<n>` within its question; referenced as `<question>/<hypothesis>` | `voice.iss-01J8X.q1/h1` |
| Source | `voice.src.<slug>` | `voice.src.dft-school-travel-2025` |
| Evidence | chosen by reasoning: SHA-256 of `source\nlocator\nexcerpt\n` | (digest) |
| Claim | `voice.<issue id>.c<n>` | `voice.iss-01J8X.c3` |

Voice stores a reference as `{ "kind": "question" | "hypothesis" | "source" | "evidence" | "claim",
"id": "<id>" }`, typed in Scala as `QuestionRef`, `HypothesisRef`, `SourceRef`, `EvidenceRef`,
`ClaimRef`. The graph's own typed id, `<kind>:<id>`, is the same pair rendered, so a reference can
be followed into Neo4j unchanged when the graph tier runs.

## What Voice writes

All writes are `POST`, idempotent by id: a new record answers 201, the identical record again 200, a
different record under a held id 409 `id.held-by-another`. Voice treats 200 and 201 alike and surfaces
409 as a seed or curation error. `dated` is optional, never in the future.

| Voice step | Reasoning route | Body (shape from `Recorder.scala`) |
|---|---|---|
| Seed or reviewer opens the Issue's question | `POST /questions` | `OpenQuestion(id, statement ≤500, hypotheses: ≥2 × HypothesisRequest(id, statement), dated?)` |
| Reviewer adds a hypothesis | `POST /questions/{q}/hypotheses` | `AddHypothesis(id, statement, dated?)` |
| Seed registers a source | `POST /sources` | `RegisterSource(id, name ≤200)` |
| Reviewer accepts candidate evidence | `POST /evidence` | `RecordEvidence(source, locator ≤2000, excerpt ≤2000, author?, publishedAt?, observedAt?)` |
| Reviewer states a claim from evidence | `POST /claims` | `StateClaim(id, holder = "voice-evidence-pack", statement ≤1000, derivesFrom: ≥1 evidence id, stances: ≥1 × Stance(hypothesis, "supports" \| "contradicts"), revises?, dated?)` |
| Reviewer marks a claim superseded by a correction | `POST /claims` with `revises` | a new claim; the old one stays and gains `revisedBy` |
| Reviewer withdraws evidence text (licence or error) | `POST /evidence/{e}/withdrawal` | `Withdraw(note?)` |

Voice records the reply's id in the Issue's `EvidenceLink` or claim list **after** the write
succeeds. There is no distributed transaction: the Issue command that links the reference is
idempotent on the reference, so a crashed step re-runs the write (200) and then the link. The order is
reasoning first, Issue second, so Voice never links a reference that does not resolve (R4).

Source, version, publication date, scope, authority, passage and limitations (FR-021): `source`,
`publishedAt`, `locator` and `excerpt` are reasoning's; **scope, authority and limitations are Voice's**,
kept on the `EvidenceLink` beside the reference, because reasoning has no field for them and must not
grow one for Voice.

## What Voice reads

### Record reads, by id, no graph needed

| Voice need | Route | Reply |
|---|---|---|
| Show a question and its hypotheses | `GET /questions/{q}` | `Question(id, statement, hypotheses[], dated, recordedAt, writer)` |
| Show a claim | `GET /claims/{c}` | `ClaimRead(claim: Claim(id, holder, statement?, derivesFrom[], stances[], revises?, …, withdrawal?), revisedBy[])` |
| Show evidence and its passage | `GET /evidence/{e}` | `Evidence(id, source, locator?, excerpt?, author?, publishedAt?, dated, recordedAt, writer, withdrawal?)` |
| Name the source | `GET /sources/{s}` | `Source(id, name, …)` |

**"Why is this claim supported?" (FR-023)** is assembled from records: the claim, each evidence id in
`derivesFrom`, each evidence's source. The reply to the citizen carries each as a link with its
`recordedAt` and `publishedAt`, and the excerpt verbatim as the passage. A claim or evidence with
`withdrawal` set is shown as withdrawn with its note, never as a passage.

**Qualifying or contradictory evidence** is found from Voice's own list of the claims linked to the
Issue's question: every linked claim whose stance on the same hypothesis is `contradicts`, read by id
as above. A claim with a non-empty `revisedBy` is shown as revised, with the revising claim.

**Abstention (FR-024)**: a question for which Voice holds no linked claim with an accepted
`EvidenceLink`, or whose linked claims are all withdrawn or superseded, is answered "the record does
not support an answer", and no reasoning route is called for a citation.

`revisedBy` comes from a view in reasoning and may lag a moment after a revising claim is written;
Voice does not depend on it for a decision.

### Graph answers, when the graph tier runs

| Voice need | Route | Notes |
|---|---|---|
| The case for or against a hypothesis | `GET /answers/case?hypothesis=<q>/<h>&stance=supports\|contradicts` | `CaseAnswer(claims: Support[], revisedClaims: Support[])`, each `Support` with its `EvidenceView`s and `SourceView` |
| What was learned since | `GET /answers/learned?question=<q>&after=<instant>` | for the proactive "new evidence" development (US6) |
| Catch up after a write | `POST /graph/wait` `{ "kind": "claim", "id": "…", "limitMs": 5000 }` | before reading an answer about something just written |

Every answer takes `asOf` and `asRecordedBy`, and answers 503 `graph.unavailable` without Kafka and
Neo4j. Voice treats 503 as "graph tier not running" and shows the record-assembled explanation alone;
no feature of this specification requires the graph tier (R4). Reasoning registers its graph consumers
only when `ANKKA_KAFKA_BOOTSTRAP_SERVERS` is set, so the record tier runs with Postgres alone.

## Refusals Voice must handle

| Reasoning rule | Status | Voice's handling |
|---|---|---|
| `id.format` | 422 | a Voice bug: the id scheme above is wrong |
| `id.held-by-another` | 409 | seed or curation error, shown to the reviewer |
| `record.not-held` | 404 | a dangling reference: shown as "record unavailable", logged, never fabricated |
| `holder.writer.does-not-speak` | 403 | Voice is not a writer for the holder: deployment error (register the holder as Voice first) |
| `graph.unavailable` | 503 | graph tier off: fall back to records |
| validation refusals (statement, excerpt or locator too long; evidence dated before publication; claim before its evidence) | 4xx | shown to the reviewer with the rule name |

## Running it beside Voice

| Tier | Needs | Gives |
|---|---|---|
| Records | the reasoning image `ghcr.io/thinkmorestupidless/ankka-reasoning:0.1.1`, its own Postgres (ankka 0.10.0 schema), no Kafka | every write and record read above |
| Graph | Kafka with the compacted topic `reasoning-graph`, Neo4j 5.26, the sink (ankka-contrib's `ankka-graph-sink-neo4j` image, or ankka-flow 0.3.0's sink built by hand) | the answers routes |

Locally Voice's `docker-compose.yml` gains a `reasoning` service and its Postgres under a compose
profile `reasoning`, and a `graph` profile for the three graph containers; ports move with
`REASONING_POSTGRES_PORT` and `ANKKA_HTTP_PORT` (reasoning on 9100 locally, Voice on 9000). Voice is
told where reasoning is by `VOICE_REASONING_URL`; in a cluster that is
`https://reasoning.<namespace>.svc.cluster.local:9000`, through ankka's `EndpointClients` so the
caller certificate is Voice's. Verify first: V4, V5 in [research.md](../research.md).

## What Voice never does

- Add a node kind, edge kind, holder kind or field to reasoning, or publish into `reasoning-graph`.
- Write to Neo4j, which a rebuild from the topic would wipe.
- Forward a person's identity to reasoning, or let a citizen call it.
- Copy an excerpt into a Voice record: the passage is read from reasoning each time it is shown, so
  a withdrawal there is honoured here.

# Research: Voice MVP demonstrator

Decisions for [plan.md](plan.md), each with what it rests on. File references are to the sibling
checkouts as read on 2026-10-18: `ankka` at `v0.12.0` (`b331c9a9`, the version the scaffold pins),
`ankka-reasoning` at `v0.1.1` plus four commits of specs (`23c8d4a`), `ankka-contrib` at `v0.1.0`.
The ankka documentation was read from the skill references the template installed under
`.claude/skills/ankka*/references/`. "Verify first" marks a claim read from code or documentation
and not yet run; the task that touches it starts with a test that would show it false. They are
collected at the end, with the requests to the platform.

## R1. Personal fields are sealed by Voice under a per-participant key; the platform's erasure is a draft

**Decision**: Every personal field is a `Sealed[A]`: ciphertext under a 256-bit key that belongs to
one participant, kept in the service's secret store under `participant/<id>/key`, with the
participant id in the clear beside it as the subject. Entities and views carry sealed values and
never open them. Endpoints, workflow steps, consumers and agents open them through one `Sealing`
service that reads the key from the store. Erasure deletes the key, after which every sealed value
for that subject opens as `Erased` and renders as *redacted or unavailable*. The AES-256-GCM
envelope's associated data is the subject, the manifest and the field name, so a value spliced from
one record into another is reported corrupt, not erased.

**Rationale**: The source document says ankka already supports field-level crypto-shredding. It does
not. ankka's spec 042, personal data erasure, exists only as `specs/042-personal-data-erasure/spec.md`
(status Draft, no plan, no tasks, no code); the `v0.12.0` tag carries the spec text and the two grant
targets from spec 040 (`erasure`, `decrypt`) that nothing enforces (`GrantsFile.scala:22-23`: "an
erasure grant the keyring's", and no keyring exists). `limitations.md` says "Retention is not
erasure", and the journal never loses an event (`deleteEntity()` appends a marker). So the requirement
is Voice's to meet, with the one primitive the SDK offers: the secret store (`SecretStore`: `put`,
`get`, `delete`; encrypted under the service's `ANKKA_SECRET_KEY`; every read recorded; offered to
endpoints, steps, consumers, timed actions and agents and **not** to entities or views). Keeping the
key there and never opening a field inside an entity is exactly the split the store exists for, and
it is also the shape spec 042 intends (`Personal[A]`, `Present`/`Erased`, a key per subject), so the
migration when 042 ships is a type swap and a key hand-over, not a redesign.

What this gives and what it does not. Replay and snapshots work with the key gone, because the
entity never needed it (FR-054, FR-056). A database dump holds ciphertext and the subject id, and the
test kit's dump assertion (that no plaintext personal value remains) is Voice's to write (SC-008).
Derived rows that hold sealed values become unreadable with the key; rows that hold the *whole*
personal record (interests, issue links) are deleted, and their views tombstone (FR-055). Logs and
traces never see a sealed value because `Sealed` prints as `Sealed(<subject>)`. What it does not
give: a backup taken before erasure still holds the ciphertext, and the key's row in the secret
store's table is in that backup too; the operating guide must say that a restore replays the erasure
log (the `erasure` workflows' completed states) before the service is ready, which is what 042 also
specifies (its FR-021). The secret store records every read, and "nothing is cached", so opening a
page of forty messages is one key read and forty decrypts, which is fine at pilot scale.

**Alternatives considered**: Everything personal in key value entities, as ankka-reasoning does
(its R1), erasing by overwriting the row: it works for a record with one value, but a conversation,
a notification stream and a contribution's review history are sequences other components react to,
which a key value entity cannot give (consistency: a view over it "may not see every intermediate
value"), and backups still hold the old rows. Keeping personal text outside ankka in a store of our
own: a second database, which rule 7 forbids, or object storage, which the platform never deletes
a bucket from and which the SDK has no client for. Waiting for 042: it has no plan and no date, and
this feature's definition of done requires an erasure that works. Building a keyring that entities
can reach (a process-global key cache fed by a `RuntimeExtension`): it reaches into the replay path
the platform owns, and 042 will do it properly.

**Verify first**: V1, V2, V3.

## R2. Public text is the one plaintext an Issue holds; everything citizen-written that is not de-identified public text stays sealed

**Decision**: An Issue's events and state hold plaintext for exactly one kind of citizen text: a
derivative the citizen approved for `DeIdentified` sharing and a reviewer approved as non-identifying.
A `RepresentativeOnly` derivative and a `PublicAttribution` derivative with its display name are held
on the Issue sealed under the participant's key, and opened at the edge by the staff endpoint or the
public endpoint. The link from a public copy (`pub-…`) to its author is a separate restricted
`issue-link` entity, deleted on erasure. Issue events carry no participant id.

**Rationale**: FR-057 says retained public knowledge must carry no reasonably identifying residue,
and FR-013 says the original never leaves the conversation. A de-identified derivative that passed
review is the "genuinely anonymised civic knowledge" the source document allows to remain, and it
has to be plaintext for the public search view and for anyone reading the journal of a public
record to be honest. Anything else a citizen wrote, including a name they chose to publish, is
personal data they may later withdraw, so it must die with their key. An Issue's chronology then
survives erasure as "a contribution was published and later withdrawn" with no text, which is what
US7.4 asks.

**Alternatives considered**: Sealing the de-identified text too: the public search index would then
be the only plaintext, and a rebuild of the view after erasure would recreate nothing, which is right,
but the Issue's journal would hold no public record at all, and the public page would do a key read
per contribution. The de-identified copy is public by the citizen's and the reviewer's decision;
sealing it would be theatre.

## R3. Identifiers are minted, prefixed and never derived from a person

**Decision**: `<kind>-<ULID>` for every Voice record. A participant id is pseudonymous personal data
and appears in no public event, response or view column. Public contributions are addressed by a
`pub-…` reference minted by the review workflow.

**Rationale**: FR-019, FR-060, and the source document's "do not assume even an opaque stable
participant ID is anonymous". Prefixes let a typed reference be checked before it is followed. ULIDs
sort by time, which the chronology and inbox views use, and carry nothing about the person.

## R4. Reasoning records are read by id over HTTP as a service caller; the graph tier is optional

**Decision**: Voice calls the reasoning service over HTTP through `EndpointClients`
(`services("reasoning")`), as the writer `service:<project>/voice`, registers one holder
`voice-evidence-pack` of kind `person`, and keeps typed references `{kind, id}` on its Issues. "Why
is this claim supported" is assembled from `GET /claims/{c}`, `GET /evidence/{e}` and
`GET /sources/{s}`; contradictions come from Voice's own list of the claims linked to the question,
read by id. The graph answers (`/answers/case`, `/answers/learned`) are used when they answer and
treated as absent on 503. The whole contract is
[contracts/reasoning-integration.md](contracts/reasoning-integration.md).

**Rationale**: Reasoning 0.1.1 admits any ankka service on every route (`Writers.acl`:
`Caller.Service` → a writer, `Caller.Gateway` → 401), so Voice is a writer and a citizen never
reaches it. Its records are immutable with writer-chosen ids (`Ids.scala`: `[A-Za-z0-9][A-Za-z0-9._-]*`,
at most 64), so a Voice naming scheme is a convention, not a kernel change (FR-029). Its record
reads need Postgres alone: the graph consumers register only when `ANKKA_KAFKA_BOOTSTRAP_SERVERS` is
set, and the answers routes return `graph.unavailable` without Neo4j. Every scenario in this
specification is met from records; the graph tier adds the richer "what was learned since" read for
proactive evidence developments. Running the graph tier locally means Kafka, Neo4j and the sink
image, all pullable (`ghcr.io/thinkmorestupidless/ankka-reasoning:0.1.1`,
`ankka-graph-sink-neo4j:0.1.0` checked 2026-10-18), under a compose profile.

Scope, authority and limitations of a piece of evidence (FR-021) have no field in reasoning
(`RecordEvidence(source, locator, excerpt, author?, publishedAt?, observedAt?)`); they are Voice's,
on the `EvidenceLink`. Acceptance, rejection and supersession are civic decisions and are Voice's
too; reasoning's `revises` and `withdrawal` are used for the corresponding facts about the records.

There is no distributed transaction. Voice writes to reasoning first, then links the reference in an
idempotent Issue command; a crashed step re-sends the write (200, same record) and the link.

**Alternatives considered**: Consuming the `reasoning-graph` delta topic: element state out of order
across partitions, edges before nodes, no writer, and nothing designed for a downstream reader.
Reading Neo4j directly with Cypher: allowed, but it makes a graph projection the source of a public
explanation, against the source document's section 4.3. A civic layer inside reasoning, as `market`
is: civic code in the reasoning deployable, which section 4.2 forbids. Holding copies of excerpts in
Voice: a withdrawal in reasoning would not be honoured here.

**Verify first**: V4, V5, V6.

## R5. The conversation is a workflow; the extraction agent has no memory and sees the transcript per turn

**Decision**: `POST /me/conversations/{c}/messages` records the citizen's message on the
`conversation` entity, starts or resumes the `conversation-turn` workflow (id `cnv-…:turn-<seq>`)
and answers 202 with the turn id. The workflow's step opens the sealed transcript, calls the
`concern-extraction` agent with the transcript in `withContext`, `MemoryProvider.none`, no tools,
`Guardrail.maxInputLength` and a forbidden-pattern guardrail, and asks for a typed reply with
`thenReplyAs[Turn]` (Voice's reply, optional clarifying question, optional `ConcernProposalDraft`
with source spans and confidence). The step records the reply and proposal on the entity, sealed.
The interface polls the conversation or holds the SSE route, reconnecting at the gateway's bound.

**Rationale**: Three facts. The gateway ends every response at fifteen seconds, streams included
(`limitations.md:123-126`), and a model turn with thinking can take longer, so nothing a browser
waits on may run the model synchronously. An agent's session memory is an event sourced entity whose
`clear` keeps the transcript (042 context, agents.md), so a transcript there would be personal text
Voice cannot erase; with `MemoryProvider.none` the model sees the conversation only through context
that is "kept out of memory", and the transcript lives sealed on Voice's own entity (R1). And a
workflow journals the turn, so a crash after the model answered does not pay for the call again and
a declared `RecoverStrategy` decides retries (designing-agents: "An agent's work is not journalled
step by step; a workflow's is"). `thenReplyAs[T]` does not send the schema to the model, so the
system message describes the JSON shape in full, and a reply that does not decode fails the step,
which the strategy retries once and then fails over to a step that records "Voice could not
interpret this; please say it another way" (FR-006, no silent failure). The model provider is
`AnthropicProvider.fromEnv()` with `ANTHROPIC_API_KEY` from a project secret by `secretKeyRef`.

What the model is sent: the transcript, in the clear, to the provider. The provider's retention
terms are outside Voice (assumption in the spec; verify V12 before any real participant text).

**Alternatives considered**: One agent session per conversation with the default memory: the
natural shape in the docs, rejected because the transcript would be unerasable. An autonomous agent
task per turn: typed result with schema and retry, but a heavier instrument for one interaction, and
its tasks are more entities holding the text. A synchronous agent call from the endpoint: cut at
fifteen seconds through the gateway; fine on a laptop, wrong in a cluster.

**Verify first**: V7, V8.

## R6. Privacy flags are deterministic patterns plus one typed agent call; no judgments in this feature

**Decision**: The `disclosure-review` workflow's `check` step runs deterministic detectors (email,
phone, UK postcode, ages and school years, named individuals by title, group sizes) and then a
`privacy-flagging` agent with `thenReplyAs[PrivacyFlags]` over the derivative text alone, union of
both sets. A non-empty flag set means enhanced review (FR-016). The agent's flags never clear a
deterministic flag.

**Rationale**: FR-016 names distinctive circumstance, health, minors and small groups: the first
three need reading, the last two are also patterns. ankka's judgments would fit (`Question.yesNo`,
fail-closed), but they are Scala-only early access on one provider with its own key
(`limitations.md:358-364`), a second external dependency for a pilot that already has one. An agent
with a typed reply over text with no tools is the documented shape for "transform text once".

**Alternatives considered**: Judgments: revisit when Jev is generally available; the step's
interface (`PrivacyFlags`) does not change. Reviewer-only flagging: the reviewer must then read
every derivative for a minor's detail with no help, and the source document asks for assistance.

## R7. Every reaction is a workflow keyed by its cause, so a repeat addresses the running one

**Decision**: `disclosure-review` is keyed by contribution id; `update-delivery` by development id;
`conversation-turn` by conversation and message sequence; `erasure` by participant id; a
`notification` entity by participant and development. Consumers do nothing but start the workflow
under that id. Commands that may be retried carry the id the entity recognises.

**Rationale**: Consumers are at least once; "a redelivered change addresses the workflow already
running" (designing-services); a `notification` whose id is `participantId:developmentId` refuses a
second preparation by construction, which is how FR-048 holds under retries, interleaving and a
crash between prepare and deliver (SC-007). Timeouts are not failures: a timed-out command may have
happened (consistency), so every Voice command is idempotent on its id.

## R8. Aggregates read one plain level beside the sealed expression, and suppress below five

**Decision**: An `expression` event carries `Sealed[Priority]` for the full statement and a plain
integer `level` and option ids with a stance; the `issue-aggregates` view reads only the plain
fields and a per-view salted hash of the participant id for deduplication. Cells under five
participants are suppressed in the query result. Labels (participants, contributions, method,
window, caveat) are written by the endpoint from the view's counts (FR-033, FR-034).

**Rationale**: A view cannot open a sealed value (no secret store), and an aggregate over sealed
values is impossible, so the one number the aggregate needs is carried in the clear. A priority
level with no text and no identity, in a cell of five or more, is the suppressed aggregate the source
document asks for. The hash is salted per view version so a rebuild re-salts; the row is tombstoned
when the expression entity is deleted on erasure.

**Alternatives considered**: Opening sealed values in the endpoint for every participant and
aggregating there: a key read per participant per page view. Not sealing priorities at all: a
priority is sensitive personal data in the spec's own table.

## R9. Citizens hold two secrets Voice never stores: a recovery credential and a session token

**Decision**: `POST /me/sessions` requires a pilot invite code, mints a participant, shows a
recovery credential once (32 random bytes, base64url; Voice keeps a hash) and issues a session
token: `base64url(participantId.expiresAt).hmac` under `VOICE_SESSION_KEY`, valid for twelve hours.
`CitizenEndpoint`'s `Acl.Authenticate` verifies the HMAC and expiry with no I/O and allows
`Principal(subject = participantId, roles = Set("citizen"))`. Revocation and erasure are checked in
the handler against the `participant` entity. `POST /me/sessions/recover` takes the recovery
credential, verifies it against the hash in the handler, rotates it and issues a new session token.

**Rationale**: The platform verifies tokens and issues none (`limitations.md:147-151`); the gateway
is one caller with no authentication or rate limiting (`:144-146`, `:172-173`); `Acl.Authenticate`
runs synchronously on the server's dispatcher, so it may not do I/O (`.claude/rules/control-plane.md:83-89`,
confirmed in `HttpServer.scala:644-673`); `remoteAddress` is always `None`, so there is no address
to rate-limit on. An HMAC check is pure CPU and needs no issuer, which is what a person with no
account can have. The recovery credential is the only thing that links a returning person to their
records, and only its hash is kept, as the spec's "a session without it is unreachable" requires.
The invite code is not identity (FR-001 holds) and is the pilot's abuse control, which the source
document asks for and which the platform does not provide. `Oidc` refuses shared-secret tokens, so
the citizen token is deliberately not a JWT and never reaches `Oidc`.

**Alternatives considered**: A Keycloak realm with anonymous users: Keycloak has no anonymous user;
provisioning one per session is an administrator's action by design. Minting ES256 JWTs and serving
a JWKS so `Oidc` can verify them: possible, convoluted, and still a token Voice must store a key
for. Cookies: the web proxy forwards them unchanged, but a bearer header is the same on the browser
and in tests, and sockets carry it as a subprotocol.

**Verify first**: V9.

## R10. Staff authenticate through OIDC; roles come from the token; Voice keeps a representative's authority

**Decision**: `StaffEndpoint` uses `Oidc.authenticate()` with one issuer named `staff`
(`ANKKA_AUTH_ISSUERS=staff`, `ANKKA_AUTH_STAFF_ISSUER`, `_JWKS_URL`, `_AUDIENCE`). Roles
`representative`, `reviewer` and `operator` are realm roles in that issuer and arrive as
`principal.roles`; a route checks the role it needs. A `staff-account` key value entity, written by
the operator, holds a representative's authority text, which every action record copies. Locally and
in CI the issuer is a Keycloak container with a realm import (`compose/keycloak/voice-staff.json`);
in tests `Oidc.authenticate(verifier, realm)` takes a key source the suite generates, since ankka's
`TestIssuer` is not published.

**Rationale**: `ankka-auth-oidc` is on Maven Central at 0.12.0 (checked 2026-10-18) and is the one
token verifier in the family; the template already names it. Roles in the token mean the operator
administers staff in one place, the identity provider, and FR-004's "provisioned by the operator"
is met there; Voice adds only what the provider cannot hold, the authority a representative acts
under (FR-040). A deprovisioned account loses its token; its past actions keep `recordedBy` and the
authority as recorded (edge case 14). An entity does not know its caller, so role checks are the
endpoint's (control-plane: "cross-entity checks live in endpoints, never handlers"), and the
principal is carried into the command as the actor (FR-062).

**Alternatives considered**: Roles in the `staff-account` entity instead of the token: two sources
of truth for who may act, and a role check in the endpoint would need an entity read per request.
A single endpoint for citizens and staff with a composed ACL: possible with `withAcl` per route,
rejected because one endpoint has one ACL by design and the two audiences must never share a route.

**Verify first**: V10.

## R11. Update eligibility is read from a view and confirmed against the entity

**Decision**: The `update-delivery` workflow's `followers` step reads `followers-by-issue`; its
`prepare` step, for each follower, reads the `interest` entity by id and skips a follower whose
interest is inactive or gone, then creates the `notification` under `participantId:developmentId`.
An interest created after the development's time is not notified of that development.

**Rationale**: A view lags and must not decide (designing-services: "never make a decision on view
data that must be exact"), while "a handful of known ids is not a view": the view answers *which*
followers, the entity answers *whether*, which is edge case 7 and FR-050. Watchable queries
(spec 047) would give a caught-up marker but are live reads, not a record, and the authoritative
check is cheaper and sufficient.

## R12. Time and actor arrive in the command

**Decision**: Every command carries `at: Instant`, read once by the endpoint or step, and `actor`
(a participant id, a staff principal subject, or `System(component)`), and entities check rules
against them. Tests set the clock through a `SettableClock` the endpoint is given.

**Rationale**: A handler has no clock and does not know its caller (ankka-reasoning R4, R5, both
still true in 0.12.0 and still on the family's request list). `action.date.future` and
`action.date.before-issue` need a time; FR-062 needs an actor on every transition.

## R13. One web-hosted interface, mounting the service's three endpoints

**Decision**: `ankka init voice-web --language web` into `web/`: React 19, Vite, TypeScript, a
`node:http` server, Node 24. Its descriptor is `hosting: web`, `processPort: 3000`, one mount
`/api → voice`, and it is the only exposed service. It holds the session token in memory and
`sessionStorage`, shows the recovery credential once, and polls or holds SSE for turns and the inbox.
Two routes trees in one app: `/` for citizens, `/staff` for representatives and reviewers, the
latter redirecting to the OIDC issuer.

**Rationale**: The source document asks for both interfaces (clarification Q1) and ankka's web
hosting is the family's way to serve one (web-hosting.md): the proxy sets `X-Ankka-Caller: internet`
and forwards `Authorization` and cookies unchanged, so the backend's own ACLs decide. Mounted
requests arrive as the gateway, which `PublicEndpoint` (`AllowAll`) and the two `Authenticate`
endpoints already admit. SSE passes the proxy; WebSockets do not, and are not needed.

**Alternatives considered**: Two web services, one per audience: two images and two hostnames for
a pilot of one representative. Serving the interface from the Scala service: the platform has a
hosting kind for exactly this and the template ships CI for it.

**Verify first**: V11.

## R14. Keyword search is one keyed view with a declared full-text query

**Decision**: A `public-search` keyed view reads `issue`, `policy-option` and the seed's source
records into one row per public record: kind, id, title, public text, version or date. One declared
query, `search`, uses `to_tsvector('english', …) @@ plainto_tsquery(:q)` over the public columns.
Nothing personal, restricted or sealed is ever a column (`public.search.filter`).

**Rationale**: A view's query is plain Postgres SQL over its JSON row (views.md:470-538); a declared
query is checked at startup to be one SELECT on the view's own table; there is no cross-view query
(`limitations.md:226-228`), so one search table is the only way to search several kinds in one
place, and keyed views (spec 031) read several entities. FR-027's "access filters before
generation" is met by construction: the search table holds only what is public. No index can be
declared; at pilot scale a sequential scan is fine, and the request is recorded below.

## R15. The research agent proposes; nothing it says becomes a record until a representative acts

**Decision**: `POST /staff/issues/{issue}/research` starts a `research` workflow (id
`inv-…`) whose step calls the `research` agent with read-only tools (`search_public_issues`,
`read_question`, `read_claim`, `read_evidence`, `list_issue_evidence`) and `thenReplyAs[ResearchSuggestions]`,
records an `Investigation` in state `Suggested` per suggested investigation and the suggested sources
and questions on the workflow state, and ends. The interface polls `GET /staff/research/{run}`. A
representative opens a suggestion with the ordinary open-investigation command.

**Rationale**: FR-038 and US5.3: a suggestion is labelled suggested and does nothing by itself. This
is the documented "agent proposes, workflow applies" pattern with the person's decision as a later
command. Read-only tools "cannot do harm when the model calls it twice"; the one writing step is the
workflow's, after the agent returned, and writes only `Suggested` records. The browser cannot wait
on it (fifteen seconds), so the route returns the run id.

**Alternatives considered**: An autonomous agent with a typed task result: the right instrument when
research becomes multi-step with a budget and nobody waiting; its result shape and rules would
replace the workflow step's decode, and tools stay the same. Named here so the step can grow into it.

## R16. The model's recorded test double is a `ModelProvider` of Voice's own

**Decision**: `RecordedModelProvider` in the test scope implements `ModelProvider` (`name`,
`modelName`, `complete(ModelRequest): Future[ModelResponse]`, optional `stream`). With
`VOICE_MODEL_RECORD=1` and a live key it delegates to `AnthropicProvider` and writes each
request's digest and response under `src/test/resources/model/<suite>/`; otherwise it answers from
those files and fails loudly on a request it has not seen. Scenario suites run on recordings; one
tagged live suite runs on a key before a release.

**Rationale**: Clarification Q2 asked for a live model with a recorded double. ankka has
`TestModelProvider`, scripted by hand, and no record-and-replay provider; `ModelProvider`'s contract
is three members. Recording keeps the fixtures honest about what the model actually said for the
seed conversations, and the digest keeps a changed prompt from passing on a stale answer.

**Verify first**: V8.

## R17. Living features in Gherkin, with the speckit-bdd extension, as the siblings

**Decision**: The spec's acceptance scenarios move into `features/<area>/*.feature` with a
`GLOSSARY.md`, checked by speckit-bdd, and run by ankka-testkit's `GherkinSuite(file)` per feature
through the HTTP routes, one suite per feature file. The spec's scenarios become the names in its
link table.

**Rationale**: The family convention (`ankka-brain/conventions.md`, ankka-reasoning's `CLAUDE.md`)
and the source document's own repository layout ask for `features/`. `GherkinSuite` takes one file
in 0.12.0, fails on an undefined step and on a directory with no scenarios, and gives each scenario
its own id. The spec was written without the extension installed, so its scenarios are inline today;
the first task of the implementation installs the extension and runs `/speckit-bdd-features`.

## R18. Three sbt modules and a web app

**Decision**: `modules/domain` (plain types: ids, `Sealed`, classifications, states, rules, the
reasoning reference types; depends on `ankka-core`'s serializer only), `modules/service` (every
component, endpoint, workflow, agent and `Main`), `modules/seed` (a client that loads the school
transport pack into reasoning and Voice), and `web/` (the interface, npm). The template's single
`root` project becomes `service`.

**Rationale**: A domain module that cannot name an entity or the runtime is where the
classification test (FR-059), the refusal catalogue and the state machines live and are tested in
milliseconds. ankka-reasoning uses modules to make a layering rule a build fact; Voice's rule is
narrower, so three modules, not five. The seed is a client, not a component, and must not be in the
image.

## R19. Dormancy is a timer per participant, replaced at every session; the erasure cause is a category on the public copy

**Decision**: Each session start schedules a timer named `dormancy:<participantId>` for the dormancy
period (`VOICE_DORMANCY_PERIOD`, default 180 days). Scheduling under an existing name replaces the
timer, so a returning participant pushes it out and no state is kept for it. When it fires, the timed
action reads the participant's last session time and either reports done or starts the `erasure`
workflow with cause `Dormancy` and no verification step. The cause (`Requested` or `Dormancy`) is
written onto each retained de-identified contribution by the erasure workflow, as a category, and the
Issue's public reply carries it per contribution and in its counts (FR-063, FR-064).

**Rationale**: Timers are durable in the service's database and replaced by name, which is exactly
the shape of "most recent session plus a period" with nothing else to store (timers.md, scala-sdk.md).
The timed action is the one component kind made for this and it is allowed the component client.
A category on the public copy answers the clarification ("flag that this abandonment occurred so
the preserved non-identifiable information can be weighted later") without adding a link from the
public record back to a participant.

**Alternatives considered**: A daily sweep over a view of last-session times: the view row is a
personal timestamp per participant beside a participant id, and a sweep's cost grows with every
participant ever seen. Erasing dormant participants by hand: nobody would. Not marking the
retained copy: rejected by the clarification. Marking it with the erasure date: a date is a
quasi-identifier beside the contribution's own date; the category alone is kept.

## Verify first

| V | Claim | Rests on | Checked by |
|---|---|---|---|
| V1 | A `Serializer` for a case class holding `Sealed[String]` round-trips through `Codecs.serializer`, and the stored JSON is the envelope with the subject in the clear | `Codecs` derive jsoniter codecs for nested case classes | the first `domain` test |
| V2 | `SecretStore.delete` of a subject key makes every later `get` answer `None`, in the test kit's `InMemorySecretStore` and against Postgres | secrets.md | the sealing suite, both stores |
| V3 | An event sourced entity whose event holds a sealed value replays and snapshots with the key gone and the field untouched | the entity never opens a `Sealed` | `EventSourcedTestKit` plus `AnkkaTestKit.restartService()` |
| V4 | `services("reasoning")` from a workflow step reaches a reasoning container locally as `Caller.Local`, and in a cluster as `service:<project>/voice`, admitted by `Writers.acl` | calling-services.md; `Writers.scala` | tier 3 of the quickstart; tier 5 on kind |
| V5 | Reasoning 0.1.1 starts with Postgres alone and answers writes and record reads; `/answers/*` give 503 `graph.unavailable` | `Main.scala:57-59` registers graph consumers only with Kafka | compose profile `reasoning` |
| V6 | `GET /claims/{c}` carries `derivesFrom` and `stances` as documented, and `GET /evidence/{e}` the `excerpt` | `Records.scala` | the first reasoning adapter test |
| V7 | A workflow step may call an agent with `thenReplyAs[T]` and an await longer than the ask timeout, under a declared step timeout | component-client.md "Timeouts", workflows.md | the conversation-turn suite |
| V8 | `ModelRequest` and `ModelResponse` are serialisable to JSON for recording, or carry enough to be | `ModelProvider.scala`; the sidecar carries them over gRPC | the recorded provider's first test |
| V9 | `Acl.Authenticate` on `CitizenEndpoint` sees the `Authorization` header through the web proxy and the gateway unchanged | web-hosting.md "Headers"; the proxy strips only `X-Ankka-*` | tier 4, through `ankka local web` |
| V10 | `Oidc.authenticate(verifier, realm)` accepts a verifier built from an in-test ES256 key, and the Keycloak realm import yields `realm_access.roles` the module maps to `principal.roles` | `Oidc.scala:45-55`; `Principals.scala:12-32` | the staff endpoint suite; tier 4 |
| V11 | `ankka local web --service voice=http://127.0.0.1:9000` serves the app with `/api` mounted, and SSE passes | first-interface.md | tier 4 |
| V12 | The model provider's data-processing terms and retention for API traffic are acceptable for synthetic participant text, and are documented before any real text is sent | outside the code | an entry in `docs/privacy-and-threat-model.md` before S1 is demonstrated |
| V13 | A keyed view's declared query with `to_tsvector` passes the startup validation of declared queries | views.md:553-655 lists what is refused, and full text is not in it | the search view suite |
| V14 | A `notification` entity keyed `participantId:developmentId` refuses a second `prepare` and the workflow treats the refusal as done | entity rules | the delivery suite with a forced retry |
| V15 | `createSingleTimer` under an existing name replaces the timer, and a `TimerRuntime(pollInterval = 200.millis)` with `VOICE_DORMANCY_PERIOD` set to seconds fires the dormancy action inside a features run; the `TimerProbe` keeps its record across `restartService()` | scala-sdk.md timers table; timers.md "Testing timers" | the erasure suite's dormancy scenarios |

## Requests

What this application would like from the platform. None blocks the feature; each names what it
would remove. They go to `notes/ankka-requests.md` with the first implementation commit.

**To ankka**

1. Personal data erasure (spec 042) implemented: `Personal[A]`, the keyring, view redaction,
   session erasure and the certificate. Would remove `Sealed`, the `Sealing` service, the
   per-participant key in the secret store, and most of the `erasure` workflow (R1). The family had
   already asked for part of this (ankka-reasoning request 1).
2. A clock and the caller in `CommandContext` (R12). Already asked by satisfactory and ankka-reasoning.
3. An index declaration for a view's declared query (R14). Would remove the note that search is a
   sequential scan.
4. A published test issuer for `ankka-auth-oidc`, so a service's suite need not mint its own keys
   (R10).
5. A record-and-replay `ModelProvider` in the test kit (R16).
6. A gateway route timeout override, already on the family list; it would let a conversation turn
   stream to its end (R5).

**To ankka-reasoning**

7. A per-claim explanation route, `GET /answers/claim/{c}`, giving the claim's evidence, sources,
   and the claims that contradict it on the same hypotheses (R4). Would remove the assembly in
   Voice's adapter.
8. Scope, authority and limitations on evidence, or a documented place for a writer's own
   annotations, if other consumers find themselves keeping them beside the reference as Voice does.

## Found during implementation

- **F1. The model fixtures are written by hand.** No `ANTHROPIC_API_KEY` was available, so T044's
  recording could not run. The fixtures under `modules/service/src/test/resources/model/features/`
  are in the recorded shape and match on `when` substrings of the transcript rather than the request
  digest. `ConversationLiveSuite` (tag `Live`, `just live`) checks the same conversations against the
  live model when a key exists; recording with `VOICE_MODEL_RECORD=1` replaces the fixtures then.
- **F2. Only unnamed server-sent events.** ankka's `sse` route takes a `Source[String]`; named events
  and ids are not reachable. Each event's data is `{"type":"message"|"proposal","data":…}` and the
  interface resumes from the last message's sequence, which it passes as `after` (R5 holds).
- **F3. Provenance travels in the command, not in metadata.** Each command carries a `Provenance`
  field and each event records it. Call metadata would need every call site to set it and every
  handler to read it back; a field is checked by the compiler and by `ClassificationSuite`.
- **F4. The recovery credential is `<participantId>~<random>`.** Recovery must find the participant
  without a lookup table that maps credentials to people. The id is not secret (it is the
  pseudonym); the random part is, and the participant keeps only its hash. Recovery rotates it.
- **F5. A refusal was missing from the contract.** Starting a session with a wrong invite code had no
  rule; `session.invite.invalid` (403) was added to contracts/refusals.md. The tasks' names
  `session.recovery.invalid` and `proposal.not-found` were brought to the contract's
  `participant.credential.invalid` and `proposal.not-held`.
- **F6. Helper views.** The routes need four read models the data model implied but did not name:
  `access-log-rows`, `staff-account-rows`, `conversation-rows` and `concern-rows`. They are listed in
  data-model.md under "Read models found during implementation".
- **F7. A model answer that is not the reply shape is said, not hidden.** The extract step retries
  once and then fails over to `could-not-interpret`, which records a reply saying Voice could not
  interpret the message (FR-006). `ConversationTurnWorkflowSuite` proves it.
- **F8. No workflow unit testkit.** The turn workflow is tested in the integration kit with a
  scripted model; see CLAUDE.md's traps.
- **F9. Two disclosure refusals were missing.** `contribution.not-owned` (403) and
  `contribution.state.invalid` (409) were added to contracts/refusals.md; the tasks named them and
  the contract did not.
- **F10. The reviewer's rules are checked by the review workflow's commands.** A command that only
  transitions to a step would answer 200 and fail later, out of the reviewer's sight. The workflow's
  state mirrors the flags and the second step, so `second-step.required`, `same-click`,
  `reason.required` and `not-under-review` refuse at once; the contribution checks them again when
  the step writes. The edge then waits (at most eight seconds) for the step it caused.
- **F11. A published review stays paused.** A withdrawal must find the copy's `pub-…` and Issue,
  and a command cannot transition an ended workflow, so `publish` ends in a pause and `withdraw`
  ends the workflow. A rejected review ends; its withdrawal goes to the contribution alone.
- **F12. The approved text is proved by the hash of its ciphertext.** The grant holds the sealed
  text and the SHA-256 of its ciphertext; a submission is refused unless the derivative's hash is
  the grant's. Nothing is opened to compare, and the hash reveals nothing.
- **F13. A chronology entry carries a fixed note beside its state.** FR-042's seven states say where
  an Issue is, not what happened; a withdrawal is a `submitted` entry noted "a contribution was
  withdrawn by its author", with the `pub-…` and no text.
- **F14. The privacy fixtures live beside the others.** One recorded model serves the whole
  harness, so the privacy-flagging fixtures are in `model/features/` rather than
  `model/disclosure/`: a default that flags nothing, and a more specific one for the distinctive
  scenario. Two helper views were added, `contribution-rows` and `issue-rows`, and an agents section
  in data-model.md; the research agent is `research-assistant` because `research` is the workflow.

- **F15. A candidate's passage is held in Voice until acceptance.** Reasoning has no "proposed"
  state, and writing a passage before a reviewer accepts it would put unreviewed text in the shared
  record. The `EvidenceLink` holds the reviewer's text as a `Candidate`; acceptance writes it to
  reasoning and drops it from the state, so an accepted passage is always read from reasoning. The
  journal keeps the `EvidenceProposed` event with the text; it is staff-curated public material, not
  personal data.
- **F16. Sources carry a Voice card.** Reasoning's `Source` has an id and a name; the licence,
  attribution and link a reader is owed live in a key value entity `source-card`, written after
  reasoning registers the source. The public search view indexes the cards and the Issues; it does
  not index evidence passages, which would copy reasoning's text into a Voice table.
- **F17. Routes the contract did not name.** `POST /staff/issues` (a reviewer opens an Issue the
  seed fills), `POST /staff/sources`, and `GET /public/issues/{issue}/questions/{question}`, which
  answers a question or abstains. Questions and claims take an optional `key` (`q1`, `c1`), giving
  the ids `voice.<issue>.<key>`, so loading the pack twice changes nothing.
- **F18. The seed goes through Voice, as a reviewer.** T061 had the seed write reasoning directly
  with an operator token. It loads through the staff routes instead, as a reviewer, so the seed
  takes the path curation takes and nothing reaches reasoning that Voice did not decide. A scenario
  loads its own copy under its own Issue title; sources and evidence are shared by id.
- **F19. The refusals T065 named were mapped to the contract's.** `evidence.not-candidate` is
  `evidence.state.invalid`; `evidence.superseded-by.unknown` and `claim.derives-from.empty` are
  `evidence.reference.unresolved` with the id at fault. A claim whose only evidence is superseded
  stays linked and is not withdrawn, but its explanation abstains until a reviewer resolves the
  prompt.
- **F20. Tier 3 did not run here.** The reasoning image cannot be pulled anonymously and no build
  of it was on this machine. `ReasoningSuite` is tagged `Reasoning`, excluded from `sbt test`, and
  run by `just tier3` after `just up`.

- **F21. An expression's id is keyed on the participant and the Issue.** One expression per
  participant per Issue needs finding again without a lookup table, which would itself be a
  sensitive link. `Ids.keyed` takes the HMAC of the pair under the service's key in the shape of a
  minted id, so the participant's own routes find it and nobody without the key can relate it to
  them. The aggregate row's key is that id, so it is also the deduplication basis.
- **F22. A trade-off's stance is a category; its words are sealed.** The data model sealed the whole
  trade-off, which left the aggregate nothing to count per option. The stance (accepts or rejects)
  is carried plain like the priority level, and the participant's words stay sealed.
- **F23. Suppression hides a second cell beside a lone one.** With one suppressed cell, a reader
  could subtract the visible cells from the participant total. When exactly one cell of a dimension
  is under the threshold, the next smallest non-zero cell is hidden too; under the threshold in
  total, every count and the window are hidden.
- **F24. Refusal names for deliberation follow the contract.** T071 named `expression.level.range`,
  `expression.option.unknown` and `expression.not-owned`; the contract's are
  `expression.priority.range` and `expression.option.not-held`, and the keyed id makes another
  participant's expression unreachable, so no ownership refusal exists. An option body carrying
  `recommended`, `recommendation`, `endorsed`, `rank`, `ranking` or `score` is `option.endorsement`.
  `PUT /staff/issues/{issue}/options/new` mints the id, and `GET /staff/issues/{issue}/options` was
  added for the seed's idempotency.

- **F25. The domain already had the investigation and action states.** `StateLabels` carried
  `InvestigationState` and `ActionState` with their public words, so the investigation record is
  `InvestigationRecord` and both entities reuse the enums rather than declaring their own. The
  investigation's status is stored as the enum's name, so `notClosed` reads `<> 'Closed'`.
- **F26. A refused staff call is logged by the gate, not by the route.** `gate.admitLogged` writes
  one access-log entry with route `refused METHOD path` and the rule as its record, so FR-040's
  "the refusal is logged" holds for every representative route at once. The entry reaches the
  operator's `GET /staff/access-log` through a view, so a test waits for it.
- **F27. The outline's citizen is a staff token with the role `citizen`.** A citizen's own session is
  not a staff credential and gets 401 before any rule is read; to show the representative rule
  refusing a citizen, the scenario signs a staff token carrying the role `citizen`, and checks the
  session's 401 beside it.
- **F28. The research fixture lives with the other recorded model answers.** T082 named
  `model/action/`; the harness reads one recorded model directory, `model/features/`, and matches a
  fixture on a substring of the prompt, so `research-suggestions.json` is there.
- **F29. The test kit's step bodies stop at six values.** The chronology step names seven labels;
  `ActionSuite` gives its own `StepBody` for seven, which the kit's public trait allows. Recorded as
  an ankka request.
- **F30. An "unanswered" entry is derived at the edge.** FR-042's open item with no response is a
  submitted entry with no later staff entry that is not itself a submission; it is computed when the
  chronology is read, not stored, so a later investigation answers every earlier contribution.
- **F31. A page's query reaches it through the route table.** The Action page reads the
  investigation it was opened from as `?investigation=`; `match` still ignores the query, as its
  test pins, and `withQuery` adds the query's values under the path's.

- **F32. Only an endpoint can query a view.** A workflow's and a consumer's contexts carry a
  component client, secrets and other services, but no view client, so the update workflow cannot
  read `followers-by-issue`. Which participants follow an Issue is kept in a key value entity
  `issue-followers` keyed by the Issue, written by the follow and stop routes after the interest;
  each `interest`, read by id, still answers whether (R11). The list is authoritative, so
  `development.stale` has nothing to retry and stays in the catalogue unused; `interest-rows`
  answers `GET /me/interests`.
- **F33. A workflow a restart interrupts resumes only when it is next addressed.** Workflows are
  sharded without remembered entities: after `restartService()` an `update-delivery` held in its
  `deliver` step stayed `Running` until something called it. The `developments` consumer therefore
  starts the workflow and waits for its end (`awaitEnd`, sixty seconds) before the event is done
  with; after a restart the event comes again and its start addresses the workflow, which resumes
  and delivers what was prepared, once (SC-007). The other workflows (`conversation-turn`,
  `disclosure-review`, `research`) are each addressed again by the participant's or the
  representative's next read, and were left as they are.
- **F34. Workflow step retries are immediate.** A retried step runs again at once, with no backoff,
  so a failure seam that counts failures exhausts a small `maxRetries` in milliseconds.
  `DeliveryFaults` counts failures for the failed-delivery scenario and can hold the `deliver` step
  for the restart; nothing in the service arms it.
- **F35. A typographical correction is a narrative update that names what it corrects.** The
  feature's immaterial development had no route; `POST /staff/issues/{issue}/updates` takes
  `corrects`, the development id of an earlier update, which makes the new development `Minor`. The
  workflow ends at `materiality` and tells no one. A correction naming no held update is
  `update.correction.not-held`, added to the contract.
- **F36. The domain already had the notification's states.** `NotificationState` with its moves was
  in `StateLabels`; the record is `NotificationRecord`, which keeps the failure reason and the
  attempts beside the state so a delivered update still shows it failed once.
- **F37. A development is its own Issue event.** Every authoritative event persists
  `DevelopmentPublished` beside it, minted once: an action, decision or outcome reuses the
  development id its record already carries, accepted evidence gets one from the endpoint, and an
  update's id is its development. The consumer reacts to that event alone.

- **F38. The erasure workflow finds a participant's records through an index of its own.** A step
  cannot query a view, and an expression's id is keyed under the session key, which a step does not
  hold. `participant-records`, keyed by the participant, lists every record id by kind; it is written
  where each record is created and deleted last by the purge.
- **F39. A participant's key lives in the service's own database.** ankka's secret store is the
  `ankka_secrets` table, each value encrypted under the service's secret key. Destroying a key
  deletes the row. A database backup taken before an erasure still holds the row, and restoring it
  with the service's secret key would make the participant's values readable again. The scenario
  "unreadable after a restore from a snapshot" proves a restart and a replay, not a restore of an
  older backup. Backups must expire inside the erasure deadline, or keys must live elsewhere; this
  is ankka spec 042's keyring (request 11).
- **F40. A suppressed de-identified copy leaves the Issue's state and views, not its journal.** A
  public copy's text is plaintext in `ContributionPublished` by design (R2). When the residue rule
  withdraws a flagged copy of an erased author, `ContributionWithdrawn` drops it from state, the page
  and the search index, but the earlier event still holds the words. Sealing each public copy under
  its own key, destroyed on suppression, would close it; recorded as a limitation.
- **F41. The residue rule is the review's flags.** A copy the privacy check or a reviewer flagged is
  withdrawn when its author is erased; an unflagged one stays, marked with the cause and counted by
  cause. Erased-author counts are not suppressed under the threshold: the dormancy scenario reads a
  count of one, and a count says that some author was erased, not which.
- **F42. A request is verified by the recovery credential it carries.** `POST /me/erasure` with the
  participant's current credential starts at once; without it, or with one that does not match, it
  is held for an operator. The operator's evidence is not kept, because a receipt holds no free text.
- **F43. The dormancy timer is scheduled at the edge, at every session start, recovery and renewal.**
  The edge reaches the timer scheduler through an accessor, so `Main` and the test harness make the
  timer runtime before the edge. The harness can shorten the period for the participants it starts
  while an override is set; the dormancy scenarios use ten minutes and move the clock.
- **F44. A view rebuild cannot be forced through the kit.** The kit restarts with the descriptors it
  started with, and `ankka_view_versions` refuses a version below 1. The rebuild scenario empties the
  view tables and deletes the views' offsets while the service is down, which is what raising every
  version does.
- **F45. A reviewer-queue row outlived its contribution after the erasure scenarios.** The queue
  failed with `contribution.not-under-review` for everyone. It now passes over a row whose
  contribution no longer exists. Why the row survived its deleted contribution was not established.

- **F46. The interface never read a page in a browser before the tier 4 walk.** `VoiceClient` called
  the browser's `fetch` as `this.fetchImpl(...)`, which a browser refuses ("Illegal invocation"), so
  every page answered "could not be read". The web suite injects its own fetch and could not see it.
  The default is now bound to the global, and a test calls it with a receiver check.
- **F47. `just tier3` and `just live` ran nothing.** `build.sbt` excluded the `Live` and `Reasoning`
  tags, and munit's exclude wins over `--include-tags`. `VOICE_INCLUDE_TAGS` now lifts a tag; tier 3
  passes V4 to V6 against the real reasoning service.
- **F48. An action state given by its Scala name was reported as missing.** `"Proposed"` answered
  `An action needs state.` The same rule now names the states Voice takes (`proposed`,
  `in-progress`, `completed`) when the value is present but unknown.
- **F49. Keycloak and a kind installation both want host port 8080.** The compose file now puts
  Keycloak on 8180, so tiers 4 and 5 can run on one machine.

## Measurements

*(empty; the quickstart's tiers record what was run and seen)*

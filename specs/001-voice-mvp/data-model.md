# Data model: Voice MVP demonstrator

The records Voice keeps, which ankka component holds each, which of their fields are personal and
how a personal field is sealed so it can be erased. The decisions behind the shape are in
[research.md](research.md), referenced by number; the routes that write and read them are in
[contracts/http-api.md](contracts/http-api.md).

## Conventions

- **Identifiers** are opaque strings the service mints: a prefix naming the kind, a hyphen and a
  26-character ULID (`prt-01J…`, `cnv-…`, `ctb-…`, `iss-…`). The prefix lets a link be checked
  for kind before it is followed (FR-029). An identifier is never derived from anything a person
  typed, and a participant's identifier appears in no public record (R3).
- **Time** is an `Instant` the endpoint read once and carried in the command (R12). Handlers have
  no clock.
- **A sealed field** is `Sealed[A]`, a value that is either `Present(a)` or `Erased`, serialised as
  a JSON object `{ "subject": "<subject>", "ciphertext": "<base64>" }` or `{ "subject": "<subject>" }`
  once erased (R1). The subject is the participant whose key seals it. Entities and views hold the
  sealed value and never open it; endpoints, workflow steps, consumers and agents open it through the
  `Sealing` service, which reads the subject's key from the secret store. A `Sealed` prints as
  `Sealed(<subject>)`.
- **Classification** per field: *public*, *restricted* (visible to a role), *personal* (sealed,
  erased with its subject), *sensitive* (personal, and shown only inside labelled aggregates). The
  classification is a comment on the field in code and a column below; a test lists every event and
  state type and fails on a `String` field with no classification (FR-059).
- **States** are small enumerations serialised by name. Every transition is an event with the actor
  and the time (FR-062).
- **Provenance**: every command carries `Provenance(actor, at, causationId, correlationId)` in its
  metadata, set once at the edge (the request's id as correlation; the request, event or timer that
  caused the command as causation), passed on by `withMetadata` on every client call, and recorded on
  every event (FR-062). A test fails on an event type without it.

## Participation

### Participant — event sourced entity `participant`, id `prt-…`

| Field | Type | Classification |
|---|---|---|
| id | `prt-…` | pseudonymous; personal as a whole record |
| recoveryCredentialHash | hash of the credential issued at session start | personal |
| displayName | `Sealed[String]`, optional | personal |
| contact | `Sealed[Contact]`, optional (verified email for later releases) | personal |
| roleGrants | none for a citizen | — |
| erasure | `None` / `Requested(at)` / `Completed(at)` | restricted |

Events: `ParticipantRegistered(credentialHash, at)`, `RecoveryCredentialRotated`, `ErasureRequested(at, verifiedBy)`, `ErasureCompleted(at)`.
Rules: a participant with `Completed` erasure refuses every command but queries, which answer `Erased`. The recovery credential is shown once and kept only as a hash (R9).

### StaffAccount — key value entity `staff-account`, id = the staff issuer's subject

| Field | Type | Classification |
|---|---|---|
| principal | the authenticated subject from the person's token | restricted |
| displayName | the name shown on records this person writes | public when shown on an action |
| authority | text: the office or mandate a representative acts under; copied onto every action | public when shown on an action |

Roles (`representative`, `reviewer`, `operator`) are realm roles in the staff issuer and arrive in
the token; Voice holds only what the issuer cannot (R10). Written by the operator through the staff
endpoint (FR-004). A deprovisioned person loses their token; the records they wrote keep their
subject, name and authority as recorded.

### ConversationTurn — workflow `conversation-turn`, id `cnv-…:turn-<seq>`

State: conversationId, messageSeq, state (`Thinking` / `Replied` / `CouldNotInterpret`). Its step opens the sealed transcript, calls the `concern-extraction` agent and records the reply and any proposal on the conversation (R5). Started by the message route, which answers 202 with the turn id; the interface polls the conversation or holds its SSE route.

### Conversation — event sourced entity `conversation`, id `cnv-…`

| Field | Type | Classification |
|---|---|---|
| participantId | `prt-…` | personal link |
| messages | list of `Message(seq, author: Citizen/Voice, text: Sealed[String], at)` | personal |
| proposals | list of `ConcernProposal` (below) | personal |
| confirmedConcerns | list of `cnc-…` | personal link |

Events: `ConversationStarted`, `MessageRecorded`, `ConcernProposed`, `ProposalEdited`, `ProposalConfirmed(concernId)`, `ProposalDiscarded`.
Rules: a proposal is confirmed by the citizen's command only (FR-008, FR-009); a confirmed proposal is bound to the message sequence numbers it was extracted from and a later message does not change it (edge case 1). The agent's session memory is not used: the transcript lives here, sealed, and is handed to the model per turn (R5).

### ConcernProposal (value inside Conversation)

| Field | Type | Classification |
|---|---|---|
| proposalId | `prp-…` | — |
| summary | `Sealed[String]` | personal |
| elements | list of `Element(kind: Problem/Who/Where/When/Impact/Wish, text: Sealed[String], sourceSpans: list of (messageSeq, from, to))` | personal |
| confidence | `Low/Medium/High` | — |
| suggestedIssues | list of (`iss-…`, score) | — |
| editedByCitizen | boolean | — |

### Concern — event sourced entity `concern`, id `cnc-…`

| Field | Type | Classification |
|---|---|---|
| participantId | `prt-…` | personal link |
| conversationId, proposalId | links | personal link |
| confirmedSummary | `Sealed[String]` | personal |
| elements | as the proposal, sealed | personal |
| contributions | list of `ctb-…` | personal link |

Events: `ConcernConfirmed(at)`, `ContributionOpened(ctb)`.
A concern is a statement by one person, never an assertion about the world (FR-022).

## Disclosure

### Contribution — event sourced entity `contribution`, id `ctb-…`

| Field | Type | Classification |
|---|---|---|
| concernId, participantId | links | personal link |
| mode | `Private` / `DeIdentified` / `RepresentativeOnly` / `PublicAttribution` | restricted |
| derivative | `Sealed[String]`: the exact approved text | personal until approved; after approval a *public copy* is written to the Issue (below) for `DeIdentified` and `PublicAttribution`, a *restricted copy* for `RepresentativeOnly` |
| audience | derived from mode | restricted |
| state | `Drafting` → `Approved by citizen` → `Under moderation` → `Published` / `Rejected(reason)` → `Withdrawn` | restricted |
| flags | set of `Distinctive`, `Health`, `Minor`, `SmallGroup` | restricted |
| review | `Review(reviewer, decision, reason, at, secondStep: Option[(reviewer, at)])` | restricted |
| issueId | `iss-…` once associated | restricted |

Events: `DerivativeProposed`, `DerivativeEdited`, `DisclosureAuthorised(mode, exactText, at)`, `SubmittedForReview(flags)`, `PublicationApproved(reviewer, at)`, `PublicationRejected(reviewer, reason, at)`, `EnhancedReviewRecorded`, `AssociatedWithIssue(iss, at)`, `AssociationChanged`, `DisclosureWithdrawn(at)`.
Rules: `Private` never leaves `Drafting` into review; `SubmittedForReview` requires `DisclosureAuthorised` first (FR-011 to FR-014); a flagged contribution's `PublicationApproved` requires `EnhancedReviewRecorded` by the same or another reviewer first (FR-016); `Withdrawn` is terminal and emits the public tombstone the Issue needs (FR-018).

### DisclosureGrant (value inside Contribution, also its own view row for the operator)

| Field | Type | Classification |
|---|---|---|
| scope | the contribution id | restricted |
| audience | `Reviewer+Representative` / `Reviewer+Public` / … | restricted |
| purpose | fixed text per mode | — |
| exactText | `Sealed[String]` | personal |
| grantedAt, withdrawnAt | times | restricted |

Kept as evidence of consent for as long as retention requires, then erased with the subject.

### DisclosureReview — workflow `disclosure-review`, id = `ctb-…`

Steps: `check` (deterministic privacy detectors, then the `privacy-flagging` agent's typed flags; R6) → `awaitReviewer` (paused; a reviewer command resumes) → `awaitSecondStep` when flagged → `publish` (writes the public or restricted copy to the Issue, records the association) or `reject`. A withdrawal command at any step transitions to `withdraw`. The workflow id is the contribution id, so a repeated submission addresses the running review (R7).

## Issues

### Issue — event sourced entity `issue`, id `iss-…`

| Field | Type | Classification |
|---|---|---|
| title, summary | text, reviewer-approved | public |
| contributions | list of `PublicContribution(pub: `pub-…`, text, publishedAt, authorErased: Option[Requested/Dormancy])` for `DeIdentified` | public, plaintext (R2); the cause is a category set by the erasure workflow (FR-064) |
| attributedContributions | list of `AttributedContribution(pub, text: Sealed[String], name: Sealed[String], publishedAt)` for `PublicAttribution` | public once opened at the edge; sealed under the author's key (R2) |
| restrictedContributions | list of `RestrictedContribution(pub, text: Sealed[String], publishedAt)` for `RepresentativeOnly` | restricted (representative); sealed under the author's key |
| questions | list of reasoning `QuestionRef` | public |
| evidence | list of `EvidenceLink(evidenceRef, state: Candidate/Accepted/Rejected/Superseded, reviewer, at)` | public |
| options | list of `pol-…` | public |
| investigations, actions, decisions, outcomes | lists of ids | public |
| reviewPrompts | list of `ReviewPrompt(id, evidence, claims, decisions, raisedAt, resolved: Option[(by, at, note)])` | restricted (reviewer, representative); raised by `EvidenceAccepted` and `EvidenceSuperseded` for every claim and decision deriving from that evidence; resolving changes nothing else (FR-028) |
| chronology | list of `Entry(state, actor kind, at, ref)` using the seven states of FR-042 | public |
| counts | `participants`, `contributions`, `withdrawn`, `authorsErased` by cause (`requested`, `dormancy`) | public, with the caveat text; the cause counts are what later weighting reads (FR-064) |

Events: `IssueOpened(reviewer)`, `ContributionPublished(pub, text)`, `ContributionAttributed(pub, sealedText, sealedName)`, `ContributionRestricted(pub, sealedText)`, `ContributionWithdrawn(pub)`, `AuthorErased(pub, cause)`, `UpdatePublished(developmentId, text, by)`, `QuestionLinked`, `EvidenceProposed`, `EvidenceAccepted`, `EvidenceRejected`, `EvidenceSuperseded`, `ReviewPromptRaised(prompt, evidence, claims, decisions)`, `ReviewPromptResolved(prompt, by, note)`, `OptionAdded`, `InvestigationOpened`, `ActionRecorded`, `DecisionRecorded`, `OutcomeRecorded`, `DevelopmentPublished(developmentId, kind, ref, summary)`.
Rules: the public copy's `pub-…` reference is minted by the review workflow and maps back to the contribution only through the restricted `IssueLink` (below), so the Issue's events carry no participant identifier (R3). The de-identified derivative is the one citizen text an Issue holds in plaintext: it is public by the citizen's and the reviewer's decision, and it is what remains after erasure as anonymised knowledge (R2). Attributed and restricted copies are sealed under the author's key, opened by the public or staff endpoint at the edge, and open as `Erased` once the author is erased. `ContributionWithdrawn` keeps the `pub-…` and drops the copy from state; the chronology shows a withdrawal with no text (FR-018, FR-057). `AuthorErased` is sent by the erasure workflow for each retained `pub-…`, sets `authorErased` on the public copy and moves the counts; it carries the cause and nothing else about the author (FR-064). `UpdatePublished` is a representative's narrative update, text as published (FR-044).

### IssueLink — key value entity `issue-link`, id `pub-…`

| Field | Type | Classification |
|---|---|---|
| contributionId, participantId | links | personal link |
| issueId | `iss-…` | restricted |
| version | integer | — |

Erased by overwriting with the empty link and deleting (FR-055). The only record that joins a public text to its author.

### PolicyOption — key value entity `policy-option`, id `pol-…`

Title, description, costs, benefits, open questions, all public, written by a reviewer or representative; no endorsement field exists (FR-032).

## Deliberation

### Expression — event sourced entity `expression`, id `exp-…`, one per participant per Issue

| Field | Type | Classification |
|---|---|---|
| participantId, issueId | links | sensitive link |
| priority | `Sealed[Priority(level 1–5, at)]` | sensitive |
| tradeOffs | list of `Sealed[TradeOff(optionId, accepts: text, rejects: text)]` | sensitive |
| considerations | list of `Sealed[Consideration(optionId, text)]` | sensitive |
| withdrawn | boolean | — |

Events: `PriorityExpressed`, `TradeOffExpressed`, `ConsiderationRecorded`, `ExpressionAmended`, `ExpressionWithdrawn`.
Rules: never appears in evidence or claim views (FR-031). The aggregate view (below) reads the priority level as a plain integer carried beside the sealed value: the level is not personal on its own, and the aggregate suppresses small cells (R8).

### View `issue-aggregates` over `expression`

One row per expression: issueId, priority level, option ids with a stance, participantId hashed under a per-view salt for deduplication, at. The query groups by issue and reports participants, contributions, window, method text and suppresses any cell under the threshold (FR-033, FR-034). On erasure the row is removed by the view's own tombstone when the entity is deleted (FR-055).

## Evidence and reasoning

Voice holds no reasoning record. It holds **references**:

| Reference | Format | Resolves through |
|---|---|---|
| `QuestionRef`, `HypothesisRef`, `SourceRef`, `EvidenceRef`, `ClaimRef`, `BeliefRevisionRef` | the reasoning service's identifier, typed by the wrapper | the reasoning service's HTTP API (R4) |

An `EvidenceLink` on the Issue carries Voice's own state for that evidence (candidate, accepted, rejected, superseded) with reviewer and time: acceptance is a civic decision and lives here, while the evidence's provenance (source, version, date, scope, authority, passage, limitations) lives in the reasoning record and is read from it (FR-021, FR-029). A `Claim`'s support and contradictions are read from the reasoning service's explanation routes and never copied (FR-023 to FR-025).

A candidate's passage is the one exception, and it is temporary: what the reviewer proposed is held on the `EvidenceLink` as a `Candidate` until acceptance writes it to reasoning, and acceptance drops it, so an accepted passage is always read from reasoning. Scope, authority and limitations stay on the link, because reasoning has no field for them.

### Source card — key value entity `source-card`

- **Id**: the reasoning source id, `voice.src.<slug>`.
- **Fields**: name, licence, attribution, link, publishedAt. All public. Written by `POST /staff/sources` after reasoning has registered the source, so a card never names a source reasoning does not hold.
- **Why it exists**: reasoning's `Source` has an id and a name; the licence, attribution and link a reader is owed (FR-021) are Voice's.

### Seed pack — files under `seed/school-transport/`

Sources, evidence, questions and claims for the one Issue, with the conflicting pair, the unsupported question and a correction left as a candidate. Every source is fictional and labelled "(illustrative)". The seed client loads the pack through Voice's staff routes as a reviewer, and Voice writes it to reasoning, reasoning first and the Issue second. Licences and attributions beside each source.

## Investigation and action

### Investigation — event sourced entity `investigation`, id `inv-…`

| Field | Type | Classification |
|---|---|---|
| issueId | link | public |
| question | text | public |
| state | `Suggested(by: agent)` / `Open(by: representative)` / `Closed` | public |
| tasks | list of `Task(text, owner, due, state)` | public |
| evidenceRequests | list | public |

Events: `InvestigationSuggested(agentRun)`, `InvestigationOpened(representative, at)`, `TaskAdded`, `TaskUpdated`, `InvestigationClosed`.
Rules: `Open` requires the representative role on the command's actor (FR-039); `Suggested` renders with that label everywhere (FR-038).

### Action — event sourced entity `action`, id `act-…`

| Field | Type | Classification |
|---|---|---|
| issueId, investigationId | links | public |
| authority, owner, rationale, publicSummary | text | public |
| date | date | public |
| state | `Proposed` / `InProgress` / `Completed` | public |
| recordedBy, recordedAt | principal and time | public |

Events: `ActionRecorded`, `ActionStateChanged`. Rules: `date` not in the future and not before the Issue opened (edge case 8); only a representative records (FR-039, FR-040); `Proposed` is never rendered as done (FR-041).

### Decision — event sourced entity `decision`, id `dec-…`; Outcome — event sourced entity `outcome`, id `out-…`

Decision: chosen approach, authority, alternatives, justification, recordedBy, at. Outcome: actionId, measured, limitations, claimsResolved: boolean (default false), recordedBy, at (FR-043, FR-049).

### Development (value inside Issue events)

`developmentId` (`dev-…`, minted once per authoritative event by the Issue), kind (`ActionRecorded`, `EvidenceAccepted`, `DecisionRecorded`, `OutcomeRecorded`, `UpdatePublished`), ref, materiality (`Material` / `Minor`), source summary with record links. The first four are record-derived and reach followers with no human step; `UpdatePublished` is a narrative update whose text a representative wrote or reviewed before it became a development (FR-044). The notification pipeline keys everything on `developmentId` (FR-048).

### View `public-search` — keyed view over `issue`, `policy-option` and the seed's sources

One row per public record: kind, id, title, public text (de-identified contributions, summaries, option descriptions, source names), version or date. Its declared query `search` is a full-text match over those columns (R14). No column may hold a sealed, restricted or sensitive value; a test over the classification table enforces it (`public.search.filter`).

### Research — workflow `research`, id = `inv-…` minted at the request

State: issueId, question, requestedBy, suggestions (`ResearchSuggestions`: sources, questions, investigations, each with the agent's stated reason and the records it read), state (`Running` / `Done` / `Failed`). Its one step calls the `research` agent with read-only tools and records an `Investigation` in `Suggested` per suggested investigation (R15). Read by `GET /staff/research/{run}`.

### AccessLog — event sourced entity `access-log`, id = the UTC day

Events `AccessRecorded(principal, route, recordId, at)` for every staff read of a restricted record (FR-005). One entity per day bounds the state; the operator reads it through the `access-log` view.

### Dormancy — timer `dormancy:<participantId>` and timed action `dormancy-check`

Every session start schedules the timer under the participant's name for the dormancy period (`VOICE_DORMANCY_PERIOD`, default 180 days); scheduling again under one name replaces the earlier timer, so a returning participant pushes it out (FR-063, V15). When it fires, the timed action asks the participant entity for its last session time: a later session means nothing to do, which it reports as done; otherwise it starts the `erasure` workflow with cause `Dormancy`, which needs no verification. The action is safe to repeat: a second firing finds the participant erased or the workflow running, and reports done (R19).

## Interests and notifications

### Interest — key value entity `interest`, id `participantId:issueId`

| Field | Type | Classification |
|---|---|---|
| participantId, targetId | links | personal link |
| delivery | `InboxImmediate`, the one value in this feature; a later channel is a new value, never a change to this one (FR-046) | personal |
| since | time | personal |
| active | boolean | personal |

Created only by the follow command (FR-045). Erased by deleting (FR-055).

### IssueFollowers — key value entity `issue-followers`, id = the Issue

| Field | Type | Classification |
|---|---|---|
| issueId | link | link |
| participants | the participants following the Issue | personal link |

Written by the follow and stop routes after the `interest` itself; adding and removing are idempotent. Read by the update workflow's `followers` step to find which participants follow (FR-047): only an endpoint can query a view in ankka 0.12, so the list is kept where a step can read it by id, and is authoritative rather than eventually consistent (R11). Each `interest` still answers whether. Erasure removes the participant from it (FR-055).

### View `interest-rows` over `interest`

Rows: the interest state. Answers `GET /me/interests` for the participant's own id. A row is removed when the interest is deleted.

### Notification — event sourced entity `notification`, id `participantId:developmentId`

| Field | Type | Classification |
|---|---|---|
| participantId | link | personal link |
| developmentId, issueId | links | personal link |
| text | `Sealed[String]`: the rendered update with its record links | personal |
| why | `Sealed[String]`: which interest selected it | personal |
| state | `Prepared` / `Delivered` / `Failed(reason, attempts)` / `Read` | personal |

Events: `NotificationPrepared`, `NotificationDelivered`, `NotificationFailed`, `NotificationRead`.
Rules: the id makes a second preparation for the same participant and development address the same entity, which refuses it (FR-048, R7). Delivery to the inbox is the `Delivered` event itself; a later email channel would add a step.

### Developments — consumer `developments` over `issue` events

On each `DevelopmentPublished`, starts `update-delivery` under the development's id and waits for its end; every other event is ignored. A repeat starts nothing new (R7).

### UpdateDelivery — workflow `update-delivery`, id = `developmentId`

Steps: `resolveIssue` (authoritative read of the Issue for the development) → `materiality` (a correction of an update's wording is `Minor` and ends here) → `followers` (reads `issue-followers` by the Issue's id, R11) → `render` (wording built deterministically from the development's record by kind, with its links; a narrative update is quoted as published; no model and no reviewer in this step, FR-047) → `prepare` (one `Notification` per follower whose `interest`, read by id, is active and older than the development, idempotent) → `deliver` (the `Delivered` event; a failed attempt is recorded and the step retried) → `end`. Started by consumer `developments` over `issue` events, which waits for the workflow's end before the event is done with: a workflow a restart interrupted resumes only when next addressed, and the redelivered event addresses it (R7, SC-007).

### View `inbox` over `notification`

Rows per participant: developmentId, issueId, sealed text, state, at. Opened by the citizen endpoint only for the participant's own id.

## Erasure

### ErasureRequest — workflow `erasure`, id = `participantId`

| Field | Type | Classification |
|---|---|---|
| participantId | link | restricted |
| cause | `Requested` / `Dormancy` | restricted; copied as a category onto each retained public contribution |
| verification | `SessionVerified(at)` / `HeldForOperator(reason)` / `OperatorVerified(principal, at)` / `NotRequired` (cause `Dormancy`) | restricted |
| scope | the lists of conversation, concern, contribution, expression, interest, notification and link ids found | restricted |
| retained | the `pub-…` references whose public text remains, with the residue check result | restricted |
| steps completed | per step, time | restricted |
| receipt | `Receipt(cause, classes erased, counts, authorisedBy, at, verifiedBy)` | restricted; contains no personal value |

Steps (R1): `verify` (marks the participant `Requested`; pauses when `HeldForOperator`, and the operator's `operator-verify` command moves on; a `Dormancy` erasure is `NotRequired`) → `inventory` (reads `participant-records` by id, authoritative) → `revoke` (stops each interest and removes the participant from each issue's followers, so nothing new is prepared for them) → `withdraw` (each attributed or restricted copy withdrawn from its Issue, and every contribution not yet published withdrawn so an open review cannot publish it later) → `residue` (each published de-identified copy: unflagged, it stays and is marked `AuthorErased(pub, cause)`, FR-064; flagged by the privacy check or a reviewer, it is withdrawn, FR-057) → `destroyKey` (deletes the subject's key from the secret store; from here every `Sealed` for the subject opens as `Erased`) → `purgeDerived` (reads the index again, then deletes the conversation, concern, contribution, expression, interest and notification entities, erases and deletes each issue link, then the index; a deleted entity's view rows go with it) → `complete` (`ErasureCompleted` on the participant; the receipt, with `completionVerified` when the key is confirmed gone). Each step is idempotent and re-runnable.

Commands: `start` (a second start, or a dormancy firing for an erasure already running, answers the record unchanged: `erasure.already-requested`), `operator-verify`, `status`.

### ParticipantRecords — key value entity `participant-records`, id = `participantId`

| Field | Type | Classification |
|---|---|---|
| conversations, concerns, contributions, expressions, interests, notifications | the ids of each record the participant holds | personal link |

Written where each record is created: the conversation and concern routes, the contribution route, the first write to an expression, the follow route, and the update workflow's `prepare` step. Read by the erasure workflow's `inventory` and `purgeDerived` steps, since only an endpoint can query a view in ankka 0.12 and an expression's id is keyed under the session key, which a step does not hold (FR-055). Deleted by `purgeDerived`. `GET /me/data` counts it.

### View `erasure-receipts` over the `erasure` workflow's state

Rows the operator reads: participantId, cause, stage (`held-for-operator`, `in-progress`, `completed`), held, requestedAt, receipt. No personal value is in a receipt by construction (FR-058): `ReceiptSuite` walks every type a receipt reaches and fails on a `Sealed` value or a string other than an operator's principal.

## What is personal, in one table

| Record | Sealed fields | Erased how |
|---|---|---|
| Participant | displayName, contact; credential hash | key destroyed; `ErasureCompleted` |
| Conversation | every message text, every proposal text | key destroyed; entity deleted |
| Concern | summary, elements | key destroyed; entity deleted |
| Contribution, DisclosureGrant | derivative, exactText | key destroyed; entity deleted after the Issue has withdrawn its copy |
| IssueLink | the whole link | overwritten and deleted |
| Expression | priority, trade-offs, considerations | key destroyed; entity deleted; aggregate row tombstoned |
| Interest | the whole record | deleted; follower row tombstoned |
| Notification | text, why | key destroyed; entity deleted; inbox row tombstoned |
| Issue | none | `ContributionWithdrawn` removes the public copy from state; counts recomputed; residue check |
| Investigation, Action, Decision, Outcome, PolicyOption | none | retained |
| ErasureRequest | none | retained as the audit record |

The platform's own erasure feature (ankka spec 042) will replace `Sealed` with its `Personal` type and the secret-store key with its keyring when it ships; the field classification table above is what that migration will need (research, "Requests").

## Agents

Each agent is a component with a typed reply and no memory of its own; its calls are made by Voice's
workflows, never by a route (R5, R6).

### ConcernExtraction — agent `concern-extraction`

One turn of a private conversation: a reply, and a clarifying question or a proposal (FR-006, FR-007).

### PrivacyFlagging — agent `privacy-flagging`

One typed call per submitted derivative: the enhanced-review flags and the reasons for each (FR-016, R6).

### ResearchAssistant — agent `research-assistant`

The research workflow's one step, with read-only tools (R15). Named apart from the workflow
`research`, because component ids are unique across kinds.

## Read models found during implementation

Views the routes read that the sections above imply but do not name. Each is registered and counted
by the inventory test like any other component.

| Component | Over | Read by |
|---|---|---|
| keyed view `access-log-rows` | `access-log` events, one row per access | `GET /staff/access-log` (FR-005) |
| view `staff-account-rows` | `staff-account` state | `GET /staff/accounts` |
| view `conversation-rows` | `conversation` events, one row per conversation, keyed by participant | `GET /me/conversations` |
| view `concern-rows` | `concern` events, one row per concern, keyed by participant | `GET /me/concerns` |
| view `contribution-rows` | `contribution` events: state, mode, flags, links, no text | `GET /staff/reviews` (FR-014) |
| view `issue-rows` | `issue` events: title, summary, counts, last development, public columns only | `GET /public/issues`, the Issue suggestions of FR-017 |
| view `investigation-rows` | `investigation` state: issue, question, status, task states; no task text | the investigations on `GET /public/issues/{issue}` (not `Suggested`), the open investigations and external follow-ups of `GET /staff/queue` (FR-038) |

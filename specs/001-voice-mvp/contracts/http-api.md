# Contract: HTTP API

Every route Voice serves, grouped by audience, because each audience is one endpoint with one ACL
(research R10). Bodies are JSON; absent values are left out, never `null`; times are ISO-8601
instants. Refusals are in [refusals.md](refusals.md); the records are in
[data-model.md](../data-model.md); how reasoning references resolve is in
[reasoning-integration.md](reasoning-integration.md).

Three served endpoints. The agents are components called by Voice's own workflows and endpoints; they have no endpoint of their own.

| Endpoint | Prefix | Audience | ACL |
|---|---|---|---|
| `PublicEndpoint` | `/public` | anyone, no credential | `AllowAll`; every response type is checked at build time to contain no sealed field and no participant id |
| `CitizenEndpoint` | `/me` | a pseudonymous participant with a session credential | `Authenticate` with an authenticator of Voice's own over the session credential, no I/O, yielding the participant as the principal (R9); every route is scoped to that participant's own records |
| `StaffEndpoint` | `/staff` | representative, reviewer, operator | `Authenticate` a person's token through `ankka-auth-oidc`; the role comes from the token's realm roles; the `staff-account` entity holds name, authority and active, never a role (R10) |

A service's only way in is HTTP (designing-services), so the web interface (R13) calls these routes
and nothing else.

## Public: `/public`

| Method and path | Query | Reply | Spec |
|---|---|---|---|
| `GET /public/issues` | `q` (keyword), `limit`, `after` | `IssueSummary[]`: id, title, summary, counts with caveat text, lastDevelopmentAt | FR-026, US3.4 |
| `GET /public/issues/{issue}` | | `IssuePage`: title, summary, published contributions (text, attribution, publishedAt, `pub` ref, `authorErased`: absent, `requested` or `dormancy`), counts of contributions by author status (present, withdrew, dormant), questions with hypotheses, evidence links with state and provenance, options, investigations (state label), actions (state, authority, owner, date, summary), decisions, outcomes, chronology, aggregates with caveat, limitations text | FR-020, FR-033, FR-042 |
| `GET /public/issues/{issue}/evidence/{evidence}` | | `EvidenceCard`: source, locator, passage, publishedAt, recordedAt, Voice's scope, authority, limitations, state | FR-021 |
| `GET /public/issues/{issue}/claims/{claim}` | | `ClaimExplanation`: statement, stance, derived-from evidence cards, source cards, contradicting and qualifying claims with their cards, revisedBy; or `{ "abstained": true, "reason": … }` | FR-023, FR-024, US3.1 to 3.3 |
| `GET /public/issues/{issue}/chronology` | | `Entry[]`: state (one of the seven), actor kind, at, ref | FR-042 |
| `GET /public/search` | `q`, `limit`, `after` | `Hit[]`: kind (issue, source, option), record id, version or date, title, snippet from public text only, link | FR-026, FR-027 |
| `GET /public/sources/{source}` | | `SourceCard`: name, licence, attribution, link | seed |

No public route creates anything. In particular, reading an Issue creates no interest (FR-045).
Snippets are built from the public view's columns only (`public.search.filter`).

## Citizen: `/me`

The session credential is carried as a bearer token the `CitizenEndpoint` resolves to a participant
id (R9). A route that names a record checks it belongs to that participant and answers 404 otherwise.

### Session

| Method and path | Body | Reply | Spec |
|---|---|---|---|
| `POST /me/sessions` | `{ "invite": "<pilot invite code>" }`, no credential | `{ "participant": "prt-…", "recoveryCredential": "<shown once>", "session": "<token>", "expiresAt", "notice": { "privacy": …, "ai": … } }` | FR-001 to FR-003, R9 |
| `POST /me/sessions/recover` | `{ "recoveryCredential": … }`, no credential | `{ "participant", "recoveryCredential": "<a fresh one; the old is rotated>", "session", "expiresAt" }` | FR-002, US1.6 |
| `POST /me/sessions/renew` | `{}` | `{ "session", "expiresAt" }` | R9 |
| `GET /me/notice` | | the privacy and AI notice text | FR-003 |

### Conversation and concern

| Method and path | Body | Reply | Spec |
|---|---|---|---|
| `POST /me/conversations` | `{}` | `Conversation` (id, messages, proposals) | US1 |
| `GET /me/conversations` | | `ConversationSummary[]` | US1.6 |
| `GET /me/conversations/{conversation}` | | `Conversation` with messages, proposals, confirmed concern ids | |
| `POST /me/conversations/{conversation}/messages` | `{ "text": … }` | `202` `{ "turn": "cnv-…:turn-<seq>", "message": <the recorded message> }`; the reply and any proposal appear on the conversation when the turn ends | FR-006, FR-007, R5 |
| `GET /me/conversations/{conversation}/turns/{turn}` | | `TurnStatus`: `Thinking` / `Replied(reply, proposal?)` / `CouldNotInterpret(message)` | R5 |
| `GET /me/conversations/{conversation}/stream` | SSE, `after=<seq>` | `message`, `proposal` and `turn` events as they are recorded; the interface reconnects at the gateway's bound | R5 |
| `POST /me/conversations/{conversation}/proposals/{proposal}/edit` | `{ "summary", "elements": [...] }` | `ConcernProposal` marked edited | FR-009 |
| `POST /me/conversations/{conversation}/proposals/{proposal}/confirm` | `{}` | `Concern` | FR-008, FR-009 |
| `POST /me/conversations/{conversation}/proposals/{proposal}/discard` | `{}` | `Conversation` | |
| `GET /me/concerns` | | `Concern[]` with their contributions' states | US1.5 |

A turn is the `conversation-turn` workflow (R5): the route records the message and starts the
workflow; its step opens the sealed transcript for this call, calls the extraction agent, and records
Voice's reply and any proposal on the conversation. Nothing is created outside the conversation, and
nothing a browser waits on runs the model.

### Disclosure

| Method and path | Body | Reply | Spec |
|---|---|---|---|
| `GET /me/concerns/{concern}/sharing` | | `SharingOptions`: four modes with plain-language text, the proposed derivative, the exact audience per mode, flags Voice detected | FR-011, FR-012 |
| `POST /me/concerns/{concern}/contributions` | `{ "mode", "text" }` | `Contribution` in `Approved by citizen`: the exact text as sent | FR-012, FR-013 |
| `POST /me/contributions/{contribution}/submit` | `{}` | `Contribution` in `Under moderation`, or `Private` kept private (no review) | FR-014 |
| `GET /me/contributions/{contribution}` | | `Contribution` with state, review outcome and reason, Issue suggestion, association | FR-015, US2.3 |
| `POST /me/contributions/{contribution}/issue` | `{ "accept": "iss-…" }` or `{ "new": true }` | `Contribution` with the association recorded | FR-017 |
| `POST /me/contributions/{contribution}/withdraw` | `{}` | `Contribution` in `Withdrawn` | FR-018 |

### Deliberation

| Method and path | Body | Reply | Spec |
|---|---|---|---|
| `GET /me/issues/{issue}/expression` | | `Expression` (own only) | FR-030 |
| `POST /me/issues/{issue}/expression/priority` | `{ "level": 1..5 }` | `Expression` | FR-030 |
| `POST /me/issues/{issue}/expression/trade-offs` | `{ "option", "accepts", "rejects" }` | `Expression` | FR-030 |
| `POST /me/issues/{issue}/expression/considerations` | `{ "option", "text" }` | `Expression` | FR-030 |
| `POST /me/issues/{issue}/expression/withdraw` | `{}` | `Expression` withdrawn | FR-030 |

### Interests and inbox

| Method and path | Body | Reply | Spec |
|---|---|---|---|
| `GET /me/interests` | | `Interest[]` | FR-045 |
| `PUT /me/interests/{issue}` | `{ "delivery": "inbox-immediate" }` (the only value in this feature) | `Interest` | FR-045, FR-046 |
| `DELETE /me/interests/{issue}` | | `204` | FR-046 |
| `GET /me/inbox` | `after` | `InboxEntry[]`: development, issue, text with record links, why, state, at | FR-047, FR-049 |
| `POST /me/inbox/{development}/read` | `{}` | `InboxEntry` | |

### Data rights

| Method and path | Body | Reply | Spec |
|---|---|---|---|
| `GET /me/data` | | `DataSummary`: what Voice holds for this participant by class, what is public, what retention applies | FR-052 |
| `POST /me/erasure` | `{}`, or `{ "recoveryCredential": "<current credential>" }` | 202 `ErasureStatus`: cause (`requested`), state (`Verifying`, `HeldForOperator`, `InProgress`, `Completed`), `rule` (`erasure.unverified` when held), what will be erased, what is retained and why, what remains public. With the participant's current recovery credential the request is verified by it and starts at once; without it, or with one that does not match, it is held for an operator. A second request answers 200 with `rule` `erasure.already-requested` | FR-052, FR-053 |
| `GET /me/erasure` | | `ErasureStatus` | |

After `Completed`, every `/me` route for the credential answers `participant.erased`.

## Staff: `/staff`

A person's token is verified by `ankka-auth-oidc` against the issuer the service descriptor names;
the subject is looked up in `staff-account` for a role (R10). Routes state the role they need.

### Operator

| Method and path | Role | Body | Reply | Spec |
|---|---|---|---|---|
| `PUT /staff/accounts/{principal}` | operator | `{ "displayName", "authority"?, "active" }` (the role is the token's) | `StaffAccount` | FR-004 |
| `GET /staff/accounts` | operator | | `StaffAccount[]` | |
| `GET /staff/erasures` | operator | `state` | `ErasureStatus[]` with receipts and the cause of each (`requested` or `dormancy`), no personal value | FR-058, FR-064 |
| `POST /staff/erasures/{participant}/verify` | operator | `{ "evidence": "<how identity was verified>" }` | `ErasureStatus` | FR-053 |
| `GET /staff/access-log` | operator | `since` | `AccessEntry[]`: principal, route, record id, at | FR-005 |

### Reviewer

| Method and path | Role | Body | Reply | Spec |
|---|---|---|---|---|
| `GET /staff/reviews` | reviewer | `state` | `ReviewItem[]`: contribution id, derivative text, mode, audience, flags, suggested Issues, age | FR-014 |
| `POST /staff/reviews/{contribution}/enhanced` | reviewer | `{ "reason" }` | `ReviewItem` with the second step recorded | FR-016 |
| `POST /staff/reviews/{contribution}/approve` | reviewer | `{ "issue": "iss-…" \| "new": { "title", "summary" } }` | `ReviewItem` published | FR-015, FR-017 |
| `POST /staff/reviews/{contribution}/reject` | reviewer | `{ "reason" }` | `ReviewItem` rejected | FR-015 |
| `GET /staff/issues/{issue}/evidence` | reviewer, representative | | `EvidenceLink[]` with provenance, including candidates | FR-021 |
| `POST /staff/issues/{issue}/evidence` | reviewer | `{ "source", "locator", "excerpt", "publishedAt"?, "scope", "authority", "limitations" }` | `EvidenceLink` in `Candidate` (written to reasoning on acceptance, not here) | US3.6 |
| `POST /staff/issues/{issue}/evidence/{evidence}/accept` | reviewer | `{}` | `EvidenceLink` accepted; reasoning evidence recorded; review prompts for connected claims | FR-028 |
| `POST /staff/issues/{issue}/evidence/{evidence}/reject` | reviewer | `{ "reason" }` | `EvidenceLink` rejected | |
| `POST /staff/issues/{issue}/evidence/{evidence}/supersede` | reviewer | `{ "by": "<evidence id>", "reason" }` | `EvidenceLink` superseded | edge case 9 |
| `GET /staff/issues/{issue}/review-prompts` | reviewer, representative | `state` | `ReviewPrompt[]`: evidence, the claims and decisions deriving from it, raised at, resolution | FR-028 |
| `POST /staff/issues/{issue}/review-prompts/{prompt}/resolve` | reviewer | `{ "note" }` | `ReviewPrompt` resolved; nothing else changes | FR-028 |
| `POST /staff/issues/{issue}/claims` | reviewer | `{ "statement", "derivesFrom": [...], "stances": [...], "revises"? }` | `ClaimRef` linked | contract: reasoning |
| `POST /staff/issues/{issue}/questions` | reviewer | `{ "statement", "hypotheses": [...] }` | `QuestionRef` linked | |
| `PUT /staff/issues/{issue}/options/{option}` | reviewer, representative | `{ "title", "description", "costs", "benefits", "openQuestions" }` | `PolicyOption` | FR-032 |

### Representative

| Method and path | Role | Body | Reply | Spec |
|---|---|---|---|---|
| `GET /staff/queue` | representative | | `WorkQueue`: new Issues, pending reviews, open review prompts, open investigations, investigation tasks awaiting an external party, each with state and age | FR-036 |
| `GET /staff/issues/{issue}` | representative | | `IssueForStaff`: the public page plus restricted contributions this role may see, participant-derived measures with caveats, unresolved questions, contrary perspectives | FR-037 |
| `POST /staff/issues/{issue}/research` | representative | `{ "question" }` | `202` `{ "run": "inv-…" }`; starts the `research` workflow (R15) | FR-038, US5.3 |
| `GET /staff/research/{run}` | representative | | `ResearchRun`: state, and when done `ResearchSuggestions` (sources, questions, investigations, each labelled `suggested` with the records the agent read) | FR-038 |
| `POST /staff/issues/{issue}/investigations` | representative | `{ "question", "fromSuggestion"? }` | `Investigation` in `Open` | FR-039 |
| `POST /staff/investigations/{investigation}/tasks` | representative | `{ "text", "owner", "due" }` | `Investigation` | US5 |
| `POST /staff/issues/{issue}/actions` | representative | `{ "investigation"?, "authority", "owner", "date", "rationale", "state", "publicSummary" }` | `Action` with recordedBy and recordedAt | FR-040 |
| `POST /staff/actions/{action}/state` | representative | `{ "state" }` | `Action` | FR-041 |
| `POST /staff/issues/{issue}/decisions` | representative | `{ "approach", "authority", "alternatives", "justification" }` | `Decision` | |
| `POST /staff/actions/{action}/outcomes` | representative | `{ "measured", "limitations", "claimsResolved" }` | `Outcome` | FR-043 |
| `POST /staff/issues/{issue}/updates` | representative | `{ "text" }` (written or reviewed by the representative) | `Development` of kind `UpdatePublished`; the delivery workflow starts. Record-derived developments need no route: they start delivery as they are recorded (FR-047) | FR-044 |

Every staff request on a restricted record appends an `AccessEntry` (FR-005), written by the endpoint
after the role check, through a consumer-free direct command to the `access-log` entity keyed by day.

## Server-sent events

| Path | Events | Spec |
|---|---|---|
| `GET /me/conversations/{conversation}/stream` | `token`, `reply`, `proposal` | the agent's streamed reply; the proposal is sent whole at the end |
| `GET /me/inbox/stream` | `notification` | a new inbox entry as it is prepared, for the open interface |

The gateway bounds a response at fifteen seconds (limitations); the interface reconnects and the
conversation route also answers in full without the stream.

## Headers and identity

- `/me` routes: `Authorization: Bearer <session token>`. The token is
  `base64url(participantId "." expiresAt) "." base64url(hmac-sha256)` under `VOICE_SESSION_KEY`,
  twelve hours, verified in the endpoint's ACL with no I/O (R9). It is not a JWT and never reaches
  `Oidc`. Revocation and erasure are checked in the handler against the participant entity.
- The **recovery credential** is different: 32 random bytes base64url, shown once by `POST /me/sessions`
  and by each recover, held only by the participant; Voice keeps its hash. It is sent only to the two
  session routes above, never as a bearer header.
- `/staff` routes: `Authorization: Bearer <OIDC access token>` from the issuer named `staff` in the
  descriptor, verified by `ankka-auth-oidc`; roles are the token's realm roles (R10).
- Requests arrive through the web proxy with `X-Ankka-Caller: internet` and `Authorization`
  unchanged; cookies are not used.
- Every response carries `Voice-State-Labels: submitted,ai-interpreted,under-moderation,staff-acknowledged,under-investigation,action-recorded,outcome-verified`
  once, on the Issue and chronology routes, so an interface renders the seven states from the server's
  vocabulary (FR-061).

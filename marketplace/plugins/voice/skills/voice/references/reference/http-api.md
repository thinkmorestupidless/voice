# HTTP API

> Look up every route Voice serves, by endpoint and audience, with its method, path, body, reply and the role it needs, and the headers, tokens and server-sent events that go with them.

Source: https://docs.makethemlisten.uk/reference/http-api/
Every route Voice serves, grouped by audience, because each audience is one endpoint with one ACL.
Bodies are JSON; absent values are left out, never `null`; times are ISO-8601 instants. A refusal is a
body `{ "rule", "status", "error", "names" }`, listed in [Refusals](refusals.md). The web interface
calls these routes and nothing else; the agents are components called by Voice's own workflows and
have no route of their own.

| Endpoint | Prefix | Audience | ACL |
|---|---|---|---|
| `PublicEndpoint` | `/public` | anyone, no credential | allow all; every response type is checked at build time to contain no sealed field and no participant id |
| `CitizenEndpoint` | `/me` | a pseudonymous participant with a session | Voice's own authenticator over the session token, with no I/O, yielding the participant as the principal; every route is scoped to that participant's own records |
| `StaffEndpoint` | `/staff` | representative, reviewer, operator | a person's OIDC token through `ankka-auth-oidc`; the role is the token's realm role; the `staff-account` entity holds name, authority and active, never a role |

## Public: `/public`

| Method and path | Query | Reply |
|---|---|---|
| `GET /public/issues` | `q` (keyword), `limit`, `after` | `IssueSummary[]`: id, title, summary, counts with caveat text, `lastDevelopmentAt` |
| `GET /public/issues/{issue}` | | `IssuePage`: title, summary, published contributions (text, attribution, `publishedAt`, `pub` reference, `authorErased` absent, `requested` or `dormancy`), counts of contributions by author status, questions with hypotheses, evidence links with state and provenance, options, investigations with their state label, actions (state, authority, owner, date, summary, `completed`), decisions, outcomes, chronology, the aggregate with its caveat, limitations text |
| `GET /public/issues/{issue}/evidence/{evidence}` | | `EvidenceCard`: source, locator, passage, `publishedAt`, `recordedAt`, scope, authority, limitations, state |
| `GET /public/issues/{issue}/claims/{claim}` | | `ClaimExplanation`: statement, stances, the evidence cards it derives from with their source cards, the contradicting and qualifying claims with their cards, `revisedBy`; or `{ "abstained": true, "reason": … }` |
| `GET /public/issues/{issue}/questions/{question}` | | the question with its hypotheses and the claims on each; or `{ "abstained": true, "reason": … }` |
| `GET /public/issues/{issue}/chronology` | | `Entry[]`: state (one of the seven labels), actor kind, `at`, `ref` |
| `GET /public/search` | `q`, `limit`, `after` | `Hit[]`: kind (`issue`, `source`, `option`), record id, version or date, title, snippet from public text only, link |
| `GET /public/sources/{source}` | | `SourceCard`: name, licence, attribution, link |

No public route creates anything; reading an issue creates no interest. Snippets are built from the
public view's columns only. A private, restricted, withdrawn or unpublished record answers `404`
`public.not-found`, the same as one that never existed.

## Citizen: `/me`

The session token is a bearer token the endpoint resolves to a participant id. A route that names a
record checks it belongs to that participant and answers `404` otherwise.

### Session

| Method and path | Body | Reply |
|---|---|---|
| `POST /me/sessions` | `{ "invite": "<pilot invite code>" }`, no credential | `201` `{ "participant": "prt-…", "recoveryCredential": "<shown once>", "session": "<token>", "expiresAt", "notice": { "privacy", "ai" } }` |
| `POST /me/sessions/recover` | `{ "recoveryCredential" }`, no credential | `{ "participant", "recoveryCredential": "<a fresh one; the old is rotated>", "session", "expiresAt" }` |
| `POST /me/sessions/renew` | `{}` | `{ "session", "expiresAt" }` |
| `GET /me/notice` | | the privacy and AI notices as Markdown |

### Conversation and concern

| Method and path | Body | Reply |
|---|---|---|
| `POST /me/conversations` | `{}` | `Conversation`: id, messages, proposals, confirmed |
| `GET /me/conversations` | | `ConversationSummary[]` |
| `GET /me/conversations/{conversation}` | | `Conversation` with messages, proposals and confirmed concern ids |
| `POST /me/conversations/{conversation}/messages` | `{ "text" }` | `202` `{ "turn": "cnv-…:turn-<seq>", "message": <the recorded message> }`; the reply and any proposal appear on the conversation when the turn ends |
| `GET /me/conversations/{conversation}/turns/{turn}` | | `TurnStatus`: `Thinking`, `Replied` with `reply` and an optional `proposal`, or `CouldNotInterpret` with `message` |
| `GET /me/conversations/{conversation}/stream` | SSE, `after=<seq>` | `message`, `proposal` and `turn` events as they are recorded |
| `POST /me/conversations/{conversation}/proposals/{proposal}/edit` | `{ "summary", "elements": [ … ] }` | `ConcernProposal` marked edited |
| `POST /me/conversations/{conversation}/proposals/{proposal}/confirm` | `{}` | `Concern` |
| `POST /me/conversations/{conversation}/proposals/{proposal}/discard` | `{}` | `Conversation` |
| `GET /me/concerns` | | `Concern[]` with their contributions' states |

A turn is the `conversation-turn` workflow: the route records the message and starts the workflow;
its step opens the sealed transcript for this call, calls the extraction agent, and records Voice's
reply and any proposal on the conversation. Nothing a browser waits on runs the model.

### Disclosure

| Method and path | Body | Reply |
|---|---|---|
| `GET /me/concerns/{concern}/sharing` | | `SharingOptions`: the four modes with plain-language text, the proposed derivative, the exact audience per mode, the flags Voice detected |
| `POST /me/concerns/{concern}/contributions` | `{ "mode", "text" }` | `Contribution`, approved by the participant, holding the exact text as sent |
| `POST /me/contributions/{contribution}/submit` | `{}` | `Contribution` under moderation; a private one is refused |
| `GET /me/contributions/{contribution}` | | `Contribution` with state, review outcome and reason, issue suggestion, association |
| `POST /me/contributions/{contribution}/issue` | `{ "accept": "iss-…" }` or `{ "new": true }` | `Contribution` with the association recorded |
| `POST /me/contributions/{contribution}/withdraw` | `{}` | `Contribution` withdrawn |

Modes on the wire: `private`, `de-identified`, `representative-only`, `public-attribution`.

### Deliberation

| Method and path | Body | Reply |
|---|---|---|
| `GET /me/issues/{issue}/expression` | | `Expression`, the participant's own only |
| `POST /me/issues/{issue}/expression/priority` | `{ "level": 1..5 }` | `Expression` |
| `POST /me/issues/{issue}/expression/trade-offs` | `{ "option", "accepts", "rejects" }` | `Expression` |
| `POST /me/issues/{issue}/expression/considerations` | `{ "option", "text" }` | `Expression` |
| `POST /me/issues/{issue}/expression/withdraw` | `{}` | `Expression` withdrawn |

### Interests and inbox

| Method and path | Body | Reply |
|---|---|---|
| `GET /me/interests` | | `Interest[]` |
| `PUT /me/interests/{issue}` | `{ "delivery": "inbox-immediate" }`, the only value | `Interest` |
| `DELETE /me/interests/{issue}` | | `204` |
| `GET /me/inbox` | `after` | `InboxEntry[]`: development, issue, text with record links, why, state, `at` |
| `POST /me/inbox/{development}/read` | `{}` | `InboxEntry` |
| `GET /me/inbox/stream` | SSE | `notification` events as entries are prepared |

### Data rights

| Method and path | Body | Reply |
|---|---|---|
| `GET /me/data` | | `DataSummary`: what Voice holds for this participant by class, what is public, what retention applies, the erasure status |
| `POST /me/erasure` | `{}`, or `{ "recoveryCredential": "<current credential>" }` | `202` `ErasureStatus`: cause `requested`, state `Verifying`, `HeldForOperator`, `InProgress` or `Completed`, `rule` `erasure.unverified` when held, what will be erased, what is retained and why, what remains public. With the current recovery credential the request is verified and starts at once; without it, or with one that does not match, it is held for an operator. A second request answers `200` with `rule` `erasure.already-requested` |
| `GET /me/erasure` | | `ErasureStatus` |

After completion, every `/me` route for the participant answers `410` `participant.erased`.

## Staff: `/staff`

A person's token is verified against the issuer the service descriptor names; the subject is looked
up in `staff-account` for its name, authority and whether it is active. Each route states the role
it needs. Every staff read of a restricted record, and every refused staff request, appends an entry
to the access log.

### Operator

| Method and path | Body | Reply |
|---|---|---|
| `PUT /staff/accounts/{subject}` | `{ "displayName", "authority"?, "active" }`; the role is the token's | `StaffAccount` |
| `GET /staff/accounts` | | `StaffAccount[]` |
| `GET /staff/erasures` | query `state`: `held-for-operator`, `in-progress`, `completed` | `ErasureStatus[]` with receipts and the cause of each, no personal value |
| `POST /staff/erasures/{participant}/verify` | `{ "evidence": "<how identity was verified>" }` | `ErasureStatus` |
| `GET /staff/access-log` | query `since` | `AccessEntry[]`: principal, route, record id, `at` |

### Reviewer

| Method and path | Role | Body | Reply |
|---|---|---|---|
| `GET /staff/reviews` | reviewer | query `state` | `ReviewItem[]`: contribution id, derivative text, mode, audience, flags, suggested issues, age |
| `POST /staff/reviews/{contribution}/enhanced` | reviewer | `{ "reason" }` | `ReviewItem` with the second step recorded |
| `POST /staff/reviews/{contribution}/approve` | reviewer | `{ "issue": "iss-…" }` or `{ "new": { "title", "summary" } }` | `ReviewItem` published |
| `POST /staff/reviews/{contribution}/reject` | reviewer | `{ "reason" }` | `ReviewItem` rejected |
| `GET /staff/issues/{issue}/evidence` | reviewer, representative | | `EvidenceLink[]` with provenance, including candidates |
| `POST /staff/issues/{issue}/evidence` | reviewer | `{ "source", "locator", "excerpt", "publishedAt"?, "scope", "authority", "limitations" }` | `EvidenceLink` as a candidate; written to reasoning on acceptance |
| `POST /staff/issues/{issue}/evidence/{evidence}/accept` | reviewer | `{}` | `EvidenceLink` accepted; reasoning evidence recorded; review prompts raised for connected claims and decisions |
| `POST /staff/issues/{issue}/evidence/{evidence}/reject` | reviewer | `{ "reason" }` | `EvidenceLink` rejected |
| `POST /staff/issues/{issue}/evidence/{evidence}/supersede` | reviewer | `{ "by": "<evidence id>", "reason" }` | `EvidenceLink` superseded |
| `GET /staff/issues/{issue}/review-prompts` | reviewer, representative | query `state` | `ReviewPrompt[]`: evidence, the claims and decisions deriving from it, raised at, resolution |
| `POST /staff/issues/{issue}/review-prompts/{prompt}/resolve` | reviewer | `{ "note" }` | `ReviewPrompt` resolved; nothing else changes |
| `POST /staff/issues/{issue}/claims` | reviewer | `{ "statement", "derivesFrom": [ … ], "stances": [ { "hypothesis", "stance" } ], "revises"? }` | `ClaimRef` linked |
| `POST /staff/issues/{issue}/questions` | reviewer | `{ "statement", "hypotheses": [ { "id", "statement" } ] }` | `QuestionRef` linked |
| `GET /staff/issues/{issue}/options` | reviewer, representative | | `PolicyOption[]` |
| `PUT /staff/issues/{issue}/options/{option}` | reviewer, representative | `{ "title", "description", "costs", "benefits", "openQuestions" }` | `PolicyOption` |

### Representative

| Method and path | Body | Reply |
|---|---|---|
| `GET /staff/queue` | | `WorkQueue`: new issues, pending reviews, open review prompts, open investigations, tasks awaiting an external party, each with kind, state, age and link |
| `GET /staff/issues/{issue}` | | `IssueForStaff`: the public page plus the restricted contributions this role may see, participant-derived measures with caveats, unresolved questions, contrary perspectives |
| `POST /staff/issues/{issue}/research` | `{ "question" }` | `202` `{ "run": "inv-…" }`; starts the `research` workflow |
| `GET /staff/research/{run}` | | `ResearchRun`: state, and when done the suggestions: sources, questions and investigations, each labelled suggested with the records the agent read |
| `POST /staff/issues/{issue}/investigations` | `{ "question", "fromSuggestion"? }` | `Investigation`, open |
| `GET /staff/investigations/{investigation}` | | `Investigation` with its tasks and records |
| `POST /staff/investigations/{investigation}/tasks` | `{ "text", "owner", "due" }` | `Investigation` |
| `POST /staff/issues/{issue}/actions` | `{ "investigation"?, "authority", "owner", "date", "rationale", "state", "publicSummary" }` | `Action` with `recordedBy` and `recordedAt` |
| `POST /staff/actions/{action}/state` | `{ "state" }` | `Action` |
| `POST /staff/issues/{issue}/decisions` | `{ "approach", "authority", "alternatives", "justification" }` | `Decision` |
| `POST /staff/actions/{action}/outcomes` | `{ "measured", "limitations", "claimsResolved" }` | `Outcome` |
| `POST /staff/issues/{issue}/updates` | `{ "text" }`, written or reviewed by the representative | `Development` of kind `update-published`; delivery starts. Record-derived developments need no route: they start delivery as they are recorded |

Action states on the wire: `proposed`, `in-progress`, `completed`.

## Server-sent events

| Path | Carries |
|---|---|
| `GET /me/conversations/{conversation}/stream` | each `message`, `proposal` and `turn` as it is recorded, from `after` |
| `GET /me/inbox/stream` | each `notification` as it is prepared |

The platform's stream sends unnamed events with no id, so each event's data is `{ "type", "data" }`
and a client keeps its own cursor. The gateway ends a response at fifteen seconds; a client
reconnects, and the conversation route also answers in full without the stream.

## Headers and identity

- `/me` routes: `Authorization: Bearer <session token>`. The token is
  `base64url(participantId "." expiresAt) "." base64url(hmac-sha256)` under `VOICE_SESSION_KEY`, valid
  for `VOICE_SESSION_HOURS`, verified in the endpoint with no I/O. It is not a JWT and never reaches
  an OIDC issuer. Erasure is checked in the handler against the participant.
- The recovery credential is different: the participant id, `~`, and 32 random bytes in base64url,
  shown once by `POST /me/sessions` and by each recovery; Voice keeps its hash. It is sent only to the
  two session routes and `POST /me/erasure`, never as a bearer header.
- `/staff` routes: `Authorization: Bearer <OIDC access token>` from the issuer named `staff` in the
  descriptor, with audience `voice-staff`; the role is the token's realm role.
- Requests arrive through the web proxy with `X-Ankka-Caller: internet` and `Authorization`
  unchanged; cookies are not used.
- The issue and chronology routes send
  `Voice-State-Labels: submitted,ai-interpreted,under-moderation,staff-acknowledged,under-investigation,action-recorded,outcome-verified`,
  so an interface renders the seven states from the server's vocabulary.

# Records

> Look up every record Voice keeps, the ankka component that holds it, its fields with their classification, the events that change it, and what erasure does to each.

Source: https://docs.makethemlisten.uk/reference/records/
Every record Voice keeps, which component holds it, and which of its fields are personal. The
conventions hold throughout:

- **Identifiers** are opaque strings the service mints: a prefix naming the kind, a hyphen and a
  26-character ULID, `prt-…`, `cnv-…`, `ctb-…`, `iss-…`. An identifier is never derived from
  anything a person typed, and a participant's identifier appears in no public record.
- **Time** is an instant the endpoint read once and carried in the command. Handlers have no clock.
- **A sealed field** is `Sealed[A]`, present or erased, serialised as `{ "subject", "ciphertext" }`
  or `{ "subject" }` once erased. [Sealed fields and erasure](../concepts/sealing.md).
- **Classification** per field: public, restricted, personal, sensitive.
- **Provenance**: every command carries the actor, the time, a causation id and a correlation id,
  set once at the edge and recorded on every event.

## Participation

**Participant**, event sourced entity `participant`, `prt-…`: the recovery credential's hash
(personal); `displayName` and `contact`, sealed and optional (personal); the erasure state, none,
requested or completed (restricted). Events: registered, credential rotated, erasure requested,
erasure completed. A participant with a completed erasure refuses every command but a query.

**StaffAccount**, key value entity `staff-account`, id the issuer's subject: principal (restricted),
display name and authority (public when shown on an action). Roles are the token's, never held here.

**Conversation**, event sourced entity `conversation`, `cnv-…`: the participant (personal link);
messages, each with its sequence, author, sealed text and time (personal); proposals (personal);
confirmed concern ids (personal link). Events: started, message recorded, concern proposed, proposal
edited, confirmed, discarded. A confirmed proposal is bound to the message sequence numbers it was
extracted from.

**ConcernProposal**, a value inside a conversation: `prp-…`; sealed summary; elements, each a kind
(problem, who, where, when, impact, wish), sealed text and source spans; confidence; suggested issues;
whether the participant edited it.

**ConversationTurn**, workflow `conversation-turn`, `cnv-…:turn-<seq>`: thinking, replied or could
not interpret.

**Concern**, event sourced entity `concern`, `cnc-…`: the participant, conversation and proposal
(personal links); the confirmed summary and elements, sealed (personal); contribution ids.

## Disclosure

**Contribution**, event sourced entity `contribution`, `ctb-…`: concern and participant (personal
links); mode (restricted); the derivative, sealed, the exact approved text (personal until
published as a public copy); state, drafting, approved by citizen, under moderation, published,
rejected with reason, withdrawn (restricted); flags, distinctive, health, minor, small group
(restricted); the review with reviewer, decision, reason, time and the second step (restricted); the
issue once associated (restricted). Private never enters review; submission needs the exact approved
text; a flagged contribution needs the enhanced review before publication; withdrawn is terminal.

**DisclosureGrant**, a value inside a contribution: scope, audience, purpose, the sealed exact text,
granted and withdrawn times. Evidence of consent, erased with its subject.

**DisclosureReview**, workflow `disclosure-review`, id the contribution: check, await reviewer,
await second step when flagged, publish or reject; withdraw at any step.

## Issues

**Issue**, event sourced entity `issue`, `iss-…`, all public unless said: title and summary;
de-identified contributions, each a public reference `pub-…`, plaintext, published time and
`authorErased` with its cause; attributed contributions, text and name sealed under the author's
key; restricted contributions, sealed (restricted to representatives); question references;
evidence links, each a reference, a state (candidate, accepted, rejected, superseded), reviewer and
time, with Voice's scope, authority and limitations; options; investigations, actions, decisions and
outcomes by id; review prompts (restricted); the chronology; the counts of contributions, withdrawn,
and authors erased by cause, with the caveat. The issue's events carry no participant identifier.

**IssueLink**, key value entity `issue-link`, `pub-…`: contribution and participant (personal
links), issue (restricted). The only record that joins a public text to its author; erased by
overwriting and deleting.

**PolicyOption**, key value entity `policy-option`, `pol-…`: title, description, costs, benefits,
open questions, all public; no endorsement field exists.

**SourceCard**, key value entity `source-card`, id the reasoning source id: name, licence,
attribution, link, published date, all public.

Views: `issue-rows`, public columns only, for the issue list and suggestions; `public-search`, one
row per public record over issues, options and sources, with no personal, restricted or sensitive
column.

## Deliberation

**Expression**, event sourced entity `expression`, `exp-…`, one per participant per issue: the
participant and issue (sensitive links); priority, trade-offs and considerations, sealed
(sensitive); withdrawn. Never appears in an evidence or claim view. View `issue-aggregates`: one row
per expression with the priority level as a plain integer and the participant hashed under a
per-view salt; the query groups by issue and suppresses small cells.

## Evidence

Voice holds references, typed `QuestionRef`, `HypothesisRef`, `SourceRef`, `EvidenceRef` and
`ClaimRef`, resolved through the reasoning service. A candidate's passage is held on the evidence
link until acceptance writes it to reasoning. [Voice and the reasoning service](../concepts/reasoning.md).

## Investigation and action

**Investigation**, event sourced entity `investigation`, `inv-…`, public: issue, question, state
(suggested by the agent, open by a representative, closed), tasks with text, owner, due date and
state, evidence requests. **Action**, `act-…`, public: issue and investigation, authority, owner,
rationale, public summary, date, state (proposed, in progress, completed), recorded by and at.
**Decision**, `dec-…`: approach, authority, alternatives, justification. **Outcome**, `out-…`:
action, measured, limitations, whether claims were resolved. **Development**, a value inside issue
events: `dev-…`, kind (action recorded, evidence accepted, decision recorded, outcome recorded,
update published), the record, materiality, a summary with links.

**Research**, workflow `research`, `inv-…`: issue, question, requested by, suggestions with the
agent's reasons and the records it read, state. **AccessLog**, event sourced entity `access-log`,
id the UTC day: one event per staff read of a restricted record, with the view `access-log-rows`.
**Dormancy**, timer `dormancy:<participant>` and timed action `dormancy-check`.

## Interests and notifications

**Interest**, key value entity `interest`, `<participant>:<issue>`: participant and issue (personal
links), delivery, since, active (personal). Erased by deleting. **IssueFollowers**, key value
entity `issue-followers`, id the issue: the following participants (personal links), kept where a
workflow step can read it by id. **Notification**, event sourced entity `notification`,
`<participant>:<development>`: participant, development and issue (personal links); text and why,
sealed (personal); state, prepared, delivered, failed, read. The id makes a second preparation
address the same record. **UpdateDelivery**, workflow `update-delivery`, id the development. The
consumer `developments` over issue events starts it. View `inbox`, opened for the participant's own
id only; view `interest-rows`.

## Erasure

**ErasureRequest**, workflow `erasure`, id the participant, all restricted: cause (requested,
dormancy); verification (by session, held for operator, by operator, not required); the scope found;
the retained public references with the residue check's result; each step's time; the receipt with
cause, classes erased, counts, who authorised and verified, when, and whether completion was
verified. **ParticipantRecords**, key value entity `participant-records`, id the participant: the
ids of every conversation, concern, contribution, expression, interest and notification the
participant holds, written where each is created and deleted last. View `erasure-receipts`, the
operator's list.

## What is personal, in one table

| Record | Sealed fields | Erased how |
|---|---|---|
| Participant | display name, contact; the credential hash | key destroyed; erasure completed |
| Conversation | every message, every proposal text | key destroyed; entity deleted |
| Concern | summary, elements | key destroyed; entity deleted |
| Contribution, DisclosureGrant | derivative, exact text | key destroyed; entity deleted after the issue has withdrawn its copy |
| IssueLink | the whole link | overwritten and deleted |
| Expression | priority, trade-offs, considerations | key destroyed; entity deleted; aggregate row removed |
| Interest | the whole record | deleted; follower entry removed |
| Notification | text, why | key destroyed; entity deleted; inbox row removed |
| Issue | none | attributed and restricted copies withdrawn; an unflagged de-identified copy marked `authorErased`; a flagged one withdrawn; counts recomputed |
| Investigation, Action, Decision, Outcome, PolicyOption | none | retained |
| ErasureRequest | none | retained as the audit record |

## Agents

Each agent is a component with a typed reply and no memory of its own, called by a workflow and
never by a route. `concern-extraction`: one turn, a reply and a clarifying question or a proposal.
`privacy-flagging`: one call per submitted derivative, the flags with a reason each.
`research-assistant`: the research workflow's one step, with read-only tools.

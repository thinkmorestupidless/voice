# Feature Specification: Voice MVP demonstrator

**Feature Branch**: `001-voice-mvp`

**Created**: 2026-10-18

**Status**: Draft

**Input**: User description: "Spec out the Ankka Voice MVP demonstrator as defined in ankka-voice-specification-mvp.md in this directory: a privacy-first civic participation system where a citizen talks pseudonymously about a concern in a private conversation, confirms the system's structured interpretation, chooses exactly what to share and with whom, has the approved derivative joined to a shared public Issue, explores source-backed evidence and uncertainty, expresses priorities and trade-offs as opinions distinct from evidence, an authorised representative opens an investigation and records a dated accountable action, consenting followers receive one truthful update, anyone can independently search the public issue, and a citizen can have their personal data erased by field-level crypto-shredding while anonymised issue knowledge remains. Use the document's implementation slices S1–S7, acceptance criteria (section 11), MVP scope and out-of-scope lists (section 9), product principles (section 2.3) and definition of done (section 17) as the source of user stories, requirements and success criteria."

**Source document**: `ankka-voice-specification-mvp.md` (product and MVP specification v0.1, 18 October 2026). This feature specification restates that document as testable requirements; where the two disagree, this file is the one planning and implementation follow, and the disagreement is a defect to fix here.

## Overview

Voice connects citizens, their elected representatives, real-world evidence, public deliberation and accountable action. Conversation is the accessible interface to a durable, inspectable set of civic records and processes. The demonstrator proves one complete loop on a single local issue (school transport), with synthetic participants, a curated evidence pack and a simulated representative:

**voice → contribution → issue → investigation → action → feedback → erasure**

Four roles take part:

- **Citizen (participant)**: pseudonymous; converses, confirms, chooses what to share, explores, deliberates, follows, withdraws.
- **Representative (and authorised staff)**: reads participant-derived concerns with caveats, investigates, records actions and decisions, publishes updates.
- **Reviewer (moderator)**: approves or rejects publication of shared contributions; reviews a representative's narrative updates before publication; handles re-identification risk.
- **Operator (data protection administrator)**: provisions roles and pilot accounts, processes data-rights requests, reads audit receipts; is not entitled to read private content.

Twelve product principles from the source document bind every requirement below. The ones that most often decide a requirement: *heard, not merely reassured*; *truth ≠ preference*; *provenance over plausible prose*; *private by default*; *human authority*; *honest signal quality*; *minimise, then protect*.

## User Scenarios & Testing *(mandatory)*

The user stories follow the source document's implementation slices S1 to S7. Each is independently demonstrable and testable; together they are the definition of done in section 17 of the source document.

### User Story 1 - Hear: a private conversation becomes a confirmed concern (Priority: P1)

A citizen opens Voice without giving a name or email, is told plainly that they are talking to an AI and that the conversation is private, and describes a concern in their own words. Voice asks non-leading clarifying questions, then shows a **proposal**: the citizen's original words (kept private), a proposed structured interpretation with the passages it came from and how confident it is, and suggestions of related public issues. The citizen edits or confirms the interpretation. Nothing leaves the private conversation. A returning citizen can pick up where they left off using a recovery credential issued at the start of the session.

**Why this priority**: Every other story depends on a confirmed concern existing, and this is where the product's core promise, *heard, not merely reassured*, is first kept or broken.

**Independent Test**: Start a session with no identity, hold a short conversation, receive a proposal, edit and confirm it, close and reopen the session with the recovery credential and see the same conversation and confirmed concern. Verify through the public issue search and representative views that nothing from the conversation is visible anywhere.

**Acceptance Scenarios** *(each names a scenario in a living feature; none is written here)*:

- added `features/participation/private-conversation.feature`: a citizen starts a conversation with no identity
- added `features/participation/private-conversation.feature`: a clarifying question proposes no position
- added `features/participation/private-conversation.feature`: a proposal shows what it came from and nothing is shared
- added `features/participation/private-conversation.feature`: a confirmed concern records the participant's edited meaning
- added `features/participation/private-conversation.feature`: a confirmed concern is not public
- added `features/participation/private-conversation.feature`: a participant returns with the recovery credential
- added `features/participation/private-conversation.feature`: a participant without the recovery credential starts afresh
- added `features/participation/private-conversation.feature`: instructions in a message are content

---

### User Story 2 - Share: the citizen approves exactly what is shared and with whom (Priority: P2)

A citizen with a confirmed concern chooses a **sharing mode** for it: keep it private, share a de-identified derivative, share identifying details with the authorised representative only, or share with public attribution. Voice shows the exact text that would be shared and the exact audience. The citizen can remove details before approving. An approved shared derivative goes to a reviewer, who checks it for privacy and re-identification risk before it enters the shared space. Once approved, it creates a new public Issue or joins an existing one. The private original never leaves the citizen's conversation.

**Why this priority**: This is the first point at which anything becomes visible to anyone other than the citizen. If the gate is wrong the product is harmful; if it is right, everything downstream can be trusted.

**Independent Test**: With a confirmed concern, choose each sharing mode in turn and observe what the reviewer, the representative and public search can see. Verify that a rejected derivative never appears, that an approved de-identified derivative appears on the Issue without the original text, and that the citizen can withdraw a disclosure.

**Acceptance Scenarios** *(each names a scenario in a living feature; none is written here)*:

- added `features/disclosure/exact-approval.feature`: four sharing modes are offered with private first
- added `features/disclosure/exact-approval.feature`: the participant approves only an edited derivative
- added `features/disclosure/exact-approval.feature`: a derivative awaiting review is seen by the reviewer alone
- added `features/disclosure/exact-approval.feature`: an approved derivative joins its issue
- added `features/disclosure/exact-approval.feature`: a rejected derivative is not published
- added `features/disclosure/exact-approval.feature`: a suggested issue is accepted or a new issue asked for
- added `features/disclosure/exact-approval.feature`: a withdrawn disclosure leaves its audience and the counts
- added `features/disclosure/exact-approval.feature`: public reading never returns a private field
- added `features/disclosure/exact-approval.feature`: a distinctive derivative needs enhanced review

---

### User Story 3 - Understand: explore source-backed evidence and uncertainty (Priority: P3)

A citizen or representative opens a public Issue and sees the questions it raises, the evidence for and against, where each piece of evidence comes from, how confident the record is and what remains unknown. Asking "why is this claim supported?" returns the recorded claim, its source and the relevant passage, and points out qualifying or contradictory evidence. When the record does not support an answer, Voice says so rather than inventing one. Anyone can search approved public Issues and sources without following anything.

**Why this priority**: Evidence is what turns a collection of concerns into something a representative can act on honestly, and provenance is the product's defence against plausible but false prose.

**Independent Test**: Seed one Issue with a curated evidence pack that includes at least one pair of conflicting sources. Ask questions whose answers are, and are not, supported by the pack. Verify every cited passage exists in the named source, that contradictions are surfaced, that unsupported questions get an abstention, and that search works with no session or subscription.

**Acceptance Scenarios** *(each names a scenario in a living feature; none is written here)*:

- added `features/evidence/source-backed.feature`: a claim is explained from its records
- added `features/evidence/source-backed.feature`: contradicting evidence is shown beside a claim
- added `features/evidence/source-backed.feature`: an unsupported question gets an abstention
- added `features/evidence/source-backed.feature`: a reader inspects an issue without following it
- added `features/evidence/source-backed.feature`: a search result carries its record and its date
- added `features/evidence/source-backed.feature`: candidate evidence waits for the reviewer
- added `features/evidence/source-backed.feature`: instructions in a document are content
- added `features/evidence/source-backed.feature`: a correction supersedes evidence and flags its claims

---

### User Story 4 - Deliberate: express priorities and trade-offs as opinions, not evidence (Priority: P4)

A citizen looking at an Issue can say how important it is to them, consider the described policy options and state what trade-offs they would accept, with an optional narrative. These are recorded as statements by a person and shown as such, never mixed with evidence or presented as facts about the world. The Issue page and the representative's view show participant-derived aggregates with their denominator, time window, self-selection caveat and small-cell suppression, and never as a claim about the whole electorate.

**Why this priority**: Legitimate policy choices depend on values as well as facts. Keeping the two apart is a product principle and the thing a casual design would get wrong.

**Independent Test**: Have several synthetic participants express priorities and trade-offs on one Issue. Verify the aggregates are labelled by participant count, method and window, that no individual ranking is public, that a small group's responses are suppressed, and that no priority ever appears in the evidence or claim views.

**Acceptance Scenarios** *(each names a scenario in a living feature; none is written here)*:

- added `features/deliberation/opinions-not-evidence.feature`: a priority is an opinion and never evidence
- added `features/deliberation/opinions-not-evidence.feature`: an option is described without endorsement
- added `features/deliberation/opinions-not-evidence.feature`: an aggregate names its sample and makes no population claim
- added `features/deliberation/opinions-not-evidence.feature`: a small cell is suppressed
- added `features/deliberation/opinions-not-evidence.feature`: a priority is amended or withdrawn
- added `features/deliberation/opinions-not-evidence.feature`: no individual is ranked and no profile is inferred
- added `features/deliberation/opinions-not-evidence.feature`: the concerns dashboard labels participant-derived measures

---

### User Story 5 - Act: a representative investigates and records an accountable action (Priority: P5)

A representative opens a work queue of Issues, pending reviews, open investigations and overdue follow-ups. On an Issue they see the concerns they are authorised to see, the priorities and trade-offs, evidence and gaps. They can ask a research agent for suggested sources or questions; suggestions are labelled as suggestions. They open an investigation, choose tasks and record an official action with its authority, owner, date, rationale, state and a public summary. Only an authorised representative can record an official action, and a proposed action is never shown as performed. The Issue's chronology shows machine processing, moderator approval, staff acknowledgement, investigation, action and outcome as distinct states.

**Why this priority**: This is where participation produces a consequence. Without it, the product is a listening exercise; with it, the feedback loop in Story 6 has something truthful to report.

**Independent Test**: As the representative, open an investigation on the seeded Issue, accept a research agent's suggestion, record an action and a decision. As a reviewer and as a citizen, attempt the same and be refused. Verify the chronology labels each entry's state and actor correctly and that the agent's suggestion is still labelled *suggested* until a representative approves it.

**Acceptance Scenarios** *(each names a scenario in a living feature; none is written here)*:

- added `features/action/human-authority.feature`: the work queue shows what waits and for how long
- added `features/action/human-authority.feature`: a representative sees concerns in the form each participant authorised
- added `features/action/human-authority.feature`: a suggested investigation stays suggested
- added `features/action/human-authority.feature`: an action record carries its authority and who recorded it
- added `features/action/human-authority.feature`: only a representative records an action
- added `features/action/human-authority.feature`: a proposed action is never shown as completed
- added `features/action/human-authority.feature`: the chronology labels every entry with one state
- added `features/action/human-authority.feature`: an outcome links to its action and overstates nothing

---

### User Story 6 - Return: consenting followers receive one truthful update (Priority: P6)

A citizen can choose to follow an Issue or investigation and, separately, choose how to be told about developments (in-app inbox by default). When an authoritative development happens, such as a representative recording an action or new evidence being accepted, Voice determines which Issues are affected, which followers are eligible, whether the development is material, and prepares a source-backed update explaining why it matters to what they follow. Each eligible follower receives exactly one update per development, with the state of the action accurately worded. Citizens who do not follow receive nothing, and following can be stopped at any time.

**Why this priority**: *Agency through feedback* is the half of the north star the citizen experiences; it is also where exaggeration ("your problem is solved") would do the most damage.

**Independent Test**: Alice follows Issue A; Bob does not. Record Action X on Issue A. Verify Alice's inbox holds exactly one update about X that names the action and its recorded source and does not overstate it, that Bob has none, that retrying the delivery produces no duplicate, and that after Alice unfollows a further action produces no update.

**Acceptance Scenarios** *(each names a scenario in a living feature; none is written here)*:

- added `features/feedback/one-truthful-update.feature`: following is an explicit choice with a default delivery
- added `features/feedback/one-truthful-update.feature`: only an eligible follower receives a development
- added `features/feedback/one-truthful-update.feature`: a development processed again sends no second update
- added `features/feedback/one-truthful-update.feature`: an immaterial development sends no update
- added `features/feedback/one-truthful-update.feature`: an update explains itself and matches the record
- added `features/feedback/one-truthful-update.feature`: a former follower receives nothing further
- added `features/feedback/one-truthful-update.feature`: a failed delivery is retried without a duplicate

---

### User Story 7 - Erase: a citizen withdraws and their personal data is shredded (Priority: P7)

A citizen asks for their personal data to be erased. Voice verifies the request proportionately, determines which records and fields are affected and which are lawfully retained, revokes access and notifications, makes the protected private fields permanently unreadable, removes or invalidates derived personal data in read models, indexes and caches, confirms that retained public Issue knowledge is genuinely anonymous, and issues an audit receipt that contains none of the erased content. The public Issue's non-personal history remains intact and the system's own records can still be rebuilt without the erased content.

**Why this priority**: The product promises *minimise, then protect*; this story proves the promise end to end rather than asserting it. It is last because it must be tested against everything the earlier stories create.

**Independent Test**: After running Stories 1 to 6 for two participants, erase one. Verify that participant's private fields cannot be read anywhere, including after a full rebuild of read models and after restoring from a snapshot; that the other participant's data is intact; that the public Issue still shows its evidence, actions and anonymous history; and that the audit receipt contains no erased content.

**Acceptance Scenarios** *(each names a scenario in a living feature; none is written here)*:

- added `features/erasure/field-erasure.feature`: an erasure request says what will go and what will stay
- added `features/erasure/field-erasure.feature`: protected fields are unreadable after erasure
- added `features/erasure/field-erasure.feature`: rebuilt views hold nothing erased
- added `features/erasure/field-erasure.feature`: the public issue keeps its anonymous history
- added `features/erasure/field-erasure.feature`: erasing one participant leaves another intact
- added `features/erasure/field-erasure.feature`: the receipt records the erasure and reproduces nothing erased
- added `features/erasure/field-erasure.feature`: an erased participant is notified of nothing further
- added `features/erasure/field-erasure.feature`: an erased field renders as unavailable
- added `features/erasure/field-erasure.feature`: a dormant participant is erased and the retained contribution says why
- added `features/erasure/field-erasure.feature`: a returning participant resets the dormancy clock

---

### Edge Cases

- A citizen confirms a concern, then keeps talking: messages are never edited; the confirmed concern stays bound to the message sequence it was confirmed against, and later messages do not silently change what was confirmed.
- A citizen approves sharing, then the reviewer has not yet acted when the citizen withdraws: the derivative never becomes visible, and the citizen sees it as withdrawn.
- A reviewer approves a derivative that the system had flagged for enhanced review: approval requires a recorded second step and reason; it cannot be a single click.
- Two approved derivatives from the same participant join the same Issue: participant counts show one participant and two contributions.
- The same concern is expressed by many participants in near-identical words: each confirmation is offered the same existing Issue and each contribution is linked to it one at a time at moderation; no contribution is rewritten or merged, and Voice proposes no cluster of its own.
- An action is recorded while an update about a previous action is still being prepared: each development yields exactly one update; neither is lost or merged into a misleading combination.
- A follower's interest is removed between the moment a development occurs and the moment its update would be delivered: no update is delivered.
- A representative tries to record an action with a date in the future or before the Issue existed: the record is refused with a reason.
- Evidence is accepted whose source later publishes a correction: the earlier evidence is marked superseded, not deleted; connected claims are flagged for review.
- A question is asked whose only supporting evidence has been marked superseded or rejected: Voice abstains.
- An erasure request arrives for a participant whose session cannot be verified (no recovery credential, no verified contact): the request is held for operator handling and the citizen is told what proof is needed; nothing is erased without verification.
- A projection or index lags behind the authoritative record when an update is being prepared: preparation waits for the record to be reflected or reads the authoritative record; it does not send an update based on stale data.
- Public aggregate counts would reveal a small group (for example only two participants in one school): the cell is suppressed.
- A dormant participant returns one day before the dormancy period ends: the session resets the dormancy clock and nothing is erased. One day after: the erasure has run, the recovery credential no longer resolves, and the participant starts afresh.
- A representative account is deprovisioned: actions it recorded remain attributed to it with its authority at the time; it can record nothing further.

## Requirements *(mandatory)*

### Functional Requirements

**Participation and identity**

- **FR-001**: Users MUST be able to start and continue a conversation pseudonymously with no name, email, account or identity verification.
- **FR-002**: System MUST issue a recovery credential at session start that lets a returning participant resume their conversations and confirmed concerns, and MUST make a session without it unreachable.
- **FR-003**: System MUST present a plain-language explanation of privacy and of the AI nature of the conversation before accepting a participant's first message, and MUST keep it reachable throughout.
- **FR-004**: System MUST support four roles (citizen, representative, reviewer, operator) with distinct permissions; representative, reviewer and operator accounts are provisioned by the operator for the pilot, and the operator role MUST NOT carry the right to read private conversation content.
- **FR-005**: System MUST record every authenticated access to restricted material (private originals, identifying derivatives, audit receipts) with actor, time and record.

**Conversation and concern extraction**

- **FR-006**: System MUST ask clarifying questions that do not propose a position, cause or culprit, and MUST let the participant decline any question.
- **FR-007**: System MUST propose a structured concern that shows the original statement, an extracted summary, the source passage for each extracted element, a confidence indication and suggested related public Issues.
- **FR-008**: System MUST NOT create a contribution, concern association or any shared record before the participant explicitly confirms the interpretation.
- **FR-009**: Participants MUST be able to edit the proposed interpretation before confirming, and the confirmed concern MUST record the participant's edited meaning and be attributed to the participant's confirmation.
- **FR-010**: System MUST treat all participant text, and every document proposed as evidence, as content, never as instructions (citizens upload no documents and submit no links in this feature); no such content may change approvals, publication, role permissions or trigger commands.

**Disclosure and publication**

- **FR-011**: System MUST offer exactly four sharing modes per contribution (private, de-identified, authorised representative only, public attribution), with private as the default, and MUST NOT infer any sharing permission from the act of conversing.
- **FR-012**: System MUST show the exact text and exact audience that would be shared before the participant approves, and MUST let the participant remove or edit details before approving.
- **FR-013**: System MUST keep the private original separate from the approved derivative; the derivative is the only text eligible for any shared space, and the original MUST never appear in any view other than the participant's own.
- **FR-014**: System MUST route every shared derivative to a reviewer for privacy and re-identification review before it becomes visible to its audience, and MUST show the participant the state *under moderation* meanwhile.
- **FR-015**: Reviewers MUST be able to approve or reject a derivative with a recorded reason; the participant MUST see the outcome and reason; nothing is published on rejection.
- **FR-016**: System MUST flag derivatives involving distinctive circumstances, health details, minors or very small groups for enhanced review and MUST require a recorded second step for their public approval.
- **FR-017**: System MUST suggest existing Issues an approved derivative may join, let the participant accept or ask for a new Issue, and record the association separately from the original so it can be reviewed and changed; it MUST NOT merge individual contributions into a claimed collective consensus automatically.
- **FR-018**: Participants MUST be able to withdraw a disclosure; a withdrawn derivative MUST leave its audience's view and the Issue's counts, and the Issue history MUST record the withdrawal without reproducing the text.
- **FR-019**: Public read interfaces MUST never return private originals, private fields, participant identifiers or contact details, in results, snippets, counts or error messages.

**Issues, evidence and exploration**

- **FR-020**: System MUST maintain public Issues that aggregate approved contributions, questions, evidence, policy options, investigations, actions, decisions, outcomes and a chronology.
- **FR-021**: System MUST record evidence with its source, version, publication date, scope, authority, relevant passage and limitations, and MUST keep candidate, accepted, rejected and superseded evidence distinguishable.
- **FR-022**: System MUST distinguish personal experience, claim, evidence, belief revision, preference or priority, and decision as different record types and MUST never present one as another.
- **FR-023**: When asked why a claim is supported, system MUST return the recorded claim, source, passage, date or version and eligibility, and MUST show qualifying or contradictory accepted evidence alongside.
- **FR-024**: System MUST abstain, stating that the record does not support an answer, when no accepted evidence supports one, and MUST NOT produce a citation for an unsupported answer.
- **FR-025**: Every factual statement in a public explanation or update MUST trace to a record and source that a reader can open.
- **FR-026**: System MUST provide keyword search over publicly approved Issues, source metadata and documents, usable without a session, following or any suggestion feed; each result MUST carry a record identifier, a version or date and a link to the source passage.
- **FR-027**: Access filtering MUST be applied before any retrieved material reaches generation of an answer, summary or update.
- **FR-028**: Acceptance of new evidence MUST trigger a review prompt for connected hypotheses and decisions, never an automatic reversal of any claim or decision.
- **FR-029**: Reasoning records (questions, hypotheses, sources, evidence, claims, belief revisions) MUST be referenced by stable typed identifiers from Voice's civic records, and civic concepts MUST NOT be added to the general reasoning vocabulary to support Voice.

**Deliberation**

- **FR-030**: Participants MUST be able to record a priority, a trade-off and option considerations with an optional narrative against an Issue, and MUST be able to amend or withdraw them.
- **FR-031**: Priorities, trade-offs and considerations MUST be stored and displayed as statements by a participant and MUST never appear among claims or evidence.
- **FR-032**: Policy options MUST be described without endorsement, with known costs, benefits and open questions.
- **FR-033**: Every displayed participant-derived aggregate MUST state participants, contributions, collection method, time window, deduplication basis and a representativeness caveat, and MUST make no claim about the proportion of all constituents.
- **FR-034**: System MUST suppress or merge aggregate cells below a configured threshold so that no individual or small group can be inferred.
- **FR-035**: System MUST NOT publish rankings of individuals and MUST NOT infer or store ideological, partisan or demographic profiles of participants.

**Investigation and action**

- **FR-036**: Representatives MUST have a work queue of new Issues, pending reviews, open investigations and investigation tasks awaiting an external party, each with state and age.
- **FR-037**: Representatives MUST see concerns only in the privacy-filtered form each participant authorised, and MUST NOT see private originals or concerns kept private.
- **FR-038**: A research agent MAY suggest sources, questions or investigations; every suggestion MUST be labelled *suggested* until an authorised representative approves it, and no suggestion may create an investigation, an action or any public statement by itself.
- **FR-039**: Only an authenticated representative role MAY open an investigation or record an action or decision; attempts by any other role MUST be refused and logged.
- **FR-040**: An action record MUST include authority, owner, date, rationale, state (proposed, in progress, completed) and public summary, and MUST show who authorised it and when.
- **FR-041**: A proposed action MUST never be displayed as completed; an action with no response MUST remain visible as unanswered. (the rule for all such wording is FR-061)
- **FR-042**: The Issue chronology MUST label each entry with exactly one of: submitted, AI-interpreted, under moderation, staff-acknowledged, under investigation, action recorded, outcome verified, with its actor.
- **FR-043**: An outcome record MUST link to the action it evaluates and state what was measured and its limitations.
- **FR-044**: Representatives MUST be able to publish a narrative update on an Issue; its text MUST be written or reviewed by a human before publication, MUST NOT imply human review occurred before it did, and once published is a development like any other. Record-derived developments need no such review (FR-047).

**Interests and notifications**

- **FR-045**: Following an Issue MUST require an explicit participant choice, and MUST never be created by viewing, searching or contributing.
- **FR-046**: Delivery preferences MUST be set separately from following, and participants MUST be able to stop following or change delivery at any time. In this feature the only delivery is immediate, to the in-app inbox; the preference is recorded so that digest and email can be added without changing what a follower already chose.
- **FR-047**: For each authoritative development (an action, decision or outcome recorded, evidence accepted, or a narrative update published), system MUST determine affected Issues, eligible followers, materiality and deduplication, then prepare an update whose wording is rendered deterministically from the record, with no model-written prose, and that says why it matters to the follower's interest. No human step stands between a record-derived development and its followers' updates.
- **FR-048**: Exactly one update per development MUST reach each eligible follower, under retries, repeated processing and interleaved developments; non-followers MUST receive nothing.
- **FR-049**: An update's wording about human review and action state MUST match the authoritative state when it was prepared, and MUST NOT claim the underlying problem is solved unless an outcome record says so. (see FR-061)
- **FR-050**: Update preparation that depends on derived data MUST wait for that data to reflect the development, or read the authoritative record, and MUST NOT send on stale data.
- **FR-051**: Delivery state (prepared, delivered, failed) MUST be recorded per update and retried within preferences without duplication.

**Erasure and data protection**

- **FR-052**: Participants MUST be able to request erasure from their session; system MUST tell them what will be erased, what is retained and why, and what remains public.
- **FR-053**: Requested erasures MUST be verified proportionately; a request that cannot be verified MUST be held for operator handling, and nothing a participant requested MUST be erased without verification. A dormancy erasure (FR-063) has no requester and needs no verification.
- **FR-054**: On completion, every protected personal field of the participant MUST be permanently unreadable, including on replay of historical records and on restore from a snapshot, with a key scope that erases one participant without affecting any other.
- **FR-055**: Derived personal data (read models, search indexes, caches, inbox, interests, traces and logs holding personal fields) MUST be removed or invalidated, and a rebuild of derived data MUST NOT recreate erased content.
- **FR-056**: Any display of an erased field MUST render a *redacted or unavailable* marker; no record recovery, rebuild or display may fail because a field is unreadable.
- **FR-057**: Retained public Issue knowledge MUST be checked for identifying residue, including counts and distinctive combinations, and MUST remain reconstructable after erasure.
- **FR-058**: An erasure MUST end with a restricted audit receipt recording classes of data erased, time, authorising actor and verification, containing none of the erased content.
- **FR-059**: Protected fields (free text, contact details, linkability-sensitive fields and sensitive derived summaries) MUST be identified before any record is created, and the classification MUST carry through snapshots and derived data.
- **FR-060**: Record envelopes, logs and partition keys MUST NOT carry raw prompts, personal free text, contact details or special-category data.
- **FR-063**: System MUST erase a participant whose last session ended more than a configurable dormancy period ago (default 180 days), through the erasure workflow, as if they had requested it, with no verification step.
- **FR-064**: Every erasure MUST record its cause, `requested` or `dormancy`, and every retained de-identified contribution whose author was erased MUST carry that cause where the contribution is shown and counted, so that the retained knowledge can be weighted knowing whether its author withdrew, abandoned participation or is still present. The cause is a category, never an identity.

**Auditability and honesty**

- **FR-061**: Every human-facing statement about a contribution's or Issue's progress MUST use the states in FR-042 and MUST never imply human consideration or action that is not recorded.
- **FR-062**: All state transitions MUST be journalled with actor, time, causation and correlation so that the Issue history and any participant's history can be reconstructed and audited.

### Key Entities

- **Participant**: pseudonymous participant key, optional verified contact, permissions and recovery credential. Private personal data. Never placed in a globally readable graph.
- **Conversation**: one participant's session and message history. Private; protected fields.
- **Concern**: a participant's confirmed interpretation of their own concern; a statement experienced or valued by one person, not a statistical truth. Private until a derivative is approved.
- **Contribution**: an explicitly confirmed, share-authorised statement derived from a conversation, with its sharing mode, review state and approved derivative text. Private original and approved derivative are separate.
- **DisclosureGrant**: the participant's choice of scope, audience and purpose for one contribution, with the evidence of consent; withdrawable.
- **Issue**: public topic aggregating approved contributions, questions, evidence, policy options, investigations, actions, decisions, outcomes and a chronology. Public, minimised.
- **IssueLink**: versioned, reviewable association between a contribution, an Issue and a reasoning record. Access-controlled, as the link itself may identify.
- **PolicyOption**: a described possible intervention with costs, benefits and open questions; no endorsement.
- **PriorityExpression / TradeOffExpression / Consideration**: a participant's normative statements about an Issue or option. Sensitive personal data; shown only in labelled aggregates.
- **Deliberation**: the structured record of options, grounds and disagreements on an Issue. Publication reviewed.
- **Evidence, Claim, Question, Source, BeliefRevision**: reasoning records referenced by stable typed identifiers; evidence has source, version, date, scope, authority, passage, limitations and a state (candidate, accepted, rejected, superseded).
- **Investigation**: a persistent question with work items, evidence requests, owner and review state; opened only by a representative.
- **Action**: a representative's recorded step with authority, owner, date, rationale, state and public summary. **Decision**: a chosen approach with authority, alternatives and justification. **Outcome**: an observed effect linked to a prior action, with measurement and limitations.
- **Interest**: a participant's explicit subscription to an Issue or investigation, with delivery preferences. Private personal data.
- **Notification**: one recipient's update about one development, with why it was selected and its delivery state. Private; protected.
- **ErasureRequest**: a data-rights request or a dormancy erasure, with its cause (`requested` or `dormancy`), verification where requested, scope determination, completion and audit receipt. Restricted.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A first-time participant with no account completes the path from opening Voice to a confirmed concern in under ten minutes of conversation, in facilitated usability sessions with the pilot cohort.
- **SC-002**: Across every automated scenario in this specification, zero private originals, private fields or participant identifiers appear in any public or representative view, search result, snippet, count or error message.
- **SC-003**: Zero contributions become visible to any audience without both the participant's explicit approval of the exact text and a recorded reviewer decision.
- **SC-004**: One hundred percent of cited passages in answers and updates exist verbatim in the named source version, measured on the curated evidence pack, and one hundred percent of questions in the unsupported set receive an abstention rather than a citation.
- **SC-005**: One hundred percent of participant-derived aggregates shown anywhere carry participant count, method, window and representativeness caveat, and zero aggregates expose a cell below the suppression threshold.
- **SC-006**: Zero official actions or investigations are recorded by any account other than a representative, across role-based access tests for every role.
- **SC-007**: In the accountable-feedback scenario, the follower receives exactly one update per development and the non-follower receives zero, under at least one forced retry and one interleaved development.
- **SC-008**: After erasure, zero erased fields are readable in the historical record, in rebuilt derived data or after snapshot restore, while every other participant's data and the public Issue's evidence, actions and anonymous history remain intact.
- **SC-009**: After reading a notification and the Issue page, usability-test participants can correctly state what they shared, what has happened, what remains unresolved and how to change their participation; the pilot gate is that every participant in the cohort does so without facilitator help.
- **SC-010**: The representative user in usability tests identifies the Issue's open questions and records an action without reading any raw conversation.
- **SC-011**: All acceptance scenarios in this specification, plus the derived-data rebuild test, run automatically and pass in the project's standard build.
- **SC-012**: Extraction accuracy and participant correction rate are measured on a labelled evaluation set before any threshold is set; no accuracy target is a gate for this feature.

## Assumptions

- **Pilot shape**: one local Issue (unreliable school transport), a curated evidence pack of about five to fifteen openly licensed sources containing at least one conflict, three to eight synthetic participants, one simulated representative, one reviewer and one operator. No real members of the public and no real representative take part in this feature.
- **Audience**: UK civic context; English language only; conversational text only. Audio is a later direction, not a requirement of this feature.
- **Session recovery**: the recovery credential is a code issued at session start; no email or account is required. A session without it is unrecoverable by design.
- **Review**: every shared derivative is reviewed by a human reviewer before it is visible; automated privacy checks assist the reviewer and flag enhanced-review cases but do not approve on their own.
- **Role provisioning**: representative, reviewer and operator accounts are pre-provisioned by the operator for the pilot; there is no self-service sign-up for those roles.
- **Delivery channels**: the in-app inbox, delivered immediately, is the only delivery in this feature; digest, email and push are later directions.
- **Reasoning records**: questions, sources, evidence, claims and belief revisions are supplied by the family's existing reasoning service and referenced by identifier; this feature does not rebuild them and does not depend on the reasoning service's planned agent-writing or process layers.
- **Erasure verification**: a request made from within an active, credential-recovered session is treated as verified; any other request is held for the operator.
- **Dormancy**: the dormancy period is 180 days since the participant's last session, configurable; a dormancy erasure needs no verification because no one is asking.
- **Suppression threshold**: aggregate cells with fewer than five participants are suppressed; the value is configurable and the DPIA may change it.
- **Legal position**: this feature exercises the privacy controls end to end but is not a compliance certificate; a DPIA, lawful-basis documentation and legal review precede any real-user pilot and are outside this feature.
- **Interfaces**: this feature delivers minimal citizen and representative interfaces covering every step in the definition of done; polish, accessibility beyond the smoke tests in SC-009 and the reviewer and operator consoles beyond what Stories 2 and 7 need are later work.
- **Model**: concern extraction and the research agent run against a live model in the demonstrator; automated tests run against recorded model behaviour so they are deterministic. Provider data-processing terms and retention settings are confirmed before any real participant text is sent.
- **Scaffold**: the service project, build and local environment (slice S0) already exist in this repository; glossary, Gherkin features and seed data are produced as part of this feature's planning and implementation.

## Clarifications

### Session 2026-10-18

- Q: Does this feature include the citizen and representative web interfaces described in section 8 of the source document, or only the service and its programmatic interface? → A: It includes minimal interfaces for both roles, because the definition of done requires a person to perform every step.
- Q: Must the AI concern extraction (Story 1, FR-007) and the research agent (Story 5, FR-038) run against a live model in the demonstrator, or may it run on scripted model behaviour? → A: A live model, with a recorded test double for automated tests.
- Q: Does a recorded action's update to followers wait for a human to approve its text, or go automatically? → A: Record-derived developments (action, decision, outcome, accepted evidence) notify followers automatically with wording rendered deterministically from the record, with no model-written prose; human review applies only to narrative updates a representative writes, which then become developments of their own.
- Q: Is digest delivery built in this feature? → A: No. Immediate in-app delivery only; the delivery preference exists in the data with one value, and digest is out of scope for this feature.
- Q: What happens to a pseudonymous participant who never returns? → A: Automatic erasure after a configurable dormancy period, default 180 days since the last session, through the same erasure workflow. The erasure records its cause (requested by the participant, or dormancy), and every retained de-identified contribution carries that cause, so the anonymous knowledge kept can be weighted later knowing its author abandoned participation rather than withdrew or stayed.
- Q: Does Voice proactively cluster similar concerns across participants and propose merges to staff? → A: No, not in this feature. Voice suggests existing Issues only when a citizen confirms a concern, and the reviewer chooses the Issue at moderation. Clustering of existing contributions across participants, with or without a merge operation, is a later feature.
- Q: Are the coined terms proposal, development, interest, disclosure, receipt and de-identified the domain's words? → A: Yes, all six are accepted as written; they are no longer proposed in the glossary.

## Out of Scope

From section 9.3 of the source document, this feature does not include:

- Live nationwide deployment or automatic import of every representative or constituency.
- Verifying constituency residency or guaranteeing one person, one response beyond the pilot's controls.
- Representative polling claims, population weighting or demographic inference.
- Political campaign messaging, voting recommendations or persuasion optimisation.
- Any automated official decision or any message sent in a representative's name by the system.
- Unmoderated publication of original chats or unverified machine-generated claims.
- An agent-driven reasoning process layer beyond what the reasoning service already provides.
- Continuous large-scale ingestion, graph recommendation systems, live broadcast notifications or mobile apps.
- A voice or audio interface.
- Digest delivery of updates; email or push delivery.
- Proactive clustering of existing contributions across participants, and merging Issues; only the per-confirmation suggestion of existing Issues is in scope.
- Uploading documents or submitting links in the citizen conversation; documents enter only as evidence proposed by a reviewer.
- Following an individual investigation; a follower follows the Issue it belongs to.

# Ankka Voice — Product and MVP Specification

**Status:** Working implementation specification (v0.1)  
**Date:** 18 October 2026  
**Scope:** UK civic participation and representative decision support; first release is a controlled demonstrator.  
**Repositories:** [Ankka](https://github.com/thinkmorestupidless/ankka) · [Ankka Reasoning](https://github.com/thinkmorestupidless/ankka-reasoning) · [Voice](https://makethemlisten.uk/)

> **North star:** Every person can be heard, explore the evidence, participate in deliberation, and see what happened because they contributed. Representatives can understand concerns, investigate evidence, weigh trade-offs, take action, and explain decisions. Voice is proactive to both sides without replacing their ability to explore for themselves.

## 1. Executive summary

Ankka Voice is a persistent, privacy-conscious civic reasoning and participation system connecting citizens, their elected representatives, real-world evidence, public deliberation, and accountable action. It is **not** simply a political chatbot, public opinion poll, petition website or MP casework CRM. Conversation is the accessible interface to an evolving set of durable, inspectable domain records and processes.

The initial MVP will demonstrate one complete loop:

1. A citizen talks naturally about a concern in a **private-by-default conversation**.
2. The system proposes a structured interpretation; the citizen edits/confirms it and chooses whether/how to share.
3. An approved contribution creates or joins a **shared Issue**; personally identifying text is not published by default.
4. Citizens explore evidence, uncertainties and options; they can express **priorities and trade-offs** separately from factual claims.
5. The system proactively detects a relevant change (new contribution, data/evidence, recorded action) and prepares an auditable update.
6. A representative sees concerns with appropriate sampling caveats, opens an investigation, and records an action and rationale.
7. Interested citizens receive **permissioned, accurate progress notifications**, and can independently inspect the public issue history and continue the discussion.
8. A citizen can withdraw from participation and request erasure of applicable personal data while genuinely anonymised civic knowledge can remain when lawful.

**MVP success means demonstrating a real chain from voice → contribution → issue → investigation → action → feedback**, with transparent states, verifiable source references and real privacy controls. It does not require a live MP, large population, or broad parliamentary integration.

## 2. Mission, stakeholders and product promises

### 2.1 Mission

Make democratic decision-making more responsive, evidence-aware, inspectable and accountable, while recognising that legitimate policy choices depend on values and competing priorities as well as facts.

### 2.2 Users

- **Citizen / participant:** may remain pseudonymous; can converse, explore, contribute, deliberate, follow outcomes, and control sharing.
- **Representative / authorised staff:** examine issue summaries and underlying contributions they are authorised to access, investigate, request further information, record decisions and communicate updates.
- **Reviewer / moderator:** approves publication, checks evidence quality, resolves high-risk anonymisation and misuse cases.
- **Operator / data protection administrator:** configures governance, retention, lawful bases, access controls and data subject request processing; not automatically entitled to read all private content.

### 2.3 Non-negotiable product principles

1. **Heard, not merely reassured:** acknowledge receipt accurately; never imply human consideration or action unless recorded.
2. **Agency through feedback:** participants can track the consequences of their contributions and respond to developments.
3. **Explore as well as receive:** proactive discovery and updates coexist with user-initiated searches and investigations.
4. **Truth ≠ preference:** evidence, factual claims, estimates, personal experiences, values and political decisions are different types.
5. **Represent disagreement:** contradictory evidence, uncertainty and competing values are inspectable.
6. **Provenance over plausible prose:** every factual public explanation traces to actual records and sources.
7. **Private by default:** sharing is granular, affirmative and reversible as applicable.
8. **Non-partisan by design:** no political persuasion profiling, automated endorsement, or opaque manipulation of priorities.
9. **Human authority:** agents may investigate and propose; authorised humans decide consequential publication and political actions.
10. **Honest signal quality:** participant activity is not synonymous with representative public opinion or issue importance.
11. **Durable, reconstructable state:** journaled lifecycle and traceability; projections can be rebuilt.
12. **Minimise, then protect:** do not store personal data merely because shredding is possible.

## 3. Product behaviours

### 3.1 Three interaction modes

| Mode | Citizen | Representative |
|---|---|---|
| Proactive | Updates on chosen interests, new evidence and progress on contributions | Emerging issues, evidence changes, unanswered questions, overdue reviews |
| Exploratory | Ask open questions, search issues/sources, compare policy options | Investigate concerns, inspect datasets, audit conclusions and historic actions |
| Collaborative | Maintain a concern, contribute evidence, deliberate and amend priorities | Run ongoing investigations, request research, record actions and seek responses |

### 3.2 Citizen journey

1. Start a pseudonymous private session; do not require real name or email for exploratory use.
2. Talk in natural language; Voice asks clarifying **non-leading** questions.
3. AI extracts candidate concerns and issue relationships with confidence and source spans; the participant reviews and confirms.
4. Show sharing modes *per contribution*: **private**, **share de-identified**, **share identifying details with authorised representative**, **public attribution**.
5. If shared, run privacy and moderation checks before publication or staff access; show the exact proposed shareable text and audience.
6. Offer contextual evidence, counter-evidence, options and open questions; cite original source passages.
7. Optionally capture salience, relative priorities, trade-offs and preferences; these are **statements by a person**, not evidence of external truth.
8. Let participant explicitly follow the issue/investigation (in-app inbox first, email optional).
9. Show a timeline distinguishing machine processing, moderator approval, staff acknowledgement, investigation, formal action and observable outcome.
10. Notify on meaningful verified developments with explanation of why it matters to their followed issue.
11. Allow continued conversation, contribution withdrawal where applicable and data-rights workflows.

### 3.3 Representative journey

1. Open a dashboard of issues, recent changes, unresolved investigations and pending actions.
2. See **counts of participating users/contributions**, collection method and known biases, not assertions about the whole electorate.
3. Examine issue's original concerns in a suitably privacy-filtered form, priorities, trade-offs, contrary perspectives, evidence and gaps.
4. Ask a research agent to suggest sources/questions; see evidence eligibility and unresolved uncertainty.
5. Authorise creation of investigation tasks and record official actions, deadlines, owner and supporting rationale.
6. Publish a verified update to interested participants. Silence/non-response remains visible as such.
7. Review outcomes and revisit earlier claims or commitments when evidence changes.

### 3.4 Proactive system behaviours

- Discover clusters of similarly expressed concerns, without creating a politically charged or demographic persuasion profile.
- Suggest new or related issues to a **human reviewer**, rather than silently rewriting citizens' contributions.
- Identify new evidence potentially bearing on a recorded question/claim/issue.
- Open a **draft research task** or review proposal; do not automatically assert conclusions or issue official correspondence.
- Detect authoritative state changes and identify relevant subscribed interests.
- Notify only on material developments, within consent/preferences, with deduplication and an explicit opt-out.
- Offer configurable digest versus immediate delivery; permit searches independent of personal feeds.

## 4. Boundaries between Ankka, Reasoning and Voice

### 4.1 Reuse, do not rebuild

**Ankka** is the runtime and infrastructure: distributed stateful entities, event journals, workflows, agents, endpoints, security, and projections. **Per the project owner, Ankka already supports field-level crypto-shredding**: individual event fields may be marked sheddable and their encryption keys dropped without rewriting the journal. Integration details and API contracts must be checked against the installed version before coding.

**Ankka Reasoning** supplies the general-purpose epistemic model: `Question`, `Hypothesis`, `Holder`, `Source`, `Evidence`, `Claim`, `BeliefRevision`, immutable revision history, explanations and graph consumers. Its README describes the belief and market layers as implemented; agent-writing and the process layer are planned, **not assumed present**. The Neo4j graph is a rebuildable, eventually consistent projection of accepted records. Reasoning's belief probability is *not* a generic representation of preferences or political choices.

**Voice** owns the civic vocabulary and workflows: participants, conversations, consent, contributions, concerns, issues, interests, deliberation, preferences, investigations, representative actions, outcomes and notifications.

### 4.2 Dependency direction

```text
Citizen UI / Representative UI
           |
         Voice APIs
           |
 +---------+------------+----------------+
 | Participation        | Civic Issues   | Accountability / Updates
 | private identity     | deliberation   | investigations/actions
 +---------+------------+----------------+
           |                              
     Ankka Reasoning API / graph reads
           |
   Ankka entities, workflows, agents, event journal,
   graph consumers, storage, security, field shredding
```

Voice references reasoning records by stable typed identifiers. Civic-specific concepts must not be added to the general reasoning kernel merely to support Voice. Keep private participant lookup and contact paths **outside** the public graph.

### 4.3 Authoritative state versus read models

- Domain commands are validated by authoritative Ankka entities/workflows.
- Events are the durable record of accepted transitions.
- Neo4j/relational/search projections serve querying and may lag journal writes.
- Never use a graph projection as the transactional authority for a command.
- Consumers use stable event IDs, replay-safe upserts, revision/version identifiers and checkpoints.
- When a workflow needs graph data after an event, await a projection checkpoint or use an authoritative read; handle projection lag explicitly.

## 5. Domain model (proposed)

### 5.1 Civic aggregates and records

| Type | Purpose | Privacy classification |
|---|---|---|
| `Participant` | Pseudonymous participant key, optional verified contact and permissions | Private personal data |
| `Conversation` | Interaction session and participant-specific message history | Private; field-sheddable |
| `Contribution` | Explicitly confirmed/share-authorised statement from conversation | Private until approved; selected fields sheddable |
| `Concern` | A confirmed issue experienced or valued by a participant; not an assertion of statistical truth | Private or approved derivative |
| `Issue` | Public topic aggregating concerns, questions, investigations, evidence and actions | Public/minimised |
| `IssueLink` | Versioned association between contribution/issue/reasoning record | Access-controlled; link may be identifying |
| `Interest` | Explicit subscription to an issue, question or investigation | Private personal data |
| `PriorityExpression` | Individual relative salience of concerns | Sensitive personal data |
| `TradeOffExpression` | Stated willingness to accept competing costs/benefits | Sensitive personal data |
| `Deliberation` | Structured discussion of options, grounds and disagreements | Mixed; publication reviewed |
| `PolicyOption` | Described possible intervention, no implied endorsement | Public |
| `Investigation` | Persistent question, work items, evidence requests and review state | Mixed |
| `Action` | Authorised representative's recorded step and rationale | Public or redacted |
| `Decision` | Chosen approach, authority, alternatives and underlying justification | Public or redacted |
| `Outcome` | Observed effect, measurement, limitations and link to prior action | Public or redacted |
| `Notification` | Recipient-specific generated update, why selected, delivery state | Private; sheddable |
| `DisclosureGrant` | Scope/purpose/audience and consent evidence or other relevant authority | Private; retain as legally appropriate |
| `ErasureRequest` | Data-rights processing workflow, audit-safe completion record | Restricted personal data |

**Keep a contribution's private original separate from its approved derivative.** A derivative is not automatically anonymised; do a disclosure/re-identification review, especially when circumstances are distinctive or sample sizes small.

### 5.2 Reasoning concepts

Re-use `ankka-reasoning` for questions, hypotheses, sources, evidence, claims, holder-specific belief revisions and their provenance. Map a public issue to relevant reasoning questions and source-backed claims through typed links. Preserve the difference between:

- `PersonalExperience`: a citizen's first-hand reported experience, not externally verified.
- `Claim`: proposition whose evidential support can be inspected.
- `Evidence`: source-anchored information with provenance and eligibility.
- `BeliefRevision`: dated judgement by a specified holder about a hypothesis.
- `Preference`/`Priority`: normative input from a participant; not a probability or fact.
- `Decision`: action by an authorised actor; neither a claim nor necessarily a belief.

### 5.3 High-level relationships

```text
Participant --owns--> Conversation --proposes--> Contribution
Participant --follows--> Interest --targets--> Issue / Investigation
Contribution --associated-with--> Issue --has--> Investigation
Issue --concerns--> Reasoning.Question / Claim / Evidence
Issue --offers--> PolicyOption --has--> TradeOff
Investigation --produces--> Action / Decision --evaluated-by--> Outcome
Event --affects--> Issue --matches--> Interest --produces--> Notification
```

**Access rule:** these are logical relationships, not a licence to put `Participant`, private links or contact data in a globally readable graph.

## 6. Event and workflow contracts

### 6.1 Event envelope

Every event should carry an opaque `eventId`, `aggregateId`, `aggregateVersion`, `eventType`, schema version, time recorded, causation/correlation IDs, and permitted actor/service identity. Avoid raw prompts, personal free text, email addresses and special-category data in envelopes, logs or topic partition keys. Sensitive event fields use Ankka's sheddable field mechanism. Do not assume even an opaque stable participant ID is anonymous.

### 6.2 Core events (names illustrative)

```text
ConversationStarted
ConcernInterpretationProposed
ConcernInterpretationConfirmed
ContributionDisclosureAuthorised
ContributionSubmitted
ContributionPublicationApproved | ContributionPublicationRejected
IssueCreated | ContributionAssociatedWithIssue
InterestCreated | InterestUpdated | InterestRemoved
PriorityExpressed | TradeOffExpressed
EvidenceCandidateDiscovered | EvidenceAccepted | EvidenceRejected
IssueReviewSuggested | InvestigationOpened | InvestigationUpdated
RepresentativeActionRecorded | DecisionRecorded | OutcomeRecorded
IssueDevelopmentPublished
NotificationPrepared | NotificationDelivered | NotificationFailed
DisclosureWithdrawn | ErasureRequested | DataShredded | ErasureCompleted
```

### 6.3 Workflows and guards

**Conversation → contribution:** AI proposes typed extraction with spans; deterministic validation checks schema and allowable transitions; citizen confirms exact meaning and disclosure; moderation/privacy gateway decides whether approved derivative may enter shared space.

**Contribution → issue:** search suggests an existing Issue; duplicate/link decisions are reviewable; store the association separately from the original message; no autonomous merge of individual opinions into a supposed collective consensus.

**Evidence → review:** identify source, version, publication date, scope, authority, relevant passage and limitations; AI extracts candidate claim; verify cited passage and qualifications; then accept/reject; trigger review of connected hypotheses/decisions, not automatic reversal.

**Issue → representative action:** representative or delegated staff opens investigation, chooses tasks and records action, authority, date and status. Proposed actions must not appear as completed actions.

**Event → update:** authoritative event → affected issue IDs → interest eligibility → materiality/deduplication → source-backed summary → notification send → logged delivery status. A projection barrier/retry is necessary when graph traversal depends on newly emitted edges.

**Erasure:** authenticate/request verification as proportionate → determine affected fields/data and exemptions → revoke access/notifications → shred subject-scoped/field-scoped keys where applicable → purge or invalidate derived personal projections/cache/indexes and assess backups → confirm retained truly anonymous/public records → issue restricted audit receipt with no erased content.

## 7. Privacy, security and governance

### 7.1 Baseline privacy rules

- Private-by-default conversation; no automatic public insertion.
- Anonymous **to the public** is not the same as anonymous **to the operator**; pseudonymous IDs remain personal data where reasonably linkable.
- No identity verification as a blanket prerequisite to learning/exploration; optional verification for constituency-specific functionality may be assessed separately.
- Per-contribution approval of **exact shared text**, audience, and intended purpose; no blanket publishing permission inferred from chatting.
- Separate consent/permission for following issues and for email/push delivery. Withdrawal must be available.
- Political opinions may be special-category personal data under UK GDPR: document Article 6 lawful basis and Article 9 condition, controller/processor relationships, purpose and retention for each relevant operation.
- Conduct a DPIA before processing real users; perform a threat model covering inference, minority/community re-identification, abusive submissions, prompt injection, model providers and accidental disclosures.
- No electoral persuasion targeting or inferred ideological profiling. Personalisation limited to expressly chosen topics/interests and genuine contribution progress.
- Apply RBAC/ABAC to staff, citizens, reviewer and operator roles; record authenticated access to restricted material.
- Treat uploaded research, constituent documents and external pages as untrusted inputs, never executable instructions.

### 7.2 Field-level crypto-shredding contract

**Existing platform capability supplied by project owner:** Ankka can individually shred encrypted fields in journalled events by dropping the appropriate keys without editing event objects. This should be exercised end-to-end, not reinvented.

Design requirements:

1. Identify every sheddable field in event schemas before emitting events; propagate classifications through snapshots and projections.
2. Choose key scopes that let one participant's information be shredded without destroying unrelated civic knowledge; avoid a shared key for all participants in an Issue.
3. Protect free-text content, contact details and any linkability-sensitive fields, including sensitive derived AI summaries.
4. Ensure snapshot recovery and journal replay still function when a key is missing; render `redacted/unavailable` safely rather than failing aggregate recovery.
5. Scrub or invalidate copies in read models, caches, search indexes, vector stores, AI traces and logs; establish explicit backup/key lifecycle semantics.
6. Keep immutable non-personal event metadata only if it cannot reasonably re-identify the person by combination.
7. Retain approved issue-level knowledge **only if genuinely anonymised and lawful to retain**; crypto-shredding by itself is not proof of legal anonymisation or compliance.
8. Test erasure after notification, projection rebuild, snapshot restoration and retries.

### 7.3 Publication and data quality

- Every human-facing statement explicitly distinguishes *submitted*, *AI-interpreted*, *under moderation*, *staff-acknowledged*, *under investigation*, *action recorded*, *outcome verified*.
- Sensitive casework, identifying health stories, minors' information and very small group reporting default to non-public or enhanced review.
- Publicly displayed aggregate statistics must show denominator, time window, participant-selection method, multiple mentions, deduplication basis and representativeness limitations.
- Suppress/redact small cells and distinctive combinations; counts and graphs can leak identities even without names.
- Record who authorised official public actions and when. Preserve earlier versions as allowed while ensuring personal-data erasure protections.

## 8. User-facing interfaces (MVP)

### 8.1 Citizen interface

- Conversation view with plain-language explanation of privacy and AI nature; persistent context for returning pseudonymous participants.
- Proposed concern card: original statement (private), extracted summary, linked issue suggestions, edit/confirm controls.
- Contribution sharing review: private / de-identified / authorised representative / publicly attributed; exact text preview.
- Issue exploration: question, evidence and counter-evidence, provenance links, uncertainty, options and trade-offs, actions and chronology.
- Simple deliberation: personal importance/priority, option considerations, optional qualitative narrative; no public ranking of individuals.
- Follow/unfollow and notification preference settings; in-app inbox and update details with original records.
- Personal data access/withdrawal/erasure controls.

### 8.2 Representative interface

- Work queue: new issues, pending review, investigations, external follow-ups.
- Constituency concerns view with explicitly labelled **participant-derived** measures, not opinion-poll claims.
- Drill-down issue details: related submissions only where authorised, approved public narratives, reasoning source graph, unresolved questions and alternate views.
- Investigation/action form with authority, owner, timestamp, supporting rationale, state and public summary.
- Updates queue: review machine-drafted summaries; publish or reject; avoid implying human review until it occurs.

### 8.3 Search and open exploration

Provide keyword/text search over **publicly approved** issues, source metadata and documents; optionally semantic retrieval after baseline quality is established. Navigation must work without any follow/subscription or trending suggestion. Search results and answers should include citation/record IDs, version/date, eligibility/authority and links to source passages. Access filters apply **before** retrieval results reach generation.

## 9. Initial MVP scope

### 9.1 Deliberately narrow pilot

**Demonstration domain:** one local issue, such as unreliable school transport, using *synthetic participant submissions* and a small curated set of openly licenced official reports or operational data. Use a **simulated representative** in initial development; swap in an actual cooperating representative only after privacy/security, accuracy and moderation readiness.

**Pilot cohort:** approximately 3–8 test participants/roles, 1 representative role, 1 reviewer; the exact number is not a success metric. Use a curated evidence pack of roughly 5–15 sources. Any numeric counts displayed in sample UI are illustrative, never population claims.

### 9.2 In scope for MVP

- Pseudonymous session with optional account/recovery mechanism and in-app notifications.
- AI-assisted conversation and concern extraction with confirmation.
- Per-contribution disclosure and publication gate.
- One issue lifecycle, contribution linking, priority/trade-off collection.
- Reuse of Ankka Reasoning's implemented belief layer for evidence, claims, source links and graph explanations.
- Basic public issue search and sourced question-answering, with abstention when evidence is inadequate.
- Representative issue/investigation/action/decision workflow.
- At least one proactive new-evidence or action-driven update to a consenting participant.
- Field-level crypto-shredding exercised in an end-to-end erasure scenario.
- Auditable workflow transitions, observability and automated tests.

### 9.3 Explicitly out of scope

- Live nationwide deployment or automatic import of every MP/constituency.
- Verifying constituency residency or guaranteeing one-person-one-response beyond stated pilot controls.
- Representative polling claims, population weighting or demographic inference.
- Political campaign messaging, recommendations about who to vote for, persuasion optimisation.
- AI autonomously making official decisions or sending messages in an MP's name.
- Unmoderated publication of original citizen chats or unverified AI claims.
- Full agent-driven reasoning process layer if not yet implemented in `ankka-reasoning`.
- Massive continuous ingestion, complex graph recommendation systems, live broadcast notifications, mobile apps.
- Voice/audio interface in first slice: *Voice* is the product name, not an audio-delivery requirement. Conversational text first; audio can be added later.

## 10. Suggested implementation slices

Build incrementally; each slice should have running UI/API and tests before the next starts.

| Slice | Deliverable | Proof |
|---|---|---|
| S0 — Scaffold | `ankka-voice` project, glossary, Gherkin features, local Ankka + Reasoning environment, sample data | Reproducible one-command local run and CI |
| S1 — Hear | Private conversation, typed concern extraction, user confirmation, durable conversation record | No contribution created before confirmation |
| S2 — Share | Per-contribution permission, privacy review, approved derivative and shared Issue | Private originals never exposed in public API |
| S3 — Understand | Curated source ingestion, evidence/claim links via Reasoning, public issue search/explanations | Sources/citations can be reconstructed, weak claims abstain |
| S4 — Deliberate | Priorities, trade-off responses, issue history and clearly labelled participant aggregates | Opinions separated from evidence, self-selection disclosed |
| S5 — Act | Representative investigation/task/action lifecycle and chronology | Only authorised actor can record official action |
| S6 — Return | Interests, event-triggered relevant feedback, in-app inbox, retry/dedupe | Consentful participant gets one truthful update; unsubscribed does not |
| S7 — Erase & harden | Field shredding, projection cleanup, access controls, threat tests, DPIA review | No erased data recovered from replay/projections; retained public issue remains usable |

**Start with S0–S2**, then integrate reasoning and proactive workflows. Resist building a general news crawler or multi-agent research swarm before the core participation loop is proven.

## 11. Acceptance criteria (BDD seeds)

```gherkin
Feature: Private participation
  Scenario: A conversation does not publish anything without approval
    Given a citizen has a private conversation containing a concern
    When Voice proposes a structured concern
    Then no public contribution exists
    When the citizen confirms its interpretation but selects private
    Then no public contribution exists
    And public issue search does not expose the conversation

Feature: Confirmed disclosure
  Scenario: A participant can approve only an edited derivative
    Given a citizen has confirmed a concern
    When Voice proposes an identifying shareable summary
    And the citizen removes an identifying detail and approves de-identified sharing
    Then the original remains private
    And publication requires privacy validation
    And only the approved derivative is eligible for the shared Issue

Feature: Source-backed reasoning
  Scenario: A question exposes evidence and uncertainty
    Given an Issue links to a Question and accepted Evidence
    When a citizen asks why a Claim is supported
    Then Voice returns the recorded Claim, Source and relevant passage
    And identifies qualifying or contradictory evidence where available
    And does not fabricate a citation for an unsupported answer

Feature: Human authority
  Scenario: A research agent cannot impersonate a representative
    Given an agent suggests a new investigation
    When no authorised representative has approved it
    Then the investigation is labelled suggested
    And no official action is shown as performed

Feature: Accountable feedback
  Scenario: Only eligible followers receive a verified development
    Given Alice follows Issue A with in-app notifications enabled
    And Bob has not subscribed
    When an authorised representative records Action X on Issue A
    And the event and relevant projections are processed
    Then exactly one update about Action X is available to Alice
    And no personalised notification is delivered to Bob
    And the update identifies the action and its recorded source
    And it does not claim that the original problem is solved

Feature: Independent exploration
  Scenario: A citizen can inspect an issue without following it
    Given Issue A is publicly visible
    When a citizen searches for the Issue without subscribing
    Then the citizen can see public evidence, actions and limitations
    And no notification interest is silently created

Feature: Field-level erasure
  Scenario: Erasure preserves genuinely anonymous issue history
    Given a citizen shared an approved anonymised derivative
    And private fields were encrypted with sheddable keys
    When the citizen's valid erasure request is completed
    Then protected personal fields cannot be decrypted on journal replay
    And private projections and notification links are removed or invalidated
    And the remaining public issue contains no reasonably identifying residue
    And its non-personal history remains reconstructable

Feature: Honest aggregate reporting
  Scenario: Participant counts are not presented as population polls
    Given an Issue has submissions from a self-selected sample
    When the representative opens the concerns dashboard
    Then metrics are labelled by participant/sample and time window
    And no claim about the proportion of all constituents is made
```

### 11.1 Additional property and integration tests

- Idempotent event reprocessing and notification sends, including crash/retry between delivery and acknowledgement.
- Projection rebuild from journal, including records whose private fields have been shredded.
- Multi-contribution, multi-issue erasure: shredding one participant leaves others' content intact.
- Citation exact-match tests (claim/support passage, negation, dates, supersession and source authority).
- Prompt injection in uploaded document or chat must not bypass approvals or trigger unauthorised commands.
- RBAC/ABAC tests for private content, representative views, public API and operator access.
- Search/access filtering applied before generation; no leakage via snippets, embeddings or error messages.
- Human review states and notification copy must match authoritative action state.

## 12. Quality measures and go/no-go gates

**Core measurement:** whether participants can see an intelligible, verifiable progression from contribution to civic process and whether representative users can act on summaries without being misled.

| Area | Measurement / gate |
|---|---|
| Conversation extraction | Human-reviewed accuracy of concerns, qualifiers and salience; participant correction rate |
| Evidence | Citation validity and entailment on curated gold-standard examples; contradictions and missing source detection |
| Workflow | All authority/privacy constraints pass automated scenarios; no unapproved public publication |
| Feedback | Notification eligibility, completeness, idempotency, timeliness and truthful status wording |
| Accessibility | Keyboard navigation, readable plain English, inclusive wording and screen-reader smoke tests |
| Privacy | Shredding/replay/projection deletion scenarios pass; DPIA and documented processing basis before real-user pilot |
| Trust and agency | Usability test: users can explain what was shared, what happened, what remains unresolved, and how to change their participation |
| Representative utility | Staff can identify issue questions and record an action without parsing every raw conversation |

Do **not** declare a precision/recall target until a labelled evaluation set exists; set thresholds in S0/S3 after baseline measurement. No production launch purely on the strength of a compelling demo.

## 13. Risks and mitigations

| Risk | Mitigation |
|---|---|
| A chatbot gives false impression of political influence | Distinct states: machine received vs human reviewed vs official action; no invented commitments |
| Self-selection/bots distort perceived priorities | Eligibility controls in pilot, contextual counts, sampling disclosures, anti-abuse controls; no representativeness claims |
| Re-identification of personal stories | Human review of distinctive submissions, aggregation, suppression, privacy-safe derivatives |
| Crypto-shredding fails to erase secondary copies | Explicit lineage of personal fields, projection/caches/logs deletion and restoration tests |
| Evidence extraction drops qualifiers or misreads sources | Source-span validation, human review, contradiction fixtures, abstention |
| Graph lag causes missed updates | Checkpoints/retries, authoritative trigger records, idempotent delivery |
| Proactive feed becomes an engagement/manipulation engine | Explicit opt-in interests, neutral summaries, frequency controls, no persuasion targeting |
| Moderation cost overwhelms representatives | Group issues, queue reviews, bounded autonomous research, triage by transparent criteria |
| Political disagreement is turned into a fake empirical question | Separate normative expressions from empirical claims; show legitimate differing priorities |
| Public promises exceed actual institutional authority | Clear actor/mandate types, signed-off actions and transparent non-response |

## 14. Early engineering decisions to verify

Before implementation, inspect current Ankka SDK/docs and `ankka-reasoning` contracts for:

1. Actual syntax/semantics for per-field sheddable event fields and key scopes, including snapshots and journal recovery.
2. Reasoning service API for `Question`/`Source`/`Evidence`/`Claim`/`BeliefRevision` and its explanation reads.
3. Reliable integration of Voice domain entity refs with Reasoning record IDs without a distributed transaction; define saga/compensation or outbox approach.
4. Existing auth/session facilities and their fit for anonymous/pseudonymous users (Ankka platform identity may target developers/operators rather than public users).
5. Graph consumer and `ankka-flow` projection checkpoint capabilities and failure semantics.
6. Data-processing agreements, LLM provider retention settings and where chat/sensitive fields are sent.
7. Licensing and attribution obligations of seed sources; no indiscriminate copying of protected material.

Treat these as **verification tasks**, not assertions that the APIs already expose the proposed shape.

## 15. Proposed repository layout

```text
ankka-voice/
  README.md
  GLOSSARY.md
  docs/
    product.md
    architecture.md
    privacy-and-threat-model.md
    evidence-and-quality.md
  features/
    private-participation.feature
    contribution-sharing.feature
    issue-and-deliberation.feature
    evidence-and-reasoning.feature
    representative-action.feature
    proactive-feedback.feature
    erasure.feature
  modules/
    civic-model/             # types, validation, permissions
    participation/           # sessions, conversations, disclosures
    issues/                  # issues, concerns, links, deliberation
    investigations/          # work queues, actions, outcomes
    interests/               # subscriptions and delivery eligibility
    evidence-adapter/        # integration with ankka-reasoning
    notification/            # render, deliver, dedupe
    api/                     # citizen/representative endpoints
  ui/
    citizen/
    representative/
  seed/
    school-transport/
  deploy/
```

This is a suggested modular decomposition, not a mandatory microservice topology. For the MVP, prefer a small number of deployables and avoid unnecessary operational boundaries.

## 16. Initial implementation backlog

**P0 — foundation and thin vertical slice**

- [ ] Create repo, glossary, product principles, Gherkin features and reproducible local environment.
- [ ] Validate Ankka field-shredding usage with a small event + recovery + key destruction test.
- [ ] Define typed identifiers, scope, status enums and role/access policy.
- [ ] Implement private pseudonymous conversation session and storage.
- [ ] Implement AI-generated candidate concern with source span, confidence, typed validation and citizen confirmation.
- [ ] Implement contribution-specific sharing, edited preview and moderation/privacy approval.
- [ ] Create/search one shared Issue and attach approved derivative.

**P1 — useful deliberation and reasoning**

- [ ] Integrate Reasoning Question/Source/Evidence/Claim read/write APIs.
- [ ] Seed a small provenance-checked evidence pack with conflicting evidence.
- [ ] Expose source-backed issue exploration and explicit uncertainty.
- [ ] Capture stated priorities, options and trade-offs separately from claims.
- [ ] Show limited participant statistics with sampling warnings.

**P2 — agency and proactive feedback**

- [ ] Implement investigations, tasks, authorised actions and outcomes.
- [ ] Create Issue/Investigation interest subscription and in-app notification store.
- [ ] Consume an authoritative issue/action/evidence event, resolve eligible followers and deliver a grounded update.
- [ ] Render inspectable issue timeline and contribution progress; don't overstate resolution.
- [ ] Add user-driven search across approved public issues and sources.

**P3 — hardening before genuine users**

- [ ] Verify shredding across journal, snapshots, graph/read projections, notifications and backups.
- [ ] Define data retention and deletion matrix; complete DPIA and legal review.
- [ ] Add prompt-injection, leakage, tampering and abuse tests.
- [ ] Run facilitated usability tests for citizen agency and representative utility.

## 17. Definition of done: first working demonstration

The demonstrator is complete only when someone can:

1. Open Voice pseudonymously and speak in their own words about a concern.
2. Confirm/correct what Voice understood and choose exactly what may be shared.
3. See their approved contribution associated with an issue without exposing private chat content.
4. Navigate public evidence and an opposing interpretation with clickable source references.
5. Express a priority and a trade-off as an opinion, not as evidence.
6. Have an authorised representative record a specific, dated, accountable action.
7. Receive a proactive **truthful** update because they followed the issue, without needing to search.
8. Independently search the same issue and reconstruct the underlying action and evidence.
9. Exercise an erasure workflow that shreds configured private journal fields and removes derived personal data while leaving legally retainable, genuinely anonymised issue knowledge intact.
10. Run all key BDD scenarios and the projection rebuild test successfully.

## 18. Post-MVP directions (not commitments)

- Automated ingestion of official datasets, Parliament APIs, research feeds and selected public consultations.
- Persistent research goals with scheduled re-evaluation of changing evidence.
- Deliberation features for structured comparison of benefits, costs, and stakeholder perspectives.
- Secure correspondence to public bodies, with authenticated authority and workflow audit.
- Better representative sampling (commissioned surveys, appropriately disclosed methods), not inferred from self-selected chat traffic.
- Accessible optional voice/audio interfaces and multilingual support.
- Outcome evaluation across time and institutional knowledge transfer between office holders.

## 19. Source and assumption notes

- **Ankka:** https://github.com/thinkmorestupidless/ankka — public README reviewed 18 Oct 2026; durable entities, event sourcing, workflows, agents, endpoints and graph capabilities. The precise field-level shredding behaviour is explicitly supplied by the project owner and must be integration-tested.
- **Ankka Reasoning:** https://github.com/thinkmorestupidless/ankka-reasoning — public README reviewed 18 Oct 2026; implemented belief/market layers, records and graph consumers, with planned process and agent capabilities. The graph is a rebuildable projection and should not decide authoritative writes.
- **Voice:** https://makethemlisten.uk/ — current public site was access-code gated; product definition in this document reflects the project owner's requirements expressed during design discussions, not a verified public-site specification.
- **ICO, pseudonymisation:** https://ico.org.uk/for-organisations/uk-gdpr-guidance-and-resources/data-sharing/anonymisation/pseudonymisation — pseudonymised personal data remains in scope of data protection law.
- **ICO, political opinion data:** https://ico.org.uk/for-organisations/direct-marketing-and-privacy-and-electronic-communications/guidance-for-the-use-of-personal-data-in-political-campaigning-1/special-category-data/ — political opinions are special-category data; Article 6 and Article 9 requirements apply. Guidance notes it is under review following legislative changes.

**This specification is an engineering and product starting point, not a legal compliance certificate.** UK GDPR compliance depends on actual implementation, purposes, roles, lawful bases, DPIA outcomes and organisational practice.

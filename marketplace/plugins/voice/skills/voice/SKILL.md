---
name: voice
description: What Voice is and how to use it — privacy-first civic participation on ankka, where a participant with no identity talks privately, approves the exact text and audience of anything shared, a reviewer publishes it to a public issue with source-backed evidence from ankka-reasoning, opinions are counted apart from evidence, a representative records accountable actions, followers get one truthful update, and erasure destroys the participant's key while the public issue stays. Covers the three endpoints and every route, the refusals by name, the records and their classification, sealing, the asynchronous turn, the seed pack, the suites, deployment and operation. Use for any question about using Voice's API or interface, writing a client against it, curating an issue, operating a pilot, or changing the service.
---

# Voice

Voice is a privacy-first civic participation service on ankka. A participant with no identity talks
privately about a concern, confirms Voice's structured reading of it, approves the exact text and
audience of anything shared, and follows what a representative records about the public issue it
joins. Evidence lives in the reasoning service with its provenance; opinions are counted apart from
it; and a participant's personal data is erased by destroying their key while the anonymous public
record stays.

## Rules

1. **Three endpoints, three credentials.** `/public` takes none and creates nothing. `/me` takes
   `Authorization: Bearer <session>`, a token Voice minted at `POST /me/sessions`; the participant is
   the token's subject, never a value in a request. `/staff` takes a person's OIDC token from the
   installation's staff issuer; the role is the token's, and each route states the role it needs.
   Never send a session token to `/staff` or a staff token to `/me`.
2. **The recovery credential is not the session.** It is shown once at session start, sent only to
   `POST /me/sessions/recover` and `POST /me/erasure`, and rotated by each recovery. Never log it,
   store it, or send it as a bearer header.
3. **Nothing a browser waits on calls the model.** `POST /me/conversations/{c}/messages` and
   `POST /staff/issues/{i}/research` answer `202` with an id; poll the turn or the run, or hold the
   conversation's stream and reconnect with `after` set to the last sequence seen. The gateway ends
   every response at fifteen seconds.
4. **Match on `rule`, never on `error` or the status alone.** A refusal is
   `{rule, status, error, names}`; `rule` is stable and `names` carries the identifiers it is about,
   never a personal value. Some rules are answers, not errors: `question.unsupported` is an
   abstention, `aggregate.suppressed` an omitted cell, `erasure.unverified` a request held for an
   operator.
5. **Send states and modes by their wire names.** Sharing modes are `private`, `de-identified`,
   `representative-only` and `public-attribution`; action states are `proposed`, `in-progress` and
   `completed`; delivery is `inbox-immediate`; erasure stages read back as `held-for-operator`,
   `in-progress` and `completed`. A Scala-cased name is refused.
6. **A contribution is the exact text approved.** Submission needs the same text the participant
   approved for the same mode; a private contribution is never reviewed; a flagged one needs an
   enhanced review as a separate request before approval; withdrawn is terminal.
7. **A concern is a statement, an opinion is a count, evidence has provenance.** Never render a
   concern or a priority as a fact, never cite an opinion as evidence, and never show a claim's
   passage from anywhere but its evidence card. A question with no accepted evidence is abstained
   from, not answered.
8. **Only a representative records.** Investigations, actions, decisions, outcomes and updates need
   the `representative` role; an action needs every field, a date not in the future and not before
   the issue opened, and never moves back from completed. A suggested investigation is labelled
   suggested until a representative opens it.
9. **No personal value outside a sealed field.** In code, a personal value is `Sealed[A]`, sealed and
   opened at the edge only: an endpoint, a workflow step, a consumer or a timed action. An entity or a
   view never opens one. A log carries ids and rule names, never text. A public reply type may hold
   no `Sealed` and no participant id.
10. **Erasure destroys the key.** After `POST /me/erasure` completes, every `/me` route answers `410`
    `participant.erased`; an unflagged de-identified copy stays on the issue marked `authorErased`
    with the cause; a receipt holds classes and counts and no content. A database backup from before
    the erasure holds the key.
11. **The reasoning service is written through Voice only.** Voice writes as `service:<project>/voice`
    for the one holder `voice-evidence-pack`, reasoning first and the issue second, with ids
    `voice.<issue>.q<n>`, `voice.src.<slug>` and `voice.<issue>.c<n>`. Never write to it directly,
    never forward a person's identity to it, and treat its `503` as "graph tier off".
12. **The interface renders the server's vocabulary.** State labels come from the
    `Voice-State-Labels` header; a sealed field renders through one component as the value or
    "redacted or unavailable"; tokens live in memory for the tab.

## Before writing a client

- Which audience is it for, and so which endpoint and credential? A client that mixes them gets `401`.
- Where will it keep the recovery credential, and will it rotate what it keeps on every recovery?
- How will it read a turn's result: polling the turn, or the stream with its own cursor?
- Which refusals are answers it should render, and which are errors to fix?

## Mistakes to check for

- Sending `"state": "Proposed"` or `"mode": "DeIdentified"`: refused by name; use the wire names.
- Waiting on the message route for the reply: it answers `202` at once.
- Treating `410 participant.erased` or `404` on another participant's record as a bug.
- Reading an issue with a session and expecting an interest: following is `PUT /me/interests/{issue}`.
- Expecting a second update for a redelivered development, or an update for an unfollowed issue.
- Approving a flagged contribution and recording the enhanced review in one request.
- Putting a clock, a caller or a key in an entity or a view, or opening a `Sealed` there.
- Asserting on text another scenario may have published, in a suite that shares the service.

## Reference files

Open the one a task needs; each is one topic and stands alone.

### Start here

- `references/index.md` — What Voice is, the seven things it does for a resident and a representative, what it refuses to do, and where in this documentation to start.

### Get started

- `references/get-started/run-locally.md` — Start Voice on a laptop with its database, the reasoning service and a staff identity provider in Docker, load the seed pack, and open the interface.
- `references/get-started/first-issue.md` — Walk the loop over HTTP with curl, from a participant's session through a representative's action and a follower's update to an erasure and its receipt.

### Concepts

- `references/concepts/architecture.md` — The two services and the reasoning service beside them, the three endpoints with their access rules, the components behind each stage of the loop, and the rules the code holds to.
- `references/concepts/the-loop.md` — The seven stages a concern passes through, from a private conversation to an erasure, who acts at each, what becomes visible, and the seven state labels a reader sees.
- `references/concepts/audiences.md` — Who reaches Voice, through which endpoint and credential, what each may read and what each never sees, and why the operator who runs the pilot cannot read private content.
- `references/concepts/sealing.md` — How every personal value is sealed under a key per participant, opened only at the edge, classified by field, and made unreadable everywhere by destroying the key, and what stays.
- `references/concepts/opinions-and-evidence.md` — Why a concern is a statement and not a fact, why a priority is an opinion and never evidence, how counts are labelled and small cells hidden, and why no option is ever endorsed.
- `references/concepts/nothing-waits-for-a-model.md` — Why a conversation turn and a research run are workflows answered with 202, how a browser polls or streams the result, and how the gateway's fifteen-second bound shapes every route.
- `references/concepts/reasoning.md` — How Voice keeps questions, evidence and claims in ankka-reasoning as a service caller, what it writes and reads, how it names records, and why the graph tier is optional.
- `references/concepts/threat-model.md` — What must be read before any real text reaches a model, how identity is kept out of Voice, what every log and receipt omits, and the threats considered with their mitigations.

### Use

- `references/build/sessions.md` — Start a participant's session with an invite code, keep the recovery credential, renew and recover a session, and read the privacy and AI notices Voice shows before the first message.
- `references/build/conversations.md` — Hold a private conversation one turn at a time, read a turn's result by polling or a stream, and edit, confirm or discard the proposal Voice makes, so it becomes a confirmed concern.
- `references/build/sharing.md` — Approve the exact text and audience of a contribution, submit it for review, see what the reviewer sees and decides, join an issue, and withdraw a disclosure.
- `references/build/evidence.md` — Curate an issue as a reviewer, from questions and candidate evidence to claims and corrections, and read it as anyone, from the issue page and evidence cards to claim explanations, abstentions and search.
- `references/build/deliberation.md` — Express a priority, a trade-off or a consideration on an issue as a participant, amend or withdraw it, and read the labelled aggregate and the representative's dashboard that result.
- `references/build/investigations-and-actions.md` — Work the queue as a representative, ask the research agent for suggestions, open an investigation, record an action, a decision and an outcome, and publish an update, with what each refuses.
- `references/build/following.md` — Follow an issue as a participant, read the one update each development sends to the inbox, know what an update may and may not say, and stop following.
- `references/build/erasure.md` — See what Voice holds for a participant, request erasure with or without the recovery credential, verify a held request as the operator, read the receipt, and set the dormancy period.
- `references/build/the-interface.md` — The web app that fronts Voice, as a web-hosted ankka service that mounts /api to the service, with its pages for readers, participants and staff and how it holds tokens, streams and sealed fields.
- `references/build/seed-pack.md` — What the school-transport seed pack holds, how it is loaded through the staff routes into Voice and the reasoning service, and how its synthetic participants are replayed through the citizen routes.
- `references/build/testing.md` — The suites from the domain's milliseconds to the living features against a throwaway Postgres, the tier against the real reasoning service, the live-model suites, and the checker over the features and the glossary.

### Deploy and operate

- `references/deploy/deploy-on-ankka.md` — Deploy the reasoning service, the project secrets, the voice service and its interface to an ankka installation, with the staff realm the installation must have, by hand on kind or from a version tag.
- `references/deploy/operate.md` — Provision staff accounts, read the access log, handle erasure requests held for verification, set the dormancy period and threshold, know what the logs omit, and what a backup can undo.

### Reference

- `references/reference/http-api.md` — Look up every route Voice serves, by endpoint and audience, with its method, path, body, reply and the role it needs, and the headers, tokens and server-sent events that go with them.
- `references/reference/refusals.md` — Look up every rule a Voice command can break, by name, with its status, when it is broken and the identifiers it names, and the rules that are answers or build-time checks rather than errors.
- `references/reference/records.md` — Look up every record Voice keeps, the ankka component that holds it, its fields with their classification, the events that change it, and what erasure does to each.
- `references/reference/configuration.md` — Every environment variable the voice service, the seed client, the test recipes and the deploy script read, with its default and what it does, and the variables the platform supplies.
- `references/reference/limitations.md` — Check what Voice does not do, what is out of scope for the pilot, the two limits of erasure as built, and the bounds a reader, a participant or an operator will meet.
- `references/reference/glossary.md` — Look up what each word means in Voice, where every term has exactly one sense, and which everyday near-synonyms are not used for it.

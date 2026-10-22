---
name: voice
description: What Voice is and how to use it — privacy-first civic participation on ankka, where a participant with no identity talks privately, approves the exact text and audience of anything shared, a reviewer publishes it to a public issue with source-backed evidence from ankka-reasoning, opinions are counted apart from evidence, a representative records accountable actions, followers get one truthful update, and erasure destroys the participant's key while the public issue stays. Covers the three endpoints and every route, the refusals by name, the records and their classification, sealing, the asynchronous turn, the seed pack, the suites, deployment and operation. Use for any question about using Voice's API or interface, writing a client against it, curating an issue, operating a pilot, or changing the service.
pages:
  - index.md
  - get-started/run-locally.md
  - get-started/first-issue.md
  - concepts/architecture.md
  - concepts/the-loop.md
  - concepts/audiences.md
  - concepts/sealing.md
  - concepts/opinions-and-evidence.md
  - concepts/nothing-waits-for-a-model.md
  - concepts/reasoning.md
  - concepts/threat-model.md
  - build/sessions.md
  - build/conversations.md
  - build/sharing.md
  - build/evidence.md
  - build/deliberation.md
  - build/investigations-and-actions.md
  - build/following.md
  - build/erasure.md
  - build/the-interface.md
  - build/seed-pack.md
  - build/testing.md
  - deploy/deploy-on-ankka.md
  - deploy/operate.md
  - reference/http-api.md
  - reference/refusals.md
  - reference/records.md
  - reference/configuration.md
  - reference/limitations.md
  - reference/glossary.md
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

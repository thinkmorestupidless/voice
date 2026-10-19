---
title: Five audiences
description: Who reaches Voice, through which endpoint and credential, what each may read and what each never sees, and why the operator who runs the pilot cannot read private content.
kind: concept
audiences: [reader, participant, reviewer, representative, operator]
related: [concepts/architecture.md, concepts/sealing.md, concepts/threat-model.md, build/sessions.md, deploy/operate.md]
---

# Five audiences

Every route belongs to one of three endpoints, and every endpoint admits one kind of caller. Who a
person is decides what they may read, and the decision is made at the edge, once, from the
credential they present.

| Audience | Reaches Voice through | Reads | Never reads |
|---|---|---|---|
| a reader | the public pages, no account | issues, evidence, claims, options, investigations, actions, decisions, outcomes, the chronology, counts with their caveat | any participant id, any private text, any sealed value |
| a participant | a session token from an invite code | their own conversations, concerns, contributions, expressions, interests and inbox, opened at the edge | anyone else's records |
| a reviewer | a staff token with the `reviewer` role | contributions under review with their flags, evidence proposals, review prompts | the conversation a contribution came from |
| a representative | a staff token with the `representative` role | restricted contributions, the issue dashboard with participant-derived measures, investigations, the work queue | conversations, concerns, unshared contributions |
| an operator | a staff token with the `operator` role | staff accounts, the access log, erasure requests and receipts | any personal value: a receipt holds none |

## A reader

A reader needs nothing. No public route creates anything, so reading an issue creates no interest
and leaves no trace of the reader. A public reply is checked at build time to contain no sealed
field and no participant id, and a public route asked for a private, restricted, withdrawn or
unpublished record answers `404`, the same as for a record that never existed, so existence leaks
nothing.

## A participant

A participant is pseudonymous: an id Voice minted, a recovery credential shown once and kept as a
hash, and a key in the secret store. The session token names the participant, and every `/me` route
is scoped to that participant's own records; a route that names another participant's record
answers `404`. The participant is told, before their first message, what Voice keeps, shares and
erases, and that they are talking to a model. [Sessions](../build/sessions.md).

## Staff

A reviewer, a representative and an operator are people with accounts in the installation's identity
provider. Their roles arrive in the token as realm roles; Voice holds only what the issuer cannot,
a display name and, for a representative, the authority they act under, which is copied onto every
action they record. Every staff read of a restricted record, and every refused staff request, is
written to the access log the operator reads.

The three roles are kept apart on purpose. A reviewer decides what enters the shared space and
never records an action; a representative records actions and never approves a contribution; an
operator runs the pilot and is not entitled to read private content, so the receipts and the access
log they read hold identifiers and classes, never text.

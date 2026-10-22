# Limitations

> Check what Voice does not do, what is out of scope for the pilot, the two limits of erasure as built, and the bounds a reader, a participant or an operator will meet.

Source: https://docs.makethemlisten.uk/reference/limitations/
The honest list: what is not built, what is deliberate, and what a pilot must plan round. Each entry
says how Voice behaves today.

## Out of scope for the pilot

- Live nationwide deployment, or importing every representative or constituency.
- Verifying residency, or one person one response beyond the invite codes.
- Polling claims, population weighting or demographic inference.
- Campaign messaging, voting recommendations or persuasion.
- Automated official decisions, or any message sent in a representative's name.
- Publishing original conversations or unverified machine-generated claims.
- An agent-driven reasoning process beyond what the reasoning service provides.
- Continuous large-scale ingestion, graph recommendations, broadcast notifications or mobile apps.
- A voice or audio interface.
- Digest, email or push delivery of updates. The inbox is the only delivery.
- Clustering contributions across participants, or merging issues. A confirmation suggests existing
  issues; it never joins one by itself.
- Uploading documents or links in a conversation. Documents enter only as evidence a reviewer
  proposes.
- Following an investigation. A follower follows the issue it belongs to.
- Rate limiting. The platform's gateway has none, and the pilot's abuse control is the invite code.

## Erasure

- Erasure is Voice's own, built on a key per participant, until ankka's personal data erasure ships.
  When it does, `Sealed` and the per-participant key give way to ankka's types and keyring.
- A participant key lives in the service's own database, encrypted under the service key. A backup
  taken before an erasure can restore it; backups must expire within the erasure deadline.
- A withdrawn de-identified copy leaves the issue's state, page and search index, but its text stays
  in the issue's journal.
- An operator's verification evidence is not kept.

## Search and counts

- Public search is a sequential scan of the search view. It suits a pilot's few issues, not many.
- Counts of expressions below the suppression threshold, `5` by default, are not shown.
- Counts of erased authors are shown at any size: they say that some author was erased, and why.

## Conversations

- Every turn needs the model. Without a key, a message is recorded and the turn ends in
  `CouldNotInterpret`.
- A message is at most 4,000 characters.
- The stream sends no event ids; a client keeps its own cursor and reconnects at the gateway's
  fifteen-second bound.

## Reasoning

- The graph tier is optional. Without it, the reasoning service answers from its own records, and the
  case for a hypothesis and what was learned since a time are not shown.
- An issue keeps evidence's scope, authority and limitations itself, beside the reference, because
  the reasoning service has no field for them.
- `revisedBy` on a claim comes from a view in the reasoning service and may lag a moment after a
  revising claim is written.

## Delivery and timers

- Workflow step retries are immediate, with no backoff, so a failing delivery is retried within
  milliseconds before it is recorded as failed.
- A workflow interrupted by a restart resumes when next addressed; for a delivery, the redelivered
  event addresses it.
- A changed dormancy period applies to timers scheduled afterwards; a participant's existing timer
  keeps its period until their next session.

## Deployment

- The interface's image and descriptor are deployed by hand; the tag workflow deploys the service
  alone.
- A deployment on an installation has not yet been run end to end; the descriptors decode under
  ankka's rules, and the same images and variables run on a laptop.

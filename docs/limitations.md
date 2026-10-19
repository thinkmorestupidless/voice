# Limitations

What voice does not do, so nothing here is promised past it.

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
- Uploading documents or links in a conversation. Documents enter only as evidence a reviewer proposes.
- Following an investigation. A follower follows the issue it belongs to.

## Erasure

- Erasure is voice's own, built on a key per participant, until ankka's personal data erasure
  ships. When it does, `Sealed` and the per-participant key give way to ankka's types and keyring.
- A participant key lives in the service's own database, encrypted under the service key. A backup
  taken before an erasure can restore it.
- A withdrawn de-identified copy leaves the issue's state, page and search index, but its text stays
  in the issue's journal.
- An operator's verification evidence is not kept.

## Search and counts

- Public search is a sequential scan of the search view. It suits a pilot's few issues, not many.
- Counts of expressions below the suppression threshold, `5` by default, are not shown.
- Counts of erased authors are shown at any size: they say that some author was erased, and why.

## Reasoning

- The graph tier is optional. Without it, the reasoning service answers from its own records.
- An issue keeps evidence's scope, authority and limitations itself, beside the reference.

## Delivery

- Workflow step retries are immediate, with no backoff, so a failing delivery is retried within
  milliseconds before it is recorded as failed.

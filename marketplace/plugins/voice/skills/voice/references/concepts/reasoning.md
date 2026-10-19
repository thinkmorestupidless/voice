# Voice and the reasoning service

> How Voice keeps questions, evidence and claims in ankka-reasoning as a service caller, what it writes and reads, how it names records, and why the graph tier is optional.

Source: https://docs.makethemlisten.uk/concepts/reasoning/
Voice holds no evidence of its own. Questions, hypotheses, sources, evidence and claims live in
[ankka-reasoning](https://reasoning.ankka.cloud/), a sibling service whose records are never edited,
and an issue holds references to them. Voice adds no civic word to the reasoning service: no node
kind, edge kind, holder kind or field.

## Who Voice is to it

Every reasoning route admits another ankka service by its certificate. Voice calls it service to
service, from endpoints and workflow steps, as the writer `service:<project>/voice`, and never
forwards a participant's or a representative's identity. No participant reaches the reasoning
service directly. Voice registers one holder, `voice-evidence-pack`, and states every claim as it:
the seed pack's claims and a reviewer's alike, because every write passes through a reviewer's
decision in Voice first.

## What Voice writes

| When | What |
|---|---|
| a reviewer or the seed opens an issue's question | the question with at least two hypotheses |
| the seed registers a source | the source, by id and name |
| a reviewer accepts candidate evidence | the evidence: source, locator, excerpt, publication date |
| a reviewer states a claim | the claim, deriving from evidence, with a stance on each hypothesis |
| a reviewer marks a claim superseded by a correction | a new claim that revises the old; the old stays |

Every write is idempotent by id: a new record answers `201`, the same record again `200`, and a
different record under a held id is refused. Voice writes to reasoning first and links the reference
on the issue second, so a crashed step re-runs the write and then the link, and an issue never links
a reference that does not resolve.

Scope, authority and limitations are Voice's, kept on the evidence link beside the reference, because
the reasoning service has no field for them. A candidate's passage is held on the link only until
acceptance writes it to reasoning; an accepted passage is read from reasoning each time it is shown,
so a withdrawal there is honoured here.

## How Voice names records

| Record | Id | Example |
|---|---|---|
| question | `voice.<issue>.q<n>` | `voice.iss-01M57P….q1` |
| hypothesis | `h<n>` within its question, referenced as `<question>/<hypothesis>` | `voice.iss-01M57P….q1/h1` |
| source | `voice.src.<slug>` | `voice.src.route-review-2026` |
| evidence | chosen by reasoning: the SHA-256 of source, locator and excerpt | a digest |
| claim | `voice.<issue>.c<n>` | `voice.iss-01M57P….c3` |

A prefix cannot collide with another writer's, and carries no personal data.

## What Voice reads

A claim's explanation is assembled from record reads by id: the claim, each piece of evidence it
derives from, and each piece's source. Contradicting claims come from Voice's own list of the claims
linked to the issue's question, every one whose stance on the same hypothesis is the opposite, read
by id the same way. A claim with a non-empty `revisedBy` is shown as revised. A question for which
Voice holds no linked claim with accepted evidence, or whose claims are all withdrawn or superseded,
is answered with an abstention, and no reasoning route is called for a citation.

The record reads need the reasoning service's Postgres alone. Its graph tier, Kafka and Neo4j,
answers the case for a hypothesis and what was learned since a time; Voice treats its `503` as
"graph tier not running" and shows the record-assembled explanation, so nothing on the public page
depends on the graph.

## Refusals Voice handles

| Reasoning rule | Voice's handling |
|---|---|
| `id.held-by-another` | a seed or curation error, shown to the reviewer |
| `record.not-held` | shown as "record unavailable", logged, never fabricated |
| `holder.writer.does-not-speak` | a deployment error: Voice must be the writer that registered the holder |
| `graph.unavailable` | fall back to records |
| a validation refusal: a statement too long, evidence dated before publication | shown to the reviewer with the rule's name |

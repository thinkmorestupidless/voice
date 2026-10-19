# Evidence and the public issue

> Curate an issue as a reviewer, from questions and candidate evidence to claims and corrections, and read it as anyone, from the issue page and evidence cards to claim explanations, abstentions and search.

Source: https://voice.ankka.cloud/build/evidence/
An issue is what a reader explores: approved contributions, questions with their competing
hypotheses, evidence from cited sources, claims derived from that evidence, options, and the record
of what was done. A reviewer curates the evidence; the reasoning service holds it; Voice shows it
with its provenance.

## Read an issue

```bash
curl -s localhost:9000/public/issues/$ISSUE | jq 'keys'
```

```text
[ "actions", "aggregate", "chronology", "claims", "contributions", "counts", "decisions", "evidence", "id",
  "investigations", "limitations", "options", "outcomes", "questions", "summary", "title", "updates" ]
```

Every part carries what a reader is owed. A contribution has its text, `publishedAt` and, when its
author was erased, `authorErased` with the cause. The counts are of contributions, not people, and
say so. The `limitations` text says what the issue is not: a survey, or a claim about how many
agree. The aggregate is on [Priorities and trade-offs](deliberation.md).

`GET /public/issues?q=bus` lists issues whose public text matches a keyword, with `limit` and
`after`; `GET /public/issues/{issue}/chronology` is the ordered record with one state label per
entry; `GET /public/sources/{source}` is a source's card: name, licence, attribution, link.

## An evidence card

```bash
curl -s localhost:9000/public/issues/$ISSUE/evidence/$EVIDENCE
```

```json
{
  "id": "evl-01M57PJHT367ZNVVECPVFE63BS",
  "evidence": "b1c3f813…",
  "state": "accepted",
  "source": { "id": "voice.src.route-review-2026", "name": "Northfield Council home-to-school route review: Route 14 (illustrative)",
              "licence": "CC0 1.0 (fictional sample text)", "attribution": "Fictional sample written for the Voice seed pack. …" },
  "locator": "Section 3, table 2",
  "passage": "Route 14 carried an average of 11 pupils a day in the autumn term.",
  "publishedAt": "2026-01-20T00:00:00Z",
  "recordedAt": "2026-10-18T14:27:42.032Z",
  "scope": "Morning runs on Route 14, September to December 2025",
  "authority": "Council officers' count from boarding records",
  "limitations": "Counts the morning run only; does not count pupils driven to school after the route changed."
}
```

The source, locator, passage and publication date are the reasoning service's, read each time the
card is shown. The scope, authority and limitations are Voice's, kept beside the reference. The
state is one of `candidate`, `accepted`, `rejected` and `superseded`.

## A claim's explanation

```bash
curl -s localhost:9000/public/issues/$ISSUE/claims/voice.$ISSUE.c1 | jq '{claim: .claim.statement, evidence: [.evidence[].passage], contradicts: [.contradicting[].claim.statement]}'
```

```json
{
  "claim": "The route review's count suggests Route 14 carried too few pupils to justify a contract.",
  "evidence": [ "Route 14 carried an average of 11 pupils a day in the autumn term." ],
  "contradicts": [ "The parents' survey suggests more pupils relied on Route 14 than the route review counted." ]
}
```

The explanation is the claim with its stances, each piece of evidence it derives from as a card
with its source, and the claims that contradict it on the same hypothesis with their cards. A claim
revised by a correction is shown with the claim that revises it. The passage is quoted from the
source's record, never paraphrased.

A question with no accepted, unwithdrawn, unsuperseded claim is answered with an abstention, which
is an answer and not an error:

```bash
curl -s localhost:9000/public/issues/$ISSUE/questions/voice.$ISSUE.q2
```

```json
{ "abstained": true, "reason": "The records Voice holds do not support an answer to this." }
```

## Search

`GET /public/search?q=footpath` answers hits over issues, sources and options: the kind, the record
id, its version or date, a title, a snippet from public text only, and a link. A search needs no
session and creates nothing; a reader who follows a hit to an issue has not followed the issue.

## Curate, as a reviewer

A reviewer links questions, proposes and accepts evidence, and states claims. Every write goes to
the reasoning service first and the issue second, so an issue never links a reference that does not
resolve; an id the reasoning service does not hold is refused with `422` and
`evidence.reference.unresolved`.

```bash
curl -s localhost:9000/staff/issues/$ISSUE/questions -H "Authorization: Bearer $REV" -H 'Content-Type: application/json' \
  -d '{"statement":"Why was the Route 14 school bus withdrawn?","hypotheses":[{"id":"h1","statement":"Too few pupils used it"},{"id":"h2","statement":"Its contract ended and was not renewed"}]}'
curl -s localhost:9000/staff/issues/$ISSUE/evidence -H "Authorization: Bearer $REV" -H 'Content-Type: application/json' \
  -d '{"source":"voice.src.route-review-2026","locator":"Section 3, table 2","excerpt":"Route 14 carried an average of 11 pupils a day in the autumn term.",
       "publishedAt":"2026-01-20T00:00:00Z","scope":"Morning runs on Route 14, September to December 2025",
       "authority":"Council officers'"'"' count from boarding records","limitations":"Counts the morning run only."}'
```

The evidence is a candidate, held on the issue with its passage, until the reviewer accepts it:

```bash
curl -s -X POST localhost:9000/staff/issues/$ISSUE/evidence/$EVIDENCE/accept -H "Authorization: Bearer $REV"
```

Acceptance writes the evidence to the reasoning service, drops the candidate's passage from the
link, and raises a review prompt for every claim and decision that derives from that evidence.
`reject` takes a reason; `supersede` takes the id of the correcting evidence and a reason, and a
superseded link supports nothing on its own. A link cannot move from rejected to accepted, or from
superseded to anything; such a move is refused with `409` and `evidence.state.invalid`. A principal
without the reviewer role is refused with `evidence.not-reviewer`.

A claim derives from accepted evidence and takes a stance on each hypothesis it bears on:

```bash
curl -s localhost:9000/staff/issues/$ISSUE/claims -H "Authorization: Bearer $REV" -H 'Content-Type: application/json' \
  -d '{"statement":"The route review'"'"'s count suggests Route 14 carried too few pupils to justify a contract.",
       "derivesFrom":["b1c3f813…"],"stances":[{"hypothesis":"voice.'$ISSUE'.q1/h1","stance":"supports"}]}'
```

A correction is a new claim with `revises` naming the old one, which stays and gains `revisedBy`.

## Review prompts

`GET /staff/issues/{issue}/review-prompts` lists what accepting or superseding evidence raised: the
evidence, the claims and decisions deriving from it, and when. A reviewer resolves a prompt with a
note; resolving changes no claim and no decision, and resolving twice answers the issue unchanged.
A representative may read the prompts and the evidence list, including candidates.

## Options

A policy option is a described possible intervention: title, description, costs, benefits and open
questions. A reviewer or a representative writes one with `PUT /staff/issues/{issue}/options/{option}`;
a body that carries a recommendation or a ranking is refused with `422` and `option.endorsement`.
`GET /staff/issues/{issue}/options` lists them, and the public issue page shows them.

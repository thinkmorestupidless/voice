# The seed pack

> What the school-transport seed pack holds, how it is loaded through the staff routes into Voice and the reasoning service, and how its synthetic participants are replayed through the citizen routes.

Source: https://docs.makethemlisten.uk/build/seed-pack/
`seed/school-transport/` is one public issue, "Rural school bus withdrawal", with the sources,
evidence, questions and claims a reviewer would have curated, and synthetic participants for the
representative's dashboard. Every source is fictional, written for the pack, labelled
"(illustrative)" and carrying its licence and attribution; the pack's own text is licensed in
`licences/`.

## The files

| File | Holds |
|---|---|
| `sources.json` | sources: id, name, licence, attribution, link, published date |
| `evidence.json` | passages from the sources: source, locator, excerpt, scope, authority, limitations, the hypotheses each bears on, and whether the seed accepts it |
| `questions.json` | the issue's question and its hypotheses, and one question no evidence answers |
| `claims.json` | claims stated from evidence, each with the evidence it derives from and its stances |
| `issue.json` | the issue's title and summary, and which questions it links |
| `options.json` | the issue's policy options: title, description, costs, benefits, open questions; none endorsed |
| `participants.json` | synthetic participants with what each shares and expresses |
| `licences/` | the licence of the pack's text, and the statement that every source is fictional |

The conflicting pair: the council's route review says Route 14 carried eleven pupils a day; the
parents' survey says twenty-three. Both are accepted evidence on the hypothesis "The route was
withdrawn because too few pupils used it", one supporting and one contradicting it. The correction:
one piece of evidence is the route review's corrected count, which the seed proposes and leaves as
a candidate, so a reviewer can accept it and supersede the first count with it. The second
question, about the ring road works, has no evidence, so Voice abstains.

Identifiers follow Voice's scheme for the reasoning service: `voice.<issue>.q<n>` for questions,
`voice.src.<slug>` for sources, `voice.<issue>.c<n>` for claims.

## Load it

```bash
SEED_STAFF_TOKEN=<a reviewer's token> just seed
```

```text
issue iss-01M57PJHFPN27YMK6J1TD0T2NG: 2 questions, 7 evidence, 3 claims
```

The seed client loads the pack through Voice's staff routes as a reviewer, and Voice writes to the
reasoning service: the same path a reviewer's curation takes, so nothing reaches reasoning that
Voice did not decide. Every write is idempotent, so loading twice changes nothing. Set `SEED_ISSUE`
to an issue id to add the pack's material to an existing issue instead of opening a new one.

## Replay the participants

With `SEED_INVITE` set to one of the pilot's invite codes, `just seed` also replays
`participants.json` through the citizen routes: each synthetic participant starts a session, holds
its conversation, confirms, shares in its mode, expresses its priority and trade-offs, and follows
the issue. This needs the model, because every turn calls it, so it runs only with
`ANTHROPIC_API_KEY` set. Afterwards the representative's dashboard and the public aggregate have
enough participants to show a cell or two over the suppression threshold.

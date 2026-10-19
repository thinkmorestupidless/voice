# Seed pack: school transport

One public Issue, "Rural school bus withdrawal", with the sources, evidence, question and claims a
reviewer would have curated, and synthetic participants for the representative's dashboard. Every
source is fictional and written for this pack; each carries its licence and attribution in
`sources.json`.

| File | Holds |
|---|---|
| `sources.json` | sources: id, name, licence, attribution, link, published date |
| `evidence.json` | passages from the sources: source, locator, excerpt, scope, authority, limitations, the hypotheses each bears on, and whether the seed accepts it |
| `questions.json` | the Issue's question and its hypotheses, and one question no evidence answers |
| `claims.json` | claims stated from evidence, each with the evidence it derives from and its stances |
| `issue.json` | the Issue's title and summary, and which questions it links |
| `options.json` | the Issue's policy options: title, description, costs, benefits, open questions; none endorsed |
| `licences/` | the licence of the pack's text, and the statement that every source is fictional |
| `participants.json` | synthetic participants with what each shares and expresses, for tier 4 |

The conflicting pair: the council's route review says the withdrawn route carried eleven pupils a
day; the parents' survey says twenty-three. Both are accepted evidence on the hypothesis "The route
was withdrawn because too few pupils used it", one supporting and one opposing it.

Identifiers follow `contracts/reasoning-integration.md`: `voice.<issue>.q<n>` for questions,
`voice.src.<slug>` for sources, `voice.<issue>.c<n>` for claims.

The correction: `e7` is the route review's corrected count. The seed proposes it and leaves it a
candidate, so a reviewer can accept it and supersede `e1` with it.

The seed loads the pack through Voice's staff routes as a reviewer, and Voice writes to reasoning:
the same path a reviewer's curation takes, so nothing reaches reasoning that Voice did not decide.

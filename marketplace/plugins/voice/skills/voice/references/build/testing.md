# Testing

> The suites from the domain's milliseconds to the living features against a throwaway Postgres, the tier against the real reasoning service, the live-model suites, and the checker over the features and the glossary.

Source: https://voice.ankka.cloud/build/testing/
Voice is tested at four levels, each proving more with more running, and `sbt test` runs the first
two in a few minutes with Docker. The live-model suites and the tier against the real reasoning
service are run on purpose.

## The domain and each component

```bash
sbt domain/test
sbt 'service/testOnly voice.unit.*'
```

The domain module depends on `ankka-core` only, so it cannot name a component and its suites run in
milliseconds: a sealed value round-trips through the platform's serializer and prints as its subject;
every event and state type is listed and the suite fails on a text field with no classification; every
refusal name in the contract exists exactly once and vice versa; the state machines of contribution,
evidence, action and notification refuse the transitions they should; a receipt's types hold no sealed
value or free text. The unit suites run each entity and workflow with the platform's test kits, and a
scripted model provider in place of the agents.

## The living features

```bash
sbt 'service/testOnly voice.features.*'
```

The platform's `AnkkaTestKit` boots the whole service against a throwaway Postgres, with a recorded
model provider answering from `modules/service/src/test/resources/model/`, an in-suite ES256 issuer
minting staff tokens, and a stub reasoning service answering the record routes from the seed pack.
The seven feature files under `features/` run as one suite per file, driving the service through its
three endpoints:

| Feature | Proves |
|---|---|
| `participation/private-conversation.feature` | a session with no identity, a turn, a proposal, confirmation, recovery across a restart; nothing public |
| `disclosure/exact-approval.feature` | four modes, exact text, review, rejection with reason, enhanced review, association, withdrawal; no public route returns a private field |
| `evidence/source-backed.feature` | a claim's explanation from records, the contradiction, the abstention, search creating no interest, corrections and review prompts |
| `deliberation/opinions-not-evidence.feature` | opinions never in claims, aggregates labelled, small cells suppressed, the dashboard's labels |
| `action/human-authority.feature` | suggested stays suggested, only a representative records, dates refused, the chronology's labels |
| `feedback/one-truthful-update.feature` | one update per follower per development, through a redelivered event and a restart |
| `erasure/field-erasure.feature` | after everything else for two participants, erase one: replay, restart, a view rebuild and a database dump find no plaintext; the other participant and the issue are intact; dormancy |

Suites share one service, so a scenario asserts on text of its own, never on text another scenario
may have published. A log-hygiene suite runs the whole set with a capturing appender and fails on any
of the seed's personal text in a log line or a trace name.

## Against the real reasoning service

```bash
just up
just tier3
```

`just tier3` runs the one suite tagged `Reasoning` against the reasoning service `just up` started:
the seed pack loads through the real service and again without change, every reference the issue
links resolves by id, and the contradiction and the abstention come from records alone. With
`VOICE_GRAPH=1` and `just up-graph`, a fourth test reads the graph tier. The build excludes the
`Live` and `Reasoning` tags from `sbt test`; a recipe lifts one with `VOICE_INCLUDE_TAGS`.

## The live model

```bash
ANTHROPIC_API_KEY=… just live
```

The live suites run the seed conversations against the model, checking the shape of every reply
and that a proposal's passages fall inside the participant's messages, and an extraction evaluation
over a labelled set in `modules/service/src/test/resources/evaluation/`. They spend tokens, so no
other recipe runs them. `VOICE_MODEL_RECORD=1` re-records the fixtures the feature suites answer
from, which is needed when a prompt changes.

## The interface

```bash
cd web && npm run typecheck && npm test && npm run build
```

## The features, the glossary and the specifications

```bash
just check
```

The checker reads every feature, the glossary and the specifications that name scenarios, and
reports a step that uses an undefined word, a refused synonym, two scenarios that contradict, or a
specification naming a scenario that does not exist. A clean run says `0 findings`.
[Features and the glossary](../contributing/features-and-glossary.md) says how to keep it so.

## Continuous integration

Every push and pull request runs the format check, the domain, service and seed suites, the
interface's type check, tests and build, and the checker. The documentation build runs on a pull
request that touches it. Nothing is deployed or published except on a version tag.

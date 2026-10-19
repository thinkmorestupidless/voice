---
title: Features and the glossary
description: How the living features under features/, the glossary at the root and the specifications are kept in agreement by one checker, and what to do when a step needs a word the glossary lacks.
kind: contributing
related: [contributing/documentation.md, build/testing.md, reference/glossary.md]
---

# Features and the glossary

Voice's behaviour is written as Gherkin features under `features/`, one file per stage of the loop,
and each file runs as a suite against the whole service. The words a step may use are the glossary's,
`GLOSSARY.md` at the root of the repository, and a checker holds the two together with the
specifications that name scenarios.

## The checker

```bash
just check
```

```text
0 findings in 59 scenarios and 1 spec
```

It reads every feature, the glossary and the specifications, and reports:

| Finding | Means |
|---|---|
| `undefined-term` | a step uses a word that is neither a glossary term nor an everyday word |
| `refused-synonym` | a step uses a word a glossary entry says to avoid, such as "user" for participant |
| `contradiction` | two scenarios share preconditions and an action and state different outcomes |
| `repeated-scenario`, `duplicated-scenario` | the same scenario twice, or a scenario's steps copied into a specification |
| `unknown-scenario`, `untraced-requirement` | a specification names a scenario that does not exist, or a requirement no scenario shows |
| `parse` | Gherkin that does not parse |

A continuous-integration job runs it on every push, so a feature that drifts from the glossary fails
the build before it fails a reader.

## Adding a word

A step that needs a word the glossary lacks adds the word to `GLOSSARY.md`, in its section, with one
sense and the near-synonyms it refuses. A word whose sense is not yet settled is marked *Proposed*
until the scenarios that use it pass against the service. The everyday words at the end of the
glossary are the ones any step may use without a definition; a word that carries meaning in Voice
belongs in a section, not there.

## Writing a scenario

- A scenario describes the domain, never the interface: no screen, button, route or status code.
  "the reviewer approves the derivative", not "the reviewer clicks Approve".
- A scenario that asserts a text is not public uses a text of its own, because the suites share one
  service and another scenario may have published the same words.
- A scenario that asserts on an aggregate uses enough synthetic participants to clear the
  suppression threshold, or asserts on the suppression.
- A scenario that needs a model reply needs a recorded fixture under
  `modules/service/src/test/resources/model/`, matched on the message's text; a step that sends a
  message with no fixture fails naming the request.

## The step definitions

Each feature file has a steps object in `modules/service/src/test/scala/voice/features/`, and the
suite binds the steps to the service through its three endpoints, with helpers that take a role and
a route: a reviewer's helper adds the `/staff` prefix, so a step names the route beneath it. A step
with more than six placeholders needs the suite's own step-body instance.

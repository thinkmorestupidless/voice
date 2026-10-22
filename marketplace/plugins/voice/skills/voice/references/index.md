# Voice

> What Voice is, the seven things it does for a resident and a representative, what it refuses to do, and where in this documentation to start.

Source: https://docs.makethemlisten.uk/
Voice is a privacy-first civic participation service. A resident talks privately about a local
problem, checks what Voice understood, decides exactly what to share and with whom, and later hears
what was done about it. A representative sees shared, source-backed issues and records what they
will do, with a date, an authority and an owner. Anyone can read and search the public issues
without an account. A participant can have their personal data erased, and the public issue stays.
It is an [ankka](https://docs.ankka.cloud/) application, and it keeps its evidence in
[ankka-reasoning](https://reasoning.ankka.cloud/).

This is a demonstrator for a pilot: synthetic data, invited participants, one area.

## The seven things it does

1. **Hear.** A participant starts a session with an invite code and no name, email or verified
   identity. They talk to Voice about a concern. Voice proposes a structured reading of what they
   said: the problem, where, when, who it affects and how. The participant edits it, confirms it or
   discards it. Nothing they said leaves the conversation at this point.
2. **Share.** For a confirmed concern, the participant writes or approves the exact text to share
   and picks one of four modes: private, de-identified, for representatives only, or publicly under
   a name they choose. A reviewer checks the text, with an automatic privacy check that flags
   distinctive circumstances, health, minors and small groups. An approved text joins a shared
   public issue.
3. **Understand.** An issue links evidence from cited sources, each with its state: candidate,
   accepted, rejected or superseded. Questions show their competing explanations, and what is not
   known is said.
4. **Deliberate.** Participants say how much an issue matters to them and which trade-offs they
   would accept. These are opinions, shown apart from evidence, as counts with their method and a
   caveat. A count below the suppression threshold is not shown.
5. **Act.** A representative opens an investigation, records a dated action with its authority,
   owner and rationale, and later a decision and an outcome. Each is public, with who recorded it.
6. **Return.** A participant who follows an issue gets one update in their inbox for each material
   development: what was recorded, why they are told, and links to the record. The update never
   says a problem is solved unless the record does.
7. **Erase.** A participant can ask for their personal data to be erased. Their key is destroyed, so
   every protected field becomes unreadable everywhere; their private records are deleted; a
   de-identified text they published stays on the issue with no link to them. A participant with no
   session for the dormancy period is erased the same way.

## What it is not

It is not a poll, a petition or a vote. It does not say how many people agree, weight responses or
infer demographics. It sends nothing in a representative's name and decides nothing. The AI in it
replies, proposes and suggests; people confirm, approve, record and decide. The full list is on
[Limitations](reference/limitations.md).

## The example

Every page uses one example: the issue "Rural school bus withdrawal", from the seed pack the
repository ships. The Route 14 school bus between Kingsway and Northfield was withdrawn at the end
of March. The council's route review says it carried eleven pupils a day; the parents' survey says
twenty-three. Both are accepted evidence on the hypothesis that the route was withdrawn because too
few pupils used it, one supporting it and one opposing it, so a reader sees the contradiction. A
second question, whether the ring road safety works will be finished before the new school term,
has no evidence, so Voice abstains. Every source in the pack is fictional and says so.

## Where to start

- **Trying it.** [Run it on your machine](get-started/run-locally.md) starts the service, the
  interface, the reasoning service and a staff identity provider; [Your first issue](get-started/first-issue.md)
  walks the loop from a session to an erasure with `curl`.
- **Understanding it.** [How Voice works](concepts/architecture.md) is the overview.
  [The loop](concepts/the-loop.md), [Five audiences](concepts/audiences.md) and
  [Sealed fields and erasure](concepts/sealing.md) are the three ideas everything else follows.
  [Opinions apart from evidence](concepts/opinions-and-evidence.md),
  [Nothing waits for a model](concepts/nothing-waits-for-a-model.md) and
  [Voice and the reasoning service](concepts/reasoning.md) complete the model;
  [Threat model](concepts/threat-model.md) says what was considered.
- **Using it.** One page per stage of the loop, from [Sessions](build/sessions.md) to
  [Erasure](build/erasure.md), each with the routes it uses; [The interface](build/the-interface.md)
  for the web app; [The seed pack](build/seed-pack.md) and [Testing](build/testing.md) for the
  material and the suites.
- **Running it.** [Deploy on ankka](deploy/deploy-on-ankka.md) and
  [Operate the pilot](deploy/operate.md).
- **Looking something up.** The [HTTP API](reference/http-api.md), the
  [refusals](reference/refusals.md), the [records](reference/records.md), the
  [configuration](reference/configuration.md) and the [glossary](reference/glossary.md).
- **Checking what is missing.** [Limitations](reference/limitations.md).

## For models and agents

This documentation is published in forms a model can read directly. `llms.txt` at the site root lists
every page with a one-sentence description, `llms-full.txt` holds every page in one file, and each page
is also served as Markdown at its own path with a `.md` suffix. The same pages are rendered as an agent
skill, `voice`, in
[the repository](https://github.com/thinkmorestupidless/voice/tree/main/marketplace/plugins/voice).

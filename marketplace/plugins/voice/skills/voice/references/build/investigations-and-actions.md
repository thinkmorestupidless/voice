# Investigations and actions

> Work the queue as a representative, ask the research agent for suggestions, open an investigation, record an action, a decision and an outcome, and publish an update, with what each refuses.

Source: https://voice.ankka.cloud/build/investigations-and-actions/
A representative is the only person who records what will be done. Everything on this page needs
the `representative` role; a principal without it is refused with `403` and
`investigation.not-representative`, and nothing changes.

## The work queue

```bash
curl -s localhost:9000/staff/queue -H "Authorization: Bearer $REP"
```

```json
[ { "kind": "investigation", "id": "inv-01M57PMJ8DBVC9A2H3AM81SE00", "issue": "iss-01M57P…", "title": "Why was Route 14 withdrawn?",
    "state": "under investigation", "since": "2026-10-18T14:28:48.013269Z", "ageSeconds": 61098, "link": "/staff/investigations/inv-01M57PMJ8DBVC9A2H3AM81SE00" } ]
```

The queue holds what waits: new issues, pending reviews, open review prompts, open investigations,
and investigation tasks awaiting an external party, each with its state and age. An open item with
no response from the representative is shown as unanswered.

## Ask for research

```bash
curl -s localhost:9000/staff/issues/$ISSUE/research -H "Authorization: Bearer $REP" \
  -H 'Content-Type: application/json' -d '{"question":"What else bears on why the route was withdrawn?"}'
```

```json
{ "run": "inv-01M5…" }
```

The reply is `202`. The research agent reads the issue, its evidence and the reasoning service's
records with tools that only read, and suggests at most three sources, questions and investigations,
each with its reason and the records it read. It claims nothing is true, recommends no action and
ranks nothing. `GET /staff/research/{run}` answers `Running`, then `Done` with the suggestions, or
`Failed`. Each suggested investigation is recorded as an investigation in the state suggested, and
is labelled `suggested by AI, not opened` everywhere until a representative opens it. Attaching a
task or an action to one still suggested is refused with `409` and
`investigation.suggested.not-approved`.

## Open an investigation

```bash
curl -s localhost:9000/staff/issues/$ISSUE/investigations -H "Authorization: Bearer $REP" \
  -H 'Content-Type: application/json' -d '{"question":"Why was Route 14 withdrawn?","fromSuggestion":"inv-01M5…"}'
```

```json
{ "id": "inv-01M57PMJ8DBVC9A2H3AM81SE00", "issue": "iss-01M57P…", "question": "Why was Route 14 withdrawn?", "state": "under investigation", "records": [], "tasks": [] }
```

`fromSuggestion` is optional and names the suggested investigation this one approves. A task has a
text, an owner and a due date:

```bash
curl -s localhost:9000/staff/investigations/$INV/tasks -H "Authorization: Bearer $REP" \
  -H 'Content-Type: application/json' -d '{"text":"Ask the operator for the boarding records.","owner":"Transport officer","due":"2026-11-07"}'
```

`GET /staff/investigations/{investigation}` answers one with its tasks and records; the public page
shows open investigations with their state label and no task text.

## Record an action

```bash
curl -s localhost:9000/staff/issues/$ISSUE/actions -H "Authorization: Bearer $REP" -H 'Content-Type: application/json' \
  -d '{"investigation":"'$INV'","authority":"Northfield Council transport committee (illustrative)","owner":"Transport officer",
       "date":"2026-10-18","rationale":"The two counts disagree.","state":"proposed","publicSummary":"A recount of Route 14'"'"'s pupils is proposed."}'
```

```json
{ "id": "act-01M57PP1PCTRN2ZEP0TEXV9TME", "state": "proposed, not started", "authority": "Northfield Council transport committee (illustrative)",
  "owner": "Transport officer", "date": "2026-10-18", "summary": "A recount of Route 14's pupils is proposed.", "rationale": "The two counts disagree.",
  "recordedBy": "a representative", "recordedAt": "2026-10-18T14:29:36.588912Z", "completed": false, "investigation": "inv-01M57PMJ8DBVC9A2H3AM81SE00" }
```

Every field is owed to a reader. One missing is refused with `422` and `action.fields.required`,
naming it; the state is sent by its wire name, `proposed`, `in-progress` or `completed`, and an
unknown one is refused under the same rule naming the three. A date after today is refused with
`action.date.future`, and one before the issue opened with `action.date.before-issue`. The authority
is the office or mandate the action is taken under, copied from the representative's staff account
when the body leaves it out.

`POST /staff/actions/{action}/state` moves an action forward; a move from completed to an earlier
state is refused with `409` and `action.state.backwards`. A proposed action is never shown as done:
`completed` is false on the public page until the state is completed.

## Decisions and outcomes

```bash
curl -s localhost:9000/staff/issues/$ISSUE/decisions -H "Authorization: Bearer $REP" -H 'Content-Type: application/json' \
  -d '{"approach":"Tender a replacement morning service.","authority":"…","alternatives":["A lit footpath"],"justification":"…"}'
curl -s localhost:9000/staff/actions/$ACT/outcomes -H "Authorization: Bearer $REP" -H 'Content-Type: application/json' \
  -d '{"measured":"The recount found 14 pupils a day.","limitations":"One week in October.","claimsResolved":false}'
```

A decision records the chosen approach, its authority, the alternatives and the justification. An
outcome records against an action what was measured and its limitations, and whether it resolved the
issue's claims, false unless said; an outcome overstates nothing. An outcome naming an action the
issue does not hold is refused with `404` and `outcome.action.not-held`.

## Publish an update

```bash
curl -s localhost:9000/staff/issues/$ISSUE/updates -H "Authorization: Bearer $REP" \
  -H 'Content-Type: application/json' -d '{"text":"A recount of Route 14'"'"'s pupils has been proposed; it is not yet done."}'
```

```json
{ "id": "dev-01M57PMJ8VFT82317A7K5M3AY1", "kind": "update-published", "ref": "dev-01M57PMJ8VFT82317A7K5M3AY1", "materiality": "material", "at": "…" }
```

A narrative update is text the representative wrote or reviewed. It is a development, like an
action, a decision, an outcome or accepted evidence, and reaches followers the same way;
[Following an issue](following.md). An update with no text is refused with `update.text.required`.

## The chronology

Each step adds an entry to the issue's chronology with one state label and its actor:

```text
under investigation  staff  an investigation was opened
action recorded      staff  an action was recorded
outcome verified     staff  an outcome was recorded
```

Every action, decision and outcome is public with who recorded it, by display name, and under what
authority. Records keep their recorder's name and authority as recorded, even after that person's
account is deprovisioned.

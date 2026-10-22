---
title: Priorities and trade-offs
description: Express a priority, a trade-off or a consideration on an issue as a participant, amend or withdraw it, and read the labelled aggregate and the representative's dashboard that result.
kind: guide
audiences: [participant, representative]
related: [concepts/opinions-and-evidence.md, build/evidence.md, build/investigations-and-actions.md, reference/configuration.md]
---

# Priorities and trade-offs

A participant says how much an issue matters to them and which trade-offs they would accept. Each
is an opinion, sealed like any personal value, and shown to anyone else only inside a count with its
label. One expression per participant per issue holds all of it.

## Express

```bash
curl -s localhost:9000/me/issues/$ISSUE/expression/priority -H "Authorization: Bearer $ME" \
  -H 'Content-Type: application/json' -d '{"level":4}'
curl -s localhost:9000/me/issues/$ISSUE/expression/trade-offs -H "Authorization: Bearer $ME" \
  -H 'Content-Type: application/json' -d '{"option":"pol-01M57P…","accepts":"a longer walk","rejects":"a route with no pavement"}'
curl -s localhost:9000/me/issues/$ISSUE/expression/considerations -H "Authorization: Bearer $ME" \
  -H 'Content-Type: application/json' -d '{"option":"pol-01M57P…","text":"The path floods in winter."}'
```

```json
{ "issue": "iss-01M57P…", "label": "opinion", "priority": 4, "tradeOffs": [ { "option": "pol-01M57P…", "accepts": "a longer walk", "rejects": "a route with no pavement" } ],
  "considerations": [ … ], "withdrawn": false,
  "notice": "This is your opinion. It is shown only inside counts of everyone who answered, never beside the evidence." }
```

A priority is a level from 1 to 5; outside it is refused with `422` and `expression.priority.range`.
A trade-off or a consideration names one of the issue's options; another is refused with `404` and
`expression.option.not-held`. Expressing a priority again replaces the earlier one. `GET /me/issues/{issue}/expression`
answers the participant's own and no one else's.

`POST /me/issues/{issue}/expression/withdraw` withdraws the whole expression, so it is no longer
counted. An amendment to a withdrawn expression is refused with `409` and `expression.withdrawn`;
expressing again afterwards is a new expression.

## The aggregate

The public issue page carries the aggregate, read from a view over expressions:

```json
{
  "participants": { "count": 7, "suppressed": false },
  "method": "Self-selected: participants on Voice who chose to say how much this issue matters to them or which trade-offs they would accept.",
  "window": { "from": "2026-10-18T14:28:47Z", "to": "2026-10-19T07:12:03Z" },
  "deduplication": "One expression per participant per issue. An amended priority replaces the earlier one; a withdrawn expression is not counted.",
  "caveat": "These are statements by the participants who chose to answer. They are opinions, not evidence, and say nothing about the proportion of all constituents who hold them.",
  "threshold": 5,
  "priorities": [ { "key": "4", "count": 5, "suppressed": false }, { "key": "5", "suppressed": true }, … ],
  "options": [ { "optionId": "pol-01M57P…", "cells": [ { "key": "accepts", "count": 6, "suppressed": false }, { "key": "rejects", "suppressed": true } ] } ]
}
```

A cell with fewer participants than `VOICE_SUPPRESSION_THRESHOLD`, five by default, is omitted and
marked `suppressed`; this is an answer, not an error. The view keys each row by a salted hash of the
participant id, for deduplication, and reads the priority level as a plain integer carried beside
the sealed value: the level alone is not personal, and the aggregate suppresses small cells. On
erasure the row goes with the deleted entity.

## The representative's dashboard

`GET /staff/issues/{issue}` answers the public page plus the contributions the representative's role
may see, the participant-derived measures with their caveats, the unresolved questions and the
contrary perspectives. Every measure is labelled participant-derived, and the page makes no claim
about the population. [Investigations and actions](investigations-and-actions.md) is what the
representative does next.

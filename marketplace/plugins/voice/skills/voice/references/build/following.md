# Following an issue

> Follow an issue as a participant, read the one update each development sends to the inbox, know what an update may and may not say, and stop following.

Source: https://docs.makethemlisten.uk/build/following/
Following is an explicit choice. A participant who reads an issue has not followed it; one who
follows it is told of each material development once, in their inbox, and can stop at any time.

## Follow

```bash
curl -s -X PUT localhost:9000/me/interests/$ISSUE -H "Authorization: Bearer $ME" \
  -H 'Content-Type: application/json' -d '{"delivery":"inbox-immediate"}'
```

```json
{ "issue": "iss-01M57P…", "title": "Rural school bus withdrawal", "delivery": "inbox-immediate", "since": "2026-10-18T14:28:47.997814Z", "active": true }
```

`inbox-immediate` is the one delivery: an update as each development is processed, into the inbox
inside Voice. A later channel would be a new value, never a change to this one; any other value is
refused with `422` and `interest.delivery.invalid`. The interest is recorded apart from the
delivery, so a later channel changes nothing a follower chose. `GET /me/interests` lists the
participant's own; `DELETE /me/interests/{issue}` stops following and answers `204`.

## What arrives

```bash
curl -s localhost:9000/me/inbox -H "Authorization: Bearer $ME"
```

```json
[ { "development": "dev-01M57PMJ8VFT82317A7K5M3AY1", "issue": "iss-01M57P…",
    "text": "A representative published an update on “Rural school bus withdrawal”: “A recount of Route 14's pupils has been proposed; it is not yet done.” Record: /public/issues/iss-01M57P…#dev-01M57PMJ8VFT82317A7K5M3AY1",
    "why": "You follow this issue.", "state": "Delivered", "at": "2026-10-18T14:28:48.1Z" } ]
```

An update says what happened, why it reached this participant, and links the record. Its wording is
built from the development's record by kind: an action with its summary, a decision, an outcome,
accepted evidence with its source, or a narrative update quoted as published. No model and no
reviewer are in that step, so an update cannot say more than the record says, and never says a
problem is solved unless the record does.

`POST /me/inbox/{development}/read` marks an entry read. `GET /me/inbox/stream` sends each new entry
as it is prepared, for an open interface; `after` on `GET /me/inbox` reads from a point.

## What does not arrive

- A development on an issue the participant does not follow.
- The same development twice. A notification's id is the participant and the development, so a
  second preparation addresses the same record and is refused by it, whether the cause is a retried
  delivery, a redelivered event or a restarted service.
- A minor development: a correction to an update's wording that changes no fact.
- Anything after the participant stops following, or after their erasure, which revokes every
  interest before the key is destroyed.

## How it is delivered

The `developments` consumer picks up each development from the issue's events and starts one
`update-delivery` workflow per development. The workflow reads the issue, decides materiality, reads
the issue's followers, renders the text, prepares one notification per follower whose interest is
active and older than the development, and delivers it; a failed delivery is recorded and the step
retried, without a duplicate. The consumer waits for the workflow's end, so a workflow a restart
interrupted is resumed by the redelivered event.

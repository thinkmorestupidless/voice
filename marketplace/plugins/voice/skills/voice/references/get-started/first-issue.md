# Your first issue

> Walk the loop over HTTP with curl, from a participant's session through a representative's action and a follower's update to an erasure and its receipt.

Source: https://docs.makethemlisten.uk/get-started/first-issue/
This page walks the loop against the service as [Run it on your machine](run-locally.md) leaves it,
with the seed pack loaded. Three people take part: a participant, a representative and an operator.
Every reply shown is the service's own, shortened where the page says so; identifiers differ on your
machine.

The conversation itself, where a participant talks and Voice proposes a concern, needs an Anthropic
API key, and is the one step this page describes without running. Everything else runs with no model.

## Staff tokens

The local realm gives a token per role. Keep each in a variable:

```bash
token() { curl -s -d grant_type=password -d client_id=voice-staff -d "username=$1" -d "password=local-$1" \
  localhost:8180/realms/voice-staff/protocol/openid-connect/token | jq -r .access_token; }
REP=$(token representative); OP=$(token operator); REV=$(token reviewer)
ISSUE=$(curl -s 'localhost:9000/public/issues' | jq -r '.[0].id')
```

## A participant arrives

A session needs the invite code and nothing else. The reply is the participant's pseudonymous id,
the recovery credential, shown this once, the session token and the two notices:

```bash
curl -s localhost:9000/me/sessions -H 'Content-Type: application/json' -d '{"invite":"pilot"}'
```

```json
{
  "participant": "prt-01M59GXQEBZPZHAR8CBN4P8TZR",
  "recoveryCredential": "prt-01M59GXQEBZPZHAR8CBN4P8TZR~<shown once>",
  "session": "<token>",
  "expiresAt": "2026-10-19T19:27:25.643027Z",
  "notice": { "privacy": "# How Voice treats what you tell it …", "ai": "**How AI is used.** …" }
}
```

Keep the session token as `ME`. The recovery credential is the only way back to these records after
the session expires; Voice keeps a hash of it and cannot show it again.

## The conversation, when a key is set

With `ANTHROPIC_API_KEY` in `.env.local`, a message is accepted at once with a turn id, and the
reply and any proposal appear on the conversation when the turn ends, a few seconds later:

```bash
CNV=$(curl -s -X POST localhost:9000/me/conversations -H "Authorization: Bearer $ME" | jq -r .id)
curl -s localhost:9000/me/conversations/$CNV/messages -H "Authorization: Bearer $ME" \
  -H 'Content-Type: application/json' \
  -d '{"text":"The Route 14 school bus stopped in March and my children now walk the ring road."}'
```

```json
{ "turn": "cnv-…:turn-1", "message": { "seq": 1, "author": "citizen", "at": "…" } }
```

`GET /me/conversations/$CNV/turns/cnv-…:turn-1` answers `Thinking` and then `Replied`, with the
reply and a proposal: a summary, its elements and the passage of the message each came from. The
participant edits, confirms or discards it; a confirmed proposal is a concern, and
[Sharing and review](../build/sharing.md) takes it from there. [Conversations and concerns](../build/conversations.md)
has every route.

## An opinion, and a follow

A participant says how much the issue matters to them. The reply labels it an opinion and says
where it will be shown:

```bash
curl -s localhost:9000/me/issues/$ISSUE/expression/priority -H "Authorization: Bearer $ME" \
  -H 'Content-Type: application/json' -d '{"level":4}'
```

```json
{ "issue": "iss-…", "label": "opinion", "priority": 4, "tradeOffs": [], "considerations": [], "withdrawn": false,
  "notice": "This is your opinion. It is shown only inside counts of everyone who answered …" }
```

Following is an explicit choice, with the inbox as the one delivery:

```bash
curl -s -X PUT localhost:9000/me/interests/$ISSUE -H "Authorization: Bearer $ME" \
  -H 'Content-Type: application/json' -d '{"delivery":"inbox-immediate"}'
```

```json
{ "issue": "iss-…", "title": "Rural school bus withdrawal", "delivery": "inbox-immediate", "since": "…", "active": true }
```

## Only a representative acts

A reviewer who tries to open an investigation is refused by name, and nothing changes:

```bash
curl -s localhost:9000/staff/issues/$ISSUE/investigations -H "Authorization: Bearer $REV" \
  -H 'Content-Type: application/json' -d '{"question":"Why was Route 14 withdrawn?"}'
```

```json
{ "rule": "investigation.not-representative", "status": 403, "error": "This needs the representative role.", "names": { "role": "representative" } }
```

The representative opens it, records an action against it, and moves the action on. An action's
state is sent by its wire name, `proposed`, `in-progress` or `completed`:

```bash
INV=$(curl -s localhost:9000/staff/issues/$ISSUE/investigations -H "Authorization: Bearer $REP" \
  -H 'Content-Type: application/json' -d '{"question":"Why was Route 14 withdrawn?"}' | jq -r .id)
ACT=$(curl -s localhost:9000/staff/issues/$ISSUE/actions -H "Authorization: Bearer $REP" \
  -H 'Content-Type: application/json' -d "{\"investigation\":\"$INV\",
  \"authority\":\"Northfield Council transport committee (illustrative)\",\"owner\":\"Transport officer\",
  \"date\":\"$(date +%F)\",\"rationale\":\"The two counts disagree.\",\"state\":\"proposed\",
  \"publicSummary\":\"A recount of Route 14's pupils is proposed.\"}" | jq -r .id)
curl -s localhost:9000/staff/actions/$ACT/state -H "Authorization: Bearer $REP" \
  -H 'Content-Type: application/json' -d '{"state":"in-progress"}' | jq '{state, completed, recordedBy}'
```

```json
{ "state": "in progress", "completed": false, "recordedBy": "a representative" }
```

The action is on the public page with its authority, owner, date and summary, and the chronology
gained an entry per step, each with one state label and its actor:

```bash
curl -s localhost:9000/public/issues/$ISSUE/chronology | jq -r '.[-3:][] | "\(.state)  \(.actor)  \(.note)"'
```

```text
under investigation  staff  an investigation was opened
staff-acknowledged  staff  an action was recorded
action recorded  staff  an action was recorded
```

## One update reaches the follower

A representative's narrative update is a development. Within a few seconds the delivery workflow
has prepared one inbox entry for every follower:

```bash
curl -s localhost:9000/staff/issues/$ISSUE/updates -H "Authorization: Bearer $REP" \
  -H 'Content-Type: application/json' -d '{"text":"A recount of Route 14'"'"'s pupils has been proposed; it is not yet done."}'
sleep 4
curl -s localhost:9000/me/inbox -H "Authorization: Bearer $ME" | jq '.[0] | {text, why, state}'
```

```json
{
  "text": "A representative published an update on “Rural school bus withdrawal”: “A recount of Route 14's pupils has been proposed; it is not yet done.” …",
  "why": "You follow this issue.",
  "state": "Delivered"
}
```

A second participant who does not follow has an empty inbox, and publishing the same development
again prepares nothing new. [Following an issue](../build/following.md) says what an update may and
may not say.

## What Voice holds, and erasing it

`GET /me/data` lists what Voice holds for this participant by class, what is public, and what
erasure would erase and keep. The request with the recovery credential is verified by it and starts
at once; without it, the request is held for an operator:

```bash
curl -s localhost:9000/me/data -H "Authorization: Bearer $ME" | jq '.held'
curl -s -X POST localhost:9000/me/erasure -H "Authorization: Bearer $ME" \
  -H 'Content-Type: application/json' -d '{"recoveryCredential":"<the credential shown at the start>"}' | jq '{state, cause}'
```

```json
[ { "name": "conversations", "count": 0 }, { "name": "expressions", "count": 1 }, { "name": "interests", "count": 1 }, { "name": "notifications", "count": 1 } ]
{ "state": "InProgress", "cause": "requested" }
```

A moment later every `/me` route for this session answers `410` with `participant.erased`. The
operator reads the receipt, which records the classes erased and their counts and none of the
content; a representative asking for it is refused:

```bash
curl -s localhost:9000/staff/erasures -H "Authorization: Bearer $OP" | jq '.[0].receipt'
```

```json
{
  "cause": "requested",
  "classes": [ { "name": "expressions", "count": 1 }, { "name": "interests", "count": 1 }, { "name": "notifications", "count": 1 }, { "name": "key", "count": 1 }, { "name": "recovery-credential", "count": 1 }, … ],
  "authorisedBy": "participant", "verifiedBy": "session", "at": "…", "completionVerified": true
}
```

The issue is unchanged: its evidence, the investigation, the action and the update are public, and
its counts say that no author was erased, because this participant had published nothing. What a
published de-identified text looks like after its author is erased is on
[Sealed fields and erasure](../concepts/sealing.md).

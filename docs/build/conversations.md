---
title: Conversations and concerns
description: Hold a private conversation one turn at a time, read a turn's result by polling or a stream, and edit, confirm or discard the proposal Voice makes, so it becomes a confirmed concern.
kind: guide
audiences: [participant]
related: [build/sessions.md, build/sharing.md, concepts/nothing-waits-for-a-model.md, concepts/opinions-and-evidence.md, reference/http-api.md]
---

# Conversations and concerns

A conversation is one participant's private exchange with Voice. Every message, reply and proposal
in it is sealed under the participant's key and shown to no one else. Each message is a turn that
runs off the request path, and the turn's result is read afterwards.

## Start a conversation

```bash
curl -s -X POST localhost:9000/me/conversations -H "Authorization: Bearer $ME"
```

```json
{ "id": "cnv-01M5…", "messages": [], "proposals": [], "confirmed": [] }
```

`GET /me/conversations` lists the participant's conversations, and `GET /me/conversations/{conversation}`
answers one with its messages, proposals and confirmed concern ids. A conversation that belongs to
another participant answers `403` and `conversation.not-owned`.

## Send a message

```bash
curl -s localhost:9000/me/conversations/$CNV/messages -H "Authorization: Bearer $ME" \
  -H 'Content-Type: application/json' \
  -d '{"text":"The Route 14 school bus stopped in March and my children now walk the ring road."}'
```

```json
{ "turn": "cnv-01M5…:turn-1", "message": { "seq": 1, "author": "citizen", "at": "…" } }
```

The reply is `202`: the message is recorded and the turn has started. A message with no text, or
more than 4,000 characters, is refused with `422` and `conversation.message.empty`. Text that
contains instructions aimed at the system is content: the model is told so, nothing is obeyed, and
no state changes beyond the message being recorded.

## Read the turn

Poll the turn until it is no longer thinking:

```bash
curl -s localhost:9000/me/conversations/$CNV/turns/cnv-01M5…:turn-1 -H "Authorization: Bearer $ME"
```

```json
{ "state": "Replied", "reply": "That sounds like a real change to the school run. Is it the morning walk, the afternoon, or both?", "proposal": null }
```

A turn ends in one of three states: `Thinking`, `Replied` with the reply and, when Voice could tell
what the problem is, a proposal, or `CouldNotInterpret` with a message for the participant. On each
turn Voice does exactly one of two things: asks one short clarifying question, which proposes no
position, cause or culprit and may be skipped, or proposes a structured concern.

Or hold the stream, which sends every message, proposal and turn as it is recorded:

```bash
curl -s -N 'localhost:9000/me/conversations/'$CNV'/stream?after=0' -H "Authorization: Bearer $ME"
```

```text
data: {"type":"message","data":{"seq":2,"author":"voice","at":"…"}}
data: {"type":"turn","data":{"turn":"cnv-01M5…:turn-1","state":"Replied"}}
```

The gateway closes a stream after fifteen seconds. Reconnect with `after` set to the last sequence
seen; nothing is missed, because every event is also on the conversation.

## The proposal

A proposal is Voice's structured reading of the concern, offered to the participant:

```json
{
  "id": "prp-01M5…",
  "summary": "The Route 14 school bus was withdrawn and children now walk along the ring road to school.",
  "elements": [
    { "kind": "Problem", "text": "the school bus was withdrawn", "sourceSpans": [ { "messageSeq": 1, "from": 4, "to": 41 } ] },
    { "kind": "Where", "text": "Route 14; the ring road", "sourceSpans": [ … ] },
    { "kind": "Impact", "text": "children walk the ring road", "sourceSpans": [ … ] }
  ],
  "confidence": "Medium",
  "suggestedIssues": [ { "issue": "iss-01M57P…", "title": "Rural school bus withdrawal" } ],
  "editedByCitizen": false
}
```

Each element names the passage of the participant's own message it came from, so the participant
can see what Voice read and where. The summary adds nothing the participant did not say. The
suggested issues are the existing issues the concern may join; a confirmation suggests, it never
joins one by itself.

## Edit, confirm or discard

```bash
curl -s localhost:9000/me/conversations/$CNV/proposals/$PRP/edit -H "Authorization: Bearer $ME" \
  -H 'Content-Type: application/json' -d '{"summary":"…","elements":[…]}'
curl -s -X POST localhost:9000/me/conversations/$CNV/proposals/$PRP/confirm -H "Authorization: Bearer $ME"
```

```json
{ "id": "cnc-01M5…", "conversation": "cnv-01M5…", "proposal": "prp-01M5…", "summary": "…", "elements": [ … ], "contributions": [] }
```

A confirmed proposal is a concern, in the participant's edited meaning: a statement by one person,
never a fact about the world. It is bound to the message sequence numbers it was extracted from, so
a later message does not change it, and a confirm that names a proposal made from messages the
participant has since edited is refused with `409` and `proposal.stale`. Confirming twice answers
`409` and `proposal.already-confirmed`; a discard removes the proposal from the conversation and
records nothing else.

`GET /me/concerns` lists the participant's concerns with the states of their contributions. A
concern is still private: nothing is shared until [Sharing and review](sharing.md).

## Without a model

With no `ANTHROPIC_API_KEY`, the message is recorded and the turn ends in `CouldNotInterpret`. The
test suites run every conversation against a recorded provider, which answers from fixtures matched
on the message's text; [Testing](testing.md) says how to record new ones.

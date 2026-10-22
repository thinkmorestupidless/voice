---
title: Erasure
description: See what Voice holds for a participant, request erasure with or without the recovery credential, verify a held request as the operator, read the receipt, and set the dormancy period.
kind: guide
audiences: [participant, operator]
related: [concepts/sealing.md, concepts/audiences.md, build/sessions.md, deploy/operate.md, reference/limitations.md]
---

# Erasure

A participant can ask for their personal data to be erased, and a participant who does not return
for the dormancy period is erased without asking. Both run the same workflow; the difference is how
the request is verified and what cause the retained public copies carry.

## What Voice holds

```bash
curl -s localhost:9000/me/data -H "Authorization: Bearer $ME"
```

```json
{
  "held": [ { "name": "conversations", "count": 1 }, { "name": "concerns", "count": 1 }, { "name": "contributions", "count": 1 },
            { "name": "expressions", "count": 1 }, { "name": "interests", "count": 1 }, { "name": "notifications", "count": 2 } ],
  "public": [ "de-identified contributions you published, without your name or any link to you",
              "contributions you published under your name or for representatives only are withdrawn" ],
  "retention": "Voice keeps your records while you take part. If you have no session for the dormancy period, your personal data is erased as if you had asked.",
  "erasure": { "state": "None", "erased": [ … ], "retained": [ … ], "why": "…", "remainsPublic": [ … ] }
}
```

The counts come from the participant's own index of records; the texts under `erased`, `retained`
and `why` are the same an erasure request answers with, so a participant sees what will go and what
will stay before asking.

## Request it

With the recovery credential, the request is verified by it and starts at once:

```bash
curl -s -X POST localhost:9000/me/erasure -H "Authorization: Bearer $ME" \
  -H 'Content-Type: application/json' -d '{"recoveryCredential":"prt-01M5…~…"}'
```

```json
{ "state": "InProgress", "cause": "requested", "requestedAt": "2026-10-18T14:28:51.162530Z",
  "erased": [ "your private conversations and the concerns you confirmed", "your drafts and contributions that were never published",
              "the priorities, trade-offs and considerations you expressed", "the issues you follow and the updates in your inbox",
              "your key, so nothing sealed for you can be read again", "your recovery credential, so this session cannot be recovered" ],
  "retained": [ "a de-identified contribution you published stays on its issue, with no link to you, marked as from an author who asked to be erased",
                "the issue's counts, which say how many authors were erased and why",
                "a receipt for the operator, recording which classes of data were erased and when, with none of their content" ],
  "why": "A published de-identified contribution became part of a public issue others rely on; it holds nothing that identifies you, and a reviewer checked it before it was published. A contribution flagged at review as possibly identifying is withdrawn instead.",
  "remainsPublic": [ … ] }
```

Without the credential, or with one that does not match, the request is held for an operator. The
reply is still `202`, with `state` `HeldForOperator` and `rule` `erasure.unverified`: an answer, not
an error. A second request answers `200` with `erasure.already-requested` and the record unchanged.
`GET /me/erasure` answers the status until completion; after it, every `/me` route answers `410`
and `participant.erased`.

## Verify a held request, as the operator

```bash
curl -s localhost:9000/staff/erasures?state=held-for-operator -H "Authorization: Bearer $OP"
curl -s localhost:9000/staff/erasures/$PARTICIPANT/verify -H "Authorization: Bearer $OP" \
  -H 'Content-Type: application/json' -d '{"evidence":"Verified in person at the pilot drop-in on 19 October."}'
```

The operator verifies that the request came from the participant it names, by whatever means the
pilot uses, and records how. The evidence text is read once and not kept. The workflow then moves
on. A principal without the operator role is refused with `403` and `erasure.not-operator`.

## The receipt

```bash
curl -s localhost:9000/staff/erasures -H "Authorization: Bearer $OP"
```

```json
[ { "participant": "prt-01M57PMJ6H3T2H6XZDGDX0D01Z", "cause": "requested", "state": "completed", "requestedAt": "2026-10-18T14:28:51.162530Z",
    "receipt": { "cause": "requested",
                 "classes": [ { "name": "conversations", "count": 0 }, { "name": "concerns", "count": 0 }, { "name": "contributions", "count": 0 },
                              { "name": "public-copies-withdrawn", "count": 0 }, { "name": "public-copies-retained", "count": 0 },
                              { "name": "expressions", "count": 1 }, { "name": "interests", "count": 1 }, { "name": "notifications", "count": 1 },
                              { "name": "key", "count": 1 }, { "name": "recovery-credential", "count": 1 } ],
                 "authorisedBy": "participant", "verifiedBy": "session", "at": "2026-10-18T14:28:51.162530Z", "completionVerified": true } } ]
```

A receipt holds the cause, the classes erased with their counts, who authorised it (`participant` or
`operator`), how it was verified (`session`, `operator` or, for dormancy, `not-required`), when, and
that the key was confirmed gone. It holds no content, by construction: a test walks every type a
receipt reaches and fails on a sealed value or free text. `state` filters the list:
`held-for-operator`, `in-progress` or `completed`.

## Dormancy

Every session start, recovery and renewal schedules a timer under the participant's name for
`VOICE_DORMANCY_PERIOD`, 180 days by default; scheduling again replaces it, so a returning participant
pushes it out. When it fires, the timed action asks the participant for its last session time: a
later session means nothing to do, which it reports as done; otherwise it starts the erasure with
cause `dormancy`, which needs no verification. A retained de-identified copy then carries
`authorErased: "dormancy"`, and the issue's counts say how many authors were erased for dormancy
and how many on request.

## What a page shows afterwards

A record of someone else's that still points at an erased field, such as an attributed copy on an
issue read before it was withdrawn, renders the field as redacted or unavailable. The interface
shows every sealed field through one component that renders the value, or that text, and nothing
else.

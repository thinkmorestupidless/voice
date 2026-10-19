---
title: Sharing and review
description: Approve the exact text and audience of a contribution, submit it for review, see what the reviewer sees and decides, join an issue, and withdraw a disclosure.
kind: guide
audiences: [participant, reviewer]
related: [build/conversations.md, build/evidence.md, concepts/the-loop.md, concepts/sealing.md, reference/refusals.md]
---

# Sharing and review

Nothing leaves a conversation unless the participant approves the exact words and the exact
audience. A reviewer then decides whether those words may enter the shared space. This page is both
sides: the participant's `/me` routes and the reviewer's `/staff/reviews`.

## The four modes

`GET /me/concerns/{concern}/sharing` answers the four modes with plain-language text, the proposed
derivative, the exact audience of each mode, and the flags Voice detected in the proposed text:

| Mode | Wire name | Who sees the text | Reviewed |
|---|---|---|---|
| private | `private` | the participant alone | no |
| de-identified | `de-identified` | everyone, with no name and no link to the author | yes |
| representative only | `representative-only` | reviewers and representatives | yes |
| publicly attributed | `public-attribution` | everyone, under a name the participant chooses | yes |

Private is offered first. A mode outside the four is refused with `422` and
`contribution.mode.invalid`.

## Approve the exact text

The participant sends the mode and the text as they will be shared. The derivative is theirs to
write or edit; Voice's proposed text is a starting point, never what is submitted unless they send
it:

```bash
curl -s localhost:9000/me/concerns/$CNC/contributions -H "Authorization: Bearer $ME" \
  -H 'Content-Type: application/json' \
  -d '{"mode":"de-identified","text":"Since the Route 14 bus was withdrawn, children on our side of the ring road walk to school along it."}'
```

```json
{ "id": "ctb-01M5…", "concern": "cnc-01M5…", "mode": "de-identified", "state": "approved by you", "flags": [], "issue": null }
```

The reply holds the text exactly as sent: that is the approval, recorded as a disclosure grant with
its scope, audience, purpose and time. Then the participant submits it:

```bash
curl -s -X POST localhost:9000/me/contributions/$CTB/submit -H "Authorization: Bearer $ME"
```

```json
{ "id": "ctb-01M5…", "state": "under moderation", "flags": ["Minor"] }
```

A private contribution is kept private and never reviewed; submitting it answers `409` and
`contribution.private`. A submission whose text differs from the approved one is refused with
`contribution.text.not-approved`. On submission the review workflow runs the privacy detectors and
the `privacy-flagging` agent and records the flags: `Distinctive`, `Health`, `Minor` or
`SmallGroup`, each with a reason that never quotes the text.

## What the reviewer sees

```bash
curl -s localhost:9000/staff/reviews -H "Authorization: Bearer $REV"
```

```json
[ { "contribution": "ctb-01M5…", "text": "Since the Route 14 bus was withdrawn, …", "mode": "de-identified", "audience": "everyone, with no name",
    "flags": ["Minor"], "suggestedIssues": [ { "issue": "iss-01M57P…", "title": "Rural school bus withdrawal" } ], "ageSeconds": 41 } ]
```

The reviewer sees the derivative, its mode and audience, the flags and the suggested issues, and
never the conversation it came from. They decide one of three things:

```bash
curl -s localhost:9000/staff/reviews/$CTB/approve -H "Authorization: Bearer $REV" \
  -H 'Content-Type: application/json' -d '{"issue":"iss-01M57P…"}'
curl -s localhost:9000/staff/reviews/$CTB/approve -H "Authorization: Bearer $REV" \
  -H 'Content-Type: application/json' -d '{"new":{"title":"…","summary":"…"}}'
curl -s localhost:9000/staff/reviews/$CTB/reject -H "Authorization: Bearer $REV" \
  -H 'Content-Type: application/json' -d '{"reason":"The street name identifies the household."}'
```

Approval into an existing issue or a new one publishes the derivative: a de-identified copy in
plaintext on the issue, an attributed or restricted copy sealed under the author's key. A rejection
carries a reason, which the participant sees; a rejection with none is refused with
`contribution.reason.required`. A decision from a principal without the reviewer role is refused with
`403` and `review.not-reviewer`.

## Enhanced review

A flagged contribution cannot be approved for public visibility until an enhanced review is recorded,
as a separate step with a reason:

```bash
curl -s localhost:9000/staff/reviews/$CTB/enhanced -H "Authorization: Bearer $REV" \
  -H 'Content-Type: application/json' -d '{"reason":"The child is not named and the road serves many households."}'
```

Approving a flagged contribution without it is refused with `409` and
`contribution.second-step.required`; sending both in one request is refused with
`contribution.second-step.same-click`. The two steps may be the same reviewer, and each is recorded
with who and when.

## The participant's side

`GET /me/contributions/{contribution}` answers the state, the review's outcome and reason, the
suggested issue and the association. States, as the participant sees them: `draft`,
`approved by you`, `under moderation`, `published`, `not published`, `withdrawn`.

When a published contribution was suggested an issue, the participant accepts it or asks for a new
one; the association is kept apart from the text and can be changed under review:

```bash
curl -s localhost:9000/me/contributions/$CTB/issue -H "Authorization: Bearer $ME" \
  -H 'Content-Type: application/json' -d '{"accept":"iss-01M57P…"}'
```

## Withdraw

```bash
curl -s -X POST localhost:9000/me/contributions/$CTB/withdraw -H "Authorization: Bearer $ME"
```

A withdrawn disclosure leaves its audience and the counts: the copy is dropped from the issue's
state, its page and the search index, and the chronology shows a withdrawal with no text. Withdrawn
is terminal; any later command but a read answers `409` and `contribution.withdrawn`.

## What a reader sees

A published de-identified copy appears on the issue with its text, `publishedAt` and a public
reference `pub-…`, and nothing that links it to its author. Public search snippets are built from
the public view's columns only. No public route ever returns a private field, which the suites
check on every public route.

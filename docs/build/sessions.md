---
title: Sessions
description: Start a participant's session with an invite code, keep the recovery credential, renew and recover a session, and read the privacy and AI notices Voice shows before the first message.
kind: guide
audiences: [participant]
related: [concepts/audiences.md, build/conversations.md, build/erasure.md, reference/http-api.md, reference/refusals.md]
---

# Sessions

A participant is pseudonymous. A session starts with the pilot's invite code and nothing about who
the person is, and the only way back to its records is the recovery credential shown once at the
start. This page is the `/me/sessions` routes and the token every other `/me` route carries.

## Start a session

```bash
curl -s localhost:9000/me/sessions -H 'Content-Type: application/json' -d '{"invite":"pilot"}'
```

```json
{
  "participant": "prt-01M59GXQEBZPZHAR8CBN4P8TZR",
  "recoveryCredential": "prt-01M59GXQEBZPZHAR8CBN4P8TZR~<32 random bytes, base64url>",
  "session": "<token>",
  "expiresAt": "2026-10-19T19:27:25.643027Z",
  "notice": { "privacy": "…", "ai": "…" }
}
```

The invite code is one of `VOICE_INVITE_CODES`. It is checked at the edge and never stored; a session
started with no code, or one not among the pilot's, is refused with `403` and `session.invite.invalid`.
The reply is `201`, and the participant's key is created in the secret store at the same time, so
every personal value this participant writes can be sealed from the first message.

## The session token

Every `/me` route takes `Authorization: Bearer <session>`. The token is
`base64url(participantId "." expiresAt) "." base64url(hmac-sha256)` under `VOICE_SESSION_KEY`,
valid for `VOICE_SESSION_HOURS`, twelve by default, and verified in the endpoint with no I/O. It is
not a JWT and never reaches an OIDC issuer. A route called without one answers `401` and
`session.required`.

`POST /me/sessions/renew` with an empty body answers a fresh token and expiry for the same
participant. Starting, recovering and renewing a session each reset the participant's dormancy timer.

## The recovery credential

The credential is the one secret that lets a participant return. It begins with the participant's
id, so recovery needs no lookup, and Voice keeps only its hash. It is sent to the two session routes
and nowhere else, never as a bearer header.

```bash
curl -s localhost:9000/me/sessions/recover -H 'Content-Type: application/json' \
  -d '{"recoveryCredential":"prt-01M59GXQEBZPZHAR8CBN4P8TZR~…"}'
```

```json
{ "participant": "prt-01M59GXQEBZPZHAR8CBN4P8TZR", "recoveryCredential": "prt-…~<a fresh one>", "session": "<token>", "expiresAt": "…" }
```

Presenting the credential rotates it: the reply carries a fresh one, and the old is no longer
accepted. A credential that does not match answers `401` and `participant.credential.invalid`,
which says nothing about whether the participant exists. A participant without their credential
starts afresh, with a new id and nothing of the old records; Voice cannot recover it for them.

## The notices

`GET /me/notice` answers the privacy notice and the AI notice as Markdown, and the session reply
carries both, so an interface shows them before the first message. The privacy notice says who the
participant is to Voice, what stays private, what they can share and how, how AI is used, and what
erasure does; the AI notice is its one section on the model, shown on its own in the conversation.

## After erasure

Once a participant's erasure has completed, every `/me` route for their token, and any later
recovery, answers `410` and `participant.erased`. [Erasure](erasure.md).

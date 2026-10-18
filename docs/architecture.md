# Architecture

voice is one ankka service, `voice`, written in Scala 3, and one web-hosted interface, `voice-web`,
which mounts the service at `/api`. The reasoning service, `reasoning` from ankka-reasoning, runs
beside them and holds the evidence graph. voice calls it as a service caller.

## Endpoints

Three HTTP endpoints, one per audience, each with its own ACL:

| Endpoint | Path | ACL |
|---|---|---|
| public | `/public` | `AllowAll`; no route creates anything, no response holds a sealed value or a participant id |
| citizen | `/me` | a session token minted by voice, signed with `VOICE_SESSION_KEY`; the participant is the token's subject, never a value in the request |
| staff | `/staff` | an OIDC token from the `staff` issuer, with the role each route needs |

## Components

Registration is explicit in `voice.VoiceService.own`; `InventorySuite` holds it equal to the data
model.

- **Participation**: event sourced entities `participant`, `conversation`, `concern`; workflow
  `conversation-turn`, which asks the `concern-extraction` agent for a reply and a proposal, off the
  request path.
- **Disclosure**: event sourced entity `contribution`; workflow `disclosure-review`, which runs the
  pattern detectors and the `privacy-flagging` agent, waits for a reviewer, and publishes; key value
  entity `issue-link`, the only map from a public copy back to its contribution.
- **Issues**: event sourced entity `issue`, which holds public copies, evidence links, options and
  the chronology; views `issue-rows` and `public-search`.
- **Deliberation**: event sourced entities `policy-option` and `expression`; view `issue-aggregates`.
- **Accountability**: event sourced entities `investigation`, `action`, `decision`, `outcome`;
  workflow `research` with the `research` agent; the access log.
- **Feedback**: key value entities `interest` and `issue-followers`; event sourced entity
  `notification`; consumer `developments` over issue events, which starts one `update-delivery`
  workflow per development.
- **Erasure**: workflow `erasure`, keyed by participant; key value entity `participant-records`,
  the index of every record a participant holds; view `erasure-receipts`; timed action
  `dormancy-check`.

## The sealing layer

Every personal value is stored as `Sealed[A]`: encrypted with AES-GCM under a key per participant,
bound to the participant and a purpose string. Keys live in the service's secret store under
`participant/<id>/key`. Values are sealed at the edge before a command is sent and opened at the
edge after a record is read, so an entity or a view never sees plaintext. Erasure destroys the key;
from then on every sealed value of that participant opens as `redacted or unavailable`.

The one citizen text held in plaintext is a de-identified public copy, because it is public by the
participant's and the reviewer's decision and is what remains after erasure.

## The asynchronous turn

A message is accepted with `202` and a turn id. The `conversation-turn` workflow calls the model,
records the reply and any proposal on the conversation, and ends. The interface polls the turn. A
model call never runs inside an HTTP request, so the gateway's request timeout never cuts a reply
short.

## Reasoning as a service caller

voice writes questions, hypotheses, claims and evidence to `reasoning` and reads answers from it,
through ankka's service client, as `service:voice/voice`. An issue holds the references and voice's
own annotations: scope, authority and limitations. Without the optional graph tier, `reasoning`
answers from its own records.

## Time

Every command carries a provenance: the actor, the time read once at the edge, the route and a
correlation id. Workflows and timed actions take time from their inputs or the timer's clock, so a
test that moves the clock moves the whole service.

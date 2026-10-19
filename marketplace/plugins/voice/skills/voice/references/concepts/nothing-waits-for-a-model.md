# Nothing waits for a model

> Why a conversation turn and a research run are workflows answered with 202, how a browser polls or streams the result, and how the gateway's fifteen-second bound shapes every route.

Source: https://voice.ankka.cloud/concepts/nothing-waits-for-a-model/
The platform's gateway ends every response, streams included, at fifteen seconds. A model call
takes a few seconds on a good day and longer on a bad one. So no route that a browser waits on ever
calls the model: the route records what it was given, starts a workflow, and answers `202` with an
id the caller reads later.

## A turn

`POST /me/conversations/{conversation}/messages` records the participant's message on the
conversation and starts the `conversation-turn` workflow, whose id is the conversation and the
message's sequence number. Its one step opens the sealed transcript for this call, hands it to the
`concern-extraction` agent, and records the reply and any proposal on the conversation as events.
The reply is `{ "turn": "cnv-…:turn-3", "message": … }`.

The caller then either polls `GET /me/conversations/{conversation}/turns/{turn}`, which answers
`Thinking`, `Replied` with the reply and proposal, or `CouldNotInterpret`, or holds
`GET /me/conversations/{conversation}/stream`, a server-sent event stream of messages, proposals
and turns as they are recorded. The gateway closes the stream at its bound; the interface reconnects
with `after` set to the last sequence it saw, and misses nothing, because every event is also on
the conversation.

The agent has no memory of its own. The transcript lives on the conversation, sealed, and is handed
to the model per turn, so the model holds nothing between calls and erasure has one place to act.

## A research run

`POST /staff/issues/{issue}/research` starts the `research` workflow and answers `202` with a run
id. Its one step calls the `research-assistant` agent with read-only tools over the issue, its
evidence and the reasoning service, and records the suggestions: sources, questions and
investigations, each with the agent's reason and the records it read. A suggested investigation is
recorded as an investigation in the state suggested, labelled so everywhere, until a representative
opens it. `GET /staff/research/{run}` answers the run's state and, when done, the suggestions.

## A review

`disclosure-review` is a workflow too, keyed by the contribution. Its first step runs the
deterministic privacy detectors and then the `privacy-flagging` agent, and records the flags. Then
it pauses, waiting for a reviewer's command, and for a second one when flagged. The reviewer's
routes answer at once; the publication they cause happens in the workflow.

## A delivery

A development on an issue is picked up by the `developments` consumer, which starts one
`update-delivery` workflow per development and waits for its end. The workflow renders the update
from the record, with no model in the step, and prepares one notification per follower. A browser
holds `GET /me/inbox/stream` or polls `GET /me/inbox`.

## Retries

A workflow step that fails is retried at once, with no backoff, a bounded number of times, and then
the workflow records the failure. A step that waits for something outside Voice, a reviewer or an
operator, pauses instead, and a command resumes it.

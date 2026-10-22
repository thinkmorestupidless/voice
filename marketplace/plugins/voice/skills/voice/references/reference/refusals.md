# Refusals

> Look up every rule a Voice command can break, by name, with its status, when it is broken and the identifiers it names, and the rules that are answers or build-time checks rather than errors.

Source: https://docs.makethemlisten.uk/reference/refusals/
Every rule a Voice command can break, named. A refusal is an `HttpProblem` whose body is
`{ "rule": "<name>", "status": <n>, "error": "<sentence>", "names": { … } }`, the same shape the
reasoning service uses, so one client reads both. The `names` map carries the identifiers the rule is
about and never a personal value. A client reads a refusal by its `rule`, not its status alone: a
status passes for the wrong refusal.

Rules live in the entity or workflow that owns them; the endpoint maps the error to the status. Role
rules are checked in the endpoint from the authenticated principal, because an entity does not know
its caller. The domain module lists every rule in `voice.domain.Refusal`, and a test fails on a rule
in this table that is not there, or there and not here.

## Participation

| Rule | Status | Broken when | Names |
|---|---|---|---|
| `participant.erased` | 410 | any command but a query reaches a participant whose erasure completed | participant |
| `session.invite.invalid` | 403 | a session is started with no invite code, or one not among the pilot's codes | none |
| `participant.credential.invalid` | 401 | a recovery credential does not match the participant's hash | none |
| `conversation.not-owned` | 403 | a session acts on a conversation that belongs to another participant | conversation |
| `conversation.message.empty` | 422 | a message has no text, or exceeds 4,000 characters | conversation |
| `proposal.not-held` | 404 | a confirm or edit names a proposal the conversation does not hold | conversation, proposal |
| `proposal.already-confirmed` | 409 | a proposal is confirmed a second time | proposal, concern |
| `proposal.stale` | 409 | a confirm names a proposal extracted from messages the participant has since edited | proposal |

## Disclosure

| Rule | Status | Broken when | Names |
|---|---|---|---|
| `contribution.mode.invalid` | 422 | a mode outside the four is sent | contribution |
| `contribution.not-owned` | 403 | a session acts on a contribution that belongs to another participant | contribution |
| `contribution.state.invalid` | 409 | a contribution is re-approved while under moderation or published, or associated with an issue once published | contribution, state |
| `contribution.text.not-approved` | 409 | a submission for review carries no approval with the exact text being submitted | contribution |
| `contribution.private` | 409 | a private contribution is submitted for review or published | contribution |
| `contribution.not-under-review` | 409 | a reviewer decision arrives for a contribution not under moderation | contribution |
| `contribution.second-step.required` | 409 | a flagged contribution is approved for public visibility with no enhanced review recorded | contribution, flags |
| `contribution.second-step.same-click` | 409 | the enhanced review and the approval arrive as one request | contribution |
| `contribution.reason.required` | 422 | a rejection or an enhanced review carries no reason | contribution |
| `contribution.withdrawn` | 409 | any command but a query reaches a withdrawn contribution | contribution |
| `contribution.issue.not-held` | 404 | an association names an issue that does not exist | contribution, issue |
| `review.not-reviewer` | 403 | a principal without the reviewer role decides a review | principal role |

## Issues and evidence

| Rule | Status | Broken when | Names |
|---|---|---|---|
| `issue.not-held` | 404 | a route names an issue that does not exist, or one the caller may not see | issue |
| `issue.contribution.duplicate` | 409 | the same public reference is published twice; the issue answers 200 and changes nothing | issue, pub |
| `issue.question.duplicate` | 409 | the same question reference is linked twice; idempotent, 200 | issue, question |
| `evidence.state.invalid` | 409 | an evidence link moves from rejected to accepted, or from superseded to anything | issue, evidence, from, to |
| `evidence.reference.unresolved` | 422 | a link names a reasoning id the reasoning service does not hold | kind, id |
| `evidence.not-reviewer` | 403 | a principal without the reviewer role accepts, rejects or supersedes evidence | principal role |
| `review-prompt.not-open` | 409 | a review prompt is resolved twice; the issue answers 200 and changes nothing | issue, prompt |
| `option.endorsement` | 422 | a policy option body carries a recommendation or ranking field | option |
| `question.unsupported` | 200, body `{ "abstained": true }` | a question has no accepted, unwithdrawn, unsuperseded claim; an answer, not an error | question |

## Deliberation

| Rule | Status | Broken when | Names |
|---|---|---|---|
| `expression.priority.range` | 422 | a priority level is outside 1 to 5 | issue |
| `expression.option.not-held` | 404 | a trade-off or consideration names an option the issue does not offer | issue, option |
| `expression.withdrawn` | 409 | an amendment reaches a withdrawn expression; expressing again is a new command | issue |
| `aggregate.suppressed` | 200, cell omitted with `"suppressed": true` | a cell is under the threshold; an answer, not an error | issue, cell |

## Investigation and action

| Rule | Status | Broken when | Names |
|---|---|---|---|
| `investigation.not-representative` | 403 | a principal without the representative role opens an investigation, records an action, a decision or an outcome, or publishes an update | principal role |
| `investigation.suggested.not-approved` | 409 | a task or action is attached to an investigation still suggested | investigation |
| `action.date.future` | 422 | an action is dated after today | action, date |
| `action.date.before-issue` | 422 | an action is dated before the issue opened | action, date, issue |
| `action.fields.required` | 422 | authority, owner, rationale, public summary or state is missing, or the state is not `proposed`, `in-progress` or `completed` | action, missing |
| `action.state.backwards` | 409 | an action moves from completed to an earlier state | action |
| `outcome.action.not-held` | 404 | an outcome names an action that does not exist on the issue | issue, action |
| `update.text.required` | 422 | a narrative update is published with no text | issue |
| `update.correction.not-held` | 404 | a correction names an update the issue does not hold | issue, development |
| `staff.inactive` | 403 | a deprovisioned staff account sends any staff command | principal |
| `staff.not-operator` | 403 | a principal without the operator role provisions or deprovisions staff | principal role |

## Interests and notifications

| Rule | Status | Broken when | Names |
|---|---|---|---|
| `interest.delivery.invalid` | 422 | a delivery other than `inbox-immediate` is sent | none |
| `interest.not-owned` | 403 | a session changes another participant's interest | none |
| `notification.duplicate` | 200, unchanged | a second preparation for the same participant and development arrives; the entity answers the first | development |
| `notification.not-owned` | 404 | a session reads another participant's inbox entry | none |
| `development.stale` | retry | the update workflow finds the follower view behind the development's time; the step retries, then fails over to a step that reads interests by id | development |

## Erasure

| Rule | Status | Broken when | Names |
|---|---|---|---|
| `erasure.unverified` | 202, state `HeldForOperator` | a request arrives from a session with no recovery credential; an answer, not an error | participant |
| `erasure.already-requested` | 200, unchanged | a second request for the same participant, or a dormancy firing for a participant whose erasure is already running or done | participant |
| `dormancy.reset` | done | the dormancy timer fires for a participant with a later session; the timed action has nothing to do and says so, which is success | participant |
| `erasure.not-operator` | 403 | a principal without the operator role verifies a held request or reads a receipt | principal role |
| `erasure.receipt.personal` | build time | a receipt type gains a sealed or free-text field; a test fails | none |

## Public read interfaces

| Rule | Status | Broken when | Names |
|---|---|---|---|
| `public.not-found` | 404 | a public route is asked for a private, restricted, withdrawn or unpublished record; the same answer as for a record that never existed, so existence leaks nothing | none |
| `public.search.filter` | build time | a public view query reads a column the classification table marks personal, restricted or sensitive; a test fails | view, column |
| `sealed.in-public-body` | build time | a public response type contains a sealed field or a participant id; a test fails | type |

## Cross-cutting

| Rule | Status | Broken when | Names |
|---|---|---|---|
| `role.required` | 401 | a staff route is called with no accepted token | none |
| `session.required` | 401 | a citizen route is called with no session token | none |
| `content.instructions` | none | participant text or a document contains instructions aimed at the system; nothing happens, the text is content. There is no refusal because there is no command to refuse; the test is that no state changes | none |

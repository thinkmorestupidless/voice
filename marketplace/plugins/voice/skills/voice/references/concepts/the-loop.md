# The loop

> The seven stages a concern passes through, from a private conversation to an erasure, who acts at each, what becomes visible, and the seven state labels a reader sees.

Source: https://voice.ankka.cloud/concepts/the-loop/
A concern moves through seven stages. At each, a different person acts, and a little more becomes
visible to a little wider audience. Nothing moves to the next stage by itself: a participant
confirms, approves and follows; a reviewer approves; a representative records.

```text
conversation → concern → contribution → issue → investigation → action → update
                                                                           ↓
                                                                        erasure
```

## Hear

A visitor becomes a participant with an invite code and no identity. Their conversation with Voice is
private: every message is sealed under their own key, and it is handed to the model one turn at a
time to produce a reply and, when the problem is clear, a proposal. A proposal is Voice's structured
reading of the concern: a one-paragraph summary, its elements (the problem, who, where, when, the
impact, the wish), the passage of the participant's own words each came from, and a confidence. The
participant edits it, confirms it or discards it. A confirmed proposal is a concern: a statement by
one person about what they experience or value, never a fact about the world.

## Share

A concern leaves the conversation only as a contribution the participant approved: the exact text,
and one of four sharing modes. Private stays with the participant and is never reviewed.
De-identified, representative only and publicly attributed go to a reviewer, who sees the text, its
mode and the flags the privacy check raised, and approves it into an issue or rejects it with a
reason. A flagged text needs a second, separately recorded review step before it can be public.
The participant can withdraw the disclosure afterwards; the text leaves its audience and the counts.
[Sharing and review](../build/sharing.md).

## Understand

An issue is the public topic approved contributions join. A reviewer links questions, each with
hypotheses that compete to answer it, and evidence from cited sources, each with a scope, an
authority and limitations, and states claims from that evidence. A reader asks why a claim is
supported and gets its evidence and sources, and the claims that contradict it on the same
hypothesis. A question with no accepted evidence gets an abstention: the records do not support an
answer. [Evidence and the public issue](../build/evidence.md).

## Deliberate

A participant expresses a priority, a trade-off or a consideration on an issue. These are opinions,
sealed like any personal value, and shown only inside counts labelled with their sample, method and
window, with cells under the suppression threshold hidden. They never appear beside evidence.
[Opinions apart from evidence](opinions-and-evidence.md).

## Act

A representative reads the issue with the contributions their role may see and the participant
counts with their caveats, asks the research agent for suggestions, which stay labelled suggested
until a representative opens one, opens an investigation, and records an action with its authority,
owner, date, rationale and a public summary. A proposed action is never shown as done. Decisions
and outcomes follow the same shape. [Investigations and actions](../build/investigations-and-actions.md).

## Return

A participant who follows an issue is told of each material development once: an action, a
decision, an outcome, accepted evidence or a published update. The update says what was recorded
and why the participant is told, and links the record; it is built from the record, with no model
and no reviewer in the step, so it cannot say more than the record says.
[Following an issue](../build/following.md).

## Erase

A participant asks for erasure, or is erased for dormancy. Their key is destroyed, their private
records deleted, their attributed and restricted copies withdrawn, and an unflagged de-identified
copy stays on the issue with no link to them, marked as from an author who was erased and why. A
receipt for the operator records the classes erased and none of the content.
[Sealed fields and erasure](sealing.md).

## The seven state labels

A reader sees one of seven states on every chronology entry and every statement about progress,
and the interface renders them from the server's vocabulary, never its own:

| Label | Set when |
|---|---|
| `submitted` | a contribution was submitted for review |
| `AI-interpreted` | a proposal was made by the model and not yet confirmed |
| `under moderation` | a contribution waits for a reviewer |
| `staff-acknowledged` | a reviewer or a representative recorded something on the issue |
| `under investigation` | a representative opened an investigation |
| `action recorded` | a representative recorded an action |
| `outcome verified` | an outcome was recorded against an action |

Every reply on an issue or chronology route carries the seven, once, in the `Voice-State-Labels`
header.

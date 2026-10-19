# Sealed fields and erasure

> How every personal value is sealed under a key per participant, opened only at the edge, classified by field, and made unreadable everywhere by destroying the key, and what stays.

Source: https://docs.makethemlisten.uk/concepts/sealing/
Voice promises that a participant's personal data can be erased while the public issue stays. The
promise is kept by one mechanism: every personal value is stored sealed under a key that belongs to
that participant alone, and erasure destroys the key.

## Sealed

A sealed field holds a value encrypted with AES-GCM under the participant's key, bound to the
participant and to a purpose string naming the field, so a ciphertext cannot be moved to another
field or another participant. It is stored as a JSON object with the subject and the ciphertext,
and prints as `Sealed(<subject>)`: a log, a dump or an error message shows which participant it
belongs to and nothing of what it says.

Keys live in the service's secret store, one per participant, encrypted under the service's own key.
Sealing and opening happen at the edge: an endpoint seals a value before it sends a command, and
opens a value after it reads a record. A workflow step, a consumer and a timed action do the same.
An entity and a view never do; they hold the sealed value and have no key, so a replay, a view
rebuild or a database dump holds ciphertext only.

## Classified

Every field of every event and state is classified, and a test fails on a text field with no
classification:

| Classification | Means | Example |
|---|---|---|
| public | anyone may read it | an issue's title, an action's authority |
| restricted | a role may read it | a contribution's state and flags, a receipt |
| personal | sealed, erased with its subject | a message, a confirmed summary, a notification's text |
| sensitive | personal, and shown only inside a labelled aggregate | a priority, a trade-off |

A public view's query is checked against the classification: a column marked personal, restricted
or sensitive cannot be read by public search.

## The one plaintext

A de-identified contribution a participant approved and a reviewer published is held in plaintext
on the issue. It is public by two people's decision, it holds nothing that identifies its author by
construction, and it is what the issue keeps as anonymous history. The map from its public reference
back to the contribution and the author is a separate restricted record, so the issue's own events
carry no participant id. A publicly attributed copy and a representative-only copy are sealed
under the author's key, because they carry a name or are not public.

## Erasure

Erasure runs as a workflow keyed by the participant, each step safe to run again:

1. **Verify.** A request with the participant's recovery credential is verified by it; without one,
   the request is held until an operator verifies it. A dormancy erasure needs no verification.
2. **Inventory.** Read the index of every record the participant holds.
3. **Revoke.** Stop every interest and remove the participant from every issue's followers, so
   nothing new is prepared for them.
4. **Withdraw.** Withdraw every attributed and restricted copy from its issue, and every
   contribution not yet published, so an open review cannot publish it later.
5. **Residue.** For each published de-identified copy: unflagged, it stays, marked as from an
   author who was erased, with the cause; flagged by the privacy check or a reviewer, it is withdrawn.
6. **Destroy the key.** From here every sealed value of the participant opens as erased, everywhere,
   including in any replay or rebuild.
7. **Purge.** Delete the conversation, concern, contribution, expression, interest and notification
   entities, erase and delete each issue link, then the index. A deleted entity's view rows go with it.
8. **Complete.** Record the completion on the participant and write the receipt.

After completion every `/me` route for that participant answers `410` with `participant.erased`.
The receipt the operator reads holds the cause, the classes erased with their counts, who authorised
and verified it, and that completion was verified: no content, by construction, which a test holds.

## What stays

| Record | After erasure |
|---|---|
| a de-identified public copy, unflagged | on the issue, with no link to its author, marked `authorErased` with the cause |
| the issue's counts | say how many authors were erased, and whether on request or for dormancy |
| investigations, actions, decisions, outcomes, options | unchanged: they were never personal |
| the receipt | restricted to the operator |
| an erased sealed field, where a record of someone else's still points at it | shown as redacted or unavailable |

## Two limits

- The secret store is a table in the service's own database. A database backup taken before an
  erasure still holds the destroyed key, so backups must expire within the erasure deadline.
- A public copy's text is plaintext in the issue's journal. Withdrawing a flagged copy removes it
  from the issue's state, its page and the search index, not from the journal.

Both are on [Limitations](../reference/limitations.md), and both go away when ankka's own personal
data erasure ships and `Sealed` gives way to its types and keyring.

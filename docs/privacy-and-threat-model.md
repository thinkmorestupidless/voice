# Privacy and threat model

## Before any real text

The demonstrator runs on synthetic data. Before any real resident's words reach the model provider,
read the provider's data terms for the account in use: retention, training use, and where requests
are processed. Conversations, proposals, derivatives under review and research prompts are sent to
the model. Nothing else is.

## Identity

- A participant has no name, email or verified identity. The invite code admits a pilot participant;
  it is checked at the edge and never stored, and it is not identity.
- The recovery credential is shown once and kept only as a hash. It names its participant, so
  recovery needs no lookup. Presenting it rotates it.
- Staff authenticate with the installation's OIDC issuer. Roles come from the token.

## What each audience can read

| Audience | Reads | Never reads |
|---|---|---|
| reader | public copies, evidence, accountable records, counts with their caveat | any participant id, any private text, any sealed value |
| participant | their own records, opened at the edge | anyone else's records |
| reviewer | the derivative under review and its flags | the conversation it came from |
| representative | restricted copies and the issue dashboard | conversations, concerns, unshared contributions |
| operator | staff accounts, the access log, erasure stages and receipts | any personal value: a receipt holds none |

Every staff read of a restricted record, and every refused staff request, is written to the access
log.

## Erasure

Erasure destroys the participant's key, deletes their private records, withdraws their attributed
and restricted copies, and keeps an unflagged de-identified copy with no link to them. A copy the
privacy check or a reviewer flagged is withdrawn instead.

Two limits hold today:

- ankka's secret store is a table in the service's own database. A database backup taken before an
  erasure still holds the destroyed key. Backups must expire within the erasure deadline.
- A public copy's text is plaintext in the issue's journal. Withdrawing a flagged copy removes it
  from the issue, its page and the search index, not from the journal.

## Logs

Logs carry ids, rules and timings, never message text, prompts or contact details.
`LogHygieneSuite` checks a full features run for any of the seed's personal text.

## Threats considered

| Threat | Mitigation |
|---|---|
| A reader links a public copy to its author | the copy's `pub-…` maps back only through the restricted `issue-link`; counts below the threshold are suppressed |
| A participant reads another's records | the participant is the token's subject; every route checks ownership |
| A prompt injection in a message | the conversation agent's reply is content, never an instruction; publication needs a reviewer |
| A distinctive detail survives de-identification | pattern detectors and the model flag it; a flagged text needs a second, separately recorded review step |
| An erased participant is notified | interests are revoked before the key goes; an update is prepared only for a participant whose key exists |

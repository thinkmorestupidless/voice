# Threat model

> What must be read before any real text reaches a model, how identity is kept out of Voice, what every log and receipt omits, and the threats considered with their mitigations.

Source: https://docs.makethemlisten.uk/concepts/threat-model/
Voice minimises first and protects second: it asks for no identity, seals what it must hold, and
puts a person between anything a participant said and anyone else seeing it. This page says what
that leaves to consider.

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
- Staff authenticate with the installation's OIDC issuer. Roles come from the token and are checked
  on every route.

## Logs

Logs carry identifiers, rule names and timings, never message text, prompts or contact details. A
sealed value prints as its subject. A test runs the whole feature suite with a capturing log
appender and fails on any of the seed's personal text in a log line or a trace name.

## Threats considered

| Threat | Mitigation |
|---|---|
| a reader links a public copy to its author | the copy's public reference maps back only through the restricted `issue-link`; attributed and restricted copies are sealed; counts below the threshold are suppressed |
| a participant reads another's records | the participant is the token's subject; every `/me` route checks ownership and answers `404` otherwise |
| a prompt injection in a message or a document | the conversation agent's reply is content, never an instruction; the research agent's tools only read; nothing is published without a reviewer |
| a distinctive detail survives de-identification | pattern detectors and the model flag it; a flagged text needs a second, separately recorded review step, and is withdrawn rather than retained on erasure |
| an erased participant is notified | interests are revoked before the key goes; an update is prepared only for a participant whose key exists |
| a staff member reads more than their role allows | each route states its role; every read of a restricted record and every refusal is in the access log the operator reads |
| a public route leaks existence | a private, restricted, withdrawn or unpublished record answers `404`, the same as one that never existed |
| a backup restores a destroyed key | not mitigated in Voice: backups must expire within the erasure deadline; see [Limitations](../reference/limitations.md) |

## Not considered

Denial of service and abuse at volume: the platform has no rate limiting at its gateway, and the
pilot's abuse control is the invite code. Verifying residency, or one person one response beyond
the invite codes, is out of scope for the pilot.

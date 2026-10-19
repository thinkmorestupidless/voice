# Operate the pilot

> Provision staff accounts, read the access log, handle erasure requests held for verification, set the dormancy period and threshold, know what the logs omit, and what a backup can undo.

Source: https://voice.ankka.cloud/deploy/operate/
The operator runs the pilot and is not entitled to read private content. Everything on this page
is reachable with the `operator` role, and none of it shows a participant's words.

## Staff accounts

Roles live in the identity provider; Voice holds only what the issuer cannot. After an account is
created in the realm, the operator writes its display name and, for a representative, the authority
they act under, which is copied onto every action they record:

```bash
curl -s -X PUT localhost:9000/staff/accounts/$SUBJECT -H "Authorization: Bearer $OP" -H 'Content-Type: application/json' \
  -d '{"displayName":"Councillor A. Example","authority":"Northfield Council transport committee (illustrative)","active":true}'
curl -s localhost:9000/staff/accounts -H "Authorization: Bearer $OP"
```

Setting `active` to false deprovisions the person: every staff command from them is refused with
`403` and `staff.inactive`, and the records they wrote keep their name and authority as recorded. A
principal without the operator role is refused with `staff.not-operator`.

## The access log

```bash
curl -s 'localhost:9000/staff/access-log?since=2026-10-18T00:00:00Z' -H "Authorization: Bearer $OP"
```

```json
[ { "principal": "representative@voice-staff", "route": "GET /staff/issues/{issue}", "record": "iss-01M57P…", "at": "2026-10-18T14:28:47Z" } ]
```

Every staff read of a restricted record, and every refused staff request, is an entry: the
principal, the route, the record's id and the time. The log is a view over one entity per day, so an
entry can follow its request by a moment.

## Erasure requests

A request made without the recovery credential waits for the operator. [Erasure](../build/erasure.md)
shows the list, the verification with its evidence, and the receipt. The evidence of how identity
was verified is read once and not kept, so the pilot keeps its own record of it if one is needed.

## Settings an operator changes

| Variable | Default | Effect of a change |
|---|---|---|
| `VOICE_DORMANCY_PERIOD` | `180d` | the next session start, recovery or renewal schedules the participant's timer for the new period; timers already scheduled keep theirs |
| `VOICE_SUPPRESSION_THRESHOLD` | `5` | takes effect on the next read of an aggregate |
| `VOICE_SESSION_HOURS` | `12` | tokens issued afterwards |
| `VOICE_INVITE_CODES` | | the next session start; running instances need `ankka services restart voice` to see a new secret value |

Every setting is on [Configuration](../reference/configuration.md).

## Logs

Logs carry identifiers, rule names and timings: a sealed value prints as its subject, and no message
text, prompt or contact detail is logged. `ankka services logs voice -p voice` reads them in a
deployment; locally they are on the console. A line with a `rule` is a refusal, which is normal
operation, not a failure.

## What a backup can undo

The secret store that holds participant keys is a table in the service's own database. A backup
taken before an erasure holds the destroyed key, and restoring it makes that participant's sealed
fields readable again. Backups must therefore expire within the erasure deadline the pilot promises,
and a restore must be followed by re-running every erasure completed since the backup, which the
receipts list. [Limitations](../reference/limitations.md) has the other bounds.

## The model provider

Conversations, proposals, derivatives under review and research prompts are sent to the model
provider, and nothing else is. Before any real resident's words reach it, the operator reads the
provider's data terms for the account in use: retention, training use and where requests are
processed. Removing `ANTHROPIC_API_KEY` stops every conversation turn and research run and nothing
else: reading, sharing already-approved text, following and erasure keep working.

# The interface

> The web app that fronts Voice, as a web-hosted ankka service that mounts /api to the service, with its pages for readers, participants and staff and how it holds tokens, streams and sealed fields.

Source: https://docs.makethemlisten.uk/build/the-interface/
`voice-web` is a React app built by Vite and served by a small `node:http` server, deployed as a
[web-hosted ankka service](https://docs.ankka.cloud/deploy/web-hosting/). Its descriptor mounts the
service `voice` at `/api`, so the browser's request for `/api/public/issues` reaches `voice` as
`/public/issues` at the interface's own address, with the `Authorization` header unchanged and
`X-Ankka-Caller: internet` set by the proxy. The interface calls these routes and nothing else; it
has no state and no database.

## Pages

| Path | Who | Shows |
|---|---|---|
| `/`, `/search` | a reader | search over issues, sources and options |
| `/issues/{issue}` | a reader | the issue: contributions, questions, evidence, claims, options, the aggregate, investigations, actions, decisions, outcomes, chronology |
| `/issues/{issue}/evidence/{evidence}`, `/issues/{issue}/claims/{claim}`, `/sources/{source}` | a reader | an evidence card, a claim's explanation, a source card |
| `/me` | a visitor | start a session with the invite code; the two notices |
| `/me/recover` | a participant | recover a session with the recovery credential |
| `/me/conversations`, `/me/conversations/{conversation}` | a participant | the conversations; one conversation with its turns, stream and proposals |
| `/me/concerns/{concern}/share` | a participant | the four modes, the exact text, submission |
| `/me/contributions/{contribution}` | a participant | a contribution's state, review outcome, issue suggestion, withdrawal |
| `/me/issues/{issue}/express` | a participant | priority, trade-offs, considerations |
| `/me/inbox` | a participant | updates, with the stream for an open tab |
| `/me/data` | a participant | what Voice holds, and the erasure request |
| `/staff` | staff | sign in with a token from the staff issuer |
| `/staff/queue`, `/staff/reviews`, `/staff/erasures` | a representative, a reviewer, an operator | the work queue, the review list, the erasure requests and receipts |
| `/staff/issues/{issue}`, `/staff/issues/{issue}/evidence`, `/staff/issues/{issue}/research`, `/staff/issues/{issue}/actions` | a representative, a reviewer | the dashboard, evidence curation, a research run, recording an action |
| `/staff/investigations/{investigation}` | a representative | an investigation and its tasks |

## Tokens

A participant's session token and a staff token are held in memory for the tab, never in a cookie
or in storage. The client sends the citizen's token to `/api/me` routes only, the staff token to
`/api/staff` routes only, and nothing to `/api/public`. A staff person pastes a token from the
installation's issuer on `/staff`; the role it carries is the issuer's, and every route checks it
again.

## Refusals and states

Every error is read as a refusal by its `rule`, never by its status alone, so a page can say what
was refused and what to do instead. The seven state labels are read from the `Voice-State-Labels`
header the issue and chronology routes send, and rendered from that vocabulary, not the app's own.
Every sealed field reaches a page through one component, which shows the value or the text
"redacted or unavailable" and nothing else.

## Streams

A conversation page holds the conversation's stream and an inbox page the inbox's. The gateway
closes a stream at fifteen seconds; the client reconnects, passing the last sequence it saw as its
own cursor, because the platform's stream sends no event ids.

## Run it

```bash
cd web && npm ci && npm run typecheck && npm test
just web
```

`just web` runs `ankka local web --service voice=http://127.0.0.1:9000 -- npm run dev`: the app on
`3000`, behind the same proxy a cluster runs, with `voice` named explicitly. The suite tests the
client, the paths, the session store, the label parsing and each page's model with an injected
fetch; the one thing it cannot see is the browser's own `fetch`, which the client binds to the global
on purpose. Build the image with `docker build -t voice-web:<version> web`.

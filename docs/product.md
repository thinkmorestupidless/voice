# What voice is

voice is a privacy-first civic participation service, built on ankka. A resident talks privately
about a local problem, checks what voice understood, decides exactly what to share and with whom,
and later hears what was done about it. Representatives see shared, source-backed issues and record
what they will do, with a date and an owner. Anyone can read and search the public issues without
an account.

This is a demonstrator for a pilot: synthetic data, invited participants, one area.

## The seven things it does

1. **Hear.** A participant starts a session with an invite code and no name, email or verified
   identity. They talk to voice about a concern. voice proposes a structured reading of what they
   said: the problem, where, when, who it affects and how. The participant edits it, confirms it or
   discards it. Nothing they said leaves the conversation at this point.
2. **Share.** For a confirmed concern, the participant writes or approves the exact text to share
   and picks one of four modes: private, de-identified, for representatives only, or publicly under
   a name they choose. A reviewer checks the text, with an automatic privacy check that flags
   distinctive circumstances, health, minors and small groups. An approved text joins a shared
   public issue.
3. **Understand.** An issue links evidence from cited sources, each with its state: candidate,
   accepted, rejected or superseded. Questions show their competing explanations, and what is not
   known is said.
4. **Deliberate.** Participants say how much an issue matters to them and which trade-offs they
   would accept. These are opinions, shown apart from evidence, as counts with their method and a
   caveat. A count below the suppression threshold is not shown.
5. **Act.** A representative opens an investigation, records a dated action with its authority,
   owner and rationale, and later a decision and an outcome. Each is public, with who recorded it.
6. **Return.** A participant who follows an issue gets one update in their inbox for each material
   development: what was recorded, why they are told, and links to the record. The update never
   says a problem is solved unless the record does.
7. **Erase.** A participant can ask for their personal data to be erased. Their key is destroyed, so
   every protected field becomes unreadable everywhere; their private records are deleted; a
   de-identified text they published stays on the issue with no link to them. A participant with no
   session for the dormancy period is erased the same way.

## Who uses it

| Audience | Reaches voice through | Can read |
|---|---|---|
| A reader | the public pages, no account | issues, evidence, actions, decisions, outcomes, counts |
| A participant | a session token from an invite code | their own conversations, concerns, contributions, expressions, interests and inbox |
| A reviewer | a staff token with the `reviewer` role | contributions under review, evidence proposals |
| A representative | a staff token with the `representative` role | restricted contributions, the issue dashboard, investigations |
| An operator | a staff token with the `operator` role | staff accounts, the access log, erasure requests and receipts |

## What it is not

It is not a poll, a petition or a vote. It does not say how many people agree, weight responses or
infer demographics. It sends nothing in a representative's name and decides nothing. See
`limitations.md` for the full list.

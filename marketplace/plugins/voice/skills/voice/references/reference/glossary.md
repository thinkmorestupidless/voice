# Glossary

> Look up what each word means in Voice, where every term has exactly one sense, and which everyday near-synonyms are not used for it.

Source: https://docs.makethemlisten.uk/reference/glossary/
The words this documentation, the interface, the API and the refusals use, each in exactly one
sense. Where a term has near-synonyms in everyday speech, the entry says which words are not used
for it, so a search for the wrong word still lands on the right one. The same list, in
`GLOSSARY.md` at the root of the repository, is what the living features are checked against, so a
term changes there first.

## People

**citizen.** A person who takes part in Voice without saying who they are: converses, confirms, shares, explores, follows, withdraws. The same person as a participant, seen from outside Voice. Not called user, member.

**participant.** A citizen as Voice knows them: a pseudonymous identity with a recovery credential and their own records, and nothing that says who they are. Not called user, account holder.

**visitor.** Someone who has opened Voice and has no session yet.

**representative.** An elected representative or the staff they authorise, with the role to open investigations and record actions, decisions, outcomes and updates. Not called MP, staff member.

**reviewer.** The person who decides whether a shared derivative may enter the shared space, and who accepts or rejects evidence. Not called moderator.

**operator.** The person who runs the pilot: provisions roles, verifies erasure requests and reads receipts. Not entitled to read private content. Not called administrator, admin.

**reader.** Anyone reading public records, with or without a session.

**person.** Any of the above when the role is given beside the word.

**role.** What a person may do: "citizen", "representative", "reviewer" or "operator".

**research agent.** The model-driven component that suggests sources, questions and investigations to a representative. It decides nothing. Not called bot, assistant.

**Voice.** The system, when it acts: asks, proposes, explains, abstains, prepares an update.

## Identity and session

**session.** A participant's current period of use, started with an invite code and resumed with the recovery credential.

**invite code.** The pilot's admission code, shared with the test cohort. It says nothing about who a person is.

**recovery credential.** The one secret that lets a participant return to their records. Shown once; Voice keeps only a hash of it. Not called password, login.

**identity.** Who a person is in the world: a name, an email, a verified attribute. Voice asks a citizen for none of it.

**privacy notice.** The plain-words explanation of what Voice keeps, shares and erases, shown before a participant's first message.

**AI notice.** The plain-words statement that the participant is talking to a model, shown before the first message.

## Conversation and concern

**conversation.** One participant's private exchange with Voice: their messages and Voice's replies. Not called chat, thread.

**message.** One thing a participant or Voice says in a conversation.

**concern.** Something a participant has confirmed they experience or value, in their own meaning. A statement by a person, not a fact about the world. Not called complaint, issue (for this).

**proposal.** Voice's structured reading of a concern, offered to the participant to edit or confirm: a summary, its elements, the passage each came from, a confidence and suggested related issues. Not called extraction, interpretation, concern card.

**structured concern.** The content of a proposal: the same thing seen as data rather than as an offer.

**summary.** The one-paragraph reading of a concern in a proposal, which the participant may edit.

**element.** One extracted part of a proposal: what the problem is, who, where, when, the impact, the wish.

**passage.** The exact words a thing came from: in a proposal, the participant's words; in evidence, the source's words. Not called span, excerpt, quote.

**confidence.** How sure Voice says it is of a proposal: low, medium or high.

**confirms.** Of a participant and a proposal: accepts it as their meaning, making it a concern. Not called approves (for this), accepts (for this).

**clarifying question.** A question Voice asks to understand a concern better. It proposes no position, cause or culprit.

**original.** The participant's own words as first said: the original statement of a concern. It is private and never shared. Not called raw text, source text.

## Disclosure

**contribution.** A statement a participant has approved for a sharing mode, with its derivative, its review and its association with an issue. Not called submission, post.

**derivative.** The text of a contribution as the participant approved it for sharing, apart from the original. Not called shareable summary, redacted copy.

**sharing mode.** How far a contribution may go: "private", "de-identified", "representative only" or "publicly attributed". Not called visibility, privacy level.

**audience.** Who a sharing mode lets see a derivative: the reviewer alone, the representative, or the public.

**approves.** Of a participant and a derivative: accepts the exact text and audience for sharing. Of a reviewer and a derivative: lets it enter the shared space.

**submits.** Of a participant and an approved derivative: sends it to review.

**review.** The reviewer's check of a derivative for privacy and re-identification risk before it is seen by its audience.

**under moderation.** The state of a contribution between submission and the reviewer's decision.

**rejects.** Of a reviewer and a derivative: refuses it with a reason; nothing is published.

**rejection.** The reviewer's refusal of a derivative, with its reason.

**flagged.** Of a derivative: marked for enhanced review because it describes a distinctive circumstance, a health detail, a minor or a very small group.

**enhanced review.** A review that needs a second recorded step before a flagged derivative may be public.

**published.** Of a derivative: shown on its issue to its audience after the reviewer approved it.

**disclosure.** The participant's grant of a sharing mode for one contribution. It may be withdrawn. Not called consent (alone), permission.

**withdraws.** Of a participant: takes back a disclosure, a priority or a trade-off, so it is no longer shown or counted. Not called deletes, retracts.

**withdrawal.** The fact that a disclosure was withdrawn, recorded without the withdrawn text.

**identifying detail.** A word or combination in a derivative that could reasonably point to a person.

**private field.** Any value Voice holds that is personal to one participant, including the original, a derivative before publication, contact details and the links from a public copy to its author.

**participant identifier.** The pseudonymous key Voice uses for a participant. Personal data; never public.

**de-identified.** Of a derivative: with every identifying detail removed, so it can be public without pointing to its author. One of the four sharing modes. Not called anonymised (for a derivative).

**participant-derived.** Of a measure: counted from the participants who took part, and labelled so; never a claim about the population.

**distinctive circumstance.** A described situation rare enough that it could identify the person it happened to.

## Issues

**issue.** A public topic that gathers approved contributions, questions, evidence, options, investigations, actions, decisions, outcomes and a chronology. Not called topic, case, campaign.

**public issue.** An issue anyone may read. In this feature every issue is public once opened.

**shared issue.** An issue seen from a contribution: the place an approved derivative goes.

**suggested issue.** An issue Voice proposes a contribution may join.

**association.** The recorded link between a contribution and an issue, kept apart from the original and changeable under review. Not called link (for this), attachment.

**chronology.** An issue's ordered record of what happened, each entry with one state label and its actor. Not called timeline, history.

**state label.** One of the seven words an entry or a statement about progress carries: "submitted", "AI-interpreted", "under moderation", "staff-acknowledged", "under investigation", "action recorded", "outcome verified".

**participant count.** How many distinct participants have published contributions on an issue.

**development.** An authoritative change to an issue that followers may be told about: an action, a decision, an outcome, accepted evidence, a published update.

**typographical correction.** A change to an issue's text that changes no fact: not a development worth an update.

**public search.** Keyword search over public issues, sources and options, open to anyone.

**snippet.** The fragment of public text a search result shows.

**record identifier.** The stable id of a record, by which a link reaches it.

## Evidence and reasoning

**reasoning service.** The sibling service that holds questions, hypotheses, sources, evidence and claims as records that are never edited.

**question.** A question an issue raises, held by the reasoning service with the hypotheses that compete to answer it.

**claim.** A proposition whose support can be inspected: derived from evidence, taking a stance on a hypothesis.

**evidence.** Source-anchored information with provenance: a source, a passage, a date, and in Voice a scope, an authority and limitations.

**source.** Where evidence comes from: a report, a dataset, a document, with its name, licence and date.

**provenance.** Where a record came from and when: its source, passage, date and authority.

**authority.** Of evidence: who stands behind the source. Of an action: the office or mandate under which it was taken.

**scope.** What a piece of evidence covers: the place, period and population it speaks to.

**limitations.** What a piece of evidence, an outcome or an aggregate cannot tell.

**superseded.** The state of accepted evidence whose source has published a correction: kept, shown as superseded, and no longer supporting anything on its own. Not called replaced, deleted.

**correction.** A later publication by the same source that changes or withdraws what earlier evidence said.

**review prompt.** What Voice raises for the reviewer when evidence is accepted or superseded: the claims and decisions that derive from that evidence, to be looked at again. A prompt changes no claim. Not called alert, flag (as a noun).

**candidate.** The state of evidence proposed and not yet accepted or rejected by the reviewer.

**accepted.** Of evidence: taken into the issue's record by the reviewer. Not called approved (for evidence).

**contradicts.** Of evidence or a claim: takes the opposite stance on the same hypothesis.

**abstains.** Voice says the records do not support an answer, and cites nothing. Not called declines, refuses to answer.

**explained.** Of a claim: shown with its source, passage, date and authority, and what contradicts it.

**document.** A file or page proposed as evidence.

## Deliberation

**option.** A described possible intervention on an issue, with costs, benefits and open questions, and no endorsement. Not called policy, proposal (for this), recommendation.

**priority.** A participant's statement of how much an issue matters to them. An opinion. Not called vote, score.

**trade-off.** A participant's statement of what cost they would accept for what benefit. An opinion.

**consideration.** A participant's note for or against an option. An opinion. Not called comment, argument.

**opinion.** A statement by a person about what they value; never evidence of what is true. Not called preference.

**aggregate.** A count or summary over many participants' opinions, always labelled with its sample. Not called statistic, poll.

**cell.** One group within an aggregate.

**suppressed.** Of a cell: not shown, because too few participants are in it.

**suppression threshold.** The fewest participants a cell may hold and still be shown.

**caveat.** The statement beside an aggregate that its participants chose themselves and are not the population.

**measure.** One number a representative's view shows about an issue, labelled participant-derived.

**concerns dashboard.** The representative's view of an issue's participant-derived measures.

**self-selected.** Of a sample: made of whoever chose to take part.

**endorsed.** Of an option: recommended. No option ever is.

## Investigation and action

**investigation.** A persistent question a representative pursues on an issue, with tasks and evidence requests. Not called inquiry, case.

**suggested.** The state of an investigation the research agent proposed and no representative has opened.

**open.** Of an investigation: approved and begun by a representative.

**action.** A step a representative has recorded taking, with authority, owner, date, rationale, state and a public summary. Not called measure (for this), intervention.

**decision.** A chosen approach a representative has recorded, with its authority, alternatives and justification.

**outcome.** An observed effect recorded against an action, with what was measured and its limitations. Not called impact.

**proposed.** The state of an action that is intended and not yet taken.

**completed.** The state of an action that has been taken.

**performed.** Of an action: shown as having been taken.

**work queue.** The representative's list of what waits: new issues, pending reviews, open review prompts, investigations, investigation tasks awaiting an external party.

**follow-up.** A thing owed by someone outside Voice that the representative is waiting on.

**unanswered.** Of an open item: with no response from the representative, and shown as such.

**public summary.** The words about an action that everyone may read.

**rationale.** Why an action was taken, as the representative recorded it.

**owner.** Who is responsible for an action.

**refused.** Not accepted: the person is told which rule was broken and nothing changes. Not called rejected (for this), denied.

**logged.** Recorded in the access or refusal log the operator reads.

## Feedback

**follows.** Of a participant and an issue: chooses to be told of its developments. Not called subscribes, watches.

**interest.** The record that a participant follows an issue, with their delivery choice. Not called subscription.

**follower.** A participant with an active interest in an issue.

**update.** The message a follower receives about one development, saying what happened, why it reached them, and linking its records. Not called notification (in steps), alert.

**inbox.** Where a participant's updates wait, inside Voice.

**delivery.** How updates reach a follower: in this feature, the inbox, immediately. Recorded apart from following so a later channel changes nothing a follower chose.

**immediate.** Delivery as each development is processed.

**processed.** Of a development: Voice has decided who is eligible and prepared their updates.

**prepared.** Of an update: written for one follower and not yet delivered.

**delivered.** Of an update: in the follower's inbox.

**eligible.** Of a follower and a development: has an active interest in the issue, so may be told.

## Erasure

**erasure.** The removal of a participant's personal data: protected fields made unreadable, private records removed, the public issue left standing. Not called deletion, shredding, crypto-shredding.

**erasure request.** A participant's request for erasure, verified before anything is erased.

**verified.** Of an erasure request: confirmed to come from the participant it names.

**protected field.** A personal value Voice can make unreadable by destroying the participant's key. Not called encrypted field, sheddable field.

**unreadable.** Of a protected field: cannot be read by anyone, including Voice, after erasure.

**erased.** Of content: made unreadable or removed by an erasure.

**retained.** Kept after erasure, because it is anonymous and lawful to keep.

**receipt.** The restricted record that an erasure completed: what classes were erased, when, who authorised it, that completion was verified, and none of the content. Not called certificate.

**dormancy.** The state of a participant whose last session ended longer ago than the dormancy period. A dormant participant is erased without a request, with the cause "dormancy". Not called inactivity, abandonment (in steps).

**dormancy period.** How long since a participant's last session before they are dormant: 180 days unless the operator sets otherwise.

**cause.** Why an erasure happened: "requested" by the participant, or "dormancy". Carried by every retained de-identified contribution whose author was erased.

**residue.** Anything left after erasure that could reasonably identify the participant.

**snapshot.** A saved point of a record's state that Voice may restore from.

**restore.** Bringing records back from a snapshot or backup.

**rebuilt.** Of views and the search index: emptied and filled again from the records.

**redacted or unavailable.** How an erased field is shown.

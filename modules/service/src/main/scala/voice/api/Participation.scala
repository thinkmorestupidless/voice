package voice.api

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.thinkmorestupidless.ankka.core.EntityId
import com.thinkmorestupidless.ankka.http.RequestContext
import voice.api.CitizenJson.*
import voice.application.erasure.{ParticipantRecordsEntity, RecordKind}
import voice.application.participation.*
import voice.domain.*
import voice.domain.participation.*
import voice.identity.RecoveryCredentials

/**
 * The citizen's participation at the edge (US1): sessions, conversations, proposals, concerns.
 * Values are sealed here, before a command is sent, and opened here, after a record is read; an
 * entity never sees plaintext (R1). The participant is always the session's, never the request's.
 */
final class Participation(reach: Reach):

  given JsonValueCodec[String] = Problems.codec[String]

  private def participant(id: String)  = reach.components.forEventSourcedEntity(EntityId(id))
  private def conversation(id: String) = reach.components.forEventSourcedEntity(EntityId(id))
  private def concern(id: String)      = reach.components.forEventSourcedEntity(EntityId(id))

  // ── sessions ───────────────────────────────────────────────────────────────

  /**
   * A recovery credential names its participant, so recovery needs no index:
   * `<participant>~<secret>`.
   */
  private def credentialFor(participantId: String): String =
    s"$participantId~${RecoveryCredentials.mint()}"

  def start(request: RequestContext, body: StartRequest): SessionReply =
    val invite = body.invite.map(_.trim).getOrElse("")
    if !reach.edge.settings.inviteCodes.contains(invite) then
      throw Rules.SessionInviteInvalid.refused(
        "Voice is open to pilot participants with an invite code."
      )
    val at         = reach.now()
    val id         = Ids.mint(IdKind.Participant, at)
    val credential = credentialFor(id)
    reach.sealing.mintKey(id)
    participant(id)
      .call(ParticipantEntity.create)
      .invoke(
        CreateParticipant(
          RecoveryCredentials.hash(credential),
          Requests.provenance(request, Actor.Participant(id), at)
        )
      ): Unit
    reach.resetDormancy(id)
    val (token, expires) = reach.edge.tokens.mint(id, at)
    SessionReply(id, credential, token, expires, Some(Participation.notice))

  def recover(request: RequestContext, body: RecoverRequest): SessionReply =
    val presented = body.recoveryCredential.trim
    val id        = presented.takeWhile(_ != '~')
    if !Ids.is(IdKind.Participant, id) then
      throw Rules.ParticipantCredentialInvalid.refused("That recovery credential does not match.")
    val at    = reach.now()
    val fresh = credentialFor(id)
    participant(id)
      .call(ParticipantEntity.recover)
      .invoke(
        RecoverSession(
          RecoveryCredentials.hash(presented),
          RecoveryCredentials.hash(fresh),
          Requests.provenance(request, Actor.Participant(id), at)
        )
      ): Unit
    reach.resetDormancy(id)
    val (token, expires) = reach.edge.tokens.mint(id, at)
    SessionReply(id, fresh, token, expires)

  def renew(request: RequestContext, participantId: String): RenewReply =
    val at = reach.now()
    participant(participantId)
      .call(ParticipantEntity.startSession)
      .invoke(
        StartSession(Requests.provenance(request, Actor.Participant(participantId), at))
      ): Unit
    reach.resetDormancy(participantId)
    val (token, expires) = reach.edge.tokens.mint(participantId, at)
    RenewReply(token, expires)

  /** Every citizen route first: the participant exists and is not erased (`participant.erased`). */
  def admit(participantId: String): Unit =
    val summary = participant(participantId).call(ParticipantEntity.summary).invoke()
    if summary.erasure == "completed" then
      throw Rules.ParticipantErased.refused(
        "This participant's data has been erased.",
        "participant" -> participantId
      )
    if !summary.exists then throw Rules.SessionRequired.refused("There is no such participant.")

  // ── conversations ──────────────────────────────────────────────────────────

  private def owned(participantId: String, conversationId: String): ConversationState =
    val held = conversation(conversationId).call(ConversationEntity.get).invoke()
    Conversation.checkOwner(held, conversationId, participantId).foreach(r => throw Refused(r))
    held

  def startConversation(request: RequestContext, participantId: String): ConversationView =
    val at = reach.now()
    val id = Ids.mint(IdKind.Conversation, at)
    ParticipantRecordsEntity.noteFor(reach.components, participantId, RecordKind.Conversation, id)
    val state = conversation(id)
      .call(ConversationEntity.start)
      .invoke(
        StartConversation(
          participantId,
          Requests.provenance(request, Actor.Participant(participantId), at)
        )
      )
    view(id, state)

  def conversations(participantId: String): Vector[ConversationSummary] =
    reach.views
      .forView(ConversationRows)
      .ask(ConversationRows.ofParticipant, "participant" -> participantId)
      .toVector
      .sortBy(_.lastAt.map(_.toEpochMilli).getOrElse(0L))
      .reverse
      .map(r => ConversationSummary(r.conversationId, r.messages, r.proposals, r.lastAt))

  def conversationView(participantId: String, conversationId: String): ConversationView =
    view(conversationId, owned(participantId, conversationId))

  def send(
      request: RequestContext,
      participantId: String,
      conversationId: String,
      body: MessageRequest
  ): MessageAccepted =
    val text = body.text.trim
    Conversation.checkMessage(conversationId, text.length).foreach(r => throw Refused(r))
    val at         = reach.now()
    val p          = Requests.provenance(request, Actor.Participant(participantId), at)
    val sealedText = reach.sealing.seal(participantId, Purposes.Message, text)
    val message = conversation(conversationId)
      .call(ConversationEntity.receive)
      .invoke(ReceiveMessage(participantId, sealedText, text.length, p))
    val turn = ConversationTurnWorkflow.turnId(conversationId, message.seq)
    reach.components
      .forWorkflow(EntityId(turn))
      .call(ConversationTurnWorkflow.start)
      .invoke(
        TurnStart(
          conversationId,
          message.seq,
          participantId,
          p.causing(p.actor, s"message:$conversationId:${message.seq}")
        )
      ): Unit
    MessageAccepted(turn, MessageView(message.seq, "citizen", text, message.at))

  def turn(participantId: String, conversationId: String, turnId: String): TurnView =
    if !turnId.startsWith(conversationId + ":turn-") then
      throw Rules.ConversationNotOwned.refused(
        "This turn is not of this conversation.",
        "conversation" -> conversationId
      )
    val held = owned(participantId, conversationId)
    val state =
      reach.components.forWorkflow(EntityId(turnId)).call(ConversationTurnWorkflow.status).invoke()
    val status = state.status match
      case TurnStatus.NotStarted | TurnStatus.Thinking => "Thinking"
      case TurnStatus.Replied                          => "Replied"
      case TurnStatus.CouldNotInterpret                => "CouldNotInterpret"
    val reply = Option
      .when(held.repliedTurns.contains(turnId)) {
        val index         = held.repliedTurns.indexOf(turnId)
        val voiceMessages = held.messages.filter(_.author == Author.Voice)
        voiceMessages.lift(index).map(m => reach.sealing.openText(m.text, Purposes.Message))
      }
      .flatten
    val proposal = state.proposalId.flatMap(held.proposal).map(p => proposalView(held, p))
    TurnView(turnId, status, reply, proposal)

  def edit(
      request: RequestContext,
      participantId: String,
      conversationId: String,
      proposalId: String,
      body: EditRequest
  ): ProposalView =
    val held = owned(participantId, conversationId)
    val current = Conversation
      .checkConfirmable(held, conversationId, proposalId)
      .fold(r => throw Refused(r), identity)
    val elements = body.elements match
      case None => current.elements
      case Some(edits) =>
        edits.flatMap { e =>
          ElementKind.values.find(_.toString.equalsIgnoreCase(e.kind)).map { k =>
            Element(
              k,
              reach.sealing.seal(participantId, Purposes.ProposalElement, e.text),
              e.spans.map(s => Span(s.message, s.from, s.to))
            )
          }
        }
    val at = reach.now()
    val edited = conversation(conversationId)
      .call(ConversationEntity.edit)
      .invoke(
        EditProposal(
          participantId,
          proposalId,
          reach.sealing.seal(participantId, Purposes.ProposalSummary, body.summary.trim),
          elements,
          Requests.provenance(request, Actor.Participant(participantId), at)
        )
      )
    proposalView(held, edited)

  /**
   * The citizen confirms; the concern records their meaning, confirmed by them (FR-008, FR-009).
   */
  def confirm(
      request: RequestContext,
      participantId: String,
      conversationId: String,
      proposalId: String
  ): ConcernView =
    val held = owned(participantId, conversationId)
    val at   = reach.now()
    val p    = Requests.provenance(request, Actor.Participant(participantId), at)
    val (concernId, proposal) = held.proposal(proposalId).map(_.state) match
      case Some(ProposalState.Confirmed(existing)) =>
        // A confirm that crashed after the conversation recorded it: finish creating the concern.
        val state = concern(existing).call(ConcernEntity.get).invoke()
        if state.confirmed then
          throw Rules.ProposalAlreadyConfirmed.refused(
            "This proposal is already confirmed.",
            "proposal" -> proposalId,
            "concern"  -> existing
          )
        (existing, held.proposal(proposalId).get)
      case _ =>
        val id = Ids.mint(IdKind.Concern, at)
        (
          id,
          conversation(conversationId)
            .call(ConversationEntity.confirm)
            .invoke(ConfirmProposal(participantId, proposalId, id, p))
        )
    ParticipantRecordsEntity.noteFor(reach.components, participantId, RecordKind.Concern, concernId)
    val summary = reach.sealing.openText(proposal.summary, Purposes.ProposalSummary)
    val elements = proposal.elements.map { e =>
      e.copy(text =
        reach.sealing.seal(
          participantId,
          Purposes.ConcernElement,
          reach.sealing.openText(e.text, Purposes.ProposalElement)
        )
      )
    }
    val state = concern(concernId)
      .call(ConcernEntity.confirm)
      .invoke(
        ConfirmConcern(
          participantId,
          conversationId,
          proposalId,
          reach.sealing.seal(participantId, Purposes.ConcernSummary, summary),
          elements,
          p
        )
      )
    concernView(concernId, state)

  def discard(
      request: RequestContext,
      participantId: String,
      conversationId: String,
      proposalId: String
  ): ConversationView =
    owned(participantId, conversationId): Unit
    val state = conversation(conversationId)
      .call(ConversationEntity.discard)
      .invoke(
        DiscardProposal(
          participantId,
          proposalId,
          Requests.provenance(request, Actor.Participant(participantId), reach.now())
        )
      )
    view(conversationId, state)

  def concerns(participantId: String): Vector[ConcernView] =
    reach.views
      .forView(ConcernRows)
      .ask(ConcernRows.ofParticipant, "participant" -> participantId)
      .toVector
      .sortBy(_.confirmedAt.map(_.toEpochMilli).getOrElse(0L))
      .map(r => concernView(r.concernId, concern(r.concernId).call(ConcernEntity.get).invoke()))

  def concernOf(participantId: String, concernId: String): ConcernState =
    val state = concern(concernId).call(ConcernEntity.get).invoke()
    if !state.confirmed || state.participantId != participantId then
      throw Rules.ConversationNotOwned.refused(
        "This concern is not yours.",
        "conversation" -> concernId
      )
    state

  // ── views, opened at the edge ──────────────────────────────────────────────

  def view(id: String, state: ConversationState): ConversationView =
    val texts =
      reach.sealing.openAll(state.participantId, state.messages.map(_.text), Purposes.Message)
    ConversationView(
      id,
      state.messages
        .zip(texts)
        .map((m, t) =>
          MessageView(
            m.seq,
            if m.author == Author.Citizen then "citizen" else "voice",
            t.render(identity),
            m.at
          )
        ),
      state.proposals.map(proposalView(state, _)),
      state.confirmedConcerns
    )

  def proposalView(state: ConversationState, p: ConcernProposal): ProposalView =
    val citizen = state.messages
      .filter(_.author == Author.Citizen)
      .map(m => m.seq -> reach.sealing.openText(m.text, Purposes.Message))
      .toMap
    val summary = reach.sealing.openText(p.summary, Purposes.ProposalSummary)
    ProposalView(
      id = p.proposalId,
      original = reach.sealing.openText(p.original, Purposes.ProposalOriginal),
      summary = summary,
      elements = p.elements.map(e => elementView(e, Purposes.ProposalElement, citizen)),
      confidence = p.confidence.toString,
      suggestedIssues =
        reach.suggestIssues(summary).map(s => SuggestionView(s.issueId, s.title, s.score)),
      edited = p.editedByCitizen,
      state = p.state match
        case ProposalState.Open         => "open"
        case ProposalState.Confirmed(_) => "confirmed"
        case ProposalState.Discarded    => "discarded",
      concern = p.state match
        case ProposalState.Confirmed(c) => Some(c)
        case _                          => None,
      fromMessages = p.fromMessages
    )

  private def elementView(e: Element, purpose: String, citizen: Map[Int, String]): ElementView =
    ElementView(
      e.kind.toString,
      reach.sealing.openText(e.text, purpose),
      e.spans.map(s =>
        SpanView(
          s.message,
          s.from,
          s.to,
          citizen
            .get(s.message)
            .map(t => t.slice(s.from, s.to))
            .getOrElse(voice.domain.Opened.Redacted)
        )
      )
    )

  def concernView(id: String, state: ConcernState): ConcernView =
    ConcernView(
      id,
      state.conversationId,
      state.summary.map(reach.sealing.openText(_, Purposes.ConcernSummary)).getOrElse(""),
      state.elements.map(e =>
        ElementView(
          e.kind.toString,
          reach.sealing.openText(e.text, Purposes.ConcernElement),
          Vector.empty
        )
      ),
      state.confirmedBy match
        case Some(Actor.Participant(_)) => "participant"
        case Some(other)                => other.render
        case None                       => "",
      state.confirmedAt,
      state.contributions.map(c => ContributionStateView(c, reach.contributionLabel(c)))
    )

object Participation:
  /** The notice, split into its AI part and the rest, as the session route answers it (FR-003). */
  lazy val notice: Notice =
    val text   = CitizenEndpoint.notice
    val marker = "**How AI is used.**"
    val i      = text.indexOf(marker)
    if i < 0 then Notice(text, "")
    else
      val end = text.indexOf("\n\n", i)
      val ai  = if end < 0 then text.substring(i) else text.substring(i, end)
      Notice(text.replace(ai, "").replaceAll("\n{3,}", "\n\n").trim, ai.trim)

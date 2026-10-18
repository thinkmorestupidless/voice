package voice.api

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec

import java.time.Instant

/** The bodies the citizen endpoint reads and writes (contracts/http-api.md, "Citizen"). */
object CitizenJson:
  final case class StartRequest(invite: Option[String] = None)
  final case class Notice(privacy: String, ai: String)
  final case class SessionReply(
      participant: String,
      recoveryCredential: String,
      session: String,
      expiresAt: Instant,
      notice: Option[Notice] = None
  )
  final case class RecoverRequest(recoveryCredential: String)
  final case class RenewReply(session: String, expiresAt: Instant)
  final case class Empty()

  final case class MessageRequest(text: String)
  final case class MessageView(seq: Int, author: String, text: String, at: Instant)
  final case class SpanView(message: Int, from: Int, to: Int, passage: String)
  final case class ElementView(kind: String, text: String, spans: Vector[SpanView])
  final case class SuggestionView(issue: String, title: String, score: Double)
  final case class ProposalView(
      id: String,
      original: String,
      summary: String,
      elements: Vector[ElementView],
      confidence: String,
      suggestedIssues: Vector[SuggestionView],
      edited: Boolean,
      state: String,
      concern: Option[String] = None,
      fromMessages: Vector[Int]
  )
  final case class ConversationView(
      id: String,
      messages: Vector[MessageView],
      proposals: Vector[ProposalView],
      confirmedConcerns: Vector[String]
  )
  final case class ConversationSummary(
      id: String,
      messages: Int,
      proposals: Int,
      lastAt: Option[Instant]
  )
  final case class MessageAccepted(turn: String, message: MessageView)
  final case class TurnView(
      turn: String,
      status: String,
      reply: Option[String] = None,
      proposal: Option[ProposalView] = None
  )
  final case class ElementEdit(kind: String, text: String, spans: Vector[SpanEdit] = Vector.empty)
  final case class SpanEdit(message: Int, from: Int, to: Int)
  final case class EditRequest(summary: String, elements: Option[Vector[ElementEdit]] = None)
  final case class ContributionStateView(id: String, state: String)
  final case class ConcernView(
      id: String,
      conversation: String,
      summary: String,
      elements: Vector[ElementView],
      confirmedBy: String,
      confirmedAt: Option[Instant],
      contributions: Vector[ContributionStateView]
  )

  given JsonValueCodec[StartRequest]     = Problems.codec[StartRequest]
  given JsonValueCodec[SessionReply]     = Problems.codec[SessionReply]
  given JsonValueCodec[RecoverRequest]   = Problems.codec[RecoverRequest]
  given JsonValueCodec[RenewReply]       = Problems.codec[RenewReply]
  given JsonValueCodec[Empty]            = Problems.codec[Empty]
  given JsonValueCodec[MessageRequest]   = Problems.codec[MessageRequest]
  given JsonValueCodec[ConversationView] = Problems.codec[ConversationView]
  given conversationSummaries: JsonValueCodec[Vector[ConversationSummary]] =
    Problems.codec[Vector[ConversationSummary]]
  given JsonValueCodec[MessageAccepted]                   = Problems.codec[MessageAccepted]
  given JsonValueCodec[TurnView]                          = Problems.codec[TurnView]
  given JsonValueCodec[EditRequest]                       = Problems.codec[EditRequest]
  given JsonValueCodec[ProposalView]                      = Problems.codec[ProposalView]
  given JsonValueCodec[ConcernView]                       = Problems.codec[ConcernView]
  given concernViews: JsonValueCodec[Vector[ConcernView]] = Problems.codec[Vector[ConcernView]]
  given JsonValueCodec[MessageView]                       = Problems.codec[MessageView]

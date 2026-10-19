package voice.unit

import com.thinkmorestupidless.ankka.testkit.EventSourcedTestKit
import voice.application.participation.*
import voice.domain.*
import voice.domain.participation.*

import java.time.Instant

/** The participant, conversation and concern entities, with no runtime (T037, T038). */
class ParticipationEntitiesSuite extends munit.FunSuite:

  private val at                      = Instant.parse("2026-11-02T09:00:00Z")
  private def p(id: String = "prt-a") = Provenance(Actor.Participant(id), at, "test", "test")
  private def sealedText(subject: String = "prt-a") = Sealed.Present[String](subject, "AAAA")
  private def ruleOf(e: com.thinkmorestupidless.ankka.core.CommandError) =
    Refusals.fromCommandError(e).map(_.rule)

  test(
    "a participant is created once, recovered only with its credential's hash, and refuses all once erased"
  ) {
    val kit = EventSourcedTestKit.of(ParticipantEntity, "prt-a")
    kit.call(ParticipantEntity.create)(CreateParticipant("hash-1", p()))
    assert(!kit.call(ParticipantEntity.create)(CreateParticipant("hash-2", p())).persisted)
    assertEquals(
      ruleOf(kit.call(ParticipantEntity.recover)(RecoverSession("wrong", "hash-3", p())).error),
      Some("participant.credential.invalid")
    )
    assert(kit.call(ParticipantEntity.recover)(RecoverSession("hash-1", "hash-3", p())).persisted)
    assertEquals(kit.currentState.recoveryHash, "hash-3")
    kit.call(ParticipantEntity.completeErasure)(MarkErasure(ErasureCause.Requested, p()))
    assertEquals(
      ruleOf(kit.call(ParticipantEntity.startSession)(StartSession(p())).error),
      Some("participant.erased")
    )
    assertEquals(kit.currentState.recoveryHash, "")
  }

  test("a session start moves the last session time the dormancy clock reads") {
    val kit = EventSourcedTestKit.of(ParticipantEntity, "prt-b")
    kit.call(ParticipantEntity.create)(CreateParticipant("h", p("prt-b")))
    kit.call(ParticipantEntity.startSession)(
      StartSession(p("prt-b").copy(at = at.plusSeconds(3600)))
    )
    assertEquals(kit.currentState.lastSessionAt, Some(at.plusSeconds(3600)))
  }

  test("a conversation refuses another participant, an empty message and an overlong one") {
    val kit = EventSourcedTestKit.of(ConversationEntity, "cnv-1")
    kit.call(ConversationEntity.start)(StartConversation("prt-a", p()))
    assertEquals(
      ruleOf(
        kit
          .call(ConversationEntity.receive)(
            ReceiveMessage("prt-b", sealedText("prt-b"), 5, p("prt-b"))
          )
          .error
      ),
      Some("conversation.not-owned")
    )
    assertEquals(
      ruleOf(
        kit.call(ConversationEntity.receive)(ReceiveMessage("prt-a", sealedText(), 0, p())).error
      ),
      Some("conversation.message.empty")
    )
    assertEquals(
      ruleOf(
        kit.call(ConversationEntity.receive)(ReceiveMessage("prt-a", sealedText(), 4001, p())).error
      ),
      Some("conversation.message.empty")
    )
    assertEquals(
      kit
        .call(ConversationEntity.receive)(ReceiveMessage("prt-a", sealedText(), 10, p()))
        .replyValue
        .seq,
      1
    )
  }

  private def proposal(id: String) =
    ConcernProposal(
      id,
      Vector(1),
      sealedText(),
      sealedText(),
      Vector.empty,
      Confidence.High,
      Vector.empty,
      false,
      ProposalState.Open
    )

  test("a turn's reply is recorded once, and a proposal is confirmed once") {
    val kit = EventSourcedTestKit.of(ConversationEntity, "cnv-2")
    kit.call(ConversationEntity.start)(StartConversation("prt-a", p()))
    kit.call(ConversationEntity.receive)(ReceiveMessage("prt-a", sealedText(), 10, p()))
    kit.call(ConversationEntity.reply)(
      RecordReply("cnv-2:turn-1", sealedText(), Some(proposal("prp-1")), p())
    )
    assert(
      !kit
        .call(ConversationEntity.reply)(
          RecordReply("cnv-2:turn-1", sealedText(), Some(proposal("prp-1")), p())
        )
        .persisted
    )
    assertEquals(kit.currentState.messages.size, 2)
    assertEquals(
      ruleOf(
        kit.call(ConversationEntity.confirm)(ConfirmProposal("prt-a", "prp-9", "cnc-1", p())).error
      ),
      Some("proposal.not-held")
    )
    kit.call(ConversationEntity.confirm)(ConfirmProposal("prt-a", "prp-1", "cnc-1", p()))
    assertEquals(
      ruleOf(
        kit.call(ConversationEntity.confirm)(ConfirmProposal("prt-a", "prp-1", "cnc-2", p())).error
      ),
      Some("proposal.already-confirmed")
    )
  }

  test("a later message leaves a confirmed proposal as it was (edge case 1)") {
    val kit = EventSourcedTestKit.of(ConversationEntity, "cnv-3")
    kit.call(ConversationEntity.start)(StartConversation("prt-a", p()))
    kit.call(ConversationEntity.receive)(ReceiveMessage("prt-a", sealedText(), 10, p()))
    kit.call(ConversationEntity.reply)(
      RecordReply("cnv-3:turn-1", sealedText(), Some(proposal("prp-1")), p())
    )
    kit.call(ConversationEntity.confirm)(ConfirmProposal("prt-a", "prp-1", "cnc-1", p()))
    val before = kit.currentState.proposal("prp-1")
    kit.call(ConversationEntity.receive)(ReceiveMessage("prt-a", sealedText(), 12, p()))
    assertEquals(kit.currentState.proposal("prp-1"), before)
  }

  test("a concern is confirmed by the participant, once") {
    val kit = EventSourcedTestKit.of(ConcernEntity, "cnc-1")
    kit.call(ConcernEntity.confirm)(
      ConfirmConcern("prt-a", "cnv-1", "prp-1", sealedText(), Vector.empty, p())
    )
    assert(
      !kit
        .call(ConcernEntity.confirm)(
          ConfirmConcern("prt-a", "cnv-1", "prp-1", sealedText(), Vector.empty, p())
        )
        .persisted
    )
    assertEquals(kit.currentState.confirmedBy, Some(Actor.Participant("prt-a")))
  }

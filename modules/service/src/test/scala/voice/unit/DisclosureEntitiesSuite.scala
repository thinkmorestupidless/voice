package voice.unit

import com.thinkmorestupidless.ankka.testkit.{EventSourcedTestKit, KeyValueEntityTestKit}
import voice.application.disclosure.*
import voice.application.issues.*
import voice.domain.*
import voice.domain.disclosure.*

import java.time.Instant

/** The contribution, Issue and link rules, with no runtime (T050, T052). */
class DisclosureEntitiesSuite extends munit.FunSuite:

  private val at = Instant.parse("2026-11-02T09:00:00Z")
  private def p(actor: Actor = Actor.Participant("prt-a"), correlation: String = "c1") =
    Provenance(actor, at, "test", correlation)
  private def reviewer(correlation: String) = p(Actor.Staff("reviewer-1"), correlation)
  private def text(s: String = "AAAA")      = Sealed.Present[String]("prt-a", s)
  private def ruleOf(e: com.thinkmorestupidless.ankka.core.CommandError) =
    Refusals.fromCommandError(e).map(_.rule)

  private def grant(mode: DisclosureMode, value: Sealed[String] = text()) =
    DisclosureGrant(
      mode,
      Modes.audience(mode),
      Modes.purpose(mode),
      value,
      Contribution.textHash(value),
      at
    )

  private def authorised(id: String, mode: DisclosureMode = DisclosureMode.DeIdentified) =
    val kit = EventSourcedTestKit.of(ContributionEntity, id)
    kit.call(ContributionEntity.authorise)(Authorise("prt-a", "cnc-1", grant(mode), None, p()))
    kit

  test("a private contribution stays a draft and is never submitted") {
    val kit = authorised("ctb-1", DisclosureMode.Private)
    assertEquals(kit.currentState.state, ContributionState.Drafting)
    assertEquals(
      ruleOf(kit.call(ContributionEntity.submit)(Submit("prt-a", p())).error),
      Some("contribution.private")
    )
  }

  test("only the exact text approved is submitted, and only by its participant") {
    val kit = EventSourcedTestKit.of(ContributionEntity, "ctb-2")
    kit.call(ContributionEntity.authorise)(
      Authorise(
        "prt-a",
        "cnc-1",
        grant(DisclosureMode.DeIdentified).copy(textHash = "other"),
        None,
        p()
      )
    )
    assertEquals(
      ruleOf(kit.call(ContributionEntity.submit)(Submit("prt-a", p())).error),
      Some("contribution.text.not-approved")
    )
    val ok = authorised("ctb-3")
    assertEquals(
      ruleOf(
        ok.call(ContributionEntity.submit)(Submit("prt-b", p(Actor.Participant("prt-b")))).error
      ),
      Some("contribution.not-owned")
    )
    assertEquals(
      ok.call(ContributionEntity.submit)(Submit("prt-a", p())).replyValue.state,
      ContributionState.UnderModeration
    )
    assert(
      !ok.call(ContributionEntity.submit)(Submit("prt-a", p())).persisted,
      "a repeated submission records nothing"
    )
    assertEquals(
      ruleOf(
        ok.call(ContributionEntity.authorise)(
          Authorise("prt-a", "cnc-1", grant(DisclosureMode.DeIdentified), None, p())
        ).error
      ),
      Some("contribution.state.invalid")
    )
  }

  test(
    "a flagged derivative needs a second step from an earlier request before approval (FR-016)"
  ) {
    val kit = authorised("ctb-4")
    kit.call(ContributionEntity.submit)(Submit("prt-a", p()))
    kit.call(ContributionEntity.recordFlags)(
      RecordFlags(
        Vector(PrivacyFlag.Minor),
        Vector("it mentions a child"),
        p(Actor.System("disclosure-review"))
      )
    )
    assertEquals(
      ruleOf(
        kit
          .call(ContributionEntity.approve)(Approve("reviewer-1", "iss-1", "pub-1", reviewer("r1")))
          .error
      ),
      Some("contribution.second-step.required")
    )
    assertEquals(
      ruleOf(
        kit
          .call(ContributionEntity.recordEnhanced)(
            RecordEnhanced("reviewer-1", " ", reviewer("r2"))
          )
          .error
      ),
      Some("contribution.reason.required")
    )
    kit.call(ContributionEntity.recordEnhanced)(
      RecordEnhanced("reviewer-1", "the child is not identifiable", reviewer("r2"))
    )
    assertEquals(
      ruleOf(
        kit
          .call(ContributionEntity.approve)(Approve("reviewer-1", "iss-1", "pub-1", reviewer("r2")))
          .error
      ),
      Some("contribution.second-step.same-click")
    )
    val approved =
      kit.call(ContributionEntity.approve)(Approve("reviewer-2", "iss-1", "pub-1", reviewer("r3")))
    assertEquals(approved.replyValue.state, ContributionState.Published)
    assert(
      !kit
        .call(ContributionEntity.approve)(Approve("reviewer-2", "iss-1", "pub-1", reviewer("r4")))
        .persisted
    )
  }

  test("a rejection needs a reason, and a withdrawal is terminal") {
    val kit = authorised("ctb-5")
    kit.call(ContributionEntity.submit)(Submit("prt-a", p()))
    assertEquals(
      ruleOf(kit.call(ContributionEntity.reject)(Reject("reviewer-1", "", reviewer("r1"))).error),
      Some("contribution.reason.required")
    )
    assertEquals(
      kit
        .call(ContributionEntity.reject)(Reject("reviewer-1", "names a neighbour", reviewer("r1")))
        .replyValue
        .review
        .flatMap(_.reason),
      Some("names a neighbour")
    )
    kit.call(ContributionEntity.withdraw)(Withdraw("prt-a", p()))
    assertEquals(kit.currentState.state, ContributionState.Withdrawn)
    assert(kit.currentState.grant.flatMap(_.withdrawnAt).isDefined)
    assertEquals(
      ruleOf(
        kit.call(ContributionEntity.associate)(Associate("prt-a", Some("iss-1"), false, p())).error
      ),
      Some("contribution.withdrawn")
    )
  }

  test("the association is the participant's and can be changed until publication (FR-017)") {
    val kit = authorised("ctb-6")
    kit.call(ContributionEntity.associate)(Associate("prt-a", Some("iss-1"), false, p()))
    kit.call(ContributionEntity.associate)(Associate("prt-a", None, true, p()))
    assertEquals((kit.currentState.associatedIssue, kit.currentState.newIssueAsked), (None, true))
  }

  test(
    "an Issue holds a copy once, drops it on withdrawal, and records a withdrawal with no text"
  ) {
    val kit   = EventSourcedTestKit.of(IssueEntity, "iss-1")
    val staff = p(Actor.Staff("reviewer-1"))
    assertEquals(
      ruleOf(
        kit.call(IssueEntity.publish)(PublishCopy(Copy.Public("pub-1", "words"), staff)).error
      ),
      Some("issue.not-held")
    )
    kit.call(IssueEntity.open)(OpenIssue("School transport", "Buses to the academy", staff))
    kit.call(IssueEntity.publish)(PublishCopy(Copy.Public("pub-1", "words"), staff))
    assert(
      !kit.call(IssueEntity.publish)(PublishCopy(Copy.Public("pub-1", "words"), staff)).persisted
    )
    assertEquals(kit.currentState.counts.contributions, 1)
    kit.call(IssueEntity.withdraw)(WithdrawCopy("pub-1", p()))
    assertEquals(kit.currentState.contributions, Vector.empty)
    assertEquals((kit.currentState.counts.contributions, kit.currentState.counts.withdrawn), (0, 1))
    val last = kit.currentState.chronology.last
    assertEquals(
      (last.ref, last.actorKind, last.state),
      ("pub-1", "participant", ChronologyState.Submitted)
    )
    assert(!last.note.contains("words"))
    assert(
      !kit.call(IssueEntity.publish)(PublishCopy(Copy.Public("pub-1", "words"), staff)).persisted,
      "a withdrawn reference is not published again"
    )
  }

  test("the link is written once and erased by overwriting it") {
    val kit = KeyValueEntityTestKit.of(IssueLinkEntity, "pub-1")
    kit.call(IssueLinkEntity.write)(WriteLink("ctb-1", "prt-a", "iss-1", p()))
    kit.call(IssueLinkEntity.write)(WriteLink("ctb-9", "prt-z", "iss-9", p()))
    assertEquals(kit.currentState.contributionId, "ctb-1")
    kit.call(IssueLinkEntity.erase)(EraseLink(p()))
    assertEquals((kit.currentState.contributionId, kit.currentState.participantId), ("", ""))
  }

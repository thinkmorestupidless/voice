package voice.api

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.thinkmorestupidless.ankka.core.EntityId
import com.thinkmorestupidless.ankka.http.{Principal, RequestContext}
import voice.application.deliberation.*
import voice.application.disclosure.DisclosurePurposes
import voice.application.issues.*
import voice.application.reasoning.{ClaimSummary, QuestionAnswer, ujsonless}
import voice.application.reasoning.ujsonless.Raw
import voice.domain.*
import voice.domain.deliberation.*
import voice.domain.issues.*

import java.time.Instant

/** The bodies of the deliberation routes (contracts/http-api.md, US4). */
object DeliberationJson:
  final case class PriorityBody(level: Int)
  final case class TradeOffBody(option: String, stance: String, text: String = "")
  final case class ConsiderationBody(option: String, text: String)
  final case class TradeOffView(option: String, stance: String, text: String, at: Instant)
  final case class ConsiderationView(option: String, text: String, at: Instant)
  final case class ExpressionView(
      issue: String,
      label: String,
      priority: Option[Int],
      tradeOffs: Vector[TradeOffView],
      considerations: Vector[ConsiderationView],
      withdrawn: Boolean,
      notice: String
  )

  /**
   * An option as a reviewer writes it. The endorsement fields are read only to be refused: a body
   * that carries any of them is `option.endorsement` (FR-032).
   */
  final case class OptionBody(
      title: String,
      description: String,
      costs: Vector[String] = Vector.empty,
      benefits: Vector[String] = Vector.empty,
      openQuestions: Vector[String] = Vector.empty,
      recommended: Option[Raw] = None,
      recommendation: Option[Raw] = None,
      endorsed: Option[Raw] = None,
      rank: Option[Raw] = None,
      ranking: Option[Raw] = None,
      score: Option[Raw] = None
  ):
    def endorses: Boolean =
      Seq(recommended, recommendation, endorsed, rank, ranking, score).exists(_.isDefined)

  final case class OptionView(
      id: String,
      title: String,
      description: String,
      costs: Vector[String],
      benefits: Vector[String],
      openQuestions: Vector[String]
  )

  /** A participant-derived measure, labelled by who it counts and when (FR-034, FR-037). */
  final case class Measure(
      label: String,
      value: Cell,
      basis: String,
      window: Window,
      method: String,
      caveat: String
  )
  final case class RestrictedView(pub: String, text: String, publishedAt: Instant)
  final case class IssueForStaff(
      page: PublicJson.IssuePage,
      restricted: Vector[RestrictedView],
      measures: Vector[Measure],
      unresolved: Vector[QuestionAnswer],
      contrary: Vector[ClaimSummary]
  )

  import ujsonless.given
  given JsonValueCodec[PriorityBody]                    = Problems.codec[PriorityBody]
  given JsonValueCodec[TradeOffBody]                    = Problems.codec[TradeOffBody]
  given JsonValueCodec[ConsiderationBody]               = Problems.codec[ConsiderationBody]
  given JsonValueCodec[ExpressionView]                  = Problems.codec[ExpressionView]
  given JsonValueCodec[OptionBody]                      = Problems.codec[OptionBody]
  given JsonValueCodec[OptionView]                      = Problems.codec[OptionView]
  given optionViews: JsonValueCodec[Vector[OptionView]] = Problems.codec[Vector[OptionView]]
  given JsonValueCodec[IssueForStaff]                   = Problems.codec[IssueForStaff]

  val Notice: String =
    "This is your opinion. It is shown only inside counts of everyone who answered, never with your name, and never as evidence."

  def option(s: PolicyOptionState): OptionView =
    OptionView(s.optionId, s.title, s.description, s.costs, s.benefits, s.openQuestions)

import DeliberationJson.{*, given}

object DeliberationPurposes:
  val Priority      = "expression.priority"
  val TradeOff      = "expression.trade-off"
  val Consideration = "expression.consideration"

/**
 * Priorities, trade-offs and considerations: opinion, held apart from evidence (FR-030 to FR-035).
 * Nothing here is read by an evidence route or a claim explanation, and nothing an evidence route
 * reads is written here (SC-005).
 */
final class Deliberation(reach: Reach):

  given JsonValueCodec[String]   = Problems.codec[String]
  given JsonValueCodec[Priority] = Problems.codec[Priority]

  private val issues = PublicIssues(reach)

  /** The participant's expression on `issue`: keyed on the pair, found again without a lookup. */
  def expressionId(participant: String, issue: String): String =
    Ids.keyed(IdKind.Expression, reach.edge.settings.sessionKey, "expression", participant, issue)

  private def expression(participant: String, issue: String) =
    reach.components.forEventSourcedEntity(EntityId(expressionId(participant, issue)))

  /** The expression to write to, noted first in the participant's index (FR-055). */
  private def writing(participant: String, issue: String) =
    val id = expressionId(participant, issue)
    voice.application.erasure.ParticipantRecordsEntity.noteFor(
      reach.components,
      participant,
      voice.application.erasure.RecordKind.Expression,
      id
    )
    reach.components.forEventSourcedEntity(EntityId(id))

  private def provenance(request: RequestContext, participant: String) =
    Requests.provenance(request, Actor.Participant(participant), reach.now())

  def view(participant: String, issue: String): ExpressionView =
    issues.held(issue): Unit
    val s = expression(participant, issue).call(ExpressionEntity.get).invoke()
    ExpressionView(
      issue,
      "opinion",
      s.level,
      s.tradeOffs.map(t =>
        TradeOffView(
          t.optionId,
          t.stance.wire,
          reach.sealing.openText(t.text, DeliberationPurposes.TradeOff),
          t.at
        )
      ),
      s.considerations.map(c =>
        ConsiderationView(
          c.optionId,
          reach.sealing.openText(c.text, DeliberationPurposes.Consideration),
          c.at
        )
      ),
      s.withdrawn,
      Notice
    )

  def priority(
      request: RequestContext,
      participant: String,
      issue: String,
      body: PriorityBody
  ): ExpressionView =
    issues.held(issue): Unit
    val p = provenance(request, participant)
    val held =
      reach.sealing.seal(participant, DeliberationPurposes.Priority, Priority(body.level, p.at))
    writing(participant, issue)
      .call(ExpressionEntity.priority)
      .invoke(ExpressPriority(issue, participant, body.level, held, p)): Unit
    view(participant, issue)

  def tradeOff(
      request: RequestContext,
      participant: String,
      issue: String,
      body: TradeOffBody
  ): ExpressionView =
    val s = issues.held(issue)
    val stance = TradeOffStance
      .parse(body.stance)
      .getOrElse(
        throw Rules.ExpressionOptionNotHeld.refused(
          "A trade-off accepts or rejects an option.",
          "issue"  -> issue,
          "option" -> body.option
        )
      )
    val p = provenance(request, participant)
    val entry = TradeOffEntry(
      body.option,
      stance,
      reach.sealing.seal(participant, DeliberationPurposes.TradeOff, body.text.trim),
      p.at
    )
    writing(participant, issue)
      .call(ExpressionEntity.tradeOff)
      .invoke(ExpressTradeOff(issue, participant, s.options.toSet, entry, p)): Unit
    view(participant, issue)

  def consider(
      request: RequestContext,
      participant: String,
      issue: String,
      body: ConsiderationBody
  ): ExpressionView =
    val s = issues.held(issue)
    val p = provenance(request, participant)
    val entry = ConsiderationEntry(
      body.option,
      reach.sealing.seal(participant, DeliberationPurposes.Consideration, body.text.trim),
      p.at
    )
    writing(participant, issue)
      .call(ExpressionEntity.consider)
      .invoke(RecordConsideration(issue, participant, s.options.toSet, entry, p)): Unit
    view(participant, issue)

  def withdraw(request: RequestContext, participant: String, issue: String): ExpressionView =
    issues.held(issue): Unit
    expression(participant, issue)
      .call(ExpressionEntity.withdraw)
      .invoke(WithdrawExpression(provenance(request, participant))): Unit
    view(participant, issue)

object Deliberation:

  def options(reach: Reach, s: IssueState): Vector[OptionView] =
    s.options.flatMap { id =>
      Option(reach.components.forKeyValueEntity(EntityId(id)).call(PolicyOptionEntity.get).invoke())
        .filter(_.exists)
        .map(option)
    }

  /**
   * The labelled aggregate of an Issue's expressions, small cells suppressed at read (FR-033,
   * FR-034).
   */
  def aggregate(reach: Reach, issue: String, s: IssueState): Aggregate =
    val rows =
      reach.views.forView(IssueAggregates).ask(IssueAggregates.forIssue, "issue" -> issue).toVector
    Aggregates.of(rows, reach.edge.settings.suppressionThreshold, s.options)

/**
 * The reviewer's and representative's side: options, and the representative's view of the Issue.
 */
final class StaffDeliberation(reach: Reach, gate: StaffGate):

  given JsonValueCodec[String] = Problems.codec[String]

  private val issues                  = PublicIssues(reach)
  private def issueEntity(id: String) = reach.components.forEventSourcedEntity(EntityId(id))

  private def held(issue: String): IssueState =
    val s =
      if Ids.is(IdKind.Issue, issue) then issueEntity(issue).call(IssueEntity.get).invoke()
      else IssueState.empty
    if !s.exists then throw Rules.IssueNotHeld.refused("There is no such issue.", "issue" -> issue)
    s

  /** `new` mints the option's id; an existing id must be one of this Issue's. */
  def writeOption(
      request: RequestContext,
      principal: Principal,
      issue: String,
      option: String,
      body: OptionBody
  ): OptionView =
    val s = held(issue)
    if body.endorses then
      throw Rules.OptionEndorsement.refused(
        "An option is described, never recommended or ranked.",
        "option" -> option
      )
    val at = reach.now()
    val id =
      if option == "new" then Ids.mint(IdKind.PolicyOption, at)
      else if Ids.is(IdKind.PolicyOption, option) then option
      else
        throw Rules.IssueNotHeld.refused("There is no such option on this issue.", "issue" -> issue)
    val existing =
      reach.components.forKeyValueEntity(EntityId(id)).call(PolicyOptionEntity.get).invoke()
    if existing.exists && existing.issueId != issue then
      throw Rules.IssueNotHeld.refused("There is no such option on this issue.", "issue" -> issue)
    val p = Requests.provenance(request, Requests.staff(principal), at)
    val written = reach.components
      .forKeyValueEntity(EntityId(id))
      .call(PolicyOptionEntity.write)
      .invoke(
        WriteOption(
          issue,
          body.title,
          body.description,
          body.costs,
          body.benefits,
          body.openQuestions,
          p
        )
      )
    issueEntity(issue).call(IssueEntity.addOption).invoke(AddOption(id, written.title, p)): Unit
    DeliberationJson.option(written)

  def options(issue: String): Vector[OptionView] = Deliberation.options(reach, held(issue))

  /**
   * The concerns dashboard (FR-037): the public page, the restricted contributions this role may
   * see (each read logged, FR-005), every participant-derived measure labelled by its basis and
   * window, the questions the records do not answer, and the claims that contradict another.
   */
  def forStaff(request: RequestContext, principal: Principal, issue: String): IssueForStaff =
    val s    = held(issue)
    val page = issues.page(issue)
    val restricted = s.restricted.map { r =>
      gate.logAccess(request, principal, "GET /staff/issues/{issue}", r.pub)
      RestrictedView(
        r.pub,
        reach.sealing.openText(r.text, DisclosurePurposes.IssueText),
        r.publishedAt
      )
    }
    val agg = Deliberation.aggregate(reach, issue, s)
    def basis = agg.participants.count.fold("fewer participants than the suppression threshold")(
      n => s"$n participants who chose to answer"
    )
    val measures = Vector(
      Measure(
        "Participants who expressed an opinion on this issue",
        agg.participants,
        basis,
        agg.window,
        agg.method,
        agg.caveat
      ),
      Measure(
        "Statements of opinion: priorities, trade-offs and considerations",
        agg.contributions,
        basis,
        agg.window,
        agg.method,
        agg.caveat
      )
    ) ++ agg.priorities.map(c =>
      Measure(
        s"Participants giving priority ${c.key} of 5",
        c,
        basis,
        agg.window,
        agg.method,
        agg.caveat
      )
    ) :+
      Measure(
        "Contributions published to this issue after review",
        Cell("contributions", Some(s.counts.contributions), false),
        "contributions residents chose to share, not participants",
        Window(
          s.contributions
            .map(_.publishedAt)
            .minOption
            .map(_.atZone(java.time.ZoneOffset.UTC).toLocalDate),
          s.contributions
            .map(_.publishedAt)
            .maxOption
            .map(_.atZone(java.time.ZoneOffset.UTC).toLocalDate)
        ),
        "Self-selected: residents who chose to share, each checked by a reviewer.",
        IssueCounts.Caveat
      )
    val contrary = page.claims.filter(c =>
      s.claims.exists(l => l.claim.id == c.id && l.stances.exists(!_.supports))
    )
    IssueForStaff(page, restricted, measures, page.questions.filter(_.abstained), contrary)

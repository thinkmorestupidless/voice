package voice.application.reasoning

import voice.domain.EvidenceState
import voice.domain.issues.*

import java.time.Instant

/** What a reader sees of a source: reasoning's name, Voice's licence and attribution (FR-021). */
final case class SourceView(
    id: String,
    name: String,
    licence: String,
    attribution: String,
    link: String,
    record: String
)

/**
 * One piece of evidence as shown (FR-021): reasoning's source, locator, passage and dates, read
 * each time; Voice's state, scope, authority and limitations. A withdrawn record shows its note,
 * never its passage.
 */
final case class EvidenceCard(
    id: String,
    evidence: Option[String],
    state: String,
    source: SourceView,
    locator: Option[String],
    passage: Option[String],
    publishedAt: Option[Instant],
    recordedAt: Option[Instant],
    scope: String,
    authority: String,
    limitations: String,
    withdrawn: Option[String],
    supersededBy: Option[String],
    link: String
)

final case class StanceView(hypothesis: String, statement: String, stance: String)

final case class ClaimCard(
    id: String,
    statement: String,
    stances: Vector[StanceView],
    evidence: Vector[EvidenceCard],
    revises: Option[String],
    revisedBy: Vector[String],
    link: String
)

/** Why a claim is supported, and what contradicts it; or an abstention (FR-023 to FR-025). */
final case class ClaimExplanation(
    claim: Option[ClaimCard],
    contradicting: Vector[ClaimCard],
    abstained: Boolean,
    reason: Option[String]
)

final case class ClaimSummary(id: String, statement: String, link: String)
final case class HypothesisAnswer(
    id: String,
    statement: String,
    supporting: Vector[ClaimSummary],
    contradicting: Vector[ClaimSummary]
)
final case class QuestionAnswer(
    id: String,
    statement: String,
    hypotheses: Vector[HypothesisAnswer],
    abstained: Boolean,
    reason: Option[String]
)

/**
 * Explanations assembled from reasoning's records by id (contracts/reasoning-integration.md, "What
 * Voice reads"), with no graph: the claim, each evidence it derives from, each evidence's source;
 * contradiction from the Issue's own claims with the opposite stance on the same hypothesis. When
 * the records support no answer Voice says so and cites nothing.
 */
final class Explanations(reasoning: ReasoningClient, cards: String => Option[SourceCardState]):

  val Unsupported = "The records Voice holds do not support an answer to this."
  val Unavailable = "record unavailable"

  private def safely[A](read: => Option[A]): Option[A] =
    try read
    catch case _: ReasoningUnavailable => None

  def source(id: String): SourceView =
    val card = cards(id)
    val name = card
      .map(_.name)
      .filter(_.nonEmpty)
      .orElse(safely(reasoning.source(id)).map(_.name))
      .getOrElse(Unavailable)
    SourceView(
      id,
      name,
      card.map(_.licence).getOrElse(""),
      card.map(_.attribution).getOrElse(""),
      card.map(_.link).getOrElse(""),
      s"/public/sources/$id"
    )

  /** A card for `link`; a candidate's passage is the reviewer's text, held until acceptance. */
  def card(issueId: String, link: EvidenceLink): EvidenceCard =
    val record = link.evidence.flatMap(e => safely(reasoning.evidence(e.id)))
    EvidenceCard(
      id = link.linkId,
      evidence = link.evidence.map(_.id),
      state = link.state.toString.toLowerCase,
      source = source(link.source),
      locator = record.flatMap(_.locator).orElse(link.candidate.map(_.locator)),
      passage =
        if record.exists(_.withdrawal.isDefined) then None
        else record.flatMap(_.excerpt).orElse(link.candidate.map(_.excerpt)),
      publishedAt = record.flatMap(_.publishedAt).orElse(link.candidate.flatMap(_.publishedAt)),
      recordedAt = record.flatMap(_.recordedAt),
      scope = link.scope,
      authority = link.authority,
      limitations = link.limitations,
      withdrawn = record.flatMap(_.withdrawal.map(_.note)),
      supersededBy = link.supersededBy,
      link = s"/public/issues/$issueId/evidence/${link.linkId}"
    )

  private def hypothesisStatement(ref: HypothesisRef): String =
    safely(reasoning.question(ref.question))
      .flatMap(_.hypotheses.find(h => ref.id.endsWith("/" + h.id)))
      .map(_.statement)
      .getOrElse(Unavailable)

  /** Accepted evidence a linked claim stands on: what makes it count as support. */
  private def accepted(issue: IssueState, claim: LinkedClaim): Vector[EvidenceLink] =
    claim.derivesFrom.flatMap(issue.linkOf).filter(_.state == EvidenceState.Accepted)

  private def claimCard(issueId: String, issue: IssueState, linked: LinkedClaim): ClaimCard =
    val read = safely(reasoning.claim(linked.claim.id))
    ClaimCard(
      id = linked.claim.id,
      statement = read.flatMap(_.claim.statement).getOrElse(Unavailable),
      stances = linked.stances.map(s =>
        StanceView(
          s.hypothesis.id,
          hypothesisStatement(s.hypothesis),
          if s.supports then "supports" else "contradicts"
        )
      ),
      evidence = linked.derivesFrom.flatMap(issue.linkOf).map(card(issueId, _)),
      revises = linked.revises.map(_.id),
      revisedBy = read.map(_.revisedBy).getOrElse(Vector.empty),
      link = s"/public/issues/$issueId/claims/${linked.claim.id}"
    )

  /** Support: a linked claim with accepted evidence, not withdrawn and not revised by another. */
  private def supported(issue: IssueState, linked: LinkedClaim): Boolean =
    accepted(issue, linked).nonEmpty && safely(reasoning.claim(linked.claim.id)).exists(r =>
      r.claim.withdrawal.isEmpty && r.revisedBy.isEmpty
    )

  private def opposes(a: LinkedClaim, b: LinkedClaim): Boolean =
    a.claim != b.claim && a.stances.exists(sa =>
      b.stances.exists(sb => sa.hypothesis == sb.hypothesis && sa.supports != sb.supports)
    )

  def claim(issueId: String, issue: IssueState, claimId: String): Option[ClaimExplanation] =
    issue.claims.find(_.claim.id == claimId).map { linked =>
      if !supported(issue, linked) then
        ClaimExplanation(None, Vector.empty, abstained = true, Some(Unsupported))
      else
        val against = issue.claims
          .filter(c => opposes(linked, c) && accepted(issue, c).nonEmpty)
          .map(claimCard(issueId, issue, _))
        ClaimExplanation(Some(claimCard(issueId, issue, linked)), against, abstained = false, None)
    }

  /** A question is answered only from claims with accepted evidence; otherwise Voice abstains. */
  def question(issueId: String, issue: IssueState, questionId: String): Option[QuestionAnswer] =
    Option.when(issue.questions.exists(_.id == questionId))(()).map { _ =>
      val read = safely(reasoning.question(questionId))
      val hypotheses = read.map(_.hypotheses).getOrElse(Vector.empty).map { h =>
        val ref = HypothesisRef(ReasoningIds.hypothesis(questionId, h.id))
        def on(supports: Boolean) = issue.claims
          .filter(c =>
            c.stances
              .exists(s => s.hypothesis == ref && s.supports == supports) && supported(issue, c)
          )
          .map(c =>
            ClaimSummary(
              c.claim.id,
              safely(reasoning.claim(c.claim.id)).flatMap(_.claim.statement).getOrElse(Unavailable),
              s"/public/issues/$issueId/claims/${c.claim.id}"
            )
          )
        HypothesisAnswer(ref.id, h.statement, on(true), on(false))
      }
      val answered = hypotheses.exists(h => h.supporting.nonEmpty || h.contradicting.nonEmpty)
      QuestionAnswer(
        questionId,
        read.map(_.statement).getOrElse(Unavailable),
        if answered then hypotheses
        else hypotheses.map(_.copy(supporting = Vector.empty, contradicting = Vector.empty)),
        abstained = !answered,
        Option.when(!answered)(Unsupported)
      )
    }

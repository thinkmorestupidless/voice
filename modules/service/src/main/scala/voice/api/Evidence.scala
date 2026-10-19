package voice.api

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.thinkmorestupidless.ankka.core.EntityId
import com.thinkmorestupidless.ankka.http.{Principal, RequestContext}
import voice.application.issues.*
import voice.application.reasoning.*
import voice.application.reasoning.ReasoningRecords.{
  HypothesisRequest,
  OpenQuestion,
  RecordEvidence,
  RegisterSource,
  StateClaim
}
import voice.domain.*
import voice.domain.issues.*

import java.time.Instant

/** The bodies of the evidence routes (contracts/http-api.md, "Reviewer" and "Public"). */
object EvidenceJson:
  final case class NewIssueRequest(title: String, summary: String)
  final case class IssueRef(id: String, title: String, summary: String)
  final case class SourceRequest(
      id: String,
      name: String,
      licence: String,
      attribution: String,
      link: String,
      publishedAt: Option[Instant] = None
  )
  final case class HypothesisBody(id: String, statement: String)
  final case class QuestionRequest(
      statement: String,
      hypotheses: Vector[HypothesisBody],
      key: Option[String] = None
  )
  final case class QuestionView(
      id: String,
      statement: String,
      hypotheses: Vector[HypothesisBody],
      link: String
  )
  final case class EvidenceRequest(
      source: String,
      locator: String,
      excerpt: String,
      scope: String,
      authority: String,
      limitations: String,
      publishedAt: Option[Instant] = None,
      author: Option[String] = None,
      bearsOn: Vector[String] = Vector.empty
  )
  final case class EmptyBody()
  final case class ReasonBody(reason: String)
  final case class SupersedeBody(by: String, reason: String)
  final case class NoteBody(note: String)
  final case class StanceBody(hypothesis: String, stance: String)
  final case class ClaimRequest(
      statement: String,
      derivesFrom: Vector[String],
      stances: Vector[StanceBody],
      key: Option[String] = None,
      revises: Option[String] = None
  )
  final case class StaffEvidence(
      card: EvidenceCard,
      bearsOn: Vector[String],
      proposedAt: Instant,
      decidedBy: Option[String],
      decidedAt: Option[Instant],
      reason: Option[String],
      prompts: Vector[String]
  )
  final case class ResolutionView(by: String, at: Instant, note: String)
  final case class PromptView(
      id: String,
      evidence: String,
      cause: String,
      claims: Vector[String],
      decisions: Vector[String],
      raisedAt: Instant,
      resolved: Option[ResolutionView]
  )
  final case class SourceCardView(
      id: String,
      name: String,
      licence: String,
      attribution: String,
      link: String,
      publishedAt: Option[Instant]
  )
  final case class Hit(
      kind: String,
      id: String,
      version: Option[String],
      date: Option[Instant],
      title: String,
      snippet: String,
      link: String
  )

  given JsonValueCodec[NewIssueRequest]                      = Problems.codec[NewIssueRequest]
  given JsonValueCodec[IssueRef]                             = Problems.codec[IssueRef]
  given JsonValueCodec[SourceRequest]                        = Problems.codec[SourceRequest]
  given JsonValueCodec[QuestionRequest]                      = Problems.codec[QuestionRequest]
  given JsonValueCodec[QuestionView]                         = Problems.codec[QuestionView]
  given JsonValueCodec[EvidenceRequest]                      = Problems.codec[EvidenceRequest]
  given JsonValueCodec[EmptyBody]                            = Problems.codec[EmptyBody]
  given JsonValueCodec[ReasonBody]                           = Problems.codec[ReasonBody]
  given JsonValueCodec[SupersedeBody]                        = Problems.codec[SupersedeBody]
  given JsonValueCodec[NoteBody]                             = Problems.codec[NoteBody]
  given JsonValueCodec[ClaimRequest]                         = Problems.codec[ClaimRequest]
  given JsonValueCodec[StaffEvidence]                        = Problems.codec[StaffEvidence]
  given staffEvidence: JsonValueCodec[Vector[StaffEvidence]] = Problems.codec[Vector[StaffEvidence]]
  given JsonValueCodec[PromptView]                           = Problems.codec[PromptView]
  given prompts: JsonValueCodec[Vector[PromptView]]          = Problems.codec[Vector[PromptView]]
  given JsonValueCodec[SourceCardView]                       = Problems.codec[SourceCardView]
  given JsonValueCodec[SourceView]                           = Problems.codec[SourceView]
  given JsonValueCodec[EvidenceCard]                         = Problems.codec[EvidenceCard]
  given JsonValueCodec[ClaimCard]                            = Problems.codec[ClaimCard]
  given JsonValueCodec[ClaimExplanation]                     = Problems.codec[ClaimExplanation]
  given JsonValueCodec[QuestionAnswer]                       = Problems.codec[QuestionAnswer]
  given hits: JsonValueCodec[Vector[Hit]]                    = Problems.codec[Vector[Hit]]

  def prompt(p: ReviewPrompt): PromptView =
    PromptView(
      p.promptId,
      p.evidence,
      p.cause,
      p.claims.map(_.id),
      p.decisions,
      p.raisedAt,
      p.resolved.map(r => ResolutionView(r.by, r.at, r.note))
    )

  def sourceCard(s: SourceCardState): SourceCardView =
    SourceCardView(s.sourceId, s.name, s.licence, s.attribution, s.link, s.publishedAt)

import EvidenceJson.{*, given}

/**
 * What both sides read through: the Issue, the source cards and the explanation built from them.
 */
final class EvidenceReads(reach: Reach):

  def issue(id: String): IssueState =
    reach.components.forEventSourcedEntity(EntityId(id)).call(IssueEntity.get).invoke()

  def card(id: String): Option[SourceCardState] =
    Option(reach.components.forKeyValueEntity(EntityId(id)).call(SourceCardEntity.get).invoke())
      .filter(_.exists)

  lazy val explanations: Explanations = Explanations(reach.reasoning, card)

/**
 * The reviewer's curation of an Issue's evidence (FR-021, FR-028, FR-029). Every write goes to
 * reasoning first and to the Issue second, so the Issue never links a reference that does not
 * resolve; both writes are idempotent, so a retry after a crash between them is safe (R4).
 */
final class StaffEvidenceRoutes(reach: Reach):

  private val reads                   = EvidenceReads(reach)
  private def issueEntity(id: String) = reach.components.forEventSourcedEntity(EntityId(id))

  private def provenance(request: RequestContext, principal: Principal) =
    Requests.provenance(request, Requests.staff(principal), reach.now())

  /** The Issue a route names, or `issue.not-held`. */
  private def held(issue: String): IssueState =
    val s = if Ids.is(IdKind.Issue, issue) then reads.issue(issue) else IssueState.empty
    if !s.exists then throw Rules.IssueNotHeld.refused("There is no such issue.", "issue" -> issue)
    s

  private def unresolved(kind: String, id: String, why: String): Nothing =
    throw Rules.EvidenceReferenceUnresolved.refused(why, "kind" -> kind, "id" -> id)

  private def key(asked: Option[String], prefix: String, taken: Int): String =
    val k = asked.map(_.trim).filter(_.nonEmpty).getOrElse(s"$prefix${taken + 1}")
    if !ReasoningIds.valid(k) then
      unresolved(prefix, k, "A key is letters, digits, dots, dashes and underscores.")
    k

  def openIssue(request: RequestContext, principal: Principal, body: NewIssueRequest): IssueRef =
    if body.title.trim.isEmpty then
      throw Rules.ContributionReasonRequired.refused(
        "An issue needs a title.",
        "contribution" -> ""
      )
    val id = Ids.mint(IdKind.Issue, reach.now())
    val s = issueEntity(id)
      .call(IssueEntity.open)
      .invoke(OpenIssue(body.title, body.summary, provenance(request, principal)))
    IssueRef(id, s.title, s.summary)

  /** A source: registered in reasoning, then its public card in Voice. */
  def source(request: RequestContext, principal: Principal, body: SourceRequest): SourceCardView =
    if !body.id.startsWith("voice.src.") || !ReasoningIds.valid(body.id) then
      unresolved("source", body.id, "A source id is voice.src.<slug>.")
    val registered = reach.reasoning.registerSource(RegisterSource(body.id, body.name.trim))
    val state = reach.components
      .forKeyValueEntity(EntityId(registered.id))
      .call(SourceCardEntity.write)
      .invoke(
        WriteSourceCard(
          registered.name,
          body.licence,
          body.attribution,
          body.link,
          body.publishedAt,
          provenance(request, principal)
        )
      )
    sourceCard(state)

  def question(
      request: RequestContext,
      principal: Principal,
      issue: String,
      body: QuestionRequest
  ): QuestionView =
    val s  = held(issue)
    val id = ReasoningIds.question(issue, key(body.key, "q", s.questions.size))
    val q = reach.reasoning.openQuestion(
      OpenQuestion(
        id,
        body.statement.trim,
        body.hypotheses.map(h => HypothesisRequest(h.id, h.statement.trim))
      )
    )
    issueEntity(issue)
      .call(IssueEntity.linkQuestion)
      .invoke(LinkQuestion(QuestionRef(q.id), provenance(request, principal))): Unit
    QuestionView(
      q.id,
      q.statement,
      q.hypotheses.map(h => HypothesisBody(h.id, h.statement)),
      s"/public/issues/$issue/questions/${q.id}"
    )

  /**
   * `q1/h1` names a hypothesis of this Issue's question `q1`; a full reasoning id is taken as it
   * is.
   */
  private def hypothesis(issue: String, s: IssueState, name: String): HypothesisRef =
    val full = name.split('/') match
      case Array(q, h) if q.startsWith("voice.") => s"$q/$h"
      case Array(q, h) => ReasoningIds.hypothesis(ReasoningIds.question(issue, q), h)
      case _ => unresolved("hypothesis", name, "A hypothesis is named <question>/<hypothesis>.")
    val ref = HypothesisRef(full)
    if !s.questions
        .contains(QuestionRef(ref.question)) || !reach.reasoning.resolves("hypothesis", full)
    then unresolved("hypothesis", full, "The hypothesis is not one of this issue's questions'.")
    ref

  def propose(
      request: RequestContext,
      principal: Principal,
      issue: String,
      body: EvidenceRequest
  ): StaffEvidence =
    val s = held(issue)
    if reads.card(body.source).isEmpty && !reach.reasoning.resolves("source", body.source) then
      unresolved("source", body.source, "The source is not registered.")
    val bearsOn = body.bearsOn.map(hypothesis(issue, s, _))
    val at      = reach.now()
    val link = EvidenceLink(
      linkId = Ids.mint(IdKind.Evidence, at),
      source = body.source,
      evidence = None,
      candidate = Some(
        Candidate(body.source, body.locator.trim, body.excerpt, body.publishedAt, body.author)
      ),
      state = EvidenceState.Candidate,
      scope = body.scope.trim,
      authority = body.authority.trim,
      limitations = body.limitations.trim,
      bearsOn = bearsOn,
      proposedAt = at
    )
    val after = issueEntity(issue)
      .call(IssueEntity.proposeEvidence)
      .invoke(ProposeEvidence(link, provenance(request, principal)))
    view(issue, after, link.linkId)

  /** Accepting writes the passage to reasoning, then the decision to the Issue, with its prompt. */
  def accept(
      request: RequestContext,
      principal: Principal,
      issue: String,
      linkId: String
  ): StaffEvidence =
    val s = held(issue)
    val link = s
      .link(linkId)
      .getOrElse(unresolved("evidence", linkId, "There is no such evidence on this issue."))
    val ref = (link.state, link.candidate) match
      case (EvidenceState.Candidate, Some(c)) =>
        EvidenceRef(
          reach.reasoning
            .recordEvidence(RecordEvidence(c.source, c.locator, c.excerpt, c.author, c.publishedAt))
            .id
        )
      case _ => link.evidence.getOrElse(EvidenceRef(""))
    val p = provenance(request, principal)
    val after = issueEntity(issue)
      .call(IssueEntity.acceptEvidence)
      .invoke(
        AcceptEvidence(
          linkId,
          ref,
          principal.subject,
          Ids.mint(IdKind.Prompt, p.at),
          p,
          Ids.mint(IdKind.Development, p.at)
        )
      )
    view(issue, after, linkId)

  def reject(
      request: RequestContext,
      principal: Principal,
      issue: String,
      linkId: String,
      body: ReasonBody
  ): StaffEvidence =
    held(issue): Unit
    val after = issueEntity(issue)
      .call(IssueEntity.rejectEvidence)
      .invoke(
        RejectEvidence(linkId, principal.subject, body.reason, provenance(request, principal))
      )
    view(issue, after, linkId)

  def supersede(
      request: RequestContext,
      principal: Principal,
      issue: String,
      linkId: String,
      body: SupersedeBody
  ): StaffEvidence =
    held(issue): Unit
    if body.reason.trim.isEmpty then
      throw Rules.ContributionReasonRequired.refused(
        "A reason is required.",
        "contribution" -> linkId
      )
    val p = provenance(request, principal)
    val after = issueEntity(issue)
      .call(IssueEntity.supersedeEvidence)
      .invoke(
        SupersedeEvidence(
          linkId,
          body.by,
          principal.subject,
          body.reason,
          Ids.mint(IdKind.Prompt, p.at),
          p
        )
      )
    view(issue, after, linkId)

  /** A claim: stated in reasoning as Voice's holder from accepted evidence, then linked. */
  def claim(
      request: RequestContext,
      principal: Principal,
      issue: String,
      body: ClaimRequest
  ): ClaimCard =
    val s = held(issue)
    val derives = body.derivesFrom.map { id =>
      s.link(id)
        .filter(_.state == EvidenceState.Accepted)
        .flatMap(_.evidence)
        .getOrElse(
          unresolved("evidence", id, "A claim derives only from evidence accepted on this issue.")
        )
    }
    if derives.isEmpty then
      unresolved("evidence", "", "A claim derives from at least one piece of evidence.")
    if body.stances.isEmpty then
      unresolved("hypothesis", "", "A claim takes a stance on at least one hypothesis.")
    val stances = body.stances.map { st =>
      val supports = st.stance match
        case "supports"    => true
        case "contradicts" => false
        case other         => unresolved("stance", other, "A stance is supports or contradicts.")
      Stance(hypothesis(issue, s, st.hypothesis), supports)
    }
    val id = ReasoningIds.claim(issue, key(body.key, "c", s.claims.size))
    val revises = body.revises
      .filter(_.nonEmpty)
      .map(r => if r.startsWith("voice.") then r else ReasoningIds.claim(issue, r))
    revises.foreach(r =>
      if !s.claims.exists(_.claim.id == r) then
        unresolved("claim", r, "A claim revises a claim linked to this issue.")
    )
    reach.reasoning.registerHolder()
    val stated = reach.reasoning.stateClaim(
      StateClaim(
        id,
        VoiceHolder,
        body.statement.trim,
        derives.map(_.id),
        stances.map(st =>
          ReasoningRecords
            .Stance(st.hypothesis.id, if st.supports then "supports" else "contradicts")
        ),
        revises
      )
    )
    val linked = LinkedClaim(ClaimRef(stated.id), derives, stances, revises.map(ClaimRef(_)))
    val after = issueEntity(issue)
      .call(IssueEntity.linkClaim)
      .invoke(LinkClaim(linked, provenance(request, principal)))
    val card = after.claims.find(_.claim.id == stated.id).getOrElse(linked)
    reads.explanations
      .claim(issue, after, card.claim.id)
      .flatMap(_.claim)
      .getOrElse(
        ClaimCard(
          stated.id,
          body.statement.trim,
          Vector.empty,
          Vector.empty,
          revises,
          Vector.empty,
          s"/public/issues/$issue/claims/${stated.id}"
        )
      )

  def evidence(issue: String): Vector[StaffEvidence] =
    val s = held(issue)
    s.evidence.map(l => view(issue, s, l.linkId))

  def reviewPrompts(issue: String, state: Option[String]): Vector[PromptView] =
    val all = held(issue).reviewPrompts
    state match
      case Some("open")     => all.filter(_.resolved.isEmpty).map(prompt)
      case Some("resolved") => all.filter(_.resolved.isDefined).map(prompt)
      case _                => all.map(prompt)

  def resolve(
      request: RequestContext,
      principal: Principal,
      issue: String,
      promptId: String,
      body: NoteBody
  ): PromptView =
    held(issue): Unit
    val after = issueEntity(issue)
      .call(IssueEntity.resolvePrompt)
      .invoke(ResolvePrompt(promptId, principal.subject, body.note, provenance(request, principal)))
    prompt(after.prompt(promptId).get)

  private def view(issue: String, s: IssueState, linkId: String): StaffEvidence =
    val l = s
      .link(linkId)
      .getOrElse(unresolved("evidence", linkId, "There is no such evidence on this issue."))
    StaffEvidence(
      reads.explanations.card(issue, l),
      l.bearsOn.map(_.id),
      l.proposedAt,
      l.decidedBy,
      l.decidedAt,
      l.reason,
      s.reviewPrompts.filter(_.evidence == linkId).map(_.promptId)
    )

/** The public side: cards, explanations, abstentions, sources and search. */
final class PublicEvidence(reach: Reach):

  private val reads = EvidenceReads(reach)

  private def notFound: Nothing = throw Rules.PublicNotFound.refused("There is no such record.")

  /**
   * Only what a reviewer decided is public: accepted evidence, and superseded evidence kept to show
   * it.
   */
  def shown(s: IssueState): Vector[EvidenceLink] =
    s.evidence.filter(l => l.state == EvidenceState.Accepted || l.state == EvidenceState.Superseded)

  def evidence(issue: String, s: IssueState, linkId: String): EvidenceCard =
    shown(s).find(_.linkId == linkId).map(reads.explanations.card(issue, _)).getOrElse(notFound)

  def cards(issue: String, s: IssueState): Vector[EvidenceCard] =
    shown(s).map(reads.explanations.card(issue, _))

  def claim(issue: String, s: IssueState, claimId: String): ClaimExplanation =
    reads.explanations.claim(issue, s, claimId).getOrElse(notFound)

  def question(issue: String, s: IssueState, questionId: String): QuestionAnswer =
    reads.explanations.question(issue, s, questionId).getOrElse(notFound)

  def questions(issue: String, s: IssueState): Vector[QuestionAnswer] =
    s.questions.flatMap(q => reads.explanations.question(issue, s, q.id))

  def claims(issue: String, s: IssueState): Vector[ClaimSummary] =
    s.claims.map(c =>
      ClaimSummary(
        c.claim.id,
        scala.util
          .Try(reach.reasoning.claim(c.claim.id))
          .toOption
          .flatten
          .flatMap(_.claim.statement)
          .getOrElse(reads.explanations.Unavailable),
        s"/public/issues/$issue/claims/${c.claim.id}"
      )
    )

  def source(id: String): SourceCardView = reads.card(id).map(sourceCard).getOrElse(notFound)

  /** Keyword search over the public view only; the snippet is cut from the row's public text. */
  def search(q: String, limit: Int): Vector[Hit] =
    if q.trim.isEmpty then Vector.empty
    else
      reach.views
        .forView(PublicSearch)
        .ask(PublicSearch.search, "q" -> q.trim)
        .toVector
        .take(limit.max(1).min(100))
        .map(r =>
          Hit(
            r.kind,
            r.recordId,
            r.version,
            r.date,
            r.title,
            PublicEvidence.snippet(r.text, q),
            r.link
          )
        )

object PublicEvidence:
  /** Up to 160 characters of `text` around the first word of `q` it holds. */
  def snippet(text: String, q: String): String =
    val flat  = text.replace('\n', ' ')
    val lower = flat.toLowerCase
    val at = q.toLowerCase
      .split("\\W+")
      .filter(_.length > 2)
      .map(lower.indexOf(_))
      .filter(_ >= 0)
      .minOption
      .getOrElse(0)
    val from = (at - 60).max(0)
    val to   = (from + 160).min(flat.length)
    (if from > 0 then "…" else "") + flat.substring(from, to).trim + (if to < flat.length then "…"
                                                                      else "")

package voice.domain

import com.thinkmorestupidless.ankka.core.{CommandError, ErrorCode}

/**
 * Why Voice did not accept a command or could not answer a read (contracts/refusals.md).
 *
 * @param rule
 *   the rule's stable name, which a client or a test matches on
 * @param status
 *   the HTTP status it is answered with
 * @param error
 *   a sentence for a person; it may be reworded
 * @param names
 *   the identifiers the rule is about, never a personal value
 */
final case class Refusal(
    rule: String,
    status: Int,
    error: String,
    names: Map[String, String] = Map.empty
)

/** Carries a refusal out of the code that found it. Nothing has been changed when it is thrown. */
final class Refused(val refusal: Refusal) extends RuntimeException(refusal.error):
  override def fillInStackTrace(): Throwable = this

/** How a rule is met: an HTTP refusal, an answer that is not an error, or no response at all. */
enum RuleKind:
  case Http, Answer, Retry, Done, BuildTime, NoCommand

/** One rule of the catalogue: its name, its status and the names its refusal carries. */
final case class Rule(name: String, status: Int, kind: RuleKind, names: Seq[String]):

  def refusal(error: String, names: (String, String)*): Refusal =
    Refusal(name, status, error, names.toMap)

  def refused(error: String, names: (String, String)*): Refused = Refused(refusal(error, names*))

  /**
   * As a `CommandError` an entity or workflow answers with; `Refusals.fromCommandError` reads it.
   */
  def error(error: String, names: (String, String)*): CommandError =
    Refusals.toCommandError(refusal(error, names*))

/** Every rule a Voice command can break. A test holds this list to the contract. */
object Rules:
  val ParticipantErased: Rule = Rule("participant.erased", 410, RuleKind.Http, Seq("participant"))
  val SessionInviteInvalid: Rule = Rule("session.invite.invalid", 403, RuleKind.Http, Seq())
  val ParticipantCredentialInvalid: Rule =
    Rule("participant.credential.invalid", 401, RuleKind.Http, Seq())
  val ConversationNotOwned: Rule =
    Rule("conversation.not-owned", 403, RuleKind.Http, Seq("conversation"))
  val ConversationMessageEmpty: Rule =
    Rule("conversation.message.empty", 422, RuleKind.Http, Seq("conversation"))
  val ProposalNotHeld: Rule =
    Rule("proposal.not-held", 404, RuleKind.Http, Seq("conversation", "proposal"))
  val ProposalAlreadyConfirmed: Rule =
    Rule("proposal.already-confirmed", 409, RuleKind.Http, Seq("proposal", "concern"))
  val ProposalStale: Rule = Rule("proposal.stale", 409, RuleKind.Http, Seq("proposal"))
  val ContributionModeInvalid: Rule =
    Rule("contribution.mode.invalid", 422, RuleKind.Http, Seq("contribution"))
  val ContributionNotOwned: Rule =
    Rule("contribution.not-owned", 403, RuleKind.Http, Seq("contribution"))
  val ContributionStateInvalid: Rule =
    Rule("contribution.state.invalid", 409, RuleKind.Http, Seq("contribution", "state"))
  val ContributionTextNotApproved: Rule =
    Rule("contribution.text.not-approved", 409, RuleKind.Http, Seq("contribution"))
  val ContributionPrivate: Rule =
    Rule("contribution.private", 409, RuleKind.Http, Seq("contribution"))
  val ContributionNotUnderReview: Rule =
    Rule("contribution.not-under-review", 409, RuleKind.Http, Seq("contribution"))
  val ContributionSecondStepRequired: Rule =
    Rule("contribution.second-step.required", 409, RuleKind.Http, Seq("contribution", "flags"))
  val ContributionSecondStepSameClick: Rule =
    Rule("contribution.second-step.same-click", 409, RuleKind.Http, Seq("contribution"))
  val ContributionReasonRequired: Rule =
    Rule("contribution.reason.required", 422, RuleKind.Http, Seq("contribution"))
  val ContributionWithdrawn: Rule =
    Rule("contribution.withdrawn", 409, RuleKind.Http, Seq("contribution"))
  val ContributionIssueNotHeld: Rule =
    Rule("contribution.issue.not-held", 404, RuleKind.Http, Seq("contribution", "issue"))
  val ReviewNotReviewer: Rule =
    Rule("review.not-reviewer", 403, RuleKind.Http, Seq("principal-role"))
  val IssueNotHeld: Rule = Rule("issue.not-held", 404, RuleKind.Http, Seq("issue"))
  val IssueContributionDuplicate: Rule =
    Rule("issue.contribution.duplicate", 409, RuleKind.Http, Seq("issue", "pub"))
  val IssueQuestionDuplicate: Rule =
    Rule("issue.question.duplicate", 409, RuleKind.Http, Seq("issue", "question"))
  val EvidenceStateInvalid: Rule =
    Rule("evidence.state.invalid", 409, RuleKind.Http, Seq("issue", "evidence", "from", "to"))
  val EvidenceReferenceUnresolved: Rule =
    Rule("evidence.reference.unresolved", 422, RuleKind.Http, Seq("kind", "id"))
  val EvidenceNotReviewer: Rule =
    Rule("evidence.not-reviewer", 403, RuleKind.Http, Seq("principal-role"))
  val ReviewPromptNotOpen: Rule =
    Rule("review-prompt.not-open", 409, RuleKind.Http, Seq("issue", "prompt"))
  val OptionEndorsement: Rule = Rule("option.endorsement", 422, RuleKind.Http, Seq("option"))
  val QuestionUnsupported: Rule =
    Rule("question.unsupported", 200, RuleKind.Answer, Seq("question"))
  val ExpressionPriorityRange: Rule =
    Rule("expression.priority.range", 422, RuleKind.Http, Seq("issue"))
  val ExpressionOptionNotHeld: Rule =
    Rule("expression.option.not-held", 404, RuleKind.Http, Seq("issue", "option"))
  val ExpressionWithdrawn: Rule = Rule("expression.withdrawn", 409, RuleKind.Http, Seq("issue"))
  val AggregateSuppressed: Rule =
    Rule("aggregate.suppressed", 200, RuleKind.Answer, Seq("issue", "cell"))
  val InvestigationNotRepresentative: Rule =
    Rule("investigation.not-representative", 403, RuleKind.Http, Seq("principal-role"))
  val InvestigationSuggestedNotApproved: Rule =
    Rule("investigation.suggested.not-approved", 409, RuleKind.Http, Seq("investigation"))
  val ActionDateFuture: Rule = Rule("action.date.future", 422, RuleKind.Http, Seq("action", "date"))
  val ActionDateBeforeIssue: Rule =
    Rule("action.date.before-issue", 422, RuleKind.Http, Seq("action", "date", "issue"))
  val ActionFieldsRequired: Rule =
    Rule("action.fields.required", 422, RuleKind.Http, Seq("action", "missing"))
  val ActionStateBackwards: Rule = Rule("action.state.backwards", 409, RuleKind.Http, Seq("action"))
  val OutcomeActionNotHeld: Rule =
    Rule("outcome.action.not-held", 404, RuleKind.Http, Seq("issue", "action"))
  val UpdateTextRequired: Rule = Rule("update.text.required", 422, RuleKind.Http, Seq("issue"))
  val UpdateCorrectionNotHeld: Rule =
    Rule("update.correction.not-held", 404, RuleKind.Http, Seq("issue", "development"))
  val StaffInactive: Rule    = Rule("staff.inactive", 403, RuleKind.Http, Seq("principal"))
  val StaffNotOperator: Rule = Rule("staff.not-operator", 403, RuleKind.Http, Seq("principal-role"))
  val InterestDeliveryInvalid: Rule = Rule("interest.delivery.invalid", 422, RuleKind.Http, Seq())
  val InterestNotOwned: Rule        = Rule("interest.not-owned", 403, RuleKind.Http, Seq())
  val NotificationDuplicate: Rule =
    Rule("notification.duplicate", 200, RuleKind.Answer, Seq("development"))
  val NotificationNotOwned: Rule = Rule("notification.not-owned", 404, RuleKind.Http, Seq())
  val DevelopmentStale: Rule     = Rule("development.stale", 0, RuleKind.Retry, Seq("development"))
  val ErasureUnverified: Rule = Rule("erasure.unverified", 202, RuleKind.Answer, Seq("participant"))
  val ErasureAlreadyRequested: Rule =
    Rule("erasure.already-requested", 200, RuleKind.Answer, Seq("participant"))
  val DormancyReset: Rule = Rule("dormancy.reset", 0, RuleKind.Done, Seq("participant"))
  val ErasureNotOperator: Rule =
    Rule("erasure.not-operator", 403, RuleKind.Http, Seq("principal-role"))
  val ErasureReceiptPersonal: Rule = Rule("erasure.receipt.personal", 0, RuleKind.BuildTime, Seq())
  val PublicNotFound: Rule         = Rule("public.not-found", 404, RuleKind.Http, Seq())
  val PublicSearchFilter: Rule =
    Rule("public.search.filter", 0, RuleKind.BuildTime, Seq("view", "column"))
  val SealedInPublicBody: Rule  = Rule("sealed.in-public-body", 0, RuleKind.BuildTime, Seq("type"))
  val RoleRequired: Rule        = Rule("role.required", 401, RuleKind.Http, Seq())
  val SessionRequired: Rule     = Rule("session.required", 401, RuleKind.Http, Seq())
  val ContentInstructions: Rule = Rule("content.instructions", 0, RuleKind.NoCommand, Seq())

  val all: Seq[Rule] = Seq(
    ParticipantErased,
    SessionInviteInvalid,
    ParticipantCredentialInvalid,
    ConversationNotOwned,
    ConversationMessageEmpty,
    ProposalNotHeld,
    ProposalAlreadyConfirmed,
    ProposalStale,
    ContributionModeInvalid,
    ContributionNotOwned,
    ContributionStateInvalid,
    ContributionTextNotApproved,
    ContributionPrivate,
    ContributionNotUnderReview,
    ContributionSecondStepRequired,
    ContributionSecondStepSameClick,
    ContributionReasonRequired,
    ContributionWithdrawn,
    ContributionIssueNotHeld,
    ReviewNotReviewer,
    IssueNotHeld,
    IssueContributionDuplicate,
    IssueQuestionDuplicate,
    EvidenceStateInvalid,
    EvidenceReferenceUnresolved,
    EvidenceNotReviewer,
    ReviewPromptNotOpen,
    OptionEndorsement,
    QuestionUnsupported,
    ExpressionPriorityRange,
    ExpressionOptionNotHeld,
    ExpressionWithdrawn,
    AggregateSuppressed,
    InvestigationNotRepresentative,
    InvestigationSuggestedNotApproved,
    ActionDateFuture,
    ActionDateBeforeIssue,
    ActionFieldsRequired,
    ActionStateBackwards,
    OutcomeActionNotHeld,
    UpdateTextRequired,
    UpdateCorrectionNotHeld,
    StaffInactive,
    StaffNotOperator,
    InterestDeliveryInvalid,
    InterestNotOwned,
    NotificationDuplicate,
    NotificationNotOwned,
    DevelopmentStale,
    ErasureUnverified,
    ErasureAlreadyRequested,
    DormancyReset,
    ErasureNotOperator,
    ErasureReceiptPersonal,
    PublicNotFound,
    PublicSearchFilter,
    SealedInPublicBody,
    RoleRequired,
    SessionRequired,
    ContentInstructions
  )

  private val byName: Map[String, Rule] = all.map(r => r.name -> r).toMap

  def named(name: String): Option[Rule] = byName.get(name)

/**
 * A refusal crosses a component boundary as a `CommandError`, whose `details` survive only for a
 * failed workflow. The rule and its names are therefore carried in the message, as
 * `<rule> [k=v, k=v]: <sentence>`, and read back at the edge.
 */
object Refusals:

  private val Encoded = """(?s)([a-z0-9][a-z0-9.-]*) \[([^\]]*)\]: (.*)""".r

  def toCommandError(r: Refusal): CommandError =
    val names = r.names.toSeq.sortBy(_._1).map((k, v) => s"$k=$v").mkString(", ")
    CommandError(s"${r.rule} [$names]: ${r.error}", codeFor(r.status))

  def fromCommandError(e: CommandError): Option[Refusal] = e.message match
    case Encoded(rule, names, sentence) =>
      val pairs = names
        .split(", ")
        .toSeq
        .filter(_.contains('='))
        .map { p =>
          val i = p.indexOf('=')
          p.substring(0, i) -> p.substring(i + 1)
        }
        .toMap
      val status = Rules.named(rule).map(_.status).filter(_ > 0).getOrElse(statusFor(e.code))
      Some(Refusal(rule, status, sentence, pairs))
    case _ => None

  def codeFor(status: Int): ErrorCode = status match
    case 401       => ErrorCode.Unauthorized
    case 403       => ErrorCode.Forbidden
    case 404 | 410 => ErrorCode.NotFound
    case 409       => ErrorCode.Conflict
    case 503       => ErrorCode.Unavailable
    case _         => ErrorCode.BadRequest

  def statusFor(code: ErrorCode): Int = code match
    case ErrorCode.BadRequest   => 400
    case ErrorCode.Unauthorized => 401
    case ErrorCode.Forbidden    => 403
    case ErrorCode.NotFound     => 404
    case ErrorCode.Conflict     => 409
    case ErrorCode.Timeout      => 504
    case ErrorCode.Unavailable  => 503
    case _                      => 500

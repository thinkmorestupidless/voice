package voice.identity

import com.thinkmorestupidless.ankka.http.{Acl, AuthDecision, Principal, RequestContext}
import voice.Clock

/**
 * The citizen endpoint's ACL: a session token of Voice's own, verified with no I/O, admitting the
 * participant it names as the principal (R9). A request with no valid token is told to log in under
 * the rule `session.required`. Revocation and erasure are the handler's to check.
 */
object CitizenAcl:

  val Role = "citizen"

  private val Challenge = """realm="voice", error="session.required""""

  def apply(tokens: SessionTokens, clock: Clock): Acl = Acl.Authenticate(decide(tokens, clock))

  def decide(tokens: SessionTokens, clock: Clock)(context: RequestContext): AuthDecision =
    bearer(context).flatMap(tokens.verify(_, clock.now())) match
      case Some(participant) =>
        AuthDecision.Allow(Principal(subject = participant, roles = Set(Role)))
      case None => AuthDecision.Unauthenticated(Challenge)

  def bearer(context: RequestContext): Option[String] =
    context
      .header("authorization")
      .map(_.trim)
      .collect { case v if v.regionMatches(true, 0, "Bearer ", 0, 7) => v.substring(7).trim }
      .filter(_.nonEmpty)

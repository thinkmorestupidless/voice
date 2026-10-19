package voice.identity

import com.thinkmorestupidless.ankka.auth.oidc.Oidc
import com.thinkmorestupidless.ankka.http.{Acl, AuthDecision, Principal}
import voice.domain.{Rule, Rules}

/**
 * The three staff roles; they are realm roles in the staff issuer and arrive in the token (R10).
 */
enum StaffRole(val name: String):
  case Representative extends StaffRole("representative")
  case Reviewer       extends StaffRole("reviewer")
  case Operator       extends StaffRole("operator")

/**
 * The staff endpoint's ACL: a person's OIDC token from the issuer named `staff`, verified by
 * `ankka-auth-oidc`. A request with no accepted token is told so under the rule `role.required`.
 */
object StaffAcl:

  /** From the environment's issuers, as a deployed service has them. */
  def fromEnv(): Acl = wrap(Oidc.authenticate())

  /** Around any authenticating ACL, as a test builds one over its own keys. */
  def wrap(inner: Acl): Acl = inner match
    case Acl.Authenticate(decide) =>
      Acl.Authenticate { context =>
        decide(context) match
          case AuthDecision.Unauthenticated(challenge) =>
            AuthDecision.Unauthenticated(s"""$challenge, voice_rule="role.required"""")
          case other => other
      }
    case other => other

  def has(principal: Principal, role: StaffRole): Boolean = principal.roles.contains(role.name)

  /** The rule a missing role breaks, per role, from the refusals contract. */
  def ruleFor(role: StaffRole, reviewerRule: Rule = Rules.ReviewNotReviewer): Rule = role match
    case StaffRole.Representative => Rules.InvestigationNotRepresentative
    case StaffRole.Reviewer       => reviewerRule
    case StaffRole.Operator       => Rules.StaffNotOperator

  /** Throws the refusal for a principal without any of `roles`. */
  def require(principal: Principal, rule: Rule, roles: StaffRole*): Unit =
    if !roles.exists(has(principal, _)) then
      throw rule.refused(
        s"This needs the ${roles.map(_.name).mkString(" or ")} role.",
        "role" -> roles.map(_.name).mkString(",")
      )

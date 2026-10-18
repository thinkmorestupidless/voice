package voice.support

import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.jwk.{Curve, ECKey, JWKSet}
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.{JOSEObjectType, JWSAlgorithm, JWSHeader}
import com.nimbusds.jwt.{JWTClaimsSet, SignedJWT}
import com.thinkmorestupidless.ankka.auth.oidc.{Issuer, Oidc, OidcConfig, OidcVerifier}
import com.thinkmorestupidless.ankka.http.Acl

import java.util.Date
import scala.jdk.CollectionConverters.*

/**
 * The staff issuer, in the suite (V10): an ES256 key generated per JVM, a verifier over its public
 * half that `Oidc.authenticate` accepts, and tokens with realm roles as Keycloak writes them.
 */
object TestIssuer:

  val Name     = "staff"
  val Url      = "https://issuer.test/realms/voice-staff"
  val Audience = "voice-staff"

  private val key: ECKey   = ECKeyGenerator(Curve.P_256).keyID("voice-test").generate()
  private val other: ECKey = ECKeyGenerator(Curve.P_256).keyID("voice-test").generate()

  private val issuer = Issuer(Name, Url, "https://issuer.test/unused-jwks", Audience)

  val verifier: OidcVerifier =
    new OidcVerifier(
      OidcConfig(Vector(issuer)),
      _ => ImmutableJWKSet[SecurityContext](JWKSet(key.toPublicJWK))
    )

  /** The staff endpoint's inner ACL, as `Oidc.authenticate()` builds it from the environment. */
  val acl: Acl = Oidc.authenticate(verifier, "voice-staff")

  /** A token for `subject` with `roles`, valid for an hour. */
  def token(subject: String, roles: String*): String = signed(key, subject, roles)

  /** A token signed by a key the verifier does not know. */
  def forged(subject: String, roles: String*): String = signed(other, subject, roles)

  private def signed(with_ : ECKey, subject: String, roles: Seq[String]): String =
    val now = System.currentTimeMillis()
    val claims = JWTClaimsSet
      .Builder()
      .issuer(Url)
      .audience(Audience)
      .subject(subject)
      .issueTime(Date(now))
      .expirationTime(Date(now + 3600_000))
      .claim("preferred_username", subject)
      .claim("realm_access", Map("roles" -> roles.asJava).asJava)
      .build()
    val jwt = SignedJWT(
      JWSHeader
        .Builder(JWSAlgorithm.ES256)
        .keyID(with_.getKeyID)
        .`type`(JOSEObjectType.JWT)
        .build(),
      claims
    )
    jwt.sign(ECDSASigner(with_))
    jwt.serialize()

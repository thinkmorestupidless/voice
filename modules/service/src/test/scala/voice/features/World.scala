package voice.features

import voice.support.TestIssuer

import scala.collection.mutable

/** A participant a scenario names, with what only they hold. */
final case class Person(name: String, id: String, session: String, recovery: String)

/**
 * What one scenario knows: its participants, its staff, its issues, the words its citizens wrote in
 * private, and the public routes it read. Reset at the start of every scenario.
 */
final class World:
  val people: mutable.Map[String, Person]         = mutable.Map.empty
  val issues: mutable.Map[String, String]         = mutable.Map.empty
  val privateTexts: mutable.Set[String]           = mutable.Set.empty
  val publicRoutes: mutable.LinkedHashSet[String] = mutable.LinkedHashSet.empty
  val notes: mutable.Map[String, String]          = mutable.Map.empty
  var last: Option[Answer]                        = None

  def reset(): Unit =
    people.clear(); issues.clear(); privateTexts.clear(); publicRoutes.clear(); notes.clear();
    last = None

  def person(name: String): Person =
    people.getOrElse(name, throw AssertionError(s"no participant named $name in this scenario"))

  // ── HTTP, per audience ─────────────────────────────────────────────────────

  def public(path: String): Answer =
    publicRoutes += path
    remember(Harness.send("GET", "/public" + path))

  def me(person: Person, method: String, path: String, body: Option[ujson.Value] = None): Answer =
    remember(Harness.send(method, "/me" + path, body, Some(person.session)))

  def staff(
      subject: String,
      roles: Seq[String],
      method: String,
      path: String,
      body: Option[ujson.Value] = None
  ): Answer =
    remember(Harness.send(method, "/staff" + path, body, Some(TestIssuer.token(subject, roles*))))

  def reviewer(method: String, path: String, body: Option[ujson.Value] = None): Answer =
    staff("reviewer-1", Seq("reviewer"), method, path, body)

  def representative(method: String, path: String, body: Option[ujson.Value] = None): Answer =
    staff("representative-1", Seq("representative"), method, path, body)

  def operator(method: String, path: String, body: Option[ujson.Value] = None): Answer =
    staff("operator-1", Seq("operator"), method, path, body)

  private def remember(answer: Answer): Answer =
    last = Some(answer)
    answer

  def ok(answer: Answer, statuses: Int*): Answer =
    val allowed = if statuses.isEmpty then Seq(200, 201, 202, 204) else statuses
    if !allowed.contains(answer.status) then
      throw AssertionError(s"expected ${allowed.mkString("/")}, got $answer")
    answer

  /** A new participant, through the session route as a visitor would (FR-001). */
  def join(name: String): Person =
    val answer = ok(Harness.send("POST", "/me/sessions", Some(ujson.Obj("invite" -> "pilot"))))
    val j      = answer.json
    val p      = Person(name, j("participant").str, j("session").str, j("recoveryCredential").str)
    people(name) = p
    p

package voice.unit

import voice.features.Harness
import voice.support.TestIssuer

/**
 * The edge before any story: the notice, the two ACLs, the operator's routes (V9, V10, FR-003 to
 * FR-005).
 */
class FoundationSuite extends munit.FunSuite:

  override def munitTimeout = scala.concurrent.duration.Duration(180, "s")

  private def staff(
      method: String,
      path: String,
      roles: Seq[String],
      body: Option[ujson.Value] = None,
      subject: String = "op-1"
  ) =
    Harness.send(method, "/staff" + path, body, Some(TestIssuer.token(subject, roles*)))

  test("the notice is readable before a session exists") {
    val answer = Harness.send("GET", "/me/notice")
    assertEquals(answer.status, 200)
    assert(answer.body.contains("How AI is used"), answer.body)
  }

  test("a citizen route with no session is told to log in under session.required") {
    val answer = Harness.send("GET", "/me/concerns")
    assertEquals(answer.status, 401)
    assert(
      answer.header("www-authenticate").exists(_.contains("session.required")),
      answer.headers.toString
    )
  }

  test("a staff route with no token, or a forged one, is told role.required") {
    val none = Harness.send("GET", "/staff/accounts")
    assertEquals(none.status, 401)
    assert(none.header("www-authenticate").exists(_.contains("role.required")))
    val forged =
      Harness.send("GET", "/staff/accounts", None, Some(TestIssuer.forged("op-1", "operator")))
    assertEquals(forged.status, 401)
  }

  test("an operator provisions an account; another role may not") {
    val put = staff(
      "PUT",
      "/accounts/rep-9",
      Seq("operator"),
      Some(ujson.Obj("displayName" -> "Cllr Rivers", "authority" -> "Ward councillor, Northfield"))
    )
    assertEquals(put.status, 200, put.toString)
    assertEquals(put.json("authority").str, "Ward councillor, Northfield")
    val refused = staff("GET", "/accounts", Seq("reviewer"))
    assertEquals(refused.status, 403)
    assertEquals(refused.rule, "staff.not-operator")
    val listed = staff("GET", "/accounts", Seq("operator"))
    assertEquals(listed.status, 200)
  }

  test("a deprovisioned account is refused every staff command") {
    staff(
      "PUT",
      "/accounts/op-2",
      Seq("operator"),
      Some(ujson.Obj("displayName" -> "Former operator", "active" -> false))
    )
    val answer = staff("GET", "/accounts", Seq("operator"), subject = "op-2")
    assertEquals(answer.status, 403)
    assertEquals(answer.rule, "staff.inactive")
  }

  test("the access log answers the operator") {
    val answer = staff("GET", "/access-log", Seq("operator"))
    assertEquals(answer.status, 200, answer.toString)
  }

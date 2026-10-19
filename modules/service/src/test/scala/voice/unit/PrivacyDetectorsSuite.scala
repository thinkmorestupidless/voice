package voice.unit

import voice.application.disclosure.PrivacyDetectors
import voice.domain.PrivacyFlag

/** The deterministic detectors (T051): each category flags, and a reason never quotes the text. */
class PrivacyDetectorsSuite extends munit.FunSuite:

  test("identifying details are found and flag enhanced review") {
    val text  = "Call me on 07700 900123 or jo@example.org, I live at 12 Mill Lane, NF1 2AB."
    val check = PrivacyDetectors.check(text)
    assertEquals(
      check.detections.map(_.kind).distinct.toSet,
      Set("phone", "email", "address", "postcode")
    )
    assertEquals(check.flags, Vector(PrivacyFlag.Distinctive))
    for d <- check.detections do assert(d.from >= 0 && d.to <= text.length)
    for r <- check.reasons do assert(!r.contains("Mill") && !r.contains("07700"), r)
  }

  test("a child, a health detail and a small group each set their own flag") {
    assertEquals(
      PrivacyDetectors.check("My daughter walks to school").flags,
      Vector(PrivacyFlag.Minor)
    )
    assertEquals(PrivacyDetectors.check("I use a wheelchair").flags, Vector(PrivacyFlag.Health))
    assertEquals(
      PrivacyDetectors.check("We are the only family on the lane").flags,
      Vector(PrivacyFlag.SmallGroup)
    )
  }

  test("an ordinary de-identified derivative is not flagged") {
    assertEquals(
      PrivacyDetectors.check("The school bus to the academy was withdrawn in September.").flags,
      Vector.empty
    )
  }

package voice.domain

import voice.domain.accountability.Action

/**
 * `action.fields.required`: a missing field is named, and a state Voice does not know names the
 * ones it does.
 */
class ActionSuite extends munit.FunSuite:

  test("a missing field is named") {
    val refusal = Action.checkFields("act-1", "date" -> "", "state" -> "proposed").get
    assertEquals(refusal.rule, "action.fields.required")
    assertEquals(refusal.names("missing"), "date")
  }

  test("a state given by its Scala name is refused with the wire names, under the same rule") {
    val refusal = Action.unknownState("act-1", "Proposed")
    assertEquals(refusal.rule, "action.fields.required")
    assertEquals(refusal.names("missing"), "state")
    assert(refusal.error.contains("proposed, in-progress, completed"), refusal.error)
    assert(refusal.error.contains("'Proposed'"), refusal.error)
  }

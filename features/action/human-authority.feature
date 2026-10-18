Feature: Human authority over investigation and action
  A representative investigates an issue and records a dated, accountable action. An agent may
  suggest; only a representative decides. A proposed action is never shown as done.

  Background:
    Given a public issue with published contributions

  Scenario: the work queue shows what waits and for how long
    Given a representative
    When the representative opens the work queue
    Then the work queue shows new issues, items pending review, open investigations and external follow-ups
    And each shows its state and its age

  Scenario: a representative sees concerns in the form each participant authorised
    Given a representative
    And a contribution shared as "representative only" and a concern kept "private"
    When the representative opens the issue
    Then the representative sees the "representative only" contribution
    And the representative does not see the "private" concern or any original
    And the measures shown are labelled as participant-derived

  Scenario: a suggested investigation stays suggested
    Given the research agent suggests an investigation
    When no representative has approved the suggestion
    Then the investigation is labelled "suggested"
    And no investigation is open
    And no action is shown as performed

  Scenario: an action record carries its authority and who recorded it
    Given a representative
    When the representative records an action with an authority, an owner, a date, a rationale, a state and a public summary
    Then the action is held with each of them
    And the action shows who recorded it and when

  Scenario Outline: only a representative records an action
    Given a person with the role "<role>"
    When the person records an action on the issue
    Then the person is refused
    And the refusal is logged

    Examples:
      | role     |
      | reviewer |
      | citizen  |
      | operator |

  Scenario: a proposed action is never shown as completed
    Given an action in the state "proposed"
    When the action is shown anywhere
    Then the action is not shown as completed
    And the action is not shown as a commitment honoured

  Scenario: the chronology labels every entry with one state
    When the chronology of the issue is shown
    Then every entry carries exactly one of the state labels "submitted", "AI-interpreted", "under moderation", "staff-acknowledged", "under investigation", "action recorded" and "outcome verified"
    And an open item with no response from the representative remains shown as unanswered

  Scenario: an outcome links to its action and overstates nothing
    Given an action and an outcome recorded for it
    When the outcome is shown
    Then the outcome links to the action it evaluates
    And the outcome states what was measured and its limitations
    And the outcome does not claim the problem is solved unless the outcome record says so

Feature: One truthful update to each follower
  A participant chooses to follow an issue. When a development is recorded on it, each eligible
  follower receives exactly one update that says what happened and why it matters to them, worded
  no stronger than the record.

  Background:
    Given a public issue "A"

  Scenario: following is an explicit choice with a default delivery
    Given a participant reading the issue
    When the participant follows the issue
    Then an interest is created for the participant
    And the delivery is the inbox
    And the participant can stop following

  Scenario: only an eligible follower receives a development
    Given a participant "Alice" who follows the issue with inbox delivery
    And a participant "Bob" who does not follow the issue
    When a representative records an action "X" on the issue
    And the development is processed
    Then exactly one update about the action is in the inbox of "Alice"
    And no update is in the inbox of "Bob"
    And the update names the action and its record
    And the update does not claim the problem is solved

  Scenario: a development processed again sends no second update
    Given a development already delivered to a follower
    When the development is processed again
    Then no second update about the development reaches any follower

  Scenario: an immaterial development sends no update
    Given a participant who follows the issue
    When a typographical correction is recorded on the issue
    Then no update is sent

  Scenario: an update explains itself and matches the record
    Given an update in a follower's inbox
    When the follower opens the update
    Then the update says why it was sent to the follower
    And the update links to the records it is about
    And the update's wording of human review and action state matches the issue's state when the update was prepared

  Scenario: a former follower receives nothing further
    Given a participant who followed the issue and stopped following
    When a representative records an action on the issue
    Then no update reaches the participant
    And the participant's earlier updates remain in the inbox

  Scenario: a failed delivery is retried without a duplicate
    Given an update prepared for a follower
    When the delivery fails once and is retried
    Then the failure is recorded
    And the follower receives the update once and whole

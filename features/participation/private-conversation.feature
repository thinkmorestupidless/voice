Feature: Private conversation
  A citizen talks to Voice about a concern without giving who they are. Voice proposes what it
  understood, and nothing leaves the conversation until the citizen confirms it and chooses to share.

  Scenario: a citizen starts a conversation with no identity
    Given a visitor with an invite code and no account
    When the visitor starts a session
    Then the visitor is a participant with no name, no email and no verified identity
    And the participant is shown the privacy notice and the AI notice before the first message is accepted
    And the participant is given a recovery credential once

  Scenario: a clarifying question proposes no position
    Given a participant describing a concern in a conversation
    When Voice asks a clarifying question
    Then the question proposes no position, no cause and no culprit
    And the participant may decline the question and continue

  Scenario: a proposal shows what it came from and nothing is shared
    Given a conversation holding a concern in the participant's own words
    When Voice proposes a structured concern
    Then the proposal shows the original statement, a summary, the passage each element came from, a confidence and the related issues it suggests
    And no contribution exists

  Scenario: a confirmed concern records the participant's edited meaning
    Given a proposal in a conversation
    When the participant edits the summary and confirms the proposal
    Then the concern holds the edited summary
    And the concern is confirmed by the participant and not by Voice

  Scenario: a confirmed concern is not public
    Given a confirmed concern
    When the participant does nothing further
    Then no contribution exists
    And public search finds neither the conversation nor the concern

  Scenario: a participant returns with the recovery credential
    Given a participant with a conversation and a confirmed concern who has closed the session
    When the participant recovers the session with the recovery credential
    Then the participant finds the conversation and the confirmed concern

  Scenario: a participant without the recovery credential starts afresh
    Given a participant with a conversation who has closed the session
    When a visitor starts a session without the recovery credential
    Then the visitor is a new participant
    And the earlier conversation is not reachable

  Scenario: instructions in a message are content
    Given a conversation
    When the participant sends a message containing instructions aimed at Voice
    Then the message is held as the participant's words
    And nothing changes but the conversation
    And Voice does not say it obeyed the instructions

Feature: Exact approval of what is shared
  A participant chooses a sharing mode for a confirmed concern and approves the exact text and the
  exact audience. A reviewer checks a shared derivative before anyone else sees it. The original
  never leaves the conversation.

  Background:
    Given a participant with a confirmed concern

  Scenario: four sharing modes are offered with private first
    When the participant opens sharing for the concern
    Then the sharing modes offered are "private", "de-identified", "representative only" and "publicly attributed"
    And each sharing mode is explained in plain words
    And "private" is the sharing mode already chosen

  Scenario: the participant approves only an edited derivative
    Given Voice proposes a derivative containing an identifying detail
    When the participant removes the detail and approves the derivative as "de-identified"
    Then the original is unchanged and private
    And the derivative is the edited text and nothing else
    And only the derivative is eligible for a shared issue

  Scenario: a derivative awaiting review is seen by the reviewer alone
    Given a derivative approved by the participant as "de-identified"
    When the participant submits the derivative
    Then the reviewer sees the derivative
    And nobody else sees the derivative
    And the participant sees the contribution as "under moderation"
    And no public record of the derivative exists

  Scenario: an approved derivative joins its issue
    Given a derivative under moderation
    When the reviewer approves the derivative into an issue
    Then the derivative is shown on the issue to its audience
    And the sharing mode is recorded with the contribution

  Scenario: a rejected derivative is not published
    Given a derivative under moderation
    When the reviewer rejects the derivative with a reason
    Then the participant sees the rejection and its reason
    And nothing is published

  Scenario: a suggested issue is accepted or a new issue asked for
    Given a derivative approved by the participant and a suggested issue it matches
    When the participant accepts the suggested issue
    Then the association of the contribution with the issue is recorded apart from the original
    And the association can be reviewed and changed later

  Scenario: a withdrawn disclosure leaves its audience and the counts
    Given a derivative published on an issue
    When the participant withdraws the disclosure
    Then the derivative is no longer shown to its audience
    And the issue's participant count no longer includes the contribution
    And the chronology of the issue records a withdrawal without the withdrawn text

  Scenario: public reading never returns a private field
    Given a conversation, a confirmed concern and a published de-identified derivative
    When a reader uses public search and reads the issue
    Then no original, no private field and no participant identifier is returned
    And no snippet, count or error message contains them

  Scenario: a distinctive derivative needs enhanced review
    Given a derivative approved by the participant that describes a distinctive circumstance, a health detail, a minor or a very small group
    When the participant submits the derivative
    Then the derivative is flagged for enhanced review
    And the reviewer cannot approve the derivative for the public in one step

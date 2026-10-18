Feature: Erasure that leaves anonymous history standing
  A participant asks for their personal data to be erased. Their protected fields become unreadable
  everywhere, their private records go, and the public issue keeps its anonymous history.

  Background:
    Given a participant "Alice" who has a conversation, a published de-identified derivative, a priority, an interest and an update in the inbox
    And a participant "Bob" who contributed to the same issue

  Scenario: an erasure request says what will go and what will stay
    When "Alice" requests erasure
    Then the request is held
    And "Alice" is told what will be erased, what is retained and why, and what remains public

  Scenario: protected fields are unreadable after erasure
    Given the erasure request of "Alice" is verified
    When the erasure completes
    Then every protected field of "Alice" is unreadable
    And every protected field of "Alice" is unreadable after the service restarts
    And every protected field of "Alice" is unreadable after a restore from a snapshot

  Scenario: rebuilt views hold nothing erased
    Given the erasure of "Alice" has completed
    When the views and the search index are rebuilt from the records
    Then nothing of the erased content of "Alice" reappears

  Scenario: the public issue keeps its anonymous history
    Given the erasure of "Alice" has completed
    When a reader reads the issue
    Then the issue's evidence, actions, decisions and anonymous chronology are intact
    And no reasonably identifying residue of "Alice" remains, in counts or in distinctive combinations

  Scenario: erasing one participant leaves another intact
    Given the erasure of "Alice" has completed
    When "Bob" reads his contributions, interests and inbox
    Then the contributions, interests and updates of "Bob" are intact

  Scenario: the receipt records the erasure and reproduces nothing erased
    Given the erasure of "Alice" has completed
    When the operator reads the receipt
    Then the receipt records the classes of data erased, when, who authorised it and that completion was verified
    And the receipt contains none of the erased content

  Scenario: an erased participant is notified of nothing further
    Given the erasure of "Alice" has completed
    When a representative records an action on the issue
    Then no update is prepared for "Alice"
    And the inbox and interests of "Alice" are gone

  Scenario: an erased field renders as unavailable
    Given the erasure of "Alice" has completed
    When a record of "Alice" with a protected field is shown
    Then the field is shown as "redacted or unavailable"
    And nothing fails

  Scenario: a dormant participant is erased and the retained contribution says why
    Given a participant "Carol" with a published de-identified derivative whose last session ended longer ago than the dormancy period
    When the dormancy period is checked
    Then "Carol" is erased with the cause "dormancy"
    And the retained derivative on the issue carries the cause "dormancy"
    And the issue's participant count shows how many authors were erased and why

  Scenario: a returning participant resets the dormancy clock
    Given a participant "Carol" whose last session ended one day before the dormancy period ends
    When "Carol" recovers the session
    Then nothing of "Carol" is erased
    And the dormancy period for "Carol" starts again

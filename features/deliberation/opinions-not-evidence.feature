Feature: Opinions apart from evidence
  A participant says how much an issue matters to them and what trade-offs they would accept. These
  are statements by a person, shown only as labelled aggregates, and never mixed with evidence.

  Background:
    Given a public issue with options

  Scenario: a priority is an opinion and never evidence
    Given a participant reading the issue
    When the participant expresses a priority and a trade-off
    Then each is held as that participant's statement, labelled as opinion
    And neither appears among the issue's claims or evidence

  Scenario: an option is described without endorsement
    When a participant reads the options
    Then each option shows its costs, its benefits and its open questions
    And no option is endorsed
    And the participant can record a consideration against any option

  Scenario: an aggregate names its sample and makes no population claim
    Given priorities from a self-selected set of participants
    When an aggregate of the priorities is shown
    Then the aggregate states the number of participants, the number of contributions, the collection method, the time window, the deduplication basis and a representativeness caveat
    And the aggregate makes no claim about the proportion of all constituents

  Scenario: a small cell is suppressed
    Given fewer participants than the suppression threshold in a cell
    When the aggregate is shown
    Then the cell is suppressed
    And no participant or small group can be inferred from the aggregate

  Scenario: a priority is amended or withdrawn
    Given a participant who has expressed a priority
    When the participant amends the priority
    Then the aggregate reflects the amended priority

  Scenario: no individual is ranked and no profile is inferred
    When any public or representative view of the issue is shown
    Then no ranking of individuals appears
    And no ideological or demographic profile of any participant appears

  Scenario: the concerns dashboard labels participant-derived measures
    Given an issue with contributions from a self-selected sample
    When the representative opens the concerns dashboard
    Then every measure is labelled by participants and time window
    And no claim about the proportion of all constituents is made

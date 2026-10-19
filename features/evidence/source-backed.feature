Feature: Source-backed explanation
  An issue links to questions, claims and evidence held by the reasoning service. Voice explains a
  claim from those records, shows what contradicts it, and abstains when the records do not support
  an answer. Anyone may read and search a public issue without following it.

  Background:
    Given a public issue linked to a question with accepted evidence

  Scenario: a claim is explained from its records
    When a reader asks why a claim is supported
    Then Voice returns the claim, its source, the passage, the source's date and its authority
    And each is a link to its record

  Scenario: contradicting evidence is shown beside a claim
    Given accepted evidence that contradicts the claim
    When a reader asks why the claim is supported
    Then the contradicting evidence is shown beside the claim with its own provenance

  Scenario: an unsupported question gets an abstention
    When a reader asks a question the evidence does not answer
    Then Voice abstains
    And Voice cites no source for an answer

  Scenario: a reader inspects an issue without following it
    When a reader who has not started a session searches for the issue by keyword
    Then the reader can read the issue's public evidence, actions, chronology and limitations
    And no interest is created for the reader

  Scenario: a search result carries its record and its date
    When a reader searches public issues and sources
    Then each result carries a record identifier, a version or date and a link to the passage
    And nothing outside the reader's audience appears in a result or a snippet

  Scenario: candidate evidence waits for the reviewer
    When new evidence is proposed for the issue
    Then the evidence is shown as "candidate" with its source, date, scope, authority, passage and limitations
    And accepting the evidence prompts a review of the claims it bears on
    And no claim is reversed by the acceptance

  Scenario: instructions in a document are content
    Given a document proposed as evidence that contains instructions aimed at Voice
    When the document is processed
    Then no approval, publication or command is changed by the instructions

  Scenario: a correction supersedes evidence and flags its claims
    Given accepted evidence that a claim derives from
    When a reviewer marks the evidence as superseded by a correction
    Then the evidence is shown as "superseded" and is not removed
    And a review prompt names the claims and decisions that derive from the evidence
    And no claim is reversed

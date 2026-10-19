import { test } from "node:test";
import assert from "node:assert/strict";
import { approval, chosenMode, nextSteps, outcomeText, stillPresent, type ContributionView, type SharingOptions } from "../src/pages/citizen/sharing.ts";
import { age, approveBody, decidable, defaultIssue, needsSecondStep, ordered, type ReviewItem } from "../src/pages/staff/reviews.ts";

const options: SharingOptions = {
  concern: "cnc-1",
  modes: [
    { mode: "private", label: "private", explanation: "x", audience: "you alone", chosen: true },
    { mode: "de-identified", label: "de-identified", explanation: "y", audience: "the public", chosen: false },
  ],
  derivative: "My daughter walks along Mill Lane.",
  flags: ["Minor"],
  reasons: ["it mentions a child"],
  detections: [{ kind: "minor", passage: "daughter" }],
  suggestedIssues: [],
  contributions: [],
};

const contribution = (state: string, extra: Partial<ContributionView> = {}): ContributionView => ({
  id: "ctb-1",
  concern: "cnc-1",
  mode: "de-identified",
  audience: "the public",
  state,
  text: "t",
  association: { newIssueAsked: false },
  suggestedIssues: [],
  ...extra,
});

const item = (extra: Partial<ReviewItem> = {}): ReviewItem => ({
  contribution: "ctb-1",
  derivative: "t",
  mode: "de-identified",
  audience: "the public",
  status: "AwaitingReviewer",
  flags: [],
  reasons: [],
  association: { newIssueAsked: false },
  suggestedIssues: [{ issue: "iss-2", title: "B", score: 0.4 }],
  ageSeconds: 60,
  ...extra,
});

test("private is chosen first and needs no approval", () => {
  assert.equal(chosenMode(options), "private");
  assert.ok(approval("private", "anything", "").problem);
});

test("the approval carries the exact text, and a name only when attributed", () => {
  assert.deepEqual(approval("de-identified", "  the text  ", "Sam").body, { mode: "de-identified", text: "the text" });
  assert.ok(approval("public-attribution", "the text", " ").problem);
  assert.deepEqual(approval("public-attribution", "the text", "Sam").body, { mode: "public-attribution", text: "the text", name: "Sam" });
  assert.ok(approval("de-identified", "   ", "").problem);
});

test("a detail the participant removed is no longer shown as present", () => {
  assert.equal(stillPresent(options.derivative, options.detections).length, 1);
  assert.equal(stillPresent("A walk along Mill Lane.", options.detections).length, 0);
});

test("what a participant can do follows the contribution's state, and a rejection shows its reason", () => {
  assert.deepEqual(nextSteps(contribution("approved by you")), ["submit", "associate", "withdraw"]);
  assert.deepEqual(nextSteps(contribution("withdrawn")), []);
  assert.match(outcomeText(contribution("not published", { review: { outcome: "rejected", reason: "it names a neighbour", at: "x" } }))!, /names a neighbour/);
  assert.equal(outcomeText(contribution("draft")), undefined);
});

test("a flagged derivative needs its second step before approval, and approval never carries it", () => {
  assert.ok(needsSecondStep(item({ flags: ["Minor"] })));
  assert.ok(!needsSecondStep(item({ flags: ["Minor"], secondStep: { reviewer: "r", reason: "ok", at: "x" } })));
  assert.ok(!decidable(item({ status: "Checking" })));
  assert.deepEqual(approveBody("iss-1", "", ""), { issue: "iss-1" });
  assert.equal(approveBody(undefined, " ", ""), undefined);
  assert.deepEqual(approveBody(undefined, "Title", ""), { new: { title: "Title", summary: "" } });
});

test("the participant's issue choice comes first, and the queue is oldest first", () => {
  assert.equal(defaultIssue(item({ association: { issue: "iss-9", newIssueAsked: false } })), "iss-9");
  assert.equal(defaultIssue(item({ association: { newIssueAsked: true } })), undefined);
  assert.equal(defaultIssue(item()), "iss-2");
  assert.deepEqual(ordered([item({ contribution: "a", ageSeconds: 5 }), item({ contribution: "b", ageSeconds: 50 })]).map((i) => i.contribution), ["b", "a"]);
  assert.equal(age(90000), "1 d");
});

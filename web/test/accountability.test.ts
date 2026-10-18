import { test } from "node:test";
import assert from "node:assert/strict";
import { actionBody, ageText, missingFields, oldestFirst, runDone, suggestions, type ActionDraft, type QueueItem, type ResearchRun } from "../src/pages/staff/accountability.ts";
import { match, withQuery } from "../src/app/paths.ts";

const draft: ActionDraft = {
  authority: "Cabinet member for transport (illustrative)",
  owner: "Transport team",
  date: "2026-11-02",
  rationale: "Pupils need a safe way to school.",
  state: "proposed",
  publicSummary: "Officers were asked to cost a footpath.",
};

test("an action is recorded only with every field", () => {
  assert.deepEqual(missingFields(draft), []);
  assert.deepEqual(actionBody({ ...draft, owner: " " }), undefined);
  assert.deepEqual(missingFields({ ...draft, owner: "", publicSummary: "" }), ["the owner", "the public summary"]);
  assert.equal(actionBody({ ...draft, investigation: "inv-1" })?.investigation, "inv-1");
  assert.equal("investigation" in (actionBody(draft) ?? {}), false);
});

test("the queue puts what has waited longest first, and says how long", () => {
  const item = (id: string, ageSeconds: number): QueueItem => ({ kind: "investigation", id, title: id, state: "open", since: "x", ageSeconds, link: "/" });
  assert.deepEqual(oldestFirst([item("a", 5), item("b", 500)]).map((i) => i.id), ["b", "a"]);
  assert.equal(ageText(30), "just now");
  assert.equal(ageText(60), "1 minute");
  assert.equal(ageText(7200), "2 hours");
  assert.equal(ageText(3 * 86400), "3 days");
});

test("every research item keeps the label the service gave it", () => {
  const run: ResearchRun = {
    run: "run-1",
    issue: "iss-1",
    state: "done",
    sources: [{ label: "suggested", item: { name: "Boarding records", why: "counts differ" } }],
    questions: [],
    investigations: [{ label: "suggested", item: { question: "Why do counts differ?", reason: "two numbers", records: [] } }],
    recorded: ["inv-1"],
  };
  assert.deepEqual(suggestions(run).map((s) => s.label), ["suggested", "suggested"]);
  assert.equal(runDone(run), true);
  assert.equal(runDone({ ...run, state: "running" }), false);
});

test("a page's query reaches it, and a path parameter wins", () => {
  const path = "/staff/issues/iss-1/actions?investigation=inv-2";
  assert.deepEqual(withQuery(path, match("/staff/issues/:issue/actions", path) ?? {}), { issue: "iss-1", investigation: "inv-2" });
  assert.deepEqual(withQuery("/issues/iss-1?issue=other", { issue: "iss-1" }), { issue: "iss-1" });
});

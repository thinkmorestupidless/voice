import { test } from "node:test";
import assert from "node:assert/strict";
import { basisText, cellText, scale, windowText, type Aggregate } from "../src/pages/public/aggregate.ts";
import { canWithdraw, stanceOn, type ExpressionView } from "../src/pages/citizen/expression.ts";

const agg = (extra: Partial<Aggregate> = {}): Aggregate => ({
  participants: { key: "participants", count: 6, suppressed: false },
  contributions: { key: "contributions", count: 8, suppressed: false },
  method: "Self-selected.",
  window: { from: "2026-11-02", to: "2026-11-03" },
  deduplication: "One expression per participant per issue.",
  caveat: "Opinions, not evidence.",
  threshold: 5,
  priorities: [
    { key: "1", count: 0, suppressed: false },
    { key: "5", count: 6, suppressed: false },
    { key: "2", suppressed: true },
  ],
  options: [],
  ...extra,
});

test("a suppressed cell never shows a number", () => {
  assert.equal(cellText({ key: "2", suppressed: true }, 5), "fewer than 5, not shown");
  assert.equal(cellText({ key: "5", count: 6, suppressed: false }, 5), "6");
});

test("every figure says who it counts and when", () => {
  assert.equal(basisText(agg()), "6 participants who chose to answer, from 2026-11-02 to 2026-11-03.");
  assert.equal(windowText({}), "not shown while the sample is small");
  assert.equal(windowText({ from: "2026-11-02", to: "2026-11-02" }), "on 2026-11-02");
  assert.deepEqual(scale(agg()).map((c) => c.key), ["5", "2", "1"]);
});

test("the participant's stance and what may be withdrawn", () => {
  const e: ExpressionView = { issue: "iss-1", label: "opinion", tradeOffs: [{ option: "pol-1", stance: "accepts", text: "", at: "x" }], considerations: [], withdrawn: false, notice: "n" };
  assert.equal(stanceOn(e, "pol-1"), "accepts");
  assert.equal(stanceOn(e, "pol-2"), undefined);
  assert.equal(canWithdraw(e), true);
  assert.equal(canWithdraw({ ...e, withdrawn: true }), false);
  assert.equal(canWithdraw({ ...e, tradeOffs: [] }), false);
});

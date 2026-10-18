import { test } from "node:test";
import assert from "node:assert/strict";
import { abstentionText, hitLine, page, passageText, shownDate, sides, stateText, type ClaimExplanation, type EvidenceCard } from "../src/pages/public/model.ts";
import { draftBody, moves, openFirst, replacements, type PromptView, type StaffEvidence } from "../src/pages/staff/evidence.ts";
import { match } from "../src/app/paths.ts";

const card = (extra: Partial<EvidenceCard> = {}): EvidenceCard => ({
  id: "evl-1",
  state: "accepted",
  source: { id: "voice.src.a", name: "A (illustrative)", licence: "CC0", attribution: "fictional", link: "https://voice.example/a", record: "/public/sources/voice.src.a" },
  passage: "Route 14 carried 11 pupils a day.",
  publishedAt: "2026-01-20T00:00:00Z",
  scope: "s",
  authority: "a",
  limitations: "l",
  link: "/public/issues/iss-1/evidence/evl-1",
  ...extra,
});

const staff = (state: string, id = "evl-1"): StaffEvidence => ({ card: card({ id, state }), bearsOn: [], proposedAt: "2026-01-01T00:00:00Z", prompts: [] });

test("a service link is a page of this interface", () => {
  assert.equal(page("/public/issues/iss-1/claims/voice.iss-1.c1"), "/issues/iss-1/claims/voice.iss-1.c1");
  assert.deepEqual(match("/issues/:issue/claims/:claim", page("/public/issues/iss-1/claims/voice.iss-1.c1")), { issue: "iss-1", claim: "voice.iss-1.c1" });
});

test("a card shows the passage verbatim, a withdrawal's note, or says it is unavailable", () => {
  assert.equal(passageText(card()), "Route 14 carried 11 pupils a day.");
  assert.equal(passageText(card({ withdrawn: "licence ended", passage: undefined })), "Withdrawn from the record: licence ended");
  assert.equal(passageText(card({ passage: undefined })), "The passage is not available.");
  assert.equal(shownDate(card()), "2026-01-20");
  assert.equal(shownDate(card({ publishedAt: undefined })), "undated");
  assert.equal(stateText(card({ state: "superseded", supersededBy: "evl-2" })), "superseded by evl-2");
});

test("support and contradiction are set side by side", () => {
  const x: ClaimExplanation = {
    claim: { id: "c1", statement: "s", stances: [], evidence: [card()], revisedBy: [], link: "/public/x" },
    contradicting: [{ id: "c2", statement: "t", stances: [], evidence: [card({ id: "evl-2" })], revisedBy: [], link: "/public/y" }],
    abstained: false,
  };
  const { support, against } = sides(x);
  assert.equal(support[0].id, "evl-1");
  assert.equal(against[0].claim.id, "c2");
  assert.equal(against[0].cards[0].id, "evl-2");
  assert.equal(abstentionText(x), undefined);
  assert.equal(abstentionText({ abstained: true }), "The records Voice holds do not support an answer to this.");
});

test("a search hit says what it is and when", () => {
  assert.equal(hitLine({ kind: "issue", id: "iss-1", date: "2026-11-02T09:00:00Z", title: "t", snippet: "s", link: "/public/issues/iss-1" }), "issue · iss-1 · 2026-11-02");
  assert.equal(hitLine({ kind: "source", id: "voice.src.a", version: "v2", title: "t", snippet: "s", link: "/public/sources/voice.src.a" }), "source · voice.src.a · v2");
});

test("the reviewer's moves follow the evidence's state", () => {
  assert.deepEqual(moves(staff("candidate")), ["accept", "reject"]);
  assert.deepEqual(moves(staff("accepted")), ["supersede"]);
  assert.deepEqual(moves(staff("rejected")), []);
  assert.deepEqual(moves(staff("superseded")), []);
  const all = [staff("accepted", "evl-1"), staff("accepted", "evl-2"), staff("candidate", "evl-3")];
  assert.deepEqual(replacements(all, all[0]).map((e) => e.card.id), ["evl-2"]);
});

test("a proposal needs every field the card shows", () => {
  const d = { source: "voice.src.a", locator: "p1", excerpt: "e", scope: "s", authority: "a", limitations: "l", publishedAt: "", bearsOn: ["q1/h1", ""] };
  assert.deepEqual(draftBody(d), { ...d, publishedAt: undefined, bearsOn: ["q1/h1"] });
  assert.equal(draftBody({ ...d, limitations: " " }), undefined);
});

test("open review prompts come first", () => {
  const p = (id: string, at: string, resolved = false): PromptView => ({
    id, evidence: "evl-1", cause: "accepted", claims: [], decisions: [], raisedAt: at,
    ...(resolved ? { resolved: { by: "r", at, note: "n" } } : {}),
  });
  assert.deepEqual(openFirst([p("a", "2026-01-01", true), p("b", "2026-01-03"), p("c", "2026-01-02")]).map((x) => x.id), ["c", "b", "a"]);
});

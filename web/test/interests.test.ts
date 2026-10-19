import { test } from "node:test";
import assert from "node:assert/strict";
import { cursor, entryOf, follows, merge, recordPage, unread, type InboxEntry } from "../src/pages/citizen/interests.ts";

const entry = (development: string, at: string, state = "delivered"): InboxEntry => ({
  development,
  issue: "iss-1",
  text: "A representative recorded an action.",
  links: ["/public/issues/iss-1#act-1"],
  why: "You follow it.",
  state,
  at,
  failedAttempts: 0,
});

test("an entry the stream sends again is held once", () => {
  const held = merge([], [entry("dev-1", "2026-11-02T09:00:00Z")]);
  const again = merge(held, [entry("dev-1", "2026-11-02T09:00:00Z", "read"), entry("dev-2", "2026-11-02T09:05:00Z")]);
  assert.deepEqual(again.map((e) => [e.development, e.state]), [["dev-1", "read"], ["dev-2", "delivered"]]);
  assert.equal(cursor(again), "2026-11-02T09:05:00Z");
  assert.equal(unread(again), 1);
});

test("only a notification event is an entry", () => {
  assert.equal(entryOf({ event: "message", data: JSON.stringify({ type: "notification", data: entry("dev-3", "x") }) })?.development, "dev-3");
  assert.equal(entryOf({ event: "message", data: JSON.stringify({ type: "message", data: {} }) }), undefined);
  assert.equal(entryOf({ event: "message", data: "not json" }), undefined);
});

test("following is read from active interests only, and a record link keeps its anchor", () => {
  assert.equal(follows([{ issue: "iss-1", title: "t", delivery: "inbox-immediate", since: "x", active: true }], "iss-1"), true);
  assert.equal(follows([{ issue: "iss-1", title: "t", delivery: "inbox-immediate", since: "x", active: false }], "iss-1"), false);
  assert.equal(recordPage("/public/issues/iss-1#act-1"), "/issues/iss-1#act-1");
});

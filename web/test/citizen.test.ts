import { test } from "node:test";
import assert from "node:assert/strict";
import { VoiceClient } from "../src/api/client.ts";
import {
  confidenceText,
  cursorOf,
  editBody,
  emptyConversation,
  fold,
  highlight,
  isOpen,
  sendable,
  suggestions,
  type ProposalView,
} from "../src/pages/citizen/model.ts";

const said = "The school bus from Northfield was withdrawn in September.";
const message = { seq: 1, author: "citizen" as const, text: said, at: "2026-11-02T09:00:00Z" };
const proposal: ProposalView = {
  id: "prp-1",
  original: said,
  summary: "A school bus was withdrawn.",
  elements: [
    { kind: "Problem", text: "bus withdrawn", spans: [{ message: 1, from: 4, to: 14, passage: "school bus" }] },
    { kind: "Where", text: "Northfield", spans: [{ message: 1, from: 20, to: 30, passage: "Northfield" }] },
    { kind: "When", text: "invented", spans: [{ message: 1, from: 50, to: 999, passage: "" }] },
  ],
  confidence: "High",
  suggestedIssues: [
    { issue: "iss-a", title: "A", score: 0.2 },
    { issue: "iss-b", title: "B", score: 0.9 },
    { issue: "iss-c", title: "C", score: 0.5 },
    { issue: "iss-d", title: "D", score: 0.1 },
  ],
  edited: false,
  state: "Open",
  fromMessages: [1],
};

const event = (type: string, data: unknown) => ({ event: "message", data: JSON.stringify({ type, data }) });

test("the proposal card highlights exactly the passages its elements came from", () => {
  const segments = highlight(message, proposal.elements);
  assert.equal(segments.map((s) => s.text).join(""), said);
  const marked = segments.filter((s) => s.kinds.length > 0);
  assert.deepEqual(
    marked.map((s) => [s.text, s.kinds]),
    [
      ["school bus", ["Problem"]],
      ["Northfield", ["Where"]],
    ],
  );
});

test("a span outside the message is not highlighted", () => {
  assert.ok(highlight(message, proposal.elements).every((s) => !s.kinds.includes("When")));
});

test("the confidence is in words, suggestions are best first and at most three", () => {
  assert.match(confidenceText("Low"), /unsure/);
  assert.equal(confidenceText("whatever"), confidenceText("Medium"));
  assert.deepEqual(suggestions(proposal).map((s) => s.issue), ["iss-b", "iss-c", "iss-a"]);
});

test("only an open proposal is edited, and an empty summary is not sent", () => {
  assert.ok(isOpen(proposal));
  assert.ok(!isOpen({ ...proposal, state: "Confirmed" }));
  assert.equal(editBody("   "), undefined);
  assert.deepEqual(editBody("  mine  "), { summary: "mine" });
  assert.ok(!sendable(""));
  assert.ok(!sendable("x".repeat(4001)));
  assert.ok(sendable("hello"));
});

test("the stream folds messages once, in order, and replaces a proposal that changed", () => {
  let view = emptyConversation("cnv-1");
  view = fold(view, event("message", { ...message, seq: 2, author: "voice", text: "Thanks" }));
  view = fold(view, event("message", message));
  view = fold(view, event("message", message));
  view = fold(view, event("proposal", proposal));
  view = fold(view, event("proposal", { ...proposal, state: "Confirmed", concern: "cnc-1" }));
  view = fold(view, { event: "message", data: "not json" });
  assert.deepEqual(view.messages.map((m) => m.seq), [1, 2]);
  assert.equal(view.proposals.length, 1);
  assert.equal(view.proposals[0].state, "Confirmed");
  assert.equal(cursorOf(view), "2");
  assert.equal(cursorOf(emptyConversation("cnv-2")), undefined);
});

test("the stream reconnects when the server ends it, resuming after the last message held", async () => {
  const urls: string[] = [];
  const stop = new AbortController();
  let view = emptyConversation("cnv-1");
  const bodies = [
    `data: ${JSON.stringify({ type: "message", data: message })}\n\n`,
    `data: ${JSON.stringify({ type: "message", data: { ...message, seq: 2, author: "voice", text: "Thanks" } })}\n\n`,
  ];
  const fake = (async (url: string) => {
    urls.push(url);
    const body = bodies.shift();
    if (body === undefined) {
      stop.abort();
      return new Response("", { status: 503 });
    }
    return new Response(body, { status: 200, headers: { "content-type": "text/event-stream" } });
  }) as typeof fetch;
  const client = new VoiceClient("/api", { citizen: () => "tok", staff: () => undefined }, fake);
  await client.stream("me", "/conversations/cnv-1/stream", (e) => (view = fold(view, e)), stop.signal, () => cursorOf(view), 1);
  assert.deepEqual(urls, [
    "/api/me/conversations/cnv-1/stream",
    "/api/me/conversations/cnv-1/stream?after=1",
    "/api/me/conversations/cnv-1/stream?after=2",
  ]);
  assert.deepEqual(view.messages.map((m) => m.seq), [1, 2]);
});

test("a refusal the caller caused ends the stream instead of retrying", async () => {
  const fake = (async () => new Response(JSON.stringify({ rule: "conversation.not-owned", status: 403, error: "not yours", names: {} }), { status: 403 })) as typeof fetch;
  const client = new VoiceClient("/api", { citizen: () => "tok", staff: () => undefined }, fake);
  await assert.rejects(client.stream("me", "/conversations/cnv-9/stream", () => {}, new AbortController().signal, undefined, 1), /not yours/);
});

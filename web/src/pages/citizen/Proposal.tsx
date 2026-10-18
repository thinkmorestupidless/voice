import { useState } from "react";
import { ApiError } from "../../api/client.ts";
import { client } from "../../app/context.ts";
import { confidenceText, editBody, highlight, isOpen, suggestions, type MessageView, type ProposalView } from "./model.ts";

/**
 * The proposal card (FR-007 to FR-009): the original statement, the summary, each element with the
 * passage it came from highlighted, the confidence in words and the Issues it suggests. The citizen
 * may edit the summary, confirm, or discard; nothing is confirmed by Voice.
 */
export function Proposal({ conversation, proposal, messages }: { conversation: string; proposal: ProposalView; messages: MessageView[] }) {
  const [summary, setSummary] = useState(proposal.summary);
  const [problem, setProblem] = useState<string | undefined>();
  const base = `/conversations/${conversation}/proposals/${proposal.id}`;
  const sources = messages.filter((m) => m.author === "citizen" && proposal.fromMessages.includes(m.seq));

  async function act(path: string, body: unknown = {}) {
    setProblem(undefined);
    try {
      await client.post("me", base + path, body);
    } catch (e) {
      setProblem(e instanceof ApiError ? e.message : "Voice could not do that; try again.");
    }
  }

  return (
    <article className="proposal" aria-label="What Voice understood">
      <h3>What Voice understood</h3>
      <p>
        <strong>You said:</strong> {proposal.original}
      </p>
      {sources.map((m) => (
        <p key={m.seq} className="source">
          {highlight(m, proposal.elements).map((s, i) =>
            s.kinds.length > 0 ? (
              <mark key={i} title={s.kinds.join(", ")}>
                {s.text}
              </mark>
            ) : (
              <span key={i}>{s.text}</span>
            ),
          )}
        </p>
      ))}
      <dl>
        {proposal.elements.map((e, i) => (
          <div key={i}>
            <dt>{e.kind}</dt>
            <dd>
              {e.text} <q>{e.spans.map((s) => s.passage).join(" … ")}</q>
            </dd>
          </div>
        ))}
      </dl>
      <p>{confidenceText(proposal.confidence)}.</p>
      {suggestions(proposal).length > 0 && (
        <p>
          It may relate to: {suggestions(proposal).map((s) => s.title).join("; ")}. You choose later whether it joins any of them.
        </p>
      )}
      {isOpen(proposal) ? (
        <>
          <label>
            Summary <textarea value={summary} onChange={(e) => setSummary(e.target.value)} rows={3} />
          </label>
          <button disabled={editBody(summary) === undefined || summary.trim() === proposal.summary} onClick={() => void act("/edit", editBody(summary))}>
            Save my wording
          </button>
          <button onClick={() => void act("/confirm")}>This is my concern</button>
          <button onClick={() => void act("/discard")}>Discard</button>
        </>
      ) : (
        <p>{proposal.state === "Confirmed" ? "You confirmed this concern." : "You discarded this."}</p>
      )}
      {problem && <p role="alert">{problem}</p>}
    </article>
  );
}

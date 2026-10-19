import { useEffect, useRef, useState } from "react";
import { ApiError } from "../../api/client.ts";
import { client, session } from "../../app/context.ts";
import { cursorOf, emptyConversation, fold, sendable, type ConversationView } from "./model.ts";
import { Proposal } from "./Proposal.tsx";

/**
 * The private conversation (FR-004 to FR-010): messages and proposals arrive on the stream, which
 * Voice ends before the gateway does, and which reconnects from the last message held (R5).
 */
export function Conversation({ id }: { id: string }) {
  const [view, setView] = useState<ConversationView>(emptyConversation(id));
  const [draft, setDraft] = useState("");
  const [thinking, setThinking] = useState(false);
  const [problem, setProblem] = useState<string | undefined>();
  const held = useRef(view);
  held.current = view;

  useEffect(() => {
    const stop = new AbortController();
    client
      .stream(
        "me",
        `/conversations/${id}/stream`,
        (event) => {
          const next = fold(held.current, event);
          if (next.messages.at(-1)?.author === "voice") setThinking(false);
          held.current = next;
          setView(next);
        },
        stop.signal,
        () => cursorOf(held.current),
      )
      .catch((e) => setProblem(e instanceof ApiError && e.rule === "conversation.not-owned" ? "This conversation is not yours." : "The conversation could not be loaded."));
    return () => stop.abort();
  }, [id]);

  async function send() {
    if (!sendable(draft)) return;
    setProblem(undefined);
    try {
      await client.post("me", `/conversations/${id}/messages`, { text: draft.trim() });
      setDraft("");
      setThinking(true);
    } catch (e) {
      setProblem(e instanceof ApiError ? e.message : "Voice could not take that message; try again.");
    }
  }

  if (!session.mayWrite()) return <p>Start a session and read the notice first.</p>;

  return (
    <section aria-labelledby="conversation-heading">
      <h2 id="conversation-heading">Your conversation</h2>
      <p>This conversation is private. Nothing in it is shared unless you choose to share it.</p>
      <ol className="messages">
        {view.messages.map((m) => (
          <li key={m.seq} className={m.author}>
            <span className="author">{m.author === "citizen" ? "You" : "Voice"}</span> {m.text}
          </li>
        ))}
      </ol>
      {thinking && <p aria-live="polite">Voice is thinking…</p>}
      {view.proposals.map((p) => (
        <Proposal key={p.id} conversation={id} proposal={p} messages={view.messages} />
      ))}
      <label>
        Your message <textarea value={draft} onChange={(e) => setDraft(e.target.value)} rows={4} maxLength={4000} />
      </label>
      <button disabled={!sendable(draft) || thinking} onClick={() => void send()}>
        Send
      </button>
      {problem && <p role="alert">{problem}</p>}
    </section>
  );
}

import { useEffect, useRef, useState } from "react";
import { ApiError } from "../../api/client.ts";
import { client, navigate } from "../../app/context.ts";
import { cursor, entryOf, merge, recordPage, unread, type InboxEntry } from "./interests.ts";

/** The participant's updates, each with why it was sent and the records it is about; live on the stream. */
export function Inbox() {
  const [entries, setEntries] = useState<InboxEntry[]>([]);
  const [problem, setProblem] = useState<string | undefined>();
  const held = useRef(entries);
  held.current = entries;

  useEffect(() => {
    const stop = new AbortController();
    client.get<InboxEntry[]>("me", "/inbox").then(
      (first) => {
        held.current = merge([], first);
        setEntries(held.current);
        client
          .stream(
            "me",
            "/inbox/stream",
            (event) => {
              const e = entryOf(event);
              if (!e) return;
              held.current = merge(held.current, [e]);
              setEntries(held.current);
            },
            stop.signal,
            () => cursor(held.current),
          )
          .catch(() => setProblem("New updates will show when you reload."));
      },
      (e) => setProblem(e instanceof ApiError ? e.message : "The inbox could not be read."),
    );
    return () => stop.abort();
  }, []);

  async function read(e: InboxEntry) {
    if (e.state !== "delivered") return;
    const after = await client.post<InboxEntry>("me", `/inbox/${encodeURIComponent(e.development)}/read`, {});
    setEntries(merge(held.current, [after]));
  }

  return (
    <main>
      <h1>Inbox</h1>
      <p>{unread(entries)} unread.</p>
      {problem && <p role="alert">{problem}</p>}
      {entries.length === 0 && <p>No updates yet. Follow an issue to be told when something is recorded on it.</p>}
      {[...entries].reverse().map((e) => (
        <article key={e.development} onClick={() => read(e)} data-state={e.state}>
          <p>{e.text}</p>
          <p>
            <small>{e.why}</small>
          </p>
          <ul>
            {e.links.map((l) => (
              <li key={l}>
                <a href={recordPage(l)} onClick={(ev) => { ev.preventDefault(); navigate(recordPage(l)); }}>
                  The record
                </a>
              </li>
            ))}
          </ul>
        </article>
      ))}
    </main>
  );
}

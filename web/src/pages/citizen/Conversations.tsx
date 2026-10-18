import { useEffect, useState } from "react";
import { client, navigate } from "../../app/context.ts";

interface Summary {
  id: string;
  messages: number;
  proposals: number;
  lastAt?: string;
}

/** The participant's conversations, found again after a recovery. */
export function Conversations() {
  const [list, setList] = useState<Summary[] | undefined>();
  useEffect(() => {
    client.get<Summary[]>("me", "/conversations").then(setList, () => setList([]));
  }, []);
  if (!list) return <p>Loading your conversations…</p>;
  return (
    <section aria-labelledby="list-heading">
      <h2 id="list-heading">Your conversations</h2>
      {list.length === 0 && <p>You have no conversations yet.</p>}
      <ul>
        {list.map((c) => (
          <li key={c.id}>
            <a href={`/me/conversations/${c.id}`} onClick={(e) => (e.preventDefault(), navigate(`/me/conversations/${c.id}`))}>
              {c.lastAt ? `Last message ${new Date(c.lastAt).toLocaleString()}` : "Not started"}, {c.messages} messages
            </a>
          </li>
        ))}
      </ul>
      <button onClick={async () => navigate(`/me/conversations/${(await client.post<{ id: string }>("me", "/conversations")).id}`)}>Start a new conversation</button>
    </section>
  );
}

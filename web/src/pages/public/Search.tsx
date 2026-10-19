import { useState } from "react";
import { ApiError } from "../../api/client.ts";
import { client, navigate } from "../../app/context.ts";
import { hitLine, page, type Hit } from "./model.ts";

/** Search public issues and sources, with no session and nothing created (FR-026, FR-045). */
export function Search() {
  const [q, setQ] = useState("");
  const [hits, setHits] = useState<Hit[] | undefined>();
  const [problem, setProblem] = useState<string | undefined>();

  async function run() {
    setProblem(undefined);
    try {
      setHits(await client.get<Hit[]>("public", `/search?q=${encodeURIComponent(q)}`));
    } catch (e) {
      setProblem(e instanceof ApiError ? e.message : "The search did not work; try again.");
    }
  }

  return (
    <main>
      <h1>Search public issues</h1>
      <form onSubmit={(e) => { e.preventDefault(); void run(); }}>
        <input aria-label="Search" value={q} onChange={(e) => setQ(e.target.value)} />
        <button disabled={q.trim() === ""}>Search</button>
      </form>
      {problem && <p role="alert">{problem}</p>}
      {hits && hits.length === 0 && <p>Nothing public matches.</p>}
      <ul>
        {hits?.map((h) => (
          <li key={`${h.kind}:${h.id}`}>
            <a href={page(h.link)} onClick={(e) => { e.preventDefault(); navigate(page(h.link)); }}>
              {h.title || h.id}
            </a>
            <p>{h.snippet}</p>
            <small>{hitLine(h)}</small>
          </li>
        ))}
      </ul>
    </main>
  );
}

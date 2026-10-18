import { useEffect, useState } from "react";
import { ApiError } from "../../api/client.ts";
import { client, navigate } from "../../app/context.ts";
import { ageText, oldestFirst, type QueueItem } from "./accountability.ts";

/** The representative's work queue: what is new, waiting or open, each with its state and age (FR-038). */
export function Queue() {
  const [items, setItems] = useState<QueueItem[] | undefined>();
  const [problem, setProblem] = useState<string | undefined>();
  useEffect(() => {
    client.get<QueueItem[]>("staff", "/queue").then(setItems, (e) => setProblem(e instanceof ApiError ? e.message : "The queue could not be read."));
  }, []);
  if (problem) return <p role="alert">{problem}</p>;
  if (!items) return <p>Reading the queue…</p>;
  return (
    <main>
      <h1>Work queue</h1>
      {items.length === 0 && <p>Nothing is waiting.</p>}
      <table>
        <thead>
          <tr>
            <th>Kind</th>
            <th>What</th>
            <th>State</th>
            <th>Waiting</th>
          </tr>
        </thead>
        <tbody>
          {oldestFirst(items).map((i) => (
            <tr key={`${i.kind}:${i.id}`}>
              <td>{i.kind}</td>
              <td>
                <a href={i.link} onClick={(e) => { e.preventDefault(); navigate(i.link); }}>
                  {i.title}
                </a>
              </td>
              <td>{i.state}</td>
              <td>{ageText(i.ageSeconds)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </main>
  );
}

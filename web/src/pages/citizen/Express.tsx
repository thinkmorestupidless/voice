import { useEffect, useState } from "react";
import { ApiError } from "../../api/client.ts";
import { client } from "../../app/context.ts";
import type { IssuePage } from "../public/model.ts";
import { canWithdraw, Levels, stanceOn, type ExpressionView } from "./expression.ts";

/** Priority, trade-offs per option, considerations and withdrawal: the participant's opinion. */
export function Express({ issue }: { issue: string }) {
  const [page, setPage] = useState<IssuePage | undefined>();
  const [mine, setMine] = useState<ExpressionView | undefined>();
  const [words, setWords] = useState<Record<string, string>>({});
  const [problem, setProblem] = useState<string | undefined>();
  const base = `/issues/${encodeURIComponent(issue)}/expression`;

  useEffect(() => {
    client.get<IssuePage>("public", `/issues/${encodeURIComponent(issue)}`).then(setPage).catch(() => setProblem("This issue could not be read."));
    client.get<ExpressionView>("me", base).then(setMine).catch(() => setProblem("Your expression could not be read."));
  }, [issue]);

  async function act(path: string, body: unknown) {
    setProblem(undefined);
    try {
      setMine(await client.post<ExpressionView>("me", base + path, body));
    } catch (e) {
      setProblem(e instanceof ApiError ? e.message : "That did not work; try again.");
    }
  }

  if (!page || !mine) return problem ? <p role="alert">{problem}</p> : <p>Reading…</p>;
  return (
    <main>
      <h1>{page.title}</h1>
      <p role="note">{mine.notice}</p>
      {problem && <p role="alert">{problem}</p>}
      <fieldset>
        <legend>How much does this issue matter to you?</legend>
        {Levels.map((l) => (
          <button key={l} aria-pressed={mine.priority === l} onClick={() => void act("/priority", { level: l })}>
            {l}
          </button>
        ))}
      </fieldset>
      {page.options.map((o) => (
        <fieldset key={o.id}>
          <legend>{o.title}</legend>
          <p>Costs: {o.costs.join("; ")}. Benefits: {o.benefits.join("; ")}.</p>
          <input placeholder="In your words (optional)" value={words[o.id] ?? ""} onChange={(e) => setWords({ ...words, [o.id]: e.target.value })} />
          <button aria-pressed={stanceOn(mine, o.id) === "accepts"} onClick={() => void act("/trade-offs", { option: o.id, stance: "accepts", text: words[o.id] ?? "" })}>
            I would accept this trade-off
          </button>
          <button aria-pressed={stanceOn(mine, o.id) === "rejects"} onClick={() => void act("/trade-offs", { option: o.id, stance: "rejects", text: words[o.id] ?? "" })}>
            I would not
          </button>
          <button disabled={(words[o.id] ?? "").trim() === ""} onClick={() => void act("/considerations", { option: o.id, text: words[o.id] })}>
            Record this as a consideration
          </button>
        </fieldset>
      ))}
      <button disabled={!canWithdraw(mine)} onClick={() => void act("/withdraw", {})}>
        Withdraw everything I said here
      </button>
    </main>
  );
}

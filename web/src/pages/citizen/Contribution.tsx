import { useEffect, useState } from "react";
import { ApiError } from "../../api/client.ts";
import { client } from "../../app/context.ts";
import { StateLabel } from "../../components/StateLabel.tsx";
import { nextSteps, outcomeText, type ContributionView } from "./sharing.ts";

/**
 * One contribution (FR-014 to FR-018): its state in the server's words, the exact text, the review
 * outcome and reason, the Issue suggestions to accept or a new Issue to ask for, and withdrawal.
 */
export function Contribution({ id }: { id: string }) {
  const [c, setC] = useState<ContributionView | undefined>();
  const [problem, setProblem] = useState<string | undefined>();

  useEffect(() => {
    client.get<ContributionView>("me", `/contributions/${id}`).then(setC, () => setProblem("This contribution could not be loaded."));
  }, [id]);

  async function act(path: string, body: unknown = {}) {
    setProblem(undefined);
    try {
      setC(await client.post<ContributionView>("me", `/contributions/${id}${path}`, body));
    } catch (e) {
      setProblem(e instanceof ApiError ? e.message : "Voice could not do that; try again.");
    }
  }

  if (!c) return <p>{problem ?? "Loading…"}</p>;
  const steps = nextSteps(c);
  return (
    <section aria-labelledby="contribution-heading">
      <h2 id="contribution-heading">Your contribution</h2>
      <p>
        <StateLabel state={c.state} />
      </p>
      <blockquote>{c.text}</blockquote>
      <p>Seen by: {c.audience}.</p>
      {outcomeText(c) && <p>{outcomeText(c)}</p>}
      {steps.includes("associate") && (
        <div>
          <p>It may belong with one of these issues. You choose:</p>
          <ul>
            {c.suggestedIssues.map((s) => (
              <li key={s.issue}>
                {s.title}{" "}
                {c.association.issue === s.issue ? <em>(chosen)</em> : <button onClick={() => void act("/issue", { accept: s.issue })}>Choose</button>}
              </li>
            ))}
          </ul>
          {c.association.newIssueAsked ? <p>You asked for a new issue.</p> : <button onClick={() => void act("/issue", { new: true })}>Ask for a new issue</button>}
        </div>
      )}
      {steps.includes("submit") && <button onClick={() => void act("/submit")}>Send for review</button>}
      {steps.includes("withdraw") && <button onClick={() => void act("/withdraw")}>Withdraw</button>}
      {problem && <p role="alert">{problem}</p>}
    </section>
  );
}

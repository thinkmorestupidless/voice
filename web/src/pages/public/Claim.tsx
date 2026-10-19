import { useEffect, useState } from "react";
import { ApiError } from "../../api/client.ts";
import { client } from "../../app/context.ts";
import { Card } from "./Issue.tsx";
import { abstentionText, sides, type ClaimExplanation } from "./model.ts";

/** Why a claim is supported, with what contradicts it beside it; or Voice's abstention (FR-023 to FR-025). */
export function Claim({ issue, id }: { issue: string; id: string }) {
  const [x, setX] = useState<ClaimExplanation | undefined>();
  const [problem, setProblem] = useState<string | undefined>();
  useEffect(() => {
    client
      .get<ClaimExplanation>("public", `/issues/${encodeURIComponent(issue)}/claims/${encodeURIComponent(id)}`)
      .then(setX)
      .catch((e) => setProblem(e instanceof ApiError ? e.message : "This claim could not be read."));
  }, [issue, id]);
  if (problem) return <p role="alert">{problem}</p>;
  if (!x) return <p>Reading the claim…</p>;
  const abstained = abstentionText(x);
  if (abstained) return <main><p role="note">{abstained}</p></main>;
  const { support, against } = sides(x);
  return (
    <main>
      <h1>{x.claim?.statement}</h1>
      {x.claim?.stances.map((s) => (
        <p key={s.hypothesis}>
          This claim {s.stance} “{s.statement}”.
        </p>
      ))}
      {x.claim && x.claim.revisedBy.length > 0 && <p role="note">This claim has been revised by {x.claim.revisedBy.join(", ")}.</p>}
      <div className="side-by-side">
        <section>
          <h2>What supports it</h2>
          {support.map((c) => (
            <Card key={c.id} card={c} />
          ))}
        </section>
        <section>
          <h2>What contradicts it</h2>
          {against.length === 0 ? <p>No accepted evidence on this issue contradicts it.</p> : against.map(({ claim, cards }) => (
            <div key={claim.id}>
              <h3>{claim.statement}</h3>
              {cards.map((c) => (
                <Card key={c.id} card={c} />
              ))}
            </div>
          ))}
        </section>
      </div>
    </main>
  );
}

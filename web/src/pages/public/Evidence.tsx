import { useEffect, useState } from "react";
import { ApiError } from "../../api/client.ts";
import { client } from "../../app/context.ts";
import { Card } from "./Issue.tsx";
import type { EvidenceCard } from "./model.ts";

/** One piece of evidence: source, version, date, scope, authority, passage and limitations (FR-021). */
export function Evidence({ issue, id }: { issue: string; id: string }) {
  const [card, setCard] = useState<EvidenceCard | undefined>();
  const [problem, setProblem] = useState<string | undefined>();
  useEffect(() => {
    client
      .get<EvidenceCard>("public", `/issues/${encodeURIComponent(issue)}/evidence/${encodeURIComponent(id)}`)
      .then(setCard)
      .catch((e) => setProblem(e instanceof ApiError ? e.message : "This evidence could not be read."));
  }, [issue, id]);
  if (problem) return <p role="alert">{problem}</p>;
  return card ? <main><Card card={card} /></main> : <p>Reading the evidence…</p>;
}

import { useEffect, useState } from "react";
import { ApiError } from "../../api/client.ts";
import { client } from "../../app/context.ts";
import { Card } from "../public/Issue.tsx";
import { draftBody, moves, openFirst, replacements, type EvidenceDraft, type PromptView, type StaffEvidence } from "./evidence.ts";

const blank: EvidenceDraft = { source: "", locator: "", excerpt: "", scope: "", authority: "", limitations: "", publishedAt: "", bearsOn: [] };

/** The reviewer's evidence for one Issue: candidates, decisions, claims, questions and review prompts. */
export function Evidence({ issue }: { issue: string }) {
  const base = `/issues/${encodeURIComponent(issue)}`;
  const [links, setLinks] = useState<StaffEvidence[]>([]);
  const [prompts, setPrompts] = useState<PromptView[]>([]);
  const [draft, setDraft] = useState<EvidenceDraft>(blank);
  const [reason, setReason] = useState("");
  const [by, setBy] = useState("");
  const [note, setNote] = useState("");
  const [claim, setClaim] = useState({ statement: "", derivesFrom: "", hypothesis: "", stance: "supports" });
  const [question, setQuestion] = useState({ statement: "", h1: "", h2: "" });
  const [problem, setProblem] = useState<string | undefined>();

  async function reload() {
    setLinks(await client.get<StaffEvidence[]>("staff", `${base}/evidence`));
    setPrompts(await client.get<PromptView[]>("staff", `${base}/review-prompts`));
  }

  async function act(path: string, body: unknown) {
    setProblem(undefined);
    try {
      await client.post("staff", base + path, body);
      await reload();
    } catch (e) {
      setProblem(e instanceof ApiError ? e.message : "That did not work; try again.");
    }
  }

  useEffect(() => {
    reload().catch((e) => setProblem(e instanceof ApiError ? e.message : "The evidence could not be read."));
  }, [issue]);

  const proposal = draftBody(draft);
  const field = (k: keyof Omit<EvidenceDraft, "bearsOn">, label: string) => (
    <label>
      {label} <input value={draft[k] ?? ""} onChange={(e) => setDraft({ ...draft, [k]: e.target.value })} />
    </label>
  );

  return (
    <main>
      <h1>Evidence</h1>
      {problem && <p role="alert">{problem}</p>}
      <section>
        <h2>Review prompts</h2>
        {openFirst(prompts).map((p) => (
          <article key={p.id}>
            <p>
              Evidence {p.evidence} was {p.cause}. Look again at: {p.claims.join(", ") || "no claim"}
              {p.decisions.length > 0 && <>; decisions {p.decisions.join(", ")}</>}.
            </p>
            {p.resolved ? <p>Resolved by {p.resolved.by}: {p.resolved.note}</p> : (
              <>
                <input placeholder="What you found" value={note} onChange={(e) => setNote(e.target.value)} />
                <button disabled={note.trim() === ""} onClick={() => void act(`/review-prompts/${p.id}/resolve`, { note })}>Resolve</button>
              </>
            )}
          </article>
        ))}
      </section>
      <section>
        <h2>Evidence on this issue</h2>
        <label>
          Reason <input value={reason} onChange={(e) => setReason(e.target.value)} />
        </label>
        {links.map((l) => (
          <div key={l.card.id}>
            <Card card={l.card} />
            {l.decidedBy && <p>Decided by {l.decidedBy}{l.reason ? `: ${l.reason}` : ""}.</p>}
            {moves(l).includes("accept") && <button onClick={() => void act(`/evidence/${l.card.id}/accept`, {})}>Accept</button>}
            {moves(l).includes("reject") && (
              <button disabled={reason.trim() === ""} onClick={() => void act(`/evidence/${l.card.id}/reject`, { reason })}>Reject</button>
            )}
            {moves(l).includes("supersede") && (
              <>
                <select value={by} onChange={(e) => setBy(e.target.value)}>
                  <option value="">Superseded by…</option>
                  {replacements(links, l).map((o) => (
                    <option key={o.card.id} value={o.card.id}>{o.card.source.name}, {o.card.locator}</option>
                  ))}
                </select>
                <button disabled={by === "" || reason.trim() === ""} onClick={() => void act(`/evidence/${l.card.id}/supersede`, { by, reason })}>
                  Supersede
                </button>
              </>
            )}
          </div>
        ))}
      </section>
      <section>
        <h2>Propose evidence</h2>
        {field("source", "Source id")}
        {field("locator", "Locator")}
        {field("excerpt", "Passage")}
        {field("publishedAt", "Published (ISO date)")}
        {field("scope", "Scope")}
        {field("authority", "Authority")}
        {field("limitations", "Limitations")}
        <label>
          Bears on (q1/h1, comma separated){" "}
          <input value={draft.bearsOn.join(",")} onChange={(e) => setDraft({ ...draft, bearsOn: e.target.value.split(",").map((s) => s.trim()) })} />
        </label>
        <button disabled={!proposal} onClick={() => proposal && void act("/evidence", proposal).then(() => setDraft(blank))}>Propose</button>
      </section>
      <section>
        <h2>State a claim</h2>
        <input placeholder="Statement" value={claim.statement} onChange={(e) => setClaim({ ...claim, statement: e.target.value })} />
        <input placeholder="Accepted evidence ids, comma separated" value={claim.derivesFrom} onChange={(e) => setClaim({ ...claim, derivesFrom: e.target.value })} />
        <input placeholder="Hypothesis (q1/h1)" value={claim.hypothesis} onChange={(e) => setClaim({ ...claim, hypothesis: e.target.value })} />
        <select value={claim.stance} onChange={(e) => setClaim({ ...claim, stance: e.target.value })}>
          <option value="supports">supports</option>
          <option value="contradicts">contradicts</option>
        </select>
        <button
          disabled={claim.statement.trim() === "" || claim.derivesFrom.trim() === "" || claim.hypothesis.trim() === ""}
          onClick={() =>
            void act("/claims", {
              statement: claim.statement,
              derivesFrom: claim.derivesFrom.split(",").map((s) => s.trim()).filter((s) => s !== ""),
              stances: [{ hypothesis: claim.hypothesis.trim(), stance: claim.stance }],
            })
          }
        >
          State the claim
        </button>
      </section>
      <section>
        <h2>Open a question</h2>
        <input placeholder="Question" value={question.statement} onChange={(e) => setQuestion({ ...question, statement: e.target.value })} />
        <input placeholder="First hypothesis" value={question.h1} onChange={(e) => setQuestion({ ...question, h1: e.target.value })} />
        <input placeholder="Second hypothesis" value={question.h2} onChange={(e) => setQuestion({ ...question, h2: e.target.value })} />
        <button
          disabled={[question.statement, question.h1, question.h2].some((s) => s.trim() === "")}
          onClick={() =>
            void act("/questions", {
              statement: question.statement,
              hypotheses: [
                { id: "h1", statement: question.h1 },
                { id: "h2", statement: question.h2 },
              ],
            })
          }
        >
          Open the question
        </button>
      </section>
    </main>
  );
}

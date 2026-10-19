import { useEffect, useState } from "react";
import { ApiError } from "../../api/client.ts";
import { client } from "../../app/context.ts";
import { age, approveBody, decidable, defaultIssue, needsSecondStep, ordered, type ReviewItem } from "./reviews.ts";

/** One derivative in the queue: flags, the second step when flagged, approve into an Issue, reject. */
function Item({ item, onChange }: { item: ReviewItem; onChange: (next: ReviewItem) => void }) {
  const [reason, setReason] = useState("");
  const [issue, setIssue] = useState<string | undefined>(defaultIssue(item));
  const [title, setTitle] = useState("");
  const [summary, setSummary] = useState("");
  const [problem, setProblem] = useState<string | undefined>();
  const base = `/reviews/${item.contribution}`;

  async function act(path: string, body: unknown) {
    setProblem(undefined);
    try {
      onChange(await client.post<ReviewItem>("staff", base + path, body));
      setReason("");
    } catch (e) {
      setProblem(e instanceof ApiError ? e.message : "That did not work; try again.");
    }
  }

  const approval = approveBody(issue, title, summary);
  return (
    <article className="review">
      <blockquote>{item.derivative}</blockquote>
      <p>
        {item.mode}, seen by {item.audience}; waiting {age(item.ageSeconds)}.
      </p>
      {item.flags.length > 0 && (
        <div role="note">
          <p>Flagged for enhanced review: {item.flags.join(", ")}.</p>
          <ul>
            {item.reasons.map((r, i) => (
              <li key={i}>{r}</li>
            ))}
          </ul>
          {item.secondStep && <p>Second step recorded by {item.secondStep.reviewer}: {item.secondStep.reason}</p>}
        </div>
      )}
      {decidable(item) && (
        <>
          <label>
            Reason <input value={reason} onChange={(e) => setReason(e.target.value)} />
          </label>
          {needsSecondStep(item) ? (
            <button disabled={reason.trim() === ""} onClick={() => void act("/enhanced", { reason })}>
              Record the enhanced review
            </button>
          ) : (
            <>
              <select value={issue ?? ""} onChange={(e) => setIssue(e.target.value || undefined)}>
                <option value="">A new issue</option>
                {item.suggestedIssues.map((s) => (
                  <option key={s.issue} value={s.issue}>
                    {s.title}
                  </option>
                ))}
              </select>
              {!issue && (
                <>
                  <input placeholder="New issue title" value={title} onChange={(e) => setTitle(e.target.value)} />
                  <input placeholder="Summary" value={summary} onChange={(e) => setSummary(e.target.value)} />
                </>
              )}
              <button disabled={!approval} onClick={() => void act("/approve", approval)}>
                Approve
              </button>
            </>
          )}
          <button disabled={reason.trim() === ""} onClick={() => void act("/reject", { reason })}>
            Reject with this reason
          </button>
        </>
      )}
      {problem && <p role="alert">{problem}</p>}
    </article>
  );
}

/** The reviewer's queue (FR-014): every derivative under moderation, oldest first. */
export function Reviews() {
  const [items, setItems] = useState<ReviewItem[] | undefined>();
  const [problem, setProblem] = useState<string | undefined>();
  useEffect(() => {
    client.get<ReviewItem[]>("staff", "/reviews").then(setItems, (e) => setProblem(e instanceof ApiError ? e.message : "The queue could not be loaded."));
  }, []);
  if (!items) return <p>{problem ?? "Loading the queue…"}</p>;
  return (
    <section aria-labelledby="reviews-heading">
      <h2 id="reviews-heading">Waiting for review</h2>
      {items.length === 0 && <p>Nothing is waiting.</p>}
      {ordered(items).map((item) => (
        <Item key={item.contribution} item={item} onChange={(next) => setItems(items.map((i) => (i.contribution === next.contribution ? next : i)))} />
      ))}
    </section>
  );
}

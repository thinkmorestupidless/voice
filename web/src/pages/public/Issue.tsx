import { SealedField } from "../../components/SealedField.tsx";
import { useEffect, useState } from "react";
import { ApiError } from "../../api/client.ts";
import { client, navigate } from "../../app/context.ts";
import { Follow } from "../citizen/Follow.tsx";
import { StateLabel } from "../../components/StateLabel.tsx";
import { AggregatePanel, Options } from "./AggregatePanel.tsx";
import { abstentionText, page, passageText, shownDate, stateText, type EvidenceCard, type IssuePage } from "./model.ts";

export function Card({ card }: { card: EvidenceCard }) {
  return (
    <article className="evidence">
      <blockquote>{passageText(card)}</blockquote>
      <p>
        <a href={page(card.source.record)} onClick={(e) => { e.preventDefault(); navigate(page(card.source.record)); }}>
          {card.source.name}
        </a>
        {card.locator && <>, {card.locator}</>}, {shownDate(card)}. {stateText(card)}.
      </p>
      <dl>
        <dt>Scope</dt>
        <dd>{card.scope}</dd>
        <dt>Authority</dt>
        <dd>{card.authority}</dd>
        <dt>Limitations</dt>
        <dd>{card.limitations}</dd>
      </dl>
      <a href={page(card.link)} onClick={(e) => { e.preventDefault(); navigate(page(card.link)); }}>
        This evidence's record
      </a>
    </article>
  );
}

/** A public Issue, read without a session: nothing here creates an interest (FR-045). */
export function Issue({ id }: { id: string }) {
  const [issue, setIssue] = useState<IssuePage | undefined>();
  const [problem, setProblem] = useState<string | undefined>();

  useEffect(() => {
    client
      .get<IssuePage>("public", `/issues/${encodeURIComponent(id)}`)
      .then(setIssue)
      .catch((e) => setProblem(e instanceof ApiError ? e.message : "This issue could not be read."));
  }, [id]);

  if (problem) return <p role="alert">{problem}</p>;
  if (!issue) return <p>Reading the issue…</p>;
  return (
    <main>
      <h1>{issue.title}</h1>
      <p>{issue.summary}</p>
      <Follow issue={issue.id} />
      <section>
        <h2>Questions</h2>
        {issue.questions.map((q) => (
          <div key={q.id}>
            <h3>{q.statement}</h3>
            {abstentionText(q) ? (
              <p role="note">{abstentionText(q)}</p>
            ) : (
              <ul>
                {q.hypotheses.map((h) => (
                  <li key={h.id}>
                    {h.statement}: {h.supporting.length} supporting, {h.contradicting.length} contradicting.
                    {[...h.supporting, ...h.contradicting].map((c) => (
                      <a key={c.id} href={page(c.link)} onClick={(e) => { e.preventDefault(); navigate(page(c.link)); }}>
                        {" "}
                        {c.statement}
                      </a>
                    ))}
                  </li>
                ))}
              </ul>
            )}
          </div>
        ))}
      </section>
      <section>
        <h2>Evidence</h2>
        {issue.evidence.map((c) => (
          <Card key={c.id} card={c} />
        ))}
      </section>
      <Options options={issue.options} />
      {issue.aggregate && <AggregatePanel aggregate={issue.aggregate} options={issue.options} />}
      <p>
        <a href={`/me/issues/${issue.id}/express`} onClick={(e) => { e.preventDefault(); navigate(`/me/issues/${issue.id}/express`); }}>
          Say how much this matters to you
        </a>
      </p>
      <section>
        <h2>What residents shared</h2>
        {issue.contributions.map((c) => (
          <blockquote key={c.pub}>
            <SealedField value={c.text} />
            {c.attribution !== undefined && (
              <footer>
                <SealedField value={c.attribution} />
              </footer>
            )}
            {c.authorErased && (
              <p>
                <small>
                  {c.authorErased === "dormancy"
                    ? "Its author's personal data was erased after a long absence; the contribution stays, with no link to them."
                    : "Its author asked for their personal data to be erased; the contribution stays, with no link to them."}
                </small>
              </p>
            )}
          </blockquote>
        ))}
        <p>{issue.counts.caveat}</p>
      </section>
      <section>
        <h2>Investigations</h2>
        {issue.investigations.length === 0 ? <p>No investigation is open.</p> : (
          <ul>
            {issue.investigations.map((i) => (
              <li key={i.id}>
                {i.question} ({i.state})
              </li>
            ))}
          </ul>
        )}
      </section>
      <section>
        <h2>Actions</h2>
        {issue.actions.length === 0 ? <p>No action has been recorded.</p> : (
          <ul>
            {issue.actions.map((a) => (
              <li key={a.id} id={a.id}>
                {a.date}: {a.summary} ({a.state}; {a.authority}, {a.owner}). {a.rationale}{" "}
                <small>
                  Recorded by {a.recordedBy} on {a.recordedAt.slice(0, 10)}.
                </small>
              </li>
            ))}
          </ul>
        )}
      </section>
      {issue.updates.length > 0 && (
        <section>
          <h2>Updates from representatives</h2>
          {issue.updates.map((u) => (
            <article key={u.id} id={u.id}>
              <p>{u.text}</p>
              <small>
                {u.corrects ? "A correction, " : ""}published by {u.publishedBy} on {u.publishedAt.slice(0, 10)}.
              </small>
            </article>
          ))}
        </section>
      )}
      {issue.decisions.length > 0 && (
        <section>
          <h2>Decisions</h2>
          {issue.decisions.map((d) => (
            <article key={d.id}>
              <p>
                {d.approach} ({d.authority})
              </p>
              {d.alternatives.length > 0 && <p>Alternatives considered: {d.alternatives.join("; ")}</p>}
              <p>{d.justification}</p>
              <small>
                Recorded by {d.recordedBy} on {d.recordedAt.slice(0, 10)}.
              </small>
            </article>
          ))}
        </section>
      )}
      {issue.outcomes.length > 0 && (
        <section>
          <h2>Outcomes</h2>
          {issue.outcomes.map((o) => (
            <article key={o.id}>
              <p>
                For <a href={`#${o.action}`}>the action it evaluates</a>: {o.measured}
              </p>
              <p>Limitations: {o.limitations}</p>
              <p>{o.resolution}</p>
            </article>
          ))}
        </section>
      )}
      <section>
        <h2>Chronology</h2>
        <ol>
          {issue.chronology.map((e, i) => (
            <li key={i}>
              {e.at.slice(0, 10)}: <StateLabel state={e.state} />, {e.note}
              {e.unanswered && <strong> No response from a representative yet.</strong>}
            </li>
          ))}
        </ol>
      </section>
      <section>
        <h2>Limitations</h2>
        <p>{issue.limitations}</p>
      </section>
    </main>
  );
}

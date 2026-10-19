import { basisText, cellText, scale, type Aggregate, type OptionView } from "./aggregate.ts";

/** Opinion, labelled as opinion: count, method, window and caveat beside every figure. */
export function AggregatePanel({ aggregate, options }: { aggregate: Aggregate; options: OptionView[] }) {
  const title = (id: string) => options.find((o) => o.id === id)?.title ?? id;
  return (
    <section aria-label="What participants said">
      <h2>What participants said (opinion, not evidence)</h2>
      <p>{basisText(aggregate)}</p>
      <ul>
        {scale(aggregate).map((c) => (
          <li key={c.key}>
            Priority {c.key} of 5: {cellText(c, aggregate.threshold)}
          </li>
        ))}
      </ul>
      {aggregate.options.map((o) => (
        <p key={o.optionId}>
          {title(o.optionId)}: {o.cells.map((c) => `${c.key} ${cellText(c, aggregate.threshold)}`).join("; ")}.
        </p>
      ))}
      <p>
        Statements counted: {cellText(aggregate.contributions, aggregate.threshold)}. {aggregate.method} {aggregate.deduplication}
      </p>
      <p role="note">{aggregate.caveat}</p>
    </section>
  );
}

/** Options as described, none endorsed (FR-032). */
export function Options({ options }: { options: OptionView[] }) {
  return (
    <section>
      <h2>Options</h2>
      {options.map((o) => (
        <article key={o.id} id={o.id}>
          <h3>{o.title}</h3>
          <p>{o.description}</p>
          <p>Costs: {o.costs.join("; ")}</p>
          <p>Benefits: {o.benefits.join("; ")}</p>
          <p>Open questions: {o.openQuestions.join("; ")}</p>
        </article>
      ))}
    </section>
  );
}

import { useEffect, useState } from "react";
import { ApiError } from "../../api/client.ts";
import { client } from "../../app/context.ts";

interface SourceCard {
  id: string;
  name: string;
  licence: string;
  attribution: string;
  link: string;
  publishedAt?: string;
}

/** A source's name, licence, attribution and link. */
export function Source({ id }: { id: string }) {
  const [card, setCard] = useState<SourceCard | undefined>();
  const [problem, setProblem] = useState<string | undefined>();
  useEffect(() => {
    client
      .get<SourceCard>("public", `/sources/${encodeURIComponent(id)}`)
      .then(setCard)
      .catch((e) => setProblem(e instanceof ApiError ? e.message : "This source could not be read."));
  }, [id]);
  if (problem) return <p role="alert">{problem}</p>;
  if (!card) return <p>Reading the source…</p>;
  return (
    <main>
      <h1>{card.name}</h1>
      <p>{card.attribution}</p>
      <p>Licence: {card.licence}</p>
      <a href={card.link} rel="noreferrer">{card.link}</a>
    </main>
  );
}

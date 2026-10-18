import { isRedacted, sealedText } from "./sealed.ts";

/** One protected field: its text, or "redacted or unavailable" when it was erased. */
export function SealedField({ value }: { value: string | undefined | null }) {
  const redacted = isRedacted(value);
  return (
    <span className={redacted ? "sealed-field redacted" : "sealed-field"} data-redacted={redacted}>
      {sealedText(value)}
    </span>
  );
}

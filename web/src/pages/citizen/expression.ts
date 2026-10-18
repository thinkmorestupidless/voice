/** The participant's own expression on an Issue, apart from React (FR-030 to FR-032). */
export interface ExpressionView {
  issue: string;
  label: "opinion";
  priority?: number;
  tradeOffs: Array<{ option: string; stance: "accepts" | "rejects"; text: string; at: string }>;
  considerations: Array<{ option: string; text: string; at: string }>;
  withdrawn: boolean;
  notice: string;
}

export const Levels = [1, 2, 3, 4, 5] as const;

/** The stance this participant holds on an option, if any. */
export function stanceOn(e: ExpressionView, option: string): "accepts" | "rejects" | undefined {
  return e.tradeOffs.find((t) => t.option === option)?.stance;
}

/** Nothing to withdraw once withdrawn, or before anything was said. */
export function canWithdraw(e: ExpressionView): boolean {
  return !e.withdrawn && (e.priority !== undefined || e.tradeOffs.length > 0 || e.considerations.length > 0);
}

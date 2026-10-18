/** What the data-rights pages decide, apart from React (FR-052, FR-053, FR-058). */

export interface ErasureStatus {
  state: string;
  cause?: string;
  rule?: string;
  requestedAt?: string;
  erased: string[];
  retained: string[];
  why: string;
  remainsPublic: string[];
}

export interface HeldClass {
  name: string;
  count: number;
}

export interface DataSummary {
  held: HeldClass[];
  public: string[];
  retention: string;
  erasure: ErasureStatus;
}

export interface ReceiptView {
  cause: string;
  classes: HeldClass[];
  authorisedBy: string;
  verifiedBy: string;
  at: string;
  completionVerified: boolean;
}

export interface ErasureRow {
  participant: string;
  cause?: string;
  state: string;
  requestedAt?: string;
  receipt?: ReceiptView;
}

const StateWords: Record<string, string> = {
  None: "You have not asked for erasure.",
  Verifying: "Your request is being checked.",
  HeldForOperator: "Your request is held until an operator confirms it is yours.",
  InProgress: "Your data is being erased.",
  Completed: "Your data has been erased.",
};

export function stateText(state: string): string {
  return StateWords[state] ?? state;
}

/** A participant may ask once; a second request is answered unchanged by the service anyway. */
export function canRequest(status: ErasureStatus): boolean {
  return status.state === "None";
}

/** The request body: the recovery credential verifies it at once; without it an operator must. */
export function requestBody(credential: string): { recoveryCredential?: string } {
  const c = credential.trim();
  return c === "" ? {} : { recoveryCredential: c };
}

const ClassWords: Record<string, string> = {
  conversations: "private conversations",
  concerns: "confirmed concerns",
  contributions: "contributions",
  expressions: "priorities, trade-offs and considerations",
  interests: "issues you follow",
  notifications: "updates in your inbox",
  "public-copies-withdrawn": "public copies withdrawn",
  "public-copies-retained": "de-identified copies retained",
  key: "your key",
  "recovery-credential": "your recovery credential",
};

export function className(name: string): string {
  return ClassWords[name] ?? name;
}

/** The operator's rows: held requests first, then the rest by when they were asked. */
export function ordered(rows: ErasureRow[]): ErasureRow[] {
  const rank = (r: ErasureRow) => (r.state === "held-for-operator" ? 0 : 1);
  return [...rows].sort((a, b) => rank(a) - rank(b) || (a.requestedAt ?? "").localeCompare(b.requestedAt ?? ""));
}

export function causeText(cause: string | undefined): string {
  if (cause === "dormancy") return "no session for the dormancy period";
  if (cause === "requested") return "asked for by the participant";
  return "";
}

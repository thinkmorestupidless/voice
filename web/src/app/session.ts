/**
 * The citizen's session, in memory only (R9): closing the tab ends it, and nothing is written to
 * storage a later visitor to the same browser could read. The recovery credential is shown once and
 * then forgotten here; the participant keeps it.
 */
export interface Session {
  participant: string;
  token: string;
  expiresAt: string;
}

export class SessionStore {
  private session: Session | undefined;
  private recovery: string | undefined;
  private noticeRead = false;
  private staffToken: string | undefined;

  begin(session: Session, recoveryCredential: string): void {
    this.session = session;
    this.recovery = recoveryCredential;
  }

  renew(token: string, expiresAt: string): void {
    if (this.session) this.session = { ...this.session, token, expiresAt };
  }

  current(): Session | undefined {
    return this.session;
  }

  /** The recovery credential, the first time it is asked for; never again. */
  takeRecoveryCredential(): string | undefined {
    const shown = this.recovery;
    this.recovery = undefined;
    return shown;
  }

  acknowledgeNotice(): void {
    this.noticeRead = true;
  }

  /** The notice is read before the first message (FR-003). */
  mayWrite(): boolean {
    return this.session !== undefined && this.noticeRead;
  }

  setStaffToken(token: string | undefined): void {
    this.staffToken = token;
  }

  tokens() {
    return { citizen: () => this.session?.token, staff: () => this.staffToken };
  }

  end(): void {
    this.session = undefined;
    this.recovery = undefined;
    this.noticeRead = false;
  }
}

export const session = new SessionStore();

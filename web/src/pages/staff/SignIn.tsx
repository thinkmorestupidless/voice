import { useState } from "react";
import { navigate, session } from "../../app/context.ts";

/**
 * Staff sign in with a token from the staff issuer (R10). The token is held in memory for this tab
 * only; the role it carries is the issuer's, and every route checks it again.
 */
export function SignIn() {
  const [token, setToken] = useState("");
  return (
    <section aria-labelledby="signin-heading">
      <h2 id="signin-heading">Staff</h2>
      <label>
        Access token <input type="password" value={token} onChange={(e) => setToken(e.target.value)} autoComplete="off" />
      </label>
      <button
        disabled={token.trim() === ""}
        onClick={() => {
          session.setStaffToken(token.trim());
          navigate("/staff/reviews");
        }}
      >
        Continue
      </button>
    </section>
  );
}

# What Voice would like from its platform

Requests to ankka and to ankka-reasoning, each with what it would remove from Voice. None blocks the
feature. The model is satisfactory's `notes/ankka-requests.md`.

## To ankka

1. **Personal data erasure (spec 042) implemented**: `Personal[A]`, the keyring, view redaction,
   session erasure and the certificate. Would remove `Sealed`, the `Sealing` service, the key per
   participant in the secret store, and most of the `erasure` workflow. ankka-reasoning asked for
   part of this already (its request 1).
2. **A clock and the caller in `CommandContext`.** Would remove time and actor from every command
   Voice sends. Already asked by satisfactory and ankka-reasoning.
3. **An index declaration for a view's declared query.** Would remove the note that public search
   is a sequential scan.
4. **A published test issuer for `ankka-auth-oidc`**, so a service's suite need not mint its own
   keys and JWKS.
5. **A record-and-replay `ModelProvider` in the test kit.** Would remove Voice's own
   `RecordedModelProvider`.
6. **A gateway route timeout override**, already on the family list. Would let a conversation turn
   stream to its end instead of being polled.

7. **`GherkinSuite` step bodies past six values.** A step naming seven chronology labels needs its
   own `StepBody`; an arity up to nine, or one taking `Seq[String]`, would remove it.

8. **A view client for workflow steps and consumers.** Would let `update-delivery` read
   `followers-by-issue` as the data model first said, and remove the `issue-followers` entity.
9. **Workflows resumed after a restart without being addressed**, or a documented way to ask for
   it. Would remove the consumer's `awaitEnd` that exists to have a redelivery wake the workflow.
10. **A backoff for workflow step retries.** Would let a step that waits on something outside retry
    over seconds rather than milliseconds.

11. **A secret store outside the service's database**, or keys whose destruction a database restore
    cannot undo. Today a participant key lives in `ankka_secrets`, so a backup from before an erasure
    holds it. Spec 042's keyring would remove this.
12. **A way to rebuild a view in the test kit**, such as `restartService` taking raised view versions.
    Would remove the rebuild scenario's table emptying.

## To ankka-reasoning

13. **A per-claim explanation route**, `GET /answers/claim/{c}`: the claim's evidence, sources and
   the claims that contradict it on the same hypotheses. Would remove the assembly in Voice's
   reasoning adapter.
14. **Scope, authority and limitations on evidence**, or a documented place for a writer's own
   annotations, if other consumers keep them beside the reference as Voice does.

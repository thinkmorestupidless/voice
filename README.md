# voice

A privacy-first civic participation service on [ankka](https://github.com/thinkmorestupidless/ankka).
A resident talks privately about a local problem, confirms what voice understood, shares exactly what
they choose, and hears what representatives recorded about it; anyone can read and search the public
issues; a participant can have their personal data erased. A demonstrator for a pilot, on synthetic
data.

**Documentation: [voice.ankka.cloud](https://voice.ankka.cloud/).** Built from `docs/` by ankka's
docs tool (`just docs`), and rendered as an agent skill under `marketplace/`.

- What it does and for whom: `docs/index.md`
- How it is built: `docs/concepts/architecture.md`
- What each audience can read, and what erasure leaves: `docs/concepts/audiences.md`, `docs/concepts/sealing.md`
- What it does not do: `docs/reference/limitations.md`
- The feature specification, plan and tasks: `specs/001-voice-mvp/`
- The behaviour, as Gherkin: `features/`

Needs: JDK 21, sbt, Docker, Node 24, `just`, the `ankka` CLI `0.12.0`.

## Test

```bash
just test                 # every suite but the live-model ones; Docker is required
cd web && npm test        # the interface's logic
just check                # the features, the glossary and the specs agree
```

## Run locally

```bash
just up                   # Postgres, the reasoning service, Keycloak for staff
just run                  # voice on :9000, with .env.local
just web                  # the interface on :3000, /api mounted to voice
just seed                 # the school-transport seed pack
```

`specs/001-voice-mvp/quickstart.md` walks each tier, from the unit suites to a deployment on a local
kind installation (`just deploy-local`).

## See what it is doing

```bash
ankka local console       # http://localhost:9889
```

Lists the ankka services running on this machine with their components, a form per HTTP route, and
the trace of each request. It reads an entity's state through its declared queries and runs no
command.

## Work with a coding agent

This project carries ankka's documentation for the version it was made with, as Agent Skills in
`.claude/skills/`, and a `.mcp.json` that starts `ankka mcp`: the CLI's commands, the services running
on this machine and the same documentation, as tools. Claude Code reads both with no configuration; it
asks once before starting the project's MCP server. The `ankka` CLI must be on your `PATH`.

For Claude Desktop, or for Claude Code in every project rather than this one:

```bash
ankka mcp install --client desktop      # Claude Desktop; quit and reopen it afterwards
ankka mcp install                       # Claude Code, for you, in every project
```

## Deploy

`service.json` and `web/service.json` are the descriptors; `specs/001-voice-mvp/contracts/deployment.md`
says what each is given. Secrets arrive by `secretKeyRef` to the project secret `voice-secrets`, set
by name with `ankka projects secrets set`; no value is in this repository.

## Deploy from GitHub

This project carries two workflows. `.github/workflows/ci.yml` builds and tests on every push and
pull request, and needs nothing configured. `.github/workflows/deploy.yml` builds the image, pushes
it to this repository's GitHub Container Registry, and deploys — on a version tag, or when you run it
by hand. Until its secrets exist it declines to run rather than failing, so the first push is green.

Four repository secrets, under **Settings → Secrets and variables → Actions**:

| Secret | Value | Where it comes from |
|---|---|---|
| `ANKKA_URL` | the control plane's address | `ankka config get url` |
| `ANKKA_TOKEN` | a deploy token | `ankka organizations tokens create <org> --label github` |
| `ANKKA_PROJECT` | the project to deploy into | `ankka projects list` |
| `ANKKA_CA` | optional: the platform's certificate authority, as PEM | `cat ~/.ankka/local-ca.crt`, for a platform whose certificate is not publicly trusted |

A deploy token is a credential a machine can hold. It is shown once, it acts as a **member** of that
organization — it can deploy, pause and restart services, and it cannot manage members or other
tokens — and `ankka organizations tokens revoke` stops it.

The cluster has to be able to *pull* the image. A GHCR package is private by default, so either make
the package public, or register the registry for the project once:

```bash
ankka projects registry set <project> --server ghcr.io --username <github user> --password <a read:packages token>
```

Then tag a release:

```bash
git tag v0.1.0 && git push --tags
```

The workflow deploys to `Ready`. It does not expose the service: making it publicly reachable is your
decision, taken once with `ankka services expose voice-web`, and it survives every later
deploy.

## Upgrading ankka

The ankka version lives in two places that must move together: `ankkaVersion` in `build.sbt` (the
artifacts this build resolves) and `runtime` in `service.json` (what the platform checks). Then
`sbt schema` and `docker compose down -v && docker compose up -d` for the local database.

---
title: Releasing
description: What pushing a version tag publishes and deploys, what each part needs outside the repository, how to prove the path on a laptop first, and what to check afterwards.
kind: contributing
related: [contributing/documentation.md, deploy/deploy-on-ankka.md, build/testing.md]
---

# Releasing

A release is a tag. Pushing `v0.1.0` to the repository publishes and deploys everything a version of
Voice consists of, and nothing else ever publishes: not a merge, not a commit to `main`, not a
command run by hand.

## What a tag does

| Workflow | Does | Needs |
|---|---|---|
| `deploy.yml` | builds the service image for `amd64` and `arm64`, pushes it to `ghcr.io/thinkmorestupidless/voice`, and deploys it with `ankka services deploy voice <image>` | the secrets `ANKKA_URL`, `ANKKA_TOKEN`, `ANKKA_PROJECT` and, for a certificate not publicly trusted, `ANKKA_CA` |
| `marketplace.yml` | publishes the documentation as a Claude Code plugin, at this version, to `thinkmorestupidless/ankka-marketplace` | the secret `MARKETPLACE_REPO_TOKEN`, a token with `contents: write` on that repository |

Both decline to run, with a notice, when their secret is absent, so a tag pushed before the
repository is configured shows green and does nothing. A tag with a hyphen, such as `v0.2.0-rc.1`,
publishes no plugin.

The documentation site is not part of a release. It is published from `main` by `docs.yml`, so it
describes `main`; the plugin a release publishes is the documentation as it stood at the tag. The
interface's image is not built by the tag either: it is built and deployed by hand, as
[Deploy on ankka](../deploy/deploy-on-ankka.md) shows.

## The version is the tag

The version comes from git and from nowhere else. sbt-dynver reads the nearest tag: on the commit
tagged `v0.1.0` the build's version is `0.1.0`, and a commit past it or a tree with a changed file
is a snapshot. `version` is never set in the build, and no tracked file holds it: the plugin's
manifest says `0.0.0` in the tree, and the release job writes the version into its own checkout
before copying the plugin out. A Docker tag may not contain `+`, so a snapshot's version has it
replaced by `-` in the image tag.

## What nothing can undo

An image tag that has been pulled is in use, and a plugin version in the marketplace is what agents
have installed. So: never move or re-push a tag that has published, never publish by hand, and fix
a mistake in a release with the next version. A re-run of the workflow for a tag is safe: the image
is pushed again under the same tag, the deploy applies the same descriptor, and the marketplace job
finds nothing to commit.

## Prove the path before the tag

Everything a release does except the push and the deploy runs on a laptop:

```bash
sbt -batch test
cd web && npm run typecheck && npm test && npm run build && cd ..
just check
just docs
sbt service/Docker/publishLocal && docker build -t voice-web:local web
```

The last line builds both images as the workflows would; `just deploy-local` deploys them to a local
kind installation.

## Cut a release

From `main`, with the pull request that leads to it merged and green:

```bash
git switch main && git pull
git tag v0.1.0
git push origin v0.1.0
```

Then check: the workflow runs for the tag, every job green or declined by name;
`docker pull ghcr.io/thinkmorestupidless/voice:0.1.0`, from a machine not logged in to `ghcr.io`,
once the package has been made public in its settings; `ankka services get voice -p voice` showing
the new image; and the `voice` entry in the marketplace's manifest at the version.

# Security policy

DeployKit is a getting-started / learning-oriented internal developer platform. [docs/security.md](docs/security.md)
tracks its threat model and known limitations in detail — read that first if you are wondering whether something is
a known gap rather than a new finding.

## Reporting a vulnerability

Please **do not open a public issue** for a security vulnerability. Instead, use GitHub's private reporting:

1. Go to the **Security** tab of this repository.
2. Click **Report a vulnerability** to open a private advisory.

If that is not available, email the maintainer listed in the repository's commit history instead. Include steps to
reproduce and the impact you'd expect; a proof of concept is welcome but not required.

## Supported versions

There are no released versions yet: only the `main` branch is supported. Fixes land there.

## Scope

In scope: the backend, frontend, Helm chart, and the Terraform in `infrastructure/aws`. Out of scope: vulnerabilities
in third-party dependencies with no DeployKit-specific exploit path (report those upstream instead), and anything
already listed as a known, accepted limitation in [docs/security.md](docs/security.md).

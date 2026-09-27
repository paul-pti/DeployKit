# Contributing

Thanks for considering a contribution. This is a getting-started-sized project: small, focused pull requests are
much easier to review than large ones.

## Getting set up

Follow the [Quick start](README.md#quick-start) in the README, then [docs/testing.md](docs/testing.md) for how to run
every test suite (backend unit/integration, frontend unit, end-to-end).

## Before opening a pull request

- `cd backend && ./mvnw verify` (needs Docker for the integration tests) and `cd frontend && npm run lint && npm run test && npm run build`.
- Follow the existing style: layered backend packages (`controller → service → repository → domain`), DTOs at the API
  boundary, feature-oriented frontend (`features/<name>`) — see [docs/architecture.md](docs/architecture.md).
- A change to how something works, not just what it does, is a good candidate for a short ADR in [docs/adr](docs/adr)
  (see the existing ones for the format: context, decision, consequences).
- Keep the README short; put detail in `docs/`.

## Reporting bugs and proposing features

Open an issue. For bugs, include how to reproduce and what you expected instead. For anything security-related,
see [SECURITY.md](SECURITY.md) instead of opening a public issue.

## Code of conduct

Be respectful and assume good faith. Disagreements about code are fine; treat people the way you'd want to be
treated in a review.

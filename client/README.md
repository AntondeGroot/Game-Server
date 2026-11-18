# GameRoom client (Angular)

The Angular client that will replace the GWT client in `../GameRoom-client`. Until the switch
(step 7 of `../docs/angular-migration.md`) it is served at `/next/`.

- `npm run generate` — the API client and types from `../gameroom_openapi.yaml` into
  `src/generated` (not committed).
- `npm start` — dev server at http://localhost:4200/next/, with the API proxied to the Spring
  server on port 4100.
- `npm test -- --watch=false --coverage` — unit tests; CI fails below 90% coverage.
- `npm run lint`, `npm run lint:styles`, `npm run lint:folders`, `npm run format:check` — the gates
  CI runs. `npm install` points git at `.husky`, which runs them before each commit and push.
- `npm run audit:deps` — `npm audit --audit-level=high`, minus the reviewed exceptions in
  `scripts/audit.mjs`; CI runs it too.

Components may use the generated DTO types but not the generated API services; data access goes
through an app service (`*.service.ts`). ESLint enforces this.

## Dependency overrides

`package.json` forces these transitive versions for security advisories. Remove an override once
the package that pulls it in asks for a fixed version itself (Renovate shows those bumps).

- `basic-ftp` `^6.2.6` — pulled in by `@openapitools/openapi-generator-cli`; earlier versions have
  high-severity advisories.
- `minimatch` `11.0.8` — GHSA-g3hv-75wg-m249 (7.0.0 – 11.0.0). Pinned exactly so a fresh release
  doesn't slip into the toolchain unreviewed; Renovate proposes newer versions.
  `eslint-plugin-sonarjs` 4.2.2 still asks for `^10.2.6`, so it runs on a major it isn't tested
  against; the parts it uses (`new Minimatch(pattern, { nocase, matchBase })`) behave the same.
  Remove the override when sonarjs asks for `^11`.

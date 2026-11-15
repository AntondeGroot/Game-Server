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

Components may use the generated DTO types but not the generated API services; data access goes
through an app service (`*.service.ts`). ESLint enforces this.

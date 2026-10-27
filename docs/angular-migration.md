# Migrating the client from GWT to Angular

The GWT client is replaced by an Angular app in steps. Each step is its own pull request and
leaves the live site working: GWT stays at `/` while the Angular app grows at `/next/`, and the
two swap only once Angular matches it.

Decisions:

- **Side by side**, not one long branch.
- **Stack as in Qwixx:** Angular 22, ngx-translate for the six languages, a client generated
  from an OpenAPI spec, Vitest, Playwright for end-to-end tests.
- **Every Angular rule from [ChecklistForProjects](https://github.com/AntondeGroot/ChecklistForProjects)
  on from the first commit.** The app starts empty, so nothing needs to be ratcheted in.
- **Same look.** `GameRoom.css` is reused (converted to SCSS so Stylelint can check it); any
  redesign is separate work afterwards.

## What has to move

- About 5,600 lines of GWT client across five screens: lobby, character selection, room, game
  options, and the admin and logs pages.
- The 17 `RoomService` GWT-RPC calls, which have no REST version yet. They are the real blocker:
  Angular cannot call GWT-RPC. (`MessageService` already has REST equivalents under `/chat`.)
- Already plain HTTP, usable as they are: the lobby, room and chat SSE streams, chat, sprite
  sheets, profile pictures, admin and login.

## Steps

| # | Step | Where players see it |
|---|------|----------------------|
| 0 | **API spec first.** Write `gameroom_openapi.yml` describing the existing REST and SSE endpoints and REST versions of the 17 RPC calls. Lint it with Redocly in CI before code generation. Spring implements interfaces generated from the spec, and contract tests check real responses. GWT keeps using RPC. | nothing changes |
| 1 | **Angular skeleton** in a new `GameRoom-web/` module, built by Maven and served at `/next/`, with all gates below. CI gets a single required `ci` job that needs every other job, plus the checklist's `ruleset.json`. | empty app at `/next/` |
| 2 | **Lobby and character selection**: lobby stream, cookies, language, room passwords. | `/next/` |
| 3 | **Room**: the D3 player animation (using the `d3` package directly), chat with `ChatCipher`, presence rings, the menu and the folding settings. | `/next/` |
| 4 | **Game options**, including the iframe settings Qwixx uses. | `/next/` |
| 5 | **Admin and logs pages.** | `/next/` |
| 6 | **End-to-end tests in Playwright** against `/next/`, covering what `GameRoomE2ETest` and `MobileLobbyUxTest` check. The Selenium tests keep guarding GWT until the switch. | — |
| 7 | **Switch.** Angular moves to `/`. Then delete the GWT client, `GameRoom-shared`, the RPC servlet and `GwtRpcPolicyController`, and update `deploy.sh`. | everyone |

## Gates from day one

- **TypeScript:** `strict`, `noUnusedLocals`, `noUnusedParameters`, `noUncheckedIndexedAccess`,
  `strictTemplates`, extended diagnostics as errors, typed forms, no `any`.
- **ESLint with zero warnings:**
  - sonarjs, with cognitive complexity capped at 15 and `todo-tag` off;
  - size caps: 200-line files, 25-line functions, one class per file;
  - `eslint-plugin-boundaries` v6 with the TypeScript import resolver. Components may import
    the generated DTO types but not the generated API services; data access goes through an
    app service.
- **Stylelint:** the standard SCSS config, plus a local rule that every `:hover` sits inside
  `@media (hover: hover) and (pointer: fine)`.
- **Prettier:** `quoteProps: consistent`, and the `angular` parser for `src/app/**/*.html`.
- **Tests:** Vitest with 90% line and branch coverage. Husky runs lint and format before commit,
  and build and test before push.
- **Security:** `npm audit` fails CI on high or critical; Renovate keeps dependencies current.
- **Folder sizes:** a script fails CI when a folder passes 8 non-test files, apart from a
  reviewed list of lookup folders such as generated models.

The checklist's Java gates (Checkstyle, PMD, SpotBugs, NullAway, ArchUnit, 90% JaCoCo,
`-Werror`, mutation testing) are a separate track, not part of this migration.

## Traps

- **`ChatCipher` must match byte for byte.** While both clients run, GWT and Angular players
  share rooms, so the TypeScript version must produce identical output. Use the vectors in the
  Java `ChatCipherTest` as fixtures for the TypeScript test.
- **Room cleanup depends on the room stream.** The server tracks presence, and deletes rooms
  nobody has open, from `/rooms/{id}/stream` connections. The Angular room page must open the
  stream on entry and close it on leaving.
- **Links from outside the app:** `?room=…` and `/rejoin` must keep working, because the games
  use them to send players back. The `playerid` and language cookies must keep their names.
- **`eslint-plugin-boundaries` passes silently without the TypeScript import resolver.** Add a
  known violation once, watch the rule fail, then remove it.
- **`deploy.sh` cache-busts CSS by editing `index.html` and `login.html`.** Update those paths
  once Angular serves the page.

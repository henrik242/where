Default to Bun instead of Node.js: `bun <file>`, `bun test`, `bun install`, `bun run <script>`,
`bun build`. Bun loads `.env` automatically, so no dotenv.

## APIs

Prefer Bun built-ins over the usual npm equivalents:

- `Bun.serve()` (WebSockets, HTTPS, routes) instead of `express`
- `bun:sqlite` instead of `better-sqlite3`; `Bun.sql` instead of `pg`; `Bun.redis` instead of `ioredis`
- Built-in `WebSocket` instead of `ws`
- `Bun.file` over `node:fs` readFile/writeFile; ``Bun.$`ls` `` instead of execa

Bun API docs are in `node_modules/bun-types/docs/**.md`.

# CLAUDE.md

## Build & Test

- Build: `cd app && ../gradlew assembleDebug`
- Test: `cd app && ../gradlew testDebugUnitTest`
- Lint: `cd app && ../gradlew lint`
- Single test: `cd app && ../gradlew testDebugUnitTest --tests "no.synth.where.ClassName"`
- Web server: `cd web && bun run dev`
- Web tests: `cd web && bun test`

## General Code Guidelines

- Maintain good test coverage — write tests for new logic
- Keep code clean and concise; no needless comments or boilerplate

## Android App Code Guidelines

- Follow Android design guidelines and Material 3 best practices
- Kotlin with Jetpack Compose, Koin for DI, Room for local storage
- Use Timber for logging, never `println` or `Log.*`

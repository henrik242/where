# CLAUDE.md

## Build & Test

- Build: `cd app && ../gradlew assembleDebug`
- Test: `cd app && ../gradlew testDebugUnitTest`
- Lint: `cd app && ../gradlew lint`
- Single test: `cd app && ../gradlew testDebugUnitTest --tests "no.synth.where.ClassName"`
- Build shared module: `./gradlew :shared:assembleDebug`
- Web server: `cd web && bun run dev`
- Web tests: `cd web && bun test`

## General Code Guidelines

- Maintain good test coverage — write tests for new logic
- Keep code clean and concise; no needless comments or boilerplate
- Make sure features are implemented both in Android and iOS, but keep as much of the implementation
  as shared/common code. Try to avoid ios/android specific code if you can.

## Android App Code Guidelines

- Follow Android design guidelines and Material 3 best practices
- Kotlin with Jetpack Compose, Koin for DI, Room for local storage
- Use Timber for logging, never `println` or `Log.*`

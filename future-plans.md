# UIPTV Architecture Refactoring Plan

## Phase Overview

This plan now follows a **5-step modernization roadmap** instead of the previous Spring Boot/Hibernate path:

1. **Phase 1:** Backend/frontend separation
2. **Backend/Core Rewrite:** Kotlin rewrite of shared backend/core code
3. **Phase 2:** Ktor + Exposed/jdbi + Flyway backend modernization
4. **UI Rebuild:** Compose Multiplatform client/UI rebuild
5. **Phase 3:** Client sync and player expansion

This ordering reduces double work, keeps the stack Kotlin-first, and makes the later mobile/client work materially easier.

---

## Context

### Current State

UIPTV is currently a JavaFX desktop application with:
- backend services using Singleton-style access
- a manual SQLite access layer built around `BaseDb` and `DatabaseUtils`
- an HTTP server based on `com.sun.net.httpserver.HttpServer`
- UI code tightly coupled to JavaFX desktop classes

### Problems

- Backend logic is not cleanly reusable outside the desktop app
- Mobile and cross-platform clients would duplicate business logic
- JavaFX dependencies leak into code that should be backend-only
- Current database and service patterns are harder to test and evolve
- A direct Spring Boot/Hibernate migration would add significant framework churn without matching the Kotlin-first target architecture

### Target Direction

The preferred long-term stack is:

- **Kotlin** for backend/core and future shared code
- **Ktor** for HTTP APIs and backend runtime
- **Exposed** or **jdbi** for persistence access
- **Flyway** for database migration/versioning
- **Compose Multiplatform (CMP)** for the rebuilt UI/client layer
- shared DTOs and serialization contracts that are easy to reuse across desktop, Android, and future clients

### Effort Baseline

These estimates assume `1 credit ~= 1 focused engineering hour`.

- **Phase 1:** `180-260 credits`
- **Backend/Core Kotlin rewrite:** `120-180 credits`
- **Phase 2 (Ktor + Exposed/jdbi + Flyway):** `130-210 credits`
- **Compose Multiplatform UI rebuild:** `220-360 credits`
- **Phase 3:** `120-190 credits`

**Expected total:** `770-1,200 credits`

The biggest uncertainty remains the persistence layer migration and the amount of behavior that is currently implicit in the existing Java/JavaFX code paths.

---

## Phase 1: Backend-Frontend Separation

### Objective

Decouple backend services from JavaFX and establish a clear backend boundary that can be reused by later Kotlin/Ktor and Compose work.

### Scope

#### Backend Services to Isolate

Backend-oriented services that should move behind stable interfaces:

- `AccountService.java`
- `AccountInfoService.java`
- `ChannelService.java`
- `CategoryService.java`
- `BookmarkService.java`
- `ConfigurationService.java`
- `SeriesEpisodeService.java`
- `SeriesWatchStateService.java`
- `VodWatchStateService.java`
- `SeriesWatchingNowSnapshotService.java`
- `FilterLockService.java`
- `CacheService.java`
- `M3U8PublicationService.java`
- `ImdbMetadataService.java`
- `PlayerRequestResolver.java`
- `BingeWatchService.java`
- `DatabaseSyncService.java`
- `InMemoryHlsService.java`
- `LogoResolverService.java`
- `HandshakeService.java`
- resolver and cache reloader classes

#### Services Requiring Special Treatment

These currently cross the backend/UI boundary and should be split or wrapped:

- `PlayerService.java`
- `FfmpegService.java`
- `LitePlayerFfmpegService.java`
- `XtremePlayerService.java`
- `DatabaseSyncService.java`
- `ReloadCachePopup.java`

#### Frontend-Only Code

These stay desktop/UI-specific for now:

- `com.uiptv.ui.*`
- `com.uiptv.player.*`
- `com.uiptv.widget.*`

### Deliverables

1. Create a backend-oriented package/module structure
2. Remove JavaFX dependencies from backend logic
3. Define service interfaces and DTO boundaries
4. Isolate database path/configuration concerns
5. Split backend tests from frontend tests

### Suggested Package Direction

```text
com.uiptv.backend/
  api/
  dto/
  service/
  repository/
  config/
```

### Verification

- backend code can be instantiated without JavaFX
- no JavaFX imports remain in backend packages
- tests can run with backend-only setup
- database path can be configured externally

---

## Backend/Core Rewrite: Kotlin

### Objective

Translate backend/core code to Kotlin before the Ktor migration so later work happens on the target language rather than repeating refactors in Java and then Kotlin.

### Why This Comes Before Phase 2

- avoids migrating Java service patterns into a new Ktor stack and then rewriting again
- gives a consistent language for DTOs, services, and future shared contracts
- improves fit with coroutines, serialization, and Compose Multiplatform

### Scope

- convert backend/core services to Kotlin
- convert shared models and DTOs that will be reused by Ktor/CMP
- preserve behavior during translation; avoid architectural churn beyond what is needed for Kotlin-first structure
- keep UI rewrite out of this step

### Deliverables

1. Kotlin backend/core source layout
2. Kotlin DTO and domain model layer for backend APIs
3. Tests updated to run against Kotlin implementations
4. Transitional interop where desktop JavaFX code still calls rewritten backend code

### Verification

- existing backend behavior still passes regression tests
- Java/Kotlin interop remains stable while desktop UI still exists
- no functional changes are mixed in unless required by the rewrite

---

## Phase 2: Ktor Backend Modernization

### Objective

Replace the current server and persistence plumbing with a Kotlin-native backend stack:

- **Ktor** for HTTP APIs/runtime
- **Exposed** or **jdbi** for database access
- **Flyway** for schema migrations
- **HikariCP** for connection pooling

### Why Not Spring Boot/Hibernate

That stack adds framework migration cost that does not align with the target architecture:

- more container and annotation migration work
- heavier ORM conversion burden
- lower payoff if the codebase is moving to Kotlin-first and CMP

Ktor keeps the server layer smaller and easier to align with the desktop and future client code.

### Persistence Recommendation

Preferred choices:

- **Exposed** if you want a Kotlin-centric SQL/DSL approach
- **jdbi** if you want explicit SQL with lighter abstraction and strong control

Either is better than combining Ktor with ad hoc persistence sprawl.

### Scope

#### Server Layer

- replace the current `HttpServer`-based JSON handlers with Ktor routes
- introduce structured request/response DTOs
- centralize configuration, error handling, and serialization

#### Persistence Layer

- retire or wrap the current `BaseDb` pattern
- migrate database access to Exposed or jdbi
- keep SQLite support practical and explicit
- move schema evolution to Flyway

#### Service Layer

- finish removing Singleton access patterns
- use constructor-injected services/modules
- make backend runtime bootable independently from the JavaFX app

#### Configuration

- make database path resolution explicit and externalized
- continue honoring `uiptv.ini` or an equivalent configuration bridge during transition

### Deliverables

1. `Ktor` application bootstrap
2. route modules replacing the existing JSON server handlers
3. migrated persistence layer on `Exposed` or `jdbi`
4. `Flyway` migration pipeline
5. backend tests for API, services, and persistence

### Verification

- Ktor application starts and serves all required endpoints
- existing backend workflows still function correctly
- database migrations run reliably on existing installations
- API contracts are stable enough for CMP and Android clients

### Remaining TODO

The backend migration is now structurally complete: Ktor is active, the legacy HTTP bridge is gone, Exposed/Flyway/HikariCP are in place, and the live API/runtime surface is covered by tests. Phase 2 still has cleanup work left before the backend should be treated as fully modernized:

1. **Finish replacing singleton-driven service wiring**
   - continue pushing constructor-owned instances through the remaining service graph
   - reduce `getInstance()` usage to JavaFX compatibility edges only
   - stop relying on global singletons as the default runtime path inside `server`

2. **Reduce custom JSON wrapper usage in runtime internals**
   - the route surface is typed, but parser/metadata internals still lean on `KJsonObject` / `KJsonArray`
   - replace those with typed Kotlin models where the shape is stable and reused
   - keep raw JSON handling limited to true boundary code

3. **Normalize the Exposed persistence layer further**
   - continue simplifying repositories that still read like hand-written table wrappers
   - keep CRUD/query patterns consistent across `db/*`
   - treat `SQLConnection` as a compatibility facade for JavaFX/tests, not the internal runtime path

4. **Isolate legacy migration/config compatibility**
   - keep `uiptv.ini` and existing SQLite installations working
   - continue shrinking legacy startup/migration compatibility code into isolated helper boundaries
   - keep the runtime configuration path explicit and predictable for desktop-hosted backend use

5. **Reassess Kotlin-first contracts before Phase 3**
   - verify which service/parser responses should become shared DTOs
   - keep the backend contracts stable enough for CMP/Android work in the next phase

These remaining slices are still part of Phase 2 and should be completed before treating the backend platform modernization as fully finished.

---

## UI Rebuild: Compose Multiplatform

### Objective

Re-imagine the UI layer in Compose Multiplatform after the backend and API shape have stabilized.

### Why This Comes After Phase 2

- the UI should target a stable backend/API surface
- shared Kotlin DTOs and services reduce duplication
- avoids redoing UI work while the backend contracts are still moving

### Scope

- replace JavaFX-specific UI with Compose Multiplatform
- build a client architecture that can serve desktop first and support Android expansion
- keep player integration as a boundary where platform-specific implementations can differ

### Suggested UI Areas

- account management
- category and channel browsing
- bookmark workflows
- configuration/settings
- sync status and remote actions
- playback initiation and player configuration

### Deliverables

1. CMP app/module structure
2. desktop CMP replacement for core JavaFX screens
3. shared UI/domain contracts with backend
4. platform-specific hooks for player and filesystem integration

### Verification

- core desktop workflows are functional in the CMP app
- parity exists for the critical user paths before JavaFX retirement
- API/client integration works against the Ktor backend

---

## Phase 3: Client Sync and Player Expansion

### Objective

Build out the cross-device client features after the Ktor backend and CMP client foundation exist.

### Scope

#### Sync

- account sync
- bookmark sync
- watching-progress sync
- conflict handling and health checks

#### Playback

- embedded player support where appropriate
- external player configuration and handoff
- platform-specific playback fallbacks

#### Client Expansion

- Android-focused delivery on top of the CMP/shared client foundation
- future iOS/web reuse where practical

### Deliverables

1. robust sync flows between client and backend
2. player configuration and playback behavior across supported platforms
3. client-side persistence/caching where needed
4. operational tooling for troubleshooting sync and playback issues

### Verification

- sync is resilient across restart/network interruption scenarios
- playback works for primary account/content types
- watching state updates are consistent across devices

---

## Recommended Execution Order

1. **Phase 1:** backend/frontend separation
2. **Backend/Core Rewrite:** Kotlin rewrite of backend/core
3. **Phase 2:** Ktor + Exposed/jdbi + Flyway backend modernization
4. **UI Rebuild:** Compose Multiplatform client/UI rebuild
5. **Phase 3:** client sync/player expansion

This is the preferred roadmap because it minimizes rework and keeps the stack coherent:

- separation first
- language consolidation second
- backend platform modernization third
- UI rebuild on top of stable contracts
- advanced sync/player work last

---

## Branch Strategy Note

There is already backend/frontend separation work on the `dev` branch. That branch should be treated as a source of architectural progress, but it should not be merged blindly if `main` has moved significantly.

Recommended approach:

1. diff `dev` against current `main`
2. identify backend-separation commits that are still conceptually valid
3. merge or cherry-pick in logical batches
4. resolve conflicts while preserving newer `main` behavior where it is intentional
5. only then proceed with Kotlin/Ktor/CMP work

If divergence is high, a controlled replay of the backend-separation changes onto current `main` may be cheaper than a single large merge commit.

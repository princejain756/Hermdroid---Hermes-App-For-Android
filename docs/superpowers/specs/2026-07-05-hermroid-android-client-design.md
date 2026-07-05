# Hermroid Android Client Design

Status: approved for implementation planning  
Date: 2026-07-05  
Product: Hermroid  
Target: Android phones, foldables, and tablets  
License: MIT

## 1. Product definition

Hermroid is a native Android control surface for a self-hosted Hermes agent. It connects to `hermes-webui`; the agent, tools, models, memory, and files remain on the user's server. Hermroid owns the Android interaction, local cache, background behavior, and presentation.

Hermroid will provide the feature depth of Hermex with an Android application written in Kotlin and Jetpack Compose. The interface will closely follow Hermex's bright, compact, glass-forward design while using Android lifecycle, navigation, accessibility, and system-integration APIs.

Hermroid is an independent project. It is not affiliated with the Hermex author, Nous Research, or the `hermes-webui` maintainers. The repository will preserve the MIT notices for source or assets derived from Hermex and every third-party dependency or icon set.

## 2. Goals

Hermroid must:

- match the current Hermex product contract, including advanced chat and server-management flows;
- render long, streaming agent conversations without visible layout instability;
- survive rotation, process death, temporary network loss, and app backgrounding;
- keep server credentials and custom authentication headers encrypted at rest;
- remain useful offline for cached sessions and messages;
- support compact phones, foldables, tablets, keyboards, and large text;
- ship as a reproducible, tested GitHub release with APKs, checksums, documentation, screenshots, and a demo;
- establish a coherent design system that contributors can extend without visual drift.

## 3. Non-goals

The first public release excludes:

- running Hermes or Python directly on Android;
- controlling Android applications through Accessibility Service;
- a hosted relay or Hermroid account service;
- Android Auto and Wear OS;
- undocumented server endpoints or invented response shapes;
- Apple-owned fonts, SF Symbols, or copied Hermex trademarks.

## 4. Compatibility baseline

Initial development references:

- Hermex commit `f6196cb77fa765a0b6a20afe77bdfb06fce05735`;
- Hermex-tested `hermes-webui` commit `f1d399b437c1ca7fe4b6d2093aebe334c32f34a3`.

The repository will record both pins. Updating either pin requires passing contract tests and real-server smoke tests. Models must ignore unknown fields and decode optional or historically inconsistent fields defensively. The wire response from the pinned server remains authoritative.

## 5. Application architecture

Hermroid uses a modular, unidirectional architecture:

```text
Compose screen
    -> ViewModel and immutable UI state
        -> feature use case
            -> repository
                -> REST/SSE client
                -> Room cache
                -> encrypted settings
```

The planned Gradle modules are:

- `app`: application entry point, navigation, deep links, share target, lifecycle, and dependency graph;
- `core:model`: API, database, and UI-domain models;
- `core:network`: REST requests, cookie jar, SSE, multipart uploads, redirects, and custom headers;
- `core:database`: Room entities, migrations, DAOs, and cache policy;
- `core:security`: Android Keystore-backed encryption and server-scoped secrets;
- `core:design`: tokens, icons, typography, motion, reusable surfaces, and adaptive layout primitives;
- `core:testing`: fixtures, fake repositories, dispatchers, and screenshot-test utilities;
- one module for each major feature: onboarding, sessions, chat, tasks, skills, memory, insights, workspace, Git, profiles, projects, settings, and voice.

Feature modules depend on core interfaces, not concrete network or database implementations. ViewModels expose `StateFlow` values and receive user actions through explicit functions. Compose functions render state and emit actions; they do not call network or database code.

## 6. Platform and dependencies

- Kotlin and Kotlin coroutines
- Jetpack Compose with Material 3 foundations
- Navigation Compose with type-safe destinations
- Hilt dependency injection
- OkHttp for REST, cookies, multipart transfer, and SSE transport
- Moshi for tolerant JSON decoding
- Room for local session and message data
- DataStore for non-secret preferences
- Android Keystore with encrypted, application-owned secret blobs
- WorkManager for bounded reconciliation and cleanup work
- a foreground service for user-visible, active agent streams
- JUnit, kotlinx-coroutines-test, MockWebServer, Room tests, and Compose UI tests

The app keeps the existing minimum SDK 26 unless a verified dependency forces an increase. Blur and advanced rendering use API-level capability checks; lower API levels receive an opaque or translucent fallback with the same layout and contrast.

## 7. Feature scope

### 7.1 Onboarding and servers

- welcome and feature overview;
- HTTPS server URL entry and normalization;
- localhost and Tailscale HTTP exceptions;
- health and authentication-status checks;
- password authentication with a persistent, server-scoped cookie jar;
- custom proxy headers with secret-value masking;
- multiple saved servers, switching, renaming, and removal;
- per-server credentials, headers, cookie state, cache, and last-opened location;
- connection diagnostics with specific remediation text;
- passkey-only server explanation when password authentication is unavailable.

Adding or probing a server must never mutate the active server's headers, cookies, cache, or login state until the new connection succeeds.

### 7.2 Sessions, projects, and profiles

- paged session list with search, filters, pinning, and archive state;
- cached list available offline;
- create, open, rename, delete, pin, archive, move, branch, and truncate flows;
- projects with counts, colors, create, and move actions supported by the server;
- profile selection and switching;
- deep links to a session, new chat, voice chat, or profile-specific chat;
- clear confirmations for destructive actions.

### 7.3 Chat

- create and resume conversations;
- incremental history paging;
- token-by-token assistant output;
- interim assistant text, reasoning, tool start, tool completion, title, completion, cancellation, transport error, application error, approval, clarification, and pending-steer events;
- collapsible reasoning and tool groups;
- approval and clarification overlays;
- steer, queue, cancel, regenerate, edit, copy, share, branch, and truncate actions;
- model, provider, reasoning effort, profile, project, and workspace controls;
- model recents and favorites;
- slash-command discovery and completion;
- context-window usage display;
- Markdown, task lists, links, tables, syntax-highlighted code, math, images, audio, and attachment cards;
- file, image, archive, and audio attachments supported by the server;
- link previews that never leak authentication headers to another origin;
- share-to-Hermroid drafts from other Android applications.

Only one live stream may own a session at a time on one device. Opening the same session from another screen attaches to the existing stream state.

### 7.4 Voice

- Android speech recognition for dictated prompts;
- voice-note recording and upload when supported by the server;
- server transcription when supported;
- optional response playback through Android text-to-speech or the server's TTS endpoint;
- explicit microphone permission, recording state, cancellation, and error recovery;
- audio focus and lifecycle handling.

### 7.5 Tasks

- task list, enabled and running state, schedule, output, and detail;
- create, edit, run, pause, resume, and delete only where the pinned server exposes verified endpoints;
- local schedule formatting with raw cron expression available;
- confirmation before run or destructive mutation.

### 7.6 Skills and memory

- searchable, categorized skill list;
- rendered skill details and linked skill files;
- memory notes and user profile sections;
- write actions only when verified by the pinned API; otherwise the screen states that it is read-only.

### 7.7 Insights

- session-derived token, cost, model, and activity summaries;
- per-session usage refresh;
- local timeframe and project filtering;
- transparent labels that distinguish calculated mobile summaries from a server-native insights endpoint.

### 7.8 Workspace and Git

- workspace selection, directory browsing, breadcrumbs, search, and refresh;
- text, code, image, and supported binary previews;
- file export through Android's Storage Access Framework;
- Git status, changed files, staged and unstaged diffs, branch picker, commit flow, and turn-change summary where verified by the pinned server;
- no local filesystem assumptions about the remote server.

### 7.9 Settings and system integration

- system, light, and dark theme;
- configurable completion notifications;
- default model and profile;
- server list and custom headers;
- cache size, clear-cache action, licenses, privacy, diagnostics, and app version;
- Android notification channels for active runs, completion, and errors;
- foreground-service notification with open and cancel actions;
- launcher shortcuts for new chat and voice chat;
- share target and deep links;
- a home-screen widget for new chat, active-run status, and recent sessions.

## 8. Streaming state machine

A stream moves through these states:

```text
idle
  -> starting
  -> connecting
  -> streaming
  -> awaitingApproval | awaitingClarification
  -> streaming
  -> completed | cancelled | failed | disconnected
```

The repository persists the session ID, stream ID, last event ID when available, partial assistant text, pending interaction, and terminal state. The UI renders the persisted state after recreation.

On transport loss, Hermroid checks the stream-status endpoint. If the run remains active, it reconnects and resumes from the server-supported point. If status is terminal, it refreshes the session. Hermroid never resends the user's prompt automatically. Manual retry starts a new request only after explaining the state.

When the app leaves the foreground during an active run, a foreground service owns the connection and exposes progress through a notification. The service stops after the stream reaches a terminal state and the cache is committed.

## 9. Persistence

Room stores server-scoped cache records for sessions, messages, attachments, tool events, pending interactions, model metadata, and active-run metadata. Database rows never store passwords, bearer tokens, custom secret headers, or reusable session secrets.

DataStore stores theme, notification choices, display preferences, model favorites, and non-secret server metadata. Android Keystore protects server passwords, sensitive custom header values, and other reusable secrets through authenticated encryption.

Cache writes occur in transactions at meaningful stream boundaries. Partial assistant text may be checkpointed at a throttled interval to support process restoration without writing every token.

## 10. Network and security

- HTTPS is the default and required for public hostnames.
- Cleartext HTTP is allowed only for emulator localhost and Tailscale's `100.64.0.0/10` range.
- Requests omit browser `Origin` and `Referer` headers.
- Built-in `Accept` and `Content-Type` headers override custom values.
- Authentication headers apply only to the configured origin and are stripped on cross-origin redirects.
- External media and link-preview requests use a credential-free client.
- Logs redact passwords, cookies, authorization values, custom secret headers, prompts, and message bodies.
- Release builds disable verbose network logging.
- Exported Android components use the narrowest possible intent filters and permissions.
- Backup rules exclude secrets and session cookies.
- The repository includes a security policy and threat model.

## 11. Visual system

Hermroid uses a faithful Android interpretation of Hermex's visual language:

- bright neutral canvas, strong black typography, and restrained semantic color;
- Inter for interface text and an open monospaced font for code;
- compact information density with a consistent 4 dp spacing scale;
- floating translucent composer with model and reasoning controls inside the same visual group;
- purposeful blur for modal separation and the composer, never as generic card decoration;
- compact sheets and menus with clear selected, pressed, focused, loading, disabled, and error states;
- collapsible dark reasoning surfaces and quiet tool activity groups;
- precise Markdown, code, table, media, and attachment layouts;
- a distinct gold Hermroid wordmark and icon rather than copied Hermex branding;
- separately designed light and dark palettes;
- motion tokens for navigation, sheets, expansion, streaming insertion, and press feedback;
- reduced-motion behavior for every nonessential transition.

Lucide is the primary icon family. Designers may use Iconify to discover and export icons, but the app commits reviewed Android vector assets and performs no runtime Iconify requests. Every imported set retains its license and attribution. Apple fonts and SF Symbols are prohibited.

Touch targets measure at least 48 dp. Text meets WCAG AA contrast, supports Android font scaling, and remains usable at 200 percent. Icon-only actions have content descriptions and tooltips where pointer or keyboard input exists.

## 12. Adaptive layouts

Compact phones use a navigation drawer for server panels and a stack-based session/chat flow. Modal selectors use bottom sheets where space permits. The system back gesture and predictive-back animation follow Android conventions.

Medium and expanded windows use navigation rail or permanent drawer plus list-detail panes. Tablets can keep the project/session hierarchy visible beside chat or workspace content. Foldable layouts avoid hinges and preserve the current selection when posture changes.

All layouts support portrait, landscape, hardware keyboard, mouse, and trackpad. No feature depends on hover, swipe, or long press alone.

## 13. Error handling

Repositories return typed failures: network unavailable, timeout, unauthorized, forbidden, incompatible server, malformed response, missing feature, upload rejected, storage failure, and stream interruption.

- Unauthorized responses invalidate only the affected server state and route to reauthentication without deleting its cache.
- Network failures keep drafts, partial output, scroll position, and retry context.
- Decode failures retain the raw event type and a redacted diagnostic summary.
- Unsupported features stay visible with an explanation when this aids discovery; they never fail silently.
- Field errors appear beside the field. Screen errors include a recovery action. Background failures create a notification only when the user enabled it or an active run requires attention.
- Destructive mutations require confirmation and refresh authoritative server state after completion.

## 14. Testing strategy

### Unit tests

- URL normalization and cleartext policy;
- custom-header scoping and redirect stripping;
- tolerant JSON models and lossy scalar decoding;
- every SSE event and state transition;
- repositories, cache policy, paging, and ViewModels;
- Markdown segmentation, tool grouping, and context-window calculations;
- notification and foreground-service state mapping.

### Contract tests

MockWebServer replays redacted fixtures captured from the pinned `hermes-webui` commit. Contract tests cover health, authentication, sessions, chat start and streaming, models, profiles, projects, upload, tasks, skills, memory, usage, workspace, files, and Git endpoints. Pin updates require a fixture audit.

### Persistence tests

- fresh database creation;
- every Room migration;
- server isolation;
- process-restoration checkpoints;
- cache pruning without active-run or favorite-session loss;
- secret exclusion from database and backup data.

### UI and accessibility tests

- onboarding, session, chat, task, skill, memory, insight, workspace, Git, and settings journeys;
- screenshot tests for light and dark themes at compact, medium, and expanded sizes;
- loading, empty, offline, error, long-content, and large-text states;
- TalkBack labels, traversal order, minimum touch targets, contrast, keyboard focus, and reduced motion;
- predictive back, rotation, fold posture, and window resizing.

### Integration and release tests

- real-server smoke tests against the pinned upstream commit;
- long streaming sessions and reconnects;
- app backgrounding, process death, and notification actions;
- attachment upload and file export;
- release build installation on API 26, API 31, and the latest stable Android API;
- phone, foldable, and tablet emulator matrix;
- baseline profile and macrobenchmark checks for startup, scroll, and chat streaming.

## 15. Delivery milestones

The complete first public release remains the target. Internal milestones keep the build reviewable:

1. foundation, design tokens, test harness, and onboarding;
2. secure multi-server networking and compatibility fixtures;
3. sessions, projects, profiles, and offline cache;
4. streaming chat and all interaction events;
5. attachments, rich rendering, voice, and system sharing;
6. tasks, skills, memory, and insights;
7. workspace, file preview/export, and Git;
8. background streams, notifications, adaptive layouts, accessibility, and performance;
9. security review, documentation, demo assets, release automation, and signed GitHub release.

Each milestone must compile, pass its tests, and preserve prior behavior. The repository remains private or unpublished until the release gate passes.

## 16. Public release gate

The first public release requires:

- all scoped features implemented or explicitly removed through a design revision;
- no open critical or high-severity security findings;
- passing unit, contract, migration, UI, accessibility, and release tests;
- successful real-server smoke tests;
- stable streaming and restoration during a one-hour agent session;
- verified phone and tablet layouts;
- MIT license, third-party notices, security policy, code of conduct, contribution guide, architecture notes, and reproducible build instructions;
- signed release APKs, SHA-256 checksums, changelog, screenshots, and demo video or GIF;
- repository description and topics that state the exact `hermes-webui` compatibility target;
- no claims of affiliation with Hermex or Nous Research.

## 17. Success criteria

A new user can install Hermroid, connect securely to a supported `hermes-webui` server, find or create a session, run an agent task, inspect reasoning and tool progress, handle approvals or clarifications, leave the app, receive completion status, return to the same conversation, and inspect the resulting remote files without losing state.

A contributor can clone the repository, run deterministic tests without a live server, launch a documented development configuration, understand module ownership, and verify a change before opening a pull request.

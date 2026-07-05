# Hermroid Dual Transport Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add safe protocol detection and authenticated Official Desktop support while preserving `hermes-webui` onboarding.

**Architecture:** Introduce a protocol-neutral `HermesTransport` boundary and two adapters. `DesktopTransport` combines authenticated REST with newline-delimited JSON-RPC over WebSocket; `WebUiTransport` retains REST/SSE. Onboarding probes without credentials, displays the detected protocol, and permits a manual override.

**Tech Stack:** Kotlin, coroutines and Flow, OkHttp REST/WebSocket, Moshi, MockWebServer, JUnit, and Jetpack Compose.

---

## File map

- `core:model/.../ServerProtocol.kt`: protocol choice, detected identity, and capabilities.
- `core:network/.../HermesTransport.kt`: shared connection contract.
- `core:network/.../ProtocolDetector.kt`: credential-free probe sequence.
- `core:network/.../WebUiTransport.kt`: existing health, auth-status, and login behavior.
- `core:network/.../DesktopAuthApi.kt`: provider discovery, password login, and WebSocket tickets.
- `core:network/.../DesktopJsonRpcClient.kt`: request correlation, event stream, and close handling.
- `core:network/.../DesktopTransport.kt`: official backend adapter.
- `feature:onboarding/...`: protocol selector, detected badge, provider/username/password inputs, and connection orchestration.

### Task 1: Define protocol and transport contracts

**Files:**
- Create: `core/model/src/main/kotlin/com/princejain/hermroid/model/ServerProtocol.kt`
- Create: `core/network/src/main/kotlin/com/princejain/hermroid/network/HermesTransport.kt`
- Test: `core/model/src/test/kotlin/com/princejain/hermroid/model/ServerProtocolTest.kt`

- [ ] **Step 1: Write the protocol identity test**

```kotlin
@Test fun `auto is not a resolved protocol`() {
    assertFalse(ServerProtocol.AUTO.isResolved)
    assertTrue(ServerProtocol.DESKTOP.isResolved)
    assertTrue(ServerProtocol.WEB_UI.isResolved)
}
```

- [ ] **Step 2: Run the test and verify failure**

Run `./gradlew :core:model:testDebugUnitTest --tests '*ServerProtocolTest'`.

Expected: FAIL because `ServerProtocol` does not exist.

- [ ] **Step 3: Implement shared types**

```kotlin
enum class ServerProtocol(val isResolved: Boolean) {
    AUTO(false), DESKTOP(true), WEB_UI(true)
}

data class ServerIdentity(
    val protocol: ServerProtocol,
    val displayName: String,
    val capabilities: Set<ServerCapability>,
)

enum class ServerCapability {
    SESSIONS, STREAMING_CHAT, MODELS, PROFILES, TASKS, SKILLS,
    MEMORY, ANALYTICS, FILES, GIT, VOICE, APPROVALS, CLARIFICATIONS
}
```

```kotlin
interface HermesTransport {
    val identity: ServerIdentity
    suspend fun verifyConnection(): ConnectionVerification
    suspend fun close()
}

sealed interface ConnectionVerification {
    data object Connected : ConnectionVerification
    data class PasswordRequired(val providers: List<AuthProvider>) : ConnectionVerification
}
```

- [ ] **Step 4: Run tests and commit**

Run `./gradlew :core:model:testDebugUnitTest :core:network:testDebugUnitTest`.

Expected: PASS.

Commit: `git commit -am "refactor(network): define Hermes transport boundary"` after adding new files.

### Task 2: Implement credential-free protocol detection

**Files:**
- Create: `core/network/src/main/kotlin/com/princejain/hermroid/network/ProtocolDetector.kt`
- Test: `core/network/src/test/kotlin/com/princejain/hermroid/network/ProtocolDetectorTest.kt`

- [ ] **Step 1: Write MockWebServer detection tests**

Test these fixtures:

```text
GET /api/auth/providers -> 200 {"providers":[...]} => DESKTOP
GET /api/auth/providers -> 503 {"detail":"no auth providers registered"} => DESKTOP
GET /api/auth/providers -> 404, GET /api/auth/status -> 200 {...}, GET /health -> 200 {"status":"ok"} => WEB_UI
all probes 401/404 => Ambiguous
network failure => Unreachable
```

Assert no probe includes `Authorization`, cookies, `Origin`, `Referer`, or a request body.

- [ ] **Step 2: Run and verify failure**

Run `./gradlew :core:network:testDebugUnitTest --tests '*ProtocolDetectorTest'`.

Expected: FAIL because `ProtocolDetector` does not exist.

- [ ] **Step 3: Implement the detector**

```kotlin
sealed interface DetectionResult {
    data class Detected(val protocol: ServerProtocol) : DetectionResult
    data object Ambiguous : DetectionResult
    data class Unreachable(val message: String) : DetectionResult
}
```

Use a cookie-free, redirect-disabled OkHttp client with a five-second timeout. Recognize Desktop by the shape of `/api/auth/providers`, including its documented 503 response. Probe `hermes-webui` only when Desktop is unrecognized. Limit response inspection to 16 KiB.

- [ ] **Step 4: Run tests and commit**

Run `./gradlew :core:network:testDebugUnitTest`.

Expected: PASS.

Commit: `git commit -m "feat(network): detect Hermes server protocol"`.

### Task 3: Preserve `hermes-webui` behind its adapter

**Files:**
- Rename: `core/network/src/main/kotlin/com/princejain/hermroid/network/AuthApi.kt` to `WebUiTransport.kt`
- Test: `core/network/src/test/kotlin/com/princejain/hermroid/network/WebUiTransportTest.kt`

- [ ] **Step 1: Add contract tests**

Verify `/health`, `/api/auth/status`, `/api/auth/login`, 401 mapping, cookie retention, and rejection of cross-origin redirects. Assert login JSON is exactly `{"password":"secret"}`.

- [ ] **Step 2: Run and verify the new tests fail**

Run `./gradlew :core:network:testDebugUnitTest --tests '*WebUiTransportTest'`.

Expected: FAIL until the class implements `HermesTransport`.

- [ ] **Step 3: Refactor without changing wire behavior**

Set identity to `WEB_UI`, map successful health/auth to `Connected`, and map enabled auth without credentials to `PasswordRequired`. Keep credentials scoped to the configured origin.

- [ ] **Step 4: Run tests and commit**

Run `./gradlew :core:network:testDebugUnitTest`.

Expected: PASS.

Commit: `git commit -m "refactor(network): isolate hermes-webui transport"`.

### Task 4: Implement Official Desktop authentication

**Files:**
- Create: `core/network/src/main/kotlin/com/princejain/hermroid/network/DesktopAuthApi.kt`
- Test: `core/network/src/test/kotlin/com/princejain/hermroid/network/DesktopAuthApiTest.kt`

- [ ] **Step 1: Write Desktop authentication tests**

Verify:

```text
GET /api/auth/providers decodes name, display_name, supports_password
POST /auth/password-login sends provider, username, password, next
successful login retains hermes session cookies
POST /api/auth/ws-ticket decodes ticket and ttl_seconds
401 maps to InvalidCredentials
429 maps to RateLimited
OAuth-only provider never exposes a password form
```

- [ ] **Step 2: Run and verify failure**

Run `./gradlew :core:network:testDebugUnitTest --tests '*DesktopAuthApiTest'`.

Expected: FAIL because `DesktopAuthApi` does not exist.

- [ ] **Step 3: Implement exact request models**

```kotlin
@JsonClass(generateAdapter = true)
data class DesktopPasswordLogin(
    val provider: String,
    val username: String,
    val password: String,
    val next: String = "",
)

@JsonClass(generateAdapter = true)
data class WsTicket(
    val ticket: String,
    @Json(name = "ttl_seconds") val ttlSeconds: Int,
)
```

Use one origin-scoped cookie jar for provider discovery, login, identity, and ticket minting. Never persist a `WsTicket`.

- [ ] **Step 4: Run tests and commit**

Run `./gradlew :core:network:testDebugUnitTest`.

Expected: PASS.

Commit: `git commit -m "feat(network): authenticate with Hermes Desktop"`.

### Task 5: Implement JSON-RPC WebSocket transport

**Files:**
- Create: `core/network/src/main/kotlin/com/princejain/hermroid/network/DesktopJsonRpcClient.kt`
- Create: `core/network/src/main/kotlin/com/princejain/hermroid/network/DesktopRpcModels.kt`
- Test: `core/network/src/test/kotlin/com/princejain/hermroid/network/DesktopJsonRpcClientTest.kt`

- [ ] **Step 1: Write WebSocket tests**

Use MockWebServer WebSocket upgrade to verify:

```json
{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{"skin":"default"}}}
{"jsonrpc":"2.0","id":"a1","method":"session.list","params":{"limit":200}}
{"jsonrpc":"2.0","id":"a1","result":{"sessions":[]}}
```

Assert unique IDs, response correlation, `event` notification delivery, JSON-RPC error mapping, unknown-event preservation, pending-request failure on socket close, and a fresh ticket for reconnect.

- [ ] **Step 2: Run and verify failure**

Run `./gradlew :core:network:testDebugUnitTest --tests '*DesktopJsonRpcClientTest'`.

Expected: FAIL because the client does not exist.

- [ ] **Step 3: Implement the client**

Expose:

```kotlin
interface DesktopRpcClient {
    val events: Flow<DesktopEvent>
    suspend fun connect(ticket: String)
    suspend fun request(method: String, params: Map<String, Any?> = emptyMap()): JsonValue
    suspend fun disconnect()
}
```

Use `ConcurrentHashMap<String, CompletableDeferred<JsonValue>>` for pending requests and `MutableSharedFlow` for events. Accept newline-delimited JSON objects and multiple objects in one WebSocket message. Wait for `gateway.ready` before reporting connection success.

- [ ] **Step 4: Run tests and commit**

Run `./gradlew :core:network:testDebugUnitTest`.

Expected: PASS.

Commit: `git commit -m "feat(network): add Desktop JSON-RPC WebSocket"`.

### Task 6: Update onboarding and rebuild the test APK

**Files:**
- Modify: `feature/onboarding/src/main/kotlin/com/princejain/hermroid/onboarding/OnboardingScreen.kt`
- Create: `feature/onboarding/src/main/kotlin/com/princejain/hermroid/onboarding/ProtocolSelector.kt`
- Test: `feature/onboarding/src/test/kotlin/com/princejain/hermroid/onboarding/OnboardingViewModelTest.kt`

- [ ] **Step 1: Test onboarding transitions**

Cover auto-detected Desktop, auto-detected Web UI, ambiguous detection, manual override, Desktop password provider, OAuth-only provider, wrong credentials, and successful connection. Failed probes must preserve the URL and manual protocol choice while clearing passwords after unauthorized responses.

- [ ] **Step 2: Run and verify failure**

Run `./gradlew :feature:onboarding:testDebugUnitTest`.

Expected: FAIL until onboarding uses the detector and transport factory.

- [ ] **Step 3: Implement the connection UI**

Keep Auto as default. Put Official Desktop and `hermes-webui` overrides in an Advanced protocol selector. Show a compact verified badge after detection. Desktop password providers display provider, username, and password fields; OAuth-only providers explain that mobile OAuth setup is not supported in this milestone. Web UI keeps its password-only form.

- [ ] **Step 4: Verify and build**

Run:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Expected: PASS and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 5: Commit and publish the branch**

```bash
git add core feature docs
git commit -m "feat: support Desktop and webui connections"
git push
```

## Completion criteria

- Auto mode identifies both pinned server types without sending credentials.
- Manual protocol selection resolves ambiguous gated deployments.
- Desktop password login and single-use ticket minting follow installed server source.
- JSON-RPC requests correlate correctly and events remain ordered.
- Existing `hermes-webui` connection behavior remains covered by tests.
- The updated APK builds and passes lint.

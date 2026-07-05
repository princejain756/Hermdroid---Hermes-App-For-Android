# Hermroid Foundation and Onboarding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a tested native Android foundation that securely connects to a supported `hermes-webui` server and establishes Hermroid's production design system.

**Architecture:** Keep the application entry point in `:app` and place reusable boundaries in `:core:model`, `:core:network`, `:core:security`, and `:core:design`. The onboarding feature owns its immutable state and ViewModel. Network and secret-storage implementations sit behind interfaces so unit tests run without Android services or a live server.

**Tech Stack:** Kotlin 2, Jetpack Compose, Material 3, Hilt, coroutines and StateFlow, OkHttp, Moshi, Android Keystore, JUnit, MockWebServer, and Compose UI tests.

---

## Scope and file map

This plan implements master-design milestones 1 and the authentication slice of milestone 2. It creates these units:

- `:core:model`: normalized server address and authentication domain models;
- `:core:security`: server-scoped secret interface plus Android Keystore implementation;
- `:core:network`: health, auth-status, login, cookies, headers, and redirect protection;
- `:core:design`: Hermroid tokens, light/dark themes, glass surface, typography, and icons;
- `:feature:onboarding`: connection form, diagnostics, state machine, and ViewModel;
- `:app`: Hilt graph, root navigation, launcher activity, and authenticated shell.

Later plans add Room and feature modules when their schemas are defined. This avoids a speculative database that migrations would need to repair.

The complete build uses this ordered plan series:

1. foundation and onboarding (this document);
2. Room-backed servers, sessions, projects, and profiles;
3. SSE streaming chat, inline model switcher, tools, reasoning, approvals, and clarifications;
4. rich Markdown/media, attachments, voice, sharing, and foreground execution;
5. tasks, skills, memory, insights, and settings;
6. workspace browsing, file export, and Git operations;
7. widgets, adaptive-layout hardening, accessibility, performance, security, and GitHub release automation.

Each plan must end in working, tested software and pass all earlier plan checks before the next plan begins.

### Task 1: Establish a reproducible Hermroid build

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `.gitignore`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/xml/network_security_config.xml`
- Create: module build files under `core/*` and `feature/onboarding`
- Create: Gradle wrapper files

- [ ] **Step 1: Add a build smoke test to CI**

Create `.github/workflows/android.yml`:

```yaml
name: Android
on:
  pull_request:
  push:
    branches: [main]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - uses: android-actions/setup-android@v3
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew testDebugUnitTest lintDebug assembleDebug --stacktrace
```

- [ ] **Step 2: Register the modules and rename the project**

Set `rootProject.name = "Hermroid"` and include:

```kotlin
include(
    ":app",
    ":core:model",
    ":core:network",
    ":core:security",
    ":core:design",
    ":feature:onboarding",
)
```

Add an `android-library` plugin alias using the same AGP version. Library modules use namespace `com.princejain.hermroid.<module>`, SDK 35, minimum SDK 26, Java 17, and consumer ProGuard rules only where needed.

- [ ] **Step 3: Configure the application identity**

Change the application ID and namespace to `com.princejain.hermroid`. Set the label to `Hermroid`, attach `@xml/network_security_config`, declare `INTERNET`, and export only `MainActivity` with the launcher intent filter.

Android Network Security Config cannot express the dynamic Tailscale `100.64.0.0/10` range. Permit cleartext at the platform layer, then make `ServerAddress` the mandatory constructor for every server client so public HTTP URLs cannot reach OkHttp:

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="true" />
</network-security-config>
```

No API may accept a raw `HttpUrl`; each client requires a validated `ServerAddress`. Tests must prove that public cleartext input fails before OkHttp creates a call. This preserves localhost and dynamic Tailscale support without permitting public HTTP through application code.

- [ ] **Step 4: Generate and verify the wrapper**

Run:

```bash
gradle wrapper --gradle-version 8.9
./gradlew projects
```

Expected: all six modules appear and Gradle exits successfully.

- [ ] **Step 5: Commit the build baseline**

```bash
git add .github .gitignore settings.gradle.kts build.gradle.kts gradle gradlew gradlew.bat app core feature
git commit -m "build: bootstrap modular Hermroid project"
```

### Task 2: Implement and test server URL policy

**Files:**
- Create: `core/model/src/main/kotlin/com/princejain/hermroid/model/ServerAddress.kt`
- Create: `core/model/src/test/kotlin/com/princejain/hermroid/model/ServerAddressTest.kt`

- [ ] **Step 1: Write the failing normalization tests**

```kotlin
class ServerAddressTest {
    @Test fun `adds https when scheme is absent`() {
        assertEquals("https://hermes.example.com/", ServerAddress.parse("hermes.example.com").baseUrl.toString())
    }

    @Test fun `keeps tailscale cleartext address`() {
        assertEquals("http://100.64.12.34:8787/", ServerAddress.parse("http://100.64.12.34:8787").baseUrl.toString())
    }

    @Test fun `rejects public cleartext host`() {
        assertFailsWith<InvalidServerAddress> { ServerAddress.parse("http://hermes.example.com") }
    }

    @Test fun `rejects query parameters`() {
        assertFailsWith<InvalidServerAddress> { ServerAddress.parse("https://example.com/hermes?x=1") }
    }
}
```

- [ ] **Step 2: Run the test and verify failure**

Run `./gradlew :core:model:testDebugUnitTest --tests '*ServerAddressTest'`.

Expected: FAIL because `ServerAddress` does not exist.

- [ ] **Step 3: Implement the policy**

```kotlin
@JvmInline
value class ServerAddress private constructor(val baseUrl: HttpUrl) {
    companion object {
        fun parse(raw: String): ServerAddress {
            val candidate = raw.trim().let { if ("://" in it) it else "https://$it" }
            val url = candidate.toHttpUrlOrNull() ?: throw InvalidServerAddress("Enter a valid server URL.")
            require(url.username.isEmpty() && url.password.isEmpty()) { "Credentials must use the password field." }
            require(url.query == null && url.fragment == null) { "Remove query parameters and fragments." }
            if (url.scheme == "http" && !url.isAllowedCleartextHost()) {
                throw InvalidServerAddress("Public servers must use HTTPS.")
            }
            return ServerAddress(url.newBuilder().encodedPath(url.encodedPath.trimEnd('/') + "/").build())
        }
    }
}

class InvalidServerAddress(message: String) : IllegalArgumentException(message)

private fun HttpUrl.isAllowedCleartextHost(): Boolean {
    if (host == "localhost" || host == "10.0.2.2" || host == "127.0.0.1") return true
    val octets = host.split('.').mapNotNull(String::toIntOrNull)
    return octets.size == 4 && octets[0] == 100 && octets[1] in 64..127 && octets.drop(2).all { it in 0..255 }
}
```

- [ ] **Step 4: Run model tests**

Run `./gradlew :core:model:testDebugUnitTest`.

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/model
git commit -m "feat(model): validate Hermes server addresses"
```

### Task 3: Build server-scoped encrypted secret storage

**Files:**
- Create: `core/security/src/main/kotlin/com/princejain/hermroid/security/SecretStore.kt`
- Create: `core/security/src/main/kotlin/com/princejain/hermroid/security/AndroidSecretStore.kt`
- Create: `core/security/src/test/kotlin/com/princejain/hermroid/security/SecretStoreContractTest.kt`

- [ ] **Step 1: Define the contract and fake-backed contract test**

```kotlin
interface SecretStore {
    suspend fun put(serverId: String, name: String, value: String)
    suspend fun get(serverId: String, name: String): String?
    suspend fun removeServer(serverId: String)
}

abstract class SecretStoreContractTest {
    abstract fun store(): SecretStore

    @Test fun `secrets are isolated by server`() = runTest {
        val store = store()
        store.put("one", "password", "alpha")
        store.put("two", "password", "beta")
        assertEquals("alpha", store.get("one", "password"))
        assertEquals("beta", store.get("two", "password"))
        store.removeServer("one")
        assertNull(store.get("one", "password"))
        assertEquals("beta", store.get("two", "password"))
    }
}
```

- [ ] **Step 2: Run the contract test and verify failure**

Run `./gradlew :core:security:testDebugUnitTest`.

Expected: FAIL until a test implementation is supplied.

- [ ] **Step 3: Implement Android encryption**

Use an AES-256-GCM key stored under `AndroidKeyStore` alias `hermroid.secrets.v1`. Store `version || iv || ciphertext` as Base64 in private SharedPreferences. Bind `serverId:name` as GCM associated data. Hash the server ID with SHA-256 for preference keys so raw server URLs do not appear in storage. Serialize all reads and writes with a `Mutex` and execute cryptography on `Dispatchers.IO`.

Required failure behavior:

```kotlin
sealed class SecretStoreException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Unavailable(cause: Throwable) : SecretStoreException("Secure storage is unavailable.", cause)
    class Corrupted(cause: Throwable) : SecretStoreException("Stored credentials could not be decrypted.", cause)
}
```

- [ ] **Step 4: Add instrumented round-trip and deletion tests**

Run `./gradlew :core:security:connectedDebugAndroidTest` on API 26 and latest API emulators.

Expected: encrypted round-trip, server isolation, overwrite, and removal tests PASS.

- [ ] **Step 5: Commit**

```bash
git add core/security
git commit -m "feat(security): add server-scoped encrypted secrets"
```

### Task 4: Implement the authentication API client

**Files:**
- Create: `core/network/src/main/kotlin/com/princejain/hermroid/network/AuthApi.kt`
- Create: `core/network/src/main/kotlin/com/princejain/hermroid/network/OkHttpAuthApi.kt`
- Create: `core/network/src/main/kotlin/com/princejain/hermroid/network/ApiFailure.kt`
- Create: `core/network/src/main/kotlin/com/princejain/hermroid/network/OriginHeaderInterceptor.kt`
- Test: `core/network/src/test/kotlin/com/princejain/hermroid/network/OkHttpAuthApiTest.kt`

- [ ] **Step 1: Write MockWebServer contract tests**

Cover these exact requests and outcomes:

```kotlin
@Test fun `health requests health endpoint`() = runTest {
    server.enqueue(MockResponse().setBody("{\"status\":\"ok\"}"))
    assertEquals(Health("ok"), api().health())
    assertEquals("/health", server.takeRequest().path)
}

@Test fun `login posts password and retains cookie`() = runTest {
    server.enqueue(MockResponse().setHeader("Set-Cookie", "session=test; HttpOnly").setBody("{\"ok\":true}"))
    assertEquals(true, api().login("secret").ok)
    val request = server.takeRequest()
    assertEquals("POST", request.method)
    assertEquals("{\"password\":\"secret\"}", request.body.readUtf8())
}

@Test fun `unauthorized maps to typed failure`() = runTest {
    server.enqueue(MockResponse().setResponseCode(401))
    assertFailsWith<ApiFailure.Unauthorized> { api().authStatus() }
}
```

Also assert that requests omit `Origin` and `Referer`, built-in headers override custom collisions, and cross-origin redirects receive no custom secret headers.

- [ ] **Step 2: Run and verify failure**

Run `./gradlew :core:network:testDebugUnitTest --tests '*OkHttpAuthApiTest'`.

Expected: FAIL because the API types do not exist.

- [ ] **Step 3: Implement typed models and requests**

```kotlin
interface AuthApi {
    suspend fun health(): Health
    suspend fun authStatus(): AuthStatus
    suspend fun login(password: String): LoginResult
    suspend fun logout(): LoginResult
}

@JsonClass(generateAdapter = true) data class Health(val status: String?)
@JsonClass(generateAdapter = true) data class AuthStatus(
    @Json(name = "auth_enabled") val authEnabled: Boolean?,
    @Json(name = "password_auth_enabled") val passwordAuthEnabled: Boolean?,
)
@JsonClass(generateAdapter = true) data class LoginResult(val ok: Boolean?)
@JsonClass(generateAdapter = true) internal data class LoginBody(val password: String)
```

`OkHttpAuthApi` builds endpoint URLs relative to `ServerAddress.baseUrl`, uses a server-scoped `CookieJar`, treats only 2xx as success, maps 401 separately, limits diagnostic bodies to 4 KiB, and never includes response bodies in release logs.

- [ ] **Step 4: Run network tests**

Run `./gradlew :core:network:testDebugUnitTest`.

Expected: PASS with no leaked password in captured logs.

- [ ] **Step 5: Commit**

```bash
git add core/network
git commit -m "feat(network): add secure Hermes authentication client"
```

### Task 5: Create the Hermroid design foundation

**Files:**
- Create: `core/design/src/main/kotlin/com/princejain/hermroid/design/HermroidTheme.kt`
- Create: `core/design/src/main/kotlin/com/princejain/hermroid/design/HermroidTokens.kt`
- Create: `core/design/src/main/kotlin/com/princejain/hermroid/design/GlassSurface.kt`
- Create: `core/design/src/main/kotlin/com/princejain/hermroid/design/HermroidWordmark.kt`
- Create: `core/design/src/main/res/font/inter_*.ttf`
- Create: `core/design/src/main/res/font/jetbrains_mono_*.ttf`
- Create: `core/design/src/main/res/raw/font_licenses.txt`
- Test: `core/design/src/androidTest/kotlin/com/princejain/hermroid/design/ThemeScreenshotTest.kt`

- [ ] **Step 1: Define tokens before components**

```kotlin
object HermroidTokens {
    val space = listOf(0.dp, 4.dp, 8.dp, 12.dp, 16.dp, 20.dp, 24.dp, 32.dp)
    val cornerSmall = 10.dp
    val cornerMedium = 14.dp
    val cornerComposer = 22.dp
    val touchTarget = 48.dp
    const val motionFast = 150
    const val motionStandard = 240
    const val motionEmphasized = 360
}
```

Define semantic light and dark colors for canvas, surface, elevated surface, ink, muted ink, outline, gold accent, success, warning, and error. Verify normal text pairs at 4.5:1 and large text at 3:1.

- [ ] **Step 2: Implement the theme and purposeful glass surface**

`GlassSurface` uses blur only on API 31+ and only for composer or modal separation. Lower APIs use an 94 percent opaque surface. It accepts `contentDescription`, shape, and content; it does not nest glass surfaces.

- [ ] **Step 3: Add locally licensed fonts and Lucide vectors**

Bundle Inter and JetBrains Mono OFL files. Add Lucide icons needed for onboarding—arrow, server, shield, eye, eye-off, check, warning, and settings—as Android vectors. Record each source revision and license under `core/design/src/main/res/raw/third_party_notices.txt`.

- [ ] **Step 4: Add screenshot and accessibility checks**

Capture light/dark previews at 360x800 and 1280x800. Assert 48 dp touch bounds and content descriptions for icon-only controls.

Run `./gradlew :core:design:connectedDebugAndroidTest`.

- [ ] **Step 5: Commit**

```bash
git add core/design
git commit -m "feat(design): establish Hermroid visual system"
```

### Task 6: Implement onboarding state and connection orchestration

**Files:**
- Create: `feature/onboarding/src/main/kotlin/com/princejain/hermroid/onboarding/OnboardingState.kt`
- Create: `feature/onboarding/src/main/kotlin/com/princejain/hermroid/onboarding/OnboardingViewModel.kt`
- Create: `feature/onboarding/src/main/kotlin/com/princejain/hermroid/onboarding/ConnectServer.kt`
- Test: `feature/onboarding/src/test/kotlin/com/princejain/hermroid/onboarding/OnboardingViewModelTest.kt`

- [ ] **Step 1: Write state-machine tests**

Test these transitions:

```text
editing -> testing -> passwordRequired -> connecting -> connected
editing -> testing -> connected (authentication disabled)
testing -> passkeyUnsupported
testing -> failed -> editing with draft preserved
connecting -> unauthorized -> passwordRequired with password cleared
```

The test fake records its address and headers. Assert a failed probe never calls `SecretStore.put` and successful login stores the password only after the server returns `{ "ok": true }`.

- [ ] **Step 2: Run and verify failure**

Run `./gradlew :feature:onboarding:testDebugUnitTest`.

Expected: FAIL because onboarding types do not exist.

- [ ] **Step 3: Implement immutable state**

```kotlin
data class OnboardingState(
    val serverUrl: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val customHeaders: List<HeaderDraft> = emptyList(),
    val phase: Phase = Phase.Editing,
    val fieldError: String? = null,
    val connectionError: String? = null,
) {
    enum class Phase { Editing, Testing, PasswordRequired, Connecting, Connected, PasskeyUnsupported }
}
```

`ConnectServer` parses the address, probes health, requires `status == "ok"`, reads auth status, rejects explicit passkey-only configuration, logs in when required, stores secrets on success, and returns a typed `ConnectionOutcome`.

- [ ] **Step 4: Implement ViewModel actions and pass tests**

Expose `StateFlow<OnboardingState>` and methods `updateUrl`, `updatePassword`, `togglePasswordVisibility`, `addHeader`, `removeHeader`, `testConnection`, and `connect`. Reject concurrent test/connect calls.

Run `./gradlew :feature:onboarding:testDebugUnitTest`.

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add feature/onboarding
git commit -m "feat(onboarding): orchestrate secure server connection"
```

### Task 7: Build the polished onboarding interface and app shell

**Files:**
- Create: `feature/onboarding/src/main/kotlin/com/princejain/hermroid/onboarding/OnboardingScreen.kt`
- Create: `feature/onboarding/src/main/kotlin/com/princejain/hermroid/onboarding/ServerForm.kt`
- Create: `feature/onboarding/src/main/kotlin/com/princejain/hermroid/onboarding/ConnectionDiagnostics.kt`
- Create: `app/src/main/kotlin/com/princejain/hermroid/HermroidApp.kt`
- Create: `app/src/main/kotlin/com/princejain/hermroid/MainActivity.kt`
- Create: `app/src/main/kotlin/com/princejain/hermroid/AppNavigation.kt`
- Create: `app/src/main/kotlin/com/princejain/hermroid/di/AppModule.kt`
- Test: `feature/onboarding/src/androidTest/kotlin/com/princejain/hermroid/onboarding/OnboardingScreenTest.kt`

- [ ] **Step 1: Write UI behavior tests**

Verify URL labeling, password reveal, custom-header disclosure, loading lockout, inline field errors, connection diagnostics, retry, keyboard next/done actions, 200 percent font scaling, and successful navigation. Use semantics tags only where a user-visible label cannot select the node.

- [ ] **Step 2: Run and verify failure**

Run `./gradlew :feature:onboarding:connectedDebugAndroidTest`.

Expected: FAIL because the screen does not exist.

- [ ] **Step 3: Build the adaptive screen**

Compact width uses one scrollable column with the Hermroid wordmark, concise explanation, labeled fields, advanced-header disclosure, and one primary action. Expanded width uses a two-pane composition: product explanation and privacy statement on the left, connection form on the right. Keep the form width at or below 560 dp.

Do not use nested cards, decorative gradients, placeholder-only labels, or icon-only errors. Every asynchronous action displays progress within 100 ms and remains cancellable through navigation.

- [ ] **Step 4: Wire Hilt and root navigation**

`HermroidApp` owns application-level injection. `MainActivity` renders `HermroidTheme` edge-to-edge. `AppNavigation` starts at onboarding until a validated active server exists, then opens an authenticated shell that displays server identity and a disabled "Sessions arrive in the next milestone" navigation item only in debug builds. Release builds remain on a connection-success view with disconnect and diagnostics actions until the sessions plan lands.

- [ ] **Step 5: Run full milestone verification**

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew connectedDebugAndroidTest
```

Expected: all tests PASS, lint has no errors, and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 6: Commit**

```bash
git add app feature/onboarding
git commit -m "feat: ship Hermroid connection onboarding"
```

### Task 8: Document, audit, and publish the milestone branch

**Files:**
- Create: `README.md`
- Create: `LICENSE`
- Create: `NOTICE`
- Create: `SECURITY.md`
- Create: `CONTRIBUTING.md`
- Create: `docs/architecture.md`
- Create: `docs/compatibility.md`
- Modify: `docs/superpowers/plans/2026-07-05-hermroid-foundation-onboarding.md`

- [ ] **Step 1: Add legal and compatibility documents**

Use the MIT license with the repository owner's copyright. `NOTICE` identifies Hermex as an MIT-licensed behavior and design reference and lists bundled fonts and Lucide. `compatibility.md` records both pinned SHAs from the design.

- [ ] **Step 2: Write contributor instructions**

Document JDK 17, Android SDK 35, emulator API levels, wrapper commands, module ownership, test commands, fixture-redaction rules, and the prohibition on invented endpoints.

- [ ] **Step 3: Run repository checks**

```bash
./gradlew clean testDebugUnitTest lintDebug assembleDebug --scan
git diff --check
git status --short
```

Expected: Gradle PASS; no whitespace errors; only intended plan checkbox updates remain.

- [ ] **Step 4: Perform manual onboarding smoke test**

Test one HTTPS password server, one no-auth development server, one invalid password, one unreachable host, one Tailscale HTTP address, rotation, dark mode, large text, and process recreation after successful connection. Record results in the commit body without storing credentials or hostnames.

- [ ] **Step 5: Mark completed checkboxes and commit**

```bash
git add README.md LICENSE NOTICE SECURITY.md CONTRIBUTING.md docs
git commit -m "docs: document Hermroid foundation"
```

- [ ] **Step 6: Push only after local verification**

```bash
git push -u origin main
```

Expected: GitHub Actions passes on `main`; the repository contains source and documentation but no secrets, local properties, signing keys, or generated APKs outside a tagged release.

## Milestone completion criteria

- A fresh clone builds with `./gradlew assembleDebug`.
- A user can connect to authenticated or unauthenticated pinned `hermes-webui` servers.
- Public HTTP is rejected; localhost and Tailscale policy is tested.
- Secrets remain server-scoped and encrypted.
- Cross-origin requests cannot receive custom authentication headers.
- Onboarding passes phone/tablet, light/dark, large-text, and accessibility checks.
- CI, legal notices, security policy, and contributor instructions exist.
- The next plan can implement Room-backed sessions without changing these public interfaces.

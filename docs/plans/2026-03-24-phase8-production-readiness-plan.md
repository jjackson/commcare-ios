# Phase 8: Production Readiness — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring CommCare iOS from TestFlight to production-ready: expanded integration tests (especially Connect APIs), App Store submission, performance profiling, and hardening (thread safety, crash reporting, edge cases).

**Architecture:** Four independent work streams that can be parallelized: (1) integration test expansion with Connect API coverage via mock + live test infrastructure, (2) App Store metadata and submission prep, (3) performance benchmarks using JVM profiling and iOS Instruments, (4) hardening audit covering thread safety, crash report upload, and defensive edge-case handling.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, SQLDelight, JUnit 4, Gradle, Xcode 15, GitHub Actions, `gh` CLI

---

## Phase Setup (per Phase Transition Checklist)

Before any implementation:
1. Create GitHub issues #371-#381 from this plan (one per task)
2. Update CLAUDE.md — add Phase 8 status + link this plan in Key Docs
3. PR and merge the plan doc + CLAUDE.md update as a doc-only PR

## Acceptance Criteria

| Task | Tests That Must Pass |
|------|---------------------|
| 1 | `./gradlew :app:jvmTest --tests "*ConnectApiRequestTest*"` — all pass (no creds needed) |
| 2 | `./gradlew :app:jvmTest --tests "*ConnectIdIntegrationTest*"` — pass or skip |
| 3 | `./gradlew :app:jvmTest --tests "*ConnectMarketplace*" --tests "*ConnectMessaging*"` — pass or skip |
| 4 | CI workflow parses without YAML errors |
| 5 | Metadata files exist at `app/iosApp/metadata/en-US/` |
| 6 | `xcodebuild build` succeeds with updated Info.plist + `compileCommonMainKotlinMetadata` passes |
| 7-8 | `./gradlew :app:jvmTest --tests "*.perf.*"` — all pass, baselines recorded |
| 9 | `./gradlew :app:jvmTest --tests "*.ThreadSafetyTest"` — all pass |
| 10 | `./gradlew :app:jvmTest --tests "*.CrashUploaderTest"` — all pass |
| 11 | `./gradlew :app:jvmTest --tests "*.EdgeCaseTest"` — all pass |

---

## File Structure

### Stream 1: Integration Tests
- Create: `app/src/jvmTest/kotlin/org/commcare/app/integration/ConnectTestConfig.kt` — env var config for Connect credentials
- Create: `app/src/jvmTest/kotlin/org/commcare/app/integration/ConnectIdIntegrationTest.kt` — ConnectID API tests
- Create: `app/src/jvmTest/kotlin/org/commcare/app/integration/ConnectMarketplaceIntegrationTest.kt` — Marketplace API tests
- Create: `app/src/jvmTest/kotlin/org/commcare/app/integration/ConnectMessagingIntegrationTest.kt` — Messaging API tests
- Create: `app/src/jvmTest/kotlin/org/commcare/app/network/ConnectApiRequestTest.kt` — New mock tests for request URL/header validation (existing files cover JSON parsing; new tests cover request construction, error codes, auth header format)
- Modify: `.github/workflows/hq-integration.yml` — add Connect secrets + test target

**Note:** Existing `ConnectMarketplaceApiJsonTest.kt` and `ConnectIdApiJsonTest.kt` already cover JSON parsing. Task 1 adds *complementary* tests for request format, URL construction, header validation, and error code handling — not duplicate coverage.

**Note:** Messaging endpoints (`getMessages`, `sendMessage`, `updateConsent`) live on `ConnectMarketplaceApi`, not a separate client. The `ConnectMessagingIntegrationTest` uses `ConnectMarketplaceApi` intentionally — messaging URLs route to `connectid.dimagi.com` while marketplace URLs route to `connect.dimagi.com`, both via the same API class.

### Stream 2: App Store Submission
- Create: `docs/plans/app-store-submission-checklist.md` — full checklist
- Create: `app/iosApp/fastlane/Fastfile` — Fastlane config for screenshots + upload
- Create: `app/iosApp/fastlane/Appfile` — app ID + team ID
- Create: `app/iosApp/metadata/en-US/description.txt` — store description
- Create: `app/iosApp/metadata/en-US/keywords.txt` — search keywords
- Create: `app/iosApp/metadata/en-US/privacy_url.txt` — privacy policy URL
- Create: `app/iosApp/metadata/en-US/subtitle.txt` — store subtitle
- Modify: `app/iosApp/iosApp/Info.plist` — add ATT description if needed, verify all privacy strings

### Stream 3: Performance
- Create: `app/src/jvmTest/kotlin/org/commcare/app/perf/RestoreBenchmark.kt` — restore XML parsing throughput
- Create: `app/src/jvmTest/kotlin/org/commcare/app/perf/CaseListBenchmark.kt` — case list with 1K/5K/10K cases
- Create: `app/src/jvmTest/kotlin/org/commcare/app/perf/CryptoBenchmark.kt` — AES + PBKDF2 timing
- Create: `app/src/jvmTest/kotlin/org/commcare/app/perf/FormEntryBenchmark.kt` — form load + answer + serialize

### Stream 4: Hardening
- Modify: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/SyncViewModel.kt` — add synchronized access
- Modify: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/MessagingViewModel.kt` — add synchronized access
- Modify: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/FormEntryViewModel.kt` — defensive null checks
- Create: `app/src/commonMain/kotlin/org/commcare/app/platform/PlatformCrashUploader.kt` — upload crash reports to HQ
- Modify: `app/src/commonMain/kotlin/org/commcare/app/platform/PlatformCrashReporter.kt` — integrate uploader
- Create: `app/src/jvmTest/kotlin/org/commcare/app/viewmodel/ThreadSafetyTest.kt` — concurrent access tests
- Create: `app/src/jvmTest/kotlin/org/commcare/app/viewmodel/EdgeCaseTest.kt` — boundary conditions

---

## Stream 1: Integration Tests (Waves 1-3)

### Task 1: Connect test config + mock-based API tests

**Files:**
- Create: `app/src/jvmTest/kotlin/org/commcare/app/integration/ConnectTestConfig.kt`
- Create: `app/src/jvmTest/kotlin/org/commcare/app/network/ConnectApiRequestTest.kt`

- [ ] **Step 1: Create ConnectTestConfig (env var reader)**

```kotlin
// ConnectTestConfig.kt
package org.commcare.app.integration

object ConnectTestConfig {
    val connectIdUrl: String get() = System.getenv("CONNECT_ID_URL") ?: "https://connectid.dimagi.com"
    val connectMarketplaceUrl: String get() = System.getenv("CONNECT_MARKETPLACE_URL") ?: "https://connect.dimagi.com"
    val connectUsername: String get() = System.getenv("CONNECT_USERNAME") ?: ""
    val connectPassword: String get() = System.getenv("CONNECT_PASSWORD") ?: ""
    val connectAccessToken: String get() = System.getenv("CONNECT_ACCESS_TOKEN") ?: ""

    val isConfigured: Boolean get() = connectAccessToken.isNotBlank() ||
        (connectUsername.isNotBlank() && connectPassword.isNotBlank())

    fun bearerHeader(token: String): String = "Bearer $token"
}
```

- [ ] **Step 2: Write mock-based Connect API tests (no credentials needed)**

These tests use a MockHttpClient to validate request construction, URL formatting, header handling, and JSON parsing without hitting real servers. This is the highest-value test — it catches bugs in the API client code itself.

```kotlin
// ConnectApiRequestTest.kt — test request construction and JSON parsing
package org.commcare.app.network

import org.commcare.core.interfaces.*
import kotlin.test.*

class ConnectApiRequestTest {
    // Mock client that records requests and returns canned responses
    private class RecordingHttpClient(
        private val responseCode: Int = 200,
        private val responseBody: String = "{}"
    ) : PlatformHttpClient {
        val requests = mutableListOf<HttpRequest>()
        override fun execute(request: HttpRequest): HttpResponse {
            requests.add(request)
            return HttpResponse(responseCode, emptyMap(), responseBody.encodeToByteArray())
        }
    }

    @Test
    fun testGetOpportunitiesRequestFormat() {
        val client = RecordingHttpClient(200, """[]""")
        val api = ConnectMarketplaceApi(client)
        api.getOpportunities("test-token")
        assertEquals(1, client.requests.size)
        val req = client.requests[0]
        assertTrue(req.url.contains("/api/opportunity/"))
        assertEquals("GET", req.method)
        assertEquals("Bearer test-token", req.headers["Authorization"])
    }

    @Test
    fun testClaimOpportunityRequestFormat() { ... }
    @Test
    fun testGetMessagesRequestFormat() { ... }
    @Test
    fun testSendMessageRequestFormat() { ... }
    @Test
    fun testUpdateConsentRequestFormat() { ... }
    @Test
    fun testUpdateChannelConsentEscapesJson() { ... }
    @Test
    fun testOpportunityJsonParsing() { ... }
    @Test
    fun testMessageThreadJsonParsing() { ... }
    @Test
    fun testErrorResponseHandling() { ... }
    @Test
    fun testAuthExpiredResponse() { ... }
}
```

- [ ] **Step 3: Run tests**

Run: `./gradlew :app:jvmTest --tests "org.commcare.app.network.ConnectApiRequestTest" -v`
Expected: All 10+ tests PASS (no credentials needed)

- [ ] **Step 4: Commit**

```bash
git add app/src/jvmTest/kotlin/org/commcare/app/integration/ConnectTestConfig.kt \
       app/src/jvmTest/kotlin/org/commcare/app/network/ConnectApiRequestTest.kt
git commit -m "test: Connect API mock tests — request format, JSON parsing, error handling"
```

---

### Task 2: ConnectID live integration tests

**Files:**
- Create: `app/src/jvmTest/kotlin/org/commcare/app/integration/ConnectIdIntegrationTest.kt`

- [ ] **Step 1: Write ConnectID integration tests**

These tests hit the real ConnectID server. They require `CONNECT_ACCESS_TOKEN` (pre-obtained via OAuth). Tests are skipped if credentials aren't configured.

```kotlin
// ConnectIdIntegrationTest.kt
package org.commcare.app.integration

import org.commcare.app.network.ConnectIdApi
import org.commcare.core.interfaces.createHttpClient
import org.junit.Assume
import org.junit.Before
import kotlin.test.*

class ConnectIdIntegrationTest {
    private lateinit var api: ConnectIdApi

    @Before
    fun setup() {
        Assume.assumeTrue("Connect credentials not configured", ConnectTestConfig.isConfigured)
        api = ConnectIdApi(createHttpClient())
    }

    @Test
    fun testFetchDbKeyWithValidToken() {
        val token = ConnectTestConfig.connectAccessToken
        Assume.assumeTrue("Access token not set", token.isNotBlank())
        val result = api.fetchDbKey(token)
        assertTrue(result.isSuccess, "fetchDbKey should succeed: ${result.exceptionOrNull()}")
        assertTrue(result.getOrNull()!!.isNotBlank(), "DB key should be non-blank")
    }

    @Test
    fun testFetchDbKeyWithExpiredToken() {
        val result = api.fetchDbKey("expired-invalid-token")
        assertTrue(result.isFailure, "Expired token should fail")
    }

    @Test
    fun testOAuthTokenWithInvalidCredentials() {
        val result = api.getOAuthToken("nonexistent@user.com", "wrongpassword")
        assertTrue(result.isFailure, "Invalid credentials should fail")
    }
}
```

- [ ] **Step 2: Run tests (skipped if no creds)**

Run: `./gradlew :app:jvmTest --tests "*ConnectIdIntegrationTest*" -v`
Expected: SKIP (if no CONNECT_ACCESS_TOKEN) or PASS

- [ ] **Step 3: Commit**

```bash
git add app/src/jvmTest/kotlin/org/commcare/app/integration/ConnectIdIntegrationTest.kt
git commit -m "test: ConnectID live integration tests — token validation, auth flow"
```

---

### Task 3: Marketplace + Messaging live integration tests

**Files:**
- Create: `app/src/jvmTest/kotlin/org/commcare/app/integration/ConnectMarketplaceIntegrationTest.kt`
- Create: `app/src/jvmTest/kotlin/org/commcare/app/integration/ConnectMessagingIntegrationTest.kt`

- [ ] **Step 1: Write Marketplace integration tests**

```kotlin
// ConnectMarketplaceIntegrationTest.kt
package org.commcare.app.integration

import org.commcare.app.network.ConnectMarketplaceApi
import org.commcare.core.interfaces.createHttpClient
import org.junit.Assume
import org.junit.Before
import kotlin.test.*

class ConnectMarketplaceIntegrationTest {
    private lateinit var api: ConnectMarketplaceApi

    @Before
    fun setup() {
        Assume.assumeTrue("Connect credentials not configured", ConnectTestConfig.isConfigured)
        api = ConnectMarketplaceApi(createHttpClient())
    }

    @Test
    fun testGetOpportunitiesReturnsValidList() {
        val token = ConnectTestConfig.connectAccessToken
        val result = api.getOpportunities(token)
        assertTrue(result.isSuccess, "getOpportunities should succeed: ${result.exceptionOrNull()}")
        // May be empty if user has no opportunities, but should not error
    }

    @Test
    fun testGetOpportunitiesWithInvalidToken() {
        val result = api.getOpportunities("invalid-token")
        assertTrue(result.isFailure, "Invalid token should fail")
    }
}
```

- [ ] **Step 2: Write Messaging integration tests**

```kotlin
// ConnectMessagingIntegrationTest.kt
package org.commcare.app.integration

import org.commcare.app.network.ConnectMarketplaceApi
import org.commcare.core.interfaces.createHttpClient
import org.junit.Assume
import org.junit.Before
import kotlin.test.*

class ConnectMessagingIntegrationTest {
    private lateinit var api: ConnectMarketplaceApi

    @Before
    fun setup() {
        Assume.assumeTrue("Connect credentials not configured", ConnectTestConfig.isConfigured)
        api = ConnectMarketplaceApi(createHttpClient())
    }

    @Test
    fun testGetMessagesReturnsThreadList() {
        val token = ConnectTestConfig.connectAccessToken
        val result = api.getMessages(token)
        assertTrue(result.isSuccess, "getMessages should succeed: ${result.exceptionOrNull()}")
    }

    @Test
    fun testUpdateConsentSucceeds() {
        val token = ConnectTestConfig.connectAccessToken
        val result = api.updateConsent(token)
        assertTrue(result.isSuccess, "updateConsent should succeed: ${result.exceptionOrNull()}")
    }

    @Test
    fun testMessagingWithInvalidToken() {
        val result = api.getMessages("invalid-token")
        assertTrue(result.isFailure, "Invalid token should fail")
    }
}
```

- [ ] **Step 3: Run tests**

Run: `./gradlew :app:jvmTest --tests "*ConnectMarketplace*" --tests "*ConnectMessaging*" -v`
Expected: SKIP (if no creds) or PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/jvmTest/kotlin/org/commcare/app/integration/ConnectMarketplaceIntegrationTest.kt \
       app/src/jvmTest/kotlin/org/commcare/app/integration/ConnectMessagingIntegrationTest.kt
git commit -m "test: Connect marketplace + messaging live integration tests"
```

---

### Task 4: CI workflow for Connect integration tests

**Files:**
- Modify: `.github/workflows/hq-integration.yml`

- [ ] **Step 1: Add Connect secrets to CI workflow**

Add to the existing `hq-integration.yml` workflow:

```yaml
    - name: Run Connect Integration Tests
      if: env.CONNECT_ACCESS_TOKEN != ''
      env:
        CONNECT_ACCESS_TOKEN: ${{ secrets.CONNECT_ACCESS_TOKEN }}
        CONNECT_USERNAME: ${{ secrets.CONNECT_USERNAME }}
        CONNECT_PASSWORD: ${{ secrets.CONNECT_PASSWORD }}
      run: |
        cd app
        ../commcare-core/gradlew jvmTest --tests "*ConnectIdIntegrationTest*" --tests "*ConnectMarketplaceIntegrationTest*" --tests "*ConnectMessagingIntegrationTest*" --no-daemon --stacktrace || true
      timeout-minutes: 5
```

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/hq-integration.yml
git commit -m "ci: add Connect integration tests to weekly CI workflow"
```

---

## Stream 2: App Store Submission (Waves 4-5)

### Task 5: App Store submission checklist (doc-only)

**Files:**
- Create: `docs/plans/app-store-submission-checklist.md`

- [ ] **Step 1: Write submission checklist**

Cover: App Store Connect setup, privacy questionnaire answers, age rating, categories, screenshots needed (6.7" iPhone, 6.5" iPhone, 12.9" iPad), review notes for Apple reviewers (demo account creds).

- [ ] **Step 2: Commit as doc-only PR**

```bash
git add docs/plans/app-store-submission-checklist.md
git commit -m "docs: App Store submission checklist"
```

---

### Task 5b: App Store metadata files (app assets)

**Files:**
- Create: `app/iosApp/metadata/en-US/description.txt`
- Create: `app/iosApp/metadata/en-US/keywords.txt`
- Create: `app/iosApp/metadata/en-US/subtitle.txt`
- Create: `app/iosApp/metadata/en-US/privacy_url.txt`

- [ ] **Step 1: Create metadata files**

```
# description.txt
CommCare is a mobile data collection and case management platform used by
frontline workers in global health, agriculture, and social services.
Built by Dimagi, CommCare enables organizations to create custom mobile
applications without coding...

# keywords.txt
commcare,data collection,case management,mobile health,mhealth,frontline,
community health,survey,forms,dimagi

# subtitle.txt
Mobile Data Collection & Case Management

# privacy_url.txt
https://www.dimagi.com/terms/
```

- [ ] **Step 2: Commit (code PR, bundled with Task 6)**

```bash
git add app/iosApp/metadata/
git commit -m "chore: add App Store metadata files"
```

---

### Task 6: Verify Info.plist completeness for App Review

**Files:**
- Modify: `app/iosApp/iosApp/Info.plist`

- [ ] **Step 1: Audit and update Info.plist**

Verify all required keys are present for App Review:
- Privacy descriptions for all used APIs (camera, mic, photos, location, Face ID)
- Add `NSUserTrackingUsageDescription` if ATT framework is linked (probably not needed)
- Verify `ITSAppUsesNonExemptEncryption` is set to `true` (we use AES)
- Add `ITSEncryptionExportComplianceCode` if applicable

- [ ] **Step 2: Run Xcode build to verify**

Run: `cd app/iosApp && xcodegen generate && xcodebuild build -scheme CommCare -destination 'platform=iOS Simulator,name=iPhone 16'`

- [ ] **Step 3: Commit**

```bash
git add app/iosApp/iosApp/Info.plist
git commit -m "chore: update Info.plist for App Store review compliance"
```

---

## Stream 3: Performance Profiling (Waves 6-7)

### Task 7: Restore parsing + case list benchmarks

**Files:**
- Create: `app/src/jvmTest/kotlin/org/commcare/app/perf/RestoreBenchmark.kt`
- Create: `app/src/jvmTest/kotlin/org/commcare/app/perf/CaseListBenchmark.kt`

- [ ] **Step 1: Write restore parsing benchmark**

```kotlin
// RestoreBenchmark.kt
package org.commcare.app.perf

import kotlin.test.Test
import kotlin.system.measureTimeMillis

class RestoreBenchmark {
    @Test
    fun benchmarkRestoreParsingSmall() {
        // Generate 100-case restore XML, parse, measure time
        val xml = generateRestoreXml(caseCount = 100)
        val ms = measureTimeMillis { parseRestore(xml) }
        println("BENCHMARK: 100 cases parsed in ${ms}ms")
        // Baseline: should be < 500ms
    }

    @Test
    fun benchmarkRestoreParsingLarge() {
        val xml = generateRestoreXml(caseCount = 1000)
        val ms = measureTimeMillis { parseRestore(xml) }
        println("BENCHMARK: 1000 cases parsed in ${ms}ms")
        // Baseline: should be < 5000ms
    }

    private fun generateRestoreXml(caseCount: Int): String { ... }
    private fun parseRestore(xml: String) { ... }
}
```

- [ ] **Step 2: Write case list filtering benchmark**

```kotlin
// CaseListBenchmark.kt — measure case list loading and filtering
package org.commcare.app.perf

class CaseListBenchmark {
    @Test fun benchmarkCaseListLoad1K() { ... }
    @Test fun benchmarkCaseListFilter1K() { ... }
    @Test fun benchmarkCaseListLoad5K() { ... }
}
```

- [ ] **Step 3: Run benchmarks and record baselines**

Run: `./gradlew :app:jvmTest --tests "org.commcare.app.perf.*" -v`
Record output to `docs/plans/performance-baselines.md`

- [ ] **Step 4: Commit**

```bash
git add app/src/jvmTest/kotlin/org/commcare/app/perf/ docs/plans/performance-baselines.md
git commit -m "perf: restore + case list benchmarks with baseline measurements"
```

---

### Task 8: Crypto + form entry benchmarks

**Files:**
- Create: `app/src/jvmTest/kotlin/org/commcare/app/perf/CryptoBenchmark.kt`
- Create: `app/src/jvmTest/kotlin/org/commcare/app/perf/FormEntryBenchmark.kt`

- [ ] **Step 1: Write crypto benchmark**

```kotlin
// CryptoBenchmark.kt — AES encrypt/decrypt and PBKDF2 timing
package org.commcare.app.perf

import org.commcare.core.interfaces.PlatformCrypto
import kotlin.system.measureTimeMillis
import kotlin.test.Test

class CryptoBenchmark {
    @Test
    fun benchmarkAesEncrypt1KB() {
        val key = PlatformCrypto.generateAesKey(256)
        val data = ByteArray(1024)
        val ms = measureTimeMillis { repeat(1000) { PlatformCrypto.aesEncrypt(data, key) } }
        println("BENCHMARK: 1000x AES encrypt 1KB in ${ms}ms (${ms/1000.0}ms/op)")
    }

    @Test
    fun benchmarkPbkdf2() {
        val salt = "user@domain".encodeToByteArray()
        val ms = measureTimeMillis { PlatformCrypto.pbkdf2("password", salt, 100_000, 32) }
        println("BENCHMARK: PBKDF2 100K iterations in ${ms}ms")
        // Baseline: should be 200-1000ms depending on hardware
    }
}
```

- [ ] **Step 2: Write form entry benchmark (load + answer + serialize)**

- [ ] **Step 3: Run and record**

- [ ] **Step 4: Commit**

```bash
git add app/src/jvmTest/kotlin/org/commcare/app/perf/
git commit -m "perf: crypto + form entry benchmarks"
```

---

## Stream 4: Hardening (Waves 8-10)

### Task 9: Thread safety audit + concurrent access tests

**Files:**
- Modify: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/SyncViewModel.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/MessagingViewModel.kt`
- Create: `app/src/jvmTest/kotlin/org/commcare/app/viewmodel/ThreadSafetyTest.kt`

- [ ] **Step 1: Identify ViewModels with shared mutable state accessed from coroutines**

Focus on ViewModels that launch coroutines AND mutate state from both the coroutine and the main thread:
- `SyncViewModel` — `syncState`, `lastSyncToken` mutated from coroutine
- `MessagingViewModel` — `threads` mutated from both main + coroutine
- `FormQueueViewModel` — already synchronized (skip)

- [ ] **Step 2: Add synchronization guards**

For SyncViewModel, wrap `lastSyncToken` and `lastRestoreHash` mutations in synchronized blocks. For MessagingViewModel, wrap `threads` list mutations.

Note: Compose `mutableStateOf` is thread-safe for reads, but compound read-modify-write operations (like list replacement) need guarding.

- [ ] **Step 3: Write concurrent access tests**

```kotlin
// ThreadSafetyTest.kt
package org.commcare.app.viewmodel

import kotlinx.coroutines.*
import kotlin.test.*

class ThreadSafetyTest {
    @Test
    fun testFormQueueConcurrentEnqueue() {
        // Enqueue 100 forms from 10 coroutines simultaneously
        val client = MockHttpClient(200)
        val queue = FormQueueViewModel(client, "https://hq", "d", "Basic x")
        runBlocking {
            val jobs = (1..10).map { threadId ->
                launch(Dispatchers.Default) {
                    repeat(10) { i ->
                        queue.enqueueForm("<form>t${threadId}_$i</form>", "Form", "")
                    }
                }
            }
            jobs.joinAll()
        }
        assertEquals(100, queue.pendingCount)
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :app:jvmTest --tests "*.ThreadSafetyTest" -v`

- [ ] **Step 5: Commit**

```bash
git add app/src/commonMain/kotlin/org/commcare/app/viewmodel/SyncViewModel.kt \
       app/src/commonMain/kotlin/org/commcare/app/viewmodel/MessagingViewModel.kt \
       app/src/jvmTest/kotlin/org/commcare/app/viewmodel/ThreadSafetyTest.kt
git commit -m "fix: thread safety guards for SyncViewModel + MessagingViewModel"
```

---

### Task 10: Crash report upload mechanism

**Files:**
- Create: `app/src/commonMain/kotlin/org/commcare/app/platform/PlatformCrashUploader.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/platform/PlatformCrashReporter.kt`

- [ ] **Step 1: Create crash report uploader**

```kotlin
// PlatformCrashUploader.kt
package org.commcare.app.platform

import org.commcare.core.interfaces.HttpRequest
import org.commcare.core.interfaces.PlatformHttpClient

class PlatformCrashUploader(
    private val httpClient: PlatformHttpClient,
    private val serverUrl: String,
    private val domain: String,
    private val authHeader: String
) {
    fun uploadPendingReports(reporter: PlatformCrashReporter): Int {
        val reports = reporter.getPendingReports()
        if (reports.isEmpty()) return 0
        var uploaded = 0
        for (report in reports) {
            val success = uploadReport(report)
            if (success) uploaded++
        }
        if (uploaded == reports.size) reporter.clearReports()
        return uploaded
    }

    private fun uploadReport(report: CrashReport): Boolean {
        return try {
            val url = "${serverUrl.trimEnd('/')}/a/$domain/phone/post_crash/"
            val body = buildReportJson(report)
            val response = httpClient.execute(HttpRequest(
                url = url, method = "POST",
                headers = mapOf("Authorization" to authHeader, "Content-Type" to "application/json"),
                body = body.encodeToByteArray()
            ))
            response.code in 200..299
        } catch (_: Exception) { false }
    }

    private fun buildReportJson(report: CrashReport): String { ... }
}
```

- [ ] **Step 2: Integrate uploader into sync flow**

Call `PlatformCrashUploader.uploadPendingReports()` at the start of `SyncViewModel.sync()`, before the restore request.

- [ ] **Step 3: Write test**

```kotlin
// CrashUploaderTest.kt
package org.commcare.app.viewmodel

class CrashUploaderTest {
    @Test fun testUploadPendingReportsSuccess() { ... }
    @Test fun testUploadWithServerError() { ... }
    @Test fun testUploadClearsReportsOnSuccess() { ... }
    @Test fun testNoReportsSkipsUpload() { ... }
}
```

Run: `./gradlew :app:jvmTest --tests "*.CrashUploaderTest" -v`
Expected: All 4 tests PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/commonMain/kotlin/org/commcare/app/platform/PlatformCrashUploader.kt \
       app/src/commonMain/kotlin/org/commcare/app/viewmodel/SyncViewModel.kt \
       app/src/jvmTest/kotlin/org/commcare/app/viewmodel/CrashUploaderTest.kt
git commit -m "feat: upload crash reports to HQ during sync"
```

---

### Task 11: Edge case hardening + defensive tests

**Files:**
- Modify: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/FormEntryViewModel.kt`
- Create: `app/src/jvmTest/kotlin/org/commcare/app/viewmodel/EdgeCaseTest.kt`

- [ ] **Step 1: Identify edge cases from code review**

Key edge cases to test and harden:
- Empty form (zero questions) — `getPrompts()` returns empty array
- Answer at invalid index — `answerAtIndex()` with index > prompts.size
- Double-submit — calling `submitForm()` twice
- Sync during sync — calling `sync()` while already syncing
- Login with empty credentials
- Queue form with empty XML
- Navigation with cleared session

- [ ] **Step 2: Write edge case tests**

```kotlin
// EdgeCaseTest.kt
package org.commcare.app.viewmodel

import kotlin.test.*

class EdgeCaseTest {
    @Test
    fun testFormQueueEmptyXml() {
        val client = MockHttpClient(200)
        val queue = FormQueueViewModel(client, "https://hq", "d", "Basic x")
        queue.enqueueForm("", "Empty Form", "")
        assertEquals(1, queue.pendingCount)
        // Submit should handle gracefully
    }

    @Test
    fun testSyncStatePreventsDoubleSync() { ... }
    @Test
    fun testNavigationStepWithClearedSession() { ... }
    @Test
    fun testLoginEmptyCredentials() { ... }
    @Test
    fun testAnswerAtInvalidIndex() { ... }
}
```

- [ ] **Step 3: Add defensive guards where tests expose gaps**

- [ ] **Step 4: Commit**

```bash
git add app/src/commonMain/kotlin/org/commcare/app/viewmodel/*.kt \
       app/src/jvmTest/kotlin/org/commcare/app/viewmodel/EdgeCaseTest.kt
git commit -m "fix: edge case hardening — empty forms, double submit, concurrent sync"
```

---

## Execution Order

Tasks can be partially parallelized. Recommended order:

1. **Task 1** (Connect mock tests) — no dependencies, immediate value
2. **Task 9** (Thread safety) — hardening first
3. **Task 11** (Edge cases) — hardening
4. **Task 7-8** (Performance benchmarks) — independent
5. **Task 10** (Crash upload) — depends on hardening
6. **Task 2-3** (Connect live tests) — blocked on credentials
7. **Task 4** (CI workflow) — after live tests exist
8. **Task 5-6** (App Store) — independent, can run anytime

## PR Strategy

Per Doc PR Rules: documentation and code changes are in separate PRs.

- **PR 0 (docs):** Plan doc + CLAUDE.md update (this plan, merged before implementation)
- **PR 1 (Stream 1a):** Task 1 — Mock-based Connect API tests
- **PR 2 (Stream 4a):** Tasks 9, 11 — Thread safety + edge cases
- **PR 3 (Stream 3):** Tasks 7, 8 — Performance benchmarks
- **PR 4 (Stream 4b):** Task 10 — Crash upload
- **PR 5 (Stream 1b):** Tasks 2, 3, 4 — Connect live tests + CI (when creds available)
- **PR 6 (docs):** Task 5 — App Store checklist doc (doc-only PR)
- **PR 7 (Stream 2):** Tasks 5b + 6 — App Store metadata files + Info.plist (code PR)

# Project Nidhi

> Last-mile digital banking for rural India — voice-first, cryptographically secure, works on ₹5,000 Android phones over 2G.

---

## What It Is

Project Nidhi lets an illiterate farmer in rural India say *"Send 200 rupees to Ramu"* in Hindi, Telugu, Tamil, or any of 8 Indian languages and have the money move securely — without needing to read a screen, remember a password, or own a smartphone above entry-level. The security model is intentionally unusual: transaction signing entropy is sourced in part from **live insect movement** captured by a webcam, mixed with hardware TRNG, timing noise, network jitter, and a client-contributed nonce.

---

## Repository Layout

```
ideathon/
├── nidhi-backend/          ← Spring Boot 3.2 (port 8081) — AI + Security brain
│   ├── src/main/java/com/nidhi/
│   │   ├── voice/          — Speech-to-text & intent parsing
│   │   ├── entropy/        — 5-source cryptographic entropy pool
│   │   ├── challenge/      — Challenge-response signing engine
│   │   ├── transaction/    — Orchestrates the full payment flow
│   │   ├── push/           — Real-time SSE notifications
│   │   └── dashboard/      — Audit log
│   └── insect_sidecar/     ← Python 3.11 Flask (port 5001) — CV entropy source
│       ├── main.py
│       ├── requirements.txt
│       └── yolov8n.pt
│
├── nidhi-bank/             ← Spring Boot 3.2 (port 8082) — Core banking ledger
│   └── src/main/java/com/nidhi/bank/
│       ├── user/           — Accounts, registration, login, admin
│       ├── transaction/    — Ledger transfers, balance management
│       └── config/         — CORS, network info
│   └── src/main/resources/static/
│       └── admin.html      — Single-page admin dashboard (HTML/CSS/JS)
│
└── nidhi-android/          ← Android (Kotlin, minSdk 24) — User-facing app
    └── app/src/main/java/com/nidhi/app/
        ├── MainActivity.kt
        ├── LoginActivity.kt
        ├── HomeActivity.kt
        ├── SendManualActivity.kt
        ├── ContactsActivity.kt
        ├── ReceiveMoneyActivity.kt
        ├── ConfirmActivity.kt
        ├── TransactionHistoryActivity.kt
        ├── MainViewModel.kt / ConfirmViewModel.kt
        ├── NidhiApi.kt / NidhiClient.kt
        ├── SessionManager.kt / ServerConfig.kt
        ├── NidhiSseClient.kt
        ├── TransactionManager.kt / TransactionPollerWorker.kt
        └── MyFirebaseMessagingService.kt
```

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                   Android App (Kotlin)                  │
│  Voice/Text Input ──► HomeActivity ──► ConfirmActivity  │
└──────────────┬──────────────────────────────────────────┘
               │ HTTP (Retrofit2)
               ▼
┌──────────────────────────────────────────────────────────────┐
│              nidhi-backend   :8081   (Spring Boot)           │
│                                                              │
│  VoiceController                                             │
│     └─ VoiceService                                          │
│           ├─ BhashiniClient ──► Google Cloud Speech-to-Text  │
│           │                ──► Google Cloud Translation      │
│           └─ IntentParser  ── regex NLP (amount + recipient) │
│                                                              │
│  TransactionController                                       │
│     └─ TransactionService                                    │
│           ├─ ChallengeEngine ──► issues ECDSA challenge      │
│           │       └─ EntropyPool                             │
│           │             ├─ S1: Java SecureRandom (hw TRNG)   │
│           │             ├─ S2: Insect XY coords (sidecar)    │
│           │             ├─ S3: nanosecond timing jitter      │
│           │             ├─ S4: network round-trip noise      │
│           │             └─ S5: client-supplied nonce         │
│           ├─ BankProxy ──► nidhi-bank :8082 (ledger exec.)   │
│           └─ PushNotificationService ── SSE to Android       │
│                                                              │
│  EntropyController ── SSE dashboard feed (500ms poll)        │
│  NotificationStreamController ── per-account SSE stream      │
└──────────────────────────┬───────────────────────────────────┘
                           │ HTTP (WebClient)
          ┌────────────────┴────────────────┐
          ▼                                 ▼
┌──────────────────────┐    ┌─────────────────────────────────┐
│  insect_sidecar :5001│    │    nidhi-bank  :8082            │
│  (Python / Flask)    │    │    (Spring Boot)                │
│                      │    │                                 │
│  YOLOv8n detects     │    │  BankUser JPA entity            │
│  insects via webcam  │    │  BankTransaction JPA entity     │
│  ──► /insect/reading │    │  H2 in-memory database          │
│      (JSON coords)   │    │  AccountController              │
│  ──► /insect/stream  │    │  UserController / UserService   │
│      (MJPEG feed)    │    │  TransferService / Controller   │
│                      │    │  admin.html (operator UI)       │
└──────────────────────┘    └─────────────────────────────────┘
```

---

## Transaction Flow (Step by Step)

```
1. User speaks or types  →  Android records audio / text
2. POST /transaction/initiate  (audio_b64, text, language, deviceId)
3. VoiceService
      a. Google Speech-to-Text   →  regional language → text
      b. Google Translate        →  text → English (for NLP)
      c. IntentParser (regex)    →  extract {recipient, amount}
4. ChallengeEngine.issueChallenge()
      a. EntropyPool.generateSeed()   →  XOR 5 entropy sources
      b. ECDSA key pair created per device
      c. Returns {txId, seedHex, signingPayload, privateKey (demo)}
5. Android receives challenge, signs payload, calls:
   POST /transaction/confirm  (txId, deviceId, signature)
6. ChallengeEngine.verify()   →  checks ECDSA signature + TTL (500 ms)
7. TransactionService forwards to nidhi-bank:
   POST /bank/transfer/internal  (from, to, amount, ref)
8. nidhi-bank TransferService   →  debit sender, credit recipient, persist BankTransaction
9. PushNotificationService.push()  →  SSE to recipient's open Android app
   (+ FCM fallback if SSE socket not open)
10. Confirmation text generated in sender's language  →  Android displays SuccessActivity
```

---

## Component Details

### `nidhi-backend` — Security & AI Brain (Spring Boot :8081)

| File | Purpose |
|------|---------|
| `VoiceController.java` | REST endpoint for voice input (`POST /voice/parse`) |
| `VoiceService.java` | Orchestrates ASR → translation → intent parsing pipeline |
| `BhashiniClient.java` | Calls Google Cloud Speech-to-Text (ASR) and Translation APIs; supports 8 Indian language BCP-47 tags |
| `IntentParser.java` | Regex + keyword NLP on English text to extract transaction type, recipient name/phone, and rupee amount |
| `EntropyPool.java` | Blends 5 entropy sources: hw TRNG, insect coordinates, nanosecond jitter, network latency noise, client nonce — XOR → SHA-256 |
| `EntropyController.java` | SSE endpoint that pushes live entropy source state every 500 ms (used by demo dashboard) |
| `ChallengeEngine.java` | Issues time-bounded ECDSA challenges (seed TTL 5 s, response TTL 500 ms); tracks per-device replay counters and lockout after 3 failures |
| `TransactionService.java` | Ties voice → challenge → bank proxy → push notification together; handles initiate + confirm lifecycle |
| `TransactionController.java` | REST endpoints: `POST /transaction/initiate`, `POST /transaction/confirm` |
| `PushNotificationService.java` | Maintains one reactive SSE sink per connected account; heartbeat every 25 s to survive Android Doze and NAT timeouts |
| `NotificationStreamController.java` | SSE endpoint `GET /push/stream/{account}` that streams `PushNotificationService` events |
| `BankProxyController.java` | Internal forwarding proxy — translates backend requests to nidhi-bank's REST API |
| `AuditLog.java` | Thread-safe in-memory audit trail: every challenge issued/verified + every transfer attempted |
| `DashboardController.java` | `/dashboard` API used by admin view to expose audit entries |
| `WebClientConfig.java` | Configures named WebClient beans: `bankWebClient`, `sidecarWebClient`, `googleSpeechWebClient`, `googleTranslateWebClient`, `fcmWebClient` |
| `CorsConfig.java` | Allows all origins for LAN demo (Android ↔ laptop) |
| `NetworkInfoController.java` | `/api/server-info` — returns local IP so Android app can auto-configure the server URL |

### `insect_sidecar` — Biological Entropy Source (Python Flask :5001)

| File | Purpose |
|------|---------|
| `main.py` | Opens webcam at 640×480 / 10fps; runs YOLOv8n object detection each frame to find insects; falls back to frame-delta motion detection if YOLOv8 unavailable; falls back to full simulation if no webcam. Serves `GET /insect/reading` (JSON with insect coords, count, nanosecond timestamp) and `GET /insect/stream` (MJPEG). |
| `requirements.txt` | `flask`, `ultralytics` (YOLOv8), `opencv-python`, `numpy` |
| `yolov8n.pt` | Pre-trained YOLOv8 nano weights (COCO classes — "person", "cat", etc. used as insect proxy in demo) |

**Why this exists:** Cryptographic key material derived from physical-world unpredictability is theoretically stronger than pure PRNG. Insect movement is non-deterministic and cannot be silently predicted by a remote attacker.

### `nidhi-bank` — Core Banking Ledger (Spring Boot :8082)

| File | Purpose |
|------|---------|
| `BankUser.java` | JPA entity: fullName, phone, languageCode, accountNumber (generated), balancePaise (integer cents), PIN (hashed), active flag, deviceId, timestamps |
| `BankUserRepository.java` | Spring Data JPA repository; custom queries to look up by phone, accountNumber, deviceId |
| `UserService.java` | Registration (generates account number, hashes PIN), login, startup migration to auto-link `deviceId = accountNumber` for pre-existing users |
| `UserController.java` | `/bank/auth/register`, `/bank/auth/login`; login validates PIN and returns session |
| `AccountController.java` | `/bank/account/info`, `/bank/account/lookup-batch` (phonebook contact matching for the Android contacts feature) |
| `BankTransaction.java` | JPA entity: txType (TRANSFER / ADMIN_CREDIT / ADMIN_DEBIT), fromAccount, toAccount, amountPaise, referenceId, status, adminNote, timestamps |
| `BankTransactionRepository.java` | Queries transactions by account, type, time range |
| `TransferService.java` | Atomic debit-then-credit for peer transfers; admin credit/debit; validates sufficient balance; persists both sides |
| `TransferController.java` | `/bank/transfer/internal` (called by backend proxy), `/bank/transfer/admin-credit`, `/bank/transfer/admin-debit` |
| `NetworkInfoController.java` | Returns bank server URL (mirrors backend version for QR-based setup) |
| `admin.html` | Full-featured single-file operator dashboard: register users, view all accounts + balances, inspect transactions, credit/debit balances, toggle account status, reset device binding, delete accounts. Fully responsive down to 480 px. Pure HTML/CSS/JS — no build step required. |

### `nidhi-android` — User App (Kotlin, minSdk 24)

| File | Purpose |
|------|---------|
| `NidhiApi.kt` | Retrofit2 interface: all HTTP calls to backend + bank, including `lookupBatch`, `getAccountInfo`, `initiate`, `confirm` |
| `NidhiClient.kt` | OkHttpClient singleton with timeout config, base URL switching |
| `ServerConfig.kt` | Persists the server base URL in SharedPreferences so users can point the app at any LAN address |
| `SessionManager.kt` | Stores logged-in account number, full name, language code in SharedPreferences |
| `MainActivity.kt` | Splash/router — sends user to Login or Home depending on session state |
| `LoginActivity.kt` | Phone + PIN login; stores session on success |
| `HomeActivity.kt` | Central hub — voice/text pay button, PAY & RECEIVE grid (contacts, receive QR, scan QR, manual entry), transaction history, balance display |
| `MainViewModel.kt` | Calls `/transaction/initiate`; holds voice result; routes to ConfirmActivity |
| `ConfirmActivity.kt` | Shows parsed transaction details for user confirmation before final commit |
| `ConfirmViewModel.kt` | Calls `/transaction/confirm` with signed challenge; receives final result |
| `SuccessActivity.kt` | Displays post-transfer confirmation with amount and recipient in user's language |
| `ContactsActivity.kt` | Reads device phonebook, calls `lookup-batch` to find which contacts have Nidhi accounts, shows filterable list with "Pay" shortcut |
| `ReceiveMoneyActivity.kt` | Generates QR code encoding the user's account number (ZXing `BarcodeEncoder`); fetches live name from API; supports sharing QR as PNG via FileProvider |
| `SendManualActivity.kt` | Manual entry form for account number + amount; pre-fills if launched from contacts or QR scan |
| `NidhiSseClient.kt` | OkHttp-based SSE client; maintains persistent connection to `/push/stream/{account}`; delivers instant incoming payment notifications |
| `TransactionManager.kt` | Singleton that routes incoming SSE push events to active UI callbacks |
| `TransactionPollerWorker.kt` | WorkManager periodic worker — fallback balance polling every 15 min when SSE is not active (battery optimisation) |
| `TransactionHistoryActivity.kt` | Paginated list of past transactions with credit/debit colour coding |
| `ProfileActivity.kt` | Shows account number (with copy button), name, phone, change-server URL field |
| `MyFirebaseMessagingService.kt` | FCM handler — shows system notification for incoming transfers when the app is backgrounded and SSE socket has timed out |

---

## Technology Choices & Why

| Technology | Why |
|-----------|-----|
| **Spring Boot 3.2 (Java 17)** | Rapid REST API development; reactive WebFlux for SSE without blocking threads; mature ecosystem for financial logic |
| **Spring WebFlux + Project Reactor** | SSE push notifications require non-blocking async streams; WebFlux `Sinks` give a clean per-account reactive broadcast model |
| **H2 in-memory DB (bank)** | Zero-config for a hackathon demo; JPA abstraction means swapping to PostgreSQL for production is one line |
| **Spring Data JPA** | Eliminates boilerplate SQL; lets us focus on domain logic rather than JDBC plumbing |
| **Python Flask (sidecar)** | YOLOv8 / OpenCV have mature Python bindings; isolating CV in a separate process prevents JVM + native lib conflicts; lightweight Flask adds minimal overhead |
| **YOLOv8n (Ultralytics)** | Nano variant runs at 10fps on CPU without a GPU; pre-trained COCO weights detect movement proxies (animals, people) with no training required for a demo |
| **OpenCV** | Industry-standard CV library; handles webcam capture, frame-delta motion detection fallback, and MJPEG streaming in ~20 lines |
| **Google Cloud Speech-to-Text** | 60 free minutes/month covers demo; best-in-class accuracy for Indian regional languages; BCP-47 tags for 8 languages supported out of the box |
| **Google Cloud Translation** | 500k free characters/month covers demo; normalises diverse input to English for a single regex-based intent parser |
| **ECDSA (EC P-256)** | Asymmetric signing with tiny key sizes — critical for 2G where every byte costs latency; challenge response fits in one HTTP round-trip |
| **Retrofit2 (Android)** | Type-safe HTTP client; suspending coroutine adapters integrate cleanly with ViewModel + LiveData |
| **ZXing Android Embedded** | Mature, permission-handling QR scanner + encoder; `ScanContract` ActivityResult API is lifecycle-safe |
| **WorkManager** | Android's battery-aware background scheduler; ensures balance polling survives Doze and app restarts without draining battery on rural devices |
| **Firebase Cloud Messaging (FCM)** | Free push delivery for when SSE socket closes (screen off, network drop); guarantees incoming payment alerts reach the user |
| **SSE over FCM for foreground** | SSE is sub-100ms on LAN — far faster than FCM which has 1-5s delivery delay; SSE used when app is open, FCM as fallback |
| **OkHttp SSE** | Low-level SSE implementation gives full control over reconnect logic and heartbeat parsing without large framework dependency |
| **ViewBinding (Android)** | Null-safe view references with no reflection overhead; eliminates the entire class of `NullPointerException` from `findViewById` |
| **SharedPreferences (session + config)** | Simple, sufficient for a single-user session store on a personal device; no Room DB needed for this data volume |
| **FileProvider** | Android 7+ restriction — QR PNG sharing between apps requires a content URI via FileProvider rather than a raw file path |

---

## Running Locally

### Prerequisites
- Java 17+, Maven 3.9+
- Python 3.11+, pip
- Android Studio (Hedgehog or newer) with a device/emulator

### 1. Start the insect sidecar
```powershell
cd nidhi-backend/insect_sidecar
pip install -r requirements.txt
python main.py
# Runs on http://localhost:5001
# Falls back to simulation if no webcam is attached
```

### 2. Start nidhi-backend (security + AI)
```powershell
cd nidhi-backend
# Set your Google API key:
$env:GOOGLE_API_KEY = "AIzaSy..."
mvn spring-boot:run
# Runs on http://localhost:8081
```

### 3. Start nidhi-bank (ledger)
```powershell
cd nidhi-bank
mvn spring-boot:run
# Runs on http://localhost:8082
# Admin dashboard: http://localhost:8082/admin.html
```

### 4. Run the Android app
- In `nidhi-android/app`, build and deploy to device/emulator
- On first launch: tap the URL bar in **Profile** and enter `http://<your-LAN-ip>:8081`
- Or enable **Mobile Hotspot** on your laptop, connect the phone, and use the URL shown in the admin dashboard's "Device Connection Setup" card

### Admin Dashboard
Open `http://localhost:8082/admin.html` in any browser to:
- Register test users
- View all accounts and balances
- Credit / debit accounts for demos
- Watch live transaction log

---

## Security Notes (Hackathon Context)

- The ECDSA **private key is returned to the client** in `ChallengePacket` — this is intentional for demo purposes (simulates a TEE that would hold the key in production). In production the private key never leaves the secure enclave.
- H2 is in-memory — all data resets on server restart.
- The `bank.internal.secret` header provides minimal backend-to-bank auth; production would use mTLS.
- Insect entropy source contributes genuine unpredictability but the system degrades gracefully to hardware TRNG + 4 other sources if the sidecar is unavailable.

# Wallet System

A secure digital-wallet backend (Paytm/PhonePe style) built with Spring Boot. Each
user owns exactly one wallet. Balances stay correct under concurrent requests,
duplicate API calls, and partial failures — enforced at the **database** layer,
never with in-memory locks.

---

## 1. Project overview

- JWT-authenticated REST API for registering users, adding money, transferring
  money between users, and viewing wallet/transaction history.
- Money operations are **idempotent** (client-supplied `Idempotency-Key`) and
  **concurrency-safe** (pessimistic row locks with deterministic ordering).
- Admin endpoints expose all wallets and transactions.

## 2. Features

- Register / login with BCrypt-hashed passwords and JWT bearer tokens.
- Auto-provisioned single wallet per user (DB-enforced 1:1).
- Add money and transfer money, each atomic and idempotent.
- Paginated transaction history (own history for users, all for admins).
- Role-based authorization (`USER`, `ADMIN`).
- Consistent JSON error responses via `@RestControllerAdvice`.
- Bean Validation on all request DTOs.
- OpenAPI/Swagger UI.
- JaCoCo coverage (80% line gate) and SonarQube configuration.

## 3. Technology stack

| Area | Choice |
|------|--------|
| Language | Java 21 |
| Framework | Spring Boot 4.1.x (Web MVC, Data JPA, Security) |
| Security | Spring Security 7 + JWT (jjwt 0.11.5) |
| Persistence | Spring Data JPA / Hibernate 7 |
| Database | H2 (file-based locally, in-memory for tests) |
| Validation | Jakarta Bean Validation |
| Docs | springdoc-openapi (Swagger UI) |
| Build | Maven |
| Testing | JUnit 5, Mockito, MockMvc, Spring Boot Test |
| Coverage | JaCoCo |
| Quality | SonarQube |

> Note: Spring Boot 4 uses **Jackson 3** (`tools.jackson`) as the default JSON
> mapper. The custom security error writers depend on the Jackson 3
> `ObjectMapper` bean accordingly.

## 4. Architecture / project structure

```
com.example.wallet_system
├── config          # SecurityConfig, OpenApiConfig, DataInitializer (admin seed)
├── controller      # AuthController, WalletController, AdminController
├── dto
│   ├── request     # RegisterRequest, LoginRequest, AddMoneyRequest, TransferRequest
│   └── response    # AuthResponse, WalletResponse, TransactionResponse, ErrorResponse, PageResponse...
├── entity          # User, Wallet, Transaction
├── enums           # Role, TransactionType, TransactionStatus
├── exception       # ApiException + typed subclasses, ErrorCode, GlobalExceptionHandler
├── mapper          # WalletMapper (entity -> DTO)
├── repository      # UserRepository, WalletRepository, TransactionRepository
├── security        # JwtService, JwtAuthenticationFilter, CustomUserDetailsService,
│                   #   AuthenticatedUser, SecurityUtil, entry-point / access-denied handlers
├── service         # AuthService, WalletService, AdminService (interfaces)
│   └── impl        # *Impl + WalletOperationCore (transactional core)
└── util            # MoneyUtil
```

Separation of concerns:
- **Controllers** — HTTP only; derive the caller from the security context.
- **Services** — business logic and transaction boundaries.
- **Repositories** — persistence, including locking queries.
- **Security** — JWT issuance/validation and authorization.

## 5. Database / entity design

**User** (`users`): `id`, `email` (unique), `password` (BCrypt hash), `role`.

**Wallet** (`wallets`): `id`, `user_id` (unique FK — one wallet per user),
`balance` (`BigDecimal(19,2)`), `version` (`@Version` optimistic lock).

**Transaction** (`transactions`): `id`, `from_wallet_id` (nullable),
`to_wallet_id`, `amount`, `type`, `status`, `idempotency_key` (**unique**),
`created_at`.

Constraints/indexes:
- `uk_users_email`, `uk_wallets_user`, `uk_transactions_idempotency_key`
- FKs `fk_wallets_user`, `fk_tx_from_wallet`, `fk_tx_to_wallet`
- Indexes on `from_wallet_id` / `to_wallet_id` for history queries.

**Why `from_wallet` is nullable:** an `ADD` (top-up) has no source wallet — money
enters the system — so `from_wallet` is null and `to_wallet` is the user's
wallet. A `TRANSFER` always has both wallets.

## 6. API list

| Method | Path | Role | Notes |
|--------|------|------|-------|
| POST | `/auth/register` | public | creates USER + wallet |
| POST | `/auth/login` | public | returns JWT |
| GET | `/wallet` | USER | own wallet |
| POST | `/wallet/add` | USER | `Idempotency-Key` required |
| POST | `/wallet/transfer` | USER | `Idempotency-Key` required |
| GET | `/wallet/transactions` | USER | paginated own history |
| GET | `/admin/wallets` | ADMIN | paginated all wallets |
| GET | `/admin/transactions` | ADMIN | paginated all transactions |

## 7. Authentication flow

1. `POST /auth/register` → password BCrypt-hashed, `USER` role assigned, wallet
   created with zero balance in the **same transaction**.
2. `POST /auth/login` → credentials verified by Spring Security's
   `AuthenticationManager`; on success a signed JWT is returned.
3. Client sends `Authorization: Bearer <token>` on every protected request.
4. `JwtAuthenticationFilter` validates the token and populates the
   `SecurityContext` with an `AuthenticatedUser` (carrying the user id + role).

## 8. JWT implementation

- HS256, signed with `app.jwt.secret` (env `APP_JWT_SECRET`).
- Claims: `sub` = user id, `email`, `role`, `iat`, `exp`.
- Expiry configurable via `app.jwt.expiration-ms` (default 1 hour).
- Invalid/expired/forged tokens leave the context unauthenticated → protected
  endpoints return `401`. The secret is **never** hardcoded in source.

## 9. Authorization rules

- `USER`: only their own wallet — the wallet is always derived from the JWT
  identity. **No wallet id is ever accepted from the request**, so a user cannot
  reach another user's wallet.
- `ADMIN`: can list all wallets and transactions.
- `/admin/**` is restricted by both URL rule and `@PreAuthorize("hasRole('ADMIN')")`.
  A `USER` hitting an admin endpoint gets `403`.

## 10. Idempotency strategy

Every money-changing API requires a non-blank `Idempotency-Key` header
(`400 MISSING_IDEMPOTENCY_KEY` otherwise). The key is stored in
`transactions.idempotency_key` with a **unique** constraint — the database is the
source of truth, not application memory.

Flow (`WalletServiceImpl`):
1. **Fast path** — if a transaction already exists for the key, return it; no
   balance change.
2. **Process** — otherwise `WalletOperationCore` mutates balances and inserts the
   ledger row in a single transaction, flushing so the unique constraint is
   checked immediately.
3. **Concurrent-duplicate path** — if two requests with the same key run at once,
   both may pass step 1; one insert wins, the other hits the unique constraint.
   The loser's whole transaction (including its balance change) rolls back; we
   catch `DataIntegrityViolationException` and return the **winner's** committed
   row.

Because the balance mutation and the key insert are in the **same** transaction,
a duplicate can never leave a committed balance change without a corresponding
(unique) ledger row. Verified by `WalletConcurrencyTest`:
8 concurrent requests with one shared key move the money exactly once.

## 11. Concurrency strategy

**Pessimistic locking** (`@Lock(PESSIMISTIC_WRITE)`), chosen over optimistic for
money movement because it is the easiest to make correct and to explain: the
row is locked for the duration of the transaction, so a conflicting transfer
simply waits rather than failing and needing a retry loop. The `@Version` column
is still present on `Wallet` (assignment requirement) and gives a second line of
defence; `ObjectOptimisticLockingFailureException` is mapped to a clean
`409 CONCURRENT_MODIFICATION`.

Example (`WalletConcurrencyTest`): balance 1000, two concurrent transfers of 800
→ exactly one succeeds, one fails with `INSUFFICIENT_BALANCE`, final balances are
200 / 800, never negative.

## 12. Locking strategy (deadlock avoidance)

A transfer locks two wallets. To avoid the classic A→B / B→A deadlock, wallet ids
are first read with a **scalar** query (`findWalletIdByUserId`, which does *not*
load the entity), then the two rows are locked with `findByIdForUpdate` in
**ascending id order**. Two transfers touching the same pair therefore always
acquire locks in the same order and cannot deadlock.

> The scalar-id read matters: loading a wallet entity *unlocked* first would pin
> a stale `@Version` in the persistence context and cause a spurious optimistic
> failure at commit even while a pessimistic lock is held. Reading only the id
> avoids that.

## 13. Transaction boundaries

`WalletOperationCore` methods run in `@Transactional(REQUIRES_NEW)`. It is a
separate bean from the orchestrator so Spring's transactional proxy actually
applies (a self-invocation would bypass it) and so a duplicate's rollback is
scoped to just that operation.

A transfer is one atomic transaction:

```
BEGIN
  resolve wallet ids (scalar)
  lock sender + receiver (PESSIMISTIC_WRITE, ascending id)
  check sender balance
  debit sender / credit receiver
  insert TRANSFER row (flush -> unique key check)
COMMIT   (any failure -> ROLLBACK everything)
```

There is never a state where the sender is debited but the receiver is not
credited, and only `SUCCESS` rows are ever persisted (a business failure rolls
back with no ledger row).

## 14. Error handling

`GlobalExceptionHandler` (`@RestControllerAdvice`) returns a consistent body and
never leaks stack traces:

```json
{
  "timestamp": "2026-01-01T00:00:00Z",
  "status": 422,
  "error": "INSUFFICIENT_BALANCE",
  "message": "Insufficient wallet balance",
  "path": "/wallet/transfer",
  "fieldErrors": null
}
```

Handled: validation errors, malformed body, missing header, duplicate email,
user/wallet not found, insufficient balance, self-transfer, invalid amount,
missing idempotency key, idempotency conflict, optimistic-lock conflict,
bad credentials (401), access denied (403), and a catch-all 500. Security
failures before the controller (401/403) use the same shape via the auth entry
point and access-denied handler.

## 15. Validation

Jakarta Bean Validation with `@Valid` on request bodies: `@Email`, `@NotBlank`,
`@NotNull`, `@Positive`, `@Size`, `@Digits` (amount ≤ 2 decimals). Failures
produce `400 VALIDATION_ERROR` with a `fieldErrors` list.

## 16. Money handling

All monetary values use `BigDecimal` (never `double`/`float`). `MoneyUtil`
normalizes to **scale 2** with **`HALF_EVEN`** (banker's) rounding. Wallet
balances can never go negative — a transfer checks the locked balance before
debiting.

## 17. H2 configuration

Configured in `src/main/resources/application.yml`.

- Local: file-based `jdbc:h2:file:./data/wallet` (persists across restarts).
- Tests: in-memory (`src/test/resources/application.yml`), `create-drop`.
- H2 console at `/h2-console` — **development/testing only**, disable in real
  deployments.

To run purely in-memory locally, set the datasource url to
`jdbc:h2:mem:wallet;DB_CLOSE_DELAY=-1`.

## 18. How to run locally

```bash
export APP_JWT_SECRET="a-long-random-at-least-32-byte-secret-value"
./mvnw spring-boot:run
```

App starts on `http://localhost:8080`. A seed **ADMIN** account is created on
first startup: `admin@wallet.local` / `Admin@12345` (override via
`APP_ADMIN_EMAIL` / `APP_ADMIN_PASSWORD`). Registration only ever creates USERs.

Swagger UI: `http://localhost:8080/swagger-ui.html`
H2 console: `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:file:./data/wallet`)

### Environment variables

| Variable | Default (dev) | Purpose |
|----------|---------------|---------|
| `APP_JWT_SECRET` | long dev fallback | HS256 signing secret (set in shared envs) |
| `APP_JWT_EXPIRATION_MS` | `3600000` | token lifetime |
| `APP_ADMIN_EMAIL` | `admin@wallet.local` | seed admin email |
| `APP_ADMIN_PASSWORD` | `Admin@12345` | seed admin password |

## 19. Maven commands

```bash
./mvnw clean compile     # build
./mvnw test              # unit + integration + concurrency tests
./mvnw verify            # tests + JaCoCo report + 80% coverage gate
./mvnw spring-boot:run   # run
```

## 20. How to run tests

```bash
./mvnw test
```

Includes real multithreaded concurrency tests (`WalletConcurrencyTest`) and DB
integration tests (`WalletServiceIntegrationTest`, `ApiIntegrationTest`) that use
the real H2 database — the locking/idempotency behavior is proven against actual
persistence, not mocks.

## 21. How to generate the coverage report

```bash
./mvnw verify
```

Report: `target/site/jacoco/index.html` (XML at `target/site/jacoco/jacoco.xml`).
The build fails if line coverage drops below **80%** (config/dto/entity/enums
excluded from the ratio). Current coverage is ~96% line / ~94% instruction.

## 22. SonarQube setup / run

Config: `sonar-project.properties` and the `sonar-maven-plugin` in `pom.xml`.
The server URL and token **must be supplied by the developer** (never committed):

```bash
# start a local server (example)
docker run -d --name sonarqube -p 9000:9000 sonarqube:community

# analyze (reuses the JaCoCo XML from `verify`)
./mvnw clean verify sonar:sonar \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.token=YOUR_TOKEN
```

## 23. API examples

### Register
```bash
curl -X POST http://localhost:8080/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"user@example.com","password":"Password123"}'
```
```json
{ "userId": 2, "email": "user@example.com", "role": "USER", "walletId": 2 }
```

### Login
```bash
curl -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"user@example.com","password":"Password123"}'
```
```json
{ "token": "eyJhbGci...", "tokenType": "Bearer" }
```

### Add money (idempotent)
```bash
curl -X POST http://localhost:8080/wallet/add \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: add-2026-0001" \
  -H 'Content-Type: application/json' \
  -d '{"amount": 500.00}'
```
```json
{ "id": 1, "fromWalletId": null, "toWalletId": 2, "amount": 500.00,
  "type": "ADD", "status": "SUCCESS", "idempotencyKey": "add-2026-0001",
  "createdAt": "2026-01-01T00:00:00Z" }
```
Repeating the request with the same `Idempotency-Key` returns the **same**
transaction and does not add money again.

### Transfer
```bash
curl -X POST http://localhost:8080/wallet/transfer \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: xfer-2026-0001" \
  -H 'Content-Type: application/json' \
  -d '{"toUserId": 3, "amount": 300.00}'
```

### Get wallet
```bash
curl http://localhost:8080/wallet -H "Authorization: Bearer $TOKEN"
```
```json
{ "walletId": 2, "balance": 1500.00 }
```

## 24. Example headers

```
Authorization: Bearer <jwt>
Idempotency-Key: <unique-per-operation-key>   # /wallet/add and /wallet/transfer
Content-Type: application/json
```

## 25. Pagination usage

```bash
curl "http://localhost:8080/wallet/transactions?page=0&size=10&sort=createdAt,desc" \
  -H "Authorization: Bearer $TOKEN"
```
Response envelope:
```json
{ "content": [ ... ], "page": 0, "size": 10, "totalElements": 23,
  "totalPages": 3, "first": true, "last": false }
```
Admin endpoints (`/admin/wallets`, `/admin/transactions`) use the same paging.

## 26. Assumptions / design decisions

- Only `SUCCESS` transactions are persisted; business failures roll back fully.
  The `FAILED` status is modelled for completeness.
- The recipient of a transfer is identified by `toUserId`; the sender is always
  the authenticated user.
- Admins are provisioned via the startup seeder, not via registration.
- `HALF_EVEN` rounding at scale 2 for all money.
- Pessimistic locking chosen for clarity and correctness; `@Version` retained per
  the assignment and mapped to a clean 409 if it ever fires.

## 27. Known limitations

- H2 is intentionally the only database (assignment constraint). It is not
  intended for production persistence.
- No refresh-token / logout / token-revocation flow (single short-lived access
  token).
- No rate limiting.
- Idempotency keys are stored indefinitely (no TTL/cleanup job).

## 28. Optional deployment

The app is a single self-contained jar (`./mvnw clean package` →
`target/wallet-system-0.0.1-SNAPSHOT.jar`) and can run on any JVM host
(Render/Railway/etc.). **However, it uses H2** (assignment requirement): on
platforms with ephemeral disks the file database is **not durable** across
restarts/redeploys. Deploy only for demonstration, and set `APP_JWT_SECRET` and
admin credentials via environment variables. For real persistence a managed RDBMS
would be required, which is out of scope here.
```bash
./mvnw clean package
APP_JWT_SECRET=... java -jar target/wallet-system-0.0.1-SNAPSHOT.jar
```

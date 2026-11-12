# Release Hardening Implementation Plan

> **Für agentische Ausführung:** Der Orchestrator koordiniert nur. JEDES Arbeitspaket (AP) wird von einem eigenen Sonnet-Subagenten umgesetzt (Skill `superpowers:subagent-driven-development`). Jedes AP unten ist ein in sich geschlossenes Briefing. Schritte mit Checkbox-Syntax (`- [ ]`) werden vom Agenten abgehakt.

**Ziel:** BoxPilot (Spring Boot 4.1.2 / React 19) release-fähig härten: kein Fremdsperren von Konten mehr (Throttling pro Client-IP), Passwort-Reset beendet Sitzungen, Upload-/Container-/Header-Härtung, CI, Test- und UX-Lücken schließen, danach vollständiger Docker-Compose-E2E-Lauf und Go/No-Go.

**Architektur in einem Satz:** Der generische Fixed-Window-Limiter wandert aus `mcp.abuse` in ein neutrales Paket `security`; ein Servlet-Filter vor dem Login antwortet mit 429, bevor DB/bcrypt arbeiten; das harte Username-Lock entfällt. Sitzungen werden über eine Spalte `users.sessions_valid_after` ungültig, die `SessionRevalidationFilter` gegen die Session-Erstellzeit prüft.

**Stand/Basis:** Repo-Root `C:\Users\mathi\Coding\BoxPilot\.claude\worktrees\springboot-4-migration-review-4b6e81`, Branch `claude/springboot-4-migration-review-4b6e81`. Höchste Flyway-Version heute: **V14** (`V2` existiert nicht; neue Migrationen sind `V15`, `V16`). Backend-Suite: 738 Tests grün, Frontend: 54 Tests grün. Hausstil-Vorbild: `2026-11-01-spring-boot-4-migration.md`.

---

## 1. Ziel und Umfang

**Im Umfang (Must-have, Owner-Entscheidungen 2 und 3):**
- Per-Client-IP-Throttling fehlgeschlagener Logins; niemand kann fremde Konten sperren.
- Passwort-Reset beendet alle Sitzungen des betroffenen Nutzers (Form-, OIDC-, Magic-Link-Sitzungen, MCP-Authorize-Auflösung), invalidiert Magic-Login-Tokens und widerruft MCP-Zugang.
- Alle P1-Punkte der drei Reviews, günstige P2-Punkte (siehe Abschnitt 3), CI-Workflow, Abhängigkeitsprüfung (`npm audit`, OWASP).
- Schlussphase: Compose-Rebuild in WSL2 mit E2E-Overlay, erweiterte Playwright-Suite, Log-Prüfung, Release-Checkliste, Go/No-Go.

**Explizit NICHT im Umfang:**
- **Nextcloud-Auto-Provisioning bleibt unverändert** (Owner-Entscheidung 1). `UserService.createOrUpdateOidcUser` wird nur insoweit angefasst, als die Lock-Prüfung (`assertMayLogIn`) entfällt; Anlegen/Verknüpfen von OIDC-Nutzern ändert sich nicht.
- Keine Entfernung der Spalten `failed_login_attempts` / `locked_until` (Migration bleibt vorwärtskompatibel, Spaltenentfernung → Backlog).
- Kein verteilter Limiter (Redis), kein MFA, keine Änderung am MCP-Protokoll.

## 2. Ausgangslage (bereits erledigt, verifiziert und committet, nicht gepusht)

Die Fixes der ersten Runde liegen als drei Commits auf dem Branch (`7f1b1a5` Backend, `6a06032` Frontend, `7103688` E2E); Rollback-Referenz vor diesem Release: `5e20b66`, Basis für die Wellen: `7103688`. Inhalt: atomares Lockout-`UPDATE` (`UserRepository.recordFailedLogin`), Upload-Limits, 400/413-Handler (`GlobalExceptionHandler`), toleranter Redis-Session-Serializer (`RedisConfig`), nginx gzip/Cache/Security-Header (`frontend/nginx.conf`, `frontend/security-headers.inc`), i18n-/401-/Login-UX-Fixes, E2E-Suite `e2e/smoke.mjs` (13 Schritte). Diese Änderungen betreffen u. a. `OAuth2SecurityConfig`, `UserService`, `UserRepository`, `ItemRepository`, `ImageStorageService`, `App.tsx`, `LoginPage.tsx`, `AdminPanel.tsx`, beide Locale-Dateien. **Phase 0 ist erledigt** (Secret-Scan ohne Treffer, Working Tree danach sauber), jedes AP arbeitet gegen einen sauberen Diff.

### Korrekturen gegenüber den Review-Aussagen (im Code geprüft)

| Review-Aussage | Befund im Code |
|---|---|
| Admin-Update brauche nur `@Valid` (`PUT /admin/users/{id}`) | `@Valid` fehlt (`AdminUserController.updateUser`), aber `UpdateUserDTO` hat **keine einzige Constraint-Annotation** → `@Valid` allein ist wirkungslos; Constraints müssen ergänzt werden (AP-A10). |
| Test „Admin kann sich nicht selbst löschen" fehlt | Die Logik existiert bereits (`UserService.deleteUser` wirft bei gleichem Username). Es fehlt nur der Controller-Test (AP-A11). |
| `AuthController` über `UserService` führen | `UserService.findByUsername/findByOidcSubject` existieren schon; kein neuer Service-Code nötig (AP-A7). |
| Lock-Pfad entfernen betrifft Login + Magic-Link | Lock wird zusätzlich geprüft in `UserService.assertMayLogIn` (OIDC), `McpAccessPolicy` (Zeile ~129), `CustomUserPrincipal.isAccountNonLocked`, Admin-UI (Badge, Filter „locked", Entsperren) und `PUT /admin/users/{id}/unlock`. Umfang größer als genannt (AP-A2). MCP-Prüfung bleibt als harmlose Toter-Pfad-Logik stehen (Spalte wird nie wieder gesetzt). |
| „ERROR-Stacktrace pro gesperrtem Login" | Im Code loggt `CustomUserDetailsService` nur `WARN` ohne Stacktrace; die ERROR-Quelle ist nicht aus dem Code ableitbar. **Live beobachtet** im Backend-Log des Docker-Laufs vom 2026-11-11: `UsernamePasswordAuthenticationFilter - An internal error occurred while trying to authenticate the user` mit `InternalAuthenticationServiceException`, verursacht durch `CustomUserDetailsService$AccountLockedException` – Spring wickelt die aus `loadUserByUsername` geworfene Custom-Exception ein. AP-A2 reproduziert den Effekt trotzdem zuerst per Test und behebt ihn dann (benutzerdefinierte Throws entfernen, Spring prüft `isAccountNonLocked()/isEnabled()` selbst). |
| Startup-Guard gegen Origin-Mismatch `VITE_API_BASE_URL` ↔ `BACKEND_URL` | Die Variable `VITE_API_BASE_URL` **wird** gelesen: `application-prod.yml` baut daraus die Nextcloud-OIDC-`redirect-uri` (`${VITE_API_BASE_URL}/login/oauth2/code/{registrationId}`), und `docker-compose.yml` reicht sie an den Backend-Container durch. Aber weder Java-Code noch eine MCP-Property binden sie → AP-A6 braucht weiterhin die neue Property `app.mcp.spa-api-base-url` (aus derselben Env-Variable in A8 gebunden). Das bestehende `redirect-uri`-Verhalten bleibt unverändert. |
| `@EnableScheduling` für den Limiter | `@EnableScheduling` hängt an `mcp/McpConfiguration` (nur bei `app.mcp.enabled=true`) und die Doku sagt „ohne MCP kein Scheduler". Der neue Limiter evictet daher **lazy** (kein Scheduler), siehe AP-A1. |
| Tote Endpunkte `GET /api/v1/items`, `GET /api/v1/box-numbers/status` | Bestätigt: kein Frontend-Aufrufer. Aber `README.md:128` dokumentiert `GET /api/v1/items` → README mitpflegen. |
| Session-Cookie SameSite hartkodiert | Nur in `application-prod.yml` (`same-site: none`, `secure: true`), nicht in dev. |
| Passwort-Hash-Vergleich im Principal (Architektur-Review) | **Verworfen**: das Security-Review will den Hash gerade aus der Session entfernen (AP-A13). Deshalb `sessions_valid_after` statt Hash-Vergleich. |

## 3. Entscheidungen

### 3.1 Getroffen (verbindlich)

1. **Throttling ersetzt das harte Username-Lock.** Würde das Lock bleiben, bliebe die Sperr-Schwachstelle. Schlüssel: pro IP (10 Fehlversuche / 15 min) und pro IP+Username (5 / 15 min), konfigurierbar (`app.security.login-throttle.*`, damit E2E deterministisch ist). IP = `request.getRemoteAddr()` (aufgelöst durch RemoteIpValve), IPv6 auf /64 normalisiert.
2. Filter vor dem Form-Login-Filter beantwortet `POST /api/auth/login` und `POST /api/v1/auth/magic-login` bei erschöpftem Kontingent mit **429 + `Retry-After`** und JSON `{"error":"Too many failed login attempts","retryAfterSeconds":N}` – vor jeder DB-/bcrypt-Arbeit. Failure-Handler und Magic-Login-401 füttern den Limiter. Ein erschöpftes IP-Kontingent blockiert auch korrekte Logins dieser IP bis Fensterende (bewusst, Standardverhalten).
3. **Limiter-Umzug:** `mcp/abuse/McpRateLimiter` → `com.boxcopilot.backend.security.FixedWindowRateLimiter` (ArchUnit-Regel `McpArchitectureTest` verbietet Abhängigkeit nicht-MCP → `mcp..`; MCP → `security` ist erlaubt). Eviction lazy im Limiter (z. B. jede 256. Anfrage), kein `@EnableScheduling` außerhalb MCP. In-Process-Limiter = ein Replica, Reset bei Neustart (dokumentieren).
4. **Sitzungs-Invalidierung** über neue nullable Spalte `users.sessions_valid_after` (V16), gesetzt in `UserService.setPassword`; `SessionRevalidationFilter.isStale` vergleicht `session.getCreationTime()`; gleiche Prüfung in `McpSessionUserResolver` (MCP-Authorize-Chain hat eigenen Filter). Zusätzlich: Magic-Login-Tokens des Nutzers invalidieren, `McpAccessRevokedEvent` publizieren. Offene technische Frage, die AP-A9 per Test klärt: `changeSessionId()` erhält die Erstellzeit (Servlet-Spezifikation und Spring Session Redis), und die eigene Admin-Sitzung bleibt bei Reset eines *anderen* Nutzers unberührt; setzt ein Admin sein *eigenes* Passwort, endet die eigene Sitzung (dokumentiert, Re-Login).
5. Flyway: **V15** löscht bestehende Locks (`UPDATE users SET locked_until=NULL, failed_login_attempts=0`), **V16** fügt `sessions_valid_after` hinzu. Eine Migration pro Anliegen; angewandte Migrationen werden nie editiert. Neues Postgres-spezifisches SQL wird gegen Postgres-Testcontainers getestet (frühere Lektion: H2 grün, Postgres im Browser rot).
6. Nextcloud-Provisioning unverändert.
7. Parallelität: kein File gehört zwei Tasks derselben Welle. Frontend-Locale-Dateien (`de.ts`/`en.ts`) und `App.tsx` gehören pro Welle genau **einem** Frontend-Task; Frontend-Tasks laufen als Kette (eine Spur FE1 → FE4 über die Wellen), parallel zu den Backend-Tasks.
8. Actuator: nur `health` exponiert, `show-details=never`, `/actuator/health` in `OAuth2SecurityConfig` freigegeben (nicht über separaten Management-Port, weil dann ein zweiter Security-Kontext zu verifizieren wäre). Der Fallback-`SimpleMeterRegistry` in `McpConfiguration` ist bereits per `@ConditionalOnMissingClass(MetricsAutoConfiguration)` abgesichert – AP-A12 prüft, dass die MCP-Metrik-Tests grün bleiben.

### 3.2 Offene Entscheidungen für den Owner (mit Empfehlung)

| # | Frage | Empfehlung |
|---|---|---|
| O1 | **Widerrufbare öffentliche Share-Links** (Rotation von `box.uuid` + Item-Image-Tokens; gedruckte QR-Labels werden ungültig → UI-Warnung). AP-X1 + Teil von FE4. | **Ja**, Größe S (Backend) / M (mit UI). Als eigener Merge-Punkt, bei Nein entfällt X1 ersatzlos. |
| O2 | **Backend standardmäßig als non-root** (`USER 10001`, `read_only`, `tmpfs /tmp`, `cap_drop: ALL`, `no-new-privileges`, benannte Volumes bzw. einmaliges `chown` für Bind-Mounts). AP-OP3. | **Ja**, aber nur mit Migrationsabschnitt in den Release-Notes (Bind-Mounts `./logs`, `./images` brauchen `chown 10001:10001`). Falls Upgrade-Reibung unerwünscht: Default aus, nur dokumentiert und im E2E-Overlay aktiv. |
| O3 | **Session-Cookie `SameSite`** konfigurierbar machen (`SESSION_COOKIE_SAME_SITE`, Default `none` wie heute, `Lax` wenn SPA und API dieselbe Site sind). AP-A12. | **Ja**, Default unverändert (kein Verhalten ändert sich ungefragt), README erklärt `Lax` für Same-Site-Setups. |
| O4 | **Scope P2/P3:** empfohlener Schnitt = alle Must-haves + alle P1 + günstige P2 (Abschnitt 3.3) in *diesem* Release; Rest ins Backlog. | Schnitt wie in 3.3 übernehmen. |
| O5 | **Mindestlänge Admin-Passwort 12** (`AdminPasswordGuard`) bricht Start bestehender Prod-Installationen mit kürzerem `ADMIN_PASSWORD`. | **Ja**, mit fettem Hinweis in den Release-Notes (Upgrade-Schritt); Start schlägt mit klarer Meldung fehl. |
| O6 | **Principal verschlanken** (Hash/`User` aus der Session, AP-A13). Alte Sessions werden unlesbar → einmaliger Re-Login (toleranter Serializer fängt das ab). | **Ja** in Welle 4; fällt er aus dem Zeitplan, ist er ohne Folgen streichbar (Go/No-Go-Kandidat zum Kappen). |
| O7 | **Versionsnummer** des Release (`backend/pom.xml` heute `0.1.0`; Dockerfile kopiert `target/backend-0.1.0.jar`). | `0.2.0`; Dockerfile auf `ARG APP_VERSION`/Wildcard-freies `finalName` umstellen (AP-OP3). |

### 3.3 Empfohlener Release-Schnitt

**In diesem Release:** AP-A1..A13, CI1, OP1..OP3, FE1..FE4, DOC1, Schlussphase; Optional markiert: X1 (O1), OP3-Non-root-Teil (O2), A13 (O6).
**Backlog (nicht in diesem Release):** Skeleton-Loading-States; Pluralisierung „1 Items" außerhalb der angefassten Dateien; `prefers-color-scheme` als Initial-Theme (nur Drei-Zeilen-Teil in FE1 falls trivial); `theme-color`-Variante pro Modus; Bulk-Action-Label-Umbruch bei 360 px; Admin-Inline-Validation-Vollausbau (nur in FE2 für Passwort/Username); verteilter Limiter; Spaltenentfernung `locked_until`/`failed_login_attempts`; Redis-gestützte Session-Revalidierung statt Pro-Request-DB-Lookup; Image-Tagging via Registry.

---

## 4. Wellen und Arbeitspakete

Übersicht (Größe S/M/L; „Dateien“ nur die *exklusiven*; Tests sind jeweils im selben AP):

| ID | Titel | Prio | Größe | Welle | Exklusive Dateien (Kurzform) | Hängt ab von |
|---|---|---|---|---|---|---|
| P0 | Baseline-Commit (erledigt: `7f1b1a5`, `6a06032`, `7103688`; offen nur Abhängigkeits-Audit) | – | S | 0 | (nur git) | – |
| A1 | Throttle-Kern + Limiter-Umzug | Must | M | 1 | `security/*` (neu), `mcp/abuse/*`, `mcp/security/*`-Nutzer des Limiters, deren Tests | P0 |
| A2 | Lock-Entfernung + V15 | Must | M | 1 | `User`, `UserRepository`, `UserService`(Lock-Teile), `CustomUserDetailsService`, `OAuth2SecurityConfig`(Failure-Handler), `AdminUserController`(unlock), V15, Tests | P0 |
| A3 | Public-Preview in Service + Box-Delete räumt Bilder | P1 | M | 1 | `PublicPreviewController`, `BoxService`, `ItemMapper`, Tests | P0 |
| A4 | Upload-Härtung (Semaphore, MAX_PIXELS) | P1 | S | 1 | `ImageStorageService`, `ImageStorageServiceTest` | P0 |
| A5 | Startup-Scans + tote Endpunkte | P1/P3 | M | 1 | `ItemService`, `ItemController`, `BoxNumberController`, `BoxNumberMigration`, `BoxRepository`, `ItemRepository`, Tests | P0 |
| A6 | MCP-Startup-Guard Origin-Check | P1 | S | 1 | `McpStartupGuard`, `McpProperties`, deren Tests (YAML-Bindung übernimmt A8) | P0 |
| A7 | `AuthController` über `UserService` | P1 | S | 1 | `AuthController`, `AuthControllerTest` | P0 |
| CI1 | GitHub-Workflow | P1 | S | 1 | `.github/workflows/ci.yml` (neu) | – |
| FE1 | Frontend-Shell, Theme, a11y, Lazy-Routes | P1/P2 | L | 1 | `index.html`, `App.tsx`, `ThemeContext`, `LanguageContext`, `LanguageSelector`, `de.ts`, `en.ts`, `App.test.tsx`, `ItemsTable.tsx` (löschen) | P0 |
| A8 | Throttle-Verdrahtung | Must | M | 2 | `OAuth2SecurityConfig`, `MagicLoginController`, `application.yml`, Tests | A1, A2 |
| A9 | Sitzungs-Invalidierung bei Passwort-Reset | Must | L | 2 | V16, `User`, `UserService`, `SessionRevalidationFilter`, `McpSessionUserResolver` + Aufrufer, Tests | A2 |
| A10 | Validierungs-Härtung | P2 | S | 2 | `AdminPasswordGuard`, `CreateUserDTO`, `UpdateUserDTO`, `SetPasswordDTO`, `AdminUserController`, Tests | A2 |
| OP1 | Compose-Härtung | P1 | M | 2 | `docker-compose.yml`, `docker-compose.e2e.yml` | P0 |
| FE2 | Login + Admin-Panel | Must/P1 | L | 2 | `LoginPage.tsx`, `AdminPanel.tsx`, `userService.ts`, `types/models.ts`, `services/apiError.ts`, Locales, Tests | FE1, A8-Vertrag |
| A11 | AuthZ-Regressionstests | P2 | M | 3 | neue Testdateien (nur Tests) | A2, A10 |
| A12 | Ops-Backend: Actuator, SameSite, OWASP, Prod-Property-Check | P1/P2 | M | 3 | `pom.xml`, `application.yml`, `application-prod.yml`, `OAuth2SecurityConfig`, `StartupGuard`, Tests | A8 |
| X1 | Share-Link-Rotation (optional, O1) | P2 | S | 3 | `BoxController`, `BoxService`, `BoxRepository`, `ItemRepository`, Tests | A3 |
| OP2 | Frontend-CSP/HSTS per envsubst | P1 | M | 3 | `frontend/Dockerfile`, `frontend/nginx.conf`, `frontend/security-headers.inc` → Template, `docker-compose.yml`, `docker-compose.e2e.yml` | OP1 |
| FE3 | PublicPreview, BoxList, Tabelle | P1 | L | 3 | `PublicPreview.tsx`, `publicPreviewService.ts`, `BoxList.tsx`, `useBoxListLogic.ts`, `EnhancedItemsTable.tsx`, Locales, Tests | FE2, A3 |
| A13 | Principal verschlanken (optional, O6) | P3 | M | 4 | `CustomUserDetailsService`, `OAuth2SecurityConfig`, `MagicLoginController`, `SessionRevalidationFilter`, `McpSessionUserResolver` + Aufrufer, `AuthController`, Tests | A8, A9, A12 |
| OP3 | Non-root (O2), Actuator-Healthcheck, Versions-Bump | P1 | M | 4 | `backend/Dockerfile`, `docker-compose.yml`, `docker-compose.e2e.yml`, `backend/pom.xml` (nur `<version>`) | A12, OP2 |
| FE4 | ItemForm, Upload, BoxEdit, Share-UI | P1/P2 | L | 4 | `ItemForm.tsx`, `ItemImageUpload.tsx`, `ItemMoveDialog.tsx`, `BoxEditPage.tsx`, `BoxForm.tsx`, `boxService.ts`, `itemService.ts`, Locales, Tests | FE3, X1 |
| DOC1 | Doku: README, .env.example, Ops-Notiz, Release-Notes-Entwurf | P2/P3 | M | 4 | `README.md`, `.env.example`, `docs/ops/*`, `docs/releases/*` (neu), `CLAUDE.md`-Dateien | alle |
| E2E1 | E2E-Suite erweitern | Must | M | Final | `e2e/smoke.mjs`, `e2e/README.md` | alle |

Wellenregel: Ein Task startet erst, wenn die Gate-Prüfung der vorigen Welle grün ist. Innerhalb einer Welle laufen alle Tasks parallel. Der Orchestrator führt `graphify update .` einmal je Gate aus (nicht die Agenten – paralleler Schreibzugriff auf `graphify-out/`).

### Gemeinsame Regeln für jeden Agenten (gelten in jedem Briefing)

- Nur die im AP genannten Dateien ändern/anlegen; bei Bedarf an einer fremden Datei: stoppen und melden, nicht editieren.
- Kein `git commit`/`push`, keine Docker-/Compose-Läufe (außer Postgres-Testcontainers über Maven – Docker Desktop muss laufen; eine Maven-Ausführung mit `Skipped > 0` bei Postgres-Tests zählt nicht als grün).
- Backend-Befehle aus `backend/`, npm-Befehle aus `frontend/`. Erst den engsten Test (`mvn -B -q -Dtest=<Klasse> test` bzw. `npx vitest run <Datei>`), dann das Modul.
- Bestehenden nächsten Test als Muster kopieren (Name im AP). Angewandte Migrationen nie editieren. DTOs und Frontend-Typen synchron halten.
- Sicherheitsrelevantes (Auth, Sitzungen, Tokens, Proxy-Header, MCP) bekommt immer einen Regressionstest.
- Abschlussbericht: geänderte Dateien, ausgeführte Befehle mit Ergebnis, Abweichungen vom Briefing.

---

### Welle 0

#### P0 – Baseline-Commit (ERLEDIGT)
- **Ziel:** Verifizierte Fixes der ersten Review-Runde sichern.
- **Status:** erledigt, drei Commits auf dem Branch (nicht gepusht): `7f1b1a5` fix(backend): atomic lockout counter, upload limits, error mapping, session compat; `6a06032` fix(frontend): i18n keys, 401 handling, a11y, double-submit guards, nginx hardening; `7103688` test(e2e): compose overlay with mock OIDC and 13-step browser journey.
- [x] Welle-0-Gate vor dem Commit grün (738 Backend- / 54 Frontend-Tests, 13 von 13 E2E-Schritten); Secret-Scan ohne Treffer.
- [x] Working Tree danach sauber (nur diese Plan-Datei untracked).
- **Hashes:** Rollback-Referenz vor diesem Release `5e20b66`; Basis für die Wellen `7103688`.
- **Einzige verbleibende P0-Aktion – Zusatz (kein Datei-Output, Sonnet-Agent vor Welle 1):** `npm audit --omit=dev` in `frontend/` und `e2e/` sowie `mvn -B org.owasp:dependency-check-maven:check` (einmalig per Kommandozeile, Plugin kommt dauerhaft in A12) – Ergebnisse als Liste an den Orchestrator; kritische Treffer werden eigene APs.

---

### Welle 1 (alle parallel)

#### A1 – Throttle-Kern + Limiter-Umzug
- **Ziel:** Wiederverwendbarer Limiter im neutralen Paket plus `LoginThrottle`; noch keine Verdrahtung in die Security-Chain.
- **Dateien (exklusiv):** neu `backend/src/main/java/com/boxcopilot/backend/security/FixedWindowRateLimiter.java` (per `git mv` aus `mcp/abuse/McpRateLimiter.java`), `security/ClientAddress.java`, `security/LoginThrottle.java`, `security/LoginThrottleProperties.java`; geändert `mcp/abuse/McpRateLimits.java`, `McpIpRateLimitFilter`, `McpSubjectRateLimitFilter`, `McpFailedTokenRateLimitFilter` und weitere Importeure (per `grep -rl McpRateLimiter backend/src`), `backend/src/test/.../mcp/abuse/McpRateLimiterTest.java` → `.../security/FixedWindowRateLimiterTest.java`.
- **Schritte:**
  - [ ] Umbenennen/verschieben, Javadoc anpassen (Eviction jetzt `evictStale()` public und zusätzlich lazy: Zähler `calls` im Limiter, alle 256 Aufrufe `evictStale()`; nur CAS-sichere Entfernung wie bisher).
  - [ ] `ClientAddress.of(HttpServletRequest)`: `getRemoteAddr()`, leer → `"unknown"`; IPv6 (enthält `:`) auf die ersten 64 Bit normalisieren (`InetAddress.getByName` nur für IP-Literale, kein DNS), IPv4-mapped IPv6 → IPv4. `McpClientAddress` bleibt unberührt.
  - [ ] `LoginThrottle(Clock, LoginThrottleProperties)`: zwei `FixedWindowRateLimiter` (IP: 10/15 min; IP+Username: 5/15 min, Username `trim().toLowerCase(Locale.ROOT)`, auf 64 Zeichen gekürzt gegen Key-Flut). API: `boolean isBlocked(ip, usernameOrNull)` (nutzt `isExhausted`, nimmt kein Permit), `void recordFailure(ip, usernameOrNull)` (`tryAcquire` beider Keys), `long retryAfterSeconds()` (= Fensterlänge, konservativ). Properties `app.security.login-throttle.{ip-limit,ip-user-limit,window}`.
  - [ ] `Clock`-Bean: `McpConfiguration` definiert `Clock` nur bei MCP. Einen `@ConditionalOnMissingBean(Clock)`-Bean in einer neutralen `SecurityBeansConfiguration` in `security/` anlegen (MCP-Bean hat bereits `@ConditionalOnMissingBean(Clock.class)` – Reihenfolge per Test absichern).
- **Tests:** Muster `McpRateLimiterTest` (mit `MutableTestClock` aus `mcp/`-Tests – verschieben nach `testutil` nur falls nötig, sonst importieren): Fenstergrenze, Parallelität, Eviction, `LoginThrottle` (IP-Limit, IP+User-Limit, anderer Client nicht betroffen, IPv6 /64 gleiche Schlüssel, Username-Normalisierung), `ClientAddress`-Tabelle. `McpArchitectureTest` muss grün bleiben (neue Regel: `security..` hängt nicht von `mcp..` ab – als zusätzlicher Test in `McpArchitectureTest` **nicht** hinzufügen, da fremde Datei; stattdessen in `FixedWindowRateLimiterTest` per ArchUnit-Einzeiler).
- **Verifikation:** `mvn -B -q -Dtest='FixedWindowRateLimiterTest,LoginThrottleTest,McpArchitectureTest,McpRateLimitIntegrationTest' test`, danach `mvn -B -q test`.
- **DoD:** Kein Verweis mehr auf `McpRateLimiter`; MCP-Verhalten unverändert; Limiter hat keinen Scheduler.

#### A2 – Lock-Entfernung + Migration V15
- **Ziel:** Kein Konto kann mehr per Fehlversuchen gesperrt werden; Altlocks gelöscht; ERROR-/WARN-Flut pro gesperrtem Login weg.
- **Dateien (exklusiv):** `backend/src/main/resources/db/migration/V15__clear_account_locks.sql` (neu), `domain/User.java` (Methoden `unlock()`, ggf. `resetFailedAttempts()` nur wenn ungenutzt entfernen; **Spalten-Felder bleiben**), `repository/UserRepository.java` (`recordFailedLogin` entfernen), `service/UserService.java` (nur: `recordFailedLogin`, `unlockUser`, `assertMayLogIn`-Lock-Zweig, `resetFailedAttempts`-Aufrufe), `service/CustomUserDetailsService.java` (eigene Throws und `AccountLockedException`/`DisabledException` entfernen), `config/OAuth2SecurityConfig.java` (nur `authenticationFailureHandler`: kein `recordFailedLogin`, keine Lock-Sonderfälle, Antwort bleibt generisch `Invalid credentials`; Grund-Texte „disabled“ **nicht** mehr preisgeben → ebenfalls generisch, verhindert Account-Enumeration), `controller/AdminUserController.java` (nur `PUT /{id}/unlock` entfernen), Tests: `UserServiceTest`, `OidcLoginRejectionTest`, `CustomOidcUserServiceTest`, `AuthControllerTest`-unabhängig; `UserRepositoryLockoutContract/H2Test/PostgresTest` löschen und durch `UserRepositoryClearLocksPostgresTest` ersetzen.
- **Nicht anfassen:** `McpAccessPolicy` (Lock-Prüfung bleibt, wirkt nie), `UserDTO` (Felder bleiben, Frontend-Sync in FE2).
- **Schritte:**
  - [ ] V15: `UPDATE users SET locked_until = NULL, failed_login_attempts = 0 WHERE locked_until IS NOT NULL OR failed_login_attempts <> 0;`
  - [ ] Zuerst Test, der den Log-Effekt eines Logins gegen deaktiviertes/altgesperrtes Konto beschreibt (Spring-`AbstractUserDetailsAuthenticationProvider` wirft `LockedException`/`DisabledException` aus `isAccountNonLocked()/isEnabled()`); dann Custom-Throws entfernen.
  - [ ] OIDC-Pfad: deaktivierte Nutzer weiterhin abgewiesen (`assertMayLogIn` behält nur den Enabled-Check; `OidcLoginRejectionTest` anpassen).
  - [ ] Grep-Nachweis (`recordFailedLogin|unlockUser|AccountLockedException|/unlock`) in `backend/src` null außerhalb der oben genannten bewusst verbliebenen Stellen.
- **Tests:** V15 gegen **Postgres** (Muster `UserRepositoryLockoutPostgresTest` + `PostgresContainerSupport`; Flyway migriert bis V14 mit gesperrter Zeile, dann V15 – Muster `McpMigrationTest`/`SchemaMatchesFlywayPostgresTest`); `UserServiceTest`: deaktivierter Nutzer bleibt abgewiesen, Lock-Felder werden nie geschrieben.
- **Verifikation:** `mvn -B -q -Dtest='UserServiceTest,OidcLoginRejectionTest,CustomOidcUserServiceTest,UserRepositoryClearLocksPostgresTest,SchemaMatchesFlywayPostgresTest,AdminMagicLinkControllerTest' test`, dann `mvn -B -q test`.
- **DoD:** Endpunkt `/unlock` weg (404), Migration grün auf Postgres, keine Sperrlogik mehr aktiv.

#### A3 – Public-Preview in `BoxService`, Box-Delete räumt Bilder
- **Ziel:** Einziger unauthentifizierter Datenpfad testbar und schlank; Bild-Dateien verwaisen nicht.
- **Dateien (exklusiv):** `controller/PublicPreviewController.java`, `service/BoxService.java`, `mapper/ItemMapper.java`, `dto/BoxPreviewDTO.java` (nur falls nötig), Tests: neu `controller/PublicPreviewControllerTest.java`, `service/BoxServiceTest.java` (erweitern).
- **Schritte:**
  - [ ] **Zuerst** `PublicPreviewControllerTest` gegen das *aktuelle* Verhalten (Muster `PublicItemImageControllerTest`): 200 mit Items und `imageUrl` inkl. `?t=`, 404 bei unbekannter UUID (heute leerer Body), anonymer Zugriff erlaubt, keine IDs fremder Boxen.
  - [ ] `ItemMapper`: gemeinsamer Helper `publicImageUrl(Item)`; `BoxService.getPublicPreview(String uuid)` (`@Transactional(readOnly=true)`, wirft `ResourceNotFoundException`); Controller dünn, ohne Repositories und ohne `@Transactional`.
  - [ ] 404-Body ändert sich auf den Fehler-JSON des `GlobalExceptionHandler`. `frontend/src/pages/PublicPreview.tsx` fängt jeden Fehler heute mit derselben Meldung ab – FE3 stellt auf Statuscode um; Vertrag im Test festschreiben.
  - [ ] `deleteBox`: Pfade der Item-Bilder vor dem Löschen sammeln, nach erfolgreichem Commit (`TransactionSynchronization.afterCommit`) `imageStorageService.deleteImage(path)`; Fehler beim Datei-Löschen nur loggen.
- **Tests:** `BoxServiceTest`: Löschen ruft `deleteImage` je Bild genau einmal, nicht bei Rollback; Preview-Service-Tests.
- **Verifikation:** `mvn -B -q -Dtest='PublicPreviewControllerTest,BoxServiceTest,PublicItemImageControllerTest' test`.
- **DoD:** Controller ohne Repository-Import; URL-Logik nur noch an einer Stelle.

#### A4 – Upload-Härtung
- **Ziel:** Speicher-Erschöpfung durch parallele Uploads verhindern.
- **Dateien:** `service/ImageStorageService.java`, `service/ImageStorageServiceTest.java`.
- **Schritte:** [ ] `Semaphore(2)` (Property `app.image.max-concurrent-uploads`, Default 2) um die Dekodier-/Skalierphase von `saveImage`, `tryAcquire` mit Timeout → `FileStorageException`-Pendant, das im `GlobalExceptionHandler` bereits auf 4xx/5xx abgebildet ist (Status **503** mit `Retry-After` falls kein Mapping existiert – dann Hinweis an Orchestrator statt Handler zu ändern). [ ] `MAX_PIXELS` ≈ 25 MP: Breite×Höhe aus dem Header (`ImageReader.getWidth/getHeight`) *vor* dem Dekodieren prüfen, `InvalidImageException`.
- **Tests:** Muster vorhandene Tests in `ImageStorageServiceTest`: Bild über Pixelgrenze abgelehnt, ohne OOM; dritter paralleler Upload wartet/wird abgewiesen; Semaphore wird auch bei Exception freigegeben.
- **Verifikation:** `mvn -B -q -Dtest=ImageStorageServiceTest,ItemServiceUploadTest test`.
- **DoD:** Permit-Leck ausgeschlossen (try/finally), Test beweist es.

#### A5 – Startup-Scans ersetzen, tote Endpunkte löschen
- **Ziel:** Kein `findAll()` bei jedem Start; ungenutzte, unbegrenzte Endpunkte weg.
- **Dateien (exklusiv):** `service/ItemService.java`, `controller/ItemController.java`, `controller/BoxNumberController.java` (löschen, falls danach leer), `config/BoxNumberMigration.java`, `repository/BoxRepository.java`, `repository/ItemRepository.java`; Tests: neu `service/ItemServiceStartupTest.java`, `config/BoxNumberMigrationTest.java`, Postgres-Test `repository/StartupBackfillQueriesPostgresTest.java`.
- **Schritte:**
  - [ ] **Nicht ersatzlos löschen** (V6/V11 vergeben keine Nummern, V9/V10 keine Tokens, Dev läuft auf H2 ohne Flyway). Stattdessen `ItemRepository.findByImageTokenIsNullOrImageTokenIs("")` bzw. `countByImageTokenIsNull()` und `BoxRepository.findByBoxNumberIsNullOrderByCreatedAt()`; `ensureImageTokens()`/`performMigration()` laufen die Abfrage, bei 0 Treffern sofortiges Return ohne Vollscan. Bei `BoxNumberMigration`: Pool-Reservierung bestehender Nummern nur, wenn überhaupt Boxen ohne Nummer existieren **oder** der Pool leer ist – Verhalten per Test gegen den alten Stand absichern, bevor umgebaut wird.
  - [ ] `GET /api/v1/items` (`ItemController` ohne Pfad) und `getAllItems()` entfernen; `GET /api/v1/box-numbers/status` (`BoxNumberController`) entfernen. Grep-Nachweis: kein Aufrufer in `frontend/src`, `e2e/`, Backend-Tests (README-Eintrag erledigt DOC1).
- **Tests:** H2- und Postgres-Variante nach dem Contract-Muster `ItemRepositoryMcpSearchContract/H2Test/PostgresTest` für die neuen Abfragen (leere Strings, NULLs, Mischbestand); Service-Test: kein `findAll()`-Aufruf (Mockito `verify(never())`).
- **Verifikation:** `mvn -B -q -Dtest='ItemServiceStartupTest,BoxNumberMigrationTest,StartupBackfillQueriesPostgresTest,BoxNumberServiceTest' test`.
- **DoD:** Beide Backfills funktionieren auf Alt-Daten (Test mit NULL-Tokens/-Nummern), Endpunkte liefern 404/401.

#### A6 – MCP-Startup-Guard: Origin-Gleichheit
- **Ziel:** Bei `app.mcp.enabled=true` und abweichenden Origins von SPA-API-URL und `BACKEND_URL` schlägt der Start fehl (heute Endlosschleife im Connect-Flow).
- **Dateien:** `mcp/McpStartupGuard.java`, `mcp/McpProperties.java`; Tests `mcp/McpStartupGuardTest.java`, `mcp/McpStartupGuardContextTest.java`. Die Property `app.mcp.spa-api-base-url` ist neu (kein Java-Code und keine MCP-Property bindet `VITE_API_BASE_URL` bisher; nur `application-prod.yml` nutzt sie für die OIDC-`redirect-uri`, unverändert) und wird per Env `VITE_API_BASE_URL` in `application.yml` gebunden – **diese Zeile übernimmt A8** (Datei gehört dort), A6 liefert nur den Property-Namen und `@Value`/Record-Feld mit Default leer.
- **Schritte:** [ ] Neues Record-Feld, optional. [ ] Wenn gesetzt: Scheme+Host+effektiver Port (Default-Port-Normalisierung, Groß/Klein) von `backendUrl` und `spa-api-base-url` vergleichen; Abweichung → `IllegalStateException` mit beiden Werten. Leer → keine Prüfung (nur Warnhinweis im Log).
- **Tests:** Muster `McpStartupGuardTest`: gleiche Origin mit/ohne Port `:443`, Pfadanteile ignoriert, abweichender Host, leer.
- **Verifikation:** `mvn -B -q -Dtest='McpStartupGuard*' test`.
- **DoD:** Fehlermeldung nennt beide Werte und die Env-Namen.

#### A7 – `AuthController` über `UserService`
- **Ziel:** Layering einhalten.
- **Dateien:** `controller/AuthController.java`, `controller/AuthControllerTest.java`.
- **Schritte:** [ ] `UserRepository` durch `UserService` ersetzen (`findByUsername`, `findByOidcSubject` existieren). [ ] Verhalten byte-gleich (JSON-Vertrag in `JsonContractTest` nicht ändern).
- **Verifikation:** `mvn -B -q -Dtest='AuthControllerTest,JsonContractTest,SpaCsrfFlowTest' test`.
- **DoD:** Kein `repository`-Import mehr im Controller.

#### CI1 – CI-Workflow
- **Ziel:** Erstmals CI (Verzeichnis `.github` fehlt).
- **Dateien:** `.github/workflows/ci.yml` (neu).
- **Schritte:** [ ] Job `backend`: `actions/setup-java` Temurin 21 mit Maven-Cache, `mvn -B verify` in `backend/` (Docker ist auf `ubuntu-latest` für Testcontainers vorhanden). [ ] Job `frontend`: Node 24, `npm ci`, `npx tsc --noEmit`, `npm test -- --run`, `npm run build` in `frontend/`. [ ] Job `e2e` nur `workflow_dispatch` + nächtlicher `schedule`: Compose mit Overlay, Env-Datei aus `secrets`/generierten Zufallswerten, `npm ci && npx playwright install --with-deps chromium && npm test` in `e2e/`, Artefakt-Upload der Screenshots. [ ] Actions auf feste Major-Versionen pinnen, `permissions: contents: read`.
- **Verifikation:** `actionlint`, falls vorhanden; sonst YAML-Syntaxprüfung per `python -c "import yaml,sys; yaml.safe_load(open('.github/workflows/ci.yml'))"`.
- **DoD:** Keine Secrets im File; Job-Namen eindeutig.

#### FE1 – Shell, Theme, a11y, Lazy-Routes
- **Ziel:** App-Rahmen, Barrierefreiheit und Bundle-Größe.
- **Dateien (exklusiv):** `frontend/index.html`, `src/App.tsx`, `src/context/ThemeContext.tsx`, `src/context/LanguageContext.tsx`, `src/components/LanguageSelector.tsx`, `src/i18n/locales/de.ts`, `en.ts`, `src/App.test.tsx`, Löschen von `src/components/ItemsTable.tsx` (vorher `grep -rn "ItemsTable'" src` ≠ `EnhancedItemsTable` → ungenutzt).
- **Schritte:**
  - [ ] `index.html`: `lang`, `<meta name="description">`, `theme-color`; `LanguageContext` setzt `document.documentElement.lang` und `document.title` je Sprache (neue Locale-Keys).
  - [ ] `ThemeContext`: Primärfarbe `#c95f26` verfehlt WCAG AA. Neue Primärfarben wählen und **Kontrast rechnerisch belegen** (Skript im Test oder Kommentar): Weiß auf Primär ≥ 4.5:1 im Light-Modus; Primär-Text auf dunklem Papier ≥ 4.5:1 im Dark-Modus; Hover/Disabled prüfen. Zusätzlich Fokus-Ring (`:focus-visible`) global.
  - [ ] `App.tsx`: Logo als `RouterLink` statt `div`; `ProtectedRoute` mit `<Navigate to="/app/boxes" replace />` statt `navigate()` im Render; `<main>`-Landmark; angemeldeter Benutzername im Desktop-Header; `React.lazy` + `Suspense` für `AdminPanel`, `BoxEditPage`, `BoxList` (Login und `PublicPreview` bleiben eager); `LanguageSelector` ohne Flaggen-Emoji (Text „DE/EN“).
  - [ ] Öffentliche Seite bekommt keinen Mehrgewicht-Import: nach Build `dist/assets` prüfen, Hauptchunk < ~500 kB.
  - [ ] Locale-Parität: `locales.test.ts` (nicht ändern) muss grün bleiben.
- **Tests:** `App.test.tsx` erweitern (Muster dort): Logo ist Link, Nicht-Admin auf `/app/admin` landet via `Navigate` auf Boxen, `lang`/`title` gesetzt.
- **Verifikation:** Frontend-Gate (Abschnitt 5).
- **DoD:** Kontrastwerte im Bericht, Chunk-Größen vorher/nachher im Bericht.

---

### Welle 1 – Gate (Abschnitt 5), dann `graphify update .` durch den Orchestrator.

---

### Welle 2 (alle parallel)

#### A8 – Throttle-Verdrahtung
- **Ziel:** Throttle greift in Form-Login und Magic-Login.
- **Dateien (exklusiv):** `config/OAuth2SecurityConfig.java`, `controller/MagicLoginController.java`, neu `security/LoginThrottleFilter.java`, `src/main/resources/application.yml` (Properties `app.security.login-throttle.*`, `app.mcp.spa-api-base-url: ${VITE_API_BASE_URL:}` für A6 – nur diese neue Property; die bestehende Nutzung in `application-prod.yml` (OIDC-`redirect-uri`) bleibt unverändert), Tests: neu `config/LoginThrottleIntegrationTest.java`, `security/LoginThrottleFilterTest.java`, `controller/MagicLoginControllerTest.java` (erweitern).
- **Schritte:**
  - [ ] `LoginThrottleFilter` (OncePerRequestFilter), `addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)`; Matcher: `POST /api/auth/login` und `POST /api/v1/auth/magic-login`. Bei `isBlocked` → 429, Header `Retry-After`, JSON-Vertrag aus 3.1(2), **ohne** `chain.doFilter`. Username für den IP+User-Key aus dem Formularparameter `username`; für Magic-Login kein Username.
  - [ ] `authenticationFailureHandler` ruft `loginThrottle.recordFailure(ip, username)`; `MagicLoginController` bei 401 `recordFailure(ip, null)`. Erfolgreiche Logins zählen nicht und setzen nichts zurück.
  - [ ] Logging: genau eine `WARN`-Zeile (ohne Stacktrace) beim *Eintritt* in die Sperre, nicht pro blockiertem Request (gegen Log-Flut).
  - [ ] Hinweis in Kommentar: IP korrekt nur, wenn `server.tomcat.remoteip.internal-proxies` (`TRUSTED_PROXY_PATTERN`) die echten Proxies trifft; sonst teilen sich alle Clients einen Bucket.
- **Tests:** Muster `SpaCsrfFlowTest` / `AuthControllerTest` (MockMvc, Profil test; `RealTomcat` aus `web/` für `X-Forwarded-For`-Fälle wie `ForwardedHeadersTrustedPeerTest`):
  - 10 falsche Logins von IP A → 11. Request 429 + `Retry-After`; **IP B loggt sich mit dem richtigen Passwort desselben Kontos erfolgreich ein** (Kernnachweis „keine Fremdsperre“).
  - IP+User-Limit bei 5; IPv6-Adressen desselben /64 teilen den Bucket.
  - 429 erfolgt vor bcrypt (UserRepository-Mock: `verifyNoInteractions` im geblockten Fall).
  - Magic-Login-Floods von einer IP → 429; gespoofter `X-Forwarded-For` von nicht vertrautem Peer wird ignoriert.
  - Bestehende Login-/CSRF-Tests bleiben grün.
- **Verifikation:** `mvn -B -q -Dtest='LoginThrottle*,MagicLoginControllerTest,AuthControllerTest,SpaCsrfFlowTest,OAuth2SecurityConfigTest,McpFlagOff*' test`.
- **DoD:** Vertrag (Status, Header, JSON) exakt wie in 3.1(2); keine DB-Zugriffe im 429-Pfad.

#### A9 – Sitzungs-Invalidierung bei Passwort-Reset
- **Ziel:** Nach `setPassword` sind alle älteren Sitzungen des Nutzers ungültig.
- **Dateien (exklusiv):** `db/migration/V16__add_users_sessions_valid_after.sql`, `domain/User.java`, `service/UserService.java`, `config/SessionRevalidationFilter.java`, `mcp/authserver/McpSessionUserResolver.java` und seine Aufrufer `McpAuthenticationNormalizationFilter`, `mcp/web/McpClientController`, `McpClientBindingValidator`; Tests: `config/SessionRevalidationFilterTest.java`, `service/UserServiceTest.java`, `service/UserMcpAccessRevocationIntegrationTest.java`, `schema/SchemaMatchesFlywayPostgresTest.java`, neu `config/PasswordResetEndsSessionsTest.java`, neu `repository/SessionsValidAfterPostgresTest.java`.
- **Schritte:**
  - [ ] V16: `ALTER TABLE users ADD COLUMN sessions_valid_after TIMESTAMP NULL` (Typ wie `locked_until` in V5 prüfen und dieselbe Konvention nutzen; UTC).
  - [ ] `UserService.setPassword`: nach Hash-Update `sessionsValidAfter = now (Instant→UTC)`; `magicLoginTokenService.invalidateAllTokensForUser(user)`; `eventPublisher.publishEvent(new McpAccessRevokedEvent(id))`.
  - [ ] `SessionRevalidationFilter.isStale`: `request.getSession(false).getCreationTime() < sessionsValidAfter` → stale. Signatur der Methode um `HttpSession` erweitern; Konstruktor unverändert (Instanziierung in `OAuth2SecurityConfig` bleibt).
  - [ ] `McpSessionUserResolver.resolve(Authentication, HttpServletRequest)` mit derselben Prüfung; alte Signatur entfernen und Aufrufer anpassen. Fehlt eine Session → wie bisher unauthentifiziert.
  - [ ] **Zu verifizieren per Test (nicht annehmen):** (a) `changeSessionId()` ändert `getCreationTime()` nicht (Servlet + Redis-Session, vgl. `RedisSessionCompatibilityTest`); (b) Admin resettet *fremdes* Konto → Admin-Sitzung bleibt; (c) Admin resettet *eigenes* Passwort → eigene Sitzung endet; (d) Uhrabweichung: Vergleich mit Toleranz 0, aber `sessionsValidAfter` wird beim Setzen mit dem Anwendungs-`Clock` gebildet (in Tests einfrierbar).
- **Tests:** Muster `SessionRevalidationFilterTest`, `MagicLoginControllerTest`, `McpAccessRevocationIntegrationTest`: Nutzer U hat Sitzung S1 (Form), Magic-Link-Sitzung S2; Admin setzt Passwort → S1/S2 liefern 401 auf `/api/v1/...`; neuer Login danach funktioniert; Magic-Link-Token von vor dem Reset ist ungültig; MCP-Authorize mit alter Sitzung → Zugriff verweigert. Postgres-Test für die neue Spalte (Schema stimmt mit Flyway überein, Lesen/Schreiben).
- **Verifikation:** `mvn -B -q -Dtest='SessionRevalidationFilterTest,PasswordResetEndsSessionsTest,UserServiceTest,SessionsValidAfterPostgresTest,SchemaMatchesFlywayPostgresTest,McpAuthorizationServerIntegrationTest,McpPendingAuthorizationIntegrationTest' test`.
- **DoD:** Alle vier Verifikationspunkte (a)–(d) sind als benannte Tests vorhanden.

#### A10 – Validierungs-Härtung
- **Dateien (exklusiv):** `config/AdminPasswordGuard.java`, `dto/CreateUserDTO.java`, `dto/UpdateUserDTO.java`, `dto/SetPasswordDTO.java`, `controller/AdminUserController.java` (nur `@Valid` und Imports; die Unlock-Entfernung aus A2 ist bereits drin), Tests `config/AdminPasswordGuardTest.java`, `dto/CreateUserDTOTest.java`, neu `dto/UpdateUserDTOTest.java`.
- **Schritte:** [ ] `AdminPasswordGuard`: Mindestlänge 12 (zusätzlich zu „nicht blank“ / „nicht admin“) – gilt für `ADMIN_PASSWORD` beim Start; Meldung nennt die Mindestlänge. [ ] `CreateUserDTO.username`: `@Size(max=100)` (Spaltenlänge in V5 prüfen, nicht darüber), analog `name`. [ ] `UpdateUserDTO`: gleiche Größenbegrenzungen auf nullable Felder, `@Valid` an `PUT /{id}`. [ ] `SetPasswordDTO`: Mindestlänge konsistent zum Frontend (12; Frontend-Test in FE2).
- **Tests:** Muster `CreateUserDTOTest`; Guard-Test: 11 Zeichen → Start abgelehnt, 12 ok; Alt-Datenbank-Check (`run`) bleibt.
- **Verifikation:** `mvn -B -q -Dtest='AdminPasswordGuardTest,CreateUserDTOTest,UpdateUserDTOTest,AdminSeedingOrderTest,StartupGuardContextTest' test`.
- **DoD:** Test-Profile/Fixtures mit kürzeren Admin-Passwörtern angepasst (nur Testdateien in dieser Welle).

#### OP1 – Compose-Härtung
- **Ziel:** Secrets nur dort, wo gebraucht; Ressourcenlimits; Basishärtung.
- **Dateien (exklusiv):** `docker-compose.yml`, `docker-compose.e2e.yml`.
- **Schritte:**
  - [ ] `env_file: .env` aus `postgres` und `frontend` entfernen (sie bekommen `CLIENT_SECRET`, `REDIS_PASSWORD`, `ADMIN_PASSWORD` sonst unnötig). Postgres hat alles in `environment:` (`POSTGRES_USER/PASSWORD/DB`). Frontend braucht zur Laufzeit nichts (OP2 fügt eine explizite Variable hinzu). Backend behält `env_file`.
  - [ ] **Nicht** die expliziten `environment:`-Blöcke streichen: `docker-compose.e2e.yml` nutzt `env_file: !reset []` und verlässt sich darauf; in der Overlay-Datei die `!reset`-Einträge für `postgres`/`frontend` nur entfernen, wenn Basis keinen `env_file` mehr hat (sonst zeigt `docker compose config` Fehler/Leerwerte).
  - [ ] `mem_limit`/`cpus` je Service (Richtwerte: backend `1g`, postgres `512m`, redis `128m`, frontend `128m`; `JAVA_TOOL_OPTIONS` hat `MaxRAMPercentage=75`), `security_opt: [no-new-privileges:true]` für alle, `cap_drop: [ALL]` für `backend`, `frontend` (nginx braucht `CHOWN SETGID SETUID NET_BIND_SERVICE` bei Port 80 als root → bei Bedarf `cap_add` minimal, im Bericht begründen), `read_only: true` + `tmpfs` nur dort, wo es *ohne* OP3-Dockerfile-Änderung läuft (sonst OP3).
  - [ ] Backend-Environment: neu `SPRING_PROFILES_ACTIVE` bleibt; kein neues Secret.
- **Verifikation (ohne Container-Start):** `docker compose --env-file .env.example config -q` und mit Overlay `docker compose --env-file .env.example -f docker-compose.yml -f docker-compose.e2e.yml config -q`; geprüft wird, dass in `config`-Ausgabe `postgres.environment` kein `CLIENT_SECRET` enthält. (Die Befehle sind reine Konfigurationsprüfung, kein `up`.)
- **DoD:** Overlay und Basis rendern fehlerfrei; Diff zeigt keine entfernten Pflicht-Variablen.

#### FE2 – Login + Admin-Panel
- **Ziel:** Login-UX inkl. 429; Admin-Panel ohne Lock-UI, mit Bestätigungen und konsistenter Fehleranzeige.
- **Dateien (exklusiv):** `src/pages/LoginPage.tsx`, `src/pages/AdminPanel.tsx`, `src/pages/AdminPanel.test.tsx`, `src/services/userService.ts`, `src/types/models.ts`, neu `src/services/apiError.ts` (+ `apiError.test.ts`), `src/services/authService.ts` (+ Test), `src/i18n/locales/de.ts`, `en.ts`.
- **Schritte:**
  - [ ] `extractApiError(err, fallback)` in `services/apiError.ts` (nutzt `err.response.data.error|message`, nie rohes `err.message`); die neun Kopien in `AdminPanel.tsx` ersetzen.
  - [ ] Login: 429 → lokalisierte Meldung inkl. `retryAfterSeconds` („Zu viele Fehlversuche, bitte in N Minuten erneut versuchen“, Rundung auf Minuten); Passwort ein-/ausblenden (`aria-label`), `autoCapitalize="off"`, `autoCorrect="off"`; `VITE_API_BASE_URL` undefiniert → klarer Hinweis statt `undefined/api/...`; Sprachumschalter auf der Login-Seite.
  - [ ] Admin: Lock-Badge, Filter-Option „locked“, Entsperren-Menü/-Button, `userService.unlockUser` und `lockedUntil`/`failedLoginAttempts` im Typ `User` **entfernen** (Backend-DTO behält die Felder, Frontend ignoriert sie – im Kommentar vermerken); Rollenwechsel mit Bestätigungsdialog; Fehler nicht hinter Dialogen (Snackbar `zIndex` über Dialog, Fehlermeldungen ≥ 6 s); Dialoge `fullScreen` auf Mobil; Passwort-Mindestlänge 12 inline.
  - [ ] Passwort-Reset-Erfolgsmeldung: Hinweis „Aktive Sitzungen des Nutzers wurden beendet“ (Locale-Key).
- **Tests:** Muster `AdminPanel.test.tsx` und `authService.test.ts`: 429-Meldung gerendert, Rollenwechsel verlangt Bestätigung, kein Entsperren-Element mehr, `extractApiError` ignoriert `err.message`.
- **Verifikation:** Frontend-Gate.
- **DoD:** `grep -rn "unlock\|lockedUntil" src` leer (außer Kommentar).

---

### Welle 2 – Gate, dann `graphify update .`.

---

### Welle 3 (alle parallel)

#### A11 – AuthZ-Regressionstests (nur neue Testdateien)
- **Dateien (alle neu):** `controller/AdminUserControllerTest.java`, `controller/ItemControllerTest.java`, `controller/BoxControllerTest.java`, `service/ItemServiceTest.java`.
- **Inhalt:** `AdminUserControllerTest` (Muster `AdminMagicLinkControllerTest`): `USER` erhält 403 auf *allen* Routen unter `/api/v1/admin/users` (GET, GET {id}, POST, PUT, PUT password, POST/DELETE magic-link, DELETE), Admin kann sich nicht selbst löschen, `/unlock` existiert nicht mehr. `ItemControllerTest`/`BoxControllerTest` (Muster `AuthControllerTest`): anonym → 401 auf jede Route, Erfolgs-Smoke mit Nutzer. `ItemServiceTest` (Muster `ItemServiceUploadTest`): Move/Bulk-Move, Bild löschen/ersetzen räumt Datei (`deleteImage`).
- **Verifikation:** `mvn -B -q -Dtest='AdminUserControllerTest,ItemControllerTest,BoxControllerTest,ItemServiceTest' test`.
- **DoD:** Tests prüfen den *Status*, nicht nur Nichtauftreten einer Exception; keine Produktionsdatei geändert.

#### A12 – Ops-Backend: Actuator, SameSite, OWASP, Prod-Property-Check
- **Dateien (exklusiv):** `backend/pom.xml`, `application.yml`, `application-prod.yml`, `config/OAuth2SecurityConfig.java`, `config/StartupGuard.java`, Tests: neu `config/ActuatorExposureTest.java`, `config/SessionCookieConfigTest.java`, `config/StartupGuardTest.java` (erweitern).
- **Schritte:**
  - [ ] `spring-boot-starter-actuator`; `management.endpoints.web.exposure.include=health`, `management.endpoint.health.show-details=never`, Health-Gruppen ohne Redis/DB-Details nach außen; `/actuator/health` (und `/actuator/health/**`) `permitAll` in `securityFilterChain`; **alle anderen** `/actuator/**` bleiben `authenticated()` bzw. nicht exponiert (Test: `/actuator/env` → 404/401).
  - [ ] MCP-Metrik-Kollision prüfen: `McpConfiguration.mcpMeterRegistry` darf mit Actuator nicht doppelt auftreten (`McpToolMetrics`-Tests, `BoxPilotMcpToolsObservabilityTest`).
  - [ ] `application-prod.yml`: `server.servlet.session.cookie.same-site: ${SESSION_COOKIE_SAME_SITE:none}` (O3); `secure: true` bleibt.
  - [ ] `StartupGuard` (Prod): fehlt eine Pflicht-Property (`CLIENT_ID`, `CLIENT_SECRET`, `NEXTCLOUD_URL`, `NEXTCLOUD_LOGOUT_URL`, `FRONTEND_URL`, `REDIS_PASSWORD`, DB-URL) → *eine* Fehlermeldung mit allen fehlenden Namen statt Folgefehlern; bestehende Prüfungen nicht verändern.
  - [ ] OWASP: `org.owasp:dependency-check-maven` in einem Profil `security-scan` (nicht im Default-Build, damit `mvn verify` offline/schnell bleibt); CI1-Job erhält den Aufruf später in DOC1/Release-Phase.
- **Verifikation:** `mvn -B -q -Dtest='ActuatorExposureTest,SessionCookieConfigTest,StartupGuard*,OAuth2SecurityConfigTest,BoxPilotMcpToolsObservabilityTest,McpFlagOff*' test`.
- **DoD:** `curl /actuator/health` im Test → `{"status":"UP"}` ohne Details; Konfiguration kommentiert.

#### X1 – Share-Link-Rotation (optional, Owner-Entscheidung O1)
- **Ziel:** `POST /api/v1/boxes/{id}/rotate-share` rotiert `box.uuid` und die Image-Tokens aller Items in **einer** Transaktion.
- **Dateien:** `controller/BoxController.java`, `service/BoxService.java`, `repository/ItemRepository.java`, `repository/BoxRepository.java`, Tests `service/BoxServiceTest.java`, `controller/BoxControllerRotateShareTest.java`, `repository/ShareRotationPostgresTest.java`.
- **Schritte:** [ ] Service-Methode `rotateShareLinks(Long id)` (`@Transactional`): neue UUID, neue Tokens (`UUID.randomUUID()`), Antwort mit neuer UUID. [ ] Falls Bulk-JPQL/Native-SQL: gegen Postgres testen. [ ] Alte `/public/{uuid}` → 404, alte Image-Token-URLs → 404 (Test gegen `PublicPreviewControllerTest`/`PublicItemImageControllerTest`-Muster).
- **Verifikation:** `mvn -B -q -Dtest='BoxServiceTest,BoxControllerRotateShareTest,ShareRotationPostgresTest,PublicPreviewControllerTest' test`.
- **DoD:** Authentifizierung nötig (anonym 401, USER nur eigene Rechte laut bestehendem Box-Modell – Modell vor Implementierung im Bericht benennen).

#### OP2 – Frontend-CSP und HSTS (envsubst-Template)
- **Ziel:** CSP und HSTS auf dem Frontend-Host; Backend-Origin erst zur Containerstartzeit bekannt.
- **Dateien (exklusiv):** `frontend/Dockerfile`, `frontend/nginx.conf` → `frontend/nginx.conf.template`, `frontend/security-headers.inc` → `frontend/security-headers.inc.template`, `docker-compose.yml`, `docker-compose.e2e.yml`.
- **Schritte:**
  - [ ] Offizielles nginx-Image rendert `/etc/nginx/templates/*.template` per `envsubst` nach `/etc/nginx/conf.d/` (nur *definierte* Variablen; nginx-eigene `$uri` etc. bleiben unberührt). Dateien als `default.conf.template` und `security-headers.inc.template` kopieren; `COPY nginx.conf /etc/nginx/conf.d/default.conf` entfällt.
  - [ ] Compose `frontend.environment`: `API_ORIGIN: ${VITE_API_BASE_URL}` (nur Origin; falls die URL einen Pfad hat, im Bericht melden). Die bestehende Durchreichung von `VITE_API_BASE_URL` an den Backend-Container (OIDC-`redirect-uri` in `application-prod.yml`) bleibt unverändert.
  - [ ] Header: `Strict-Transport-Security: max-age=31536000; includeSubDomains` (nur sinnvoll hinter TLS – Traefik terminiert TLS; im E2E-Overlay auf http **nicht** setzen: Variable `HSTS_ENABLED`/zweites Include-Snippet); CSP: `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob: ${API_ORIGIN}; font-src 'self' data:; connect-src 'self' ${API_ORIGIN}; frame-ancestors 'none'; base-uri 'self'; form-action 'self'`; `X-Frame-Options` auf `DENY`. @react-pdf: Schriften/Blobs in `src/utils/pdfFonts.ts` (nur lesen) prüfen; fehlende `blob:`/`data:`-Quellen ergänzen (`worker-src blob:` falls nötig). Danach CSP-Verstöße im E2E per Konsole prüfen (Schlussphase).
  - [ ] **Erst `Content-Security-Policy-Report-Only`**, im Schluss-E2E auf Verstöße prüfen, dann scharf schalten (Variable `CSP_MODE`); Go/No-Go entscheidet über scharf/Report-Only.
- **Verifikation (ohne Start):** Dockerfile baut nicht in diesem Task; `docker compose ... config -q`; zusätzlich Template-Syntax mit `nginx -t` **nur** in der Schlussphase.
- **DoD:** Kein `$`-Konflikt (alle nginx-Variablen unverändert), Header-Include in allen Locations beibehalten (nginx-Regel zu `add_header`-Vererbung).

#### FE3 – PublicPreview, BoxList, Tabelle
- **Dateien (exklusiv):** `src/pages/PublicPreview.tsx`, `src/services/publicPreviewService.ts`, `src/pages/BoxList.tsx`, `src/hooks/useBoxListLogic.ts`, `src/components/EnhancedItemsTable.tsx`, `src/i18n/locales/de.ts`, `en.ts`, neue Tests `src/pages/PublicPreview.test.tsx`, `src/hooks/useBoxListLogic.test.tsx`.
- **Schritte:**
  - [ ] `PublicPreview`: 404 → „Link ungültig oder widerrufen“, Netzwerk-/5xx-Fehler → „Vorübergehender Fehler“ mit Wiederholen-Button; App-Bar mit App-Namen/-Icon auch im Fehlerzustand; Foto-Klickziele als `ButtonBase` mit `aria-label`.
  - [ ] `useBoxListLogic`: `loadError`-State mit Retry; echter Leerzustand („Noch keine Kisten“) mit Call-to-Action „Kiste anlegen“; `BoxList`: untere Padding-Zone, damit Mobile-FABs die letzte Karte nicht überlappen; Karten-Kopf und „aufklappen“-Zeile als Buttons mit `aria-expanded`; Foto-Klick (`BoxList.tsx:~383`) und `EnhancedItemsTable.tsx:~261` als `ButtonBase`; Pluralformen („1 Gegenstand“); Lösch-Bestätigungen nennen Kisten-/Gegenstandsnamen.
- **Tests:** Muster `useMagicLogin.test.tsx` (Hook) und `AdminPanel.test.tsx` (Seite): Fehlerzustand, Retry, Leerzustand.
- **Verifikation:** Frontend-Gate.
- **DoD:** Kein `|| 'English fallback'` mehr in angefassten Dateien.

---

### Welle 3 – Gate, dann `graphify update .`.

---

### Welle 4 (alle parallel)

#### A13 – Principal verschlanken (optional, O6)
- **Ziel:** Bcrypt-Hash und `User`-Entity liegen nicht mehr in der Session.
- **Dateien (exklusiv):** `service/CustomUserDetailsService.java`, `config/OAuth2SecurityConfig.java` (Success-Handler `principal.getUser()` → `principal.getId()`), `controller/MagicLoginController.java`, `config/SessionRevalidationFilter.java`, `mcp/authserver/McpSessionUserResolver.java`, `controller/AuthController.java`, zugehörige Tests (`grep -rln "CustomUserPrincipal\|getUser()" backend/src`).
- **Schritte:** [ ] `CustomUserPrincipal` hält nur `id`, `username`, `role`, `enabled`-Snapshot (kein Passwort-Hash; `getPassword()` liefert `null` – `eraseCredentials` ist bei Spring bereits üblich, hier aber nicht verlassen). [ ] Login-Prüfung benötigt den Hash weiterhin: Spring lädt über `loadUserByUsername`; dafür separaten Eingangs-Typ (`UserDetails` mit Hash) und danach `AuthenticationProvider`-seitiges `eraseCredentials` bzw. Principal-Wechsel nach erfolgreicher Authentifizierung (Variante im Bericht begründen, ggf. `eraseCredentials(true)` + Test, dass `session`-Serialisierung keinen Hash enthält). [ ] `serialVersionUID` erhöhen.
- **Tests:** `RedisSessionCompatibilityTest` (Muster): Session des alten Formats ist unlesbar → toleranter Serializer, kein 500, Re-Login; neuer Test: serialisierte Session enthält keinen `$2a$`-Hash.
- **Verifikation:** `mvn -B -q -Dtest='RedisSessionCompatibilityTest,RedisConfigTest,SessionRevalidationFilterTest,OAuth2SecurityConfigTest,AuthControllerTest,MagicLoginControllerTest' test`, dann Modul.
- **DoD:** Nachweis-Test „kein Hash in Session“. Wenn nicht stabil, streichen und ins Backlog (kein Release-Blocker).

#### OP3 – Non-root (O2), Actuator-Healthcheck, Versions-Bump
- **Dateien (exklusiv):** `backend/Dockerfile`, `docker-compose.yml`, `docker-compose.e2e.yml`, `backend/pom.xml` (nur `<version>`/`finalName`).
- **Schritte:** [ ] `USER 10001:10001` im Dockerfile (Gruppe/User sind schon angelegt), `COPY --chown`. [ ] Compose: `read_only: true`, `tmpfs: [/tmp]`, `cap_drop: [ALL]`, `no-new-privileges`; Daten: benannte Volumes für Bilder/Logs **oder** (Bind-Mounts beibehalten) dokumentiertes einmaliges `chown -R 10001:10001 ./logs ./images` (Entscheidung nach O2). [ ] Healthcheck von TCP-Probe auf `/actuator/health` umstellen (ohne curl: `bash -c` mit `/dev/tcp` und HTTP-Request-String, Antwort auf `"status":"UP"` prüfen) – gleiches im Dockerfile-`HEALTHCHECK`. [ ] Version 0.2.0: Jar-Name aus Dockerfile entkoppeln (`finalName` fix `backend` oder `ARG`).
- **Verifikation (ohne Start):** `docker compose ... config -q`; `mvn -B -q -DskipTests package` in `backend/` (Jar entsteht unter erwartetem Namen). Echter Start erst in der Schlussphase.
- **DoD:** Overlay-Pfade und Volumes konsistent; Rollback-Hinweis (`user: "0:0"` per Override) im Kommentar.

#### FE4 – ItemForm, Upload, BoxEdit, Share-UI
- **Dateien (exklusiv):** `src/components/ItemForm.tsx`, `ItemImageUpload.tsx`, `ItemMoveDialog.tsx`, `LabelGenerationProgress.tsx`, `BoxForm.tsx`, `src/pages/BoxEditPage.tsx`, `src/services/boxService.ts`, `itemService.ts`, `src/types/models.ts`, `de.ts`, `en.ts`, Tests `ItemForm.test.tsx`, `BoxEditPage.test.tsx`.
- **Schritte:** [ ] `ItemForm`: `<form>` (Enter speichert), „Speichern und nächstes“ (Dialog bleibt offen, Felder leeren, Fokus auf Namen), `fullScreen` auf Mobil, Fehler über `extractApiError` (kein rohes `err.message`). [ ] `ItemImageUpload`: Fehlgeschlagener Upload → Warnung + Wiederholen statt Erfolgspfad; Foto entfernen im Bearbeiten-Modus erst beim Speichern (oder Bestätigungsdialog) statt sofortigem Server-Delete. [ ] `BoxEditPage`: Leerzustand, Aktionen „Etikett drucken“ und „Zurück“, klickbare Divs → Buttons. [ ] **Nur mit X1:** Button „Link neu erzeugen“ mit Warndialog („Gedruckte QR-Labels werden ungültig“), `boxService.rotateShare`.
- **Tests:** Muster `AdminPanel.test.tsx`: Enter-Submit, „Speichern und nächstes“, Fehler-Upload zeigt Warnung, Rotation nur nach Bestätigung.
- **Verifikation:** Frontend-Gate.
- **DoD:** Kein direktes `err.message` im UI.

#### DOC1 – Doku
- **Dateien (exklusiv):** `README.md`, `.env.example`, `docs/ops/backup-restore.md` (neu), `docs/ops/mcp-server.md` (nur Abschnitte zu Limiter/Origin), `docs/releases/0.2.0.md` (neu), `CLAUDE.md` (Root, `backend/`, `backend/src/main/java/com/boxcopilot/backend/config/`, `service/`, `controller/`).
- **Inhalt:** README: Endpunktliste bereinigen (`GET /api/v1/items` weg, `/unlock` weg, `rotate-share` falls X1), Throttling-Abschnitt (Limits, 429, `Retry-After`, **In-Process, ein Replica, Reset bei Neustart**, Abhängigkeit von `TRUSTED_PROXY_PATTERN`), Sitzungs-Invalidierung bei Passwort-Reset, Upgrade-Abschnitt (siehe 6), Traefik-Beispiel bereinigen (Zeilen ~269–395; nur lesen was nicht stimmt, nichts erfinden), SameSite-Hinweis. `.env.example`: `POSTGRES_PORT` löschen (ungenutzt, nur dort vorhanden), `SESSION_COOKIE_SAME_SITE`, `API_ORIGIN`-Hinweis, Mindestlänge `ADMIN_PASSWORD`. `backup-restore.md`: `pg_dump` **und** Bilder-Verzeichnis zusammen sichern (gleicher Zeitpunkt), Restore-Reihenfolge, Test-Restore. Release-Notes-Entwurf: Highlights, Breaking/Upgrade-Schritte, Migrationsnotizen (V15, V16 forward-only), bekannte Einschränkungen.
- **Verifikation:** Links/Dateipfade per `grep`; keine Platzhalter.
- **DoD:** Alle Aussagen decken sich mit dem Code des Gates (Orchestrator liest Stichproben).

---

### Welle 4 – Gate, dann `graphify update .`.

---

## 5. Qualitätstore pro Welle

Ein Sonnet-Verifikationsagent (kein Edit-Recht) führt aus und berichtet Rohergebnisse; bei Rot geht der Orchestrator zurück an das verursachende AP (Zuordnung per Dateibesitz) und wiederholt das Gate.

| Gate | Backend (`backend/`) | Frontend (`frontend/`) | Zusatz |
|---|---|---|---|
| Welle 0 (bestanden) | `mvn -B -q verify` – **738 Tests grün, Skipped = 0** | `npx tsc --noEmit && npm test -- --run && npm run build` – 54 Tests grün | Docker Desktop läuft (Testcontainers); E2E 13 von 13 Schritten grün |
| Welle 1 | `mvn -B -q verify`; Testzahl ≥ 738 + neue; ArchUnit `McpArchitectureTest` grün | wie oben; Hauptchunk < ~500 kB | `grep -rn McpRateLimiter backend/src` leer; Flyway-Folge V1..V15 lückenlos bis auf V2 |
| Welle 2 | wie oben (V16 auf Postgres angewendet) | wie oben | 429-Vertrag in Test belegt; `docker compose config -q` (Basis + Overlay) |
| Welle 3 | wie oben | wie oben | `/actuator/health` UP ohne Details; `/actuator/env` nicht erreichbar |
| Welle 4 | wie oben | wie oben | `mvn -B -q -DskipTests package` erzeugt erwartetes Jar; `docker compose config -q` |

Zusätzlich je Gate: `git status` darf nur Dateien der laufenden Welle zeigen (Prüfung der exklusiven Besitzrechte); `npm audit --omit=dev` und `mvn -B -P security-scan org.owasp:dependency-check-maven:check` (ab Welle 3) werden nur berichtet, Treffer ≥ HIGH blockieren das Gate von Welle 4.

---

## 6. Abschlussphase (nach Gate Welle 4)

Ausführung auf dem Owner-Rechner in **WSL2** (Docker Engine dort). Orchestrator-Schritte als Sonnet-Agenten, Go/No-Go durch den Owner.

### 6.1 E2E1 – Suite erweitern (Sonnet-Agent; Dateien `e2e/smoke.mjs`, `e2e/README.md`)

Neue Schritte (Muster: bestehende `step(...)`-Struktur, `allow(re, status)` für erwartete 4xx, `newCtx()` je simuliertem Client):
- [ ] **Throttle:** E2E-Overlay setzt kleine Limits (z. B. `APP_SECURITY_LOGINTHROTTLE_IPLIMIT=5`, im Overlay-File nicht in OP1 – Env-Zeile in `docker-compose.e2e.yml` von E2E1 **erlaubt zu ändern**). Zehnmal falsches Passwort von „Client A“ (`X-Forwarded-For: 203.0.113.10`; das Docker-Gateway ist laut Regex vertrauter Proxy, daher wird der Header im Overlay akzeptiert) → 429 + `Retry-After`; **danach loggt „Client B“ (`203.0.113.20`) mit dem richtigen Passwort desselben Kontos erfolgreich ein**; UI zeigt lokalisierte 429-Meldung (kein rohes Englisch). Dieser Schritt läuft vor dem Cleanup und nach allen Schritten, die den gemeinsamen Standard-Client-Bucket nutzen.
- [ ] **Magic-Link-Throttle:** wiederholte falsche Tokens → 429.
- [ ] **Passwort-Reset beendet Sitzung:** Nutzer N loggt in Kontext 1 ein; Admin (Kontext 2) setzt N ein neues Passwort; Kontext 1 `GET /api/v1/me` → unauthentifiziert/401, UI zeigt Login; Admin-Sitzung bleibt aktiv; N loggt mit neuem Passwort ein; alter Magic-Link ungültig.
- [ ] **Kein Konto-Lock:** Admin-Panel zeigt kein Gesperrt-Badge; `PUT /admin/users/{id}/unlock` → 404.
- [ ] **Öffentliche Fehlerseite:** `/public/<zufällige-uuid>` → App-Bar mit Markenname, Text „Link ungültig“, Statuscode 404; kein Retry-Button; simulierter 5xx (Route per `page.route`) → Retry-Button.
- [ ] **Security-Header:** Frontend-Antwort enthält CSP (oder `-Report-Only`), `X-Content-Type-Options`, bei HTTPS HSTS; keine CSP-Konsolenverstöße beim Durchlauf der gesamten Suite (Konsolen-Listener existiert, Filter `Content Security Policy`).
- [ ] **Container-Härtung (falls O2 = ja):** `docker exec boxpilot-e2e-backend id -u` = 10001 (Prüfung im Schlussskript, nicht im Browser); Upload und Bildabruf funktionieren weiterhin; `read_only`-Root-FS hat keine Schreibfehler im Log.
- [ ] **Share-Rotation (falls X1):** Link rotieren → alter `/public/<uuid>` 404, neuer 200.
- [ ] **Admin-Passwort-Policy:** zu kurzes Passwort (<12) im Panel inline abgelehnt (bestehender Schritt 8 erweitern).
- [ ] **Upload-Pixellimit:** Bild > 25 MP (generiertes PNG-Header-Bild, `crc`-Helper existiert) wird abgelehnt, vorheriges Foto bleibt (Schritt 5 erweitern).
- **Verifikation:** `node --check e2e/smoke.mjs`; Lauf erst in 6.2.

### 6.2 Start und Lauf

- [ ] Env-Datei aus `.env.example` mit Zufallswerten (Admin-Passwort ≥ 12 Zeichen, `REDIS_PASSWORD`, `CLIENT_SECRET` Dummy) **außerhalb des Repos** erzeugen.
- [ ] Sauberer Start: `docker compose -p boxpilot-e2e --env-file <env> -f docker-compose.yml -f docker-compose.e2e.yml down -v`, dann
  `docker compose -p boxpilot-e2e --env-file <env> -f docker-compose.yml -f docker-compose.e2e.yml up -d --build --wait`.
- [ ] Erwartung: alle Services `healthy`; Backend-Healthcheck über Actuator; Flyway meldet V15/V16 angewendet.
- [ ] **Upgrade-Rehearsal:** zusätzlich einmal mit einer Datenbank im Stand *vor* diesem Release (V14, gesperrter Benutzer, Item ohne Token, Box ohne Nummer): `pg_dump` des Altstands einspielen, neues Image starten → V15 löscht Lock, Backfills laufen, bestehende Browser-Sitzung bei Redis-Altformat führt zu *einem* Re-Login ohne 500.
- [ ] `cd e2e && npm ci && npx playwright install chromium && E2E_USERNAME=… E2E_PASSWORD=… npm test` → alle Schritte grün (Screenshots sichten).
- [ ] CSP: bei `Report-Only` Verstöße auswerten; keine Verstöße → scharf schalten und Suite wiederholen.

### 6.3 Log-Prüfung

- [ ] `docker compose -p boxpilot-e2e … logs backend | grep -E "ERROR|Exception"` – nach dem Lauf **keine unerwarteten** ERROR-Zeilen. Erwartete: keine. Insbesondere: kein Stacktrace pro blockiertem/deaktiviertem Login, keine `SessionRepository`-Deserialisierungsfehler (außer der einmaligen, tolerierten Altsession-WARN), keine `ImageStorageService`-Fehler.
- [ ] Ergebnis der `grep`-Prüfung und Log-Größe protokollieren (Log-Flut-Check: 200 Fehlversuche erzeugen < 10 Logzeilen).
- [ ] `docker compose … down -v` nach Abschluss; keine Reste (`docker volume ls | grep boxpilot-e2e`).

### 6.4 Release-Checkliste

- [ ] Alle Gates grün, E2E vollständig grün, Logprüfung sauber, `npm audit`/OWASP ohne offene HIGH/CRITICAL (oder dokumentierte Ausnahme).
- [ ] **Versions-Bump** `0.1.0` → Zielversion (O7) in `backend/pom.xml`, `frontend/package.json` (`version`), Release-Notes-Datei.
- [ ] **Release-Notes** (`docs/releases/0.2.0.md`) mit **Upgrade-Schritten**: (1) `ADMIN_PASSWORD` ≥ 12 Zeichen setzen; (2) `TRUSTED_PROXY_PATTERN` prüfen (sonst teilen alle Clients einen Throttle-Bucket); (3) bei Non-root: `chown -R 10001:10001 ./logs ./images` vor `up`; (4) `docker compose pull/build`, `up -d`; (5) alle Nutzer müssen sich einmal neu anmelden (Session-Format-Änderung, A13); (6) neue Env-Variablen (`SESSION_COOKIE_SAME_SITE`, `API_ORIGIN` via Compose, `VITE_API_BASE_URL`-Origin muss mit `BACKEND_URL` übereinstimmen, wenn MCP aktiv – die Variable war bereits Pflicht für die OIDC-`redirect-uri`; neu ist nur die Bindung an `app.mcp.spa-api-base-url`, das `redirect-uri`-Verhalten ändert sich nicht).
- [ ] **Migrationshinweise:** V15 (Locks gelöscht, irreversibel), V16 (neue nullable Spalte); Flyway vorwärts-only → **vor dem Upgrade `pg_dump` + Bilder-Backup** (`docs/ops/backup-restore.md`).
- [ ] Baseline-Commits (`7f1b1a5`, `6a06032`, `7103688`; Rollback-Referenz `5e20b66`) + Wellen-Commits geprüft (Commit-Messages, keine Secrets, `git status` sauber, `graphify update .` gelaufen).
- [ ] **Tagging:** `git tag -a v0.2.0 -m "BoxPilot 0.2.0"` erst nach Go; Image-Tags `boxpilot-backend:0.2.0` / `:latest` nur lokal/registry laut Owner; Tag nicht ohne Owner-OK pushen.
- [ ] Rollback-Plan im Release-Notes-Anhang (Abschnitt 7).

### 6.5 Go/No-Go (Owner)

Der Orchestrator legt dem Owner eine Ein-Seiten-Zusammenfassung vor: Gate-Ergebnisse je Welle, E2E-Schritte (Anzahl/Status), Log-Befund, Audit-Befund, offene Punkte/Backlog, getroffene Owner-Entscheidungen O1–O7. **Release erst nach ausdrücklichem „Go“ des Owners im Chat.** No-Go-Kriterien: rotes Gate, fehlschlagender E2E-Schritt zur Throttle-/Reset-Kernfunktion, unerklärte ERRORs im Log, offene CRITICAL-Funde. Optional kappbar ohne No-Go: A13, X1/Share-UI, OP3-Non-root, CSP scharf.

---

## 7. Risiken und Rollback

| Risiko | Wirkung | Gegenmaßnahme / Rollback |
|---|---|---|
| Flyway ist vorwärts-only (V15 löscht Locks, V16 fügt Spalte hinzu) | Rückkehr auf Vorversion nur mit DB-Backup | `pg_dump` vor Upgrade (Pflicht in Release-Notes). V16-Spalte ist nullable und für altes Backend harmlos; V15-Datenverlust (Locks) ist gewollt. Rollback = altes Image + ggf. Restore. |
| Session-Format ändert sich (A13) | Einmaliger Re-Login für alle | Toleranter Serializer (`RedisConfig`) → kein 500; in Release-Notes ankündigen. Notfalls A13 streichen. |
| Throttle ist In-Process | Mehrere Replicas teilen keine Zähler; Neustart setzt zurück | Ein Replica dokumentieren; Verteilung (Redis) im Backlog. |
| Falsch konfiguriertes `TRUSTED_PROXY_PATTERN` | Alle Clients erscheinen als Proxy-IP → ein Angreifer sperrt per IP-Limit *alle* | Startup-Warnung/Hinweis in README; E2E-Overlay prüft Header-Verhalten; Limits konfigurierbar. |
| Erschöpftes IP-Kontingent blockiert auch richtiges Passwort | NAT/Büro-IP temporär gesperrt (15 min) | Bewusst; Limits per Property anpassbar; Admin-Hinweis in Doku. |
| Passwort-Mindestlänge 12 bricht Start | Prod startet nicht mit kurzem `ADMIN_PASSWORD` | Klare Fehlermeldung + Upgrade-Schritt (O5). |
| Non-root (OP3) und Bind-Mount-Rechte | Schreibfehler auf `./images`/`./logs` | `chown`-Schritt; Override `user: "0:0"` als Rollback. |
| CSP bricht @react-pdf/Bilder | Etikettendruck/Bilder fehlen | Erst `Report-Only`, E2E prüft Konsole, dann scharf. |
| Session-Revalidierung ohne Cache | Zusätzlicher DB-Zugriff je Request (besteht schon) | Kein neues Risiko; Backlog: Cache. |
| `sessions_valid_after` vs. Uhrabweichung mehrerer App-Knoten | Falsch-stale Sitzungen | Ein Knoten; sonst UTC-Zeit + NTP; Test mit eingefrorenem `Clock`. |
| Paralleler Agentenzugriff auf `graphify-out/` | Beschädigter Graph | Nur der Orchestrator führt `graphify update .` je Gate aus. |

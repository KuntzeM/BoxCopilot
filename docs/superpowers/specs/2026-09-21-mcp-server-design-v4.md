# BoxPilot MCP-Server – Design (v4)

Status: Entwurf v4 nach Review-Diskussion Hugo/Udo. Ersetzt v3 (`2026-09-21-mcp-server-design.md`), das unverändert als Vorstufe bestehen bleibt. Datum: 2026-09-21.

Grundlage sind v3, die drei Diskussionsrunden zwischen Sicherheitsreview (Hugo) und Architekturreview (Udo) und ein erneuter Abgleich mit dem Code. Alle Entscheidungen sind getroffen. Offene Prüfpunkte stehen als Spike-Liste in Phase 0 (Abschnitt 7). Was sich gegenüber v3 geändert hat und warum, steht in Abschnitt 10.

## 1. Ziel und Abgrenzung

Ein in das Spring-Boot-Backend integrierter MCP-Server (Model Context Protocol), über den ein KI-Agent (Claude) BoxPilot-Daten **nur lesend** abfragen kann:

- Items suchen und ermitteln, in welchen Boxen sie liegen
- Bild eines Items abrufen
- Boxen suchen
- Inhalt einer Box (Items) abrufen

Nicht Teil von v1: Schreibzugriffe, Dynamic Client Registration (DCR, laut neuer MCP-Spec-Revision deprecated), Client-ID-Metadata-Documents (CIMD), Verbindungs-UI über den kleinen Client-ID-Dialog hinaus, ChatGPT-Support (wird im Spike geprüft).

Zielclients: Claude Free/Pro (Einrichten im Web/Desktop, danach auch Mobile-App), Claude Code. Claude Free erlaubt einen Custom Connector, der zwingend OAuth 2.1 nutzt. Statische Header-Auth (`static_headers`) ist Org-Admin-Beta und für Free nicht verfügbar.

Klarstellung zum Datenmodell: BoxPilot kennt keine Mandanten. Weder `Box` noch `Item` haben eine Owner-Beziehung, jeder aktive Nutzer sieht das gesamte Inventar. `mcp_allowed` und die Client-Bindung (2.1) sind deshalb **keine Mandantentrennung**, sondern begrenzen nur, wer eine Claude-Verbindung aufbauen darf und wem eine Verbindung gehört.

## 2. Entscheidungen

| Thema | Entscheidung |
|---|---|
| Hosting | Im bestehenden Backend, Package `com.boxcopilot.backend.mcp`. Package-Grenze per ArchUnit-Test abgesichert: Nur `mcp` darf `mcp` nutzen, `mcp` darf Services nutzen, nicht Controller |
| Transport | Spring AI 1.1.x `spring-ai-starter-mcp-server-webmvc`, `protocol=STATELESS`, Endpoint `/mcp` |
| Authorization Server | Eingebaut: Spring Authorization Server (SAS, Boot-verwaltet, `spring-boot-starter-oauth2-authorization-server`) |
| Token-Format | **Opaque Reference-Tokens** (`accessTokenFormat(REFERENCE)`), kein JWT, keine JWKS, kein Signing-Key. AS und Resource Server sind dieselbe Anwendung mit derselben Datenbank |
| Resource Server | Stock Spring Security `oauth2-resource-server` mit `opaqueToken()` und eigenem `OpaqueTokenIntrospector`. **Kein** `mcp-security` (Community-WIP) |
| Zentrale Zugriffskontrolle | `McpAuthorizationService` (Decorator um `OAuth2AuthorizationService`) plus `McpAccessPolicy`. Eine Stelle für Access-Token, Refresh-Token, Code und Widerruf |
| Client-Registrierung | **Ein vorregistrierter Public Client pro freigeschaltetem Nutzer**, Client-ID zufällig, an genau eine `userId` gebunden. Kein DCR, kein CIMD |
| Login im Authorize-Schritt | Bestehende Frontend-Login-Seite (SPA), Rücksprung über ein zustandsloses Cookie `mcp_authz` |
| Bild-Ausgabe | MCP-`ImageContent` (Base64-JPEG), Zugriff über `itemId` |
| Feature-Flag | `app.mcp.enabled`, Default `false`. Aus: keine MCP-Beans, keine MCP-Chains, `/mcp` und die Authorize-/Token-Pfade sind nicht erreichbar (kein 2xx, keine Daten) |
| Zugriff | Nur vom Admin freigeschaltete User (`users.mcp_allowed`, Default `false`) und nur über die eigene Client-ID |

### 2.1 Freischaltung und Client-Bindung (Review K1/H1)

Das Grundproblem aus v3 bleibt der Ausgangspunkt: Bei einem gemeinsamen öffentlichen Client kann ein Angreifer mit eigenem Claude-Account eine gültige Authorize-URL (mit seinem `state` und PKCE) an ein BoxPilot-Opfer schicken. Bestätigt das Opfer den Consent, erhält die Claude-Session des Angreifers Lesezugriff auf das Inventar. Ein `mcp_allowed`-Flag verkleinert nur den Kreis der Opfer, es verhindert den Angriff nicht.

Ein Browser- oder Session-Binding auf BoxPilot-Seite hilft nicht: Die URL wird von claude.ai erzeugt und ist für BoxPilot identisch, egal ob der Angreifer sie öffnet oder das Opfer. Der erste Kontakt mit BoxPilot ist in beiden Fällen der Browser des Opfers. Einzige Größe, die Claude-Session und BoxPilot verbindet, ist die Client-ID, die der Nutzer selbst im Connector-Dialog einträgt. Deshalb:

- Jeder freigeschaltete User hat **einen eigenen Public Client**. Die Client-ID ist zufällig (mindestens 128 Bit aus `SecureRandom`, Base64url) und in den `client_settings` als `boxpilot.user_id` an genau eine `userId` gebunden. Redirect-URIs sind bei allen Clients dieselben, Scope ist `mcp:read`.
- **Authorize-Validator** (im Authorization-Code-Request-Provider, siehe 4.3) und **Decorator** (`McpAccessPolicy`) prüfen die Bindung: Ist der eingeloggte User nicht der gebundene, wird der Authorize abgelehnt. Der Decorator prüft zusätzlich bei jedem Zugriff, dass der `principalName` der Authorization dem gebundenen User des Clients entspricht.
- Ein Angreifer kann damit nur seine eigene Client-ID verwenden. Öffnet das Opfer den Link, ist der eingeloggte User nicht der gebundene, der Authorize scheitert. Der Angriff funktioniert nur, wenn das Opfer selbst die Client-ID des Angreifers in seinen Claude-Connector eintippt, und dann ist der Ablauf ohnehin der des Angreifers.
- Bereitstellung: `POST /api/v1/me/mcp-client` (Session, CSRF-Token, nur bei `mcp_allowed`) legt den Client **lazy und idempotent** an und liefert die Client-ID. `POST /api/v1/me/mcp-client/regenerate` erzeugt einen neuen Client und löscht den alten samt Authorizations („Neu erzeugen“, etwa nach Verdacht auf Leck).
- Entzug von `mcp_allowed`, Deaktivieren (`enabled=false`) oder Löschen des Users löscht Client **und** alle Authorizations dieses Users. Die **temporäre Sperre** (`lockedUntil`, automatisch nach 5 Fehlversuchen, `User.java:180-183`) löscht **nichts**: Sie wird nur von der `McpAccessPolicy` bei jedem Zugriff abgewiesen. Sonst könnte ein Angreifer durch absichtliche Fehlversuche die Verbindung des Opfers samt Client-ID zerstören (gezielter Account-DoS, Client-ID müsste neu im Connector eingetragen werden).
- Die Client-ID gilt als vertraulich (nicht als Geheimnis im Sinne eines Passworts). Sie wird von BoxPilot **nie geloggt** und nie in Audit-Zeilen geschrieben. Sie steht aber im Query des Authorize-Aufrufs und damit in Traefik-Access-Logs und Browser-Verlauf des Nutzers: Access-Logs sind als vertraulich zu behandeln bzw. ohne Query-String zu führen (Betriebsleitfaden).
- **Restrisiko:** Gelangt die Client-ID eines Users an einen Angreifer (Screenshot, geteilte Einstellungen), fällt der Schutz für diesen User auf das v3-Niveau zurück. Gegenmaßnahme: „Neu erzeugen“.
- **Login-CSRF-Bewertung:** Der Angreifer kann durch einen Cross-Site-Login das Opfer im Browser als Angreifer anmelden. Dann würde das Opfer mit der Client-ID des Angreifers einen Consent für die Daten des Angreifers geben, ein Nullschaden. Der auf der Consent-Seite angezeigte Benutzername bleibt dennoch die letzte Verteidigung. Zusätzlich entfallen die CSRF-Ausnahmen für Login und Magic-Link (4.4), damit Login-CSRF nicht mehr möglich ist.
- Aufwand: kleiner SPA-Dialog („Claude-Verbindung“) mit Anzeige und Kopieren der Client-ID, Button „Neu erzeugen“ und „Alle Verbindungen widerrufen“. Ein User-Einstellungsbereich existiert im Frontend bisher nicht (nur `AdminPanel.tsx` und der Menübereich in `App.tsx`), der Dialog wird dort angehängt. Das Admin-Panel bekommt den Schalter `mcp_allowed`.

## 3. Architektur

### 3.1 Komponenten (`com.boxcopilot.backend.mcp`)

- `BoxPilotMcpTools`: dünne `@McpTool`-Methoden, delegieren an bestehende Services über eigene Lese-Services (3.3).
- `McpSecurityConfig`: drei getrennte `SecurityFilterChain`s mit explizitem `@Order` und `securityMatcher` (4.2).
- `McpAuthorizationServerConfig`: `AuthorizationServerSettings` (fester Issuer), `TokenSettings` (REFERENCE, TTLs), JDBC-Stores für Clients und Authorizations, No-Op-Consent-Service, Consent-Seite, Validator für die Client-Bindung, Beschränkung der SAS-Endpunkte.
- `McpAuthorizationService`: Decorator um `JdbcOAuth2AuthorizationService`. `save()` stempelt beim ersten Speichern einer Authorization das Attribut `boxpilot.started_at` (Epoch-Sekunden als `Long`, wird von SAS über alle Rotationen hinweg kopiert). `findByToken()`, `findById()` und alle Lesepfade wenden `McpAccessPolicy` an und liefern bei Verstoß `null`.
- `McpAccessPolicy`: entscheidet fail-closed. Sie prüft, ob der User existiert, aktiviert und nicht gesperrt ist, `mcp_allowed` gesetzt hat, ob die Client-Bindung passt und das absolute Verbindungsalter (`started_at` plus `app.mcp.max-connection-age`, Default 14 Tage) noch nicht überschritten ist. Kein Cache: jeder Aufruf lädt den User frisch. Jeder Fehler (Datenbank, unbekannter Client, fehlendes Attribut) führt zur Ablehnung. Zwei Einstiegspunkte: `checkAuthorization(authorization)` für den Decorator und `requireActiveUser(userId)` für die Tools (3.4).
- `McpOpaqueTokenIntrospector`: Implementierung von `OpaqueTokenIntrospector` über `findByToken(token, ACCESS_TOKEN)` des Decorators. Prüft `Token.isActive()` (Ablauf und Invalidierung), liefert einen `OAuth2AuthenticatedPrincipal` mit Name = `userId`, Authorities `SCOPE_<scope>` aus den autorisierten Scopes. Refresh-Tokens werden als Bearer abgelehnt, weil der Lookup typgebunden auf `ACCESS_TOKEN` läuft.
- `McpAuthenticationNormalizationFilter`: Filter in der AS-Chain vor dem Authorize-Provider (4.3).
- `McpTransportContextExtractor`: legt die `userId` aus dem authentifizierten Principal in den `McpTransportContext`.
- `McpPendingAuthorizationController` und `McpPendingCookies`: Cookie `mcp_authz` und `GET /api/v1/mcp/continue` (4.1).
- `McpClientController`: `POST /api/v1/me/mcp-client`, `POST /api/v1/me/mcp-client/regenerate`, `DELETE /api/v1/me/mcp-connections` (alle meine Verbindungen widerrufen).
- `McpAuthorizationCleanup`: `@Scheduled`, löscht abgelaufene und invalidierte Authorizations (4.3).
- `McpTextSanitizer`, `McpAuditLogger`, `McpRateLimiter` (In-Memory-Zähler, ohne Fremdbibliothek).

Alles unter `@ConditionalOnProperty(app.mcp.enabled)`. Zusätzlich wird die Autokonfiguration des Spring-AI-Starters an dieselbe Flag gebunden: `spring.ai.mcp.server.enabled=${app.mcp.enabled:false}` in `application.yml`. Ohne diese Zeile würde die Starter-Autokonfiguration den `/mcp`-Endpoint auch bei ausgeschalteter Flag registrieren, und er läge hinter der Session-Chain der App.

Beim Start schlägt die Anwendung fehl, wenn `app.mcp.enabled=true` und

- `BACKEND_URL` fehlt (in Prod zusätzlich: kein `https`),
- keine Redirect-URI konfiguriert ist,
- Profil `test` aktiv ist.

Tests für MCP laufen mit einem eigenen Profil (`mcp-test`) mit echter Chain-Konfiguration, nie mit `NoSecurityConfig`. `NoSecurityConfig` und `application-test.yml` wandern nach `src/test` (4.4), damit sie nicht im Produktions-Jar stehen.

Abhängigkeiten: Bekannte Fragilität ist die Kopplung von Boot 3.5, Spring Security 6.5 / SAS 1.5 und Spring AI 1.1. Der Upgrade-Pfad führt auf Boot 4, Spring Security 7 (SAS geht dort in den Core über) und Spring AI 2.x, ein koordinierter Sprung. Die Eigenbauten stützen sich bewusst nur auf öffentliche Interfaces (`OAuth2AuthorizationService`, `OpaqueTokenIntrospector`, `RegisteredClientRepository`, Filter, Validatoren), um diesen Sprung klein zu halten.

### 3.2 Tools (alle `readOnlyHint`, Limit max. 50, Query max. 100 Zeichen, leere Query abgelehnt)

| Tool | Ergebnis |
|---|---|
| `search_items(query, limit)` | Items: `itemId`, Name, Box (Nummer, Räume), `hasImage` |
| `get_item_image(itemId, size = thumbnail \| large)` | `ImageContent` JPEG plus Text (Name, Box). Ohne Bild: Textantwort. Default `thumbnail` |
| `search_boxes(query?, room?, limit)` | Boxen per Nummer, Raum, Beschreibung: Nummer, Räume, Flags, `itemCount` |
| `get_box_contents(boxNumber)` | Box-Details plus alle Items (`itemId`, Name, `hasImage`) |

Tool-Beschreibungen weisen darauf hin, dass zurückgegebene Namen und Beschreibungen untrusted Daten sind.

### 3.3 Datenschicht

- Eigene schlanke Ausgabe-DTOs. Ausgeliefert werden nur `itemId`, Boxnummer, Räume, Flags, Namen, gekürzte Beschreibung (500 Zeichen). **Nie** `imageToken`, `imageUrl` oder **Box-UUID**.
- Grund: `GET /api/v1/public/{uuid}` (`PublicPreviewController`) liefert ohne Authentifizierung Box-Details, alle Items und alle Bild-Token-URLs zu einer Box-UUID. Die UUID darf deshalb nicht an einen KI-Anbieter gehen. Boxen werden über die Boxnummer adressiert.
- Die bestehende Suche (`ItemService.searchItems`, `ItemRepository`) ist unbegrenzt und liefert DTOs mit `imageToken`/UUID. Sie wird nicht durchgereicht. Neu: `Pageable`-Suchmethoden in `ItemRepository` und `BoxRepository` (Boxnummer, `currentRoom`, `targetRoom`, `description`), `findByBoxNumber`, Fetch-Join gegen N+1, `itemCount` per Zählabfrage.
- LIKE-Wildcards: Abgeleitete Spring-Data-Methoden (`...ContainingIgnoreCase`) escapen `%` und `_` nach unserer Kenntnis selbst (im Spike zu bestätigen). Eigene `@Query`-Methoden müssen mit `ESCAPE` arbeiten und den Suchbegriff escapen.
- Bilder über `ImageStorageService` (`getImage`/`getLargeImage`, per `itemId`). Fehlerfälle (kein Bild, Datei fehlt) liefern eine generische Meldung ohne Pfade. Dass Bilder beim Upload durch Thumbnailator neu kodiert werden (EXIF-Daten entfallen), ist eine gewollte Eigenschaft und wird als Absicht dokumentiert.
- Tool-Fehler laufen nicht über `GlobalExceptionHandler`, sondern werden explizit generisch gehalten. Zusätzlich wird der Handler selbst generisch (4.4).

### 3.4 Nutzeridentität im Tool (Review H6)

Spring AI führt Tool-Aufrufe nicht zwingend im Request-Thread aus. `SecurityContextHolder` (ThreadLocal) kann leer sein. Deshalb:

- Die `userId` kommt ausschließlich aus dem `McpTransportContext`, befüllt vom `McpTransportContextExtractor` aus dem bereits authentifizierten Principal.
- Jede Tool-Methode ruft `requireActiveUser(context)`, das dieselbe `McpAccessPolicy` nutzt wie der Decorator. Fehlender Kontext oder unbekannter, deaktivierter oder gesperrter User, fehlendes `mcp_allowed` löst eine Exception aus. Es gibt keinen Default-User.
- Spike-Test: Tool-Aufruf ohne Kontext scheitert.

## 4. Authentifizierung und Autorisierung

### 4.1 Ablauf (OAuth 2.1, Authorization Code + PKCE S256)

1. Client ruft `POST /mcp` ohne Token, Antwort `401` mit `WWW-Authenticate: Bearer resource_metadata="<BACKEND_URL>/.well-known/oauth-protected-resource/mcp", scope="mcp:read"` (eigener Entry-Point, der den Header schreibt).
2. Client lädt Protected-Resource-Metadata (RFC 9728, `authorization_servers: ["<BACKEND_URL>"]`, `scopes_supported: ["mcp:read"]`, kleines statisches JSON) und Authorization-Server-Metadata (RFC 8414). Der Client verwendet die vom Nutzer eingetragene Client-ID.
3. Client startet `/oauth2/authorize` mit `code_challenge_method=S256`. Ein mitgeschicktes `resource` wird nicht ausgewertet (es gibt genau einen Resource Server, das Token gilt nur hier).
4. Nicht eingeloggt: Der **Entry-Point der AS-Chain** speichert nichts in der Session. Er setzt das Cookie `mcp_authz` und leitet auf `<FRONTEND_URL>/`.
   - Cookie: Wert `base64url(Query-String)` (maximal 2 KB, sonst 400), `HttpOnly`, `Secure`, `Path=/`, `Max-Age=600`. `SameSite` wird wie beim Session-Cookie gesetzt (in Prod heute `none`, `application-prod.yml`). Nur bei Frontend und Backend in derselben Site genügt `Lax`, da sonst das XHR der SPA nach `/api/v1/me` das Cookie nicht mitsendet.
   - „Latest wins“: Ein neuer Aufruf überschreibt das Cookie. Es gibt keine Map und keinen serverseitigen Zustand, damit keine anonymen Sessions in Redis (Flut-Schutz).
5. Der Login kann über Nextcloud-OIDC, Passwort oder Magic-Link erfolgen. Alle drei enden mit einem Neuladen der SPA auf der Startseite. Der Login selbst kennt den Rücksprung nicht.
   - **Passwort:** `LoginPage` sitzt inline im `ProtectedRoute`, nach Erfolg `window.location.reload()`.
   - **Nextcloud:** `defaultSuccessUrl(frontendUrl, true)` führt auf die Startseite der SPA, danach lädt die SPA `/api/v1/me`.
   - **Magic-Link:** `/magic`-Route (4.4) navigiert nach erfolgreichem POST auf `/`.
6. `/api/v1/me` liefert für eingeloggte User das Feld `mcpPending` (`true`, wenn das Cookie `mcp_authz` vorhanden ist). Für anonyme Aufrufer gibt es diese Information nicht. Ist es `true`, navigiert die SPA **top-level** auf `GET <BACKEND_URL>/api/v1/mcp/continue`.
7. `continue` verlangt eine authentifizierte Session (sonst Redirect auf Frontend), liest das Cookie, löscht es (`Max-Age=0`) und leitet auf `<BACKEND_URL>/oauth2/authorize?<Query>`. Die Ziel-URL wird aus der Konstante `BACKEND_URL` und dem dekodierten Query gebaut, es wird nie eine vom Client gelieferte URL als Ziel benutzt. Der dekodierte Query wird auf druckbares ASCII ohne `#` geprüft. Der Aufruf hat als GET einen Nebeneffekt (Cookie verbrauchen), der aber harmlos ist: Das Ergebnis erfordert weiterhin die Consent-Seite mit POST und CSRF-Token.
8. Der Authorize-Endpunkt lädt den User **frisch aus der DB** (nicht aus der Session) und lehnt deaktivierte, gesperrte oder nicht für MCP freigeschaltete User (`mcp_allowed = false`) ab. Der Validator prüft die Client-Bindung (2.1), **aber nur bei bereits authentifiziertem Principal**: SAS ruft den Validator vor der Login-Prüfung auf. Ein anonymer Aufruf muss zum Entry-Point (Schritt 4) durchlaufen, sonst gäbe es nie einen Login. Die Bindung wird beim Replay nach dem Login geprüft.
9. Eigene Consent-Seite (immer, siehe 4.3), dann Authorization Code (Lebensdauer 60 Sekunden), dann `/oauth2/token`.
10. Access-Token: opak (REFERENCE), **30 Minuten**. Refresh-Token: Rotation (`reuseRefreshTokens(false)` explizit), Idle-Fenster **7 Tage** (`refreshTokenTimeToLive`). Zusätzlich gilt ein **absolutes Verbindungsalter** von **14 Tagen** (`app.mcp.max-connection-age`, konfigurierbar), ab `boxpilot.started_at`. Danach ist Re-Consent nötig. Bei jedem Refresh und bei jedem Zugriff greift `McpAccessPolicy`.

Das absolute Alter ist nötig, weil SAS bei jeder Rotation ein Refresh-Token mit frischer Laufzeit ausstellt. Ohne eigene Grenze wäre die Kette gleitend und ein gestohlener, rotierter Token nie ablaufend. Spike: Bestätigung dieses Verhaltens und dass das Attribut `boxpilot.started_at` über Rotationen erhalten bleibt (Fallback: `authorization_code_issued_at` aus der Authorization).

Es gibt kein `max_age` und keine erzwungene Login-Frische. Spring AS setzt `max_age` nicht selbst um, Magic-Link-User könnten sich nach dem Einmal-Link-Fix nicht erneut anmelden, und Nextcloud re-authentifiziert per SSO stumm. Die bewusste Zustimmung erfolgt über die Consent-Seite und die Client-Bindung.

Scope: Der Introspector liefert `SCOPE_mcp:read`, die Resource-Chain verlangt `hasAuthority("SCOPE_mcp:read")`. Sendet Claude im Authorize kein `scope`, setzt ein kleiner Converter den einzigen Scope als Default (Spike).

### 4.2 Filter-Chains

1. **Authorization-Server-Chain** (`@Order(1)`). `securityMatcher` nur auf die benötigten Endpunkte: `/oauth2/authorize`, `/oauth2/consent`, `/oauth2/token`, `/oauth2/revoke`, `/.well-known/oauth-authorization-server`. Alle anderen SAS-Endpunkte (Introspection, Device-Authorization, PAR, OIDC-Registrierung) liegen außerhalb des Matchers und sind damit nicht erreichbar. OIDC bleibt aus. Session, CSRF an, Header `X-Frame-Options: DENY`. Enthält den Entry-Point (Cookie setzen) und den `McpAuthenticationNormalizationFilter`.
2. **Resource-Server-Chain** (`@Order(2)`, `securityMatcher`: `/mcp`, `/mcp/**`, `/.well-known/oauth-protected-resource/**`). Stateless, `opaqueToken()`, keine Cookies, kein CORS, CSRF aus (nur Bearer). Protected-Resource-Metadata ist `permitAll`, `/mcp/**` verlangt `SCOPE_mcp:read`.
3. **Bestehende App-Chain** (`@Order(3)`, bekommt dafür ein `@Order`) bleibt fachlich unverändert (Session + CSRF). Hygiene: `permitAll` auf `/oauth2/**` (`OAuth2SecurityConfig`) wird auf `/oauth2/authorization/**` verengt. Damit fallen `/mcp` und alle unbekannten `/oauth2`-Pfade in `anyRequest().authenticated()`.

Bei ausgeschalteter Flag gibt es nur die App-Chain. Erwartetes Verhalten: `/mcp` und die Authorize-/Token-Pfade sind **nicht erreichbar** (kein 2xx, keine Daten). Anonym führt das zu einem Redirect auf das Frontend (Entry-Point der App-Chain), mit Session zu 404, weil kein Handler registriert ist (Voraussetzung: `spring.ai.mcp.server.enabled` folgt der Flag). Eine explizite `denyAll`-Regel ist nicht nötig.

Kein `oauth2ResourceServer` auf der App-Chain: Ein MCP-Bearer-Token wirkt nie an `/api/**`, eine Session wirkt nie an `/mcp`.

### 4.3 Sicherheitsmaßnahmen

**Token, Issuer und Bindung**
- Fester `issuer` aus der Pflicht-Konfiguration `BACKEND_URL` (Startabbruch, wenn leer). Nie aus Request oder `X-Forwarded-*` abgeleitet.
- Kein `aud` und keine `resource`-Auswertung: Opaque Tokens sind nur gegen den eigenen Authorization-Store gültig. Ein Token dieses AS kann bei keinem anderen Resource Server verwendet werden, ein anderes Ziel gibt es nicht.
- `sub`/Principal-Name ist die **interne User-ID** (immutable). Username und OIDC-Subject sind änderbar bzw. kollidierbar und dürfen nicht als Identität dienen. Keine Rollen in Token-Authorities übernehmen, nur `SCOPE_mcp:read`.
- Der `McpAuthenticationNormalizationFilter` sitzt in der AS-Chain vor dem Authorize-Provider und gilt für den Authorize und für den Consent-POST. Er tauscht die Session-Authentication (heute `CustomUserPrincipal` mit JPA-`User` samt Passwort-Hash oder `OAuth2AuthenticationToken` mit OIDC-Daten) gegen ein schlankes `UsernamePasswordAuthenticationToken` mit `org.springframework.security.core.userdetails.User(username = userId, leeres Passwort, keine Authorities)`. Dabei lädt er den User frisch aus der DB und prüft `mcp_allowed`. Für diese Klassen liefert Spring Security die Jackson-Mixins bereits mit, ein `McpPrincipal` mit Eigen-Mixins ist nicht nötig. Der Austausch darf die Session-Authentication der SPA nicht überschreiben (Explicit-Save, im Spike zu prüfen).

**Client-Registrierung**
- Registrierte Clients werden zur Laufzeit pro User angelegt (2.1). Public Client (`client_authentication_method=none`), nur `authorization_code` und `refresh_token`, PKCE Pflicht (`plain` abgelehnt), kein `client_credentials`, `requireAuthorizationConsent=true`.
- Redirect-URIs per **exaktem String-Match** und aus Konfiguration. Claude: `https://claude.ai/api/mcp/auth_callback`.
- Claude Code (optional): fest konfigurierter Loopback-Port (`http://127.0.0.1:<PORT>/callback`, Aufruf mit `--callback-port`). Keine Wildcard-Ports.
- Der AS ruft nie URLs ab, die ein Client liefert (kein `jwks_uri`, `logo_uri`, kein CIMD). Damit entfällt die SSRF-Klasse.

**Consent**
- Consent wird bei **jeder** Autorisierung verlangt. Ein No-Op-`OAuth2AuthorizationConsentService` (`findById` liefert `null`, `save` verwirft) verhindert das Überspringen durch gespeicherte Zustimmung. Es gibt keine Consent-Tabelle.
- Die Seite wird **serverseitig im Backend** als statisches HTML gerendert (kein Template-Framework nötig, HTML-Escaping für Namen). Header: `Content-Security-Policy: default-src 'none'; form-action 'self'; frame-ancestors 'none'`, `X-Frame-Options: DENY`.
- Inhalt: allgemeiner Client-Name („Claude-Verbindung“), Redirect-Host, angemeldeter User (Benutzername, wichtig als Login-CSRF-Verteidigung), Scope in Klartext, Hinweis auf die Datenübertragung an den KI-Anbieter (Namen, Beschreibungen, Bilder), Warntext („Nur bestätigen, wenn Sie diese Verbindung gerade selbst in Claude gestartet haben“), CSRF-Token, „Ablehnen“-Button. Uhrzeit und IP werden nicht angezeigt (kein Nutzen gegen Phishing, die IP steht im Audit-Log).
- Der Consent-POST geht an das Backend mit gültigem CSRF-Token.
- Die bisherigen CSRF-Ausnahmen für `/api/auth/login` und `/api/v1/auth/magic-login` (`OAuth2SecurityConfig`) entfallen (Phase 0b, unabhängig von der MCP-Flag, 4.4), die SPA holt vorher `/api/v1/csrf`, wie `LoginPage.tsx` das schon tut.

**Sitzung, Nutzer und Widerruf**
- Bei jedem `/mcp`-Aufruf und jedem Refresh: `McpAccessPolicy` (frisch aus der DB, fail-closed), bei Verstoß `401`.
- Deaktivieren und Löschen eines Users sowie Entzug von `mcp_allowed` löschen den Client und alle Authorizations. Eine temporäre Sperre (`lockedUntil`) löscht nicht, die Policy weist sie bei jedem Zugriff ab (siehe 2.1). Dafür gibt es eine JDBC-Methode `deleteByPrincipal(userId)` (SAS kennt das nicht). Rollenwechsel tut das nicht (Tokens tragen keine Rollen, Tools sind rollenunabhängig).
- Refresh-Token-Reuse-Detection wird in v1 **nicht** gebaut: Spring AS speichert keine Token-Familie, der Eigenbau ist nicht atomar. Stattdessen: Rotation, Idle-Fenster 7 Tage, absolutes Alter 14 Tage, Re-Consent. Restrisiko dokumentiert (Abschnitt 8).
- Nutzer-Widerruf in v1: nur „alle meine Verbindungen widerrufen“ (`DELETE /api/v1/me/mcp-connections`). Eine Anzeige der Verbindungen entfällt, sie wäre eine Query auf `oauth2_authorization` (Nutzer, Client, `started_at`) und kann später ohne neue Tabelle ergänzt werden. Kill-Switch: `app.mcp.enabled=false` deaktiviert alles, die Zeilen in `oauth2_authorization` lassen sich per Betriebsleitfaden löschen.
- `@Scheduled`-Bereinigung (`McpAuthorizationCleanup`, täglich): löscht Authorizations, deren Refresh-Token abgelaufen ist (bzw. deren Access-Token abgelaufen ist, falls es keinen Refresh-Token gibt), deren `started_at` älter als das absolute Alter ist, sowie abgelaufene unbenutzte Codes. SAS räumt selbst nichts auf.

**Schlüssel und Persistenz**
- **Kein Signing-Key, keine JWKS.** Opaque Tokens brauchen weder Schlüsseldatei noch Rotation noch `kid`. Ob SAS ohne `JWKSource`-Bean startet, wird im Spike geprüft. Falls SAS eine Bean erzwingt, wird ein ungenutzter Schlüssel im Speicher erzeugt, der nie signiert und nirgends veröffentlicht wird (`/oauth2/jwks` ist nicht im Matcher).
- JDBC-Stores (Flyway): `oauth2_registered_client`, `oauth2_authorization`. Refresh- und Access-Token liegen dort im Klartext, in derselben DB wie alle Inventardaten, daher kein Hashing in v1 (Backups verschlüsseln).
- Die Referenz-DDL von SAS verwendet `varchar(4000)`, was für `attributes` und Token-Metadaten zu klein ist. In der Migration: `text` und `bytea` (Postgres). Ein Test mit Testcontainers-Postgres ist Pflicht, da die bestehenden Tests nur H2 nutzen.
- SAS serialisiert den Principal und die Attribute in `oauth2_authorization.attributes` mit Jackson-Allowlist. `boxpilot.started_at` wird deshalb als `Long` gespeichert.
- Flyway `validate-on-migrate` ist heute `false` (`application-prod.yml`). Selektiv für neue Tabellen geht nicht, nur global. Vor dem Aktivieren wird `flyway validate` einmal auf Staging gegen die echte Historie geprüft (V11 heißt „repair“). Schlägt es fehl, bleibt es aus und die Abweichung wird dokumentiert.

**Missbrauchsschutz**
- Rate-Limit **pro Token-`sub`** im App-Layer als einfacher In-Memory-Zähler (ein Backend, keine Fremdbibliothek), nicht pro IP (Anthropic-Egress teilen sich alle Claude-Nutzer). Startwerte, konfigurierbar: 60 Aufrufe pro Minute, 200 `get_item_image`-Aufrufe pro Tag. Große Base64-Antworten (`large`) sind der Kostentreiber.
- IP-Limit auf `/oauth2/authorize` und `/oauth2/token` (In-Memory, Startwert 30 pro Minute), gegen Flut und Missbrauch. Die IP zählt nur, wenn die Weiterleitungs-Header vertrauenswürdig sind (4.4, M4).
- Opaque Tokens kosten pro Aufruf eine DB-Abfrage, auch für ungültige Bearer-Tokens. Deshalb zählen fehlgeschlagene Token-Lookups an `/mcp` pro IP (In-Memory, Startwert 30 pro Minute, nur Fehlschläge; gültige Aufrufe laufen über das `sub`-Limit).
- Das bestehende Account-Lockout (5 Fehlversuche, 1 h) ermöglicht gezielten Account-DoS und wird nicht verschärft.
- Volumen-Anomalien werden geloggt, nicht aktiv gedrosselt.

**Freitext und Prompt-Injection**
- Item-Namen und Box-Beschreibungen sind untrusted. `McpTextSanitizer` entfernt Steuer-, Zero-Width- und Bidi-Zeichen und kürzt Längen. Der Server erzeugt keine URLs in Ausgaben. Ausgabe als strukturiertes JSON mit klar getrennten Feldern, Tool-Beschreibung weist auf untrusted Daten hin.
- Restrisiko (dokumentiert): Nutzt der Anwender im selben Chat weitere Connectors mit Außenkanal, kann Freitext den Agenten zu Datenabfluss verleiten. Bilder können Text-Injection enthalten.

**Logging und Observability**
- Audit pro Aufruf: `sub`, Tool, Trefferzahl, IP. **Nie** Tokens, `Authorization`-Header und **Client-ID** loggen. Debug-Logging der Security-Pakete in Prod aus.
- Da der Magic-Link-Token nicht mehr im Query steht (4.4), entfällt das Log-Scrubbing für `token=`. Das Token-Präfix wird nirgends mehr geloggt.
- Micrometer-Zähler pro Tool und Ergebnisklasse (`ok`, `denied`, `rate_limited`, `error`). Er ist sichtbar, sobald Actuator/Prometheus im Betrieb ist. Es entsteht kein neuer Endpunkt.

### 4.4 Voraussetzungs-Fixes im Bestandscode

Alle Punkte im Code geprüft. Die Fixes von **Phase 0b** sind Pflicht vor MCP. Der Rest sind eigenständige Tickets.

**Phase 0b (Pflicht)**

1. **Magic-Link einmalig, sicher und ohne Seiteneffekt im GET.** `MagicLoginTokenService.validateToken` prüft `used`, setzt es nie (`markAsUsed()` wird nirgends aufgerufen). Der GET-Endpunkt `AuthController.magicLogin` hat einen Seiteneffekt, den Link-Vorschauen auslösen, und der Token steht in URL, Referer und Logs. Neu:
   - Link `<FRONTEND_URL>/magic#t=<token>`. Das Fragment wird nie an Server, Proxy oder Referer geschickt.
   - Die SPA-Route `/magic` liest das Fragment, entfernt es sofort per `history.replaceState`, holt `/api/v1/csrf` und sendet `POST /api/v1/auth/magic-login` mit dem Token im Body. Die Seite setzt `Referrer-Policy: no-referrer` (nginx-Header und Meta-Tag).
   - Der POST hat **keine** CSRF-Ausnahme (die SPA sendet das CSRF-Token). Die Ausnahme für `/api/auth/login` entfällt ebenfalls.
   - Backend: Token und User laden, `enabled` und `lockedUntil` prüfen (fehlt heute, `MagicLoginTokenService.java:124`), dann atomar verbrauchen: `UPDATE … SET used=true WHERE token=? AND used=false AND expiry > now()`, mit Prüfung der betroffenen Zeilen (1 = Erfolg).
   - `changeSessionId` nach der Authentifizierung (Session-Fixation, `AuthController.java:195-196`) oder regulärer Spring-Authentifizierungsmechanismus. Der Sicherheitskontext wird über den `SecurityContextRepository` gespeichert.
   - Der alte GET-Endpunkt entfällt. Vor dem Umstellen ausgegebene Links (24 h Laufzeit) werden ungültig, das ist akzeptiert.
   - Maximum für `expiresInHours` (Startwert 72). Kein Token-Präfix mehr in Logs (`AuthController.java:168`, `MagicLoginTokenService.java:99/104`).
   - `MagicLoginTokenService.resolveBaseUrl` fällt bei leerem Wert nicht mehr auf den Request-Kontext zurück, sondern bricht den Start ab. Die Basis-URL ist jetzt die Frontend-URL (`FRONTEND_URL`, Pflicht).
2. **OIDC-Login prüft `enabled`/`locked`.** `UserService.createOrUpdateOidcUser` setzt `lastLogin` und löscht Fehlversuche, ohne Zustand zu prüfen (`UserService.java:256-267`). Prüfung ergänzen, deaktivierte oder gesperrte Nextcloud-User scheitern.
3. **`NoSecurityConfig` und `application-test.yml` nach `src/test`.** Sie liegen heute in `src/main` (Profil `test`) und sind damit im Produktions-Jar. Zusätzlich Startabbruch, wenn `test` neben `prod` (oder einer Postgres-Datasource) aktiv ist, unabhängig von MCP.
4. **Redis absichern:** `requirepass` setzen (`spring.data.redis.password`), Image-Tag statt `latest` pinnen (`docker-compose.yml:22-26`). Redis hängt nur am `boxcopilot-network`, das Risiko ist compose-intern, betrifft aber die Session-Fälschung.
5. **Default-Admin:** `ADMIN_PASSWORD` in Prod ohne Default (`application.yml`, heute `admin`), Startabbruch, wenn der Wert fehlt. Zusätzlich ein einmaliger Start-Check, ob irgendein ADMIN-Passwort-Hash zu `admin` passt (`passwordEncoder.matches`), für bestehende Datenbanken.
6. **Generische Fehler:** `GlobalExceptionHandler` gibt `ex.getMessage()` aus (`GlobalExceptionHandler.java:36`, `:115`). Nach außen generische Meldung, Details nur ins Log.
7. **`show-sql` aus** (`application.yml`, Default `true`). Es logt Statements, keine Parameterwerte, verursacht aber Rauschen.
8. **`/oauth2/**`-`permitAll` verengen** (4.2).

**Phase 4 und eigene Tickets**

- M3: Session-Invalidierung bei Rollenwechsel und Deaktivierung (Sessions leben 34 h und tragen die Entity), Machbarkeit über `FindByIndexNameSessionRepository`.
- M4: `forward-headers-strategy: framework` (vertraut jedem) auf `native` umstellen und `server.tomcat.remoteip.internal-proxies` auf den Traefik eingrenzen, Traefik `forwardedHeaders.trustedIPs`. Voraussetzung für das IP-Limit.
- Container-Härtung: non-root, `read_only` mit Volumes, `cap_drop: ALL`.
- Login-Fehlermeldungen vereinheitlichen (gesperrt/deaktiviert/falsch nicht unterscheidbar, `OAuth2SecurityConfig` `authenticationFailureHandler`).
- Cookie-`SameSite` konfigurierbar machen (heute `none`, `application-prod.yml`). Bei gleicher Site auf `Lax`, sonst bleibt `None` und der Schutz hängt an CSRF-Token und Frame-Header.
- Spring Boot auf einen unterstützten Stand heben (siehe 8, Restrisiko „Support-Status“).
- LIKE-Escaping-Check aller Suchpfade (3.3).
- `validate-on-migrate` global nach Staging-Check (4.3).

## 5. Bereitstellung

- Neue Konfiguration: `APP_MCP_ENABLED`, `BACKEND_URL` (Pflicht bei aktivem MCP), `FRONTEND_URL` (bereits Pflicht, jetzt auch Basis der Magic-Links), Redirect-URIs der Clients, `app.mcp.max-connection-age`, Rate-Limit-Werte, `REDIS_PASSWORD`, `ADMIN_PASSWORD` (ohne Default in Prod). Ein Signing-Key entfällt.
- Traefik: Der Backend-Router führt per Host-Regel alles auf dem Backend-Host, also auch `/mcp`, `/oauth2/**` und `/.well-known/**`. `forwardedHeaders.trustedIPs` setzen, HSTS aktivieren.
- Frontend (nginx) proxyt nur `/api/`. `/mcp` und `/oauth2` sind darüber nicht erreichbar (gewollt). Die SPA bekommt die Route `/magic`, `mcpPending`-Behandlung nach dem Login, den Dialog „Claude-Verbindung“ und den Header `Referrer-Policy: no-referrer`.
- Optional (nur Betriebsmaßnahme, nicht Teil des Designs): Traefik-`ipAllowList` auf die Anthropic-Egress-Range. Die Range ist ungeprüft, ändert sich, und der Schutz würde Claude Code und den MCP Inspector aussperren.
- Betriebsleitfaden: Client-Neuerzeugung, Verbindungen widerrufen, Kill-Switch, Bereinigung.

## 6. Tests

Alle mit Profil `mcp-test` (echte Chains), Datenbank-Tests mit Testcontainers-Postgres:

- **Zugang ohne Token:** `/mcp` → 401 mit korrektem `WWW-Authenticate` (inkl. `scope`).
- **Flag aus:** kein MCP-Bean (`McpServer`, `RegisteredClientRepository`), `/mcp` und `/oauth2/authorize|token` liefern **kein 2xx und keine Daten** (anonym und mit eingeloggter Session). `spring.ai.mcp.server.enabled` folgt der Flag.
- **Decorator, alle Pfade:** Deaktivierter, gesperrter, nicht freigeschalteter User oder falsche Client-Bindung → Ablehnung am Authorize, bei `/mcp` (Access-Token), beim Refresh (Refresh-Grant), beim Widerruf und bei der Code-Einlösung. Entzug von `mcp_allowed` löscht Client und Authorizations.
- **Absolutes Alter:** Nach `app.mcp.max-connection-age` schlägt der Refresh trotz frischer Rotation fehl (`started_at` bleibt über Rotationen erhalten). Alter Refresh-Token nach Rotation ungültig (`reuseRefreshTokens=false`).
- **Sperre vs. Löschen:** Temporäre Sperre (`lockedUntil`) weist Zugriffe ab, löscht aber weder Client noch Authorizations. Deaktivieren, Löschen und Entzug von `mcp_allowed` löschen beides. Anonymer Authorize wird vom Bindungs-Validator nicht abgelehnt, sondern läuft zum Entry-Point.
- **Client-Bindung:** Authorize mit der Client-ID eines anderen Users wird abgelehnt. Regenerate löscht den alten Client. Idempotenz von `POST /api/v1/me/mcp-client`. Client-ID taucht in keinem Log auf.
- **Refresh-Token als Bearer** an `/mcp` → 401. Fehlender Scope → 403.
- **Consent:** wird bei bereits erteiltem Consent trotzdem verlangt. Consent-Seite: CSRF-Token Pflicht, Frame-/CSP-Header, HTML-Escaping, Benutzername sichtbar.
- **PKCE und Redirect:** PKCE `plain` und fehlendes PKCE abgelehnt. Auth-Code nur einmal einlösbar, 60 s Lebensdauer. Redirect-URI mit Zusatzpfad, Query oder anderem Host abgelehnt.
- **Principal-Austausch:** Im Authorization-Attribut steht ein schlanker `User`-Principal (userId), kein `CustomUserPrincipal`, kein Passwort-Hash, keine OIDC-Daten. Die Session der SPA behält ihre Authentication.
- **Issuer:** `X-Forwarded-Host` verändert Metadaten nicht.
- **Cookie-Pending:** Cookie wird gesetzt (HttpOnly, Secure, `Path=/`, Max-Age 600, SameSite wie Session-Cookie), Query über 2 KB → 400, „latest wins“, `mcpPending` nur für eingeloggte Aufrufer, `continue` löscht das Cookie und leitet nur auf `BACKEND_URL` mit dem Query, ohne Cookie Redirect ins Frontend. Kein Fremd-URL-Rücksprung. Flut von anonymen `/oauth2/authorize`-Aufrufen erzeugt keine Sessions.
- **Chains:** Cookie/Session an `/mcp` wirkungslos, Bearer an `/api/**` wirkungslos. Nicht benötigte SAS-Endpunkte (Introspection, Device-Authorization) sind nicht erreichbar.
- **Magic-Link:** Zweimal einlösen → zweiter Versuch scheitert (atomar, auch parallel). Session-ID wechselt. POST ohne CSRF-Token wird abgelehnt. Gesperrter oder deaktivierter User scheitert. Token nicht in Logs. `expiresInHours` über dem Maximum abgelehnt. GET-Endpunkt existiert nicht mehr.
- **Login:** OIDC-Login eines deaktivierten oder gesperrten Users scheitert. Form-Login ohne CSRF-Token scheitert.
- **Start-Guards:** Start bricht ab bei `app.mcp.enabled=true` ohne `BACKEND_URL`, bei Profil `test` neben `prod`, bei Prod ohne `ADMIN_PASSWORD` bzw. mit Passwort `admin`.
- **Tool ohne `McpTransportContext`** scheitert fail-closed.
- **Tool-Ausgaben:** kein `imageToken`, keine `boxUuid`, Sanitizer, Limits, generische Fehler. LIKE-Wildcards in Suchbegriffen werden nicht als Muster interpretiert.
- **Rate-Limit:** pro `sub` und Tageskontingent für Bilder, IP-Limit auf Authorize/Token und auf fehlgeschlagene Token-Lookups an `/mcp`.
- **Migration:** V13/V14 laufen auf Postgres durch (Testcontainers), JWT-Größe ist nicht relevant, `attributes` als `text`. Cleanup-Job entfernt abgelaufene Zeilen.
- **Architektur:** ArchUnit-Test für die Package-Grenze `mcp`.
- **End-to-End:** MCP Inspector (`@modelcontextprotocol/inspector`), Claude Free (Custom Connector) manuell.

## 7. Phasen

- **Phase 0 – Spike (final, alle Punkte prüfen bevor Phase 1 beginnt):**
  1. Claude Free mit einer **pro Nutzer verschiedenen** Client-ID, ohne DCR und CIMD, auch nach einem Refresh (bekannter Bug bei gespeicherten Client-Credentials, Issue #344). Sendet Claude `scope` und `resource`?
  2. Gleitende Refresh-Kette von SAS 1.5 bestätigen. Bleibt `boxpilot.started_at` über Rotationen erhalten (Fallback `authorization_code_issued_at`)?
  3. `McpAuthenticationNormalizationFilter`: Filterposition, Austausch gegen `UsernamePasswordAuthenticationToken(User)`, Jackson-Serialisierung in `attributes`, Verhalten beim Consent-POST, Session der SPA bleibt unberührt.
  4. Der Decorator um `OAuth2AuthorizationService` wird von allen Pfaden genutzt (Access, Refresh, Revoke, Code, Consent-State). Bean-Ersetzung gegen die Boot-Autokonfiguration.
  5. Spring AI 1.1 mit `STATELESS`: `McpTransportContext` aus dem authentifizierten Principal, Verhalten von `SecurityContextHolder` im Tool-Thread, `@McpTool` liefert `ImageContent`, Darstellung in Claude Mobile. ChatGPT (Developer Mode) und statische Client-IDs, Bilder aus Tool-Ergebnissen.
  6. SAS startet ohne `JWKSource`-Bean mit REFERENCE-Tokens (sonst ungenutzter In-Memory-Schlüssel).
  7. Postgres-Migration mit `text`/`bytea` (Testcontainers), Verhalten der Jackson-Allowlist.
  8. Spring Data `Containing`-Escaping von `%`/`_` und Redirect-Validator-Verhalten für `localhost` (Claude Code), `plain`-PKCE.
  9. Support-Status von Spring Boot 3.5 und Kompatibilitätsmatrix Spring AI / SAS / Spring Security. Ergebnis: Upgrade-Ticket (Phase 4) oder ausdrückliche Risikoakzeptanz.
- **Phase 0b:** Pflicht-Fixes aus 4.4.
- **Phase 1:** Tools und Datenschicht (Unit-Tests der Services), Feature-Flag samt Autokonfigurations-Bindung, Start-Guards.
- **Phase 2:** Resource Server für `/mcp` (Opaque-Introspector, `McpAccessPolicy`, `McpTransportContext`), Decorator.
- **Phase 3:** Authorization Server, Flyway V13/V14 (eine Migration für `users.mcp_allowed BOOLEAN NOT NULL DEFAULT FALSE` samt `UserDTO`/`UpdateUserDTO`/AdminPanel-Schalter, eine für die SAS-Tabellen; `mcp_allowed` ist nur über die Admin-Endpunkte änderbar), Client-Bereitstellung und Bindung, Principal-Austausch, Cookie-Pending und `continue`, Consent-Seite, SPA-Dialog.
- **Phase 4:** Härtung (Rate-Limit, IP-Limit, Audit, Cleanup-Job, Widerruf, Kill-Switch), Micrometer, restliche Tickets aus 4.4, Tests. Boot-Upgrade als eigenes Ticket vor dem Livegang.
- **Phase 5:** Doku (README), Docker/Traefik, Betriebsleitfaden.

**Bedingungen vor Livegang:** H1 (Pro-User-Client-ID) und H2 (absolutes Alter) sind umgesetzt und getestet, Cookie-Pending (M1) und Magic-Link (M2) sind umgesetzt, Punkte 1-6 aus Phase 0b sind erledigt, Boot-Support-Status ist geklärt. Zusätzlich vor Livegang (nicht in Phase 4 verschiebbar): Decorator/Policy auf allen Pfaden getestet (Access, Refresh, Code, Widerruf), Principal-Tausch samt Postgres-Test, Flag-Bindung der Starter-Autokonfiguration, IP-Limit auf Authorize/Token/`/mcp`-Fehlschläge **inklusive M4** (vertrauenswürdige Proxy-Header, sonst zählt das Limit die falsche IP).

## 8. Restrisiken (dokumentiert, akzeptiert)

- Geleakte Client-ID eines Users (Screenshot, Traefik-Access-Log mit Authorize-Query, Browser-Verlauf): Der Schutz vor Consent-Phishing fällt für diesen User auf v3-Niveau zurück, bis die ID neu erzeugt wird. Ein Angreifer ohne Client-ID des Opfers kann den Angriff nicht mehr ausführen.
- Prompt-Injection über Freitext und Bilder (Abfluss über andere Connectors des Anwenders).
- Inventardaten gehen an den KI-Anbieter. Optionales Sperr-Flag pro Box ist für v1.1 vorgesehen.
- Die bestehende öffentliche Preview (`/api/v1/public/{uuid}`) macht die Box-UUID auf dem QR-Etikett zum öffentlichen Zugriffstoken. Bestandsverhalten, nicht Teil dieses Vorhabens.
- Keine Mandantentrennung: Jeder MCP-freigeschaltete User sieht das gesamte Inventar, wie in der App. Insider: Jeder aktive User kann Item-Namen schreiben.
- Refresh-Token-Diebstahl ohne Reuse-Detection: begrenzt durch Rotation, 7 Tage Idle, 14 Tage absolut, Widerruf und sofortige Sperre über die Policy.
- Klartext-Tokens in der Datenbank (kein Hashing): Wer die DB lesen kann, kann aktive Tokens nutzen. Er sieht dort aber ohnehin das gesamte Inventar. Backups verschlüsseln.
- Geleakter Magic-Link bleibt bis zur ersten Nutzung oder zum Ablauf gültig. Ohne Query-Token und Log-Spur ist die Angriffsfläche kleiner als in v3.
- Cookie `mcp_authz` ist nicht signiert: Ein untergeschobenes Cookie kann nur an `/oauth2/authorize` laufen, mit exakter Redirect- und Client-Validierung, Bindung und Consent, und ist damit nicht mächtiger als ein direkter Link.
- Support-Status Spring Boot 3.5: Nach unserer Kenntnis läuft der OSS-Support 2026 aus. Vor Livegang klären und entweder upgraden oder das Risiko ausdrücklich akzeptieren. Der Upgrade-Pfad (Boot 4, Spring Security 7, Spring AI 2.x) ist ein koordinierter Sprung.
- `mcp-security` und `mcp-authorization-server` werden bewusst nicht verwendet. Der Spring-AI-Starter ist auf Transport beschränkt.

## 9. Verworfen (Überengineering oder bessere Alternative)

- Origin-Allowlist auf `/mcp`: bei Bearer-Token ohne Cookies und ohne CORS ohne Sicherheitsgewinn, bricht Browser-Clients wie den Inspector.
- Consent-Tabelle, Hashing der Refresh-Tokens, Reuse-Detection, Löschen bei Rollenwechsel, aktive Anomalie-Drosselung, spekulativer Policy-Hook für Mandantenfähigkeit, 5-Minuten-Login-Frische, RFC-9207-Aufwand.
- JWT mit JWKS, Signing-Key-Datei und Rotation: durch Opaque Tokens ersetzt (AS und Resource Server sind dieselbe Anwendung).
- `aud`-Eigenbau und `resource`-Converter/Validator: bei einem Resource Server und Opaque Tokens wirkungslos.
- Mehrfach-Einträge pro State-Hash und `SavedRequest` in der Session (Redis-Flut, Zustand): durch das zustandslose Cookie `mcp_authz` ersetzt.
- `McpPrincipal` mit Eigen-Mixins: durch `UsernamePasswordAuthenticationToken(User)` ersetzt.
- `mcp_connection`-Tabelle: durch Attribut `boxpilot.started_at` und Query auf `oauth2_authorization` ersetzt.
- `McpConnectionController` mit Verbindungsliste und IP: v1 nur „alle widerrufen“.
- Anzeige von Uhrzeit und IP auf der Consent-Seite (kein Nutzen gegen Phishing).
- Bucket4j: In-Memory-Zähler genügt.
- Arming-Fenster („Claude-Verbindung erlauben, 10 Minuten“): schützt nur teilweise (der Link ist im offenen Fenster nicht unterscheidbar, Social Engineering), Pro-User-Client-ID schützt vollständig bei fast gleichem UI-Aufwand.
- Nextcloud als Authorization Server: entfällt, weil Client-Bindung pro User und absolutes Verbindungsalter dort nicht durchsetzbar sind (`aud` vermutlich `client_id`, kein Scope, TTLs bei Nextcloud, Erreichbarkeit aus der Anthropic-Cloud).
- Separater Auth-Service: doppelter Betrieb ohne Nutzen gegenüber getrennten Chains.
- Explizites `denyAll` für `/mcp` bei ausgeschalteter Flag: Verengung von `permitAll` genügt.
- Log-Scrubbing für `token=`: durch den Magic-Link mit Fragment und POST hinfällig.
- Traefik-`ipAllowList` als Designbestandteil (nur optionale Betriebsmaßnahme).

## 10. Diskussionsprotokoll: Änderungen gegenüber v3

Wer: H = Hugo (Sicherheit), U = Udo (Architektur).

| Thema | v3 | v4 | Begründung | Wer |
|---|---|---|---|---|
| Client-Bindung / Phishing (K1/H1) | Gemeinsamer Client, `mcp_allowed` | Client-ID pro User, Bindung in `client_settings`, Validator plus Decorator | Browser-Binding trägt nicht (URL ist identisch), Client-ID ist die einzige Out-of-band-Größe. Arming schützt nur teilweise | H fordert, U ergänzt Begründung; H gibt Arming-Fenster auf, U gibt Browser-Binding-Idee auf |
| Token-Format | JWT RS256 mit JWKS und Signing-Key | Opaque REFERENCE, Introspector, kein Schlüssel | AS = RS = eine App und eine DB, weniger bewegliche Teile, Widerruf sofort | H schlägt vor, U bestätigt und präzisiert; U gibt JWT auf |
| Widerruf/Zugriffskontrolle | Prüfung an `/mcp`, Authorize, Refresh getrennt | Decorator um `OAuth2AuthorizationService` plus `McpAccessPolicy`, fail-closed, kein Cache | Eine Stelle deckt Access, Refresh, Code und Widerruf ab (SAS-Refresh läuft nicht über den Introspector) | U |
| Refresh-Lebensdauer (H2) | „absolut 14 Tage“ behauptet | Idle 7 Tage, absolutes Alter 14 Tage per `started_at` im Decorator, Access 30 min | SAS gibt bei Rotation frisches TTL, Kette wäre gleitend. Aussage in v3 war falsch | H findet, U bestätigt (eigene Aussage korrigiert) |
| Verbindungsdaten | (Vorschlag Hugo) `mcp_connection`-Tabelle | Keine Tabelle, Attribut in `oauth2_authorization` | Framework-nativ, keine zweite Wahrheit; Liste per Query | H gibt Tabelle auf, U schlägt Alternative vor |
| Pending-Rücksprung | Session, Map pro `state`-Hash, POST `continue`, TTL 10 min | Cookie `mcp_authz`, latest wins, `mcpPending` in `/me`, GET `continue` | Zustandslos, keine anonymen Redis-Sessions, ein SPA-Pfad für alle Logins | H hatte Session-`SavedRequest` bevorzugt, beide einigen sich auf Cookie; U gibt `SavedRequest` auf, H gibt Multi-Map auf |
| Principal | `McpPrincipal` mit Eigen-Mixins | Filter tauscht gegen `UsernamePasswordAuthenticationToken(User(userId))` | Session-Authentication enthält Entity mit Hash oder OIDC-Daten, Standard-Mixins reichen | U, H stimmt zu |
| Feature-Flag | `@ConditionalOnProperty` auf eigenen Klassen, „404“ | Zusätzlich `spring.ai.mcp.server.enabled=${app.mcp.enabled:false}`; „nicht erreichbar, kein 2xx, keine Daten“ | Starter-Autokonfiguration ignoriert die Flag; App-Chain liefert 302, nicht 404 | U findet, H formuliert die kleine Fassung; H gibt `denyAll` auf |
| Migration | V13 mit `varchar(4000)` | V13/V14 mit `text`/`bytea`, Testcontainers-Postgres | Referenz-DDL zu klein, Tests nur H2 | U, H stimmt zu |
| Boot-Support | „aktueller 3.5.x-Patch“ | Support-Status und Upgrade-Ticket vor Livegang oder ausdrückliche Risikoakzeptanz | Boot 3.5 vermutlich außerhalb OSS-Support, Upgrade auf Boot 4 ist gekoppelt | U findet, H stimmt zu |
| Magic-Link | GET-Link, atomares UPDATE | Link mit Fragment, SPA-POST mit CSRF-Token (ohne Ausnahme), `changeSessionId`, `lockedUntil`, Max-Dauer | GET mit Seiteneffekt wird von Link-Vorschauen verbrannt, Token in Logs und Referer, Log-Scrubbing entfällt | H findet, U ändert CSRF-Ausnahme in CSRF-Token |
| `resource`-Validator / `aud` | Converter/Validator, `aud`-Konstante | Gestrichen | Bei einem RS und Opaque Tokens wirkungslos | U schlägt vor, H stimmt zu |
| Consent-Inhalt | IP und Uhrzeit angezeigt | Nur Benutzername, keine IP/Zeit | Kein Nutzen gegen Phishing, IP im Audit-Log | H gibt auf |
| Rate-Limit | Bucket4j | In-Memory-Zähler pro `sub`, IP-Limit auf Authorize/Token | Ein Backend, keine Bibliothek nötig | H und U geben Bucket4j auf |
| Verbindungsliste | `McpConnectionController` mit IP | Nur „alle widerrufen“ | Minimum für v1 | H gibt IP-Liste auf |
| Cleanup | fehlte | `@Scheduled`-Bereinigung abgelaufener Authorizations | SAS räumt nicht auf | U findet |
| Phase 0b | 9 Punkte | Pflicht: Magic-Link, OIDC, Test-Config, Redis, Admin, Fehler, `show-sql`; Rest als Tickets (M3, M4, Härtung, u. a.) | Getrennte Bugs nicht an MCP koppeln | U schlägt vor, H akzeptiert (M3 und Kleinthemen als Tickets) |
| Audit-Log | `sub`, `client_id`, Tool, Trefferzahl | `sub`, Tool, Trefferzahl, IP; Client-ID nie loggen | Client-ID ist vertraulich (2.1) | H |
| `NoSecurityConfig` | Profil `test`, nur Startabbruch mit MCP | Nach `src/test` und Startabbruch `test` neben `prod` | Klasse gehört nicht ins Produktions-Jar | H findet, U ergänzt den Verschiebe-Schritt |
| Nextcloud als AS | Nicht geprüft | Verworfen | Client-Bindung und absolutes Alter dort nicht erzwingbar | U schlug ihn als Gate vor und gibt ihn auf; H stimmte einem Ein-Tages-Gate zu und akzeptiert das Ergebnis |
| Traefik-`ipAllowList` | nicht erwähnt | Nur optionale Betriebsmaßnahme | Range ungeprüft, brüchig, bricht Claude Code/Inspector | H schlug vor, U lehnt als Designbestandteil ab, H akzeptiert |
| Hugo (Review) – Sperre vs. Löschen | „Sperren“ löscht Client und Authorizations | Nur Deaktivieren, Löschen, Entzug `mcp_allowed` löschen; `lockedUntil` wird nur von der Policy abgewiesen (2.1, 4.3, Tests) | Auto-Lockout nach 5 Fehlversuchen hätte per Fehlversuch die Verbindung samt Client-ID des Opfers zerstört (Account-DoS) | Hugo (Review) |
| Hugo (Review) – Validator bei anonymem Authorize | Validator prüft Bindung | Validator prüft nur bei authentifiziertem Principal (4.1 Schritt 8) | SAS ruft ihn vor der Login-Prüfung auf, sonst kein Login-Redirect | Hugo (Review) |
| Hugo (Review) – Client-ID in Logs | „nie geloggt“ | Zusatz: steht im Authorize-Query (Traefik-Access-Logs, Browser-Verlauf), Betriebsleitfaden und Restrisiko ergänzt | Aussage war sonst zu stark | Hugo (Review) |
| Hugo (Review) – DB-Last durch Opaque Tokens | nicht behandelt | IP-Zähler für fehlgeschlagene Token-Lookups an `/mcp` (30/min) | Jeder ungültige Bearer kostet eine DB-Abfrage (bei JWT stateless) | Hugo (Review) |
| Hugo (Review) – Livegang-Bedingungen, Migration, CSRF-Wortlaut | Livegang-Liste ohne Decorator-Tests, Principal-Tausch, Flag-Bindung, IP-Limit/M4; `mcp_allowed`-Migration nicht explizit; CSRF-Ausnahmen „bei aktivem MCP“ | Ergänzt (7, 4.3, Phase 3); CSRF-Ausnahmen entfallen unabhängig von MCP | Widerspruch zwischen 4.3 und Phase 0b; IP-Limit ist ohne M4 wirkungslos oder falsch | Hugo (Review) |
| Hugo (Review) – Zahlen | Rate-Limit 60/min pro `sub`, 200 Bilder/Tag, IP 30/min, Magic-Link max. 72 h | Unverändert bestätigt | Plausibel für wenige Nutzer, konfigurierbar, Default 24 h bei Magic-Link bleibt | Hugo (Review) |

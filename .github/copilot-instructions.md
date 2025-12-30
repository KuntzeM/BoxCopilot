# BoxCopilot - AI Coding Agent Instructions

## AI Agent Guidelines

### Problem-Solving Philosophy
- **NO Quick Fixes or Workarounds**: Always analyze problems deeply and implement proper, sustainable solutions
- **Root Cause Analysis**: Identify the underlying issue, not just symptoms
- **Modern Best Practices**: Use current, industry-standard approaches and patterns
- **Framework-First**: Leverage Spring Boot's built-in features and conventions before creating custom solutions
- **Security by Design**: Prioritize secure implementations using Spring Security's proven patterns
- **No Summary Files**: Do NOT create `.md` files documenting AI processes, summaries, or implementation steps unless explicitly requested

### Spring Boot Best Practices
- Use Spring Boot auto-configuration where possible
- Leverage Spring's dependency injection (constructor injection preferred)
- Follow Spring Security's authentication/authorization patterns
- Use Spring Data JPA conventions for repository queries
- Apply Spring's `@Transactional` properly for data consistency
- Utilize Spring Boot's configuration properties over hardcoded values
- Prefer Spring's exception handling mechanisms (`@ControllerAdvice`, `@ExceptionHandler`)

### Handling Circular Dependencies
**NEVER use `@Lazy` as a quick fix for circular dependencies!**

When encountering: `The dependencies of some of the beans in the application context form a cycle:`

1. **Analyze the Architecture**: Circular dependencies indicate a design problem
2. **Restructure First**: Consider these solutions (in order of preference):
   - **Extract Interface**: Create an interface that breaks the cycle
   - **Introduce Intermediate Service**: Add a facade or mediator service
   - **Event-Driven Approach**: Use Spring's `ApplicationEventPublisher` for decoupling
   - **Configuration Class**: Use `@Configuration` with `@Bean` methods for setter injection
   - **Rethink Responsibilities**: Split services that have too many responsibilities (SRP violation)
3. **Document the Solution**: Explain why the chosen approach is architecturally sound

**Example from this project**: `UserService` ↔ `MagicLoginTokenService` circular dependency solved via `ServiceConfiguration.java` with setter injection, not `@Lazy`.

## Project Overview

BoxCopilot is a full-stack moving/packing management application. Users create boxes, assign items, and generate QR codes for easy tracking during moves. The application supports dual authentication (Nextcloud OIDC + local credentials), role-based access control (USER/ADMIN), magic login links, item images, and multi-language UI.

### Technology Stack

**Backend:**
- Java 21 with Spring Boot 3.5.0
- Spring Security with dual auth: OAuth2/OIDC (Nextcloud) + form login (local users)
- Spring Data JPA with H2 (dev) / PostgreSQL (prod)
- Flyway for database migrations
- Redis (prod) / JDBC (dev) for session management
- Maven for build management
- BCrypt password encoding

**Frontend:**
- React 18.2.0 with TypeScript
- Vite 7.3.0 as build tool
- Material-UI (MUI) 5.14.11 for component library
- Axios 1.12.0 for HTTP requests
- i18n with cookie-persisted language selection (EN/DE)

**Infrastructure:**
- Docker & Docker Compose for containerization
- PostgreSQL 15 for production database
- Redis for caching and session storage
- Traefik reverse proxy (external, configured via docker-compose labels)
- File-based image storage at `/var/boxcopilot/images` (prod) or `./data/images` (dev)

## Project Structure

```
BoxPilot/
├── backend/                          # Spring Boot API server
│   ├── src/main/java/com/boxcopilot/backend/
│   │   ├── BoxCopilotApplication.java       # Application entry point
│   │   ├── config/                           # Configuration beans
│   │   │   ├── OAuth2SecurityConfig.java    # Security config with OIDC (dev/prod profiles)
│   │   │   ├── NoSecurityConfig.java        # Disabled security (test profile)
│   │   │   └── RedisConfig.java             # Redis session management
│   │   ├── controller/                       # REST controllers
│   │   │   ├── AuthController.java          # Authentication endpoints
│   │   │   ├── BoxController.java           # Box CRUD operations
│   │   │   ├── ItemController.java          # Item management
│   │   │   └── PublicPreviewController.java # Public access endpoints
│   │   ├── domain/                           # JPA entities (POJO with getters/setters)
│   │   │   ├── Box.java                     # Box entity (id, uuid, currentRoom, targetRoom, description)
│   │   │   └── Item.java                    # Item entity (id, name, box reference)
│   │   ├── dto/                              # Data Transfer Objects
│   │   │   ├── BoxRequestDTO.java           # Create box payload
│   │   │   ├── BoxResponseDTO.java          # Box response
│   │   │   ├── BoxUpdateDTO.java            # Update box payload
│   │   │   ├── BoxPreviewDTO.java           # Public preview response
│   │   │   ├── ItemRequestDTO.java          # Create item payload
│   │   │   ├── ItemResponseDTO.java         # Item response
│   │   │   ├── ItemUpdateDTO.java           # Update item payload
│   │   │   └── UserPrincipalDTO.java        # User info
│   │   ├── exception/                        # Custom exception handling
│   │   │   └── GlobalExceptionHandler.java  # Centralized error handling
│   │   ├── mapper/                           # Entity-DTO mapping (MapStruct-style)
│   │   │   └── BoxMapper.java               # Box entity <-> DTO conversion
│   │   ├── repository/                       # Spring Data JPA repositories
│   │   │   ├── BoxRepository.java           # Box data access
│   │   │   └── ItemRepository.java          # Item data access
│   │   └── service/                          # Business logic layer
│   │       ├── BoxService.java              # Box business logic
│   │       ├── ItemService.java             # Item business logic
│   │       ├── CustomOidcUserService.java   # OIDC user mapping
│   │       └── ResourceNotFoundException.java # Custom exception
│   ├── src/main/resources/
│   │   ├── application.yml                   # Default profile
│   │   ├── application-dev.yml               # Dev profile (H2, OIDC enabled)
│   │   ├── application-prod.yml              # Production profile (PostgreSQL, Redis)
│   │   ├── application-test.yml              # Test profile (no security)
│   │   ├── logback-spring.xml                # Logging configuration
│   │   └── db/migration/
│   │       └── V1__init.sql                  # Flyway migration (if used)
│   └── pom.xml                               # Maven dependencies
│
├── frontend/                          # React + Vite UI
│   ├── src/
│   │   ├── App.tsx                           # Main app with routing, auth, theme toggle
│   │   ├── main.tsx                          # React entry point
│   │   ├── pages/
│   │   │   ├── BoxList.tsx                   # Main box listing with search, filter, QR
│   │   │   ├── BoxEditPage.tsx               # Box edit page with items
│   │   │   └── PublicPreview.tsx             # Public box preview (no auth)
│   │   ├── components/                       # Reusable UI components
│   │   │   ├── BoxForm.tsx                   # Box creation/edit form
│   │   │   ├── ItemForm.tsx                  # Item creation/edit form
│   │   │   └── ItemsTable.tsx                # Items list/table
│   │   ├── context/
│   │   │   └── ThemeContext.tsx              # Dark/Light theme provider (cookie-based)
│   │   ├── services/                         # API client services (Axios)
│   │   │   ├── axiosConfig.ts                # Axios instance with defaults
│   │   │   ├── boxService.ts                 # Box API calls
│   │   │   ├── itemService.ts                # Item API calls
│   │   │   └── publicPreviewService.ts       # Public preview API
│   │   ├── types/
│   │   │   └── models.ts                     # TypeScript interfaces
│   │   └── utils/
│   │       └── textUtils.ts                  # Text utilities (truncation, etc.)
│   ├── index.html                            # HTML entry point
│   ├── package.json                          # NPM dependencies
│   ├── tsconfig.json                         # TypeScript configuration
│   ├── vite.config.ts                        # Vite build configuration (proxy)
│   ├── nginx.conf                            # Nginx config for production
│   └── Dockerfile                            # Container image (multi-stage build)
│
├── docker-compose.yml                  # Service orchestration (PostgreSQL, Redis, Backend, Frontend)
└── .env                                # Environment variables (not in git)
```

## Core Domain Model

### Entities
- **User**: Authentication & authorization entity
  - Fields: `username`, `password`, `email`, `name`, `role` (ADMIN/USER), `authProvider` (NEXTCLOUD/LOCAL), `enabled`, `accountLocked`, `failedLoginAttempts`, `lockoutEndTime`, `lastLogin`
  - Password can be null (for magic-link-only users or OIDC users)
  - Auto-created for OIDC logins via `CustomOidcUserService`
  - Username conflicts for OIDC users handled by appending `_nc`, `_nc2`, etc.
- **Box**: Container entity with UUID-based public sharing
  - Fields: `id`, `uuid` (auto-generated), `currentRoom`, `targetRoom`, `description`, `createdAt`, `user` (owner)
  - One-to-many with Items (cascade delete)
- **Item**: Item entity
  - Fields: `id`, `name`, `box` (FK), `imagePath` (nullable)
  - Images stored on disk, path saved in DB
- **MagicLoginToken**: Passwordless authentication tokens
  - Fields: `id`, `token` (UUID), `user` (FK), `createdAt`, `expiryDate`, `used`
  - Default validity: 24 hours (configurable via `app.magiclink.default-valid-hours`)
  - Single-use tokens; old tokens invalidated when generating new ones

### Key Business Rules
1. **Authentication**:
   - OIDC users auto-created in `users` table; username conflict resolution adds suffixes
   - Local users require password (except magic-link-only accounts)
   - Account lockout after 5 failed login attempts for 1 hour
   - Magic login links are single-use and time-limited
2. **Authorization**:
   - ADMIN role required for user management endpoints (`/api/v1/admin/**`)
   - Users can only access their own boxes/items
   - Public preview endpoints (`/api/v1/public/{uuid}`) allow anonymous read-only access
3. **Image Management**:
   - Images uploaded via multipart/form-data, saved to `app.image.storage-path`
   - Filenames: `{itemId}_{timestamp}.{ext}`
   - Large images resized to max 1024x1024px, thumbnails to 200x200px
   - Deletion removes files from disk and nullifies `imagePath` in DB

## Architecture Patterns

### Backend Layers
1. **Controller Layer** (`controller/`): REST endpoints, request/response handling
   - `AuthController`: `/api/v1/auth/logout`, `/api/v1/me`, `/api/v1/auth/magic-login` (public)
   - `BoxController`: `/api/v1/boxes` (CRUD operations)
   - `ItemController`: `/api/v1/items`, `/api/v1/items/search`, `/api/v1/items/{id}/image`
   - `PublicPreviewController`: `/api/v1/public/{uuid}` (no auth)
   - `AdminUserController`: `/api/v1/admin/users/**` (ADMIN only)
2. **Service Layer** (`service/`): Business logic, transaction boundaries
   - Use `@Transactional` for write operations
   - Use `@Transactional(readOnly = true)` for read operations
   - Direct entity-DTO conversion (no MapStruct dependency)
   - Example: `UserService` handles username conflict resolution, password encoding, account lockout
3. **Repository Layer** (`repository/`): Spring Data JPA interfaces
   - Custom query methods: `findByUsername`, `findByTokenAndExpiryDateAfter`, etc.
   - Leverage JPA naming conventions for automatic query generation
4. **DTO Layer** (`dto/`): API contract definitions
   - Request DTOs: `@Valid` annotations for validation (e.g., `@NotBlank`, `@Email`)
   - Response DTOs: Include only necessary fields (avoid exposing passwords)
   - Example: `CreateUserDTO` has optional `password` field for magic-link-only accounts
5. **Configuration** (`config/`):
   - `OAuth2SecurityConfig`: Security filter chain for dev/prod (dual auth enabled)
   - `NoSecurityConfig`: Security disabled for test profile
   - `ServiceConfiguration`: Resolves circular dependencies (e.g., UserService ↔ MagicLoginTokenService)
   - Profile-based activation: `@Profile({"dev", "prod"})` vs `@Profile("test")`

### Frontend Architecture
- **Pages** (`pages/`): Routed components (BoxList, BoxEditPage, PublicPreview, LoginPage, AdminPanel)
- **Components** (`components/`): Reusable UI (BoxForm, EnhancedItemsTable, ItemImageUpload, LanguageSelector)
- **Services** (`services/`): Axios-based API clients (boxService, itemService, userService, publicPreviewService)
  - Centralized config in `axiosConfig.ts` (withCredentials, base URL, CSRF handling)
- **Context** (`context/`): Theme (dark/light) and language (EN/DE) with cookie persistence
- **i18n** (`i18n/`): Translation keys in `locales/en.ts` and `locales/de.ts`

### Key Integration Points
1. **OIDC Flow**: 
   - Frontend redirects to `/oauth2/authorization/nextcloud`
   - Backend handles callback at `/login/oauth2/code/nextcloud`
   - `CustomOidcUserService.loadUser()` auto-creates User entity if not exists
   - Session cookie set, frontend redirected to `${FRONTEND_URL}/?login=success`
2. **Form Login Flow**:
   - Frontend POSTs to `/login` with `username` and `password`
   - `CustomUserDetailsService.loadUserByUsername()` loads User entity
   - Success: redirects to `${FRONTEND_URL}/?login=success`
   - Failure: redirects to `${FRONTEND_URL}/login?error=true` (or `?locked=true`)
3. **Magic Login Flow**:
   - Admin generates link via `POST /api/v1/admin/users/{id}/magic-link`
   - User clicks link → `GET /api/v1/auth/magic-login?token={uuid}`
   - Backend validates token, creates session, redirects to `${FRONTEND_URL}/?login=success`
4. **Image Upload**:
   - Frontend sends multipart/form-data to `/api/v1/items/{id}/image`
   - Backend saves to `app.image.storage-path` and stores path in `Item.imagePath`
   - Frontend retrieves via `GET /api/v1/items/{id}/image` (returns image bytes)
- **Prod**: Configured via environment

## Development Workflows

### Running Backend Locally
**Required Environment Variables:**
```powershell
# PowerShell syntax
$env:FRONTEND_URL = 'http://localhost:3000'
$env:CLIENT_ID = 'your-nextcloud-client-id'
$env:CLIENT_SECRET = 'your-nextcloud-client-secret'
$env:NEXTCLOUD_URL = 'https://cloud.example.com'
$env:NEXTCLOUD_LOGOUT_URL = 'https://cloud.example.com/index.php/logout'
$env:SPRING_PROFILES_ACTIVE = 'dev'
```

**Run Commands:**
```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```
Backend runs on port 8080 with H2 file-based database at `./data/boxcopilot-dev`.

### Running Frontend Locally
```bash
cd frontend
npm install
npm run dev
```
Frontend runs on port 5173 (Vite dev server). API calls proxied to `http://localhost:8080` via `vite.config.ts`.

### Running Tests
```bash
cd backend
mvn test
```
Tests run with `test` profile (NoSecurityConfig, H2 in-memory).

### Database Migrations
Flyway migrations are in `backend/src/main/resources/db/migration/`:
- `V1__init.sql`: Initial schema (boxes, items)
- `V3__add_handling_flags.sql`: Handling flags for boxes
- `V4__add_item_image.sql`: Item image support
- `V5__create_users_table.sql`: User authentication/authorization
- `V7__add_magic_login_tokens.sql`: Magic login tokens

**Important**: Migrations run automatically on startup. For schema changes, create new versioned migration file.

### Docker Compose Deployment
```bash
# Create .env file with production variables
docker-compose up --build
```
Services: postgres, redis, backend (port 8080), frontend (port 80). Traefik labels configure reverse proxy.

### Common Debugging
- **H2 Console**: Not enabled by default; if needed, add `spring.h2.console.enabled=true` to dev profile
- **Backend Logs**: Check `./logs` directory or console output
- **OIDC Issues**: Verify `CLIENT_ID`, `CLIENT_SECRET`, `NEXTCLOUD_URL` are correct
- **Session Issues**: Dev uses JDBC sessions (stored in H2); prod uses Redis
- **Image Upload Failures**: Check `app.image.storage-path` exists and is writable

## Project-Specific Conventions

### Backend Code Style
- **No Lombok**: Use explicit getters/setters in entity POJOs
- **SLF4J Logging**: `private static final Logger log = LoggerFactory.getLogger(ClassName.class);`
- **Transaction Boundaries**: `@Transactional` on service methods; `@Transactional(readOnly = true)` for reads
- **Exception Handling**: 
  - `ResourceNotFoundException` for 404 cases
  - `IllegalArgumentException` for validation failures
  - `GlobalExceptionHandler` for centralized HTTP responses
- **DTO Validation**: Use `@Valid`, `@NotBlank`, `@Email`, etc. on DTOs
- **Profile Configuration**: `@Profile({"dev", "prod"})` for SecurityConfig; `@Profile("test")` for NoSecurityConfig
- **Circular Dependencies**: Resolve via setter injection (see `ServiceConfiguration.java`)

### Frontend Code Style
- **Functional Components**: No class components; use hooks exclusively
- **Type Safety**: Define interfaces in `types/models.ts` for all API contracts
- **Service Layer**: All API calls go through dedicated service modules (e.g., `boxService.ts`)
- **Context Usage**: Theme and language contexts use cookie persistence (via `js-cookie`)
- **MUI Styling**: Prefer `sx` prop over inline styles or external CSS
- **i18n Pattern**: Use `useTranslation()` hook; translation keys in `locales/en.ts` and `locales/de.ts`

### Naming Conventions
- **Backend**: `BoxService`, `BoxRepository`, `BoxController`, `BoxResponseDTO`
- **Frontend**: `BoxList.tsx`, `boxService.ts`, `useBoxes()` (if custom hook)
- **Package/Folder Structure**: By feature (box, item, user, auth) not by layer
- **API Versioning**: All REST endpoints prefixed with `/api/v1/`
- **Commit Messages**: Clear, descriptive, in English

### Critical Gotchas
1. **Password Nullable**: `User.password` can be null for magic-link-only or OIDC users; check before encoding
2. **Username Conflicts**: OIDC users auto-created; duplicate usernames get `_nc`, `_nc2` suffixes via `UserService.createUniqueUsername()`
3. **Session Store**: Dev uses `jdbc` (H2 table), prod uses `redis`; profile mismatch causes session loss
4. **CSRF Tokens**: Backend sends CSRF token via cookie; frontend must include in POST/PUT/DELETE via `axiosConfig.ts`
5. **Image Paths**: Stored as absolute paths in DB; ensure `app.image.storage-path` is consistent across environments
6. **Magic Links**: Single-use and time-limited; calling `generateMagicLink()` invalidates previous links for user
7. **Account Lockout**: 5 failed login attempts = 1 hour lock; tracked via `failedLoginAttempts` and `lockoutEndTime`
8. **Cascade Deletes**: Deleting User cascades to boxes, items, magic tokens; deleting Box cascades to items

## Key Files Reference

### Backend Entry Points
- [BoxCopilotApplication.java](backend/src/main/java/com/boxcopilot/backend/BoxCopilotApplication.java): Application entry point, creates default admin on startup
- [OAuth2SecurityConfig.java](backend/src/main/java/com/boxcopilot/backend/config/OAuth2SecurityConfig.java): Dual auth configuration (OIDC + form login)
- [ServiceConfiguration.java](backend/src/main/java/com/boxcopilot/backend/config/ServiceConfiguration.java): Resolves circular dependencies

### Core Business Logic
- [UserService.java](backend/src/main/java/com/boxcopilot/backend/service/UserService.java): User CRUD, username conflict resolution, account lockout
- [MagicLoginTokenService.java](backend/src/main/java/com/boxcopilot/backend/service/MagicLoginTokenService.java): Magic link generation/validation
- [BoxService.java](backend/src/main/java/com/boxcopilot/backend/service/BoxService.java): Box CRUD with user ownership checks
- [ItemService.java](backend/src/main/java/com/boxcopilot/backend/service/ItemService.java): Item CRUD, image management, search

### Frontend Entry Points
- [App.tsx](frontend/src/App.tsx): Routing, theme provider, language context
- [axiosConfig.ts](frontend/src/services/axiosConfig.ts): Axios instance with CSRF handling
- [BoxList.tsx](frontend/src/pages/BoxList.tsx): Main dashboard with search, filter, QR generation
- [AdminPanel.tsx](frontend/src/pages/AdminPanel.tsx): User management UI (ADMIN only)

### Configuration
- [application-dev.yml](backend/src/main/resources/application-dev.yml): H2, JDBC sessions, OIDC config
- [application-prod.yml](backend/src/main/resources/application-prod.yml): PostgreSQL, Redis sessions
- [docker-compose.yml](docker-compose.yml): Production deployment with Traefik labels

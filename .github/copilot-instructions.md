# BoxCopilot - Copilot Instructions

## Project Overview

BoxCopilot is a full-stack application for managing boxes and items with public link sharing capabilities. It uses OpenID Connect (Nextcloud) for authentication.

### Technology Stack

**Backend:**
- Java 21 with Spring Boot 3.2.3
- Spring Security with OAuth2/OIDC (Nextcloud)
- Spring Data JPA with H2 (dev) / PostgreSQL (prod)
- Redis for session management
- Maven for build management

**Frontend:**
- React 18.2.0 with TypeScript
- Vite as build tool
- Material-UI (MUI) for component library
- Axios for HTTP requests

**Infrastructure:**
- Docker & Docker Compose for containerization
- PostgreSQL for production database
- Redis for caching and session storage

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

## Core Features

1. **Authentication**: OpenID Connect via Nextcloud (OIDC flow)
   - Active in `dev` and `prod` profiles
   - Disabled in `test` profile (NoSecurityConfig)
   - Custom logout flow with Nextcloud logout URL
2. **Box Management**: Create, read, update, delete boxes
   - UUID-based identification (auto-generated)
   - Metadata: currentRoom, targetRoom, description, createdAt
   - One-to-many relationship with items (cascade delete)
3. **Item Management**: Manage items within boxes
   - Simple entity with name and box reference
   - Bulk operations support
4. **Public Preview**: Share boxes publicly via UUID without authentication
   - Endpoint: `/api/v1/public/{uuid}`
   - Read-only access to box and items
   - QR code generation in frontend
5. **Session Management**: 
   - Redis-backed in production
   - JDBC-backed in development
6. **Search & Filter**:
   - Item search across all boxes
   - Room-based filtering
   - Client-side filtering in frontend

## Development Guidelines

### Backend Development

#### Code Style
- Follow Spring Boot conventions
- Use plain POJOs for entities with explicit getters/setters (no Lombok currently)
- Implement proper exception handling with custom exceptions
- Use DTOs for API contract definition
- Direct entity-DTO conversion in services (no MapStruct)
- Use SLF4J Logger for logging (`LoggerFactory.getLogger`)

#### Key Patterns
- **Service Layer**: Business logic in services, controllers delegate to services
- **Repository Pattern**: Spring Data JPA repositories for data access
- **Exception Handling**: 
  - `ResourceNotFoundException` for missing entities
  - `GlobalExceptionHandler` for centralized error responses
- **OIDC Integration**: 
  - `CustomOidcUserService` for Nextcloud user mapping
  - Profile-based security configuration (`@Profile`)
- **API Versioning**: All endpoints use `/api/v1/` prefix
- **Transaction Management**: `@Transactional` on service methods
- **Validation**: `@Valid` annotations on request DTOs

#### Configuration
- Use profiles: `dev` (H2, local), `prod` (PostgreSQL, Redis), `test` (in-memory)
- Environment variables for sensitive data (CLIENT_ID, CLIENT_SECRET)
- SecurityConfig for OAuth2 flow configuration

### Frontend Development

#### Code Style
- Use functional components with hooks (no class components)
- TypeScript strict mode for type safety
- Material-UI components for consistency
- Proper component composition and separation of concerns

#### Key Patterns
- **Pages**: Page-level components in `src/pages/`
  - `BoxList.tsx`: Main dashboard with search, filter, QR codes, bulk operations
  - `BoxEditPage.tsx`: Box details and item management
  - `PublicPreview.tsx`: Anonymous access via UUID
- **Components**: Reusable UI components in `src/components/`
  - Form components (`BoxForm`, `ItemForm`)
  - Display components (`ItemsTable`)
- **Services**: API calls through dedicated service classes using Axios
  - Centralized axios configuration in `axiosConfig.ts`
  - Service-specific modules (`boxService`, `itemService`, `publicPreviewService`)
- **Types**: Define interfaces in `src/types/models.ts` for API responses and state
- **Context**: `ThemeContext` for dark/light mode (cookie-persisted)
- **Routing**: React Router with public and protected routes

#### Styling
- Use MUI's `sx` prop for component-level styling
- Utilize the theme configuration in `App.tsx`
- Theme toggle with cookie persistence via `ThemeContext`
- Responsive design with `useMediaQuery` and breakpoints
- Avoid inline styles when possible

### API Contract

#### Base URL
- **Dev**: `http://localhost:8080`
- **Prod**: Configured via environment

#### Authentication
- Uses OAuth2/OIDC with Nextcloud
- Session cookies for authenticated requests
- Protected endpoints require valid session

#### Key Endpoints
- `POST /api/auth/logout` - User logout
- `GET /api/v1/boxes` - List all user's boxes
- `POST /api/v1/boxes` - Create new box
- `GET /api/v1/boxes/{uuid}` - Get box by UUID
- `PUT /api/v1/boxes/{id}` - Update box
- `DELETE /api/v1/boxes/{id}` - Delete box
- `GET /api/v1/boxes/{boxId}/items` - List items in box
- `POST /api/v1/boxes/{boxId}/items` - Create item in box
- `PUT /api/v1/items/{id}` - Update item
- `DELETE /api/v1/items/{id}` - Delete item
- `GET /api/v1/items/search?query={query}` - Search items across all boxes
- `GET /api/v1/public/{uuid}` - Public box preview (no auth required)

## Development Workflow

### Local Development Setup

**Backend:**
```bash
cd backend
mvn clean install
mvn spring-boot:run
```

**Frontend:**
```bash
cd frontend
npm install
npm run dev
```

**Using Docker Compose:**
```bash
docker-compose up --build
```

### Debugging

- **Backend**: Use your IDE's debugger or add logging via `org.slf4j.Logger`
- **Frontend**: Use browser DevTools or VS Code React Native Tools extension

## Important Notes

- **OIDC Configuration**: Requires valid Nextcloud instance and registered OAuth2 client
- **Session Store**: Dev uses JDBC, prod uses Redis
- **Database**: H2 for dev (file-based), PostgreSQL for prod
- **API Calls**: Always use the proper DTO objects for request/response contracts
- **Error Handling**: Return appropriate HTTP status codes and error messages
- **Validation**: Use `@Valid` and `@Validated` for input validation

## Common Gotchas

1. **OIDC Configuration Missing**: If seeing auth failures, verify `CLIENT_ID` and `CLIENT_SECRET` environment variables
2. **Database Schema**: If using `ddl-auto: update`, ensure migrations are idempotent
3. **CORS**: If frontend can't reach backend, verify CORS configuration in SecurityConfig
4. **Redis Connection**: Ensure Redis is running when using session management in prod
5. **Session Store Type**: Verify `spring.session.store-type` matches your infrastructure (jdbc for dev, redis for prod)

## Conventions

- **Java Naming**: `BoxService`, `BoxRepository`, `BoxController`, `BoxResponseDTO`
- **File Naming**: PascalCase for classes, camelCase for files in frontend
- **Package Structure**: By feature (box, item, auth) rather than by layer
- **Commit Messages**: Clear and descriptive in English
- **Comments**: Technical explanations only, code should be self-documenting

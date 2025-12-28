# Dual Authentication System with RBAC

This document describes the dual authentication system and role-based access control implemented in BoxCopilot.

## Features

### 1. Dual Authentication

BoxCopilot now supports two authentication methods:

#### **Nextcloud OIDC Authentication** (existing)
- Users can log in using their Nextcloud account
- Automatically creates a user record on first login
- Usernames are derived from the OIDC `preferred_username` claim
- If username conflicts occur, suffixes like `_nc`, `_nc2`, etc. are appended

#### **Local Username/Password Authentication** (new)
- Admins can create local user accounts
- Uses BCrypt password hashing for security
- Passwords can be set/changed by admins
- No password complexity requirements (admin discretion)

### 2. Role-Based Access Control (RBAC)

Two user roles are available:

- **USER**: Regular users with basic access to box management
- **ADMIN**: Administrators with full access including user management

### 3. Account Security

#### Failed Login Tracking
- Failed login attempts are tracked per user
- After 5 failed attempts, the account is automatically locked for 1 hour
- Admins can manually unlock accounts
- Failed attempts counter resets on successful login

#### Account Status
- **Active**: User can log in normally
- **Inactive**: Account is disabled (by admin)
- **Locked**: Account is locked due to failed login attempts

### 4. Admin Panel

Admins have access to a user management panel at `/app/admin` with the following features:

- **User List**: View all users with filtering options
  - Filter by role (User/Admin)
  - Filter by auth type (Nextcloud/Local)
  - Filter by status (Active/Inactive/Locked)
  
- **User Creation**: Create new local users
  - Set username, name, password
  - Assign role (User/Admin)
  - Set enabled status

- **User Editing**: Modify existing users
  - Update name, role, and enabled status
  - Cannot change username for Nextcloud users
  
- **Password Management**: 
  - Set/reset passwords for local users
  - Not available for Nextcloud users
  
- **Account Actions**:
  - Unlock locked accounts
  - Toggle admin role
  - Delete users (cannot delete self)

## Default Admin Account

On first startup, if no users exist in the database, a default admin account is created:

- **Username**: `admin` (configurable via `ADMIN_USERNAME` env var)
- **Password**: `admin` (configurable via `ADMIN_PASSWORD` env var)
- **Name**: `Administrator` (configurable via `ADMIN_NAME` env var)

**⚠️ IMPORTANT**: Change the default password immediately after first login!

## Environment Variables

### Backend Configuration

```bash
# Default admin account (used only on first startup)
ADMIN_USERNAME=admin
ADMIN_PASSWORD=admin
ADMIN_NAME=Administrator

# Existing Nextcloud OIDC configuration
CLIENT_ID=your-client-id
CLIENT_SECRET=your-client-secret
NEXTCLOUD_URL=https://your.nextcloud.com
NEXTCLOUD_LOGOUT_URL=https://your.nextcloud.com/index.php/logout
FRONTEND_URL=http://localhost:3000
```

### Frontend Configuration

```bash
# API base URL
VITE_API_BASE_URL=http://localhost:8080
```

## API Endpoints

### Authentication

- `POST /api/auth/login` - Form-based login (username/password)
- `POST /api/auth/logout` - Logout
- `GET /api/v1/me` - Get current user info (with role and isAdmin flag)
- `GET /api/v1/csrf` - Get CSRF token

### Admin User Management (requires ADMIN role)

- `GET /api/v1/admin/users` - List all users (with optional filters)
  - Query params: `role`, `authProvider`, `enabled`
- `GET /api/v1/admin/users/{id}` - Get user by ID
- `POST /api/v1/admin/users` - Create new local user
- `PUT /api/v1/admin/users/{id}` - Update user
- `PUT /api/v1/admin/users/{id}/password` - Set user password
- `PUT /api/v1/admin/users/{id}/unlock` - Unlock user account
- `DELETE /api/v1/admin/users/{id}` - Delete user

## Database Schema

### Users Table

```sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255),
    password_hash VARCHAR(255),
    auth_provider VARCHAR(50) NOT NULL,
    oidc_subject VARCHAR(255),
    role VARCHAR(50) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    last_login TIMESTAMP,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMP
);
```

## Security Considerations

1. **Password Storage**: Passwords are hashed using BCrypt with default strength
2. **CSRF Protection**: Enabled for all state-changing operations
3. **Session Management**: 
   - JDBC-based sessions in dev
   - Redis-based sessions in prod
4. **Account Lockout**: Automatic lockout after 5 failed attempts
5. **Admin Access**: All admin endpoints require ADMIN role
6. **Self-Protection**: Users cannot delete themselves

## Upgrading from Previous Versions

When upgrading to this version:

1. The Flyway migration `V5__create_users_table.sql` will automatically create the users table
2. On first application startup, the default admin account will be created
3. Existing Nextcloud users will have user accounts automatically created on their next login
4. All existing functionality remains unchanged for regular users

## Frontend Components

### Login Page
- Combined form with username/password fields
- "Anmelden" button for local login
- "Mit Nextcloud anmelden" button for OIDC flow
- Error display for failed login attempts or locked accounts

### Admin Panel
- Accessible via "Admin" button in AppBar (visible only to admins)
- Tab-based interface with user management and settings
- User table with inline actions
- Dialogs for create/edit/delete/password operations

### Protected Routes
- `/app/boxes` - Box management (requires authentication)
- `/app/boxes/:id/edit` - Box editing (requires authentication)
- `/app/admin` - Admin panel (requires ADMIN role)

## Testing

### Backend Tests

Run backend tests:
```bash
cd backend
mvn test -Djava.version=17 -Dmaven.compiler.source=17 -Dmaven.compiler.target=17
```

### Frontend Tests

Build frontend:
```bash
cd frontend
npm install
npm run build
```

## Troubleshooting

### Cannot login with default admin
- Ensure the application has started at least once (admin is created on first startup)
- Check logs for admin creation message
- Verify no environment variables override the default credentials

### Users table not created
- Check Flyway migration status
- Ensure database connection is working
- Check application logs for migration errors

### OIDC users not created automatically
- Verify OIDC configuration is correct
- Check CustomOidcUserService logs
- Ensure database connection is available

### Account locked
- Wait 1 hour for automatic unlock
- Admin can manually unlock via admin panel
- Check `locked_until` timestamp in database

## Future Enhancements

Potential future improvements:
- Email verification for local accounts
- Password reset functionality
- Two-factor authentication
- User activity logging
- Session management (view/revoke active sessions)
- Project-based permissions (placeholder exists in User entity)

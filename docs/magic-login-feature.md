# Magic Login Link Feature

## Overview

The Magic Login Link feature allows administrators to generate passwordless login links for users. This is particularly useful for:
- Setting up passwordless accounts that can only be accessed via magic links
- Providing temporary access without sharing passwords
- Simplifying the onboarding process for new users

## How It Works

1. **Generate Magic Link**: Admins can generate a magic login link for any user through the admin panel
2. **Send Link**: The admin copies the generated link and shares it with the user (via email, chat, etc.)
3. **User Clicks Link**: When the user clicks the link, they are automatically authenticated and redirected to the application
4. **Session Created**: A secure session is created, identical to normal login sessions

## Configuration

The feature can be configured via environment variables:

```bash
# Magic Link validity period (default: 24 hours)
MAGIC_LINK_VALID_HOURS=24

# Frontend URL used to construct the magic login link
FRONTEND_URL=https://your-app.example.com
```

## Admin Panel Usage

### Creating a Passwordless User

1. Navigate to the Admin Panel (`/app/admin`)
2. Click "New User"
3. Fill in username and name
4. **Leave the password field empty** for a passwordless account
5. Select the user role (USER or ADMIN)
6. Save the user

Passwordless users will be indicated with a "Passwordless" chip in the user list.

### Generating a Magic Login Link

1. In the Admin Panel user list, locate the user
2. Click the link icon (🔗) in the Actions column
3. A dialog will appear showing:
   - The full magic login URL
   - The expiration date/time
4. Click "Copy Link" to copy the URL to clipboard
5. Share the link with the user via your preferred communication channel

**Note**: Each user can have only one active magic link at a time. Generating a new link will invalidate any previously generated links for that user.

## Security Considerations

- **Token Expiry**: Magic login tokens expire after the configured time period (default 24 hours)
- **One Active Token**: Only one active magic login token exists per user at any time
- **Reusable Until Expiry**: Tokens can be used multiple times until they expire
- **Automatic Invalidation**: Tokens are automatically invalidated when:
  - A new token is generated for the same user
  - The user is disabled
  - The user is deleted
- **User Status**: Disabled users cannot use magic login tokens, even if the token is valid
- **Secure Generation**: Tokens are generated using cryptographically secure random UUIDs
- **Session Security**: Magic login creates the same secure session as normal authentication methods

## API Endpoints

### Generate Magic Link (Admin Only)

```http
POST /api/v1/admin/users/{userId}/magic-link
Authorization: Required (ADMIN role)
Content-Type: application/json

Request Body (optional):
{
  "expiresInHours": 24
}

Response:
{
  "token": "a0b1c2d3-...-uuid",
  "url": "https://app.example.com/magic-login?token=a0b1c2d3-...-uuid",
  "expiresAt": "2025-12-29T10:23:45Z"
}
```

### Magic Login (Public)

```http
GET /api/v1/auth/magic-login?token={token}
Authorization: Not required

Success: Redirects to frontend with session created
Failure: Redirects to frontend with error parameter
```

## Database Schema

The `magic_login_tokens` table stores all magic login tokens:

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT | Primary key |
| token | VARCHAR(255) | Unique UUID token |
| user_id | BIGINT | Foreign key to users table |
| created_at | TIMESTAMP | Token creation timestamp |
| expiry_date | TIMESTAMP | Token expiration timestamp |
| used | BOOLEAN | Audit flag (token remains reusable) |

Indexes:
- `idx_magic_token` on `token` for fast lookups
- `idx_magic_user` on `user_id` for finding user's tokens
- `idx_magic_expiry` on `expiry_date` for cleanup of expired tokens

## Frontend Integration

The frontend automatically handles magic login redirects:

1. User clicks magic login link: `https://app.example.com/?magicLogin=success`
2. Backend validates token and creates session
3. Backend redirects to frontend
4. Frontend detects authenticated session and loads the application

## Troubleshooting

### Magic Link Doesn't Work

- **Check Expiration**: Verify the link hasn't expired
- **User Status**: Ensure the user account is enabled
- **Token Validity**: A new token may have been generated, invalidating the old one
- **Frontend URL**: Verify `FRONTEND_URL` is correctly configured

### Cannot Generate Magic Link

- **Admin Role**: Only users with ADMIN role can generate magic links
- **User Exists**: The user must exist in the system
- **Authentication**: You must be logged in as an admin

## Best Practices

1. **Short Validity**: For highly sensitive accounts, use shorter validity periods (e.g., 1-2 hours)
2. **Secure Sharing**: Share magic links through secure, encrypted communication channels
3. **Regular Cleanup**: The system automatically tracks used tokens; consider periodic cleanup of expired tokens
4. **Passwordless Strategy**: Use passwordless accounts for:
   - Temporary access
   - Service accounts
   - External collaborators
   - Demo accounts
5. **Password Option**: Even passwordless users can be assigned a password later via the "Set Password" action in the admin panel

# AI Recruitment Event Platform - Project Documentation

## 1. Project Overview

This project is a Spring Boot backend for managing recruitment or hackathon-style events. It supports:
- Event creation with automatic status computation and QR code generation
- Participant registration with multipart file uploads (resume/photo)
- AI-powered resume analysis using Google Gemini API
- Panelist management with invite-based registration and availability tracking
- Template-based feedback collection system
- Squad management with auto-generation capabilities
- JWT-based authentication with role-based access control (ADMIN, PANELIST, USER)
- Dashboard views for admins and panelists
- Email notifications for key events

Base URL:

```text
http://localhost:8080
```

Swagger UI:

```text
http://localhost:8080/swagger-ui.html
```

OpenAPI JSON:

```text
http://localhost:8080/v3/api-docs
```

## 2. Technology Stack

- Java 21
- Spring Boot 3.3.6
- Spring Web
- Spring Data JPA
- Spring Security with JWT
- PostgreSQL
- Jakarta Validation
- Lombok
- Google Gemini API for AI resume analysis
- ZXing for QR code generation
- Springdoc OpenAPI
- Maven
- Spring Mail for email notifications

## 3. Application Configuration

Main configuration file:

```text
src/main/resources/application.properties
```

Environment-based configuration (via `.env` file or system environment variables):

```properties
# Database
DB_URL=jdbc:postgresql://localhost:5432/hackathon_db
DB_USERNAME=postgres
DB_PASSWORD=1234

# Server
PORT=8080
APP_BASE_URL=http://localhost:8080
APP_FRONTEND_URL=http://localhost:3000
APP_QR_URL_BASE=http://localhost:8080

# JWT
jwt.secret=changeit1234567890changeit1234567890changeit1234567890  # Minimum 90 chars
jwt.expiration-ms=86400000  # 24 hours

# Gemini AI
GEMINI_API_KEY=your-gemini-api-key
GEMINI_MODEL=gemini-2.5-flash

# Email/SMTP
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=your-email@gmail.com
SMTP_PASSWORD=your-app-password
SMTP_AUTH=true
SMTP_STARTTLS=true
SMTP_CONNECTION_TIMEOUT=5000
SMTP_TIMEOUT=5000
SMTP_WRITETIMEOUT=5000
SMTP_FROM=noreply@recruitment-platform.com
```

File Upload Configuration:

```properties
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=20MB

app.upload-dir=uploads
```

Uploaded files are stored under:

```text
uploads/
  ├── resumes/
  ├── photos/
  └── qrcodes/
```

Uploaded files are served back by Spring MVC through `WebConfig`:

- `/uploads/**` is mapped to the configured `app.upload-dir`.
- The file resource location is resolved as an absolute `file:` directory URI and always ends with `/`.
- This matters when the project path contains spaces, because the upload directory URI must not be double-encoded.

Spring Security allows public read access to uploaded files:

```text
/uploads/**
```

This lets browser URLs such as the following open without a JWT token:

```text
http://localhost:8080/uploads/resumes/{stored-file-name}.pdf
http://localhost:8080/uploads/photos/{stored-file-name}.jpg
http://localhost:8080/uploads/qrcodes/{stored-qr-file-name}.png
```

Troubleshooting:
- If a newly uploaded file returns 403, check `SecurityConfig` includes `/uploads/**` in `permitAll()`.
- If it returns "No static resource", check the file exists under `uploads/` and restart the Spring Boot server after changing `WebConfig`.

## 4. Implementation Flow

### Events

When an event is created via POST `/api/events`:

1. Admin provides: `name`, `description`, `startDate`, `endDate` (optional)
2. System automatically computes the `status` based on UTC today:
   - If `startDate` is null → `OPEN` (undated events default to open)
   - If today < `startDate` → `UPCOMING`
   - If today > `endDate` (or `startDate` if no `endDate`) → `CLOSED`
   - Otherwise → `OPEN`
3. Event is saved to database
4. Registration URL is constructed: `http://localhost:8080/participants/register?eventId={eventId}`
5. QR code image is generated for the registration URL
6. QR code is stored in `uploads/qrcodes/` folder
7. Both `registrationUrl` and `qrCodeUrl` are persisted on the event record
8. Event response includes full details with status, URLs, and QR code path

Event statuses:
- `UPCOMING`: Start date is in the future
- `OPEN`: Event is active and accepting registrations
- `CLOSED`: Event end date has passed
- `CANCELLED`: Event was manually cancelled (terminal state)

### Participant Registration

Participants can register via POST `/api/participants/register` in two ways:

**Option 1: Multipart form with optional file uploads**
- Content-Type: `multipart/form-data`
- Fields: `eventId`, `name`, `email`, `techStack`, `phone`, `experienceYears`, `resume` (file), `photo` (file)

**Option 2: JSON request without files**
- Content-Type: `application/json`
- Fields: `eventId`, `name`, `email`, `techStack`, `phone`, `experienceYears`

During pre-registration:

1. Event existence is validated
2. Email uniqueness is verified (prevents duplicate registrations)
3. **Concurrent parallel tasks** (independent I/O operations):
   - **Resume File Storage**: Upload to `uploads/resumes/` (returns resumeUrl)
   - **Photo File Storage**: Upload to `uploads/photos/` (returns photoUrl)
4. Participant code is generated from database ID: `PART-{4-digit-padded-id}` (e.g., `PART-0042`)
5. Participant record is created with status `REGISTERED`
6. **No AI analysis runs at this stage** (`aiScore`, `skills`, `resumeAnalysisJson` remain unset)
7. **Asynchronous email notification**: Registration confirmation sent to participant's email with their code

### Venue Check-In

After scanning the event QR code, participant hits `GET /api/participants/check-in/qr?eventId={eventId}`.

The response contains navigation options:
- Message/instructions for check-in
- Pre-registration URL for fallback edits/registration
- Verification endpoint: `POST /api/participants/check-in/verify`

Check-in verification request:

```json
{
  "email": "participant@example.com",
  "participantCode": "PART-0042"
}
```

Check-in flow:

1. Participant is verified using `email + participantCode`
2. Status changes from `REGISTERED` to `CHECKED_IN`
3. Resume AI analysis is triggered asynchronously (Gemini)
4. AI result is stored later in participant fields: `aiScore`, `skills`, `resumeAnalysisJson`
5. Check-in response returns immediately with `resumeAnalysisTriggered=true`

### Panelist Management

#### Panelist Invite System

Admin creates an invite via POST `/api/panelists/invite`:

1. A unique `token` (32-char UUID) is generated
2. Token validity period: 48 hours from creation
3. Full registration URL is returned: `{frontendUrl}/panelist-register/{token}`
4. Admin shares the URL and/or token with the panelist
5. Admin can view all active (unused, non-expired) invites via GET `/api/panelists/invite/active`

#### Panelist Registration

Public endpoint: `GET /api/panelists/invite/validate/{token}` validates token before form display.

Panelist self-registers via `POST /api/panelists/register/{token}`:

Fields:
- `name`: Full name
- `email`: Work email (must be unique across all panelists)
- `domain`: Expertise domain (e.g., "Backend", "Frontend", "DevOps")
- `password`: Login password
- `availability`: Select from enum:
  - `FULLDAY`: Available entire day
  - `FORENOON`: 9:00 AM - 12:00 PM
  - `AFTERNOON`: 1:00 PM - 5:00 PM
  - `EVENING`: After 5:00 PM
  - `CUSTOM`: Custom time slot (requires `customAvailabilityTime`)
- `customAvailabilityTime`: Only required when `availability=CUSTOM` (e.g., "14:00-17:00")

On success:
1. Token is marked as used and expires
2. Panelist account created with credentials
3. Panelist receives registration confirmation email
4. Login credentials created for dashboard access

### Feedback System

#### Feedback Templates

Templates are pre-configured by system. Admins can retrieve template fields via GET `/api/feedback/templates/{feedbackType}`:

Supported feedback types:
- `TECHNICAL`: Technical skills assessment
- `COMMUNICATION`: Communication skills assessment
- `CULTURAL_FIT`: Cultural fit evaluation
- (Extensible via enum)

Each template defines fields with:
- `fieldName`: Name of the field
- `fieldType`: `TEXT`, `NUMBER_RATING`, `BOOLEAN`, `REFERENCE`
- `isRequired`: Whether field must be filled

#### Feedback Submission

Panelist submits feedback via POST `/api/feedback/submit`:

Request body:
```json
{
  "participantId": 42,
  "feedbackType": "TECHNICAL",
  "details": [
    {
      "fieldName": "technical_score",
      "value": "8"
    },
    {
      "fieldName": "comments",
      "value": "Strong backend knowledge"
    }
  ]
}
```

Constraints:
- Each participant can have **only one feedback record per feedback type** (unique constraint)
- Validates that participant exists
- Validates that all required template fields are provided
- Stores submission timestamp

#### Feedback Retrieval

- `GET /api/feedback/status/{participantId}`: Aggregate feedback status for a participant
- `GET /api/feedback/participant/{participantId}`: Detailed feedback records for participant
- `GET /api/feedback/by-participant/{participantId}`: Alias for above (deprecated)

### Squads

Squads are participant grouping units within an event. Two creation methods:

#### Manual Squad Creation

POST `/api/squads`:
```json
{
  "eventId": 1,
  "name": "Squad A"
}
```

Then add members: `POST /api/squads/{squadId}/members/{participantId}`

#### Auto-Generation

POST `/api/squads/auto-generate`:
```json
{
  "eventId": 1,
  "squadCount": 5,
  "autoAssignMode": "BALANCED"  // e.g., distribute participants evenly
}
```

Returns list of auto-generated squads with participants distributed.

### Dashboard

#### Admin Dashboard

GET `/api/dashboard/summary`:

Returns aggregate counts:
```json
{
  "totalEvents": 10,
  "totalParticipants": 250,
  "checkedInParticipants": 180,
  "totalAssignments": 150,
  "totalFeedbacks": 120,
  "totalEmailLogs": 500
}
```

#### Panelist Dashboard

GET `/api/dashboard/panelist/me` (authenticated panelist):

Returns:
```json
{
  "panelistId": 5,
  "panelistName": "John Doe",
  "panelistEmail": "john@example.com",
  "assignedParticipantsCount": 8,
  "feedbackPageParticipants": [ { participant details } ],
  "feedbackSubmittedCount": 6,
  "feedbackPendingCount": 2,
  "totalEvents": 1
}
```

Alternative: `GET /api/dashboard/panelist/{panelistId}` (admin or targeted panelist)

### Authentication & Authorization

JWT-based authentication via POST `/api/auth/login`:

Request:
```json
{
  "username": "admin_user",
  "password": "password123"
}
```

Response:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "type": "Bearer",
  "username": "admin_user",
  "roles": ["ADMIN"]
}
```

Token is included in subsequent requests: `Authorization: Bearer {token}`

Roles and permissions:
- `ADMIN`: Can create/update/delete events, manage panelists, view dashboards, authorize registrations
- `PANELIST`: Can submit feedback, view their dashboard, access assigned participants
- `USER`: Participant-level access (minimal, mostly for participant endpoints)

Admin user registration via POST `/api/auth/register` (admin-only):
```json
{
  "username": "new_admin",
  "email": "admin@example.com",
  "password": "password123",
  "role": "ADMIN"
}
```

### File Storage

Files are stored in the `uploads/` directory structure:

```
uploads/
├── resumes/
│   └── {timestamp}_{originalFileName}.pdf
├── photos/
│   └── {timestamp}_{originalFileName}.jpg
└── qrcodes/
    └── {timestamp}_event_{eventId}.png
```

Each uploaded file:
1. Stored with timestamp prefix to ensure uniqueness
2. Resumes max 10MB, photos max 10MB, total request max 20MB
3. Accessible via relative URL path: `/uploads/{type}/{filename}`
4. Public read access (no auth required)

### System Health Check

GET `/api/system/keep-alive`:

Returns:
```json
{
  "status": "OK",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

Used for monitoring application availability.

## 5. Common Response Models

### Success Response

All successful responses follow standard HTTP status codes:

- `200 OK`: Successful GET, PUT operations
- `201 Created`: Successful POST operations that create new resources
- `204 No Content`: Successful DELETE operations
- Entity responses are returned in JSON format with all fields populated

### Error Response

All handled errors follow this standard shape:

```json
{
  "timestamp": "2026-06-25T12:30:00.000Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed or business rule violation",
  "validationErrors": {
    "email": "must be a well-formed email address",
    "eventId": "Event not found"
  }
}
```

Common status codes and scenarios:

- `400 Bad Request`: Validation errors, invalid field values, business logic violations
- `401 Unauthorized`: Missing JWT token or token expired
- `403 Forbidden`: Authenticated user lacks required role permission
- `404 Not Found`: Requested resource (event, participant, panelist) does not exist
- `500 Internal Server Error`: Unexpected server-side errors

### Authentication Response

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "type": "Bearer",
  "username": "admin_user",
  "roles": ["ADMIN"]
}
```

Use token in subsequent requests:
```
Authorization: Bearer {token}
```

## 6. Enums

### EventStatus

```
UPCOMING    - Event start date is in the future
OPEN        - Event is currently active and accepting registrations  
CLOSED      - Event end date has passed
CANCELLED   - Event was manually cancelled (terminal state)
```

### ParticipantStatus

```
REGISTERED  - Initial status after registration
CHECKED_IN  - Participant has checked in at the event
ASSIGNED    - Participant has been assigned to a panelist
SELECTED    - Participant has been selected (after feedback review)
REJECTED    - Participant has been rejected (after feedback review)
```

### PanelistAvailability

```
FULLDAY     - Available all day (9:00 AM - 5:00 PM)
FORENOON    - Available 9:00 AM - 12:00 PM
AFTERNOON   - Available 1:00 PM - 5:00 PM
EVENING     - Available after 5:00 PM
CUSTOM      - Custom time slot (specify via customAvailabilityTime)
```

### Role

```
ADMIN       - Full system access, event/panelist management
PANELIST    - Can submit feedback, view dashboard
USER        - Participant-level access
```

### FeedbackType

```
TECHNICAL       - Technical skills assessment
COMMUNICATION   - Communication skills assessment
CULTURAL_FIT    - Cultural fit evaluation
```

### FeedbackFieldType

```
TEXT            - Text input field
NUMBER_RATING   - Numeric rating (e.g., 1-10 scale)
BOOLEAN         - Yes/No toggle
REFERENCE       - Reference/recommendation field
```

### Recommendation

```
HIRE            - Recommend for hiring
HOLD            - Keep on hold for further review
REJECT          - Recommend rejection
```

## 7. Endpoint Documentation

### 7.1 Events

#### Create Event

```http
POST /api/events
Content-Type: application/json
```

Request:

```json
{
  "name": "AI Hiring Drive 2026",
  "description": "Campus and lateral recruitment event.",
  "eventDate": "2026-07-10",
  "status": "OPEN"
}
```

Validation:

- `name` is required.
- `eventDate` is required and must be today or a future date.
- `status` is optional. If omitted, it defaults to `OPEN`.

Response `201 Created`:

```json
{
  "id": 1,
  "name": "AI Hiring Drive 2026",
  "description": "Campus and lateral recruitment event.",
  "eventDate": "2026-07-10",
  "status": "OPEN",
  "registrationUrl": "http://localhost:8080/api/participants/register?eventId=1",
  "qrCodeUrl": "/uploads/qrcodes/uuid-registration.png"
}
```

#### Get All Events

```http
GET /api/events
```

Response `200 OK`:

```json
[
  {
    "id": 1,
    "name": "AI Hiring Drive 2026",
    "description": "Campus and lateral recruitment event.",
    "eventDate": "2026-07-10",
    "status": "OPEN",
    "registrationUrl": "http://localhost:8080/api/participants/register?eventId=1",
    "qrCodeUrl": "/uploads/qrcodes/uuid-registration.png"
  }
]
```

#### Get Event By ID

```http
GET /api/events/{id}
```

Response `200 OK`:

```json
{
  "id": 1,
  "name": "AI Hiring Drive 2026",
  "description": "Campus and lateral recruitment event.",
  "eventDate": "2026-07-10",
  "status": "OPEN",
  "registrationUrl": "http://localhost:8080/api/participants/register?eventId=1",
  "qrCodeUrl": "/uploads/qrcodes/uuid-registration.png"
}
```

#### Delete Event

```http
DELETE /api/events/{id}
Authorization: Bearer <admin-token>
```

Access: `ROLE_ADMIN`

Response `204 No Content`.

Implementation details:

- Returns `404 Not Found` when the event does not exist.
- Deletes squads for the event and their squad member rows.
- Deletes participants registered for the event.
- Deletes dependent participant records before deleting those participants: assignments, attendance, feedback, and squad memberships.

### 7.2 Participants

#### Register Participant - JSON

Use this when no files are uploaded.

```http
POST /api/participants/register
Content-Type: application/json
```

Request:

```json
{
  "eventId": 1,
  "name": "Test User",
  "email": "test@example.com",
  "phone": "9876543210",
  "experienceYears": 2
}
```

Validation:

- `eventId` is required.
- `name` is required.
- `email` is required and must be valid.
- `experienceYears` must be `0` or greater when provided.

Response `201 Created`:

```json
{
  "id": 2,
  "participantCode": "PART-0002",
  "eventId": 1,
  "name": "Test User",
  "email": "test@example.com",
  "phone": "9876543210",
  "experienceYears": 2,
  "resumeUrl": null,
  "photoUrl": null,
  "aiScore": 62,
  "skills": "Java, Spring, Spring Boot, PostgreSQL",
  "status": "REGISTERED"
}
```

#### Register Participant - Multipart With Files

Use this when uploading resume/photo files.

```http
POST /api/participants/register
Content-Type: multipart/form-data
```

Form fields:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `eventId` | number | yes | Event ID |
| `name` | string | yes | Participant name |
| `email` | string | yes | Participant email |
| `phone` | string | no | Participant phone |
| `experienceYears` | number | no | Years of experience |
| `resume` | file | no | Resume file |
| `photo` | file | no | Photo file |

Example curl:

```bash
curl -X POST http://localhost:8080/api/participants/register \
  -F "eventId=1" \
  -F "name=Test User" \
  -F "email=test@example.com" \
  -F "phone=9876543210" \
  -F "experienceYears=2" \
  -F "resume=@resume.pdf" \
  -F "photo=@photo.jpg"
```

Response `201 Created`:

```json
{
  "id": 2,
  "participantCode": "PART-0002",
  "eventId": 1,
  "name": "Test User",
  "email": "test@example.com",
  "phone": "9876543210",
  "experienceYears": 2,
  "resumeUrl": "/uploads/resumes/uuid-resume.pdf",
  "photoUrl": "/uploads/photos/uuid-photo.jpg",
  "aiScore": 65,
  "skills": "Java, Spring Boot",
  "status": "REGISTERED"
}
```

#### Get All Participants

```http
GET /api/participants
```

Response `200 OK`:

```json
[
  {
    "id": 2,
    "participantCode": "PART-0002",
    "eventId": 1,
    "name": "Test User",
    "email": "test@example.com",
    "phone": "9876543210",
    "experienceYears": 2,
    "resumeUrl": "/uploads/resumes/uuid-resume.pdf",
    "photoUrl": "/uploads/photos/uuid-photo.jpg",
    "aiScore": 65,
    "skills": "Java, Spring Boot",
    "status": "REGISTERED"
  }
]
```

#### Get Participant By ID

```http
GET /api/participants/{id}
```

Response `200 OK` uses the same participant shape shown above.

#### Get Participants By Event

```http
GET /api/participants/event/{eventId}
```

Response `200 OK`:

```json
[
  {
    "id": 2,
    "participantCode": "PART-0002",
    "eventId": 1,
    "name": "Test User",
    "email": "test@example.com",
    "phone": "9876543210",
    "experienceYears": 2,
    "resumeUrl": "/uploads/resumes/uuid-resume.pdf",
    "photoUrl": "/uploads/photos/uuid-photo.jpg",
    "aiScore": 65,
    "skills": "Java, Spring Boot",
    "status": "REGISTERED"
  }
]
```

#### Delete Participant

```http
DELETE /api/participants/{id}
Authorization: Bearer <admin-token>
```

Access: `ROLE_ADMIN`

Response `204 No Content`.

Implementation details:

- Returns `404 Not Found` when the participant does not exist.
- Deletes dependent records before deleting the participant: assignments, attendance, feedback, and squad memberships.

### 7.3 Venue Check-In

#### Check-In QR Navigation

```http
GET /api/participants/check-in/qr?eventId={eventId}
```

Response `200 OK`:

```json
{
  "eventId": 1,
  "eventName": "AI Hiring Hackathon",
  "message": "Welcome. Verify your registration using email and participant code.",
  "preRegistrationUrl": "http://localhost:8080/participants/register?eventId=1",
  "verifyEndpoint": "/api/participants/check-in/verify",
  "method": "POST",
  "requiredFields": "email,participantCode"
}
```

#### Verify + Check-In

```http
POST /api/participants/check-in/verify
Content-Type: application/json
```

Request:

```json
{
  "email": "participant@example.com",
  "participantCode": "PART-0002"
}
```

Validation:

- `email` is required and must be valid.
- `participantCode` is required.
- Duplicate check-in returns `400 Bad Request`.

Response `200 OK`:

```json
{
  "participantId": 2,
  "participantCode": "PART-0002",
  "name": "Test User",
  "email": "participant@example.com",
  "status": "CHECKED_IN",
  "message": "Check-in successful. Resume analysis has been queued.",
  "dashboardUrl": "http://localhost:3000/participant/dashboard",
  "supportUrl": "http://localhost:3000/support",
  "resumeAnalysisTriggered": true
}
```

### 7.4 Panelists

#### Create Panelist

```http
POST /api/panelists
Content-Type: application/json
```

Request:

```json
{
  "name": "Priya Menon",
  "email": "priya.menon@example.com",
  "domain": "Backend Engineering"
}
```

Validation:

- `name` is required.
- `email` is required and must be valid.
- `domain` is required.

Response `201 Created`:

```json
{
  "id": 1,
  "name": "Priya Menon",
  "email": "priya.menon@example.com",
  "domain": "Backend Engineering"
}
```

#### Get All Panelists

```http
GET /api/panelists
```

Response `200 OK`:

```json
[
  {
    "id": 1,
    "name": "Priya Menon",
    "email": "priya.menon@example.com",
    "domain": "Backend Engineering"
  }
]
```

#### Delete Panelist

```http
DELETE /api/panelists/{id}
Authorization: Bearer <admin-token>
```

Access: `ROLE_ADMIN`

Response `204 No Content`.

Implementation details:

- Returns `404 Not Found` when the panelist does not exist.
- Updates participants assigned to the panelist back to `REGISTERED`.
- Deletes assignments and feedback linked to the panelist.
- Deletes the linked user account when a user exists with the panelist email.

### 7.5 Assignments

#### Create Assignment

```http
POST /api/assignments
Content-Type: application/json
```

Request:

```json
{
  "participantId": 2,
  "panelistId": 1
}
```

Validation:

- `participantId` is required and must exist.
- `panelistId` is required and must exist.

Response `201 Created`:

```json
{
  "id": 1,
  "participantId": 2,
  "panelistId": 1
}
```

Side effect:

- Participant status becomes `ASSIGNED`.

#### Get All Assignments

```http
GET /api/assignments
```

Response `200 OK`:

```json
[
  {
    "id": 1,
    "participantId": 2,
    "panelistId": 1
  }
]
```

### 7.6 Feedback

#### Create Feedback

```http
POST /api/feedback
Content-Type: application/json
```

Request:

```json
{
  "participantId": 2,
  "panelistId": 1,
  "technicalRating": 4,
  "communicationRating": 5,
  "recommendation": "HIRE",
  "comments": "Strong fundamentals and clear project explanation."
}
```

Validation:

- `participantId` is required and must exist.
- `panelistId` is required and must exist.
- `technicalRating` is required and must be between `1` and `5`.
- `communicationRating` is required and must be between `1` and `5`.
- `recommendation` is required. Allowed values: `HIRE`, `HOLD`, `REJECT`.

Response `201 Created`:

```json
{
  "id": 1,
  "participantId": 2,
  "panelistId": 1,
  "technicalRating": 4,
  "communicationRating": 5,
  "recommendation": "HIRE",
  "comments": "Strong fundamentals and clear project explanation."
}
```

#### Get All Feedback

```http
GET /api/feedback
```

Response `200 OK`:

```json
[
  {
    "id": 1,
    "participantId": 2,
    "panelistId": 1,
    "technicalRating": 4,
    "communicationRating": 5,
    "recommendation": "HIRE",
    "comments": "Strong fundamentals and clear project explanation."
  }
]
```

### 7.7 Squads

#### Create Squad

```http
POST /api/squads
Content-Type: application/json
```

Request:

```json
{
  "eventId": 1,
  "name": "Backend Squad"
}
```

Validation:

- `eventId` is required and must exist.
- `name` is required.

Response `201 Created`:

```json
{
  "id": 1,
  "eventId": 1,
  "name": "Backend Squad"
}
```

#### Add Participant To Squad

```http
POST /api/squads/{squadId}/members/{participantId}
```

Response `201 Created`:

```json
{
  "id": 1,
  "squadId": 1,
  "participantId": 2
}
```

#### Get Squads By Event

```http
GET /api/squads/event/{eventId}
```

Response `200 OK`:

```json
[
  {
    "id": 1,
    "eventId": 1,
    "name": "Backend Squad"
  }
]
```

#### Get Squad Members

```http
GET /api/squads/{squadId}/members
```

Response `200 OK`:

```json
[
  {
    "id": 2,
    "participantCode": "PART-0002",
    "eventId": 1,
    "name": "Test User",
    "email": "test@example.com",
    "phone": "9876543210",
    "experienceYears": 2,
    "resumeUrl": "/uploads/resumes/uuid-resume.pdf",
    "photoUrl": "/uploads/photos/uuid-photo.jpg",
    "aiScore": 65,
    "skills": "Java, Spring Boot",
    "status": "ASSIGNED"
  }
]
```

#### Delete Squad

```http
DELETE /api/squads/{id}
Authorization: Bearer <admin-token>
```

Access: `ROLE_ADMIN`

Response `204 No Content`.

Implementation details:

- Returns `404 Not Found` when the squad does not exist.
- Deletes squad member rows for the squad.
- Does not delete participant records.

### 7.8 Dashboard

#### Get Dashboard Summary

```http
GET /api/dashboard/summary
```

Response `200 OK`:

```json
{
  "events": 1,
  "participants": 2,
  "checkedIn": 1,
  "assigned": 1,
  "feedbackSubmitted": 1,
  "emailsSent": 2
}
```

## 8. Local Run Steps

1. Create a PostgreSQL database:

```sql
CREATE DATABASE hackathon_db;
```

2. Confirm credentials in `application.properties`.

3. Start the application:

```bash
./mvnw spring-boot:run
```

On Windows:

```bat
mvnw.cmd spring-boot:run
```

4. Open Swagger:

```text
http://localhost:8080/swagger-ui.html
```

## 9. Testing

Run tests:

```bash
./mvnw test
```

If running on Java 24 with the current Mockito/Byte Buddy dependency versions, use:

```bash
./mvnw "-DargLine=-Dnet.bytebuddy.experimental=true" test
```

## 11. Security and Authentication

### 11.1 Security Overview

This project uses Spring Security 6 with JWT authentication and role-based authorization.

Key security components:
- `spring-boot-starter-security`
- `io.jsonwebtoken:jjwt-api`, `jjwt-impl`, `jjwt-jackson`
- `BCryptPasswordEncoder` for password hashing
- `@EnableMethodSecurity` in `SecurityConfig`
- `SecurityFilterChain` configuration (no `WebSecurityConfigurerAdapter`)
- `JwtAuthenticationFilter` for token validation

### 11.2 Authentication Model

The project introduces a `User` entity stored in the `users` table.

#### User entity

Fields:
- `id` (auto-generated)
- `username` (unique)
- `email` (unique)
- `password` (BCrypt hashed)
- `role` (`ROLE_ADMIN` or `ROLE_PANELIST`)

#### Role enum

```text
ROLE_ADMIN
ROLE_PANELIST
```

### 11.3 Default Admin Seeder

`AdminUserSeeder` creates a default admin user when the `users` table does not already have `admin@hackathon.com`.

Default admin:
- Email: `admin@hackathon.com`
- Username: `admin`
- Password: `Admin@123`
- Role: `ROLE_ADMIN`

### 11.4 JWT Configuration

Configuration properties in `src/main/resources/application.properties`:

```properties
jwt.secret=changeit1234567890changeit1234567890changeit1234567890
jwt.expiration-ms=86400000
```

The JWT flow is:
1. Client POSTs credentials to `/api/auth/login`
2. Server authenticates using `AuthenticationManager`
3. `JwtService` issues a JWT token with username and role claims
4. Client sends `Authorization: Bearer <token>` on protected requests
5. `JwtAuthenticationFilter` validates the token and sets the security context

### 11.5 Auth Endpoints

#### Login

```http
POST /api/auth/login
Content-Type: application/json
```

Request body:

```json
{
  "email": "admin@hackathon.com",
  "password": "Admin@123"
}
```

Response body:

```json
{
  "token": "jwt-token",
  "role": "ROLE_ADMIN",
  "username": "admin"
}
```

This endpoint is public and does not require authentication.

#### Register User

```http
POST /api/auth/register
Content-Type: application/json
```

Request body:

```json
{
  "username": "panelist1",
  "email": "panelist1@example.com",
  "password": "Panelist@123",
  "role": "ROLE_PANELIST"
}
```

Response body:

```json
{
  "token": "jwt-token",
  "role": "ROLE_PANELIST",
  "username": "panelist1"
}
```

This endpoint requires `ROLE_ADMIN`.

### 11.6 Authorization Matrix

#### Public endpoints
- `POST /api/auth/login`
- `POST /api/participants/register`
- `GET /api/events`
- `GET /api/events/{id}`
- Swagger and OpenAPI UI endpoints (`/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`)

#### Admin-only endpoints
- `/api/dashboard/**`
- `/api/panelists/**`
- `/api/assignments/**`
- `/api/squads/**`
- `POST /api/events`
- `PUT /api/events/**`
- `DELETE /api/events/**`
- `POST /api/auth/register`

#### Admin + Panelist endpoints
- `GET /api/participants/**`
- `PUT /api/participants/**`
- `GET /api/feedback/**`
- `POST /api/feedback/**`
- `PUT /api/feedback/**`

### 11.7 Method-level Security

Controllers use `@PreAuthorize` annotations to enforce method-level authorization.

Examples:
- `@PreAuthorize("hasRole('ADMIN')")` for admin-only routes
- `@PreAuthorize("hasAnyRole('ADMIN','PANELIST')")` for shared access routes

### 11.8 Swagger / OpenAPI JWT Support

Swagger UI is configured to support bearer token authorization.
The OpenAPI document includes a security scheme named `Bearer Authentication`.

When using Swagger UI, users can click `Authorize` and provide a JWT token as `Bearer <token>`.

### 11.9 Security Classes

Important security classes:
- `com.hackathon.config.SecurityConfig`
- `com.hackathon.security.CustomUserDetailsService`
- `com.hackathon.security.JwtService`
- `com.hackathon.security.JwtAuthenticationFilter`
- `com.hackathon.service.AuthenticationService`
- `com.hackathon.controller.AuthController`
- `com.hackathon.config.AdminUserSeeder`

### 11.10 Notes on Authentication Behavior

- The `User` details are loaded by email in `CustomUserDetailsService`.
- Passwords are stored with BCrypt hashing.
- If a JWT is invalid or expired, the request is rejected with `401 Unauthorized`.
- Access denied due to insufficient role returns `403 Forbidden`.
- The registration endpoint for participants remains public and does not require authentication.

### 11.11 Database Impact

The new `users` table is created automatically by JPA with:
- `id`
- `username`
- `email`
- `password`
- `role`

Ensure your database migration or schema update strategy includes this table.

### 11.12 Testing Security

To test login and a protected route:

1. Login as admin:
   ```bash
   curl -X POST http://localhost:8080/api/auth/login \
     -H "Content-Type: application/json" \
     -d '{"email":"admin@hackathon.com","password":"Admin@123"}'
   ```
2. Use the returned JWT in the `Authorization` header:
   ```bash
   curl -X GET http://localhost:8080/api/dashboard/summary \
     -H "Authorization: Bearer <token>"
   ```

To test panelist access, create a panelist user with admin credentials and use their token for feedback or participant views.

## 12. CORS Configuration

### 12.1 CORS Overview

Cross-Origin Resource Sharing (CORS) is configured to allow requests from frontend applications running on different origins.

The CORS configuration is implemented in `CorsConfig` and integrated into the Spring Security filter chain via `SecurityConfig`.

### 12.2 Allowed Origins

The backend allows requests from:
- `http://localhost:5173` (Vite dev server, primary frontend dev origin)
- `http://127.0.0.1:5173` (Vite loopback alternative)
- `http://localhost:3000` (alternative frontend dev origin)
- `http://localhost:3001` (alternative dev origin)

To add production origins, update the `CorsConfig.corsConfigurationSource()` method:

```java
configuration.addAllowedOrigin("https://yourdomain.com");
```

### 12.3 Allowed Methods and Headers

The backend allows:
- Methods: `GET`, `POST`, `PUT`, `DELETE`, `OPTIONS`, `PATCH`
- Headers: `*` (all), with explicit support for `Authorization` and `Content-Type`
- Credentials: enabled (for cookie-based or token-based auth)

### 12.4 CORS Preflight Requests

- Preflight requests (`OPTIONS`) are cached for 3600 seconds (1 hour).
- This reduces redundant preflight requests during active frontend sessions.

### 12.5 Frontend Integration with CORS

Frontends can make requests to the backend without CORS errors:

```javascript
// Example: Fetch with Bearer token
fetch('http://localhost:8080/api/dashboard/summary', {
  method: 'GET',
  headers: {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  }
});
```

Axios users can configure an interceptor to automatically attach the JWT token (as shown in `FRONTEND_INTEGRATION.md`).

### 12.6 CORS Configuration Files

Files:
- `com.hackathon.config.CorsConfig` - CORS bean definition
- `com.hackathon.config.SecurityConfig` - CORS integration into Spring Security filter chain

No XML configuration or properties file changes are required; CORS is implemented entirely via Spring beans.

### 12.7 Notes on Security and CORS

- CORS does not bypass authentication or authorization; it only allows cross-origin requests.
- All endpoints still enforce role-based authorization via `@PreAuthorize` and `SecurityFilterChain`.
- The JWT token is required for protected endpoints, regardless of CORS settings.
- Token must be sent in the `Authorization: Bearer <token>` header.

This makes the dashboard and list endpoints useful immediately after the first startup.

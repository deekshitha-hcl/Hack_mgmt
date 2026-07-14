# API Endpoint Reference

Base URL: `http://localhost:8080`

All protected endpoints require:

```http
Authorization: Bearer <jwt-token>
```

Dates use ISO format (`YYYY-MM-DD`). Date-time values are returned as ISO local date-time strings.

## Roles and Public Access

| Access | Endpoints |
| --- | --- |
| Public | `POST /api/auth/login`, `POST /api/participants/register`, `GET /api/events`, `GET /api/events/{id}`, `GET /api/system/keep-alive`, `GET /api/panelists/invite/validate/{token}`, `POST /api/panelists/register/{token}` |
| `ROLE_ADMIN` | `POST /api/auth/register`, `POST /api/events`, `PUT /api/events/{id}`, `DELETE /api/events/{id}`, `DELETE /api/participants/{id}`, `/api/dashboard/**`, `POST /api/panelists/invite`, `GET /api/panelists/invite/active`, `DELETE /api/panelists/{id}`, `/api/squads/**` |
| `ROLE_ADMIN` or `ROLE_PANELIST` | `GET /api/panelists`, `GET /api/participants/**`, `/api/feedback/**` |

## Common Models

### Error Response

Returned for validation failures, missing resources, bad credentials, authorization failures, and unexpected errors.

```json
{
  "timestamp": "2026-06-25T16:30:00.000",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "validationErrors": {
    "email": "must be a well-formed email address"
  }
}
```

Common status codes:

| Status | Meaning |
| --- | --- |
| `400 Bad Request` | Validation failed, duplicate check-in, duplicate username/email, or invalid business action |
| `401 Unauthorized` | Missing/invalid token or invalid login credentials |
| `403 Forbidden` | Authenticated user does not have the required role |
| `404 Not Found` | Referenced event, participant, panelist, or squad does not exist |
| `500 Internal Server Error` | Unexpected server error |

### Event

```json
{
  "id": 1,
  "name": "AI Hiring Hackathon",
  "description": "Recruitment event for backend engineers",
  "startDate": "2026-07-01",
  "endDate": "2026-07-02",
  "status": "OPEN",
  "registrationUrl": "http://localhost:8080/api/participants/register?eventId=1",
  "qrCodeUrl": "http://localhost:8080/uploads/qrcodes/example-registration.png"
}
```

`status` values: `UPCOMING`, `OPEN`, `CLOSED`, `CANCELLED`. Status is auto-computed from `startDate` / `endDate` unless the event is explicitly cancelled, in which case it stays `CANCELLED`.

| Condition | Status |
| --- | --- |
| `startDate` is null | `OPEN` |
| today < `startDate` | `UPCOMING` |
| today is between `startDate` and `endDate` (inclusive) | `OPEN` |
| today > `endDate` (or `startDate` when no `endDate`) | `CLOSED` |

`CANCELLED` is a terminal manual state set through the update endpoint.

### Participant

```json
{
  "id": 1,
  "participantCode": "PART-0001",
  "eventId": 1,
  "name": "Asha Rao",
  "email": "asha@example.com",
  "techStack": "Java, Spring Boot, React",
  "phone": "+919876543210",
  "experienceYears": 3,
  "resumeUrl": "http://localhost:8080/uploads/resumes/resume.pdf",
  "photoUrl": "http://localhost:8080/uploads/photos/photo.png",
  "aiScore": 82,
  "skills": "Java, Spring, PostgreSQL",
  "status": "REGISTERED"
}
```

`status` values: `REGISTERED`, `CHECKED_IN`, `ASSIGNED`, `SELECTED`, `REJECTED`, `COMPLETED`.

### Panelist

```json
{
  "id": 1,
  "name": "Dr. Meera Shah",
  "email": "meera@example.com",
  "domain": "Backend",
  "availability": "CUSTOM",
  "customAvailabilityTime": "14:00-17:00"
}
```

`availability` values: `MORNING`, `AFTERNOON`, `CUSTOM`. `customAvailabilityTime` is only set when `availability` is `CUSTOM`.

### Panelist Invite

```json
{
  "token": "a1b2c3d4-e5f6-...",
  "registrationUrl": "https://yourfrontend.com/panelist/register?token=a1b2c3d4-e5f6-...",
  "expiresAt": "2026-07-16T10:00:00"
}
```

### Feedback

```json
{
  "id": 1,
  "participantId": 1,
  "panelistId": 1,
  "technicalRating": 5,
  "communicationRating": 4,
  "recommendation": "HIRE",
  "comments": "Strong backend fundamentals"
}
```

`recommendation` values: `HIRE`, `HOLD`, `REJECT`.

### Squad

```json
{
  "id": 1,
  "eventId": 1,
  "name": "Team Alpha"
}
```

### Squad Member

```json
{
  "id": 1,
  "squadId": 1,
  "participantId": 1
}
```

### Dashboard Summary

```json
{
  "events": 2,
  "participants": 35,
  "checkedIn": 0,
  "assigned": 0,
  "feedbackSubmitted": 18,
  "emailsSent": 35
}
```

### Keep Alive Response

```json
{
  "status": "UP",
  "database": "UP",
  "message": "System is healthy",
  "timestamp": "2026-06-25T16:30:00.000"
}
```

### Authentication Response

```json
{
  "token": "<jwt-token>",
  "role": "ROLE_ADMIN",
  "username": "admin"
}
```

## Authentication Endpoints

### Login

`POST /api/auth/login`

Access: Public

Content-Type: `application/json`

Request body:

| Field | Type | Required | Validation |
| --- | --- | --- | --- |
| `email` | string | Yes | Must be a valid email |
| `password` | string | Yes | Must not be blank |

Request example:

```json
{
  "email": "admin@example.com",
  "password": "password123"
}
```

Response: `200 OK`

```json
{
  "token": "<jwt-token>",
  "role": "ROLE_ADMIN",
  "username": "admin"
}
```

Notes:

- Use the returned `token` in the `Authorization` header for protected endpoints.
- Invalid credentials return `401 Unauthorized`.

### Register User

`POST /api/auth/register`

Access: `ROLE_ADMIN`

Content-Type: `application/json`

Request body:

| Field | Type | Required | Validation |
| --- | --- | --- | --- |
| `username` | string | Yes | Must not be blank; must be unique |
| `email` | string | Yes | Must be a valid email; must be unique |
| `password` | string | Yes | Must not be blank |
| `role` | string | Yes | `ROLE_ADMIN` or `ROLE_PANELIST` |

Request example:

```json
{
  "username": "panelist1",
  "email": "panelist1@example.com",
  "password": "password123",
  "role": "ROLE_PANELIST"
}
```

Response: `201 Created`

```json
{
  "token": "<jwt-token>",
  "role": "ROLE_PANELIST",
  "username": "panelist1"
}
```

## Event Endpoints

### Create Event

`POST /api/events`

Access: `ROLE_ADMIN`

Content-Type: `application/json`

Request body:

| Field | Type | Required | Validation |
| --- | --- | --- | --- |
| `name` | string | Yes | Must not be blank |
| `description` | string | No | Optional free-text description |
| `startDate` | date | Yes | ISO date (`YYYY-MM-DD`); the day registrations/event opens |
| `endDate` | date | No | ISO date; must be >= `startDate`. Omit for a single-day event |

Status is derived automatically from the dates. Do not pass a `status` field.

Request example:

```json
{
  "name": "AI Hiring Hackathon",
  "description": "Recruitment event for backend engineers",
  "startDate": "2026-07-01",
  "endDate": "2026-07-02"
}
```

Response: `201 Created`

```json
{
  "id": 1,
  "name": "AI Hiring Hackathon",
  "description": "Recruitment event for backend engineers",
  "startDate": "2026-07-01",
  "endDate": "2026-07-02",
  "status": "OPEN",
  "registrationUrl": "http://localhost:8080/api/participants/register?eventId=1",
  "qrCodeUrl": "http://localhost:8080/uploads/qrcodes/1-registration.png"
}
```

Notes:

- `endDate` before `startDate` returns `400 Bad Request`.
- Single-day event: omit `endDate` or set it equal to `startDate`.

### Get All Events

`GET /api/events`

Access: Public

Request: No request body.

Response: `200 OK`

```json
[
  {
    "id": 1,
    "name": "AI Hiring Hackathon",
    "description": "Recruitment event for backend engineers",
    "startDate": "2026-07-01",
    "endDate": "2026-07-02",
    "status": "OPEN",
    "registrationUrl": "http://localhost:8080/api/participants/register?eventId=1",
    "qrCodeUrl": "http://localhost:8080/uploads/qrcodes/1-registration.png"
  }
]
```

### Get Event By ID

`GET /api/events/{id}`

Access: Public

Path parameters:

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | number | Yes | Event ID |

Request: No request body.

Response: `200 OK`

```json
{
  "id": 1,
  "name": "AI Hiring Hackathon",
  "description": "Recruitment event for backend engineers",
  "startDate": "2026-07-01",
  "endDate": "2026-07-02",
  "status": "OPEN",
  "registrationUrl": "http://localhost:8080/api/participants/register?eventId=1",
  "qrCodeUrl": "http://localhost:8080/uploads/qrcodes/1-registration.png"
}
```

### Update Event

`PUT /api/events/{id}`

Access: `ROLE_ADMIN`

Content-Type: `application/json`

Path parameters:

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | number | Yes | Event ID |

Request body (all fields optional, only provided values are updated):

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `name` | string | No | New event name |
| `description` | string | No | New description |
| `startDate` | date | No | ISO date; triggers status recalculation |
| `endDate` | date | No | ISO date; must be >= `startDate`; triggers status recalculation |
| `cancelled` | boolean | No | When `true`, sets the event status to `CANCELLED` |

Request example:

```json
{
  "name": "AI Hiring Hackathon - Extended",
  "endDate": "2026-07-03"
}
```

Cancel example:

```json
{
  "cancelled": true
}
```

Response: `200 OK`

```json
{
  "id": 1,
  "name": "AI Hiring Hackathon - Extended",
  "description": "Recruitment event for backend engineers",
  "startDate": "2026-07-01",
  "endDate": "2026-07-03",
  "status": "OPEN",
  "registrationUrl": "http://localhost:8080/api/participants/register?eventId=1",
  "qrCodeUrl": "http://localhost:8080/uploads/qrcodes/1-registration.png"
}
```

Notes:

- Any change to `startDate` or `endDate` automatically recalculates `status`.
- Updating only `name` or `description` does not change `status`.
- Setting `cancelled` to `true` forces the event into `CANCELLED` and keeps it there across later date edits.
- `endDate` before the resolved `startDate` returns `400 Bad Request`.
- If the event does not exist, the API returns `404 Not Found`.

### Delete Event

`DELETE /api/events/{id}`

Access: `ROLE_ADMIN`

Path parameters:

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | number | Yes | Event ID |

Request: No request body.

Response: `204 No Content`

Notes:

- Deleting an event also deletes squads for that event, squad memberships, participants for that event, and dependent participant records such as feedback.
- If the event does not exist, the API returns `404 Not Found`.

## Participant Endpoints

### Register Participant - JSON

`POST /api/participants/register`

Access: Public

Content-Type: `application/json`

Request body:

| Field | Type | Required | Validation |
| --- | --- | --- | --- |
| `eventId` | number | Yes | Must reference an existing event |
| `name` | string | Yes | Must not be blank |
| `email` | string | Yes | Must be a valid email |
| `techStack` | string | Yes | Must not be blank |
| `phone` | string | No | Optional |
| `experienceYears` | number | No | Must be `0` or greater when provided |

Request example:

```json
{
  "eventId": 1,
  "name": "Asha Rao",
  "email": "asha@example.com",
  "techStack": "Java, Spring Boot, React",
  "phone": "+919876543210",
  "experienceYears": 3
}
```

Response: `201 Created`

```json
{
  "id": 1,
  "participantCode": "PART-0001",
  "eventId": 1,
  "name": "Asha Rao",
  "email": "asha@example.com",
  "techStack": "Java, Spring Boot, React",
  "phone": "+919876543210",
  "experienceYears": 3,
  "resumeUrl": null,
  "photoUrl": null,
  "aiScore": 82,
  "skills": "Java, Spring, PostgreSQL",
  "status": "REGISTERED"
}
```

Error response if participant with same email already exists: `400 Bad Request`

```json
{
  "timestamp": "2026-06-25T16:30:00.000",
  "status": 400,
  "error": "Bad Request",
  "message": "Participant already registered with email: asha@example.com"
}
```

Notes:

- Email must be unique across the system. Duplicate email registrations are rejected with `400 Bad Request`.
- If the event does not exist, returns `404 Not Found`.
- If validation fails, returns `400 Bad Request` with validation error details.

### Register Participant - Multipart

`POST /api/participants/register`

Access: Public

Content-Type: `multipart/form-data`

Form fields:

| Field | Type | Required | Validation |
| --- | --- | --- | --- |
| `eventId` | number | Yes | Must reference an existing event |
| `name` | string | Yes | Must not be blank |
| `email` | string | Yes | Must be a valid email |
| `techStack` | string | Yes | Must not be blank |
| `phone` | string | No | Optional |
| `experienceYears` | number | No | Must be `0` or greater when provided |
| `resume` | file | No | Optional file upload |
| `photo` | file | No | Optional file upload |

Request example:

```bash
curl -X POST http://localhost:8080/api/participants/register \
  -F "eventId=1" \
  -F "name=Asha Rao" \
  -F "email=asha@example.com" \
  -F "techStack=Java,Spring Boot,React" \
  -F "phone=+919876543210" \
  -F "experienceYears=3" \
  -F "resume=@resume.pdf" \
  -F "photo=@photo.png"
```

Response: `201 Created`

```json
{
  "id": 1,
  "participantCode": "PART-0001",
  "eventId": 1,
  "name": "Asha Rao",
  "email": "asha@example.com",
  "techStack": "Java, Spring Boot, React",
  "phone": "+919876543210",
  "experienceYears": 3,
  "resumeUrl": "http://localhost:8080/uploads/resumes/resume.pdf",
  "photoUrl": "http://localhost:8080/uploads/photos/photo.png",
  "aiScore": 82,
  "skills": "Java, Spring, PostgreSQL",
  "status": "REGISTERED"
}
```

Error response if participant with same email already exists: `400 Bad Request`

```json
{
  "timestamp": "2026-06-25T16:30:00.000",
  "status": 400,
  "error": "Bad Request",
  "message": "Participant already registered with email: asha@example.com"
}
```

Implementation notes:

- Email must be unique across the system. Duplicate email registrations are rejected with `400 Bad Request`.
- If the event does not exist, returns `404 Not Found`.
- Uploaded resumes are stored in `uploads/resumes/`.
- Uploaded photos are stored in `uploads/photos/`.
- Returned `resumeUrl` and `photoUrl` values are public static URLs under `/uploads/**`.
- `/uploads/**` is permitted in Spring Security, so these file URLs do not require an Authorization header.
- `WebConfig` maps `/uploads/**` to the configured `app.upload-dir` as an absolute `file:` directory URI.

### Get All Participants

`GET /api/participants`

Access: `ROLE_ADMIN` or `ROLE_PANELIST`

Request: No request body.

Response: `200 OK`

```json
[
  {
    "id": 1,
    "participantCode": "PART-0001",
    "eventId": 1,
    "name": "Asha Rao",
    "email": "asha@example.com",
    "phone": "+919876543210",
    "experienceYears": 3,
    "resumeUrl": "http://localhost:8080/uploads/resumes/resume.pdf",
    "photoUrl": "http://localhost:8080/uploads/photos/photo.png",
    "aiScore": 82,
    "skills": "Java, Spring, PostgreSQL",
    "status": "REGISTERED"
  }
]
```

### Get Participant By ID

`GET /api/participants/{id}`

Access: `ROLE_ADMIN` or `ROLE_PANELIST`

Path parameters:

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | number | Yes | Participant ID |

Request: No request body.

Response: `200 OK`

```json
{
  "id": 1,
  "participantCode": "PART-0001",
  "eventId": 1,
  "name": "Asha Rao",
  "email": "asha@example.com",
  "phone": "+919876543210",
  "experienceYears": 3,
  "resumeUrl": "http://localhost:8080/uploads/resumes/resume.pdf",
  "photoUrl": "http://localhost:8080/uploads/photos/photo.png",
  "aiScore": 82,
  "skills": "Java, Spring, PostgreSQL",
  "status": "REGISTERED"
}
```

### Get Participants By Event

`GET /api/participants/event/{eventId}`

Access: `ROLE_ADMIN` or `ROLE_PANELIST`

Path parameters:

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `eventId` | number | Yes | Event ID |

Request: No request body.

Response: `200 OK`

```json
[
  {
    "id": 1,
    "participantCode": "PART-0001",
    "eventId": 1,
    "name": "Asha Rao",
    "email": "asha@example.com",
    "phone": "+919876543210",
    "experienceYears": 3,
    "resumeUrl": "http://localhost:8080/uploads/resumes/resume.pdf",
    "photoUrl": "http://localhost:8080/uploads/photos/photo.png",
    "aiScore": 82,
    "skills": "Java, Spring, PostgreSQL",
    "status": "REGISTERED"
  }
]
```

### Delete Participant

`DELETE /api/participants/{id}`

Access: `ROLE_ADMIN`

Path parameters:

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | number | Yes | Participant ID |

Request: No request body.

Response: `204 No Content`

Notes:

- Deleting a participant also deletes their feedback and squad memberships.
- If the participant does not exist, the API returns `404 Not Found`.

## Panelist Endpoints

Panelists are created through a two-step invite flow:
1. Admin generates a one-time invite link.
2. Panelist opens the link and self-registers via a public form.

### Generate Panelist Invite Link

`POST /api/panelists/invite`

Access: `ROLE_ADMIN`

Request: No request body.

Response: `201 Created`

```json
{
  "token": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "registrationUrl": "https://yourfrontend.com/panelist/register?token=a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "expiresAt": "2026-07-16T10:00:00"
}
```

Notes:

- The `registrationUrl` is built from the `APP_FRONTEND_URL` environment variable.
- The token expires after **48 hours** and is **single-use**.
- Share the `registrationUrl` with the panelist directly.

### Get Active Invite Tokens

`GET /api/panelists/invite/active`

Access: `ROLE_ADMIN`

Request: No request body.

Response: `200 OK`

```json
[
  {
    "id": 1,
    "token": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "createdAt": "2026-07-14T10:00:00",
    "expiresAt": "2026-07-16T10:00:00",
    "used": false
  }
]
```

### Validate Invite Token

`GET /api/panelists/invite/validate/{token}`

Access: Public

Path parameters:

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `token` | string | Yes | Invite token from the registration URL |

Request: No request body.

Response: `200 OK` — token is valid and unused.

Error responses:

| Status | Message |
| --- | --- |
| `404 Not Found` | Invalid registration link |
| `400 Bad Request` | Link already used |
| `400 Bad Request` | Link has expired |

Notes:

- Call this endpoint when the panelist opens the registration page to confirm the link is still valid before rendering the form.

### Panelist Self-Registration

`POST /api/panelists/register/{token}`

Access: Public

Content-Type: `application/json`

Path parameters:

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `token` | string | Yes | Invite token from the registration URL |

Request body:

| Field | Type | Required | Validation |
| --- | --- | --- | --- |
| `name` | string | Yes | Must not be blank |
| `email` | string | Yes | Must be a valid email |
| `domain` | string | Yes | Must not be blank |
| `password` | string | Yes | Minimum 8 characters |
| `availability` | string | Yes | `MORNING`, `AFTERNOON`, or `CUSTOM` |
| `customAvailabilityTime` | string | Conditional | Required when `availability` is `CUSTOM` (e.g. `"14:00-17:00"`) |

Request example:

```json
{
  "name": "Dr. Meera Shah",
  "email": "meera@example.com",
  "domain": "Backend",
  "password": "securepass123",
  "availability": "CUSTOM",
  "customAvailabilityTime": "14:00-17:00"
}
```

Response: `201 Created`

```json
{
  "id": 1,
  "name": "Dr. Meera Shah",
  "email": "meera@example.com",
  "domain": "Backend",
  "availability": "CUSTOM",
  "customAvailabilityTime": "14:00-17:00"
}
```

Notes:

- Token is validated before registration: expired or already-used tokens return `400 Bad Request`.
- Email must be unique; duplicate email returns `400 Bad Request`.
- A `User` account with `ROLE_PANELIST` is automatically created.
- The invite token is invalidated (marked used) on success.

### Get All Panelists

`GET /api/panelists`

Access: `ROLE_ADMIN` or `ROLE_PANELIST`

Request: No request body.

Response: `200 OK`

```json
[
  {
    "id": 1,
    "name": "Dr. Meera Shah",
    "email": "meera@example.com",
    "domain": "Backend",
    "availability": "CUSTOM",
    "customAvailabilityTime": "14:00-17:00"
  }
]
```

### Delete Panelist

`DELETE /api/panelists/{id}`

Access: `ROLE_ADMIN`

Path parameters:

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | number | Yes | Panelist ID |

Request: No request body.

Response: `204 No Content`

Notes:

- Deleting a panelist also deletes feedback for that panelist.
- If a user account exists with the panelist email, that user account is deleted too.
- If the panelist does not exist, the API returns `404 Not Found`.

## Feedback Endpoints

### Create Feedback

`POST /api/feedback`

Access: `ROLE_ADMIN` or `ROLE_PANELIST`

Content-Type: `application/json`

Request body:

| Field | Type | Required | Validation |
| --- | --- | --- | --- |
| `participantId` | number | Yes | Must reference an existing participant |
| `panelistId` | number | Yes | Must reference an existing panelist |
| `technicalRating` | number | Yes | Integer from `1` to `5` |
| `communicationRating` | number | Yes | Integer from `1` to `5` |
| `recommendation` | string | Yes | `HIRE`, `HOLD`, or `REJECT` |
| `comments` | string | No | Optional |

Request example:

```json
{
  "participantId": 1,
  "panelistId": 1,
  "technicalRating": 5,
  "communicationRating": 4,
  "recommendation": "HIRE",
  "comments": "Strong backend fundamentals"
}
```

Response: `201 Created`

```json
{
  "id": 1,
  "participantId": 1,
  "panelistId": 1,
  "technicalRating": 5,
  "communicationRating": 4,
  "recommendation": "HIRE",
  "comments": "Strong backend fundamentals"
}
```

Notes:

- After feedback is created, the participant status is automatically updated to `COMPLETED`.

### Get All Feedback

`GET /api/feedback`

Access: `ROLE_ADMIN` or `ROLE_PANELIST`

Request: No request body.

Response: `200 OK`

```json
[
  {
    "id": 1,
    "participantId": 1,
    "panelistId": 1,
    "technicalRating": 5,
    "communicationRating": 4,
    "recommendation": "HIRE",
    "comments": "Strong backend fundamentals"
  }
]
```

## Squad Endpoints

### Create Squad

`POST /api/squads`

Access: `ROLE_ADMIN`

Content-Type: `application/json`

Request body:

| Field | Type | Required | Validation |
| --- | --- | --- | --- |
| `eventId` | number | Yes | Must reference an existing event |
| `name` | string | Yes | Must not be blank |

Request example:

```json
{
  "eventId": 1,
  "name": "Team Alpha"
}
```

Response: `201 Created`

```json
{
  "id": 1,
  "eventId": 1,
  "name": "Team Alpha"
}
```

### Add Squad Member

`POST /api/squads/{squadId}/members/{participantId}`

Access: `ROLE_ADMIN`

Path parameters:

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `squadId` | number | Yes | Squad ID |
| `participantId` | number | Yes | Participant ID |

Request: No request body.

Response: `201 Created`

```json
{
  "id": 1,
  "squadId": 1,
  "participantId": 1
}
```

### Get Squads By Event

`GET /api/squads/event/{eventId}`

Access: `ROLE_ADMIN` or `ROLE_PANELIST`

Path parameters:

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `eventId` | number | Yes | Event ID |

Request: No request body.

Response: `200 OK`

```json
[
  {
    "id": 1,
    "eventId": 1,
    "name": "Team Alpha"
  }
]
```

### Get Squad Members

`GET /api/squads/{squadId}/members`

Access: `ROLE_ADMIN` or `ROLE_PANELIST`

Path parameters:

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `squadId` | number | Yes | Squad ID |

Request: No request body.

Response: `200 OK`

```json
[
  {
    "id": 1,
    "participantCode": "PART-0001",
    "eventId": 1,
    "name": "Asha Rao",
    "email": "asha@example.com",
    "phone": "+919876543210",
    "experienceYears": 3,
    "resumeUrl": "http://localhost:8080/uploads/resumes/resume.pdf",
    "photoUrl": "http://localhost:8080/uploads/photos/photo.png",
    "aiScore": 82,
    "skills": "Java, Spring, PostgreSQL",
    "status": "REGISTERED"
  }
]
```

### Delete Squad

`DELETE /api/squads/{id}`

Access: `ROLE_ADMIN`

Path parameters:

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | number | Yes | Squad ID |

Request: No request body.

Response: `204 No Content`

Notes:

- Deleting a squad also deletes its squad membership rows.
- Participants are not deleted when only a squad is deleted.
- If the squad does not exist, the API returns `404 Not Found`.

## Dashboard Endpoints

### Get Dashboard Summary

`GET /api/dashboard/summary`

Access: `ROLE_ADMIN`

Request: No request body.

Response: `200 OK`

```json
{
  "events": 2,
  "participants": 35,
  "checkedIn": 0,
  "assigned": 0,
  "feedbackSubmitted": 18,
  "emailsSent": 35
}
```

## System Endpoints

### Keep Alive

`GET /api/system/keep-alive`

Access: Public

Request: No request body.

Response: `200 OK`

```json
{
  "status": "UP",
  "database": "UP",
  "message": "System is healthy",
  "timestamp": "2026-06-25T16:30:00.000"
}
```

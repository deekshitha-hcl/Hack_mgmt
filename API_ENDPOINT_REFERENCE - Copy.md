# API Endpoint Reference

Base URL: `http://localhost:8080`

All protected endpoints require:

```http
Authorization: Bearer <jwt-token>
```

Dates use ISO 8601 format (`YYYY-MM-DD`). DateTime values are returned as ISO local date-time strings (`YYYY-MM-DDTHH:mm:ss`).

## Roles and Public Access

| Access | Endpoints |
| --- | --- |
| **Public** | `POST /api/auth/login`, `POST /api/participants/register`, `GET /api/events`, `GET /api/events/{id}`, `GET /api/system/keep-alive`, `GET /api/panelists/invite/validate/{token}`, `POST /api/panelists/register/{token}` |
| **ADMIN** | Event CRUD, participant deletion, panelist management (invite, active, delete), squad operations, `/api/dashboard/summary`, `/api/auth/register` |
| **PANELIST** | `GET /api/dashboard/panelist/me`, `/api/feedback/**`, `GET /api/participants/**`, `GET /api/panelists` |
| **ADMIN or PANELIST** | `GET /api/panelists`, `GET /api/participants/**`, `GET /api/feedback/**` |

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

`availability` values: `FULLDAY`, `FORENOON`, `AFTERNOON`, `EVENING`, `CUSTOM`. `customAvailabilityTime` is only set when `availability` is `CUSTOM`.

- `FULLDAY`: Available all day (9:00 AM - 5:00 PM)
- `FORENOON`: Available 9:00 AM - 12:00 PM
- `AFTERNOON`: Available 1:00 PM - 5:00 PM
- `EVENING`: Available after 5:00 PM
- `CUSTOM`: Custom time slot (must provide customAvailabilityTime in format "HH:mm-HH:mm", e.g., "14:00-17:00")

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
  "feedbackType": "DESIGN",
  "submittedAt": "2026-07-20T11:14:41"
}
```

Detailed field values are stored dynamically in `participant_feedback_details` based on the selected template.

`feedbackType` values:

| Value | Purpose |
| --- | --- |
| `DESIGN` | Design-focused evaluation |
| `DEVELOPMENT` | Development-focused evaluation |
| `FINAL_REVIEW` | Final evaluation and recommendation |



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

### Panelist Dashboard

`GET /api/dashboard/panelist/me`

Access: `ROLE_PANELIST`

Response example:

```json
{
  "panelistId": 5,
  "panelistName": "Deekshi",
  "panelistEmail": "deekshi@example.com",
  "eventsHandled": 2,
  "participantsFeedbackGiven": 8,
  "feedbackSubmitted": 8,
  "events": [
    {
      "eventId": 101,
      "eventName": "Buildathon",
      "participantCount": 5,
      "feedbackCount": 5,
      "feedbackTypes": ["DESIGN", "DEVELOPMENT"],
      "lastFeedbackAt": "2026-07-20T11:00:00"
    }
  ]
}
```

`GET /api/dashboard/panelist/{panelistId}`

Access: `ROLE_ADMIN` or `ROLE_PANELIST`

Notes:

- The dashboard is derived from feedback activity, since there is no panelist-event assignment table in the current schema.
- `eventsHandled` counts distinct events with at least one feedback submission from that panelist.
- `participantsFeedbackGiven` counts distinct participants the panelist has evaluated.
- `feedbackSubmitted` counts all feedback rows submitted by the panelist.
- `events` contains per-event breakdowns for UI cards or tables.

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
| `username` | string | Yes | Must not be blank |
| `password` | string | Yes | Must not be blank |

Request example:

```json
{
  "username": "admin_user",
  "password": "password123"
}
```

Response: `200 OK`

```json
{
  "token": "<jwt-token>",
  "type": "Bearer",
  "username": "admin_user",
  "roles": ["ADMIN"]
}
```

Notes:

- Use the returned `token` in the `Authorization` header for protected endpoints.
- Invalid credentials return `401 Unauthorized`.

### Register User

`POST /api/auth/register`

Access: `ADMIN` only

Content-Type: `application/json`

Request body:

| Field | Type | Required | Validation |
| --- | --- | --- | --- |
| `username` | string | Yes | Must be unique |
| `email` | string | Yes | Must be a valid email and unique |
| `password` | string | Yes | Must not be blank |
| `role` | string | Yes | `ADMIN` or `PANELIST` |

Request example:

```json
{
  "username": "panelist1",
  "email": "panelist1@example.com",
  "password": "password123",
  "role": "PANELIST"
}
```

Response: `201 Created`

```json
{
  "token": "<jwt-token>",
  "type": "Bearer",
  "username": "panelist1",
  "roles": ["PANELIST"]
}
```

## Event Endpoints

### Create Event

`POST /api/events`

Access: `ADMIN` only

Content-Type: `application/json`

Request body:

| Field | Type | Required | Validation |
| --- | --- | --- | --- |
| `name` | string | Yes | Must not be blank |
| `description` | string | No | Optional free-text description |
| `startDate` | date | No | ISO date (`YYYY-MM-DD`); omit for undated events (status defaults to OPEN) |
| `endDate` | date | No | ISO date; must be >= `startDate`. Omit for single-day events |

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
  "registrationUrl": "http://localhost:8080/participants/register?eventId=1",
  "qrCodeUrl": "http://localhost:8080/uploads/qrcodes/1-registration.png"
}
```

Notes:

- `endDate` before `startDate` returns `400 Bad Request`.
- Undated events (no startDate/endDate) default to `OPEN` status.
- Single-day event: omit `endDate` or set it equal to `startDate`.
- QR code is auto-generated and stored in `/uploads/qrcodes/`.

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

### Get Feedback Template By Type

`GET /api/feedback/templates/{feedbackType}`

Access: `ROLE_ADMIN` or `ROLE_PANELIST`

Path parameters:

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `feedbackType` | string | Yes | One of `DESIGN`, `DEVELOPMENT`, `FINAL_REVIEW` |

Request: No request body.

Response: `200 OK`

```json
{
  "feedbackType": "DESIGN",
  "fields": [
    { "name": "uiUxUnderstanding", "type": "RATING", "required": true },
    { "name": "wireframingSkills", "type": "RATING", "required": true },
    { "name": "creativity", "type": "RATING", "required": true },
    { "name": "designToolsKnowledge", "type": "RATING", "required": true },
    { "name": "comments", "type": "TEXT", "required": false }
  ]
}
```

Template details by type:

`DESIGN`
- `uiUxUnderstanding` (`RATING`, required)
- `wireframingSkills` (`RATING`, required)
- `creativity` (`RATING`, required)
- `designToolsKnowledge` (`RATING`, required)
- `comments` (`TEXT`, optional)

`DEVELOPMENT`
- `codingSkills` (`RATING`, required)
- `problemSolving` (`RATING`, required)
- `databaseKnowledge` (`RATING`, required)
- `apiDevelopment` (`RATING`, required)
- `codeQuality` (`RATING`, required)
- `comments` (`TEXT`, optional)

`FINAL_REVIEW`
- `projectCompletionStatus` (`RATING`, required)
- `featureImplementation` (`RATING`, required)
- `presentationSkills` (`RATING`, required)
- `teamCollaboration` (`RATING`, required)
- `finalComments` (`TEXT`, optional)

Detailed implementation:

1. Backend loads field metadata from `feedback_template` by `feedbackType`.
2. Fields are returned in insertion order and should be rendered dynamically by frontend.
3. This endpoint is source-of-truth for form generation. Frontend should not hardcode fields.

Error behavior:

- Invalid `feedbackType` enum value returns `400 Bad Request`.
- Valid enum with no configured template rows returns `404 Not Found`.

Example cURL:

```bash
curl -X GET "http://localhost:8080/api/feedback/templates/DESIGN" \
  -H "Authorization: Bearer <jwt-token>"
```

### Get Participant Feedback Status

`GET /api/feedback/status/{participantId}`

Access: `ROLE_ADMIN` or `ROLE_PANELIST`

Path parameters:

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `participantId` | number | Yes | Participant ID to check completion status |

Request: No request body.

Response: `200 OK`

```json
{
  "participantId": 1,
  "totalSubmitted": 2,
  "totalAllowed": 3,
  "completed": false,
  "feedbackTypes": [
    { "feedbackType": "DESIGN", "submitted": true },
    { "feedbackType": "DEVELOPMENT", "submitted": true },
    { "feedbackType": "FINAL_REVIEW", "submitted": false }
  ]
}
```

Detailed implementation:

1. Backend validates participant existence.
2. Backend checks submission presence for each feedback type.
3. `totalAllowed` is always `3` (DESIGN, DEVELOPMENT, FINAL_REVIEW).
4. `completed` becomes `true` only when all three are submitted.

Error behavior:

- Unknown participant returns `404 Not Found`.

Example cURL:

```bash
curl -X GET "http://localhost:8080/api/feedback/status/1" \
  -H "Authorization: Bearer <jwt-token>"
```

### Get Participant Feedback Details

`GET /api/feedback/participant/{participantId}`

Also available as: `GET /api/feedback/by-participant/{participantId}`

Access: `ROLE_ADMIN` or `ROLE_PANELIST`

Path parameters:

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `participantId` | number | Yes | Participant ID to retrieve all feedback records |

Request: No request body.

Response: `200 OK` (returns array of feedback records, empty if none submitted)

```json
[
  {
    "id": 101,
    "participantId": 12,
    "panelistId": 5,
    "panelistName": "Deekshi",
    "panelistEmail": "deekshi@example.com",
    "feedbackType": "DESIGN",
    "submittedAt": "2026-07-20T10:30:00",
    "fieldValues": {
      "uiUxUnderstanding": "5",
      "wireframingSkills": "4",
      "creativity": "5",
      "designToolsKnowledge": "4",
      "comments": "Strong design thinking"
    }
  },
  {
    "id": 102,
    "participantId": 12,
    "panelistId": 5,
    "panelistName": "Deekshi",
    "panelistEmail": "deekshi@example.com",
    "feedbackType": "DEVELOPMENT",
    "submittedAt": "2026-07-20T11:45:00",
    "fieldValues": {
      "codingSkills": "4",
      "problemSolving": "5",
      "databaseKnowledge": "4",
      "apiDevelopment": "4",
      "codeQuality": "5",
      "comments": "Clean and well-structured code"
    }
  }
]
```

Detailed implementation:

1. Validate participant exists.
2. Fetch all feedback records for participant sorted by `submittedAt` ascending.
3. For each feedback, load panelist name and email.
4. For each feedback, fetch all dynamic field values from `participant_feedback_details`.
5. Combine field key-value pairs into `fieldValues` map.
6. Return array of full records or empty array if none exist.

Response details:

- `id`: Feedback record ID
- `panelistName`: Full name of the panelist; falls back to "Unknown" if panelist deleted
- `panelistEmail`: Email of the panelist; falls back to "unknown@example.com" if panelist deleted
- `fieldValues`: Object with all submitted field names as keys and their values as strings
- Records sorted by oldest `submittedAt` first

Error behavior:

- Unknown participant returns `404 Not Found`.
- Participant with no feedback returns `200 OK` with empty array `[]`.

Example cURL:

```bash
curl -X GET "http://localhost:8080/api/feedback/participant/1" \
  -H "Authorization: Bearer <jwt-token>"
```

Alternative alias:

```bash
curl -X GET "http://localhost:8080/api/feedback/by-participant/1" \
  -H "Authorization: Bearer <jwt-token>"
```

Frontend usage notes:

- Use this endpoint to display feedback submission history for a participant in dashboards.
- No pagination: returns all feedback records. If needed, implement pagination later.
- Each `fieldValues` entry matches the template fields for that `feedbackType`.
- Panelist info is included so you can display "Feedback from Deekshi" with email and domain if desired.


### Submit Participant Feedback

`POST /api/feedback/submit`

Access: `ROLE_ADMIN` or `ROLE_PANELIST`

Content-Type: `application/json`

Request body:

| Field | Type | Required | Validation |
| --- | --- | --- | --- |
| `participantId` | number | Yes | Must reference an existing participant |
| `panelistId` | number | Yes | Must reference an existing panelist |
| `feedbackType` | string | Yes | One of `DESIGN`, `DEVELOPMENT`, `FINAL_REVIEW` |
| `fieldValues` | object | Yes | Key-value map containing fields defined for selected `feedbackType` |

Request example (`DESIGN`):

```json
{
  "participantId": 1,
  "panelistId": 1,
  "feedbackType": "DESIGN",
  "fieldValues": {
    "uiUxUnderstanding": "5",
    "wireframingSkills": "4",
    "creativity": "4",
    "designToolsKnowledge": "5",
    "comments": "Great sense of design fundamentals"
  }
}
```

Request example (`DEVELOPMENT`):

```json
{
  "participantId": 1,
  "panelistId": 1,
  "feedbackType": "DEVELOPMENT",
  "fieldValues": {
    "codingSkills": "5",
    "problemSolving": "4",
    "databaseKnowledge": "4",
    "apiDevelopment": "5",
    "codeQuality": "4",
    "comments": "Solid backend implementation"
  }
}
```

Request example (`FINAL_REVIEW`):

```json
{
  "participantId": 1,
  "panelistId": 1,
  "feedbackType": "FINAL_REVIEW",
  "fieldValues": {
    "projectCompletionStatus": "5",
    "featureImplementation": "4",
    "presentationSkills": "4",
    "teamCollaboration": "5",
    "finalComments": "Ready for next round"
  }
}
```

Response: `200 OK`

```json
{
  "status": "SUCCESS",
  "message": "Feedback submitted successfully"
}
```

Detailed implementation:

1. Validate `participantId`, `panelistId`, `feedbackType`, and `fieldValues` are present.
2. Validate participant and panelist existence.
3. Check uniqueness by participant + feedbackType.
4. Load configured template for selected type.
5. Reject unknown fields not present in template.
6. Validate required fields are provided.
7. Validate `RATING` fields in range `1-5`.
8. Save record in `participant_feedback`.
9. Save each dynamic field in `participant_feedback_details`.
10. Mark participant status as `COMPLETED`.

Notes:

- `feedbackType` is mandatory.
- All required fields for selected `feedbackType` must be provided.
- `RATING` fields must be integers from `1` to `5`.
- Unknown fields (not in template) are rejected with `400 Bad Request`.
- Each participant can have at most 3 feedback submissions total: exactly one for each type (`DESIGN`, `DEVELOPMENT`, `FINAL_REVIEW`).
- Duplicate submission for the same participant + feedback type returns `409 Conflict`, even from a different panelist.
- After feedback is submitted, participant status is updated to `COMPLETED`.

Example error responses:

Duplicate submission (`409 Conflict`):

```json
{
  "timestamp": "2026-07-20T11:40:00",
  "status": 409,
  "error": "Conflict",
  "message": "Feedback for this type is already submitted for participant: DESIGN",
  "validationErrors": null
}
```

Unknown field (`400 Bad Request`):

```json
{
  "timestamp": "2026-07-20T11:40:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Unknown field submitted: randomField",
  "validationErrors": null
}
```

## Squad Endpoints

### Create Squad (Manual)

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

### Auto-Generate Squads

`POST /api/squads/auto-generate`

Access: `ROLE_ADMIN`

Content-Type: `application/json`

Automatically distributes all participants of an event into balanced squads. Each squad gets a mix of experience levels. Remainder participants are assigned to the squad with the lowest total experience sum.

Request body:

| Field | Type | Required | Validation |
| --- | --- | --- | --- |
| `eventId` | number | Yes | Must reference an existing event |
| `minMembers` | number | Yes | Minimum `2`; determines squad count (`floor(total / minMembers)`) |
| `maxMembers` | number | Yes | Must be `>= minMembers` |
| `squadNamePrefix` | string | No | Prefix for squad names, e.g. `"Team"` → `"Team 1"`, `"Team 2"`. Defaults to `"Squad"` |
| `techStackFilter` | string | No | Keyword to filter participants by tech stack (e.g. `"Java"`). Omit to include all participants |

Request example:

```json
{
  "eventId": 1,
  "minMembers": 3,
  "maxMembers": 4,
  "squadNamePrefix": "Team",
  "techStackFilter": "Java"
}
```

Response: `201 Created` — list of created squads.

```json
[
  { "id": 1, "eventId": 1, "name": "Team 1" },
  { "id": 2, "eventId": 1, "name": "Team 2" },
  { "id": 3, "eventId": 1, "name": "Team 3" }
]
```

Notes:

- Participants are sorted by experience years (descending) then distributed via round-robin so every squad gets a balanced seniority mix.
- Remainder participants (e.g. 10 ÷ 3 = 3 squads + 1 extra) are each assigned to the squad with the lowest cumulative experience sum.
- Example: 10 participants, `minMembers=3` → 3 squads of sizes `[4, 3, 3]`.
- Returns `400 Bad Request` if no participants match the filter or `maxMembers < minMembers`.
- Returns `404 Not Found` if the event does not exist.

### Add Squad Member (Manual)

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

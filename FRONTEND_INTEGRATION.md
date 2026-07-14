Frontend Integration Guide for AI Recruitment Event Platform

Purpose
- Describe how the frontend should call the backend so backend changes can be made in alignment with the frontend (for Copilot-assisted development).

Assumptions
- Backend base URL: http://localhost:8080
- Frontend origin for local dev: http://localhost:3000
- Backend uses JWT Bearer tokens for auth (returned by POST /api/auth/login)

1. Authentication

1.1 Login
- Endpoint: POST /api/auth/login
- Request JSON: { "username": "...", "password": "..." }
- Response: { "token": "<jwt>", "role": "ADMIN|PANELIST|PARTICIPANT" }
- Frontend responsibilities:
  - Store token in memory or localStorage (we recommend memory + refresh token flow; localStorage acceptable for short projects).
  - Attach Authorization header: `Authorization: Bearer <token>` for protected requests.

1.2 Usage
- Include `Authorization` header for all protected endpoints.
- Example Axios interceptor:

```javascript
import axios from 'axios';
const api = axios.create({ baseURL: 'http://localhost:8080/api' });
api.interceptors.request.use(config => {
  const token = localStorage.getItem('token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});
export default api;
```

2. CORS
- Backend allows `http://localhost:5173` (Vite dev server), `http://localhost:3000`, `http://localhost:3001`, and `http://127.0.0.1:5173` origins during development.
- Allow methods: GET, POST, PUT, DELETE, OPTIONS, PATCH
- Allow credentials when using cookies or token-based auth.
- Backend CORS is implemented in `CorsConfig` and integrated into Spring Security.
- To add production origins, update `CorsConfig.corsConfigurationSource()` in the backend.
- Preflight requests are cached for 1 hour to reduce overhead.

3. Endpoints mapping (Frontend pages → backend endpoints)

- Dashboard (`/dashboard` page)
  - GET `/api/dashboard/summary` -> { events, participants, checkedIn, assigned, feedbackSubmitted, emailsSent }

- Events
  - GET `/api/events` -> list events
  - GET `/api/events/{id}` -> event details
  - POST `/api/events` -> create event (ADMIN)
  - DELETE `/api/events/{id}` -> delete event and related event data (ADMIN)

- Participants (registration page)
  - POST `/api/participants/register` -> register participant (multipart/form-data or JSON)
  - GET `/api/participants` -> list participants
  - GET `/api/participants/event/{eventId}` -> participants by event
  - DELETE `/api/participants/{id}` -> delete participant and related participant data (ADMIN)

- Attendance (check-in)
  - POST `/api/attendance/check-in` -> { participantCode }

- Panelists
  - POST `/api/panelists` -> create panelist
  - GET `/api/panelists` -> list panelists
  - DELETE `/api/panelists/{id}` -> delete panelist, reset assigned participants to REGISTERED, and remove linked user/assignment/feedback data (ADMIN)

- Assignments
  - POST `/api/assignments` -> { participantId, panelistId }
  - GET `/api/assignments`

- Feedback
  - POST `/api/feedback` -> submit feedback
  - GET `/api/feedback`

- Squads
  - POST `/api/squads` -> create squad
  - POST `/api/squads/{squadId}/members/{participantId}` -> add member
  - GET `/api/squads/event/{eventId}`
  - GET `/api/squads/{squadId}/members`
  - DELETE `/api/squads/{id}` -> delete squad and squad membership rows (ADMIN)

4. Request and response shapes
- Use shapes from PROJECT_DOCUMENTATION_BACKENED.md for each endpoint. Keep backend responses consistent with those shapes.

5. Error handling
- Backend returns structured errors with fields: timestamp, status, error, message, validationErrors (map).
- Frontend should show `message` to users and consider `validationErrors` for field-level messages.

6. Role-based UI behavior (frontend responsibilities)
- Use the `role` value from login response to show/hide UI parts:
  - ADMIN: full access to create/edit events, manage participants, assignments
  - PANELIST: view events, manage participants assigned to them, submit feedback
  - PARTICIPANT: register, view own status

7. Token expiration handling
- Tokens expire (`app.jwt.expiration-ms`). Frontend should detect 401 responses and redirect to login or attempt token refresh (not implemented in backend yet). Suggestion: implement refresh tokens later.

8. Example fetch calls

Login
```javascript
const r = await fetch('http://localhost:8080/api/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username, password })
});
const data = await r.json();
localStorage.setItem('token', data.token);
```

Get events
```javascript
const r = await fetch('http://localhost:8080/api/events', {
  headers: { 'Authorization': `Bearer ${localStorage.getItem('token')}` }
});
const events = await r.json();
```

9. Recommended backend changes to support frontend
- Ensure all endpoints return the documented shapes in PROJECT_DOCUMENTATION_BACKENED.md.
- Add pagination for listing endpoints if data grows.
- Implement refresh tokens for smoother UX.
- Provide an endpoint `/api/auth/me` to return current user details from token.
- Ensure CORS accepts the frontend origin(s) used in staging/production.

10. Next steps for Copilot-assisted backend edits
- Use this document as mapping; when asking Copilot to change the backend, reference the endpoint name and the required request/response shape and desired role access.
- Example instruction: "Update POST /api/events to return registrationUrl and qrCodeUrl as in PROJECT_DOCUMENTATION_BACKENED.md and allow only ADMIN role." 


---
Generated on 2026-06-25

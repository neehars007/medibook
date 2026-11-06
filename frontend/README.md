# MediBook frontend

React 18 + Vite + TypeScript, TanStack Query, React Hook Form + Zod, Tailwind CSS.

## Run

The backend must be running on :8080 (see the root README). Then:

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173
```

`/api` is proxied to `localhost:8080` (vite.config.ts), so the httpOnly refresh cookie works
without CORS tricks. Links in the emails (`/verify-email`, `/me/visits/:id`, ...) open here.

## What's built

- Public: home (specialties, doctors with next free time), doctor search and profiles
- Auth: log in (with optional 2FA code), register, email verification, password reset, staff invite
- Booking wizard: doctor or "first available" -> visit type -> who it is for (self or family) ->
  day and time (10-minute hold with countdown) -> intake form and payment choice -> appointment card
- Patient portal: upcoming and past appointments, cancel, join video, receipt download,
  visit summary with prescription and summary PDF downloads
- 15-minute inactivity warning and logout; access token kept in memory and refreshed automatically

Next: reception desk, doctor's day and visit-note editor, queue TV page, admin.

## Design

Atkinson Hyperlegible Next (made for low-vision readers) at an 18px base, high contrast,
mobile-first. Time slots and the confirmation are styled like a clinic's paper appointment card.

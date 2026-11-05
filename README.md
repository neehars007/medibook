# MediBook — Clinic Appointment Booking (backend)

Spring Boot 3 / Java 21 API for a single clinic: online booking by specialty or doctor, no double-booked
doctors, rooms or patients, intake forms, visit notes and prescriptions, walk-in token queue, Stripe (test
mode) or pay-at-clinic, reminders, reports, and need-to-know access control for health data.

> **Portfolio project. Use only fake patient data.** A real deployment would need HIPAA (or equivalent)
> compliance, which is beyond this project.

## Run it

```bash
docker compose up -d postgres redis minio mailhog   # infra
./mvnw spring-boot:run                               # API on :8080, dev profile, demo data seeded
```

or `docker compose up --build` for everything. Then:

| What | URL |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| MailHog (all emails) | http://localhost:8025 |
| MinIO console | http://localhost:9001 (minioadmin / minioadmin) |

**Demo logins** (password `Demo@12345`): `admin@medibook.test`, `reception@medibook.test`,
`doctor@medibook.test` (Dr. Priya Mehta, General Medicine), `patient@medibook.test` (Alex Demo, has a
dependent child and a completed visit 5 days ago, so a free follow-up can be booked).

Payments run in **simulated mode** unless `STRIPE_SECRET_KEY` is set (test key), in which case the
appointment stays `PENDING_PAYMENT` until the `payment_intent.succeeded` webhook arrives
(`stripe listen --forward-to localhost:8080/api/v1/payments/webhook`).

## Tests

```bash
./mvnw verify
```

* Unit: availability engine (doctor + room + buffer + notice), follow-up eligibility, status machine,
  refund rules, payment-mode rules, access-control policy, TOTP (RFC 6238 vectors), AES-GCM converter, CSV escaping.
* Integration (Testcontainers, skipped when Docker isn't running): 20 concurrent bookings of the same doctor
  slot → exactly one succeeds; same for a room; patient self-overlap; cancelled rows don't block;
  20 concurrent walk-in tokens → no duplicates; receptionist 403 on visit notes; doctor 403 on a
  non-patient's chart; timed-out session → 401.

## How the important parts work

**Double-booking** — two layers. A Redis hold (`SET hold:doctor:{id}:{start} NX EX 600`, plus a room key)
reserves the slot for 10 minutes while the patient fills the intake form. The real guarantee is three
PostgreSQL `EXCLUDE USING gist` constraints (doctor, room, patient) over `tstzrange(start_at, end_at)` for
active statuses — see `V1__init_schema.sql`. Booking re-validates every rule inside one transaction and
maps constraint violations to HTTP 409.

**Availability** — `SlotCalculator` is a pure function: doctor sessions ∩ clinic hours − leave/holidays −
active appointments (with buffer) − holds − room conflicts, stepped by the clinic's granularity, then
minimum-notice and max-advance rules. Follow-up types only appear within N days of a completed visit.

**Need-to-know** — `PatientAccessService` + `ClinicalAccessPolicy` enforce in the service layer that
clinical data is visible only to the patient/guardian and to doctors with an active or recent (90-day)
appointment. Receptionists and admins never see it. Every view is written to `access_log`, which the
patient can read at `GET /api/v1/patients/me/access-log`.

**Encryption at rest** — diagnosis, notes, vitals, history, intake answers, prescription items, insurance
policy number and TOTP secrets use `EncryptedStringConverter` (AES-256-GCM, key from
`MEDIBOOK_ENCRYPTION_KEY`).

**Sessions** — 15-minute access JWT + rotating refresh token in an httpOnly `SameSite=Strict` cookie. A Redis
session key expires after 15 minutes of inactivity, which kills both. Optional TOTP 2FA, lockout after 5
failed logins, per-IP rate limits on login, booking and search.

**Immutable notes** — finalizing a visit locks the note, completes the appointment and generates the
summary/prescription PDFs (OpenPDF → private MinIO bucket). Later changes are timestamped addendums.
Emails never contain diagnoses or medication — only links to the portal.

## Status machine

```
PENDING_PAYMENT → CONFIRMED → CHECKED_IN → IN_CONSULTATION → COMPLETED
       ↓              ↓    ↘
    EXPIRED      CANCELLED   NO_SHOW
```

Video visits go `CONFIRMED → IN_CONSULTATION` (no physical check-in). Unpaid `PENDING_PAYMENT` bookings
expire after 15 minutes.

## Packages (by feature)

`auth` · `patient` · `clinic` · `doctor` · `room` · `availability` · `appointment` (holds, booking) ·
`queue` · `reception` · `consultation` · `prescription` (PDFs) · `payment` · `notification` · `review` ·
`report` · `audit` · `admin` · `storage` · `video` · `seed` · `common` (security, crypto, config)

## API overview (`/api/v1`)

* **Auth** `POST /auth/register|login|refresh|logout|verify-email|forgot-password|reset-password|accept-invite|2fa/setup|2fa/verify`
* **Public** `GET /clinic`, `/specialties`, `/doctors?specialtyId=&q=&language=&gender=`, `/doctors/{id}`, `/doctors/{id}/reviews`, `/appointment-types?specialtyId=`, `/availability?doctorId=|specialtyId=&typeId=&date=&patientId=&mode=`
* **Patient** `GET|PUT /patients/me`, `/patients/me/dependents`, `/patients/{id}/medical-history`, `POST|GET /patients/{id}/documents`, `GET /documents/{id}/download`, `GET /patients/me/access-log`, `GET /patients/me/export`
* **Booking** `POST /holds`, `DELETE /holds/{id}`, `POST /appointments`, `GET /appointments/me?status=upcoming|past`, `PATCH /appointments/{id}/reschedule`, `POST /appointments/{id}/cancel`, `GET /appointments/{id}/visit-summary|summary.pdf|prescription.pdf|receipt.pdf|video-link`, `POST /reviews`
* **Reception** `GET /reception/today`, `POST /reception/patients`, `GET /reception/patients/search?q=`, `POST /reception/appointments`, `POST /reception/appointments/{id}/check-in`, `GET /reception/queue/{doctorId}`, `POST /reception/queue/{doctorId}/walk-in`, `POST /reception/payments/{appointmentId}`
* **Queue** `GET /queue/display` (public, anonymized), `POST /doctor/queue/next`
* **Doctor** `GET /doctor/me/today|appointments`, `GET|PUT /doctor/me/sessions`, `GET|POST|DELETE /doctor/me/leave`, `POST /doctor/me/emergency-block`, `GET /doctor/patients/{id}/chart`, `POST /doctor/appointments/{id}/start|no-show`, `PUT /doctor/appointments/{id}/visit-note`, `POST /doctor/appointments/{id}/visit-note/finalize`, `POST /doctor/visit-notes/{id}/addendum`
* **Admin** `/admin/clinic`, `/admin/clinic/hours|policy`, `/admin/holidays`, `/admin/specialties`, `/admin/rooms`, `/admin/appointment-types`, `/admin/doctors[/invite|/{id}|/{id}/sessions]`, `/admin/staff[/invite]`, `/admin/appointments`, `/admin/reviews`, `/admin/reports/summary|doctors|revenue|export.csv`, `/admin/audit-logs`, `/admin/payments/{id}/refund`
* **Stripe** `POST /payments/webhook`

## Not built yet

The React frontend (spec §10), SMS/WhatsApp, ICD-10 search, WebSocket queue updates (the TV page polls
`/queue/display`), and the AI stretch goals.

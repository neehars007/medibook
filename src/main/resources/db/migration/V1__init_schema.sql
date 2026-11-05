-- MediBook initial schema. Sensitive clinical columns are TEXT because they hold
-- AES-GCM ciphertext (see EncryptedStringConverter), never plaintext.

CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ---------- Auth ----------
CREATE TABLE app_user (
    id              uuid PRIMARY KEY,
    email           varchar(255) NOT NULL,
    password_hash   varchar(255),
    role            varchar(30)  NOT NULL,
    phone           varchar(40),
    email_verified  boolean      NOT NULL DEFAULT false,
    totp_secret     text,
    totp_enabled    boolean      NOT NULL DEFAULT false,
    active          boolean      NOT NULL DEFAULT true,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz,
    deleted         boolean      NOT NULL DEFAULT false
);
CREATE UNIQUE INDEX ux_app_user_email ON app_user (lower(email));

-- ---------- Clinic ----------
CREATE TABLE clinic (
    id                        uuid PRIMARY KEY,
    name                      varchar(200) NOT NULL,
    address                   varchar(500),
    phone                     varchar(40),
    timezone                  varchar(60)  NOT NULL,
    logo_url                  varchar(500),
    about                     text,
    emergency_notice          varchar(500),
    min_notice_minutes        integer NOT NULL DEFAULT 60,
    max_advance_days          integer NOT NULL DEFAULT 30,
    cancellation_cutoff_hours integer NOT NULL DEFAULT 4,
    payment_policy            varchar(30) NOT NULL DEFAULT 'EITHER',
    no_show_prepay_threshold  integer NOT NULL DEFAULT 2,
    buffer_minutes            integer NOT NULL DEFAULT 5,
    slot_granularity_minutes  integer NOT NULL DEFAULT 10,
    created_at                timestamptz NOT NULL DEFAULT now(),
    updated_at                timestamptz,
    deleted                   boolean NOT NULL DEFAULT false
);

CREATE TABLE clinic_hours (
    id          uuid PRIMARY KEY,
    day_of_week varchar(10) NOT NULL UNIQUE,
    open_time   time,
    close_time  time,
    closed      boolean NOT NULL DEFAULT false,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz,
    deleted     boolean NOT NULL DEFAULT false
);

CREATE TABLE holiday (
    id          uuid PRIMARY KEY,
    date        date NOT NULL,
    name        varchar(200) NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz,
    deleted     boolean NOT NULL DEFAULT false
);
CREATE UNIQUE INDEX ux_holiday_date ON holiday (date) WHERE deleted = false;

CREATE TABLE specialty (
    id          uuid PRIMARY KEY,
    name        varchar(120) NOT NULL,
    description text,
    icon        varchar(80),
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz,
    deleted     boolean NOT NULL DEFAULT false
);

CREATE TABLE room (
    id          uuid PRIMARY KEY,
    name        varchar(120) NOT NULL,
    type        varchar(30)  NOT NULL,
    active      boolean NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz,
    deleted     boolean NOT NULL DEFAULT false
);

-- ---------- Patients ----------
CREATE TABLE patient (
    id                      uuid PRIMARY KEY,
    user_id                 uuid REFERENCES app_user (id),
    guardian_patient_id     uuid REFERENCES patient (id),
    relationship            varchar(40),
    full_name               varchar(200) NOT NULL,
    dob                     date NOT NULL,
    gender                  varchar(20) NOT NULL,
    phone                   varchar(40),
    address                 varchar(500),
    emergency_contact_name  varchar(200),
    emergency_contact_phone varchar(40),
    blood_group             varchar(10),
    insurance_provider      varchar(200),
    insurance_policy_no     text,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz,
    deleted                 boolean NOT NULL DEFAULT false
);
CREATE INDEX ix_patient_user ON patient (user_id);
CREATE INDEX ix_patient_guardian ON patient (guardian_patient_id);
CREATE INDEX ix_patient_name ON patient (lower(full_name));
CREATE INDEX ix_patient_phone ON patient (phone);

CREATE TABLE medical_history (
    id          uuid PRIMARY KEY,
    patient_id  uuid NOT NULL REFERENCES patient (id),
    type        varchar(20) NOT NULL,
    description text NOT NULL,
    since       date,
    active      boolean NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz,
    deleted     boolean NOT NULL DEFAULT false
);
CREATE INDEX ix_medical_history_patient ON medical_history (patient_id);

CREATE TABLE patient_document (
    id          uuid PRIMARY KEY,
    patient_id  uuid NOT NULL REFERENCES patient (id),
    file_key    varchar(300) NOT NULL,
    file_name   varchar(300) NOT NULL,
    mime_type   varchar(100) NOT NULL,
    size_bytes  bigint NOT NULL,
    doc_type    varchar(30) NOT NULL,
    uploaded_by uuid NOT NULL REFERENCES app_user (id),
    uploaded_at timestamptz NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz,
    deleted     boolean NOT NULL DEFAULT false
);
CREATE INDEX ix_patient_document_patient ON patient_document (patient_id);

-- ---------- Doctors ----------
CREATE TABLE doctor (
    id               uuid PRIMARY KEY,
    user_id          uuid NOT NULL UNIQUE REFERENCES app_user (id),
    full_name        varchar(200) NOT NULL,
    gender           varchar(20),
    specialty_id     uuid NOT NULL REFERENCES specialty (id),
    qualifications   varchar(500),
    experience_years integer NOT NULL DEFAULT 0,
    languages        varchar(300),
    bio              text,
    license_no       varchar(100) NOT NULL,
    consultation_fee numeric(10, 2) NOT NULL DEFAULT 0,
    photo_url        varchar(500),
    active           boolean NOT NULL DEFAULT true,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz,
    deleted          boolean NOT NULL DEFAULT false
);
CREATE INDEX ix_doctor_specialty ON doctor (specialty_id);

CREATE TABLE doctor_session (
    id          uuid PRIMARY KEY,
    doctor_id   uuid NOT NULL REFERENCES doctor (id),
    day_of_week varchar(10) NOT NULL,
    start_time  time NOT NULL,
    end_time    time NOT NULL,
    room_id     uuid REFERENCES room (id),
    mode        varchar(20) NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz,
    deleted     boolean NOT NULL DEFAULT false,
    CONSTRAINT ck_session_time CHECK (start_time < end_time)
);
CREATE INDEX ix_doctor_session_doctor ON doctor_session (doctor_id, day_of_week);

CREATE TABLE doctor_leave (
    id          uuid PRIMARY KEY,
    doctor_id   uuid NOT NULL REFERENCES doctor (id),
    start_at    timestamptz NOT NULL,
    end_at      timestamptz NOT NULL,
    reason      varchar(300),
    kind        varchar(20) NOT NULL DEFAULT 'LEAVE',
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz,
    deleted     boolean NOT NULL DEFAULT false,
    CONSTRAINT ck_leave_time CHECK (start_at < end_at)
);
CREATE INDEX ix_doctor_leave_doctor ON doctor_leave (doctor_id, start_at);

-- ---------- Appointments ----------
CREATE TABLE appointment_type (
    id                    uuid PRIMARY KEY,
    specialty_id          uuid NOT NULL REFERENCES specialty (id),
    name                  varchar(120) NOT NULL,
    duration_minutes      integer NOT NULL,
    fee                   numeric(10, 2),
    requires_room_type    varchar(30),
    is_follow_up          boolean NOT NULL DEFAULT false,
    follow_up_window_days integer,
    mode                  varchar(20),
    intake_required       boolean NOT NULL DEFAULT false,
    active                boolean NOT NULL DEFAULT true,
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz,
    deleted               boolean NOT NULL DEFAULT false
);

CREATE TABLE appointment (
    id                  uuid PRIMARY KEY,
    patient_id          uuid NOT NULL REFERENCES patient (id),
    booked_by_user_id   uuid REFERENCES app_user (id),
    doctor_id           uuid NOT NULL REFERENCES doctor (id),
    room_id             uuid REFERENCES room (id),
    appointment_type_id uuid NOT NULL REFERENCES appointment_type (id),
    start_at            timestamptz NOT NULL,
    end_at              timestamptz NOT NULL,
    mode                varchar(20) NOT NULL,
    status              varchar(30) NOT NULL,
    source              varchar(20) NOT NULL DEFAULT 'ONLINE',
    token_number        integer,
    checked_in_at       timestamptz,
    consult_started_at  timestamptz,
    consult_ended_at    timestamptz,
    video_link          varchar(500),
    cancel_reason       varchar(500),
    cancelled_by        varchar(30),
    payment_mode        varchar(30) NOT NULL,
    version             bigint NOT NULL DEFAULT 0,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz,
    deleted             boolean NOT NULL DEFAULT false,
    CONSTRAINT ck_appointment_time CHECK (start_at < end_at)
);
CREATE INDEX ix_appointment_doctor_start ON appointment (doctor_id, start_at);
CREATE INDEX ix_appointment_patient ON appointment (patient_id, start_at);
CREATE INDEX ix_appointment_status ON appointment (status);

-- Double-booking prevention (spec section 7): doctor, room and patient.
ALTER TABLE appointment ADD CONSTRAINT no_doctor_overlap
    EXCLUDE USING gist (doctor_id WITH =, tstzrange(start_at, end_at) WITH &&)
    WHERE (status IN ('PENDING_PAYMENT', 'CONFIRMED', 'CHECKED_IN', 'IN_CONSULTATION'));

ALTER TABLE appointment ADD CONSTRAINT no_room_overlap
    EXCLUDE USING gist (room_id WITH =, tstzrange(start_at, end_at) WITH &&)
    WHERE (room_id IS NOT NULL AND status IN ('PENDING_PAYMENT', 'CONFIRMED', 'CHECKED_IN', 'IN_CONSULTATION'));

ALTER TABLE appointment ADD CONSTRAINT no_patient_overlap
    EXCLUDE USING gist (patient_id WITH =, tstzrange(start_at, end_at) WITH &&)
    WHERE (status IN ('PENDING_PAYMENT', 'CONFIRMED', 'CHECKED_IN', 'IN_CONSULTATION'));

CREATE TABLE intake_form (
    id                   uuid PRIMARY KEY,
    appointment_id       uuid NOT NULL UNIQUE REFERENCES appointment (id),
    reason               text,
    symptoms             text,
    duration_of_symptoms text,
    current_meds         text,
    allergies_confirmed  boolean NOT NULL DEFAULT false,
    extra_notes          text,
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz,
    deleted              boolean NOT NULL DEFAULT false
);

-- Walk-in token sequence: one row per doctor per day, locked with SELECT ... FOR UPDATE.
CREATE TABLE queue_token_sequence (
    doctor_id  uuid NOT NULL REFERENCES doctor (id),
    day        date NOT NULL,
    last_value integer NOT NULL DEFAULT 0,
    PRIMARY KEY (doctor_id, day)
);

-- ---------- Consultation ----------
CREATE TABLE visit_note (
    id                uuid PRIMARY KEY,
    appointment_id    uuid NOT NULL UNIQUE REFERENCES appointment (id),
    doctor_id         uuid NOT NULL REFERENCES doctor (id),
    patient_id        uuid NOT NULL REFERENCES patient (id),
    chief_complaint   text,
    findings          text,
    vitals            text,
    diagnosis         text,
    icd_code          varchar(20),
    advice            text,
    recommended_tests text,
    follow_up_in_days integer,
    finalized_at      timestamptz,
    summary_pdf_key   varchar(300),
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz,
    deleted           boolean NOT NULL DEFAULT false
);
CREATE INDEX ix_visit_note_patient ON visit_note (patient_id);

CREATE TABLE visit_note_addendum (
    id            uuid PRIMARY KEY,
    visit_note_id uuid NOT NULL REFERENCES visit_note (id),
    doctor_id     uuid NOT NULL REFERENCES doctor (id),
    text          text NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz,
    deleted       boolean NOT NULL DEFAULT false
);

CREATE TABLE prescription (
    id            uuid PRIMARY KEY,
    visit_note_id uuid NOT NULL UNIQUE REFERENCES visit_note (id),
    pdf_key       varchar(300),
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz,
    deleted       boolean NOT NULL DEFAULT false
);

CREATE TABLE prescription_item (
    id              uuid PRIMARY KEY,
    prescription_id uuid NOT NULL REFERENCES prescription (id),
    position        integer NOT NULL DEFAULT 0,
    medicine        text NOT NULL,
    dosage          text,
    frequency       text,
    duration_days   integer,
    instructions    text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz,
    deleted         boolean NOT NULL DEFAULT false
);

-- ---------- Payments ----------
CREATE TABLE payment (
    id                       uuid PRIMARY KEY,
    appointment_id           uuid NOT NULL REFERENCES appointment (id),
    stripe_payment_intent_id varchar(100) UNIQUE,
    amount                   numeric(10, 2) NOT NULL,
    mode                     varchar(30),
    status                   varchar(30) NOT NULL,
    refunded_amount          numeric(10, 2) NOT NULL DEFAULT 0,
    paid_at                  timestamptz,
    receipt_pdf_key          varchar(300),
    created_at               timestamptz NOT NULL DEFAULT now(),
    updated_at               timestamptz,
    deleted                  boolean NOT NULL DEFAULT false
);
CREATE INDEX ix_payment_appointment ON payment (appointment_id);

-- ---------- Notifications ----------
CREATE TABLE notification (
    id              uuid PRIMARY KEY,
    appointment_id  uuid REFERENCES appointment (id),
    recipient_email varchar(255) NOT NULL,
    type            varchar(30) NOT NULL,
    channel         varchar(20) NOT NULL,
    scheduled_at    timestamptz NOT NULL,
    sent_at         timestamptz,
    status          varchar(20) NOT NULL,
    last_error      varchar(500),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz,
    deleted         boolean NOT NULL DEFAULT false
);
CREATE INDEX ix_notification_due ON notification (status, scheduled_at);

-- ---------- Reviews ----------
CREATE TABLE review (
    id             uuid PRIMARY KEY,
    appointment_id uuid NOT NULL UNIQUE REFERENCES appointment (id),
    patient_id     uuid NOT NULL REFERENCES patient (id),
    doctor_id      uuid NOT NULL REFERENCES doctor (id),
    rating         integer NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment        varchar(2000),
    status         varchar(20) NOT NULL,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz,
    deleted        boolean NOT NULL DEFAULT false
);
CREATE INDEX ix_review_doctor ON review (doctor_id, status);

-- ---------- Audit ----------
CREATE TABLE audit_log (
    id          uuid PRIMARY KEY,
    actor_id    uuid,
    action      varchar(80) NOT NULL,
    entity_type varchar(80) NOT NULL,
    entity_id   varchar(80),
    details     varchar(2000),
    ip_address  varchar(60),
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz,
    deleted     boolean NOT NULL DEFAULT false
);
CREATE INDEX ix_audit_log_created ON audit_log (created_at DESC);

CREATE TABLE access_log (
    id          uuid PRIMARY KEY,
    actor_id    uuid NOT NULL,
    patient_id  uuid NOT NULL REFERENCES patient (id),
    resource    varchar(20) NOT NULL,
    resource_id varchar(80),
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz,
    deleted     boolean NOT NULL DEFAULT false
);
CREATE INDEX ix_access_log_patient ON access_log (patient_id, created_at DESC);

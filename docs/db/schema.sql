-- Hotel PMS — schema (logical DDL)
-- Conventions:
--   * lowercase snake_case identifiers (Postgres folds unquoted names to lowercase anyway)
--   * every table carries the same metadata columns (created_at/by, updated_at/by)
--   * enum TYPES are created before any table that uses them (types are global; columns are local)

-- ─────────────────────────────────────────────────────────────
-- Platform-level admins/users — the ONLY table with no hotel_id
CREATE TABLE IF NOT EXISTS user_pms (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        varchar NOT NULL,
    email       varchar NOT NULL,
    phone       varchar NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    created_by  bigint,
    updated_at  timestamptz,
    updated_by  bigint
);

-- Tenants
CREATE TABLE IF NOT EXISTS hotels (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        varchar NOT NULL,
    email       varchar NOT NULL,
    phone       varchar NOT NULL,
    city        varchar NOT NULL,
    state       varchar NOT NULL,
    pincode     varchar NOT NULL,          -- text, not int: identifiers can have leading zeros / '+'
    created_at  timestamptz NOT NULL DEFAULT now(),
    created_by  bigint,
    updated_at  timestamptz,
    updated_by  bigint
);

-- Staff (hotel-scoped)
CREATE TYPE staff_role AS ENUM ('MANAGER', 'RECEPTIONIST');

CREATE TABLE IF NOT EXISTS hotel_users (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    hotel_id    bigint NOT NULL REFERENCES hotels(id),
    name        varchar NOT NULL,
    email       varchar NOT NULL,
    phone       varchar NOT NULL,
    role        staff_role NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    created_by  bigint,
    updated_at  timestamptz,
    updated_by  bigint
);

-- Guests (hotel-scoped; per-hotel storage)
CREATE TABLE IF NOT EXISTS hotel_guests (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    hotel_id    bigint NOT NULL REFERENCES hotels(id),
    name        varchar NOT NULL,
    phone       varchar NOT NULL,
    email       varchar,                    -- nullable: email is optional
    created_at  timestamptz NOT NULL DEFAULT now(),
    created_by  bigint,
    updated_at  timestamptz,
    updated_by  bigint
);

-- Rooms (hotel-scoped)
CREATE TYPE room_type   AS ENUM ('SINGLE', 'DOUBLE', 'SUITE');
CREATE TYPE room_status AS ENUM ('AVAILABLE', 'OCCUPIED', 'CLEANING', 'MAINTENANCE');

CREATE TABLE IF NOT EXISTS hotel_rooms (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    hotel_id     bigint NOT NULL REFERENCES hotels(id),
    room_number  varchar NOT NULL,
    floor        int NOT NULL,
    type         room_type NOT NULL,        -- short, contextual: 'rooms.type'
    status       room_status NOT NULL,
    rate         numeric(10,2) NOT NULL CHECK (rate >= 0),   -- price per night
    created_at   timestamptz NOT NULL DEFAULT now(),
    created_by   bigint,
    updated_at   timestamptz,
    updated_by   bigint,
    CONSTRAINT uq_room_per_hotel UNIQUE (hotel_id, room_number)
);

-- Bookings (hotel-scoped; the core)
CREATE TYPE booking_status AS ENUM ('PENDING', 'CONFIRMED', 'CHECKED_IN', 'CHECKED_OUT', 'CANCELLED');

CREATE TABLE IF NOT EXISTS hotel_bookings (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    hotel_id         bigint NOT NULL REFERENCES hotels(id),
    guest_id         bigint NOT NULL REFERENCES hotel_guests(id),
    room_id          bigint NOT NULL REFERENCES hotel_rooms(id),   -- the assigned physical room
    room_type        room_type NOT NULL,                           -- the type the guest requested
    check_in_date    date NOT NULL,
    check_out_date   date NOT NULL,
    status           booking_status NOT NULL,
    created_at       timestamptz NOT NULL DEFAULT now(),
    created_by       bigint,
    updated_at       timestamptz,
    updated_by       bigint,
    CONSTRAINT chk_booking_dates CHECK (check_out_date > check_in_date)
);

-- Payments (one booking -> many payment rows)
CREATE TYPE payment_status AS ENUM ('INITIATED', 'SUCCESS', 'FAILED');

CREATE TABLE IF NOT EXISTS payments (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    hotel_id    bigint NOT NULL REFERENCES hotels(id),
    booking_id  bigint NOT NULL REFERENCES hotel_bookings(id),
    amount      numeric(12,2) NOT NULL CHECK (amount >= 0),
    status      payment_status NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    created_by  bigint,
    updated_at  timestamptz,
    updated_by  bigint
);

-- ─────────────────────────────────────────────────────────────
-- Indexes (per query pattern; see ERD notes).
-- We deliberately DON'T add a standalone (hotel_id) index where a composite or UNIQUE index
-- already begins with hotel_id — its leftmost prefix already serves hotel_id-only filters.
-- Over-indexing costs write speed and storage.
CREATE INDEX idx_hotel_users_hotel ON hotel_users (hotel_id);                 -- nothing else covers this
CREATE INDEX idx_guests_phone      ON hotel_guests (hotel_id, phone);         -- returning-guest lookup; also covers hotel_id-only
-- hotel_rooms: hotel_id filters are already served by UNIQUE (hotel_id, room_number)
CREATE INDEX idx_bookings_overlap  ON hotel_bookings (hotel_id, room_id, check_in_date, check_out_date); -- double-booking check
CREATE INDEX idx_bookings_by_date  ON hotel_bookings (hotel_id, check_in_date);   -- "today's arrivals" report
CREATE INDEX idx_payments_booking  ON payments (booking_id);

-- NOTE: the airtight "no two active bookings overlap on the same room" rule will later become a
-- Postgres EXCLUDE constraint (GiST) — the concurrency highlight — added at the booking deep-dive.

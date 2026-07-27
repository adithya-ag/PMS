# Product Requirements Document — Hotel PMS

> **Status:** Functional requirements only. Non-functional requirements (performance, security
> hardening, scalability targets) are intentionally excluded at this stage. This document is the
> agreed "what & why"; the ERD and schema will turn it into "how."

---

## 1. Problem statement / overview

A Hotel Property Management System (PMS) is a backend platform that lets a hotel's receptionists,
managers, and owners run daily operations — rooms, guests, reservations, and payments — from one
system instead of error-prone manual records.

The platform is **multi-tenant**: a single deployment serves many independent hotels. This lets us
maintain one codebase and share infrastructure (lower cost) while guaranteeing **strict data
isolation** — no hotel can see or modify another hotel's rooms, guests, bookings, or finances.

## 2. Actors / roles

The Guest is **not** a system user — they are a person the hotel serves, represented as a record
that staff manage. (Guest self-service login is a future/backlog item.)

| Actor | Scope | What they can do | What they must NOT do |
|-------|-------|------------------|-----------------------|
| **Super Admin** | Platform (not hotel-scoped) | Onboard, verify, enable/disable hotels; manage subscriptions & billing | View or operate a hotel's operational records (rooms, guests, bookings) |
| **Hotel Manager** | One hotel | Oversee operations; create/manage staff & roles; configure room inventory & pricing; view revenue/occupancy reports | Access any other hotel's data |
| **Receptionist** | One hotel | Check availability; create bookings for verified guests; manage check-in/check-out and payments | Change room inventory or pricing |

## 3. Core entities (plain description — NOT the schema)

- **Hotel (tenant):** A physical property on the platform; an isolated operational unit with its
  own rooms, staff, guests, and financial records.
- **Room:** A physical accommodation unit belonging to exactly one hotel. Has a **type**
  (SINGLE / DOUBLE / SUITE), a **nightly rate**, and a live **status**
  (AVAILABLE / OCCUPIED / CLEANING / MAINTENANCE).
- **Guest:** A person served by a specific hotel, stored **per-hotel** (see §4.3). Staff record
  their identity and verification details.
- **Booking / Reservation:** The record of one guest's agreed stay in one room for a date range.
  Tracks the booking's **lifecycle state** and, separately, whether it has been **paid**.
- **Staff / User:** People who operate the system — platform-level Super Admins and hotel-level
  Managers and Receptionists.

## 4. Functional requirements

### 4.1 Hotel / tenant management
- As a **Super Admin**, I want to verify and onboard new hotels and enable/disable their accounts,
  so that only authorized, active tenants can use the PMS.
- **Isolation requirement:** a hotel's data must never be readable or writable by another hotel.

### 4.2 Room management
- As a **Hotel Manager**, I want to create and manage the room inventory — each room's type
  (SINGLE / DOUBLE / SUITE) and nightly rate.
- As a **Receptionist**, I want to update a room's live status as real-world events happen.
- **Room statuses:** `AVAILABLE`, `OCCUPIED`, `CLEANING`, `MAINTENANCE`.
- **Legal transitions only:** a room must not go `OCCUPIED → AVAILABLE` directly; it passes through
  `CLEANING` first. Room status is largely driven by booking events (see §4.4).

### 4.3 Guest management
- As a **Receptionist**, I want to record a guest and their verification details before assigning a
  room, so the hotel keeps a lawful record of who stayed.
- **Guests are stored per-hotel** — each hotel owns its own guest records. A hotel can recognize a
  returning guest *of its own* (e.g., by mobile number) but cannot see that guest's activity at any
  other hotel.

### 4.4 Booking / Reservation  ← core of the system

**Information needed to create a booking:** guest details (name, phone, email) and verification;
the requested room type; the check-in and check-out dates. From these, staff can determine whether
the booking is possible for those dates.

**Booking lifecycle states (the booking's own state machine):**
- `PENDING` — reservation requested but not yet secured (e.g., awaiting payment/confirmation); room tentatively held.
- `CONFIRMED` — reservation secured; guest expected on the check-in date.
- `CHECKED_IN` — guest has arrived and taken the room (this event turns the room `OCCUPIED`).
- `CHECKED_OUT` — guest has departed; stay complete (this event turns the room `CLEANING`).
- `CANCELLED` — reservation voided before check-in.
- *(Backlog: `NO_SHOW` — a confirmed guest who never arrived.)*

**Transitions (event → resulting state):**
- *create* → `PENDING`
- *payment / guarantee completed* → `CONFIRMED`
- *guest arrives (staff check-in)* → `CHECKED_IN`
- *guest departs (staff check-out)* → `CHECKED_OUT`
- *staff or guest cancels within policy* → `CANCELLED`

**Payment is a SEPARATE dimension from lifecycle.** A booking is `PAID` or `UNPAID` independently of
where it sits in its lifecycle (e.g., pay-at-hotel means `CONFIRMED` + `UNPAID`). See §4.6.

**THE critical rule (when to refuse a booking):** the system must refuse to create or confirm a
booking if the target room already has an **active** booking (`CONFIRMED` or `CHECKED_IN`) whose
date range **overlaps** the requested dates. Two ranges overlap when:
`requested_check_in < existing_check_out` **AND** `requested_check_out > existing_check_in`.

**Check-in / check-out effects:** check-in flips the assigned room to `OCCUPIED`; check-out flips it
to `CLEANING` (a later staff action returns it to `AVAILABLE`).

**Cancellation:** allowed according to each hotel's cancellation policy (free vs. paid window). The
policy is a hotel-level configuration.

### 4.5 Staff / user management
- A staff member belongs to **exactly one hotel** (Super Admins are platform-level, not hotel-scoped).
- Roles: **Manager** and **Receptionist** (plus the platform **Super Admin**).
- The Super Admin creates a hotel's initial **Manager** account; the Manager creates **Receptionist**
  accounts.

### 4.6 Payments (simulated)
- **No real money.** The system tracks, per booking, whether it is `PAID` or `UNPAID`, and the amount
  owed (derived from the room's rate × number of nights).
- Two natural checkpoints: **at reservation** (to confirm) and **at check-out** (to settle any
  outstanding charges, e.g., room service).

### 4.7 Reporting (basic, read-only)
- **Rooms available right now** (for a hotel and date range) — the most-queried view; *computed* from
  bookings, not stored.
- **Today's check-ins (arrivals)** for my hotel.
- **Rooms currently occupied.**

## 5. Business rules / invariants

Things that must ALWAYS be true / must NEVER happen (these become DB constraints and validation):

1. A room can never have two **active** bookings (`CONFIRMED` or `CHECKED_IN`) with overlapping dates.
2. A booking's **check-out date must be strictly after its check-in date**.
3. A booking's room and guest must belong to the **same hotel** as the booking (tenant integrity).
4. Booking states may only advance **legally**: `CONFIRMED → CHECKED_IN → CHECKED_OUT`. You cannot
   check out a booking that was never checked in, nor check in one that isn't confirmed.
5. A room must not move `OCCUPIED → AVAILABLE` directly; it passes through `CLEANING`.
6. A payment **amount must be ≥ 0**; a booking cannot be marked `PAID` without a valid amount.
7. The number of simultaneously **active** bookings for a hotel can never exceed its number of
   physical rooms.
8. Every tenant-scoped read or write must be filtered by `hotel_id` — no query may cross tenants.

## 6. Out of scope

This project deliberately will NOT include:

- **Real payment processing** — payments are simulated (mock succeed/fail); backend only records
  `PAID / UNPAID`.
- **A real front end** — only a thin, AI-built demo UI; real learning is proven via automated tests.
- **Real email / SMS delivery** — notification delivery (SMTP) is theory only, built last if time allows.
- **Non-functional requirement *targets/SLAs*** — no hard performance, scalability, or availability
  numbers are specified (premature before the system exists). NFR *awareness* — why tools like
  indexing, caching, and queues exist and where they touch this project — is tracked in
  `docs/NFR-notes.md`.
- **Heavy reporting / analytics / BI** — only the few basic read-only views in §4.7.
- **Multi-currency, taxes, invoicing, refunds** — payment tracking stays minimal.
- **Housekeeping / inventory** beyond a room's status field.
- **External integrations** — no OTA / channel-manager integrations (Booking.com, Expedia, etc.).

## 7. Planned later phases (IN SCOPE — committed, built after the core)

The core CRUD and the booking concurrency work are built **first, without auth**, to keep early
learning focused. These are then layered on as committed later phases of this **same** project —
they are neither out of scope nor optional:

- **Authentication & authorization** — **Spring Security + JWT** (complementary layers, not alternatives).
- **Notification plugin (Observer pattern)** — the extensibility test, built last. Message *delivery*
  is simulated; **SMTP is covered as theory only** (no real email is sent).

## 8. Future / backlog (remembered, NOT committed — only if time remains)

- **Guest self-service** — guests logging in to browse and book.
- **Subscription tiers & per-hotel account limits** — e.g., cap receptionist/manager counts per hotel.
- **`NO_SHOW` handling** and automated cancellation windows.
- **Asynchronous processing** — e.g., sending notifications without blocking a booking response.
- **File / ID-document upload** — guest ID as image/PDF (HTTP multipart / object storage, not FTP).
- **WebSocket** — real-time dashboard updates.
- **Database-per-tenant isolation** — optional bonus only; the committed approach is a **single shared DB**.

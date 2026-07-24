# Product Requirements Document — Hotel PMS

> **How to use this skeleton (read me):**
> This is a *middle-ground* template: section headings + prompting questions, but the answers
> are yours to write. Delete the italic prompts as you replace them with real content.
> Scope for now = **functional requirements only** (what the system does). No non-functional
> requirements yet. Keep it plain-English. If you can't answer a prompt, that's a signal to
> come ask — a gap here is cheaper than a gap in code.

---

## 1. Problem statement / overview
*In 3–5 sentences: what is this system, who is it for, and what problem does it solve?
Why does "multi-tenant" matter here — what does it mean for one software to serve many hotels?*

## 2. Actors / roles
*Who interacts with the system? For each: what are they allowed to do, and what should they
NOT be able to do? Think: hotel manager, receptionist, guest, (the platform itself?).*

| Actor | What they can do | What they must NOT do |
|-------|------------------|-----------------------|
|       |                  |                       |

## 3. Core entities (plain description — NOT the schema yet)
*One or two lines each describing what the thing is in the real world. Resist adding database
fields here; that's the ERD's job. Just: what is a Hotel? a Room? a Guest? a Booking? a Staff member?*

- **Hotel (tenant):**
- **Room:**
- **Guest:**
- **Booking / Reservation:**
- **Staff / User:**

## 4. Functional requirements (the heart of the PRD)
*Write these as user stories: "As a [role], I want to [do something] so that [reason]."
Group them by area. I've seeded prompting questions — answer the ones that apply and add your own.*

### 4.1 Hotel / tenant management
*How does a hotel exist in the system? Who creates it? Does data from Hotel A ever touch Hotel B?*

### 4.2 Room management
*A room has a type (SINGLE/DOUBLE/SUITE), a rate, and a status (AVAILABLE/OCCUPIED/MAINTENANCE).
Who changes a room's status, and when? Can a room move from any status to any other, or are some
transitions illegal? (This question matters a lot later.)*

### 4.3 Guest management
*How does a guest get into the system? Do they self-register, or does staff create them?
Can the same person be a guest at two different hotels — is a Guest global or per-hotel?*

### 4.4 Booking / Reservation  ← the most important section
*Walk through the full life of a booking in plain English, start to finish.*
- *What information is needed to create one?*
- *What are the possible states of a booking, and what event moves it from one to the next?*
  *(e.g. created → confirmed → checked-in → checked-out; where does cancellation fit?)*
- *THE critical rule: under what condition must the system REFUSE to create a booking?*
  *Describe "overlapping dates for the same room" precisely — when do two date ranges overlap?*
- *What happens on check-in and check-out — does anything else change (e.g. room status)?*
- *Can a booking be cancelled? By whom, and until when?*

### 4.5 Staff / user management
*Who creates staff accounts? Does a staff member belong to exactly one hotel? What roles exist?*

### 4.6 Payments (simulated)
*We won't process real money. What does the system need to track — who paid, how much, paid/unpaid?
When in the booking life does payment happen?*

### 4.7 Reporting (very basic)
*What are the 1–2 simplest read-only views that would tell you the app is working?
(e.g. "list today's check-ins for my hotel", "which rooms are occupied right now?")*

## 5. Business rules / invariants
*List the things that must ALWAYS be true or must NEVER happen, regardless of feature.
Example shape: "A room can never have two active bookings with overlapping dates."
These become database constraints and validation later — the stricter you are here, the safer the code.*

## 6. Out of scope (be explicit — saying no is a PRD skill)
*List what this project deliberately will NOT do, so no one expects it.*

## 7. Future / backlog (nice-to-have, not now)
*Park ideas here so they're remembered but don't creep into current work.*
- Async processing, file/ID-document upload, WebSocket real-time updates, real auth,
  email notifications, database-per-tenant isolation.

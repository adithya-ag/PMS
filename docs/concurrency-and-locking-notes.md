# Concurrency, Locking & Indexes — Notes

> Written the way the questions actually came up: **the confusion first, then what made it click.**
> Sessions of 2026-08-21 → 2026-08-23, while fixing the double-booking bug.
> Companions: `docs/local/PROGRESS.md` (project state) · `docs/local/LEARNING-LOG.md` (concept index).

## Contents
1. [What is my code actually touching?](#1)
2. [Why the transaction didn't save me](#2)
3. [What is a lock, physically?](#3)
4. [Coarse vs fine — and what "more concurrency" means](#4)
5. [Shared vs exclusive locks](#5)
6. [Concurrency vs parallelism](#6)
7. [MVCC — "a new version? then does the ID change?"](#7)
8. [Pessimistic vs optimistic — and why `@Version` can't fix my bug](#8)
9. [Composite indexes and the leftmost-prefix rule](#9)
10. [What an index is physically made of](#10)
11. [Reading `EXPLAIN ANALYZE`](#11)
12. [`findByIdAndHotelId` is authorization, not retrieval](#12)
13. [The `EXCLUDE` constraint](#13)
14. [`SERIALIZABLE` isolation](#14)
15. [Booking lifecycle: State pattern vs enum transition table](#15)
16. [Multi-tenancy: convention → framework → database](#16)
17. [The one principle underneath all of it](#17)

---

<a id="1"></a>
## 1. What is my code actually touching?

**My question:** *"I don't understand the database part. Why are we checking `hotel_bookings` to see if a
room is free? Are we scanning the whole table?"*

**What made it click — first, list every table the request touches:**

| # | Call | Table | Read/Write |
|---|---|---|---|
| 1 | `hotelRepo.findById` | `hotels` | READ |
| 2 | `guestRepo.findByIdAndHotelId` | `hotel_guests` | READ |
| 3 | `roomRepo.findByIdAndHotelId` | `hotel_rooms` | READ |
| 4 | `repo.existsOverlapping` | `hotel_bookings` | **READ** ⚠️ |
| 5 | `repo.save(booking)` | `hotel_bookings` | **WRITE** ⚠️ |

Only **one** table is ever written. Steps 1–3 are lookups and are not where the bug lives.
The bug lives entirely in the gap between step 4 and step 5.

**Why availability comes from `hotel_bookings` and not `hotel_rooms.status`:**

`hotel_rooms.status` is a single value describing **right now** — a housekeeping fact
(AVAILABLE / OCCUPIED / CLEANING / MAINTENANCE).

But the question is *"is room 5 free from Sept 1 to Sept 5?"* — a question about **a time range,
possibly months away**. One column cannot answer that. Only the booking rows can.

> **Availability is not a column. It is derived by querying the bookings.**

So yes: one room has **many** booking rows (past, future, cancelled, checked-out), and you filter by
BOTH the status (`BLOCKING_STATUSES`) AND the date overlap. That is the standard model — hotels,
airlines and cinemas all do it this way.

**The shape of the bug has a name:**

```
 step 4:  SELECT ... "is room 5 free?"   →  "yes"
              ⏱  a gap in TIME lives here
 step 5:  INSERT booking for room 5
```

You **read** to make a decision, then **write** based on it. Between the two, the world can change.
That is **check-then-act**, also called **TOCTOU** (Time Of Check To Time Of Use). The same shape as:

```java
if (map.get(key) == null) {   // check
    map.put(key, value);      // act   ← another thread may have put() in between
}
```

`Thread.sleep(3000)` did not *create* the bug. It **stretched the gap** from 2 ms to 3 s so it could be
hit reliably from two Postman tabs.

**The wider family of concurrency bugs:**

| Bug | Shape |
|---|---|
| Dirty read | I read data you never committed |
| Lost update | Both read `10`, both write `9`. Two sales, one decrement. |
| Non-repeatable read | Same row read twice in one TX, different values |
| **Phantom read** | Same *query* twice, different *set of rows* — new rows appeared |
| **Write skew** | Both TXs individually correct; the pair violates an invariant |
| **Check-then-act** | The code-level name for the two above |
| Deadlock | A holds x wants y; B holds y wants x |

---

<a id="2"></a>
## 2. Why the transaction didn't save me

**My realisation (this was correct):** *"the database isn't breaking any rules, because I never told it
this rule. I never declared a unique constraint or anything like it."*

**That is exactly right, and it is the most important idea in this whole document:**

> Postgres will defend **every rule you have declared** with absolute rigour, under any concurrency,
> forever. It will defend **zero rules you have not declared.**

`hotel_bookings` declares: a primary key, three foreign keys, and `check_out_date > check_in_date`.
Two overlapping bookings violate **none of them**. The database did its job perfectly.

The rule *"no two blocking bookings for the same room may overlap"* existed only in Java — and Java
cannot enforce anything across concurrent transactions.

```
        TX-A                                  TX-B
   t1   read → "room 5 free"
   t2                                         read → "room 5 free"
   t3   INSERT (uncommitted, invisible)
   t4                                         INSERT (uncommitted, invisible)
   t5   COMMIT ✅                              COMMIT ✅
                    Two rows. Both TXs individually CORRECT. Together: wrong.
```

`@Transactional` gives **atomicity** (all-or-nothing) and **isolation from uncommitted data**.
It does **not** give **mutual exclusion**.

---

<a id="3"></a>
## 3. What is a lock, physically?

**My question:** *"I need this broken down so any CS fresher would get it."*

**What made it click — the café toilet key:**

A lock is **not** a wall around your data. Nothing is hidden, encrypted or made read-only. A lock is
literally:

> **A note in the database's shared memory saying "transaction #4711 currently owns row X."**
> Plus a rule everyone agrees to follow: *"before I touch X, I check the note. If someone else owns it,
> I sleep until they're done."*

The café has one toilet and one key. The key doesn't lock the door — anyone *could* walk in. But
everyone agrees to take the key first, and there is only one key. **The token plus the discipline is
what creates the safety.**

The consequence that matters most:

> **A lock only excludes people who ask for it.**
> If one code path takes the lock and another writes without asking, the second walks straight past.

**How the waiting physically works:**

```
 TX-B asks for the lock on hotel_rooms row 5
   ├── free?  → write "TX-B owns it", continue immediately
   └── owned by TX-A?
          → Postgres puts TX-B's backend process to SLEEP (an OS-level wait)
          → TX-B's Tomcat thread is BLOCKED. 0% CPU. It just sits.
          → when TX-A commits, Postgres WAKES TX-B
          → TX-B re-reads the row AS IT IS NOW, and continues
```

That last line is the entire fix. TX-B does **not** resume with its old stale view.

**How long is a lock held?**

> **Until the transaction ends — COMMIT or ROLLBACK. Always. There is no "unlock" statement.**

Not until the method returns; until the *transaction* ends. In Spring terms: until the `@Transactional`
proxy commits. This is why holding a lock while doing something slow (an HTTP call, a sleep) is a
performance disaster — everyone queues behind you.

---

<a id="4"></a>
## 4. Coarse vs fine — and what "more concurrency" means

**My question:** *"you said coarse and fine without defining them. And what does 'more concurrency,
more thinking required' mean? Also — isn't a column BIGGER than a row?"*

**On the column point: I was right, the AI's ladder was wrong.** A column spans every row, so it is
"bigger" than a row. **There is no column-level locking in Postgres.** The real ladder:

```
COARSE ─────────────────────────────────────────► FINE
whole database  →  table  →  page (8KB)  →  ROW
                                            ▲ the finest lock you can actually take
```

- **Coarse-grained** = one lock covers a lot (lock the whole table)
- **Fine-grained** = many small locks, each covering a little (lock one row)

**"More concurrency" simply means: more requests can make progress at the same time.**

```
 TABLE LOCK on hotel_rooms:          ROW LOCK on hotel_rooms:
   booking room 5  🔑                  booking room 5  🔑 proceeds
   booking room 6  😴 waits            booking room 6  🔑 proceeds
   booking room 7  😴 waits            booking room 7  🔑 proceeds
   → 1 booking at a time, HOTEL-WIDE   → 1 booking at a time, PER ROOM
```

**"More thinking required" = fine-grained locking makes YOU responsible for things the big lock
handled automatically:**

1. **Deadlocks become possible.** One big lock cannot deadlock — there's only one key. With many keys,
   A holds room 5 and wants 6 while B holds 6 and wants 5. *Prevention: always acquire multiple locks
   in a consistent order, e.g. ascending by id.*
2. **You must pick the right row.** Lock the wrong one and you've excluded nobody.
3. **Every code path must opt in.** One person who doesn't take the key ruins it for everyone.

---

<a id="5"></a>
## 5. Shared vs exclusive locks

**My guess (WRONG, worth recording):** *"shared means I lock it so I can read and write, but others can
only read."*

**The correction:** shared does **not** give you write permission.

| I hold | I may | Others: plain SELECT | Others: SHARED | Others: EXCLUSIVE/write |
|---|---|---|---|---|
| **SHARED** (`FOR SHARE`) | read, guaranteed it won't change | ✅ | ✅ | ❌ wait |
| **EXCLUSIVE** (`FOR UPDATE`) | read **and** write | ✅ **yes!** | ❌ wait | ❌ wait |

Two corrections to my sentence:
- **SHARED = "I'm reading this and need it to stay still. Others may read too; nobody may change it."**
  It's *many readers*, not one reader plus a writer.
- **EXCLUSIVE does not stop plain reads.** It blocks other *lock requests* and writes. See §7 (MVCC).

**Mnemonic:** shared locks are compatible **with each other** — that is the only reason the mode exists.

**"Can these two coexist?" — WHICH two?** *(I had to ask.)* Two **transactions**, wanting a lock on the
**same row**. Read the matrix as: *"TX-A holds mode X. TX-B now asks for mode Y. Does TX-B get it, or sleep?"*

```
                     TX-B asks for →
                  SHARED        EXCLUSIVE
 TX-A  SHARED     ✅ both        😴 B sleeps
 holds EXCLUSIVE  😴 B sleeps    😴 B sleeps
```

Only one cell lets both proceed.

**In JPA:** `LockModeType.PESSIMISTIC_READ` → `FOR SHARE`; `LockModeType.PESSIMISTIC_WRITE` → `FOR UPDATE`.

---

<a id="6"></a>
## 6. Concurrency vs parallelism

**My guess:** *"parallel processing of sorts — one thing goes on, another can too, without being affected."*
Close, but it merges two different ideas.

| | **Concurrency** | **Parallelism** |
|---|---|---|
| Meaning | *Dealing with* many things at once | *Doing* many things at once |
| Needs multiple cores? | **No** | **Yes** |
| Mechanism | tasks **interleave** | tasks run at the **same instant** |

**The chef analogy:**
- **Concurrency, one chef:** puts pasta on to boil and *while it boils* chops onions, stirs sauce, drains
  pasta. One person, never idle, three dishes in flight — but only ever doing **one** thing at any instant.
- **Parallelism, three chefs:** genuinely simultaneous.

**Why it matters here:**

> **Concurrency alone is enough to break the code. Parallelism is not required.**

```
 one core:  [TX-A: SELECT "free"] [TX-B: SELECT "free"] [TX-A: INSERT] [TX-B: INSERT]
                                  ▲ the OS switched threads HERE
            Nothing ran "at the same time". The bug still happens.
```

`Thread.sleep(3000)` is forcing that switch by hand, so it happens every time instead of once in a thousand.

And now "more concurrency" reads correctly: **more tasks in flight at once.**

---

<a id="7"></a>
## 7. MVCC — "a new version? then does the ID change?"

**My question:** *"you said UPDATE writes a new version rather than overwriting. Then the primary key
changes, right? And other tables point at this one via foreign keys — so how does that work?"*

**Great worry to have. The answer clears up a lot.**

Every row in Postgres carries hidden system columns. Two matter:

| Hidden column | Meaning |
|---|---|
| `xmin` | the transaction id that **created** this version |
| `xmax` | the transaction id that **superseded/deleted** it (0 = still live) |

An `UPDATE` does not overwrite the bytes:

```
BEFORE                              AFTER  UPDATE hotel_rooms SET status='OCCUPIED' WHERE id=5
┌───────────────────────────┐       ┌───────────────────────────┐
│ xmin=100  xmax=0          │       │ xmin=100  xmax=205 ← DEAD │
│ id=5  status='AVAILABLE'  │       │ id=5  status='AVAILABLE'  │
└───────────────────────────┘       ├───────────────────────────┤
                                    │ xmin=205  xmax=0   ← LIVE │
                                    │ id=5  status='OCCUPIED'   │
                                    └───────────────────────────┘
```

**"Won't the ID change?"** **No.** `id` is an ordinary data column, copied verbatim into the new version.
Both versions say `id = 5`. What differs is the **physical address** of the tuple on disk (`ctid`) — an
internal detail nothing in the app ever references.

**"But foreign keys point here!"** FKs reference the **logical value** `id = 5`, not a disk address.
`hotel_bookings.room_id = 5` still resolves fine.

**"So which version does a reader see?"** Every transaction has a **snapshot** — which transaction ids were
committed when it began. Walking a row's version chain it picks the newest version whose `xmin` is visible
to its snapshot and whose `xmax` is not. **Two transactions can look at the same `id = 5` and legitimately
see different versions. That is MVCC. Nobody waits.**

**"Doesn't the table grow forever?"** It would. Dead versions are reclaimed by **`VACUUM`** (autovacuum runs
it automatically). Growth from dead tuples is called **bloat** — the operational price Postgres pays for
never blocking readers.

**And `FOR UPDATE`?** It creates no version. It stamps a *lock marker* into the live tuple's header. Another
`FOR UPDATE` sees it and sleeps. Plain readers never look at it.

**The rule to memorise:**

> **Readers never block writers. Writers never block readers. Writers block writers.**

**⚠️ This is a Postgres/Oracle guarantee, not a SQL guarantee:**

| Database | Plain `SELECT` |
|---|---|
| PostgreSQL, Oracle | MVCC — never takes row locks |
| MySQL / InnoDB | MVCC, plus gap/next-key locks at REPEATABLE READ |
| **SQL Server** (default) | ❗ **locks on read** — SELECTs block writers. The classic source of SQL Server blocking. |
| SQLite | whole-database lock |

---

<a id="8"></a>
## 8. Pessimistic vs optimistic — and why `@Version` can't fix my bug

The words describe **your assumption about how often conflicts happen.**

| | Assumption | Strategy | Analogy |
|---|---|---|---|
| **Pessimistic** | "a clash is likely" | **Take the lock up front.** Others wait. | take the toilet key before walking over |
| **Optimistic** | "a clash is rare" | **Take no lock.** At write time, check whether anyone moved. If so, **throw and retry.** | walk in; if occupied, come back later |

Pessimistic **prevents** the conflict. Optimistic **detects** it afterwards.

### How `@Version` actually works

**My question:** *"what does `id=5 AND version=3` mean? And what does '0 rows affected means someone beat
you' mean?"*

Add a `version int` column. Nobody sets it — **Hibernate owns it.**

```
hotel_rooms:  id=5  status='CLEANING'  version=3
```

Two receptionists open room 5 at the same moment. Both read `version = 3`. **No lock is taken; nobody waits.**

```sql
-- TX-A commits first
UPDATE hotel_rooms SET status='AVAILABLE', version=4
 WHERE id=5 AND version=3;        -- ⭐ "only if nobody moved since I looked"
-- → 1 row affected ✅   row is now version=4

-- TX-B commits a moment later
UPDATE hotel_rooms SET status='MAINTENANCE', version=4
 WHERE id=5 AND version=3;        -- but the row says version=4 now
-- → 0 ROWS AFFECTED
```

That's the whole mechanism. The `WHERE` matched **nothing**, because the version moved. Postgres reports no
error — it just updated nothing. Hibernate counts the affected rows, sees 0, and concludes *"my update
matched no row, so someone changed it after I read it"* — and throws **`OptimisticLockException`**.

**"Someone beat you"** = another transaction committed a change to that row between your read and your
write. You lost the race — and crucially **you find out**, instead of silently overwriting their work.

### ⭐ The misconception worth killing

**My reasoning was:** *"room status can be optimistic because it doesn't have to be completely accurate."*
**Right conclusion, wrong reason.**

> Optimistic locking is **not** less accurate than pessimistic. It is exactly as correct. It does not
> tolerate a wrong answer — it **refuses** the second write and throws.

The choice is **never** about accuracy. It's about **when you pay**:

| | Pessimistic | Optimistic |
|---|---|---|
| Cost when no conflict | you still pay lock + waiting | **zero** |
| Cost when conflict | none, you just waited | exception → **you must retry** |
| Use when | hot contended row (last seat on a flight) | rarely contended (a room's housekeeping status) |

If conflicts are rare, optimistic is nearly free. If they're common, everyone retries in a storm and you'd
have been better off queueing.

### Why it cannot fix double-booking

> `@Version` guards an **UPDATE of a row that exists**.
> Double-booking is an **INSERT of a row that does not exist yet**.
> No row → no version → no `WHERE` clause to fail. **The mechanism has nothing to attach to.**

**Where `@Version` DOES belong in this project:** `Booking.status` and `Room.status` transitions — two
receptionists editing the same record. A 10-minute add (`@Version private Integer version;` + a column),
not a phase.

---

<a id="9"></a>
## 9. Composite indexes and the leftmost-prefix rule

**My question:** *"I get the rule but not the why. Break it down more."*

**The one sentence everything follows from:**

> **An index is a single list, physically sorted by all its columns at once, left to right.
> Not one list per column. ONE list.**

`(hotel_id, room_id, check_in_date)` as actually stored:

```
   hotel_id │ room_id │ check_in_date
   ─────────┼─────────┼──────────────
       1    │    3    │  2026-09-01
       1    │    3    │  2026-11-20     ┐
       1    │    5    │  2026-08-02     │ everything for hotel 1, contiguous
       1    │    5    │  2026-09-01     │
       1    │    9    │  2026-07-14     ┘
       2    │    2    │  2026-09-01
       2    │    5    │  2026-01-09     ← room 5 again, nowhere near the first ones
       2    │    5    │  2026-09-03
       3    │    5    │  2026-05-05     ← and again
```

**Search A: `hotel_id=1 AND room_id=5`** → binary-search to hotel 1, then to room 5 within it. **ONE
contiguous block.** Read two rows, stop. *(Measured: `Buffers: shared hit=2`)*

**Search B: `room_id=5` alone** → the 5s sit in **three separate places**, and you cannot know where
without looking. There is no "start of the 5s" to jump to, because the list was never sorted by `room_id`
— `room_id` only orders rows *within* an identical `hotel_id`. So Postgres walks the whole index.
*(Measured: `Buffers: shared hit=25` — 12× the pages, for the same zero rows.)*

**The mental model that made it click:**

> A composite index is a **dictionary sorted by full phrase**, not a set of independent lookups.
> `(hotel_id, room_id)` behaves like the string `"0001|0005"`. You can binary-search a **prefix**
> (`"0001…"`) because prefixes stay together when sorted. You cannot binary-search a **suffix**
> (`"…|0005"`) because sorting scatters suffixes everywhere.

**Left-to-right is a prefix; anything else is a scatter.**

### The real finding in this project

`idx_bookings_overlap (hotel_id, room_id, check_in_date, check_out_date)` leads with `hotel_id`, but
`existsOverlapping` never filters on `hotel_id` → no seek possible.

Two fixes were considered:
- **Pad the query** — add `and b.hotel.id = :hotelId` purely to feed the index
- **Fix the index** — `(room_id, check_in_date, check_out_date)`, since `room_id` is globally unique and
  `hotel_id` adds **zero selectivity** here ⭐ **chosen**

> **Design the index for the query you run. Don't reshape the query to fit an index you happened to
> write first.**

**Deliberately NOT applied** — at this data volume both plans run in under a millisecond. Recorded as a
known trade-off. The theoretical cost of leaving it: 8 wasted bytes per index entry, slightly slower
writes forever, and no seek for the most important query.

**Changing an index is safe and cheap:**
```sql
DROP INDEX idx_bookings_overlap;
CREATE INDEX idx_bookings_overlap ON hotel_bookings (room_id, check_in_date, check_out_date);
-- production: CREATE INDEX CONCURRENTLY ...  (slower, but takes no write lock)
```
No table data is touched, no query results change, **and no Java changes** — you never name an index in
code. The planner picks by cost. An index is a pure performance object.

**The COLUMN `hotel_bookings.hotel_id` still stays** — it serves `findByHotelId` / "today's arrivals"
directly (otherwise every listing would join through `hotel_rooms`), RLS policies are written against it,
and partitioning would need it. Only the *index ordering* was questionable.

---

<a id="10"></a>
## 10. What an index is physically made of

**My question:** *"you said changing an index doesn't touch the data. Then what IS the B-tree made of?"*

**Clarification:** "doesn't touch the data" means it doesn't change your **table**. The index absolutely
*is* data — just **derived** data, rebuildable from the table at any time.

An index is **its own file**, its own 8 KB pages. Each entry is a pair:

```
   ( key value ,  pointer to where the row physically sits )
   ( 5, 2026-09-01 ,  ctid (14, 3) )    ← page 14, slot 3 of the table file
```

That pointer is why a wider index is a *bigger* index — every entry carries every key column.

```
                    ┌─────────────────┐
       ROOT         │  [ 5 ]  [ 12 ]  │   ← 1 page. "<5 left, 5–12 middle, >12 right"
                    └───┬─────┬─────┬─┘
              ┌─────────┘     │     └─────────┐
   INTERNAL ┌─▼─────┐    ┌────▼───┐     ┌─────▼──┐   ← signpost pages, 1–2 levels
            │[2][4] │    │[7][9]  │     │[15][20]│
            └─┬─────┘    └────────┘     └────────┘
   LEAF  ┌────▼───────────────────────────────────┐
         │ 5→ctid  5→ctid  6→ctid  7→ctid  ...    │  ← ALL entries, fully sorted,
         └────────────────────────────────────────┘     chained left-to-right
```

- **Leaves** hold every entry in sorted order, linked sideways — so a range scan is "find the start, walk right"
- **Internal pages** are pure signposts
- **Building it:** read every row → extract key columns → **sort** → write sorted leaf pages → build
  signpost layers on top until one root page covers everything

**A lookup is 3–4 page reads for millions of rows**, because each level multiplies reach by ~hundreds.

And the leftmost-prefix rule is now obvious in the picture: **the leaves are sorted by the full key, left
to right — the signposts can only route you if you know the leading value.**

---

<a id="11"></a>
## 11. Reading `EXPLAIN ANALYZE`

Read it **bottom-up, inside-out** — indented lines run first and feed their parent.

| Field | Means |
|---|---|
| `cost=0.15..21.54` | the planner's **guess**, arbitrary units. Only useful for *comparing* plans. |
| `actual time=` | real milliseconds |
| `rows=` | rows actually produced |
| **`Buffers: shared hit=25`** | ⭐ **8 KB pages actually read. The honest measure of work.** |
| **`Index Cond` vs `Filter`** | ⭐⭐ the most important distinction in the output |

> **`Index Cond` does NOT mean "the index seeked using this."**
> - `Index Cond` = checked **while walking the index**
> - `Filter` = checked **after fetching the row**
>
> Both narrow results. **Neither tells you how much of the index got walked.** For that, read `Buffers`.

That last point was the trap: the slow plan *looked* efficient because `room_id` appeared under
`Index Cond`. `Buffers: 25` vs `Buffers: 2` told the truth.

Also worth knowing: **the planner is cost-based, not rule-based.** Adding `hotel_id` made it switch to a
*different* index entirely (`idx_bookings_by_date`), because it estimated that one cheaper. On tiny tables
these choices are semi-arbitrary — always check `count(*)` before drawing conclusions.

---

<a id="12"></a>
## 12. `findByIdAndHotelId` is authorization, not retrieval

**My claim (WRONG, and important):** *"room_id is a globally unique primary key, so checking it belongs to
this hotel is unnecessary."*

**Why that's wrong:** `roomId` arrives in the **request body**. It is untrusted input.

```
POST /api/hotels/1/bookings
{ "roomId": 999 }          ← room 999 belongs to hotel 2
```

- `findById(999)` → found ✅ → you create a booking in hotel 1 for **hotel 2's room**. Cross-tenant breach.
- `findByIdAndHotelId(999, 1)` → not found → 404 ✅

> That lookup is not *"find the room."* It is *"prove this caller is allowed to touch this room."*
> **Authorization, not retrieval.**

This bug class is **IDOR** — Insecure Direct Object Reference — consistently in the OWASP Top 10 and the
#1 bug class in multi-tenant apps.

**The nuance that IS true:** by the time `existsOverlapping` runs, the room has *already* been proven to
belong to hotel 1. So adding `hotel_id` **there** would be redundant — see §9.

---

<a id="13"></a>
## 13. The `EXCLUDE` constraint

### Plain English first

Right now, the thing preventing double-booking is a **house rule that exists only in Java**. It works
because every code path politely agrees to take the lock first.

But rules kept by politeness get broken. A data-import script, a colleague's bulk endpoint, a hotfix
written next year by someone who never read this document, a manual `INSERT` from psql at 2am — none of
them take the lock. The moment one of them writes a booking, the protection is gone, **silently**.

`EXCLUDE` moves the rule **into the database**. There it isn't politeness — it's physics. The database
refuses to store an overlapping row no matter who asks, from what language, through what tool. There is
no way around it.

Once it exists, **the lock is no longer what makes you correct.** Its only remaining job is to make
failure *pleasant* — a tidy `409` instead of a raw database error leaking out.

### What it actually rejects

**My guess:** *"no two rows can have the value 5,10 and 5,10?"* — that describes `UNIQUE`. `EXCLUDE` is
the generalisation:

| | Rule |
|---|---|
| `UNIQUE (a, b)` | no two rows where a **=** a AND b **=** b |
| `EXCLUDE (a WITH =, b WITH &&)` | no two rows where a **=** a AND b **overlaps** b |

**`UNIQUE` is `EXCLUDE` with `=` hardcoded.** `EXCLUDE` lets you choose the operator.

```
   room 5,  Sep 1 → Sep 5      ✅ stored
   room 5,  Sep 3 → Sep 8      ❌ REJECTED — nothing is equal, but the ranges OVERLAP
   room 5,  Sep 5 → Sep 8      ✅ stored — '[)' means Sep 5 is free (the half-open rule)
   room 6,  Sep 3 → Sep 8      ✅ stored — different room
```

### The DDL

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE hotel_bookings
  ADD CONSTRAINT no_double_booking
  EXCLUDE USING gist (
      room_id WITH =,
      daterange(check_in_date, check_out_date, '[)') WITH &&
  )
  WHERE (status IN ('CONFIRMED', 'CHECKED_IN'));
```

| Piece | What it does |
|---|---|
| `USING gist` | GiST can answer *"does this overlap anything?"* — B-trees can't, they only do ordering |
| `btree_gist` | extension letting a plain `=` on a `bigint` participate in a GiST index |
| `room_id WITH =` | "same room" |
| `daterange(in, out, '[)')` | ⭐ inclusive start, exclusive end — **exactly** the `in < :out AND out > :in` rule, as a native type |
| `WITH &&` | `&&` is the range **overlap** operator |
| `WHERE (status IN ...)` | a **partial** constraint — only CONFIRMED/CHECKED_IN block. Must match `BLOCKING_STATUSES`. |

### "Isn't this write-heavy? Does it scan the table?"

**No scan.** And the reframe that settles it:

> **`UNIQUE` has exactly the same problem, and you never worried about it.**
> Inserting into `hotels` with `UNIQUE(email)` does not scan the table — `UNIQUE` is backed by a B-tree
> index and the check is a **lookup**. `EXCLUDE` is backed by a **GiST** index and the check is a lookup
> too. Same cost class.

**How an index answers "does anything overlap?"** GiST stores **bounding boxes** in a tree:

```
        ┌──────────────────────────────┐
        │ "everything below me lives    │
        │  between Jan 1 and Dec 31"    │
        └──────────┬───────────────────┘
           ┌───────┴────────┐
    ┌──────▼──────┐  ┌──────▼───────┐
    │ Jan 1–Jun 30│  │ Jul 1–Dec 31 │
    └─────────────┘  └──────┬───────┘
    looking for Sep 1–5? ───┘  descend ONLY here; prune the left half unread.
```

**What you do pay:** one more index to maintain per write (the same tax `UNIQUE` charges) and a brief
internal lock during the check.
**What you get back:** the index that *enforces* the rule is the same index that *answers* the
availability query — so it can replace a SELECT you were already doing. Often a net win.

**In Java:** a violation surfaces as Spring's `DataIntegrityViolationException`. One `@ExceptionHandler`
in `GlobalExceptionHandler` checks the constraint name → `409`.

### The production pattern it unlocks

For the `roomType` branch (assign a room from a pool), the classic answer is *pick one, lock it,
re-check, retry with the next.* But once you have the constraint, the cleaner version is:

```
candidates = findAvailableRooms(...)
for each room in candidates:
    try:   insert booking for that room  →  success, done
    catch constraint violation:  someone took it — try the next
throw 409 if none left
```

Same guarantee. **No locks, no deadlock risk, no lock ordering.** The database is the arbiter.

---

<a id="14"></a>
## 14. `SERIALIZABLE` isolation

**Plain English:** you tell Postgres *"pretend my transactions ran one after another, not overlapping."*
Postgres watches what each transaction reads and writes, and if two of them together produce a result no
sequential order could have produced, it **kills one**. You catch that error and run the request again.

**How:** Postgres tracks **predicate locks** — not on rows, but on **the conditions you searched by**
("bookings for room 5 between Sep 1–5"). That's how it catches phantoms: it knows what you *would* have
seen. This is **SSI** (Serializable Snapshot Isolation), and Postgres's implementation is world-class.

```java
@Transactional(isolation = Isolation.SERIALIZABLE)
public BookingResponse createBooking(...) { ... }
```

**The catch:**
- The failure is `SQLSTATE 40001` (`serialization_failure`), thrown **at commit time** — after all the work
- **A retry loop is mandatory.** Without it you've converted data corruption into a random 500. The retry
  must live **outside** the transaction (Spring Retry, or a manual loop in the controller)
- Real throughput cost under contention

**Verdict for this project:** understand it; don't build it. Building it means building retry
infrastructure for a demo app.

---

<a id="15"></a>
## 15. Booking lifecycle: State pattern vs enum transition table

**The problem.** Some moves through the lifecycle are nonsense:

```
   PENDING ──► CONFIRMED ──► CHECKED_IN ──► CHECKED_OUT
      │             │
      └──► CANCELLED ◄┘

   nonsense:  CHECKED_OUT ──► CANCELLED   (they already left)
              CANCELLED  ──► CHECKED_IN   (it was cancelled!)
```

**Right now nothing stops any of it.** `BookingService.cancelBooking` sets `CANCELLED` without checking
the current status — you can cancel a checked-out booking today.

**(a) The State pattern** — one class per state, each knowing what it permits:
```java
interface BookingState { BookingState cancel(); BookingState checkIn(); }

class CheckedOutState implements BookingState {
    public BookingState cancel()  { throw new IllegalTransitionException(); }
    public BookingState checkIn() { throw new IllegalTransitionException(); }
}
```
Five states → five classes.

**(b) A transition table on the enum** — the enum lists its own legal successors, and
`cancelBooking` asks `booking.getStatus().canTransitionTo(CANCELLED)` before acting.
**Same protection, ~15 lines instead of 5 classes.**

**The judgement call, which is the real lesson:**

> The State pattern earns its keep when each state has genuinely **different behaviour** — different
> pricing, notifications, validation. Here the states differ only in **what may come next**, which is
> **data**. Data belongs in a table, not a class hierarchy.

*"I know this pattern and here is why it's the wrong tool"* is worth more than implementing it.

---

<a id="16"></a>
## 16. Multi-tenancy: convention → framework → database

Writing `findByIdAndHotelId` everywhere is level 1 of three.

**Level 1 — Hibernate `@TenantId`** = *"Hibernate, this field is the tenant. Enforce it. Always."*
```java
@TenantId
private Long hotelId;
```
Plus a `CurrentTenantIdentifierResolver` bean reading the current tenant (from the JWT, once Security
lands). Hibernate then **automatically** appends `AND hotel_id = ?` to every query on that entity **and**
sets it on every insert. `findById(5)` becomes tenant-safe by itself. You cannot turn it off.

**Level 2 — `@FilterDef` / `@Filter`** = *"here's a reusable WHERE fragment; I'll tell you when to switch
it on."* Two annotations because it's declare-then-attach:
```java
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "hotelId", type = Long.class))
@Filter(name = "tenantFilter", condition = "hotel_id = :hotelId")
@Entity public class Booking { ... }
```
Then at runtime, usually once per request in a servlet filter:
```java
session.enableFilter("tenantFilter").setParameter("hotelId", currentHotelId);
```
Every query on `Booking` silently gains `AND hotel_id = 1`. Don't enable it → no filtering.

**⭐ Why you'd ever want it OFF — the PRD answers this.** `docs/PRD_V_01.md` defines a **Super Admin who is
not hotel-scoped**. With `@TenantId` that role is awkward — the filter is welded on. With `@Filter` you
simply don't enable it for them.

**Level 3 — Postgres Row-Level Security (RLS)** — the database itself refuses to return other tenants' rows:
```sql
ALTER TABLE hotel_bookings ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON hotel_bookings
  USING (hotel_id = current_setting('app.current_hotel')::bigint);
```
The app sets `SET LOCAL app.current_hotel = 1` at transaction start.

| | `@TenantId` | `@Filter` | RLS |
|---|---|---|---|
| Enforced by | Hibernate | Hibernate | **the database** |
| Bypassable | via raw SQL | if you forget to enable it | **no** |
| Sets tenant on INSERT | ✅ auto | ❌ you must | ✅ via policy |
| Super-Admin escape hatch | awkward | ✅ easy | via a role |

---

<a id="17"></a>
## 17. The one principle underneath all of it

Every topic in this document is the same ladder:

```
   CONVENTION            →   FRAMEWORK            →   DATABASE
   (politeness)              (enforced, but           (physics —
                              only inside the app)     nobody can bypass)

   findByIdAndHotelId    →   @TenantId / @Filter   →  Row-Level Security
   existsOverlapping     →   pessimistic lock      →  EXCLUDE constraint
   "remember to check"   →   enum transition table →  CHECK constraint
```

> **Correctness in the database. Ergonomics in the application. Never the reverse.**
>
> An app-only rule is a rule with holes in it. A database-only rule gives users incomprehensible
> errors. You want both, in that order.

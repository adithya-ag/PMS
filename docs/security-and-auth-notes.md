# Security & Authentication — Notes

> Written the way the questions actually came up: **the confusion first, then what made it click.**
> Sessions of 2026-08-28 → 2026-08-29, while building Spring Security + JWT.
> Companions: `docs/how-it-all-works.md` (Sections 5, 6) · `docs/00-the-complete-picture.md` (Section 8).

## Contents
1. [The map — what we are building](#1)
2. [What a servlet actually is](#2)
3. [`"any request for / goes to this object"`](#3)
4. [Tomcat vs HttpServletRequest vs DispatcherServlet](#4)
5. [An HTTP request is just text](#5)
6. [`Basic` vs `Bearer`](#6)
7. [The password still travels — HTTPS is what protects it](#7)
8. [Hashing, encryption, Base64, BCrypt — untangled](#8)
9. [How BCrypt verification actually works](#9)
10. [The JWT filter, traced step by step](#10)
11. [`SecurityContextHolder` and the thread's locker](#11)
12. [⭐ `username` is NOT `name`](#12)
13. [Why a `UserDetails` adapter instead of the entity](#13)
14. [One user table or two — what production does](#14)
15. [`jjwt` vs the Spring starter](#15)

---

<a id="1"></a>
## 1. The map — what we are building

**The whole feature, in one picture:**

```
  ONCE (login):
     user sends email + password
        → we look them up in the DATABASE
        → BCrypt-check the password
        → hand back a token                 ⭐ the ONLY time auth touches the DB

  EVERY REQUEST AFTER:
     browser sends the token
        → check its signature
        → we now know who they are          ⭐ NO database. The token IS the proof.
```

**Everything else is plumbing around those two flows.** Keeping them separate is the single most
important thing — most confusion comes from applying a fact about one flow to the other.

| Piece | What it does | Matters deeply? |
|---|---|---|
| `User` entity | mirrors `hotel_users` | ✅ hand-written |
| **`UserDetails` adapter** | translates YOUR user into SPRING's expected shape | ✅ hand-written |
| `UserDetailsService` | *"find the user with this login id"* — one method | ✅ ~10 lines |
| `SecurityFilterChain` | which URLs are public, which are locked | ✅ hand-written |
| Login endpoint | checks the password, issues the token | ✅ hand-written |
| **JWT filter** | verifies the token on every request | ✅ **the heart of it** |
| `PasswordEncoder` | BCrypt | ❌ one `@Bean` line |
| `AuthenticationManager` | internal plumbing | ❌ Spring provides it |

---

<a id="2"></a>
## 2. What a servlet actually is

**My question:** *"I don't understand what servlet means."*

**Plain English: a servlet is a Java object that handles web requests.** The word sounds exotic; the
idea isn't.

It is a **Java standard from 1997**. Before it, every web server had its own incompatible way of running
Java, so they agreed on one interface:

```java
public interface Servlet {
    void service(ServletRequest req, ServletResponse res);   // "here's a request, deal with it"
}
```

**That interface is a contract between two parties who know nothing about each other:**

- **Tomcat's side:** *"I handle sockets, TCP and parsing HTTP text. When a request arrives I call
  `service()` on whichever object is registered for that URL. I don't care what that object does."*
- **Your side:** *"I implement `service()`. I don't care how the bytes got here."*

**Tomcat has never heard of Spring.** It knows only the `Servlet` interface. `DispatcherServlet` is a
Spring-written class that implements it — and that single fact is the whole reason a Spring object can
sit inside a Tomcat socket. (Full version: `docs/how-it-all-works.md` → Section 5.1.)

---

<a id="3"></a>
## 3. `"any request for / goes to this object"`

**My question:** *"I don't understand this part."*

**`/` here does NOT mean the homepage.** In servlet mapping, `/` is the **catch-all pattern** — the root
and everything beneath it. The sentence means: **"route every single request to the DispatcherServlet."**

**Why it reads oddly is historical.** Old Java web apps registered many servlets, one per feature:

```
   /login    →  LoginServlet
   /users    →  UserServlet
   /orders   →  OrderServlet      ← a new servlet, and an XML entry, for every URL
```

Spring MVC threw that out and registered **one** servlet at `/` that catches everything and routes
internally:

```
   /  (everything)  →  DispatcherServlet  ──►  BookingController.createBooking()
                                          ──►  RoomController.list()
```

**That is what "front controller" means** — one door for the whole building, with a receptionist inside
directing people. It is also why adding `@PostMapping(...)` needs no registration anywhere.

---

<a id="4"></a>
## 4. Tomcat vs HttpServletRequest vs DispatcherServlet

**My assumption (nearly right):** *"HttpServletRequest helps parse the request into a Java object, and
DispatcherServlet routes it to the correct section?"*

**One correction: `HttpServletRequest` doesn't DO anything. It is the RESULT, not a worker.**

| | Job |
|---|---|
| **Tomcat** | text → Java object. The **translator**. |
| **`HttpServletRequest`** | the translated request. A **bag of getters**, no behaviour. |
| **`DispatcherServlet`** | reads that bag, picks the right controller method. The **traffic cop**. |

```
   ┌── TOMCAT ────────────────────────────────────────────────────┐
   │  raw text off the socket:                                    │
   │      GET /api/hotels/1/bookings HTTP/1.1                     │
   │      Authorization: Bearer eyJ...                            │
   │                                                              │
   │  PARSES it and builds an OBJECT:                             │
   │      HttpServletRequest {                                    │
   │          method  = "GET"                                     │
   │          path    = "/api/hotels/1/bookings"                  │
   │          headers = { "Authorization" → "Bearer eyJ..." }     │
   │      }                                                       │
   └────────────────────────┬─────────────────────────────────────┘
                            │  Tomcat looks up "/" → DispatcherServlet
                            ▼
   ┌── DispatcherServlet (Spring) ────────────────────────────────┐
   │  reads method + path, matches your @GetMapping annotations   │
   │  → calls BookingController.listBooking(1, pageable)          │
   └──────────────────────────────────────────────────────────────┘
```

**The security filter sits between the second and third boxes** — which is why it can read headers (the
object exists) but knows nothing about controllers (routing hasn't happened yet).

---

<a id="5"></a>
## 5. An HTTP request is just text

This is the foundation everything else sits on.

**What actually travels down the socket is plain text:**

```
GET /api/hotels/1/bookings HTTP/1.1
Host: localhost:8080
Accept: application/json
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiI3Ii...
User-Agent: PostmanRuntime/7.36.0

```

**Headers are just `Name: value` lines**, one per line, ending with a blank line.

**There is nothing special about `Authorization`.** It is a line of text with a name people agreed on.
You could invent `X-My-Token:` and it would work identically — you'd just be alone in the world.

So *"the filter reads the Authorization header"* literally means one map lookup:

```java
String value = request.getHeader("Authorization");
// "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiI3Ii..."
```

---

<a id="6"></a>
## 6. `Basic` vs `Bearer`

**My question:** *"Basic is base64 of user:password — isn't that easily decodable?"*

**Yes. Trivially. By anyone.**

```
   Authorization: Basic YWRpdGh5YTpodW50ZXIy
                        └───────┬──────────┘
                        base64-decode →  adithya:hunter2
```

> **Basic auth provides ZERO confidentiality on its own.** It sends the password, barely disguised, on
> **every single request**. It is only ever safe inside HTTPS.

**Bearer** means: *"whoever bears this token IS the user."* Like cash or a cinema ticket — the token
itself is the proof, and nobody checks who is holding it. **Which is exactly why a leaked token is a
leaked account**, and why tokens must be short-lived and HTTPS-only.

### Why the `"Bearer "` prefix exists (it is not decoration)

There is **one** `Authorization` header but **many** kinds of credential. The prefix tells the server how
to interpret what follows:

```
   Authorization: Basic  YWRpdGh5YTpodW50ZXIy      → "split on ':' — user and password"
   Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...   → "a token; verify its signature"
   Authorization: Digest username="...", nonce=...  → "challenge-response, parse these fields"
```

Without the scheme name the server would have to **guess** the format — and guessing about credentials
is how security holes happen. It also lets new schemes be added without inventing a new header.
**It is a type tag.** The filter strips those 7 characters once the type is confirmed.

---

<a id="7"></a>
## 7. The password still travels — HTTPS is what protects it

**My question:** *"without JWT the password travels on every login — so someone could steal it and
pretend to be me?"*

**Yes — and the part I missed: that is true WITH JWT too.** Login always sends the real password; there
is no other way to prove you know it the first time.

```
   WITHOUT JWT (Basic):  password sent on EVERY request   ← hundreds of chances to be stolen
   WITH    JWT:          password sent ONCE, at login     ← one chance, then a token that EXPIRES
```

So JWT **shrinks the exposure**. It does not protect the password in transit. **HTTPS does.**

```
   HTTP:    you ──► [router] ──► [ISP] ──► server
                       └── sees:  POST /api/auth/login
                                  {"email":"...","password":"hunter2"}     ☠️ plain text

   HTTPS:   you ──► [router] ──► [ISP] ──► server
                       └── sees:  17 03 03 00 45 a9 f2 c1 ...             ✅ encrypted bytes
```

**Where TLS sits in the stack:**

```
   Application   HTTP        your text, headers, JSON
   ─────────────────────────────────────────────────────
                 TLS    ⭐   encrypts everything above
   ─────────────────────────────────────────────────────
   Transport     TCP         reliable delivery
   Network       IP          routing between machines
```

TLS is a **wrapper between the application and transport layers**. HTTP doesn't know it's there; neither
does your Java code. That is why moving to HTTPS changes nothing in the app — you configure a
certificate and the same headers travel inside an encrypted tunnel.

**Three conclusions:**
1. **JWT is not transport security.** Without HTTPS, password *and* token are stolen equally. JWT solves
   statelessness and scale (`00-the-complete-picture.md` → Section 8.2); TLS solves eavesdropping.
2. **A stolen token is a stolen account** until it expires — hence short-lived tokens and refresh tokens.
3. This project runs on `localhost` over plain HTTP, which is fine for development (traffic never leaves
   the machine). **In production HTTPS is not optional** — worth one line in the README.

---

<a id="8"></a>
## 8. Hashing, encryption, Base64, BCrypt — untangled

**My confusion:** *"there is base64, then hash, then bcrypt — is that the order?"*

**They are not layers of each other. They are four separate things** that all happen to appear in this
project:

| | Reversible? | Needs a key? | For | Used here for |
|---|---|---|---|---|
| **Base64** | ✅ **by anyone** | ❌ | writing bytes as safe text | the JWT header + payload |
| **Encryption** | ✅ with the key | ✅ | keeping a secret you need back | *nowhere in this project* |
| **Hashing** | ❌ **never** | ❌ | proving you know something without storing it | — |
| **BCrypt** | ❌ never | ❌ | **hashing, specialised for passwords** | the `password_hash` column |
| **HMAC-SHA256** | ❌ never | ✅ | proving nobody tampered with a message | signing the JWT |

**The two separations that matter:**

> **Base64 is not security. It is spelling.** A way to write bytes using safe characters. Anyone decodes
> it instantly — which is why the JWT payload is readable.
>
> **Hashing is not encryption.** Encryption is a locked box; the right key opens it. Hashing is a
> **meat grinder** — put a cow in, get mince out. No key, and no way back. You can only grind another
> cow and see if the mince matches.

---

<a id="9"></a>
## 9. How BCrypt verification actually works

**My question:** *"if hashing gives a unique output, how do you ever compare the password again?"*

**The premise was backwards — and correcting it IS the answer. Hashing is deterministic: the same input
always produces the same output.**

```
   SIGN-UP:  hash("hunter2")       → "a3f9c2..."   → store this
   LOGIN:    hash("hunter2")       → "a3f9c2..."   → same? ✅
             hash("wrongpass")     → "7b1e44..."   → different? ❌
```

**Nothing is ever decrypted.** You hash what they typed and compare two strings.

### The problem that creates, and the salt

If hashing is deterministic, two users with the same password get **identical** stored values — which
leaks information, and lets an attacker precompute hashes for millions of common passwords once and
match the whole table at speed (a **rainbow table**).

**The fix is a salt:** random characters mixed in before hashing, different per user.

```
   adithya:  hash("Ku0Z1Xv" + "hunter2")  →  "8b2e77..."
   priya:    hash("9pQmT2a" + "hunter2")  →  "d41f03..."   ← same password, different output
```

### "But where is the salt kept?" ← the actual question

**Inside the stored string.** A BCrypt hash is four fields glued together with `$`:

```
   $2b$12$Ku0Z1XvJ8kZq3mYw7Pl9ceRT4uN2XjK8Lp0aBcDeFgHiJkLmNoPqR
   ─┬─ ─┬─ ──────┬─────────  ──────────────┬──────────────────
    │   │        │                          └── the actual hash
    │   │        └── the SALT (22 chars, random, made at sign-up)
    │   └── cost factor: 12 → 2^12 = 4096 rounds
    └── algorithm version
```

**So verification is just string handling:**

```
   1. load the stored value
   2. split on '$'  →  version, cost, salt
   3. re-run BCrypt on the submitted password with THAT salt and THAT cost
   4. compare with the stored hash
```

Determinism holds because the salt came out of the stored record. `passwordEncoder.matches(raw, stored)`
does all four steps.

### "Then can't an attacker do the same thing?"

**Yes. That IS the attack.** They have the salt and the cost — nothing is being withheld. What protects
you is not secrecy but **cost**:

```
   SHA-256 :  a GPU tests ~10,000,000,000 guesses/sec  →  common passwords fall in seconds
   BCrypt  :  a GPU tests ~     10,000    guesses/sec  →  the same list takes years
```

**For passwords you want a hash that is deliberately SLOW.** That `12` is tunable — each increment
doubles the work — and because it is stored per-hash you can raise it later and old passwords still
verify. *(Argon2 is the modern successor, also memory-hard. BCrypt is Spring Security's default and
entirely respectable.)*

> This is **Kerckhoffs's principle**: a system must stay secure even when everything about it is public
> except the key. For passwords there isn't even a key — so cost does the work.

---

<a id="10"></a>
## 10. The JWT filter, traced step by step

A **real** token (generated for these notes, secret `my-super-secret-key-...`):

```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiI3IiwiaG90ZWxJZCI6MSwicm9sZSI6Ik1BTkFHRVIiLCJleHAiOjE3ODcwMDAwMDB9.HNhb6zRcjBEIYjysD-6Py_GljdkLPV77GDHgyRRRZaE
└──────────── header ──────────────┘ └──────────────────── payload ──────────────────────────┘ └──────── signature ────────┘
```

Parts 1 and 2 are base64 of JSON. Decoding part 2 — **no key, no permission needed**:

```json
{"sub":"7","hotelId":1,"role":"MANAGER","exp":1787000000}
```

- `sub` — subject: user id 7
- `hotelId` — ⭐ the tenant, which is why every request can be scoped **without a DB hit**
- `role` — for `@PreAuthorize`
- `exp` — expiry, Unix timestamp

### What "validate the signature" means

When the token was made:

```
   signing_input = header_b64 + "." + payload_b64
   signature     = HMAC-SHA256( signing_input, SECRET_KEY )
```

On every later request the filter **does it again** and compares. **Proof it works** — changing
`hotelId` from 1 to 2 in that exact token:

```
   correct signature for hotelId=2 :  PVu7VoQIzGAMxl5BL7E8ipwD3QcH97AMSsZ9qJ1PI30
   signature the token carries     :  HNhb6zRcjBEIYjysD-6Py_GljdkLPV77GDHgyRRRZaE
                                      ────────── mismatch → rejected ──────────
```

An attacker can **read and edit** the payload freely. They cannot produce a matching signature without
`SECRET_KEY`.

> **The signature gives integrity, not secrecy.** Nothing is hidden. It is tamper-evident, like a wax
> seal — you can read the letter, you just can't reseal it convincingly.

### "Pulling out the claims" is not magic

Once the signature checks out, jjwt: base64-decodes the payload → parses the JSON → checks `exp` against
the clock. `claims.get("hotelId")` → `1`. **Decode, then parse.** That is the whole trick.

### The whole flow

```
   text on a socket
        ▼
   TOMCAT ─ parses text into HttpServletRequest
        ▼
   ┌─ YOUR JWT FILTER ────────────────────────────────────────────┐
   │  1. request.getHeader("Authorization")   → "Bearer eyJ..."    │
   │  2. starts with "Bearer "? strip it      → "eyJ..."           │
   │  3. jjwt: recompute HMAC, compare        → valid? expired?    │
   │  4. jjwt: base64-decode + parse payload  → {sub, hotelId,...} │
   │  5. build an Authentication object       → who + authorities  │
   │  6. SecurityContextHolder.setAuthentication(auth)  ← locker   │
   └──────────────────────────┬───────────────────────────────────┘
   AUTHORIZATION FILTER ──────┤  reads the locker. Empty → 401. Wrong role → 403.
                              ▼
   DispatcherServlet → BookingController → BookingService
                              └── @PreAuthorize, AuditorAware and your own code
                                  all read the SAME locker. Nothing was passed as a parameter.
```

**Three things worth carrying:**
1. **No database was touched** in steps 1–6. The token *is* the proof. That is statelessness.
2. **Every step is ordinary code** — a map lookup, a substring, a hash comparison, a JSON parse.
3. **The locker is why `AuditorAware` will just work.** It isn't told who the user is; it opens the same
   locker the filter filled.

---

<a id="11"></a>
## 11. `SecurityContextHolder` and the thread's locker

**A `ThreadLocal` is a variable where every thread gets its own private copy.** Same variable name,
different value per thread.

> **The gym-locker analogy.** Everyone says "put it in *my locker*." Same phrase, same wall of lockers —
> but each person opens a different door. Nobody carries a key number around; they just say "my locker".

```
   Tomcat thread-1  →  request from Adithya  →  its locker holds: Adithya, hotel 1, MANAGER
   Tomcat thread-2  →  request from Priya    →  its locker holds: Priya,  hotel 2, RECEPTIONIST
```

One Tomcat thread handles one request start to finish, so "the current user" can live in what looks like
a global variable and **still be safe**.

**What it buys:** you can ask *"who is logged in?"* from anywhere — a service, a helper, `AuditorAware` —
without threading a `currentUser` parameter through every method signature. The line is literally:

```java
SecurityContextHolder.getContext().setAuthentication(auth);
```

Same fact as `docs/how-it-all-works.md` → Section 6.12 (*beans are stateless, per-request data lives with
the thread*) — used deliberately instead of avoided.

---

<a id="12"></a>
## 12. ⭐ `username` is NOT `name`

**My confusion:** *"Spring wants a username, but my table has a `name` column — is that the problem?"*

**It is purely a vocabulary collision.**

| My column | Holds | Purpose |
|---|---|---|
| `name` | `"Adithya Anand Gondkar"` | **display** — what you print on a screen |
| `email` | `"adithya@rightturn.co.in"` | ⭐ **the thing you type to log in** |

Spring's `getUsername()` does **not** mean "the person's name". It means:

> **"the unique string this person types to identify themselves at login."**

Spring called it `username` in 2003 because everyone logged in with a username then. Today it is usually
an email. **Spring never renamed the method.**

```java
getUsername()  →  return user.getEmail();     ← correct
                  return user.getName();      ← wrong: names aren't unique, nobody types them to log in
```

**The `name` column is not involved in authentication at all.** Nothing is missing from the schema.

**Related trap I also hit:** *"how does Spring authenticate without touching the DB?"* — **login DOES
touch the database.** Only the *subsequent* JWT-verified requests don't. See Section 1: two flows, always
keep them separate.

---

<a id="13"></a>
## 13. Why a `UserDetails` adapter instead of the entity

Spring Security only understands an object with a specific shape:

```
   getUsername()     → a String (the login id)
   getPassword()     → the stored hash
   getAuthorities()  → the roles
   + isAccountNonExpired(), isAccountNonLocked(), isCredentialsNonExpired(), isEnabled()
```

Two ways to bridge that to a `User` entity:

| | Approach | |
|---|---|---|
| **(a)** | the entity itself `implements UserDetails` | fewer classes |
| **(b)** | ⭐ a small adapter class wraps the entity | **chosen** |

> **The card analogy.** (a) is making your passport double as your gym membership card — one object, two
> unrelated jobs, and the gym's rules now affect your passport. (b) is a separate gym card that says
> *"this belongs to passport #123."*

**The decisive reason:** look at those four booleans. `isAccountNonLocked()`, `isCredentialsNonExpired()`
— **these are Spring Security concepts that mean nothing to the database.** With (a), a JPA entity that
mirrors a table sprouts four methods that just `return true`, plus a `getUsername()` that returns an
email. The persistence layer starts carrying a security framework's vocabulary.

With (b), `User` stays a clean mirror of `hotel_users` and one small adapter speaks Spring's dialect.
Swap Spring Security out later and the entity never notices. **Cost: one extra file.**

**Related detail:** `hasRole('MANAGER')` looks for the literal authority string **`ROLE_MANAGER`**.
Convention is to add that `ROLE_` prefix **in the adapter**, not to store it in the database — the
database should hold the domain fact (`MANAGER`), not a framework's naming convention.

---

<a id="14"></a>
## 14. One user table or two — what production does

**What this schema has:**

```
  pms_users      id, name, email, phone, password_hash               ← platform admins, NO hotel_id
  hotel_users    id, hotel_id, name, email, phone, role, password_hash  ← staff
```

**The problem: two tables means two login paths.** The login endpoint would have to search table A, then
table B, then decide — two `UserDetailsService` branches, two chances to get it wrong, and an awkward
question about what happens if the same email exists in both.

**What production does — ONE identity table.** Authentication is one concern, so it lives in one place:

```
  users
    id, email (UNIQUE), password_hash, name, phone,
    role,                    -- SUPER_ADMIN | MANAGER | RECEPTIONIST
    hotel_id  NULL           -- ⭐ NULL = platform-level, not tied to any hotel
```

**The nullable `hotel_id` is the whole trick.** A Super Admin is simply a user whose `hotel_id` is null —
which maps exactly onto `concurrency-and-locking-notes.md` → Section 16, where a tenant filter you can
*switch off* is what a non-tenant-scoped admin needs.

Larger systems split further: `users` for *identity*, plus `roles` / `user_roles` tables for
*permissions*, because one person can hold several roles.

**Decision for this project (2026-08-29):** use **`hotel_users` only**, ignore `pms_users`. One login
path, learned cleanly. Adding the Super Admin later becomes a real exercise that makes the merged-table
argument obvious from the inside. **Note the simplification in the README** — *"a production schema would
use a single `users` table with a nullable `hotel_id`"* is a good thing for a reviewer to read.

---

<a id="15"></a>
## 15. `jjwt` vs the Spring starter

**Forget the names. The only difference is how much code you write.**

**With `jjwt`** — a small library that does two things: make a signed token, and check one.

```
   YOUR FILTER (~40 lines):
     1. read the Authorization header
     2. does it start with "Bearer "? if not, move on
     3. hand the token to jjwt: "is this signature valid?"     ← jjwt
     4. pull the claims out                                    ← jjwt
     5. build an Authentication object
     6. put it in the thread's locker
```

**With `spring-boot-starter-oauth2-resource-server`** — the filter already exists.

```
   YOUR CONFIG (~3 lines):
     "here is my signing key"
     "validate every request"
```

**"Resource server" is OAuth2 jargon** and the name is opaque. It means *the server that holds the
protected data*, as opposed to the **authorization server** that *issues* tokens. In a big system those
are two applications (Google issues, your API consumes). Here you are both, which is why it reads oddly.

> **Bread analogy:** `jjwt` sells you flour and yeast — you bake. The starter sells you the finished
> loaf. For dinner, buy the loaf. **To learn baking, use the flour.**

**Chosen: `jjwt` with a hand-written filter**, because the point is to have touched every step. Then read
about the starter and recognise every piece it replaced.

# How Your Backend Actually Works — OS → Spring Boot → Postgres

The big picture first, then each piece broken down. Skim Part 1 to get oriented; come back to
Part 2 sections as they show up in real code. You do **not** need to master all of this before we
build — it's a map, and building will make it stick.

## Contents

**Part 1 — The Big Picture**
1. [The stack: where your app lives](#1-the-stack-where-your-app-lives)
2. [The journey of one request](#2-the-journey-of-one-request)

**Part 2 — The Fragments**
3. [Processes, threads & the OS](#3-processes-threads--the-os)
4. [The network path: OSI, TCP, ports, sockets](#4-the-network-path-osi-tcp-ports-sockets)
5. [Tomcat & the DispatcherServlet](#5-tomcat--the-dispatcherservlet)
6. [IoC, DI & the bean container](#6-ioc-di--the-bean-container)
7. [Spring beans vs JavaBeans](#7-spring-beans-vs-javabeans)
8. [Annotations (with a reference list)](#8-annotations-with-a-reference-list)
9. [MVC, the "View", REST & JSON](#9-mvc-the-view-rest--json)
10. [JDBC, the driver, Hibernate & Spring Data JPA](#10-jdbc-the-driver-hibernate--spring-data-jpa)
11. [Why this all leads to the concurrency problem](#11-why-this-all-leads-to-the-concurrency-problem)

---

# Part 1 — The Big Picture

## 1. The stack: where your app lives

Everything runs in layers. From the metal up:

```
┌──────────────────────────────────────────────────────────┐
│  CLIENT   browser / Postman / mobile app / your tests      │
└──────────────────────────────────────────────────────────┘
                      │   HTTP request (over TCP/IP, the network)
                      ▼
╔══════════════════ YOUR COMPUTER / SERVER ═════════════════════╗
║  ┌──────────────────── OPERATING SYSTEM ───────────────────┐  ║
║  │  KERNEL — manages the hardware for everyone:             │  ║
║  │    CPU scheduling · memory · threads · files ·           │  ║
║  │    the network stack (TCP/IP)                            │  ║
║  │                                                          │  ║
║  │   ┌──── PROCESS: the JVM (your Spring Boot app) ─────┐   │  ║
║  │   │   • Embedded Tomcat  → a POOL of threads          │   │  ║
║  │   │   • DispatcherServlet → routes each request       │   │  ║
║  │   │   • Your BEANS:  Controller → Service → Repository │   │  ║
║  │   └───────────────────────────────────────────────────┘   │  ║
║  │                     │  SQL over JDBC (also TCP)            │  ║
║  │                     ▼                                      │  ║
║  │   ┌──── PROCESS: PostgreSQL (native, or in Docker) ──┐    │  ║
║  │   │   stores data · runs SQL · enforces constraints   │    │  ║
║  │   └───────────────────────────────────────────────────┘   │  ║
║  └──────────────────────────────────────────────────────────┘  ║
╚════════════════════════════════════════════════════════════════╝
```

Key takeaways:
- **Your app and Postgres are two separate *processes*** (two running programs), even on one machine.
- They talk to each other **over the network** (TCP), just like the client talks to your app — even
  when everything is on `localhost`.
- **Nobody touches the hardware directly.** Every program asks the **kernel** for CPU, memory, files,
  and network access.

## 2. The journey of one request

Follow a single "create a booking" request end-to-end. This flow *is* the whole system:

```
 CLIENT                          YOUR APP (JVM process)                 POSTGRES
   │                                                                       │
   │ 1. HTTP POST /bookings  ──────►  2. Kernel receives bytes on the      │
   │    { guestId, roomId,            port, hands them to the process       │
   │      dates } as JSON             listening there (Tomcat)              │
   │                                                                       │
   │                         3. Tomcat parses raw bytes → HttpServletRequest│
   │                            and picks a THREAD from its pool            │
   │                                                                       │
   │                         4. DispatcherServlet matches POST + /bookings  │
   │                            to your BookingController method            │
   │                                                                       │
   │                         5. Jackson turns the JSON body → a Java object │
   │                                                                       │
   │                         6. Controller → Service → Repository (beans)   │
   │                                                                       │
   │                         7. Repository → Hibernate builds SQL →         │
   │                            JDBC driver sends it  ─────────────────────►│ 8. runs SQL,
   │                                                                        │    enforces
   │                         9. rows come back ◄────────────────────────────│    constraints,
   │                            Hibernate maps rows → Java objects           │    returns rows
   │                                                                       │
   │                        10. Controller returns a Java object;           │
   │                            Jackson turns it → JSON                     │
   │ 11. HTTP 201 + JSON  ◄──────────  Tomcat writes bytes back over TCP    │
   ▼                                                                       │
```

Every numbered step is a "fragment" below. Now the details.

---

# Part 2 — The Fragments

## 3. Processes, threads & the OS

- **Process** = a running program with its own isolated memory. Your Spring app is **one** process
  (one JVM). Postgres is **another** process.
- **Thread** = a single line of execution *inside* a process. One process can run **many threads at
  once** (across CPU cores, or time-sliced by the kernel).
- **The kernel** is the core of the OS. It:
  - schedules which thread runs on which CPU core, and when,
  - hands out memory,
  - owns the files and the **network stack**.
- A program can't touch hardware directly — it asks the kernel through **system calls** ("send these
  bytes on the network," "read this file").

**Why this matters for you:** Tomcat keeps a **pool of threads**. Ten simultaneous requests →
ten threads running your booking code **at the same time**. That's the seed of the whole concurrency
problem (section 11).

## 4. The network path: OSI, TCP, ports, sockets

When the client sends a request, the data passes through layers (the OSI/TCP model, simplified):

```
  Your JSON data           ← what you care about
  ── HTTP ──               application layer: method, URL, headers, body
  ── TCP ──                transport layer: reliable stream + PORT numbers (e.g. 8080, 5432)
  ── IP  ──                network layer: addresses + routing between machines
  ── Ethernet/Wi-Fi ──     physical/link layer: actual bits on the wire
```

- **Port** = a numbered "door" on a machine so the OS knows *which program* the data is for
  (Tomcat listens on 8080; Postgres on 5432).
- **Socket** = the endpoint the OS gives a program to send/receive network data. Tomcat "opens a
  socket" and listens on its port; the kernel routes incoming bytes for that port to it.
- So "localhost:5432" = *this machine*, door *5432* → whatever program is listening there.

## 5. Tomcat & the DispatcherServlet

Two distinct things, working together:

- **Tomcat** = a **web server / servlet container**. It:
  - owns the socket and listens on the port,
  - accepts TCP connections and **parses raw HTTP bytes → a Java `HttpServletRequest`** (method, URL,
    headers, body),
  - runs the **thread pool** (one thread per request),
  - calls your app, then writes your response back as HTTP bytes.
  - **"Embedded"** = it lives *inside* your app's JAR — no separate server to install.
- **DispatcherServlet** = Spring MVC's **"front controller"** — a single servlet that receives **every**
  request from Tomcat and **dispatches** each to the correct handler (your controller method), based on
  the URL + HTTP method.

```
  Tomcat (bytes → HttpServletRequest, on a thread)
        │
        ▼
  DispatcherServlet  ── "POST /bookings? → BookingController.create()"
        │
        ▼
  Your @RestController method
```

So Tomcat is the **bridge between the network and Java**; DispatcherServlet is the **traffic cop**
inside Spring that sends each request to the right method.

## 6. IoC, DI & the bean container

### 6.1 The big picture
Any real app is a **web of objects that need each other**. To create a booking, a *controller* needs a
*service*, which needs a *repository*, which needs a *database connection*. The one question this whole
section answers: **who creates all these objects and wires them together?**
- **Plain Java:** *you* do — by hand, with `new`, everywhere.
- **Spring:** the *framework* does it for you. Handing that job over is **IoC**; the way it's done is **DI**.

### 6.2 First — what is a "dependency"? (the word you didn't get)
- A **dependency** is simply **another object that an object needs in order to do its job.**
- **"A depends on B" = "A can't do its work without B."**
- Example: `BookingService` needs to *save* a booking, but it doesn't know how to talk to the database —
  it delegates that to `BookingRepository`. So **`BookingRepository` is a dependency of `BookingService`.**
- Our chain of dependencies:
```
  BookingController  ─depends on→  BookingService  ─depends on→  BookingRepository  ─depends on→  DB connection
```
Each one **needs the next** to do its job. "Dependency" is just that relationship.

### 6.3 Object vs bean vs dependency (three words, cleanly)
| Word | Meaning | Example |
|---|---|---|
| **Object** | *any* instance of a class | a `Booking`, a `BookingService` |
| **Bean** | an object that **Spring creates & manages** (a shared "worker") | `BookingService`, `BookingController` |
| **Dependency** | an object that **another object needs** to function | `BookingRepository` (needed by `BookingService`) |

Note: "dependency" isn't a *new kind* of object — it's a **role**. `BookingRepository` is a *bean*, and
it is *also* a *dependency* of the service. Same object, described from two angles.

### 6.4 IoC vs DI — the difference (this is what trips everyone)
- **IoC — Inversion of Control — the *principle*.** *Who is in control of creating & wiring objects?*
  Normally **your** code is (you call `new`). With IoC you **invert** that control and give it to the
  framework. It's the **what / why**.
- **DI — Dependency Injection — the *technique*.** The specific way Spring *achieves* IoC: instead of an
  object building its own dependencies, Spring **injects** them from outside, usually through the
  **constructor**. It's the **how**.
- **One line:** *IoC is the policy ("the framework is in charge of wiring"); DI is the mechanism ("hand
  each object its dependencies through the constructor").*

### 6.5 The example — by hand vs with Spring
**Plain Java** — *you* build and wire the whole chain:
```java
DataSource ds          = new DataSource(...);
BookingRepository repo  = new BookingRepository(ds);
BookingService service  = new BookingService(repo);
BookingController ctrl  = new BookingController(service);
```
Tedious, and every class is hard-wired to exact constructions.

**With Spring** — each class just *declares what it needs* (a constructor parameter), and Spring supplies it:
```java
@Service                                   // "I am a bean — Spring, manage me"
class BookingService {
    private final BookingRepository repo;
    BookingService(BookingRepository repo) {   // "I need a repository to work"
        this.repo = repo;                       // Spring INJECTS one here
    }
}
```
You never call `new BookingService(...)`. At startup Spring sees `@Service`, creates the bean, and
injects a `BookingRepository` bean into it — automatically. **That injection is DI; the fact that Spring
(not you) is doing the creating is IoC.**

### 6.6 The container
- Spring's **`ApplicationContext`** is the "box" that holds all the beans.
- At startup Spring:
  1. **scans** for component classes (`@Service`, `@Repository`, `@RestController`, `@Component`),
  2. creates **one instance of each** → a **bean**,
  3. **injects** each bean's declared dependencies.
```
   Spring container (built once at startup)
   ┌───────────────────────────────────────────────┐
   │  BookingController  ─needs→  BookingService     │
   │  BookingService     ─needs→  BookingRepository  │
   │  BookingRepository  ─needs→  DataSource         │
   └───────────────────────────────────────────────┘
```

### 6.7 Beans are STATELESS shared workers (the part that confused you)
The key mental correction — there are **two kinds of objects**:
- **Beans** (`BookingService`, `BookingController`) — created **once**, **shared** by everyone
  ("singleton"). They hold **behavior + their dependencies — NOT any user's data.**
- **Data objects** (`Booking`, `Hotel`, `Guest`) — created **fresh, one per request/person** — these
  carry the actual data and **flow *through*** the shared beans.
```
                 ┌───────────────────────────┐
  Alice's data ─►│                           │─► Alice's result
  Bob's data   ─►│   ONE BookingService bean │─► Bob's result
  Cara's data  ─►│   (shared, stateless)     │─► Cara's result
                 └───────────────────────────┘
     many per-request DATA objects flow through one shared BEAN
```
- **Why it's safe with many requests at once:** the bean holds no per-request data. Each request's data
  lives in **method parameters / local variables** on **each thread's own private stack**, so two threads
  running the same bean method never clobber each other.
- This is *exactly why* beans are kept **stateless** — and a direct preview of the concurrency deep-dive:
  putting shared *mutable* data inside a bean would be a real bug.

### 6.8 Why all this is worth it (payoff)
- **Loose coupling** — a class asks for *what* it needs, not *how* to build it.
- **Easy testing** — inject a *fake* `BookingRepository` in a test.
- **No manual wiring** — no giant `new` chains.
- **Safe sharing** — one stateless bean serves every request.

**One analogy to hold it all — a restaurant:**
- **IoC** = the *manager* (framework), not the cooks, controls how stations get stocked.
- **DI** = before service, each *station* (bean) is stocked with exactly the *ingredients* (dependencies) it declared it needs.
- **Beans** = the *cooks / stations* — shared, reused all night.
- **Data objects** = the individual *orders* flowing through — one per customer.

## 7. Spring beans vs JavaBeans

The word "bean" means two different things — this confuses everyone:

| Term | What it is | Example in our project |
|---|---|---|
| **Spring bean** | any object the Spring **container** creates & manages | your `BookingService`, `BookingController` |
| **JavaBean** | an old Java *convention*: a plain class with private fields + getters/setters + a no-arg constructor | your `Booking` entity / DTO data classes |

They overlap in name only. "Bean **Validation**" (section 8) uses the **JavaBean** sense.

## 8. Annotations (with a reference list)

- An **annotation** is **metadata** you attach to code with `@Something`. It's a plain Java language
  feature (`@Override` is core Java).
- By itself an annotation *does nothing* — it's a **marker**. A **framework reads it** (via reflection,
  at startup or runtime) and *acts* on it. So Spring/JPA/Validation each define their **own** annotations
  and give them meaning.

The ones you'll actually meet, grouped:

| Group | Annotations | Meaning |
|---|---|---|
| **DI / components** | `@Component` `@Service` `@Repository` `@RestController` `@Configuration` `@Bean` | "make this a Spring bean" (each word hints at its role/layer) |
| **Injection** | constructor injection (preferred) · `@Autowired` | "inject the dependency here" |
| **Web / MVC** | `@GetMapping` `@PostMapping` `@PutMapping` `@DeleteMapping` `@PathVariable` `@RequestParam` `@RequestBody` | map URLs+verbs to methods; pull values out of the request |
| **Persistence (JPA)** | `@Entity` `@Table` `@Id` `@GeneratedValue` `@Column` `@ManyToOne` `@OneToMany` `@JoinColumn` | map a class ↔ a table, a field ↔ a column, relationships ↔ foreign keys |
| **Validation** | `@NotNull` `@NotBlank` `@Size` `@Email` `@Min` `@Max` `@Valid` | rules checked on incoming data |
| **Boot** | `@SpringBootApplication` | turns on component-scanning + auto-configuration |

You don't memorize these — you meet them as we write each layer, and this table is your lookup.

## 9. MVC, the "View", REST & JSON

**Two styles of web app:**

- **Traditional MVC (server-rendered):** the server builds the **View** = a finished **HTML page** and
  sends it to the browser. The "V" is real, and it lives on the server.
- **REST API (what we build):** the server sends **data (JSON)**, *not* a page. The **client** (a
  browser JS app, a mobile app) renders the UI itself. So there is **"no server-side view"** — the
  server's job ends at producing JSON; the *client* owns the View.
  → That's why I said "the JSON response effectively takes the place of the View."

**JSON travels BOTH ways** (this was your question):

```
  Client ──(request body JSON: the new booking's data)──►  Server     ← INbound
  Client ◄─(response body JSON: the created booking)─────  Server     ← OUTbound
```
- Inbound: e.g. `POST /bookings` with `{ "guestId": 3, "roomId": 12, ... }` in the body.
- Outbound: the server replies `{ "id": 55, "status": "CONFIRMED", ... }`.
- **Jackson** (bundled in starter-web) does both conversions automatically: JSON → Java object on the
  way in, Java object → JSON on the way out.

**REST endpoint** = **HTTP method + URL** = one callable operation: `GET /hotels/1`, `POST /bookings`,
`DELETE /rooms/5`. Resources are **nouns** (URLs); verbs are the **actions**.

## 10. JDBC, the driver, Hibernate & Spring Data JPA

Four layers, each removing more manual work. Bottom to top:

```
  Your code
     │   calls simple methods (save, findById)
     ▼
  Spring Data JPA     ← auto-writes the repository implementation for you
     │
     ▼
  JPA / Hibernate     ← ORM: maps @Entity objects ↔ table rows, GENERATES the SQL
     │
     ▼
  JDBC (Java standard) ← the common API for "talk to a relational database"
     │
     ▼
  PostgreSQL driver    ← implements JDBC for Postgres's specific wire protocol
     │   (sends SQL over TCP :5432)
     ▼
  PostgreSQL
```

- **JDBC** = **Java Database Connectivity** — a *standard set of Java interfaces* for "open a connection,
  send SQL, read results," the **same** regardless of database. But it's just interfaces.
- **The driver** = the **implementation** of those interfaces for one specific database. PostgreSQL's
  driver (published by the Postgres project, **not** Spring) knows Postgres's network protocol. MySQL has
  its own driver. *Analogy:* JDBC is a universal socket standard; the driver is the plug adapter for your
  specific database.
- **Hibernate (an ORM = Object-Relational Mapper):** you annotate a class `@Entity`; Hibernate maps it to
  a table and **writes the SQL for you**, so you work with objects, not `ResultSet` rows. **JPA** is the
  *specification*; **Hibernate** is the common *implementation*.
- **Spring Data JPA:** you declare a repository **interface**
  (`interface HotelRepository extends JpaRepository<Hotel, Long>`) and Spring **generates the code** —
  `save`, `findById`, `findAll`, `delete` for free, plus queries from method names (`findByCity(...)`).

**Without Spring** you'd hand-write level "JDBC": open a connection, write SQL strings, loop the
`ResultSet`, copy columns into fields by hand. It works — it's just verbose and repetitive.

## 11. Why this all leads to the concurrency problem

Now everything connects to the centerpiece of this project:

- Tomcat runs **many threads at once** (section 3).
- So **two "book room 12 for the same dates" requests can hit your booking code simultaneously**, on two
  threads, in the same instant.
- Each thread reads "room 12 looks free," and each proceeds to book it → **double-booking** — the exact
  bug we swore never happens.
- Fixing it lives across the stack you now understand:
  - **transactions** (all-or-nothing units of work),
  - **locking** (pessimistic/optimistic — making one thread wait for the other),
  - **database constraints** (the Postgres `EXCLUDE` constraint — the last, unbreakable line).

That's why we spent so long on the fundamentals: **the concurrency deep-dive is this whole picture under
stress.** When we get there, you won't be memorizing tricks — you'll be reasoning about threads, a
process, and a database you actually understand.

# The Complete Picture — Hotel PMS

**Start here. This is the map; the other docs are the territory.**

---

## Why this file exists

You now have four documents, and tracing an idea across them is hard. This file fixes that. It does
two jobs:

1. **Top-down narrative** — one diagram containing *everything*, then progressively zoomed views of
   each part. Read it front to back and you'll have the whole system in your head.
2. **Index to the detail** — every section ends with *"full detail in: `file` → Section N"*, so you
   always know exactly where to go deeper.

**Nothing here is new material.** It's the same system, told once, from the top.


### The problem we are ultimately solving

Everything in these docs exists to make **one sentence** true:

> **A hotel room can never be booked twice for overlapping dates — even when two requests arrive in
> the same millisecond.**

That sentence is why we care about threads, transactions, locking, and database constraints. Every
layer below is either *serving* that guarantee or *threatening* it.

---

## The document map

| File | Holds | Read it when |
|---|---|---|
| **`docs/00-the-complete-picture.md`** *(this file)* | The whole system, top-down, with the index | You're lost, or starting a session |
| `docs/how-it-all-works.md` | **The runtime concepts** — threads, sockets, Tomcat, IoC/DI/beans, JDBC/Hibernate/JPA | You want to understand *how something works* |
| `docs/spring-boot-setup-and-anatomy.md` | **The project & tooling** — the generated files, naming, Maven, `pom.xml`, `application.properties`, modularization | You want to know *what a file is or what to put in it* |
| `docs/spring-and-maven-notes.md` | First-pass primer — JDK, Spring vs Boot, the Initializr CLI command | Quick refresher on the basics |
| `docs/HLD.md` | Architecture & technology rationale | Design-level "why this stack" |
| `docs/db/schema.sql` | The DDL — **the source of truth for the schema** | Anything about tables |
| `docs/ERD_V_01.md`, `docs/PRD_V_01.md` | The data model and the requirements | "What are we building?" |
| `docs/docker-notes.md` | Docker & the `pms-postgres` container | Container questions |
| `docs/NFR-notes.md` | Why indexing/caching/queues exist | Performance awareness |

---

## Contents

**Level 0 — Everything at once**
- [0.1 The master diagram](#01-the-master-diagram)
- [0.2 The four lifetimes](#02-the-four-lifetimes)

**Level 1 — The four phases, zoomed**
- [1. BUILD TIME — source becomes a JAR](#1-build-time--source-becomes-a-jar)
- [2. STARTUP TIME — a JAR becomes a wired, running application](#2-startup-time--a-jar-becomes-a-wired-running-application)
- [3. REQUEST TIME — HTTP becomes JSON](#3-request-time--http-becomes-json)
- [4. DATA TIME — a Java object becomes a row](#4-data-time--a-java-object-becomes-a-row)

**Level 2 — Reference**
- [5. The cast of characters](#5-the-cast-of-characters)
- [6. The six "contract vs implementation" pairs](#6-the-six-contract-vs-implementation-pairs)
- [7. Where the concurrency problem lives](#7-where-the-concurrency-problem-lives)

- [8. State, statelessness and scale](#8-state-statelessness-and-scale)
- [9. Postgres mechanics that constrain your app](#9-postgres-mechanics-that-constrain-your-app)

**Level 3 — Forward**
- [10. The roadmap](#10-the-roadmap)

---

# Level 0 — Everything at once

## 0.1 The master diagram

Everything in this project, on one page. Nothing is omitted — every box is explained later.

```
╔══════════════════════════════════════════════════════════════════════════════════════════════╗
║ ①  BUILD TIME — happens on your laptop / on CI. The app is not running.                      ║
╟──────────────────────────────────────────────────────────────────────────────────────────────╢
║                                                                                              ║
║   YOU WRITE                    MAVEN reads pom.xml                    OUTPUT                 ║
║   ┌──────────────────┐         ┌────────────────────────┐            ┌────────────────────┐  ║
║   │ src/main/java/   │         │ • resolve dependencies │            │ target/            │  ║
║   │   *.java         │────────►│   from ~/.m2 & Central │───────────►│   classes/*.class  │  ║
║   │ src/main/        │         │ • javac  → .class      │            │   hotel-pms.jar    │  ║
║   │   resources/     │         │ • copy resources       │            │   └ FAT JAR:       │  ║
║   │   application.   │         │ • run tests            │            │     your code +    │  ║
║   │   properties     │         │ • package + MANIFEST   │            │     ~50 libs +     │  ║
║   │ pom.xml          │         └────────────────────────┘            │     Tomcat         │  ║
║   └──────────────────┘                                               └────────────────────┘  ║
║                                                                               │              ║
╚═══════════════════════════════════════════════════════════════════════════════│══════════════╝
                                                                                │ java -jar
╔═══════════════════════════════════════════════════════════════════════════════▼══════════════╗
║ ②  STARTUP TIME — one JVM process starts. Takes ~3 seconds. Happens ONCE.                    ║
╟──────────────────────────────────────────────────────────────────────────────────────────────╢
║   main() → SpringApplication.run()                                                           ║
║      │                                                                                       ║
║      1. read application.properties        (url, ddl-auto, logging…)                         ║
║      2. COMPONENT SCAN under com.pms.hotel → find @RestController @Service @Repository        ║
║      3. AUTO-CONFIGURATION → "JDBC driver + url present? → define a DataSource"               ║
║      4. CREATE + WIRE every bean  ────────────────────────────┐                               ║
║      5. start TOMCAT, register DispatcherServlet              │                               ║
║      6. bind port 8080 → now accepting requests               │                               ║
║                                                               ▼                               ║
║   ┌──────────────── SPRING ApplicationContext (the bean container) ────────────────────────┐  ║
║   │                                                                                        │  ║
║   │   DispatcherServlet                                                                    │  ║
║   │        │                                                                               │  ║
║   │        ▼          ┌──────────────┐      ┌──────────────┐      ┌───────────────────┐    │  ║
║   │   BookingController ──depends──► BookingService ──────► BookingRepository          │    │  ║
║   │   (HTTP ↔ Java)   │              │(business rules│      │(generated by Spring    │    │  ║
║   │                   │              │ @Transactional│      │ Data — an interface!)  │    │  ║
║   │                   └──────────────┘      └───────┬──────┘      └────────┬──────────┘    │  ║
║   │                                                 │                      │               │  ║
║   │                                          EntityManager ────────► HIBERNATE (ORM)       │  ║
║   │                                                                        │               │  ║
║   │                                                                  DataSource            │  ║
║   │                                                                  (HikariCP POOL)       │  ║
║   └────────────────────────────────────────────────────────────────────────┼──────────────┘  ║
╚═══════════════════════════════════════════════════════════════════════════ │ ════════════════╝
                                                                             │
╔════════════════════════════════════════════════════════════════════════════│═════════════════╗
║ ③  REQUEST TIME — happens thousands of times, CONCURRENTLY, on many threads│                 ║
╟────────────────────────────────────────────────────────────────────────────│─────────────────╢
║                                                                            │                 ║
║  CLIENT            OS KERNEL         TOMCAT          YOUR BEANS            │                 ║
║  ┌──────┐   TCP    ┌─────────┐      ┌────────┐      ┌──────────────┐       │                 ║
║  │Postman│════════►│ port    │─────►│ thread │─────►│ Controller   │       │                 ║
║  │browser│  HTTP   │ 8080    │      │ pool   │      │  ↓ Jackson   │       │                 ║
║  │       │         │ socket  │      │(1 thread│     │ Service      │       │                 ║
║  │       │◄════════│         │◄─────│ per req)│◄────│  ↓           │       │                 ║
║  └──────┘  JSON    └─────────┘      └────────┘      │ Repository   │───────┤                 ║
║                                                     └──────────────┘       │                 ║
║   ⚠️ MANY THREADS RUN THE SAME BEANS AT THE SAME TIME → this is where       │                ║
║      double-booking becomes possible. See Section 7.                       │                 ║
╚════════════════════════════════════════════════════════════════════════════│═════════════════╝
                                                                             │
╔════════════════════════════════════════════════════════════════════════════▼═════════════════╗
║ ④  DATA TIME — Java objects become rows                                                      ║
╟──────────────────────────────────────────────────────────────────────────────────────────────╢
║                                                                                              ║
║   repo.save(booking)                                                                         ║
║        │                                                                                     ║
║   SPRING DATA JPA   generated impl: "id is null → persist"                                   ║
║        │                                                                                     ║
║   HIBERNATE (JPA impl)  reads @Entity → builds:  insert into hotel_bookings (…) values (?,?)  ║
║        │                persistence context · dirty checking · lazy loading                  ║
║        │                                                                                     ║
║   JDBC  (java.sql interfaces — a CONTRACT, no implementation)                                 ║
║        │                ps.setLong(1, 7)                                                     ║
║        │                                                                                     ║
║   POSTGRESQL DRIVER  (the IMPLEMENTATION) — translates to Postgres wire protocol             ║
║        │                'P' Parse  'B' Bind  'E' Execute  'S' Sync                           ║
║        │                                                                                     ║
║   ═════╪═══════ TCP to localhost:5433 ══════════════════════════════════════════              ║
║        ▼                                                                                     ║
║   ┌────────────────── POSTGRESQL (a SEPARATE PROCESS, in Docker) ────────────────┐            ║
║   │  hotel_pms database · public schema                                          │            ║
║   │  hotels · hotel_rooms · hotel_guests · hotel_bookings · payments · …          │            ║
║   │  ENFORCES: PK, FK, UNIQUE, CHECK (check_out > check_in),                      │            ║
║   │            and later → EXCLUDE (no overlapping bookings)  ◄── the guarantee   │            ║
║   │  schema.sql is the SOURCE OF TRUTH. Hibernate only validates against it.      │            ║
║   └──────────────────────────────────────────────────────────────────────────────┘            ║
╚══════════════════════════════════════════════════════════════════════════════════════════════╝
```

## 0.2 The four lifetimes

The single most useful idea for keeping all of this straight: **different things happen at different
times, and confusion almost always comes from mixing two of them up.**

| | ① BUILD | ② STARTUP | ③ REQUEST | ④ DATA |
|---|---|---|---|---|
| **How often** | When you run `mvnw` | **Once** per app start | **Thousands** of times | Per DB operation |
| **How long** | seconds–minutes | ~3 seconds | milliseconds | microseconds–ms |
| **Who's in charge** | **Maven** | **Spring** | **Tomcat + your beans** | **Hibernate + the driver** |
| **Is the app running?** | ❌ No | Becoming | ✅ Yes | ✅ Yes |
| **Threads** | 1 (the build) | 1 (main) | **Many, at once** | One per request |
| **Key file** | `pom.xml` | `application.properties` | your controllers | your `@Entity` + `schema.sql` |
| **Typical failure** | "cannot find symbol" | "Failed to configure a DataSource" | HTTP 500 | constraint violation |
| **Concurrency risk** | none | none | ⚠️ **HERE** | ⚠️ **and HERE** |

**Read that last row.** Build and startup are single-threaded and calm. Everything dangerous in this
project lives in columns ③ and ④.

---

# Level 1 — The four phases, zoomed

## 1. BUILD TIME — source becomes a JAR

### The problem being solved

You have `.java` files. The JVM can't run those — it runs `.class` bytecode. And your code depends on
~50 libraries you didn't write. Somebody must fetch them all, compile in the right order, run the
tests, and bundle the result. **That's a build tool. Ours is Maven.**

### The flow

```
   pom.xml  ─── declares ───►  4 dependencies (no version numbers!)
      │                              │
      │                        inherits versions from
      │                        spring-boot-starter-parent 4.1.0
      │                              │
      │                        resolves TRANSITIVELY → ~50 JARs
      │                              │
      │                        downloads to  C:\Users\adith\.m2\repository\
      ▼
   ./mvnw  ──►  validate → compile → test → package
                    │         │        │       │
                    │         │        │       └─ maven-jar-plugin: zip it + write MANIFEST.MF
                    │         │        │          spring-boot-maven-plugin: REPACKAGE as fat JAR
                    │         │        └─ surefire-plugin: run JUnit
                    │         └─ compiler-plugin: javac → target/classes/
                    └─ sanity check
                                                          ▼
                                        target/hotel-pms-0.0.1-SNAPSHOT.jar
```

### The points

- **`pom.xml` is the whole build.** Identity (GAV), Java version, dependencies, plugins.
- **Maven itself does almost nothing** — **plugins** do the work. Compiling, testing, packaging are
  all plugins, pre-configured by the parent POM.
- **Phase vs goal:** a *phase* is a slot in time (`compile`); a *goal* is actual work a plugin does
  (`compiler:compile`); *binding* puts a goal into a phase. Running a phase runs every phase before
  it. Running a bare goal (`spring-boot:run`) runs no lifecycle at all.
- **The fat JAR** is your code + all 50 dependencies + **embedded Tomcat**, in one file. The
  `MANIFEST.MF` inside tells the JVM which class has `main()`.
- **`./mvnw`, never `mvn`** — the wrapper pins Maven 3.9.16 so every machine builds identically.
- **`target/` is git-ignored but IS what runs in production.** Git holds source; CI regenerates the
  JAR. Git-ignored ≠ not deployed.

> **Full detail in:** `docs/spring-boot-setup-and-anatomy.md` → Sections 14–25
> (build tool, lifecycle, `~/.m2`, wrapper, reading `pom.xml`, parent POM, scopes, plugins)

## 2. STARTUP TIME — a JAR becomes a wired, running application

### The problem being solved

Your app is a **web of objects that need each other**: a controller needs a service, which needs a
repository, which needs a database connection, which needs a URL and password from a config file.
**Who creates all of that, in what order, and hands each object what it needs?**

Doing it by hand means every class knows how to build everything beneath it. **Spring's container does
it instead.** That's IoC (the principle: the framework is in control) achieved via DI (the mechanism:
dependencies are handed in through constructors).

### The sequence — in actual order

```
   1. JVM starts, calls main()
   2. SpringApplication.run() creates an empty ApplicationContext
   3. reads application.properties          ← config first: everything below depends on it
   4. COMPONENT SCAN under com.pms.hotel    ← finds your classes → bean DEFINITIONS (recipes)
   5. AUTO-CONFIGURATION                    ← ~150 conditional questions about the classpath
   6. INSTANTIATE + WIRE every bean         ← resolves the dependency graph
        • DataSource created → HikariCP opens connections to Postgres
        • Hibernate boots, reads your @Entity classes, runs ddl-auto=validate
        • repository implementations GENERATED, services built, controllers built
   7. TOMCAT created and started; DispatcherServlet registered into it
   8. port 8080 bound → NOW requests are accepted
   9. "Started HotelPmsApplication in 3.421 seconds"
```

**Two orderings people get wrong:**
- ❌ "Tomcat starts, then Spring" — **no**: the context is built first; Tomcat is one of the last beans.
- ❌ "The app accepts requests while starting" — **no**: the port opens only after everything is proven.

### How the wiring actually resolves

**Nobody writes down the chain.** Each class declares only its own immediate need:

```java
@RestController
class BookingController {
    private final BookingService service;                 // "I need a service." That's all it knows.
    BookingController(BookingService service) { … }       // ← the word "repository" never appears
}

@Service
class BookingService {
    private final BookingRepository repo;                 // "I need a repository."
    BookingService(BookingRepository repo) { … }
}

interface BookingRepository extends JpaRepository<Booking, Long> { }
```

Spring walks it one hop at a time:

```
   "Need a BookingController → its constructor wants a BookingService"
      → "Need a BookingService → its constructor wants a BookingRepository"
         → "BookingRepository is an INTERFACE — I'll generate the implementation"
         ← hand it up
      ← hand it up
   ← done
```

**The chain is emergent, not declared.**

### The points

- **`@SpringBootApplication` = three annotations:** `@ComponentScan` (find my classes),
  `@EnableAutoConfiguration` (configure my libraries), `@SpringBootConfiguration`.
- **Component scan starts at the main class's package.** Everything must live under
  `com.pms.hotel` or Spring will silently never see it.
- **Beans are singletons** — one instance, shared. Many references can point at one object.
- **Beans must be stateless.** Many threads use the same bean simultaneously; per-request data lives
  in method parameters and local variables, on each thread's *own stack*.
- **Constructor injection, not field `@Autowired`** — allows `final`, no half-built objects, testable
  without Spring.
- **You can't `new` your way out of this:** repositories are interfaces (no class to instantiate), and
  `new` bypasses the proxy that makes `@Transactional` work.

> **Full detail in:** `docs/how-it-all-works.md` → Section 6 (all of it — 6.3 dependency-vs-inheritance,
> 6.8 "why not `new`", 6.9 the emergent chain, 6.11 startup sequence, 6.12 singletons & threads,
> 6.13 "who's stopping me")

## 3. REQUEST TIME — HTTP becomes JSON

### The problem being solved

Bytes arrive on a TCP socket. They must become a Java method call on the right object, with the right
arguments — and the return value must become bytes again. **Many times at once.**

### The journey of one request

```
  CLIENT                    KERNEL              TOMCAT                 SPRING
    │
    │ POST /bookings                                                   ①  the OS receives bytes
    │ {"roomId":12,…}  ────►  port 8080  ────►                            on port 8080 and hands
    │                          socket                                     them to the listening
    │                                                                     process
    │                                        ② parse HTTP bytes
    │                                          → HttpServletRequest
    │                                          → take a THREAD from
    │                                            the pool
    │                                              │
    │                                              ▼
    │                                        ③ call DispatcherServlet.service()
    │                                          (a SPRING object, living INSIDE Tomcat —
    │                                           it implements the Servlet interface,
    │                                           which is the only thing Tomcat understands)
    │                                              │
    │                                        ④ match POST + /bookings → BookingController.book()
    │                                              │
    │                                        ⑤ Jackson: JSON body → Java object
    │                                              │
    │                                        ⑥ Controller → Service → Repository
    │                                           @Transactional opens here ──────┐
    │                                              │                            │
    │                                        ⑦ …DATA TIME (Section 4)…          │
    │                                              │                            │
    │                                           commit ─────────────────────────┘
    │                                              │
    │                                        ⑧ Jackson: Java object → JSON
    │                                              │
    │ 201 Created  ◄──── bytes  ◄──────────────────┘
    ▼
```

### The points

- **Tomcat** owns the socket, parses HTTP, and runs a **thread pool** — one thread per request.
  It's *embedded* (inside your JAR), so production needs no Tomcat installed.
- **DispatcherServlet** is Spring's front controller: **written and created by Spring, but hosted and
  called by Tomcat**, because it implements the `Servlet` interface. Both statements are true.
- **Jackson** converts JSON ↔ Java, in both directions.
- **REST** = data (JSON) out, not HTML pages. The client owns the view.
- **The controller's only job is HTTP ↔ Java.** Business rules belong in the service — that's where
  `@Transactional` goes, and therefore where correctness lives.
- ⚠️ **Every request runs on its own thread, through shared beans.** This is the concurrency door.

> **Full detail in:** `docs/how-it-all-works.md` → Sections 3, 4, 5, 5.1, 9

## 4. DATA TIME — a Java object becomes a row

### The problem being solved

Java thinks in **objects, references, camelCase**. SQL thinks in **rows, foreign keys, snake_case**.
Bridging that by hand is enormous, repetitive, error-prone work. Four layers each remove part of it.

### The four layers, and what each removes

```
   YOUR CODE            repo.save(booking)
        │
        ▼
   SPRING DATA JPA      removes: writing a DAO class per entity
                        gives:   free CRUD from an INTERFACE + queries derived from method names
        │
        ▼
   JPA / HIBERNATE      removes: writing SQL, and field↔column mapping
   (spec / impl)        gives:   @Entity mapping, generated SQL, persistence context,
                                 dirty checking, lazy loading
        │
        ▼
   JDBC                 removes: nothing — it IS the standard vocabulary
   (interfaces only)    gives:   database-independent API (Connection, PreparedStatement, ResultSet)
        │                        ⚠️ CONTAINS NO IMPLEMENTATION
        ▼
   POSTGRESQL DRIVER    removes: the wire protocol
                        gives:   the actual implementation — TCP socket, auth, SSL,
                                 Java types ↔ Postgres binary types, error translation
        │
        ▼
   POSTGRESQL           the truth. Enforces every constraint, regardless of what the app believes.
```

### Why JDBC needs a driver (the interview answer, condensed)

> JDBC is only a **specification** — Java interfaces with no implementation. It standardises the
> **API**, but it cannot standardise the **wire protocol**, because every vendor invented their own
> incompatible binary protocol over TCP. The **driver** is the vendor's implementation: it translates
> JDBC method calls into Postgres protocol messages and back, and owns the socket, auth, SSL and type
> mapping. **JDBC is the contract; the driver is the implementation.**

### Who owns the schema — the decision we made

```
  CODE-FIRST  (ddl-auto=update)          SCHEMA-FIRST  (ddl-auto=validate)  ✅ OURS
  @Entity ──generates──► tables          schema.sql ──applied──► tables
  Java is the truth.                                                ▲
  Hibernate ALTERs your DB.              @Entity ──must match───────┘
  Never drops → silent drift.            Hibernate only CHECKS, at startup.
```

We chose schema-first because Hibernate cannot generate our `CHECK` constraint, our Postgres `ENUM`
types, our composite indexes, or — critically — the **`EXCLUDE` constraint** that will make
double-booking physically impossible.

### The points

- **`@Entity` classes are not automatic.** Hibernate does not read your database and generate them.
  One hand-written class per table you want to use. **You currently have zero.**
- **The entity is not a link in the dependency chain** — it's the *cargo* flowing through it.
- **`?` placeholders + bound parameters**: Hibernate builds `insert … values (?,?,?)`, JDBC binds real
  values into the slots. This makes SQL injection impossible and lets the DB reuse query plans.
- **The persistence context does dirty checking** — change a loaded entity inside a transaction and
  Hibernate issues the `UPDATE` at commit, with no `save()` call.
- **Lazy loading** means `booking.getGuest()` can secretly hit the database — which is why
  `open-in-view=false` matters (it turns hidden N+1 queries into loud exceptions).
- **HikariCP** is the connection pool: opening a TCP connection to Postgres is expensive, so a set of
  them is kept open and lent out per request.

> **Full detail in:** `docs/how-it-all-works.md` → Section 10 (10.1 what JDBC does, 10.2 why a driver,
> 10.3 Hibernate with example, 10.4 Spring Data JPA with example, 10.5 no-entity case,
> 10.6 the two directions, 10.7 what `save()` really does)
> **Config decisions in:** `docs/spring-boot-setup-and-anatomy.md` → Section 28

---

# Level 2 — Reference

## 5. The cast of characters

Every named thing in this project, in one table.

| Thing | What it is | Who wrote it | When it exists | Deeper |
|---|---|---|---|---|
| **Maven** | Build tool: dependencies, lifecycle, convention | Apache | Build | anatomy §14 |
| **`pom.xml`** | The build file — GAV, Java version, deps, plugins | You | Build | anatomy §18–25 |
| **Plugin** | A JAR containing the code that does a build step | Various | Build | anatomy §15 |
| **`~/.m2`** | Local cache of every downloaded library | — | Build | anatomy §16 |
| **Maven wrapper** | `mvnw` + a version pin, for reproducible builds | Apache | Build | anatomy §17 |
| **Fat JAR** | Your code + all deps + Tomcat, in one runnable file | `spring-boot-maven-plugin` | Build → Run | anatomy §24 |
| **`MANIFEST.MF`** | Label inside the JAR naming the `main()` class | Generated | Build → Run | anatomy §24 |
| **JVM** | The process that runs bytecode | Oracle/Temurin | Runtime | how-it-works §3 |
| **`application.properties`** | Runtime config — overrides Boot's defaults | You | Startup | anatomy §26–28 |
| **`ApplicationContext`** | The bean container | Spring | Startup → shutdown | how-it-works §6.11 |
| **Bean** | An object Spring creates, wires and shares (singleton) | Spring | Startup → shutdown | how-it-works §6.5 |
| **Auto-configuration** | Conditional bean definitions based on the classpath | Spring | Startup | anatomy §9 |
| **Tomcat** | Servlet container: owns the socket, parses HTTP, thread pool | Apache | Startup → shutdown | how-it-works §5 |
| **DispatcherServlet** | Spring's front controller; routes each request | Spring | Startup → shutdown | how-it-works §5.1 |
| **Thread pool** | Reused threads, one per in-flight request | Tomcat | Request | how-it-works §3 |
| **Jackson** | JSON ↔ Java, both directions | FasterXML | Request | how-it-works §9 |
| **`@RestController`** | HTTP ↔ Java. **Only** that. | You | Request | how-it-works §9 |
| **`@Service`** | Business rules. Where `@Transactional` goes. | You | Request | how-it-works §6.13 |
| **`@Repository`** | Database access. An **interface** you don't implement. | You (interface) / Spring (impl) | Request | how-it-works §10.4 |
| **`@Entity`** | A class mapped to a table. **Data, not a bean.** | You | Data | how-it-works §10.3 |
| **DTO** | A shape for the API, decoupled from the entity | You | Request | *(later)* |
| **Spring Data JPA** | Generates repository implementations; derives queries from names | Spring | Startup + Data | how-it-works §10.4 |
| **JPA** | The **specification** — annotations + `EntityManager` | Jakarta EE | Data | how-it-works §10.3 |
| **Hibernate** | The **implementation** of JPA — the ORM engine | Red Hat | Data | how-it-works §10.3 |
| **`EntityManager`** | The handle you use to persist/find entities | Hibernate | Per transaction | how-it-works §10.3 |
| **Persistence context** | In-memory map of loaded entities → enables dirty checking | Hibernate | Per transaction | how-it-works §10.3 |
| **JDBC** | The **specification** — `java.sql` interfaces. No implementation. | Java | Data | how-it-works §10.1 |
| **JDBC driver** | The **implementation** — speaks Postgres's wire protocol | PostgreSQL project | Data | how-it-works §10.2 |
| **`DataSource`** | The thing you get connections from | Spring/Hikari | Startup → shutdown | how-it-works §10.1 |
| **HikariCP** | The connection **pool** behind the DataSource | Brett Wooldridge | Startup → shutdown | anatomy §23 |
| **PostgreSQL** | A **separate process**, in Docker, on port 5433 | PG project | Always | docker-notes |
| **`schema.sql`** | **The source of truth for the schema** | You | Always | `docs/db/schema.sql` |

## 6. The six "contract vs implementation" pairs

Once you see this pattern, half the stack stops being mysterious. **The same idea recurs six times.**

| # | The contract (interface / spec) | The implementation | Why split? |
|---|---|---|---|
| 1 | `Servlet` interface | `DispatcherServlet` | Spring MVC runs on Tomcat, Jetty, or Undertow unchanged |
| 2 | **JDBC** (`java.sql.*`) | **PostgreSQL driver** | Same Java code runs on any database |
| 3 | **JPA** (`jakarta.persistence`) | **Hibernate** | Swap ORM engines without rewriting entities |
| 4 | `DataSource` interface | HikariCP | Swap connection pools freely |
| 5 | `BookingRepository` (yours) | Generated by Spring Data | You write zero implementation code |
| 6 | `BookingService` (your class) | The `@Transactional` **proxy** wrapping it | Transactions added without touching your code |

**The general shape every time:**

```
    SOMEONE DEFINES A CONTRACT          →  everyone codes against this
              ▲
              │ implements
    ┌─────────┼─────────┬─────────┐
  impl A    impl B    impl C   …       →  swappable, chosen at runtime/config
```

**And the two consequences that keep biting you:**
- **You cannot `new` a contract.** Interfaces have no constructor. Somebody must supply an
  implementation — which is *why dependency injection exists at all*.
- **Pair 6 is the dangerous one.** If you bypass Spring with `new`, no proxy is created, and
  `@Transactional` silently does nothing. No error. That's the failure mode that would let this
  project double-book a room.

## 7. Where the concurrency problem lives

Everything above converges here. This is the project's actual subject.

```
   Tomcat's thread pool
        │
        ├── THREAD-1 ──┐                          both threads run the SAME
        └── THREAD-2 ──┤                          BookingService bean, at the
                       ▼                          same instant, on their own stacks
              ┌──────────────────┐
              │ BookingService   │
              │ .bookRoom(12, …) │
              └────────┬─────────┘
                       │
   T1:  SELECT … is room 12 free for Aug 5–9?  →  YES  ─┐
   T2:  SELECT … is room 12 free for Aug 5–9?  →  YES  ─┤ ← both checked BEFORE either wrote
   T1:  INSERT booking (room 12, Aug 5–9)              ─┤
   T2:  INSERT booking (room 12, Aug 5–9)              ─┘
                       │
                       ▼
              ROOM 12 IS BOOKED TWICE.
              A real guest arrives to a room that isn't there.
```

**Why every earlier layer matters to this one diagram:**

| Layer | Its role in the bug — or the fix |
|---|---|
| **Threads** (Section 3) | The *cause*. Without concurrency there is no bug. |
| **Stateless beans** (Section 2) | One shared object serving both threads safely — as far as memory goes. |
| **`@Transactional`** (Section 4) | Makes check+insert **atomic** — the first line of defence. |
| **The proxy** (Section 6, pair 6) | If you bypass Spring with `new`, this silently vanishes and the fix is gone. |
| **Isolation levels & locking** | Decides whether T2 can *see* or *wait for* T1's in-flight work. |
| **Postgres `EXCLUDE` constraint** | The **last, unbreakable** line — the database refuses the second insert, no matter what the app does. |

**The lesson we're building toward:** application-level checks are advisory; **the database is the
only thing that can actually guarantee an invariant.** Everything else reduces the odds. That's why
`schema.sql` is the source of truth, and why we chose `ddl-auto=validate`.

> **Full detail in:** `docs/how-it-all-works.md` → Sections 3, 6.12, 11

---

## 8. State, statelessness and scale

Section 7 was "what goes wrong when two **threads** share one JVM." This is the same question one
level up: **what goes wrong when two JVMs share one job.**

### 8.1 What "state" actually is

> **State = data the server keeps in memory *between* requests, that it needs to answer the next one.**

Not data in the database. Not data in the request. **Objects still on the heap after the response was
written, because they'll be needed again.**

| | Still on the heap after the response? | State? |
|---|---|---|
| Method parameters, local variables | ❌ stack frame popped | No |
| `Booking`/`Guest` objects made this request | ❌ unreachable → young-gen garbage | No |
| Bean fields pointing at other beans | ✅ but identical on every instance | No |
| **`HttpSession`** | ✅ held by the container's map for ~30 min | **Yes** |
| **`static Map` cache** | ✅ one per JVM, lives for the process | **Yes** |
| **Mutable field on a singleton** | ✅ and two threads will corrupt it | **Yes** — and a race |
| **A file written to local disk** | (disk, not heap — same problem) | **Yes** |

### 8.2 Sessions vs JWT — traced

```
  ┌── STATEFUL (session) ────────────────────────────────────────────────┐
  │ REQ 1  POST /login                                                   │
  │   ├─ verify against DB                                               │
  │   ├─ create Session@4f2a{user, hotelId:7} ON THE HEAP                 │
  │   ├─ sessionMap.put("A7F3B2", Session@4f2a)   ← THIS JVM's heap       │
  │   └─ Set-Cookie: JSESSIONID=A7F3B2                                    │
  │      ⚠ Session@4f2a is NOT garbage. The map holds it for 30 minutes.  │
  │                                                                       │
  │ REQ 2  Cookie: JSESSIONID=A7F3B2                                      │
  │   └─ sessionMap.get("A7F3B2") → Session@4f2a → "this is adithya"      │
  │      The cookie carries NO information. The server could only         │
  │      identify the user because an object from REQ 1 survived.         │
  └───────────────────────────────────────────────────────────────────────┘

  ┌── STATELESS (JWT) ───────────────────────────────────────────────────┐
  │ REQ 1  POST /login                                                    │
  │   ├─ verify against DB                                                │
  │   ├─ build a STRING:                                                  │
  │   │    payload = {"sub":"adithya","hotelId":7,"role":"MANAGER","exp":…}│
  │   │    sig     = HMAC-SHA256(header + "." + payload, SECRET_KEY)      │
  │   └─ return base64(header).base64(payload).sig                        │
  │      ✅ Server stored NOTHING. Everything is garbage at response time. │
  │                                                                       │
  │ REQ 2  Authorization: Bearer eyJ…                                     │
  │   ├─ recompute HMAC(header.payload, SECRET_KEY), compare → MATCH      │
  │   └─ read hotelId=7 straight out of the request. Serve.               │
  │      ✅ Needed: the request + the secret key. No memory of REQ 1.      │
  └───────────────────────────────────────────────────────────────────────┘
```

**Why it only matters with more than one instance:**

```
  STATEFUL:  POST /login   → LB → JVM-A   heap: sessionMap{"A7F3B2"→…}
             GET /bookings → LB → JVM-B   heap: sessionMap{}  → null → 401 ❌
             Two JVMs = two heaps = two address spaces. B cannot reach into A's memory.

  STATELESS: both JVMs hold the same SECRET_KEY → both verify → works ✅
```

**Statelessness is not about serving one request well. It's the property that makes running more than
one copy possible.** That's why it maps directly onto cost.

### 8.3 JWT mechanics

```
   eyJhbGciOiJIUzI1NiJ9 . eyJzdWIiOiJhZGl0aHlhIiwiaG90ZWxJZCI6N30 . 4f3a9c2b…
   └──── header ───────┘   └───────── payload (claims) ─────────┘   └ signature ┘
```

- **⚠️ The payload is base64, NOT encrypted.** Anyone holding the token can decode and read it.
  **Never put a secret in a JWT.**
- The signature guarantees **integrity, not secrecy**. Change `hotelId` from 7 to 8 and the signature
  no longer matches — you'd need `SECRET_KEY` to compute a valid one.

| | **HS256** (symmetric) ← ours | **RS256** (asymmetric) |
|---|---|---|
| Keys | One secret, shared by all instances | Private key signs; public key verifies |
| Who can forge? | **Anyone with the secret** | Only the private-key holder |
| Use when | One app, several instances | Several services verify; one may issue |

**The secret comes from config (an env var), identical on every instance.** If each instance
generated its own, a token issued by A wouldn't verify on B — you'd be back to the session problem.

**Revocation — the real trade-off.** There is nothing on the server to delete, because the server
never stored the token. "Logout" only deletes the client's copy. Fire a receptionist at 10:00 and
their token stays valid until `exp`. The standard answer:

| | Access token | Refresh token |
|---|---|---|
| Lifetime | **5–15 min** | Days/weeks |
| Verified by | Signature only, no DB hit | **A database lookup** |
| Revocable? | ❌ | ✅ delete the row |

So you reintroduce a *small, deliberate* amount of server-side state exactly where revocation is
needed, and keep everything else stateless.

### 8.4 The three multi-instance traps

Not related as features — **related because each works perfectly with one instance and breaks
silently with three.**

| Trap | What happens with 3 instances | Fix |
|---|---|---|
| **Local file storage** | Upload lands on A's disk; the download request hits B → 404. A's disk dies with A on the next deploy. | **Object storage** (S3 / DO Spaces). Store the *URL* in Postgres, the bytes in the service every instance can reach. |
| **`@Scheduled`** | Each JVM has its own scheduler thread. 02:00 fires on all three → the job runs three times. | A DB-backed lock (ShedLock) so only one wins. |
| **In-memory cache** | Each JVM caches independently → three different answers | Shared cache (Redis), or accept staleness |

**`@Scheduled`** is code that runs on a clock with no HTTP request and no caller — a background thread
inside the JVM watches the time and fires the method:
```java
@Scheduled(cron = "0 0 2 * * *")          // 02:00 daily
void releaseUnconfirmedBookings() { … }   // cancel PENDING bookings older than 24h
```

**All three have the same fix**: when instances must agree on something, the agreement has to live
**outside all of them** — in Postgres, in Redis, in object storage. Same move as sessions → JWT.

### 8.5 Cost, and why serverless doesn't fit

```
   STATEFUL (sticky sessions)         STATELESS
   Scale UP: one bigger box           Scale OUT: more small boxes
   1 × 8GB droplet   = $48/mo         3 × 2GB droplets = $36/mo
   Next tier ≈ 2× price               +1 instance = +$12. Linear.
   Instance dies → users logged out   Instance dies → LB reroutes, nobody notices
```

*(A **droplet** is DigitalOcean's word for a virtual machine — a whole simulated computer with its
own CPU, RAM, disk and OS, carved out of a physical server. "2 GB droplet" = a VM allotted 2 GB.)*

**But you never eliminate state — you relocate it into Postgres.** The app tier becomes cheap and
disposable; the database becomes the expensive, hard-to-scale, correctness-critical thing. **You have
concentrated all your difficulty into one component.** Which is why this project's centrepiece is a
database problem.

**Serverless** (pay per request, instance destroyed after it, scales to zero) *requires*
statelessness. Java can do it, but:

```
   Cold start for a Spring Boot app:
   container start 200ms → JVM 100ms → classloading (10k+ classes) 500ms
   → component scan + 150 autoconfig conditions 1000ms → bean graph 500ms
   → Hibernate metamodel 800ms → Hikari opens connections 300ms → Tomcat 200ms
   ────────────────────────────────────────────────────────── ≈ 3–6 SECONDS
```

Python/PHP/Node: ~100–300ms. **The difference isn't "interpreted vs compiled" — it's how much
initialisation happens before the first request.** Fixes exist (**GraalVM native image**: AOT-compile
to a binary, ~50 ms start, ~50 MB RAM — at the cost of minutes-long builds and reflection config;
also AWS Lambda SnapStart).

**The real blocker for this app is the database** — see 9.1. Serverless means many short-lived
instances, each needing a connection, and Postgres forks a process per connection. A pool is useless
when the instance dies after one request.

**Cost crossover:** droplet = $12/mo flat regardless of traffic; Lambda = $0 at zero traffic but more
than a droplet under sustained load. **Serverless wins for spiky or near-zero traffic.** A PMS has
continuous traffic. Verdict: long-running container on a droplet. Serverless would suit a future
image-resize step or webhook receiver — short, spiky, no DB connection.

## 9. Postgres mechanics that constrain your app

### 9.1 `fork()`, and why connections are expensive

**`fork()` is a Unix system call that creates a new process by duplicating the calling one.** The
child gets a copy of the parent's memory (copy-on-write, so it's cheap until either writes), its own
PID, and its own address space.

*(Unrelated to a GitHub "fork" — that's a copy of a repository. Same metaphor, different mechanism.)*

**Postgres uses a process-per-connection model:**

```
   postmaster (the parent) listens on 5432
        │  a client connects
        ▼
      fork()  ──►  backend process #1   ← serves ONE client, exclusively, for its whole session
      fork()  ──►  backend process #2
      fork()  ──►  backend process #3
                   each ≈ 5–10 MB RSS
```

*(**RSS** = Resident Set Size — the physical RAM a process is actually occupying right now, as
opposed to virtual address space it has merely reserved.)*

**Contrast with your app, which you already understand:**

| | Spring Boot / Tomcat | PostgreSQL |
|---|---|---|
| Unit of concurrency | **Thread** | **Process** |
| Memory | One heap, shared by all threads | Separate address space each |
| 100 concurrent clients | 1 process, 100 threads, one heap | **100 processes, ~700 MB** |
| Isolation | Shared — hence race conditions | Total — hence IPC and shared buffers |

**The ceiling this creates:** `max_connections` defaults to 100.

```
    3 app instances × Hikari pool 10 =  30 connections   ✅
   20 app instances × Hikari pool 10 = 200 connections   ❌ "sorry, too many clients already"
```

**Horizontal app scaling is capped by the database's connection budget, not by your app.**

**PgBouncer** is the answer — a **separate proxy process** that speaks the Postgres protocol on both
sides (unlike the JDBC driver, which is a JAR *inside* your JVM). It exploits the fact that a pooled
connection is idle ~99% of the time:

```
   client conn #1: ──idle──[QUERY 3ms]──idle──idle──[QUERY 2ms]──idle──
   client conn #2: ──idle──idle──[QUERY 4ms]──idle──idle──idle──idle──
                   ↑ never all busy simultaneously

   200 client connections ──► PgBouncer ──► 25 real Postgres backends
```

It hands over a real backend only for the duration of a transaction, then reclaims it. Not filtering
"genuine" traffic — **time-sharing**.

### 9.2 Indexing vs partitioning

**Different things. Indexes add a lookup structure; partitioning changes where rows are stored.**

```
  INDEX — rows don't move
    heap: [p1][p2][p3][p4][p5][p6]      ← without an index: read EVERY page
    B-tree:      [root]
                /      \                 ← 2–4 page reads to find a row pointer,
           [leaf]      [leaf]              then 1 read of the heap page. O(log n).

  PARTITIONING — rows do move
    hotel_bookings          ← parent: NO storage, just routing rules
      ├── bookings_hotel_1  ← a real table, real files
      ├── bookings_hotel_2  ← a real table
      └── bookings_hotel_3
    On INSERT, Postgres routes by the partition key.
    On SELECT … WHERE hotel_id = 2, it never opens the others ("partition pruning").
```

| | Index | Partitioning |
|---|---|---|
| Changes physical storage? | ❌ | ✅ |
| Speeds up point lookups | ✅ primary purpose | ⚠️ only via pruning |
| Delete a whole slice of data | `DELETE` — slow, bloats the table | **`DROP TABLE partition`** — instant |
| Useful from | ~10k rows | **~50–100M rows** |
| Maintenance | Low | High — partitions must be created ahead of time |
| Both together? | **Yes, normally** | |

**"Partitioned index"** isn't a third concept — it's an index created on the parent, which Postgres
automatically materialises as a real index on every child partition.

#### ⚠️ The constraint rule, and why the partition key choice matters

**On a partitioned table, every `UNIQUE`/`PRIMARY KEY`/`EXCLUDE` constraint must include the partition
key.** Postgres can only enforce uniqueness *within* one partition — a global check would need a lock
on every child on every insert.

Apply that to this project's centrepiece:

```sql
EXCLUDE USING gist (
    hotel_id  WITH =,
    room_id   WITH =,
    daterange(check_in_date, check_out_date, '[)') WITH &&
)
```

| Partition by | Compatible with the EXCLUDE constraint? |
|---|---|
| `check_in_date` (RANGE) | ❌ Would require `check_in_date WITH =` — nonsense for an *overlap* check |
| **`hotel_id`** (LIST/HASH) | ✅ **Yes** — `hotel_id WITH =` is already in the constraint |

**So partitioning by `hotel_id` is genuinely compatible.** It also has a real multi-tenant benefit:
offboarding a hotel becomes `DROP TABLE bookings_hotel_42` instead of a slow `DELETE`, and vacuum /
maintenance can run per-tenant.

**Why we still won't do it:**
- **Scale.** 100 hotels × 50 rooms × several years ≈ a few million rows. Partitioning pays off in the
  tens of millions.
- **Your indexes already begin with `hotel_id`** — the leftmost prefix gives tenant-scoped lookups
  most of the same benefit, with none of the cost.
- **Operational burden.** LIST partitioning means running `CREATE TABLE` every time a hotel signs up —
  DDL at runtime, which takes locks. HASH partitioning fixes the partition count upfront and is
  painful to change.
- **Skew.** One large chain = one huge partition. Uneven benefit.
- **Cross-tenant queries** (platform-wide admin reports) scan every partition.

**Verdict: correct instinct, right key, wrong scale.** Knowing *why you're not partitioning* — and
that `hotel_id` would be the key if you did — is the answer worth having.

### 9.3 Time, timezones, and what to store

**UTC** = Coordinated Universal Time — the world's reference time, at zero offset. *(The odd acronym
is a compromise between the English "Coordinated Universal Time" and the French "Temps Universel
Coordonné".)* **India is UTC+5:30**, not +5.

**How the connection timezone actually works** — this is what caused the `Asia/Calcutta` failure:

```
   1. JDBC driver reads TimeZone.getDefault() from the JVM
   2. Sends it as a parameter in the connection STARTUP PACKET
   3. Postgres sets that SESSION's `TimeZone` setting to that value
   4. From then on, timestamptz values are converted to/from that zone on the way out/in
```

So yes — **the app tells the database its timezone, per connection.** Not a global DB setting; a
per-session one.

**What `timestamptz` really stores:** 8 bytes — microseconds since 2000-01-01 UTC. **It does not
store a timezone.** The name is misleading. It stores an *instant*, and converts on input and output
using the session's `TimeZone`. That's why the session setting matters.

#### What should an application store?

**Three categories. Getting these right is a domain decision, not a technical one.**

| | Type | Store as | Example in this project |
|---|---|---|---|
| **1. An instant** — a moment that happened | "when was this row created?" | **`timestamptz`**, always UTC | `created_at`, `updated_at`, payment time |
| **2. A local calendar date/time** — means the same locally regardless of viewer | "which day?" | **`date` / `time`**, no zone | `check_in_date`, `check_out_date`, an 11:00 checkout policy |
| **3. A future scheduled event across zones** | "the 09:00 shuttle on 12 Mar" | local time **+ the IANA zone name**, separately | *(not in this project)* |

**Category 3 needs the zone stored separately because governments change DST rules.** Convert a future
local time to UTC today and the rule may change before it arrives — you'd fire the event at the wrong
wall-clock time.

**✅ Your schema already gets this right**, and it's worth seeing why:
- `created_at timestamptz` — an instant. Correct.
- `check_in_date date` — **correct, and the interesting one.** A booking for "Aug 5" means Aug 5 at
  that hotel. It is not an instant, and it must not shift because a guest booked from another country.
  Storing it as `timestamptz` would be a bug.

**The rule:** *store instants in UTC; store calendar dates as dates; convert to the viewer's local
time only at the presentation edge.* Servers run in UTC — which is why `-Duser.timezone=UTC` is the
right setting rather than a workaround, and why DigitalOcean droplets default to UTC.

---

# Level 3 — Forward

## 10. The roadmap

### Where we are

```
   1. Scope           ✅
   2. PRD             ✅   docs/PRD_V_01.md
   3. ERD             ✅   docs/ERD_V_01.md
   4. Schema (DDL)    ✅   docs/db/schema.sql — live in Postgres
   5. PROJECT SETUP   ◀── HERE
   6. Basic CRUD APIs
   7. Booking deep-dive — concurrency, transactions, locking   ◀── the destination
   8. Backlog: Spring Security + JWT, notifications (Observer), async
```

### Step 5, broken down

| Step | What | The concept it teaches | Status |
|---|---|---|---|
| 5.1 | Understand the generated project | Layout, Maven, POM, properties | 🔄 in progress |
| 5.2 | Reconcile `.gitignore` | What belongs in version control | ⬜ next |
| 5.3 | Tidy `pom.xml` | Dependencies, scopes, parent POM | ⬜ partly done by you |
| 5.4 | Choose package structure | **Modularization** — shapes everything after | ⬜ **decision needed** |
| 5.5 | Write `application.properties` + profiles | Datasource, JDBC URLs, secrets | ⬜ **decisions needed** |
| 5.6 | Start the app, read the startup log | Sections 2 & 4 above, made real | ⬜ |
| 5.7 | First `@Entity`: `Hotel` | JPA mapping; `validate` proving it | ⬜ |

### Decisions still open

| # | Decision | Options | Where the trade-offs are |
|---|---|---|---|
| 1 | Package structure | by-layer vs **by-feature** | anatomy §29–30 |
| 2 | Which Postgres | 5433 Docker / 5432 native / **both via profiles** | anatomy §28① |
| 3 | `ddl-auto` | ✅ **decided: `validate`** | anatomy §28② |
| 4 | Password handling | plain / **`application-local.properties`** / env var | anatomy §28⑤ |
| 5 | DevTools | add / skip | anatomy §25③ |

### After setup

- **Step 6** — CRUD for `Hotel`, then `Room`. Learn REST design, status codes, DTOs vs entities,
  validation, centralized error handling.
- **Step 7** — the booking flow. Transactions, isolation levels, pessimistic vs optimistic locking,
  and the `EXCLUDE` constraint. **This is what the whole project is for.**
- **Later** — Spring Security + JWT, the Observer-pattern notification plugin, async processing.

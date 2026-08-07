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

### 5.1 "Is the DispatcherServlet Tomcat's or Spring's?" — resolving the apparent contradiction

You noticed that this section says the DispatcherServlet **belongs to Spring**, while section 6.11
says *"Tomcat is started and the DispatcherServlet is registered into it."* Those sound contradictory.
They aren't — but only because two different questions are being answered.

**Separate "who wrote/owns it" from "where does it live and who calls it":**

| Question | Answer |
|---|---|
| Who **wrote** the class? | **Spring.** It's `org.springframework.web.servlet.DispatcherServlet`. |
| Who **creates the object** at startup? | **Spring** — it's a bean in the ApplicationContext. |
| Who **calls** it when a request arrives? | **Tomcat.** |
| Where does it **run**? | **Inside Tomcat**, on a Tomcat thread. |

**The bridge between those two worlds is one Java interface: `Servlet`.**

Tomcat is a *servlet container*. It knows nothing about Spring, controllers, or beans. It knows exactly
one contract:

```java
public interface Servlet {
    void service(ServletRequest req, ServletResponse res);   // "here's a request, handle it"
}
```

Tomcat's whole job is: accept a TCP connection → parse HTTP → find the registered `Servlet` for that
URL → call `service()`. That's it.

**`DispatcherServlet` is a Spring class that implements `Servlet`.** That single fact is what lets a
Spring object plug into a Tomcat socket:

```
   ┌───────────────────── TOMCAT (knows only the Servlet interface) ──────────────────┐
   │                                                                                  │
   │   socket :8080 ──► parse HTTP ──► look up servlet for "/" ──► call service()      │
   │                                                                  │               │
   │                                          ┌───────────────────────▼─────────────┐ │
   │                                          │  DispatcherServlet                  │ │
   │                                          │  • written by Spring                │ │
   │                                          │  • created by Spring as a BEAN      │ │
   │                                          │  • implements Servlet, so Tomcat     │ │
   │                                          │    can call it without knowing       │ │
   │                                          │    anything about Spring             │ │
   │                                          └───────────────────────┬─────────────┘ │
   └──────────────────────────────────────────────────────────────────┼───────────────┘
                                                                      │
                        ┌─────────────────────────────────────────────▼──────────────┐
                        │  SPRING ApplicationContext                                  │
                        │    BookingController → BookingService → BookingRepository    │
                        └─────────────────────────────────────────────────────────────┘
```

**"Registered into it" means:** at startup, Spring hands Tomcat a reference to the DispatcherServlet
bean and says *"any request for `/` goes to this object."* Tomcat stores that reference in its
internal URL→servlet map. Spring made the object; Tomcat holds a pointer to it and calls it.

**The analogy:** a company (Spring) sends an employee (DispatcherServlet) to work at a client's office
(Tomcat). The client's receptionist routes visitors to that desk. The client didn't hire or train the
employee — but the employee sits in their building and answers their door. Both statements are true:
*"she works for Spring"* and *"she's stationed inside Tomcat."*

**Why this design is clever:** it means Spring MVC works on **any** servlet container — Tomcat, Jetty,
Undertow — with zero changes. Spring only had to implement one standard interface. Swap
`spring-boot-starter-webmvc`'s Tomcat for Jetty in your `pom.xml` and everything above the
DispatcherServlet is unaffected.

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

### 6.3 ⚠️ A dependency is NOT inheritance (the misconception that breaks everything)

This is the single most important correction in this section. If you think "A depends on B" means
"A gets B's methods," nothing else here will make sense.

| | **Inheritance** (`extends`) | **Dependency / composition** (a field) |
|---|---|---|
| Relationship | **IS-A** | **HAS-A** |
| Java syntax | `class Dog extends Animal` | `class Service { Repository repo; }` |
| Do you get the other's methods? | **YES** — `dog.eat()` works, inherited | **NO** — you must go *through* the object |
| How you call it | `this.eat()` | `repo.save(x)` |

**Concretely, in our chain:**

```java
class BookingService {
    private final BookingRepository repo;   // HAS-A repository
}
```

`BookingService` does **not** gain a `save()` method. It has a *field* that holds an object that has
`save()`. To use it you must write `repo.save(booking)` — you go **through** the object.

**This directly answers your question:** *"if things are linked/stacked, shouldn't I be able to call
`save()` directly from the controller?"*

**No — and here is exactly why:**

```
   BookingController                     BookingService                   BookingRepository
   ┌──────────────────┐                  ┌──────────────────┐             ┌──────────────┐
   │ field:           │                  │ field:           │             │              │
   │   service ───────┼─────────────────►│   repo ──────────┼────────────►│  save()      │
   │                  │                  │                  │             │  findById()  │
   │ can call:        │                  │ can call:        │             │              │
   │   service.xxx()  │                  │   repo.save()  ✅│             └──────────────┘
   │   repo.save()  ❌│                  │                  │
   │   ↑ "repo" is not a name             └──────────────────┘
   │     that exists in this class"
   └──────────────────┘
```

Inside `BookingController`, the identifier `repo` **does not exist**. It is a private field of a
*different class*. The controller can only call methods on objects it *holds* — and it holds exactly
one: the service. It literally will not compile otherwise.

**Nothing "stacks" and nothing is "inherited" down the chain.** Each class can see exactly one step
ahead, and no further. That is the whole point — it's called **encapsulation**, and it's a feature.

### 6.4 Where does the entity ("model") fit? — it is NOT in the chain

You said: *"the service would need a dependency of the model, and the model would need a dependency of
the repository."* That's a reasonable guess, and it's wrong. Let me be precise, because this is the
thing that made the chain look mysterious.

**The chain has exactly three links, and the entity is not one of them:**

```
  BookingController ──► BookingService ──► BookingRepository ──► the database
       (a bean)            (a bean)            (a bean)
```

The entity (`Booking`, `Hotel`, `Room`) is a **different kind of thing entirely**:

| | Controller / Service / Repository | Entity (`Booking`, `Hotel`) |
|---|---|---|
| What it is | **Behaviour** — a worker | **Data** — a record |
| Managed by Spring? | ✅ Yes, it's a bean | ❌ No — you create it with `new`, or Hibernate does |
| How many exist? | **One**, shared by everyone | **Many** — one per booking, per request |
| Has dependencies? | ✅ Yes | ❌ No |
| Position | A **link in the chain** | **Flows through** the chain |

```
                                          the entity is the CARGO,
                                          not a link in the chain
                                                    │
   Controller ────────► Service ────────► Repository ────────► DB
       │                   │                  │
       └─ Booking ────────►└─ Booking ───────►└─ Booking ────► row
              (data flowing THROUGH the workers)
```

So: **`Booking` is not a dependency of anything.** The service doesn't "need a Booking to function" —
it needs a *repository* to function, and it *handles* Bookings. Different relationship entirely.

*(This is the same "beans vs data objects" split as §6.12. Keep it front of mind — it's the axis the
whole architecture turns on.)*

### 6.5 Object vs bean vs dependency (three words, cleanly)
| Word | Meaning | Example |
|---|---|---|
| **Object** | *any* instance of a class | a `Booking`, a `BookingService` |
| **Bean** | an object that **Spring creates & manages** (a shared "worker") | `BookingService`, `BookingController` |
| **Dependency** | an object that **another object needs** to function | `BookingRepository` (needed by `BookingService`) |

Note: "dependency" isn't a *new kind* of object — it's a **role**. `BookingRepository` is a *bean*, and
it is *also* a *dependency* of the service. Same object, described from two angles.

### 6.6 IoC vs DI — the difference (this is what trips everyone)
- **IoC — Inversion of Control — the *principle*.** *Who is in control of creating & wiring objects?*
  Normally **your** code is (you call `new`). With IoC you **invert** that control and give it to the
  framework. It's the **what / why**.
- **DI — Dependency Injection — the *technique*.** The specific way Spring *achieves* IoC: instead of an
  object building its own dependencies, Spring **injects** them from outside, usually through the
  **constructor**. It's the **how**.
- **One line:** *IoC is the policy ("the framework is in charge of wiring"); DI is the mechanism ("hand
  each object its dependencies through the constructor").*

### 6.7 The example — by hand vs with Spring
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

### 6.8 "Why can't I just use `new`?" — the objection, taken seriously

This is the right question to ask, and the honest answer is: **for a tiny program, you can, and DI
would be over-engineering.** DI is not magic sprinkled on for its own sake. It solves specific
problems that only appear at real size. Here they are, concretely.

**Your proposal:**

```java
@RestController
class BookingController {
    private BookingService service = new BookingService();     // ← your idea

    @PostMapping("/bookings")
    Booking bookRoom(@RequestBody BookingRequest req) {
        return service.bookRoom(req.roomId(), req.guestId(), req.from(), req.to());
    }
}
```

That reads fine. Now watch what actually happens when you try it.

---

#### Problem 1 — It doesn't compile. The chain isn't optional; it's *discovered*.

`new BookingService()` requires `BookingService` to have a no-argument constructor. But the service
can't do its job without a repository. So *someone* has to supply one. That someone is now you:

```java
private BookingService service = new BookingService( new BookingRepository() );
```

But `BookingRepository` can't talk to the database by itself — it needs an `EntityManager`. Which
needs an `EntityManagerFactory`. Which needs a `DataSource`. Which needs a connection pool. Which
needs the URL, username and password:

```java
// what "new BookingService()" ACTUALLY expands to:
HikariDataSource ds = new HikariDataSource();
ds.setJdbcUrl("jdbc:postgresql://localhost:5433/hotel_pms");
ds.setUsername("postgres");
ds.setPassword("...");                        // ← hard-coded secret, in a controller
EntityManagerFactory emf = Persistence.createEntityManagerFactory(...);
EntityManager em = emf.createEntityManager();
BookingRepository repo = new BookingRepositoryImpl(em);
BookingService service = new BookingService(repo);
```

**This is the answer to "why is there a chain, and why must I know about it in advance?"**

You don't declare the chain. **The chain is what `new` forces you to build.** To construct one object
you must first construct everything it needs, and everything *those* need, all the way down. The chain
was always there — DI is what lets you *stop having to know about it*.

> **Read that again, because it inverts your intuition:** DI does not *create* the dependency chain.
> The chain exists because of what the code needs. DI is the thing that means **the controller never
> has to know the repository exists.** Without DI, the controller must know about the repository, the
> EntityManager, *and* the database password. With DI, it knows about exactly one thing: the service.

#### Problem 2 — You physically cannot `new` a Spring Data repository

This one is decisive. Your repository will look like this:

```java
public interface BookingRepository extends JpaRepository<Booking, Long> { }
```

It is an **interface**. There is no class. There is no implementation to instantiate.
`new BookingRepository()` is a **compile error** — you cannot instantiate an interface in Java.

So who implements `save()`, `findById()`, `findAll()`? **Spring Data generates the implementation
class at runtime**, at startup, by reading the interface, and registers it as a bean.

There is no version of this that works with `new`. The object you need **does not exist** until
Spring creates it.

#### Problem 3 — `@Transactional` silently stops working, and your double-booking bug becomes unfixable

**This is the one that matters most for this project.**

When Spring creates a bean annotated `@Transactional`, it doesn't hand you your object directly. It
wraps it in a **proxy** — a generated subclass that intercepts every method call:

```
   Spring-created bean:
   caller ──► [PROXY] ──► your BookingService
                │  1. BEGIN TRANSACTION
                │  2. call the real method
                │  3. COMMIT   (or ROLLBACK if it threw)
                ▼

   new BookingService():
   caller ──► your BookingService          ← no proxy. no transaction. @Transactional is
                                             just a comment that does nothing.
```

If you write `new BookingService()`, Spring never sees the object, never wraps it, and
**`@Transactional` does absolutely nothing — with no error, no warning, no log line.**

For this project that is fatal. Your entire no-double-booking guarantee depends on transactions and
locking working correctly (§11). A silently-disabled transaction means:
- the "check availability then insert" pair is no longer atomic,
- two concurrent threads can both pass the check,
- **the exact bug this project exists to prevent.**

**You cannot get transactions without letting Spring create the object.** That alone settles it.

#### Problem 4 — Configuration lives outside the code, on purpose

Your database URL and password come from `application.properties`, and differ per environment (your
laptop, Docker, production). With `new`, they'd be hard-coded in a `.java` file and compiled into the
JAR — meaning a password in Git and a rebuild to change environments.

Spring reads the properties file and builds the `DataSource` from it. Your code never mentions a URL.

#### Problem 5 — Testing

To test `BookingService` without a database, you need to hand it a **fake** repository:

```java
BookingService service = new BookingService(fakeRepo);   // ✅ possible — the constructor takes it
```

If the service does `new BookingRepository()` *inside itself*, there is no seam. You cannot substitute
anything. **Every test now needs a real running Postgres.** Constructor injection is what makes a
class testable in isolation.

#### The honest summary

| Your `new` approach | What DI gives you |
|---|---|
| Works for 2–3 simple classes | Necessary once objects need objects need objects |
| You build the whole chain by hand | You declare one need; Spring builds the chain |
| Impossible for interfaces (repositories) | Spring generates and injects the implementation |
| **`@Transactional` silently dead** | Proxies work → transactions work |
| Config hard-coded in Java | Config from properties/env |
| Untestable without a real DB | Inject fakes freely |

**DI is not a fancier `new`. It's the thing that stops `new` from spreading up the call chain.**

### 6.9 The chain is NOT something you declare in advance

You asked: *"why do I have to tell in advance that the service is also linked with the repository?"*

**You don't.** Nobody ever writes down the chain. Each class declares **only its own immediate need**,
and knows nothing beyond it:

```java
@RestController
class BookingController {
    private final BookingService service;                    // I need: a service. That's all I know.
    BookingController(BookingService service) { ... }
}

@Service
class BookingService {
    private final BookingRepository repo;                    // I need: a repository. That's all I know.
    BookingService(BookingRepository repo) { ... }
}

public interface BookingRepository extends JpaRepository<Booking, Long> { }
                                                             // I need: nothing you write.
```

**Read those three declarations. The word "repository" never appears in the controller.** The
controller has no idea a repository exists.

The "chain" in the diagram is not a thing anyone wrote — it's what Spring **works out** at startup by
following each declaration one hop at a time:

```
   Spring's reasoning at startup:
     "I need a BookingController.  Its constructor wants a BookingService."
        → "I need a BookingService.  Its constructor wants a BookingRepository."
             → "I need a BookingRepository.  It's an interface — I'll generate one."
                  → "That needs an EntityManager — auto-config already built one."
             ← build the repository, hand it up
        ← build the service, hand it up
     ← build the controller. Done.
```

That is called **resolving the dependency graph**, and it is the entire job of the container. The
chain is an *emergent* consequence of local declarations — not a plan anyone authored.

### 6.10 `@Autowired` vs constructor injection

You described `@Autowired` as "reading an instance of a different class." Close, but one word is off,
and it matters:

- **`@Autowired` does not create anything.** It says: *"Spring, hand me the bean you already made."*
- `new` **creates a brand-new object** that only you have.

Same word "get", completely different meaning. With `@Autowired`, your controller and every other
class receive **the same single shared instance**.

**There are two ways to inject, and the industry has settled on one:**

```java
// ❌ FIELD injection — what most old tutorials show
@RestController
class BookingController {
    @Autowired
    private BookingService service;
}

// ✅ CONSTRUCTOR injection — what you should write
@RestController
class BookingController {
    private final BookingService service;

    BookingController(BookingService service) {     // Spring calls this and passes the bean
        this.service = service;
    }
}
```

| | Field `@Autowired` | Constructor injection |
|---|---|---|
| Can the field be `final`? | ❌ No | ✅ **Yes** — immutable after construction |
| Can the object exist half-built? | ✅ Yes (briefly `null`) | ❌ No — dependencies required to construct |
| Testable without Spring? | ❌ No — you'd need reflection | ✅ Yes — just call the constructor |
| Hidden dependencies? | ✅ Yes — add 10 fields, nobody notices | ❌ No — a 10-arg constructor screams "this class does too much" |
| Spring's own recommendation | Discouraged since 4.3 | **Recommended** |

**Bonus you'll see in real code:** if a class has **exactly one** constructor, Spring injects through
it automatically — you don't even write `@Autowired`. That's why modern Spring code often has no
injection annotations at all.

### 6.11 The container, and what actually happens at startup
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

#### The startup sequence, in actual order

You sketched this as *"first Tomcat, and after that the application context, and inside the context
the DispatcherServlet."* **Two of those three are the wrong way round** — and the real order explains
several things you'll see in the log.

Here is what `SpringApplication.run(...)` actually does:

```
   1. JVM starts, runs main()
          │
   2. SpringApplication.run() creates an empty ApplicationContext
          │
   3. Reads application.properties            ← config must exist before anything is built
          │
   4. COMPONENT SCAN — finds your @RestController, @Service, @Repository classes.
      Records them as "bean DEFINITIONS" (recipes). Nothing is created yet.
          │
   5. AUTO-CONFIGURATION — asks ~150 conditional questions about the classpath:
        "JDBC driver + a datasource URL present?" → define a HikariCP DataSource bean
        "Hibernate present + a DataSource defined?" → define an EntityManagerFactory bean
        "Tomcat on the classpath?"                → define a TomcatWebServer bean
      Still just definitions.
          │
   6. INSTANTIATE all non-lazy singletons, resolving the dependency graph (§6.9).
      → DataSource created, pool opens connections
      → Hibernate boots, reads your @Entity classes, runs ddl-auto=validate
      → repositories generated, services built, controllers built
          │
   7. NOW Tomcat is created and STARTED. The DispatcherServlet is registered into it.
          │
   8. Tomcat binds the socket on port 8080 and begins accepting requests.
          │
   9. "Started HotelPmsApplication in 3.421 seconds"
```

**Corrections to your model:**

| You said | Actually |
|---|---|
| Tomcat first, then the context | **Context first.** Tomcat is created near the *end* — it is itself just another bean. |
| Tomcat is "inside" the application context | Right idea, wrong order: the context **contains** the Tomcat bean, so the context must exist first. |
| DispatcherServlet is inside the context | ✅ **Correct.** It's a bean, registered into Tomcat at step 7. |

**Why the order matters practically — the whole app is built before a single request can arrive.**
That is deliberate:
- A missing database password fails at **step 6**, in 2 seconds, on your screen.
- An `@Entity` that doesn't match your schema fails at **step 6** (that's `ddl-auto=validate`).
- Tomcat only opens the port at step 7 — **after** everything is proven healthy.

So a Spring Boot app that has started is an app whose entire object graph is already verified. It
never accepts a request it isn't ready to serve. This is why "fail fast at startup" is a design
principle here, not an accident.

### 6.12 Beans are STATELESS shared workers (the part that confused you)
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

#### "But OOP says I need an object to call methods. If there's only ONE object, how does everyone use it?"

Good question, and the answer is a piece of core Java that's easy to have never had spelled out.

**Key fact: a variable does not *contain* an object. It contains a *reference* — an arrow pointing at
an object that lives on the heap.** And **many arrows can point at the same object**.

```java
BookingService a = someService;
BookingService b = someService;
BookingService c = someService;
// THREE variables. ONE object. All three arrows point to the same place in memory.
a == b   →  true
```

```
      HEAP (where objects live)
      ┌──────────────────────────────┐
      │   BookingService  #7f3a       │◄──── controller's  `service` field
      │   ┌────────────────────────┐  │◄──── some other class's field
      │   │ repo → BookingRepo#2b1 │  │◄──── a test's local variable
      │   └────────────────────────┘  │
      └──────────────────────────────┘
             ONE object, MANY references
```

So "one bean" never means "only one place can use it". It means **everyone shares the same instance**
instead of each making their own. Your OOP rule still holds perfectly: you *do* need an object to call
a method — you just don't need a *separate* one.

**"But then what about 50 requests at the same time?"** This is the part that feels impossible, and
it's worth getting right because the whole concurrency deep-dive rests on it.

**When you call a method, the object does not "run". A thread runs, using the object.**

Each thread gets its own **call stack** — a private scratch area. Every method call pushes a new
**stack frame** onto it, holding *that call's* parameters and local variables:

```
   ONE BookingService object (on the heap)
                 ▲          ▲          ▲
                 │          │          │   all three threads call bookRoom() on the SAME object
     ┌───────────┘   ┌──────┘   ┌──────┘
   THREAD-1        THREAD-2   THREAD-3
   ┌──────────┐   ┌──────────┐   ┌──────────┐
   │ own STACK│   │ own STACK│   │ own STACK│      ← each thread has its OWN stack.
   │ roomId=12│   │ roomId=88│   │ roomId=12│        Same variable NAME, separate memory.
   │ guest=41 │   │ guest=17 │   │ guest=93 │        Nobody can see anyone else's.
   └──────────┘   └──────────┘   └──────────┘
```

**The method's *code* is shared. The method's *data* is not.** `roomId` in thread-1 and `roomId` in
thread-2 are different memory locations that happen to share a name. This is why one stateless object
can serve a thousand simultaneous requests without any of them interfering.

**And now the trapdoor — this is the whole project in one example:**

```java
@Service
class BookingService {
    private final BookingRepository repo;   // ✅ SAFE: set once at startup, never changes
    private int bookingCount = 0;           // ❌ DANGER: shared MUTABLE state on a shared object
    private Booking current;                // ❌ DISASTER: thread-2 overwrites thread-1's booking

    void bookRoom(Long roomId, Long guestId) {
        Long total = roomId + guestId;      // ✅ SAFE: local variable, lives on THIS thread's stack
    }
}
```

| Where the data lives | Shared between threads? | Safe? |
|---|---|---|
| A **local variable** inside a method | ❌ No — one copy per call, on that thread's stack | ✅ Always safe |
| A **method parameter** | ❌ No — same, on the stack | ✅ Always safe |
| A **field** that never changes (`final`, set in the constructor) | ✅ Yes, but read-only | ✅ Safe |
| A **field that gets modified** (`bookingCount`, `current`) | ✅ Yes — **one copy for everyone** | ❌ **Race condition** |

`bookingCount++` looks like one operation. It's three: *read, add one, write back.* Two threads can
both read `5`, both compute `6`, both write `6` — and one booking silently vanishes from the count.

**That is a race condition, and it is the exact shape of the double-booking bug** — just at the
database level instead of the memory level (section 11). Two threads both read "room 12 is free", both
decide to book it, both write. Same failure, bigger blast radius.

**So the rule "beans are stateless" isn't style advice.** It's the thing that makes sharing one object
across many threads safe at all.

### 6.13 "Who is actually stopping me?" — the honest answer

You asked three sharp questions that all share one root. Let me answer them plainly, including the
part where **you are right and nothing is stopping you.**

> 1. If the service can hold a `BookingRepository`, who stops me holding one in the controller too?
> 2. Why does `BookingService` take `repo` as a constructor parameter — is that what *lets* it use the class?
> 3. Why can't I just create the object I need wherever I want?

#### Question 2 first, because it's the misconception underneath the other two

**The constructor parameter is NOT permission to use the class.**

That's the crux. You seem to be reading `BookingService(BookingRepository repo)` as *"this is what
grants BookingService access to BookingRepository."* It isn't. Java access has nothing to do with it.

**In Java, any `public` class on the classpath can be named from anywhere.** No declaration, no
import ceremony, no permission:

```java
package com.pms.hotel.booking;

class AnyClassAtAll {
    void whatever() {
        BookingRepository r;              // ✅ legal. Naming the TYPE needs no permission.
        SomeOtherClass    s;              // ✅ also legal
    }
}
```

So what *is* the parameter for? **It's a declaration of need, addressed to Spring.**

```java
BookingService(BookingRepository repo)
               └──────┬──────────────┘
      "Spring: I cannot function without one of these.
       Do not construct me unless you hand me one."
```

Two separate things you've been merging into one:

| | What it is | Who provides it |
|---|---|---|
| **The type** `BookingRepository` | A *name* — the class/interface | The compiler; free to anyone |
| **An instance** of it | A live *object* with a DB connection behind it | **Spring** — see 6.8, you cannot `new` it |

The parameter is about the **second**. It's not "may I use this class?" — it's **"where do I get a
working one?"** And the answer to *that* can only be Spring, because the repository is an interface
whose implementation Spring generates at runtime.

**Test it against your own idea.** You proposed doing this instead:

```java
class BookingService {
    BookingRepository repo = new BookingRepository();   // ❌ compile error: interface, can't instantiate
}
```

**That is the whole argument in one line.** You *can* name the type anywhere. You *cannot* create an
instance. So the object has to come from outside — and "declaring it in the constructor" is simply how
you tell Spring to bring you one.

#### Question 1 — can the controller hold a repository?

**Technically: yes. Absolutely. Nothing stops you.** This compiles and runs perfectly:

```java
@RestController
class BookingController {
    private final BookingRepository repo;          // ← the controller, holding a repository

    BookingController(BookingRepository repo) { this.repo = repo; }

    @PostMapping("/bookings")
    Booking book(@RequestBody Booking b) {
        return repo.save(b);                        // ← works. Really. It works.
    }
}
```

Spring will happily inject it. The compiler is fine. The app starts. The endpoint responds.

**I'm being explicit about this because I don't want you to believe in a barrier that doesn't exist.**
The layered architecture is a **design discipline**, not a technical restriction. Java will not stop
you and neither will Spring.

**So why not do it?** Four concrete reasons — not "best practice", actual consequences:

**① You lose your transaction boundary — and with it, this project.**

`@Transactional` belongs on the *service*, because a business operation is what must be all-or-nothing:

```java
@Transactional                          // ← BEGIN ... COMMIT wraps this ENTIRE method
Booking bookRoom(Long roomId, ...) {
    if (repo.overlapsExisting(roomId, from, to)) throw new RoomUnavailable();
    Booking b = repo.save(new Booking(...));
    paymentRepo.save(new Payment(b, PENDING));
    return b;                                       // ← all three succeed, or all three roll back
}
```

Skip the service and put those calls in the controller and **each repository call becomes its own
tiny transaction**. The check and the insert are no longer atomic — which is *precisely* the gap two
concurrent threads slip through to double-book a room. **The service layer is where your correctness
guarantee physically lives.**

**② The controller's job stops being one job.**

A controller should translate **HTTP ↔ Java**: read the body, validate the shape, return a status
code. Business rules ("a booking may not overlap", "cancelling within 24h forfeits the deposit") are
not HTTP concerns. Merge them and every rule is now reachable only through an HTTP request.

**③ The logic becomes unreusable.**

Later you'll want to create a booking from somewhere that isn't a web request — a scheduled job, a
CSV import, a test, a message consumer. If the rules live in `BookingService`, all of those just call
`service.bookRoom(...)`. If they live in the controller, you must fake an HTTP request or copy-paste
the logic. Copy-paste means the rule now exists twice and will diverge.

**④ You cannot test the rules without a web server.**

```java
new BookingService(fakeRepo).bookRoom(...)     // ✅ plain unit test, milliseconds, no Spring
```
versus booting Tomcat and firing real HTTP to test that overlapping dates are rejected.

> **The honest summary:** nothing enforces layers. They are a *choice* that buys you a transaction
> boundary, reusable rules, and fast tests. For this project the transaction boundary alone settles
> it — it's the difference between a booking system that can double-book and one that can't.

#### Question 3 — "why can't I create the object wherever I want?"

Now you can see the two halves clearly:

| | Can you? | Why |
|---|---|---|
| **Name** any public type anywhere | ✅ Yes | Java imposes no restriction |
| **`new`** an ordinary class anywhere | ✅ Yes | It's just Java |
| **`new` a `BookingRepository`** | ❌ **No** | It's an **interface**. There is no class to instantiate. |
| **`new` a `BookingService`** and have `@Transactional` work | ❌ **No** | No Spring → no proxy → transactions silently dead (6.8) |
| Have Spring inject anything, anywhere | ✅ Yes | Layering is discipline, not enforcement |

#### And finally — "how does this link to the controller? Why does the controller depend on the service?"

It doesn't have to. **You chose it.**

```
   The controller depends on the service BECAUSE you decided
   the controller should do HTTP and the service should do business rules.

   Once you've made that decision, the controller needs a way to reach
   the business rules — so it holds a service.
   That "holding" IS the dependency. Nothing more mystical than that.
```

**The dependency is a consequence of the design, not a rule imposed by Spring.** If you put everything
in the controller, the controller would depend on the repository instead — and Spring would wire that
just as cheerfully. The chain in the diagrams reflects **the responsibilities you assigned**, and
nothing else.

### 6.14 Why all this is worth it (payoff)
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

### 10.1 What JDBC actually *does* (and what it deliberately does not)

**JDBC = Java Database Connectivity.** It ships with Java itself, in `java.sql`. And the single most
important fact about it:

> **JDBC is almost entirely *interfaces*. It contains no code that can talk to any database.**

It is a **contract** — a standard vocabulary that says: *"any database library for Java must offer
these methods, with these names and these signatures."*

**The five types you'd use, and what each is for:**

| Interface | Represents | Key methods |
|---|---|---|
| `DriverManager` / `DataSource` | Where to get a connection from | `getConnection()` |
| `Connection` | One open session with the database | `prepareStatement()`, `commit()`, `rollback()`, `setAutoCommit()` |
| `PreparedStatement` | One SQL statement with `?` slots | `setLong()`, `setString()`, `executeQuery()`, `executeUpdate()` |
| `ResultSet` | A cursor over returned rows | `next()`, `getString()`, `getLong()` |
| `SQLException` | Something went wrong | `getSQLState()` |

**What JDBC gives you — a single, database-independent vocabulary:**

```java
Connection conn = dataSource.getConnection();
PreparedStatement ps = conn.prepareStatement("select name from hotels where id = ?");
ps.setLong(1, 7);
ResultSet rs = ps.executeQuery();
while (rs.next()) {
    System.out.println(rs.getString("name"));
}
```

**That exact code runs unchanged against PostgreSQL, MySQL, Oracle, SQL Server or H2.** Not one
character differs. That is JDBC's entire purpose and its entire achievement.

**What JDBC does NOT do — and this is the part that answers your driver question:**

| JDBC does | JDBC does **not** |
|---|---|
| Define the method names | Implement any of them |
| Define what a `Connection` *is* | Open a TCP socket |
| Define `executeQuery()` | Know how to encode a query for any actual database |
| Define `SQLException` | Speak a single database's network protocol |
| Standardise the **API** | Standardise the **wire protocol** ← the crux |

So JDBC is a set of empty method signatures. **Somebody has to write the code behind them. That
somebody is the driver.**

### 10.2 Why do we need a driver if we already have JDBC? *(interview answer)*

This is a genuinely common interview question. Here's the reasoning, then the crisp answer.

#### The core reason: databases standardised their SQL, but never their networking

Every database server listens on a TCP port and accepts a **binary message format of its own
invention** — its **wire protocol**. These protocols are completely incompatible:

| Database | Default port | Wire protocol |
|---|---|---|
| PostgreSQL | 5432 | The "PostgreSQL Frontend/Backend Protocol v3" — messages tagged `Q`, `P`, `B`, `E`, `S`… |
| MySQL | 3306 | The MySQL Client/Server Protocol — a completely different byte layout |
| Oracle | 1521 | TNS / Oracle Net — different again, and proprietary |

Sending a Postgres `Parse` message to MySQL gets you a connection reset. There is **no universal
database wire protocol** and there never has been. Standardising it would have required every vendor
to abandon their own — commercially impossible.

**So the industry standardised the layer it could: the Java API.** JDBC says *what you can ask for*.
Each vendor supplies a translator that turns those asks into their own bytes. **That translator is the
driver.**

#### What is actually communicated — the two conversations

This is what you wanted: what passes between these pieces.

```
  ┌── CONVERSATION 1 : Your JVM, in-process ─────────────────────────────────┐
  │                                                                          │
  │   Hibernate/your code            org.postgresql.jdbc.PgPreparedStatement  │
  │        │                                    │                             │
  │        │  ps.setLong(1, 7)   ──────────────►│    ← ordinary JAVA METHOD   │
  │        │  ps.executeQuery()  ──────────────►│      CALLS. No network.     │
  │        │                                    │      Nanoseconds.           │
  │        │◄──── returns a ResultSet ──────────│                             │
  └──────────────────────────────────────────────────────────────────────────┘
                                       │
                        the driver now TRANSLATES
                                       │
  ┌── CONVERSATION 2 : Over TCP to port 5433 ────────────────────────────────┐
  │                                                                          │
  │   THE DRIVER                                        POSTGRESQL SERVER     │
  │        │                                                    │             │
  │        │  'P' Parse   "select name from hotels where id=$1" │             │
  │        │  'B' Bind    parameter 1 = int8(7)                 │             │
  │        │  'E' Execute                                        │             │
  │        │  'S' Sync                          ────────────────►│             │
  │        │                                                    │  plans &    │
  │        │                                                    │  executes   │
  │        │◄─── 'T' RowDescription  (column names, types)  ─────│             │
  │        │◄─── 'D' DataRow         (raw bytes: "Taj Palace")───│             │
  │        │◄─── 'C' CommandComplete ───────────────────────────│             │
  │        │◄─── 'Z' ReadyForQuery   ───────────────────────────│             │
  └──────────────────────────────────────────────────────────────────────────┘
```

**The driver's job, precisely, is to sit between those two conversations and convert:**

| Direction | The driver converts |
|---|---|
| **Outbound** | Java method calls + Java types → Postgres protocol messages + Postgres binary types (`setLong(1, 7)` → an 8-byte big-endian integer in a `Bind` message) |
| **Inbound** | Postgres protocol bytes → Java objects (`DataRow` bytes → a `String` from `rs.getString()`) |

**It also handles everything else that's Postgres-specific:** the TCP connection and startup
handshake, authentication (`scram-sha-256`), SSL negotiation, type mapping (Postgres `numeric` →
Java `BigDecimal`, `timestamptz` → `OffsetDateTime`), turning Postgres error codes into
`SQLException`s, and Postgres-only features like `COPY` and `LISTEN/NOTIFY`.

#### How the right driver gets chosen

You never name the driver in your code. Two mechanisms do it:

1. **The subprotocol in your URL.** `jdbc:postgresql://…` — the second segment is the routing key.
2. **Service discovery.** The `postgresql.jar` contains a file
   `META-INF/services/java.sql.Driver` naming `org.postgresql.Driver`. On startup, Java's
   `ServiceLoader` reads every such file on the classpath and registers each driver. `DriverManager`
   then asks each one *"do you accept `jdbc:postgresql:…`?"* until one says yes.

**That's the full loop:** driver present on the classpath at runtime (scope `runtime`, section 22 of
the anatomy doc) → self-registers → matched by the URL's subprotocol → never named in your code.

#### The interview answer, condensed

> **"We have JDBC — why do we need a driver?"**
>
> JDBC is only a **specification** — a set of Java interfaces (`Connection`, `PreparedStatement`,
> `ResultSet`) with no implementation. It standardises the **API** that Java code uses, but it cannot
> standardise the **wire protocol**, because every database vendor invented their own incompatible
> binary protocol over TCP.
>
> The driver is the **vendor-specific implementation** of those interfaces. It translates JDBC method
> calls into that database's protocol messages and translates the responses back into Java types. It
> also owns the socket, authentication, SSL, type mapping and error translation.
>
> **JDBC is the contract; the driver is the implementation.** That split is what lets the same Java
> code run against any database by swapping one JAR and one URL.

**One-line analogy:** JDBC is the *phrasebook* — it defines what you're allowed to say. The driver is
the *interpreter* who actually speaks Postgres.

### 10.3 What Hibernate (the ORM) actually does — with an example

**The problem an ORM solves: Java and SQL model the world differently.**

| Java thinks in | SQL thinks in |
|---|---|
| Objects with fields | Rows with columns |
| References (`booking.getGuest()`) | Foreign keys (`guest_id = 41`) |
| Inheritance, collections | Neither exists |
| `camelCase` | `snake_case` |
| `LocalDate` | `date` |

Bridging that by hand is what makes raw JDBC tedious. **This is called the object-relational impedance
mismatch, and Hibernate's job is to bridge it automatically.**

#### The same operation, both ways

**Raw JDBC — you do every step yourself:**
```java
String sql = "insert into hotel_bookings "
           + "(hotel_id, guest_id, room_id, check_in_date, check_out_date, status) "
           + "values (?,?,?,?,?,?::booking_status)";
try (PreparedStatement ps = conn.prepareStatement(sql, RETURN_GENERATED_KEYS)) {
    ps.setLong(1, booking.getHotelId());              // you map every
    ps.setLong(2, booking.getGuestId());              // field to every
    ps.setLong(3, booking.getRoomId());               // column, by hand,
    ps.setObject(4, booking.getCheckInDate());        // in the right order,
    ps.setObject(5, booking.getCheckOutDate());       // and it silently breaks
    ps.setString(6, booking.getStatus().name());      // if the schema changes
    ps.executeUpdate();
    ResultSet keys = ps.getGeneratedKeys();
    if (keys.next()) booking.setId(keys.getLong(1));  // and you copy the id back
}
```
Reading is worse — a `while (rs.next())` loop copying each column into each field.

**With Hibernate — you describe the mapping *once*, in annotations:**
```java
@Entity
@Table(name = "hotel_bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hotel_id", nullable = false)
    private Long hotelId;

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;

    @Enumerated(EnumType.STRING)
    private BookingStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_id")
    private Guest guest;            // ← an OBJECT reference, not a Long
}
```
and then the operation is one line:
```java
entityManager.persist(booking);     // Hibernate writes the INSERT for you
```

**What Hibernate did with those annotations:**

| Annotation | Tells Hibernate |
|---|---|
| `@Entity` | "This class maps to a table — manage it" |
| `@Table(name="hotel_bookings")` | Which table (without it, it'd guess `booking`) |
| `@Id` | Which field is the primary key |
| `@GeneratedValue(IDENTITY)` | The DB generates the id; read it back after insert |
| `@Column(name="check_in_date")` | Field ↔ column name mapping |
| `@Enumerated(STRING)` | Store the enum's *name*, not its ordinal number |
| `@ManyToOne` + `@JoinColumn` | This object reference **is** the `guest_id` foreign key |

#### The four things Hibernate does that JDBC never could

**① Generates the SQL** — INSERT, UPDATE, DELETE and SELECT, from the annotations. Change a column
name in one place and every statement follows.

**② Turns foreign keys into object references.** `booking.getGuest().getName()` — Hibernate fetches
the guest row and builds the object. In JDBC you'd write and join that yourself.

**③ The persistence context, and dirty checking.** This one genuinely surprises people:

```java
@Transactional
void cancelBooking(Long id) {
    Booking b = repo.findById(id).orElseThrow();
    b.setStatus(CANCELLED);
    // NO save() call. None.
}                                  // ← on commit, Hibernate notices the change
                                   //   and issues: update hotel_bookings set status=? where id=?
```

Inside a transaction, Hibernate keeps a **persistence context** — a map of every entity it loaded,
plus a snapshot of its original values. At commit it compares each entity to its snapshot and writes
`UPDATE`s for whatever changed. That's **dirty checking**. It's powerful and it's a common source of
"why did that row change?" until you know it exists.

**④ Batching, caching and flush ordering** — it can reorder and batch statements, and it won't
re-query for an entity already in the persistence context.

**JPA vs Hibernate, in one line:** **JPA** is the *specification* (the `jakarta.persistence`
annotations and the `EntityManager` interface — the same "contract vs implementation" split as JDBC).
**Hibernate** is the most common *implementation*. Your annotations are JPA; the engine executing them
is Hibernate.

### 10.4 What Spring Data JPA actually does — with an example

Hibernate removed the SQL. **Spring Data JPA removes the boilerplate around Hibernate.**

**Without it** you'd write a DAO class for every entity, all nearly identical:
```java
@Repository
public class BookingRepository {
    @PersistenceContext private EntityManager em;

    public Booking save(Booking b)       { em.persist(b); return b; }
    public Booking findById(Long id)     { return em.find(Booking.class, id); }
    public List<Booking> findAll()       { return em.createQuery("from Booking", Booking.class).getResultList(); }
    public void delete(Booking b)        { em.remove(b); }
    public List<Booking> findByHotelId(Long hotelId) {
        return em.createQuery("from Booking where hotelId = :h", Booking.class)
                 .setParameter("h", hotelId).getResultList();
    }
}
```
Then the same file again for `Room`, `Guest`, `Hotel`, `Payment` — with two words changed each time.

**With Spring Data JPA, you write an interface and no implementation at all:**
```java
public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByHotelId(Long hotelId);

    List<Booking> findByHotelIdAndStatus(Long hotelId, BookingStatus status);

    List<Booking> findByRoomIdAndCheckOutDateAfterAndCheckInDateBefore(
            Long roomId, LocalDate from, LocalDate to);          // ← the overlap query!
}
```

**Two distinct pieces of magic here, and they're worth separating:**

**① Inherited CRUD.** `JpaRepository<Booking, Long>` — the two type parameters are *"which entity"*
and *"what type is its id"*. From that alone you inherit `save`, `saveAll`, `findById`, `findAll`,
`delete`, `count`, `existsById`, plus paging and sorting. **You wrote none of them.**

**② Query derivation from method names.** Spring **parses the method name at startup** and builds the
query from it:

```
   findByRoomIdAndCheckOutDateAfterAndCheckInDateBefore(roomId, from, to)
   └─┬─┘└──┬──┘└┬┘└──────┬───────┘└─┬──┘└┬┘└──────┬──────┘└──┬──┘
     │     │    │        │          │    │        │          │
   find   Room  AND  CheckOutDate  After AND  CheckInDate  Before
   (verb)  Id                                                       ← keywords

   becomes:
     select b from Booking b
     where b.roomId = ?1 and b.checkOutDate > ?2 and b.checkInDate < ?3
```

The keyword vocabulary includes `And`, `Or`, `Between`, `LessThan`, `GreaterThan`, `After`, `Before`,
`Like`, `In`, `IsNull`, `OrderBy`, `Top`, `Distinct`. **If you misspell a field name, startup
fails** — it validates every derived query against your entities at boot.

**When names get silly, write the query yourself:**
```java
@Query("select b from Booking b where b.room.id = :roomId " +
       "and b.checkInDate < :out and b.checkOutDate > :in " +
       "and b.status in ('CONFIRMED','CHECKED_IN')")
List<Booking> findOverlapping(@Param("roomId") Long roomId,
                              @Param("in") LocalDate in,
                              @Param("out") LocalDate out);
```
*(That's **JPQL** — it queries your **entity classes**, not tables. `@Query(nativeQuery = true)` lets
you drop to real SQL when you need Postgres-specific features.)*

**How it works mechanically:** at startup Spring scans for interfaces extending `Repository`, and for
each one **generates an implementation class in memory** (a dynamic proxy), where every method
delegates to an `EntityManager` call. It then registers that generated object as a bean.

> **This is the concrete answer to "why can't I just `new` it" (section 6.8):** there is no class in
> your source tree to `new`. The implementation does not exist until Spring builds it at startup.

**The three layers, summarised:**

| Layer | Removes | You still write |
|---|---|---|
| **JDBC** | The wire protocol | All the SQL, all the field↔column copying |
| **+ Hibernate** | The SQL and the mapping | A DAO class per entity |
| **+ Spring Data JPA** | The DAO classes | **An interface** |

### 10.5 "What if I never write an `@Entity` class?"

Nothing breaks — you simply get less. Here's exactly what happens at each level:

| What you have | What you get | What you lose |
|---|---|---|
| **No `@Entity` at all** | The app starts fine. Hibernate boots with zero mapped entities. `ddl-auto=validate` passes trivially (nothing to check). You can still use raw JDBC or `JdbcTemplate` and write SQL by hand. | Every ORM benefit. No repositories, no dirty checking, no object mapping. |
| **`@Entity` classes, no repositories** | Hibernate maps and validates them against the schema | Convenience methods — you'd use `EntityManager` directly |
| **`@Entity` + repository interfaces** | The full stack | — |

**Concretely for you right now:** your project has **zero entities**, and that is exactly why
`ddl-auto=validate` currently has nothing to do. Your seven tables exist in Postgres and Hibernate is
completely unaware of them. **A table only becomes visible to your Java code once you write an
`@Entity` for it.** Writing `Hotel` is step 5.7 — and the moment you do, `validate` starts checking it
against the real `hotels` table.

**Important: an entity is not automatic.** Hibernate does not scan your database and generate classes.
You write each `@Entity` by hand, one per table you want to use. Seven tables → seven entity classes,
eventually.

### 10.6 The two directions — who owns the schema? *(you asked this precisely)*

You worked this out yourself: *"either we create the classes and Hibernate converts them into the
schema and updates the database, or we write the schema separately and only talk to it."*

**That is exactly the choice, and it has names.**

```
  ┌── CODE-FIRST (entity-first) ──────────────────────────────────────────────┐
  │                                                                           │
  │    You write:   @Entity class Booking { ... }                             │
  │                          │                                                │
  │                          │  ddl-auto = update / create                    │
  │                          ▼                                                │
  │    Hibernate GENERATES:  create table booking (...);                      │
  │                          alter table booking add column ...;              │
  │                                                                           │
  │    The Java class is the source of truth. The DB follows.                 │
  └───────────────────────────────────────────────────────────────────────────┘

  ┌── SCHEMA-FIRST (database-first) ─────────────────  ✅ WHAT WE ARE DOING ──┐
  │                                                                           │
  │    You write:   docs/db/schema.sql   ← version-controlled, reviewed       │
  │                          │                                                │
  │                          │  applied by you (psql / pgAdmin / later Flyway)│
  │                          ▼                                                │
  │                     PostgreSQL                                            │
  │                          ▲                                                │
  │                          │  ddl-auto = validate                           │
  │                          │  Hibernate only CHECKS. It never writes DDL.   │
  │    You write:   @Entity class Booking { ... }  ← must MATCH the table     │
  │                                                                           │
  │    The SQL file is the source of truth. The Java must conform.            │
  └───────────────────────────────────────────────────────────────────────────┘
```

**Your reasoning for schema-first was right, and here is the sharpest form of it:**

Hibernate's generated DDL is *adequate*, never *good*. It cannot express the things that actually
matter in your schema:

| In your `schema.sql` | Can `ddl-auto=update` generate it? |
|---|---|
| `CHECK (check_out_date > check_in_date)` | ❌ No |
| `CONSTRAINT uq_room_per_hotel UNIQUE (hotel_id, room_number)` | ⚠️ Only with extra annotations, awkwardly |
| Composite index `(hotel_id, room_id, check_in_date, check_out_date)` | ⚠️ Clumsily, via `@Index` |
| Postgres `ENUM` types (`booking_status`) | ❌ No — it'd use varchar |
| `bigint GENERATED ALWAYS AS IDENTITY` | ❌ No — it'd use a sequence |
| **The `EXCLUDE` constraint that prevents double-booking** | ❌ **Absolutely not** |

And the killer: **`update` never removes anything.** Rename a field and you get a *new* column beside
the old one, silently, forever. Delete an entity and the table stays. After six months nobody knows
which columns are real. That's schema drift, and it's how production databases rot.

**So yes — your conclusion is correct, and it's the professional default.** Code-first is fine for a
prototype or a demo. Every serious system owns its schema explicitly, in files, under version control.
Ours does. `validate` is how we get Hibernate to *verify* our decision instead of *making* it.

*(The one upgrade later: replacing the single `schema.sql` with **Flyway** migrations, so schema
*changes* are versioned too, not just the current state. Same philosophy, better mechanics — see the
anatomy doc, section 28②.)*

### 10.7 What actually happens when you call `repo.save(booking)`

You guessed *"there will be some query written with a prepared statement, and then the values of the
parameters get injected."* **That guess is correct** — that is exactly the mechanism. Here it is in
full:

```
  YOUR CODE
     repo.save(booking);
        │
        ▼
  SPRING DATA JPA  — the generated implementation of your interface.
     "save() means: if the id is null → persist (INSERT); else → merge (UPDATE)."
        │
        ▼
  HIBERNATE  — reads your @Entity annotations to know the table and column names,
     and builds a SQL string with ? placeholders instead of values:

        insert into hotel_bookings (hotel_id, guest_id, room_id, check_in_date, ...)
        values (?, ?, ?, ?, ...)
        │
        ▼
  JDBC  — creates a PreparedStatement from that string, then BINDS each value
     into its numbered slot:

        ps.setLong(1, 7);                        binding parameter [1] = 7
        ps.setLong(2, 42);                       binding parameter [2] = 42
        ps.setObject(4, LocalDate.of(2026,8,5)); binding parameter [4] = 2026-08-05
        │
        ▼
  POSTGRES DRIVER  — sends the statement and the values over TCP to port 5433
        │
        ▼
  POSTGRESQL  — plans and executes it, enforces your constraints, returns the new id
```

**Why `?` placeholders instead of just building the string?** Two reasons, both important:

1. **SQL injection is impossible.** A prepared statement sends the *query structure* and the *values*
   as **separate things**. Postgres parses the SQL before it ever sees the values, so a value can
   never become SQL. If a guest's name is `Robert'); DROP TABLE hotel_bookings;--`, it is stored as a
   name — a harmless 45-character string. With string concatenation, it would execute.
2. **Speed.** The database can parse and plan `insert ... values (?,?,?)` **once** and reuse that plan
   for thousands of different bookings.

**This is exactly what your logging settings show you.** With `show-sql=true` you see the statement
with `?`; with `logging.level.org.hibernate.orm.jdbc.bind=TRACE` you also see the values going into
the slots:

```
  Hibernate:
      insert into hotel_bookings (check_in_date, check_out_date, guest_id, ...)
      values (?, ?, ?, ?, ?)
  TRACE ... binding parameter [1] as [DATE]   - [2026-08-05]     ← the actual value
  TRACE ... binding parameter [2] as [DATE]   - [2026-08-09]
  TRACE ... binding parameter [3] as [BIGINT] - [42]
```

So yes — **a "bound parameter" is the real value that gets slotted into a `?`.** Without the TRACE
line you would only ever see `?` and have no idea what was actually sent. That is why it's worth
turning on while learning.

### 10.8 Transactions and `@Transactional`

**A transaction is a group of SQL statements that either ALL take effect or NONE do.**

```sql
BEGIN;                                     -- open
  INSERT INTO hotel_bookings (...) ...;
  INSERT INTO payments (...) ...;
COMMIT;                                    -- both become permanent, atomically
-- or --
ROLLBACK;                                  -- both erased; the DB is as if neither ran
```

Between `BEGIN` and `COMMIT`, your changes exist **only for your connection**. Nobody else can see
them. That property is what makes "check then insert" safe to reason about.

#### ACID, concretely

| Letter | Means | In your booking |
|---|---|---|
| **A**tomicity | All or nothing | Booking + payment row both land, or neither does |
| **C**onsistency | Constraints hold at commit | `CHECK (check_out > check_in)` cannot be violated |
| **I**solation | Concurrent transactions don't see each other's uncommitted work | Thread 2 can't see thread 1's half-finished booking |
| **D**urability | Committed = survives a crash | Written to the WAL and fsynced before COMMIT returns |

**Isolation is the one this project turns on.** It's not binary — Postgres has levels
(`READ COMMITTED` is the default, then `REPEATABLE READ`, then `SERIALIZABLE`), and the level decides
exactly *how much* of another transaction's work you can see. That's step 7 of the roadmap.

#### What `@Transactional` actually does

```java
@Service
class BookingService {

    @Transactional                                   // ← BEGIN before, COMMIT after
    Booking bookRoom(Long roomId, LocalDate in, LocalDate out) {
        if (repo.overlaps(roomId, in, out)) throw new RoomUnavailableException();
        Booking b = repo.save(new Booking(...));
        paymentRepo.save(new Payment(b, PENDING));
        return b;
    }                                                // ← COMMIT here (or ROLLBACK if it threw)
}
```

**The mechanism is a proxy, and this is why it can silently fail.** Spring does not hand your
controller a reference to your `BookingService` object. It generates a subclass that wraps it:

```
   controller's field ──►  BookingService$$SpringProxy@1a2b        ← what you actually hold
                                 │
                                 │  1. txManager.begin()   → conn.setAutoCommit(false)
                                 │  2. super.bookRoom(...) → YOUR object, at a different address
                                 │  3. txManager.commit()  → conn.commit()
                                 │     (on RuntimeException → conn.rollback())
                                 ▼
                           BookingService@3c4d                     ← your actual object
```

**Three consequences that catch everyone:**

| Situation | Result |
|---|---|
| `new BookingService()` | No proxy exists → `@Transactional` does **nothing**, silently |
| One method in the class calls another `@Transactional` method via `this.other()` | **No transaction** — `this` is your object, not the proxy. The call never crosses the boundary. |
| The method throws a *checked* exception | **No rollback by default.** Spring rolls back on `RuntimeException`/`Error` only. Use `@Transactional(rollbackFor = ...)` to change it. |

**Where it belongs:** on the **service**, not the controller and not the repository. A transaction
should wrap one *business operation*. Put it on the repository and each individual `save()` becomes
its own transaction — which reopens the exact gap two concurrent threads use to double-book.

> **This is the single most important reason the layered architecture isn't just tidiness
> (section 6.13).** The service layer is physically where your correctness boundary lives.

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

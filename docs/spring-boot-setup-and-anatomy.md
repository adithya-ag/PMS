# Spring Boot — Project Setup & Anatomy (Hotel PMS)

**What this doc is for:** you have a freshly generated Spring Boot project sitting in your repo and you
have not touched it yet. This file explains *what it is*, *where every file came from*, *what Spring Boot
has already decided for you*, and *what is left for you to decide* — **before** we change a single line.

**How to use it:** read Part 0 first (the map). Then read Parts 1–7 in order. Part 8 (modularization /
microservices) is forward-looking — read it when you're curious, it doesn't block the build. Part 9 is
the checklist we'll actually work through together.

> **Nothing in this document has been applied to your project.** Where a decision is needed, it is
> presented as a *choice with trade-offs*. You make the call; then we edit.

---

## Contents

**Part 0 — The map**
1. [Where we are, and what this phase covers](#1-where-we-are-and-what-this-phase-covers)

**Part 1 — Starting a Spring Boot project**
2. [What Spring Initializr actually is](#2-what-spring-initializr-actually-is)
3. [The decisions you make before generating](#3-the-decisions-you-make-before-generating)
4. [Your CLI command, decoded](#4-your-cli-command-decoded)

**Part 2 — Nomenclature**
5. [What a package is, and reverse-domain naming (`com.pms`)](#5-reverse-domain-naming-why-compms)
6. [GAV: groupId, artifactId, version](#6-gav-groupid-artifactid-version)
7. [groupId vs packageName vs name](#7-groupid-vs-packagename-vs-name)
8. [Java naming conventions cheat sheet](#8-java-naming-conventions-cheat-sheet)

**Part 3 — The generated project**
9. [Every file in the zip, explained](#9-every-file-in-the-zip-explained)
10. [What Spring Boot has already done for you](#10-what-spring-boot-has-already-done-for-you)

**Part 4 — The directory layout**
11. [The Maven Standard Directory Layout](#11-the-maven-standard-directory-layout)
12. [`main` vs `test`, `java` vs `resources`](#12-main-vs-test-java-vs-resources)
13. [`target/` and the component-scan rule](#13-target-and-the-component-scan-rule)

**Part 5 — Maven, the build tool**
14. [What a build tool does](#14-what-a-build-tool-does)
15. [The build lifecycle: phases vs goals](#15-the-build-lifecycle-phases-vs-goals)
16. [The local repository `~/.m2`](#16-the-local-repository-m2)
17. [`mvn` vs `./mvnw` — the wrapper](#17-mvn-vs-mvnw--the-wrapper)

**Part 6 — Reading `pom.xml`**
18. [XML in 60 seconds](#18-xml-in-60-seconds)
19. [The skeleton of a POM](#19-the-skeleton-of-a-pom)
20. [Your POM, section by section](#20-your-pom-section-by-section)
21. [The parent POM and why versions are missing](#21-the-parent-pom-and-why-versions-are-missing)
22. [Dependency scopes](#22-dependency-scopes)
23. [Transitive dependencies](#23-transitive-dependencies)
24. [Plugins and `spring-boot-maven-plugin`](#24-plugins-and-spring-boot-maven-plugin)
25. [Decisions open in YOUR pom.xml](#25-decisions-open-in-your-pomxml)

**Part 7 — `application.properties`**
26. [What it is and when it's read](#26-what-it-is-and-when-its-read)
27. [Property precedence](#27-property-precedence)
28. [Decisions open in YOUR application.properties](#28-decisions-open-in-your-applicationproperties)

**Part 8 — Modularization & microservices**
29. [Package-by-layer vs package-by-feature](#29-package-by-layer-vs-package-by-feature)
30. [The modular monolith](#30-the-modular-monolith)
31. [Your auto-scaling example, worked through](#31-your-auto-scaling-example-worked-through)
32. [What breaks when you split](#32-what-breaks-when-you-split)
33. [When to actually split](#33-when-to-actually-split)

**Part 9 — The roadmap**
34. [Checklist for this phase](#34-checklist-for-this-phase)

---

# Part 0 — The map

## 1. Where we are, and what this phase covers

The project roadmap (from `CLAUDE.md`):

```
  1. Scope            ✅ done
  2. PRD              ✅ done
  3. ERD              ✅ done
  4. Schema (DDL)     ✅ done — applied to native PG (5432) and Docker PG (5433)
  5. PROJECT SETUP    ◀── YOU ARE HERE
  6. Basic CRUD APIs
  7. Booking deep-dive (concurrency, transactions, locking)
  8. Backlog
```

**Step 5 — "project setup" — is these seven things, in this order:**

| # | Step | What you learn |
|---|---|---|
| 5.1 | Understand the generated project | Directory layout, Maven, what Boot pre-decided |
| 5.2 | Reconcile `.gitignore` | What belongs in version control and what doesn't |
| 5.3 | Read and tidy `pom.xml` | Dependencies, scopes, the parent POM, plugins |
| 5.4 | Decide your package structure | **Modularization** — the decision that shapes everything after |
| 5.5 | Write `application.properties` | Datasource, schema ownership, logging, secrets |
| 5.6 | Start the app against Postgres | The startup log, the connection pool, Hibernate booting |
| 5.7 | First `@Entity` (`Hotel`) | JPA mapping, and `ddl-auto=validate` proving your schema |

**The working rule from here on:** *understand → decide → edit.* This document is the "understand"
half. The "decide" is yours. The "edit" is yours, with my instructions.

---

# Part 1 — Starting a Spring Boot project

## 2. What Spring Initializr actually is

**Spring Initializr is a code generator. That's all it is.**

- It is a web service run by the Spring team, living at `https://start.spring.io`.
- You give it a handful of answers (Java version, build tool, dependencies, names).
- It stamps out a **skeleton project** as a `.zip` and hands it back.
- **It runs once, at the beginning, and is never involved again.** It is not a framework, not a
  dependency, not something your app uses at runtime. Delete the zip and nothing breaks.

It is *not* magic and it is *not* required — you could hand-write every file it produces. It exists
because those files are boring, identical across every project, and easy to get subtly wrong.

**Three ways to reach it — all produce the identical zip:**

| Way | How | Notes |
|---|---|---|
| Web form | `start.spring.io` in a browser | Easiest to explore; shows all options |
| **HTTP API** | `curl` the same service | ← **what you did**; scriptable, reproducible |
| IDE | IntelliJ "New Spring Project", VS Code Spring extension | Same service, wrapped in a GUI |

You used the API. That was the better learning choice — every option was explicit and typed by you,
with nothing hidden behind a checkbox.

## 3. The decisions you make before generating

The Initializr form is really **eight decisions**. Understanding them is understanding why your
project looks the way it does.

| Decision | Options | What it affects |
|---|---|---|
| **Build tool** | Maven / Gradle | `pom.xml` vs `build.gradle`; the whole build system |
| **Language** | Java / Kotlin / Groovy | Source language |
| **Boot version** | e.g. 4.1.0 | Which Spring libraries, which starter *names* |
| **Java version** | 17 / 21 / 25 | Language features available; `<java.version>` in the POM |
| **groupId** | e.g. `com.pms` | Your organisation's identifier |
| **artifactId** | e.g. `hotel-pms` | This project's identifier; the JAR filename |
| **name** | e.g. `hotel-pms` | Derives the **main class name** |
| **packageName** | e.g. `com.pms.hotel` | The **folder structure** under `src/main/java` |
| **dependencies** | web, data-jpa, … | Which starters land in `pom.xml` |

Two of these — **`packageName`** and **`dependencies`** — are the ones that actually change how you
write code. The rest are naming and versioning.

## 4. Your CLI command, decoded

This is the command you ran (recorded in `docs/spring-and-maven-notes.md`):

```powershell
curl.exe -s -G "https://start.spring.io/starter.zip" `
  -d "type=maven-project" -d "language=java" -d "javaVersion=21" `
  -d "groupId=com.pms" -d "artifactId=hotel-pms" -d "name=hotel-pms" `
  -d "packageName=com.pms.hotel" `
  -d "dependencies=web,data-jpa,postgresql,validation" `
  -o starter.zip
```

**The mechanics of the command itself:**

| Piece | Meaning |
|---|---|
| `curl.exe` | The real curl. In PowerShell, bare `curl` is an *alias* for `Invoke-WebRequest`, which takes different flags — hence `.exe`. |
| `-s` | Silent: no progress bar |
| `-G` | Send the `-d` values as **URL query parameters** on a GET, instead of a POST body |
| `-d "k=v"` | One parameter |
| `-o starter.zip` | Write the response to this file |
| `` ` `` | PowerShell's line-continuation character (bash uses `\`) |

**Now the important part — which flag produced which file.** This is the answer to "how did the app
suddenly get named `HotelPmsApplication`, and where did `com/pms/hotel` come from":

```
  -d "type=maven-project"  ────────────►  pom.xml, mvnw, mvnw.cmd, .mvn/
                                          (had you said gradle-project:
                                           build.gradle, gradlew instead)

  -d "javaVersion=21"      ────────────►  pom.xml:  <java.version>21</java.version>

  -d "groupId=com.pms"     ────────────►  pom.xml:  <groupId>com.pms</groupId>
  -d "artifactId=hotel-pms"────────────►  pom.xml:  <artifactId>hotel-pms</artifactId>

  -d "packageName=com.pms.hotel" ──────►  the FOLDERS:
                                            src/main/java/com/pms/hotel/
                                            src/test/java/com/pms/hotel/
                                          and the first line of every .java file:
                                            package com.pms.hotel;

  -d "name=hotel-pms"      ────────────►  "hotel-pms"
                                            → strip separators, PascalCase → "HotelPms"
                                            → append "Application"       → HotelPmsApplication
                                          giving:  HotelPmsApplication.java
                                                   HotelPmsApplicationTests.java
                                          and also  pom.xml: <name>hotel-pms</name>
                                                    application.properties:
                                                      spring.application.name=hotel-pms

  -d "dependencies=web,data-jpa,postgresql,validation"
                           ────────────►  the four <dependency> blocks in pom.xml
                                          (+ their matching test starters)
```

**Every generated name traces back to a flag you typed.** Nothing was invented by the tool.

⚠️ **One thing to notice:** you asked for `dependencies=web`, but your `pom.xml` says
`spring-boot-starter-webmvc`. Initializr translated the short ID `web` into whatever that starter is
*called in Boot 4.1.0*. See §20 — this matters when you Google things.

---

# Part 2 — Nomenclature

## 5. Reverse-domain naming: why `com.pms`

### 5.0 First — what *is* a package? (in plain terms)

**A package is a folder for your Java classes, plus a namespace.** That's genuinely it. But both
halves matter.

**Half 1 — it's a folder.** A `package` declaration and a directory are the same thing:

```java
package com.pms.hotel.booking;        // ← the first line of the file
public class Booking { }
```
```
   src/main/java/com/pms/hotel/booking/Booking.java     ← must live exactly here
```

Java **enforces** this. Declare a package and put the file elsewhere and it won't compile. One folder
per dot, always.

**Half 2 — it's a namespace (this is the part that matters).** A class's real name is
**package + class name**, called its *fully-qualified name*:

```
   com.pms.hotel.booking.Booking        ← the class's actual, full identity
   └────── package ─────┘ └ class ┘
```

**Why that's necessary — a concrete example.** Every real project ends up with name collisions:

```
   java.util.Date              ← the old JDK date class
   java.sql.Date               ← the JDBC date class, DIFFERENT and incompatible
   com.pms.hotel.booking.Date  ← if you wrote your own
```

Three classes, all called `Date`. Without packages the JVM could not tell them apart and they simply
could not coexist on one classpath. With packages, all three are unambiguous. **This is why you can
put 50 libraries in one app without their class names fighting.**

**Three practical things packages do for you:**

| | What it means | Example in this project |
|---|---|---|
| **1. Organisation** | Related classes sit together; you can find things | `booking/` holds everything about bookings |
| **2. Name collision avoidance** | Full name = package + class | `com.pms.hotel.room.Room` vs some library's `Room` |
| **3. Access control** | A member with *no* modifier is **package-private** — visible only inside its own package | Lets a feature hide its internals from other features |

That third one is the underrated one, and it's the mechanism behind §29–30:

```java
package com.pms.hotel.booking;

public  class BookingController { }   // PUBLIC  → any package can use it
        class BookingValidator  { }   // no modifier → package-private:
                                      //   only com.pms.hotel.booking can see it.
                                      //   com.pms.hotel.room CANNOT touch it.
```

**That is a real, compiler-enforced boundary.** It's how a package becomes a *module* rather than just
a folder — and it only works if you group by feature, not by layer (§29).

**"How is this used in actual production environments?"** Exactly as above, at larger scale. A real
codebase might have:

```
   com.acme.payments.card        ← team A owns this; internals hidden
   com.acme.payments.wallet      ← team A
   com.acme.orders.checkout      ← team B; can only use payments' PUBLIC classes
   com.acme.orders.fulfilment    ← team B
```

Packages are how large teams keep out of each other's code, how libraries avoid clashing, and how you
draw the lines that later become microservice boundaries.

### 5.1 Why reverse-domain?

**The problem it solves:** Java loads classes by their **fully-qualified name** — package + class. If
two libraries on your classpath both contain a class called `Booking`, the JVM must be able to tell
them apart. `com.pms.hotel.Booking` and `com.acme.travel.Booking` are unambiguous. Bare `Booking`
twice is a collision.

**The convention:** take a domain name you control and **reverse it**.

```
   rightturn.co.in        →   in.co.rightturn
   google.com             →   com.google
   apache.org             →   org.apache
```

**Why reversed?** Domain names go *specific → general* left to right (`mail.google.com`). Package
names go *general → specific* (`com.google.mail`). Reversing lines them up so that everything owned by
one organisation shares a common **prefix**, and folders nest naturally:

```
   com/
    └── pms/            ← everything this org owns lives under here
         └── hotel/     ← this particular application
```

**Why it works:** domain names are globally unique by definition — ICANN guarantees it. So borrowing
them gives Java globally unique package names for free, with no central registry.

**Your case:** `com.pms` is not a domain you own. **That is completely fine for a learning project**
and extremely common. `com.example` is the official placeholder. The convention only becomes a hard
requirement when you publish a library to Maven Central — then you must prove domain ownership.

**Rules for package names (enforced or strongly conventional):**
- **all lowercase** — `com.pms.hotel`, never `com.PMS.Hotel`. (Some filesystems are
  case-insensitive; mixed case breaks portably.)
- **no hyphens or underscores** — hyphens are illegal in Java identifiers. This is precisely why your
  `artifactId` is `hotel-pms` (hyphen fine) but your package is `com.pms.hotel` (no hyphen).
- **no Java keywords** — you cannot have a package segment called `int`, `new`, `class`.
- **singular nouns** by convention — `com.pms.hotel.booking`, not `bookings`.

## 6. GAV: groupId, artifactId, version

Maven identifies **every** library in the world — including yours — by three values, universally
called **GAV coordinates**:

```
   <groupId>com.pms</groupId>              ← WHO made it     (the organisation)
   <artifactId>hotel-pms</artifactId>      ← WHICH project   (the thing itself)
   <version>0.0.1-SNAPSHOT</version>       ← WHICH release
```

That triple is a globally unique address. It's how `spring-boot-starter-data-jpa` is found, and it's
how *your* project would be found if you published it.

```
   groupId    : artifactId                 : version
   org.postgresql : postgresql             : 42.7.3
   com.pms        : hotel-pms              : 0.0.1-SNAPSHOT
```

**On `-SNAPSHOT`:** this suffix means "**in development, not released, contents may change**". Maven
treats snapshots specially — it will re-download them, because the same version number can have
different contents. A version *without* `-SNAPSHOT` (like `1.0.0`) is a **release**: immutable
forever, cached and never re-fetched. You go `0.0.1-SNAPSHOT → 1.0.0 → 1.0.1-SNAPSHOT → …`.

**Version numbering** is usually [Semantic Versioning](https://semver.org): `MAJOR.MINOR.PATCH`
— major = breaking change, minor = new feature (compatible), patch = bug fix.

## 7. groupId vs packageName vs name

Three similar-looking things that do three different jobs. This is the part that confuses everyone:

| Field | Your value | Lives in | Governs | Can you change it later? |
|---|---|---|---|---|
| `groupId` | `com.pms` | `pom.xml` | **Maven** identity — how the *build system* names your artifact | Easily — one line |
| `packageName` | `com.pms.hotel` | folders + `package` statements | **Java** identity — how the *compiler and JVM* name your classes | Painful — rename folders + every file's first line |
| `name` | `hotel-pms` | `pom.xml` | Human-readable label; **derived the main class name** | Easily, but the class name won't auto-rename |

**Key insight: `groupId` and `packageName` are independent.** They *conventionally* match or nest —
which is why `com.pms` (group) and `com.pms.hotel` (package) look related — but nothing enforces it.
Maven doesn't read your Java packages; Java doesn't read your POM.

The common convention is `packageName = groupId + "." + something`:
- `groupId = com.pms` → the organisation
- `packageName = com.pms.hotel` → this specific app within the organisation

Which is exactly what you chose. That leaves room for `com.pms.billing`, `com.pms.crm` later without
collision — a small but real bit of forward thinking.

## 8. Java naming conventions cheat sheet

These are not compiler rules (mostly) — they are universal community conventions. Violating them is
legal and marks code as amateur.

| Thing | Convention | Example |
|---|---|---|
| **Package** | all lowercase, dots, no separators | `com.pms.hotel.booking` |
| **Class / Interface / Enum** | `PascalCase`, **singular noun** | `Booking`, `HotelRepository`, `RoomStatus` |
| **Method** | `camelCase`, **starts with a verb** | `findByCity()`, `isAvailable()`, `createBooking()` |
| **Variable / field** | `camelCase`, noun | `checkInDate`, `roomNumber` |
| **Constant** | `UPPER_SNAKE_CASE` | `MAX_STAY_NIGHTS` |
| **Enum constant** | `UPPER_SNAKE_CASE` | `CHECKED_IN`, `MAINTENANCE` |
| **Generic type param** | single capital | `T`, `E`, `K`, `V` |
| **Boolean getter** | `is…` / `has…` | `isCancelled()`, `hasPayment()` |
| **Test class** | `<ClassUnderTest>Test` or `…Tests` | `BookingServiceTest` |
| **Maven artifactId** | `kebab-case` (hyphens legal here!) | `hotel-pms` |
| **File name** | **must exactly match** the public class | `Booking.java` ↔ `class Booking` |

**Spring-specific layer suffixes** — conventional, and they make code navigable at a glance:

| Suffix | Role | Example |
|---|---|---|
| `…Controller` | HTTP endpoint | `BookingController` |
| `…Service` | Business logic | `BookingService` |
| `…Repository` | Database access | `BookingRepository` |
| `…Dto` / `…Request` / `…Response` | Data-transfer objects | `CreateBookingRequest` |
| `…Config` | Configuration class | `SecurityConfig` |
| `…Exception` | Custom exception | `RoomUnavailableException` |

⚠️ **A convention that will bite you:** your database uses `snake_case` (`check_in_date`,
`hotel_bookings`). Java uses `camelCase` (`checkInDate`). These are *different worlds* and both are
correct in their own world. Hibernate bridges them with a **naming strategy** that converts
`checkInDate → check_in_date` automatically. We'll confirm this works rather than assume it — that's
part of what `ddl-auto=validate` will prove.

---

# Part 3 — The generated project

## 9. Every file in the zip, explained

This is exactly what `starter.zip` contained and what now sits in your repo:

```
  hotel-pms/
  ├── .gitattributes                     Git config — line endings
  ├── .gitignore                         Git config — what not to commit
  ├── .mvn/
  │   └── wrapper/
  │       └── maven-wrapper.properties   Which Maven version the wrapper downloads
  ├── mvnw                               Maven wrapper script (Linux/macOS)
  ├── mvnw.cmd                           Maven wrapper script (Windows)
  ├── HELP.md                            Boilerplate links from Initializr
  ├── pom.xml                            THE BUILD FILE
  └── src/
      ├── main/
      │   ├── java/com/pms/hotel/
      │   │   └── HotelPmsApplication.java      THE ENTRY POINT
      │   └── resources/
      │       └── application.properties        RUNTIME CONFIG
      └── test/
          └── java/com/pms/hotel/
              └── HotelPmsApplicationTests.java  A smoke test
```

**Ten files. That's the whole thing.** Now each one:

### `.gitattributes`
- **What:** a Git configuration file (nothing to do with Java or Spring).
- **Its job here:** **line-ending normalisation**. Windows ends lines with `CRLF`, Unix with `LF`.
  The `mvnw` shell script *must* keep `LF` endings or it will not run on Mac/Linux. This file tells
  Git to leave it alone.
- **Edit it?** No.
- **Commit it?** Yes.
- *(This is also the source of those `LF will be replaced by CRLF` warnings you see from Git.)*

### `.gitignore`
- **What:** the list of paths Git should never track.
- **Initializr's version** ignores `target/`, IDE folders (`.idea/`, `.vscode/`, `.settings`), and
  `HELP.md`.
- **The conflict:** your repo **already had** a `.gitignore` (you wrote it earlier, covering secrets,
  Docker volumes, `CLAUDE.md`, etc). Two files now want to be `.gitignore`. **Reconciling these is
  step 5.2** and there's one real trap in it — see §25's note on `.mvn/`.
- **Edit it?** Yes — this is one of the first things we'll do together.

### `.mvn/wrapper/maven-wrapper.properties`
- **What:** three lines that pin the Maven version:
  ```
  wrapperVersion=3.3.4
  distributionType=only-script
  distributionUrl=https://repo.maven.apache.org/maven2/.../apache-maven-3.9.16-bin.zip
  ```
- **Its job:** when you run `./mvnw`, the script reads this file, downloads *exactly* Maven 3.9.16 if
  it's not already cached, and runs your build with it.
- **Edit it?** Only to deliberately upgrade Maven.
- **Commit it?** **Yes — critically.** This file *is* the reproducibility guarantee. See §17.

### `mvnw` and `mvnw.cmd`
- **What:** the wrapper scripts themselves. `mvnw` = shell script for Linux/macOS. `mvnw.cmd` = batch
  script for Windows (**this is the one you'll run**).
- **Edit them?** Never. Generated code.
- **Commit them?** Yes.

### `HELP.md`
- **What:** a Markdown file of links to Spring reference docs for the starters you picked.
- **Value to you:** near zero — you have far better project-specific docs in `docs/`.
- **Note:** Initializr's own `.gitignore` lists `HELP.md`, i.e. *even Spring expects you to not commit
  it.* Keep it or delete it, your call.

### `pom.xml`
- **What:** the **Project Object Model** — Maven's configuration file. The single most important file
  in the project after your source code.
- **Its job:** declares your identity (GAV), your Java version, your **dependencies**, and your build
  **plugins**.
- **Edit it?** Yes, regularly — every time you add a library.
- **All of Part 6 is about reading this file.**

### `src/main/java/com/pms/hotel/HotelPmsApplication.java`
- **What:** the entry point. Thirteen lines:
  ```java
  package com.pms.hotel;

  import org.springframework.boot.SpringApplication;
  import org.springframework.boot.autoconfigure.SpringBootApplication;

  @SpringBootApplication
  public class HotelPmsApplication {
      public static void main(String[] args) {
          SpringApplication.run(HotelPmsApplication.class, args);
      }
  }
  ```
- **`public static void main`** — the standard Java entry point. Every Java program has one. Your
  Spring app is, underneath everything, an ordinary Java program.
- **`SpringApplication.run(...)`** — hands control to Spring. This one call creates the
  `ApplicationContext`, scans for your beans, runs auto-configuration, and starts Tomcat.
- **`@SpringBootApplication`** — "the switch". **Turns on *what*, exactly?** It is literally three
  annotations bundled into one, and each turns on one specific thing:

  | The annotation inside it | What it switches on | Concretely, for you |
  |---|---|---|
  | `@ComponentScan` | **Find my classes.** Scan this package and everything below it for `@Service`, `@Repository`, `@RestController`, `@Component` — and make each one a bean. | Your `BookingService` becomes a managed object without you registering it anywhere. **This is why everything must live under `com.pms.hotel`** — see §13. |
  | `@EnableAutoConfiguration` | **Find my libraries.** Look at what's on the classpath and configure it with sensible defaults. | Sees Tomcat → starts a web server on 8080. Sees a JDBC driver + your `spring.datasource.url` → builds a HikariCP connection pool. Sees Hibernate → builds an `EntityManagerFactory`. **You wrote none of that.** |
  | `@SpringBootConfiguration` | **This class may itself define beans.** Marks it as a configuration source. | Rarely matters to you; it's what lets you add `@Bean` methods here later. |

  Without this one annotation you would be hand-writing hundreds of lines of configuration to get a
  web server, a connection pool, JSON conversion, and transaction management. *(Full detail:
  `docs/how-it-all-works.md` §6.11 — the startup sequence.)*
- **Edit it?** Rarely. It stays this small in most projects.

### `src/main/resources/application.properties`
- **What:** runtime configuration. Currently **one line**: `spring.application.name=hotel-pms`.
- **Its job:** override Spring Boot's defaults — database URL, port, logging levels, and much more.
- **Edit it?** Yes — heavily. **All of Part 7 is about this file.**

### `src/test/java/com/pms/hotel/HotelPmsApplicationTests.java`
- **What:** a single "smoke test":
  ```java
  @SpringBootTest
  class HotelPmsApplicationTests {
      @Test
      void contextLoads() { }
  }
  ```
- **What it tests:** the method body is *empty on purpose*. `@SpringBootTest` boots the **entire**
  application context. If any bean fails to construct — a missing dependency, a bad property, an
  entity that doesn't match the schema — **the test fails before the empty body ever runs**.
- **Why that's clever:** it's a free, zero-maintenance check that your whole wiring is sane.
- ⚠️ **Consequence for us:** once you configure a datasource, this test will try to **connect to
  Postgres**. If Postgres is down, this test fails. That's expected, and it's a decision point we'll
  handle later.

## 10. What Spring Boot has already done for you

An honest accounting — this is the "what did I get for free" list:

**✅ Already done:**

| Done for you | Instead of you doing |
|---|---|
| Curated, compatible versions for ~400 libraries | Hand-picking versions that don't conflict |
| A build file with 4 dependencies resolved transitively | Downloading dozens of JARs manually |
| A pinned Maven version via the wrapper | "works on my machine" build differences |
| A working `main()` and entry point | Understanding the Spring bootstrap sequence to start |
| Standard directory layout | Inventing your own and configuring Maven to find it |
| Embedded Tomcat inside the app | Installing and configuring a separate Tomcat server |
| Auto-configuration ready to fire | Hundreds of lines of XML/Java config for MVC, JPA, JSON |
| A smoke test that validates all wiring | Writing your own context-load check |

**❌ NOT done — all still yours:**

- No database connection (the driver is on the classpath; the URL is not set)
- No entities, repositories, services, or controllers — **zero business logic**
- No package structure beyond the single root package
- No `.gitignore` reconciliation with your existing one
- No decision on schema ownership (`ddl-auto`)
- No logging configuration, no profiles, no secret handling
- The app **will not currently start** — see below

⚠️ **Important thing to know before you try running it:** with `spring-boot-starter-data-jpa` on the
classpath and **no** `spring.datasource.url` configured, Spring Boot **fails at startup** with:

```
  Failed to configure a DataSource: 'url' attribute is not specified and no
  embedded datasource could be configured.
```

That is not a bug. Auto-configuration saw JPA + a JDBC driver, concluded "this app clearly wants a
database", found no address for one, and refused to start half-configured. **Failing loudly at
startup rather than at the first request is a deliberate design choice** — and a good one.

---

# Part 4 — The directory layout

## 11. The Maven Standard Directory Layout

Maven's defining philosophy is **convention over configuration**: rather than letting every project
invent its own layout and then describe it in config, Maven declares one standard layout and expects
you to follow it. Follow it and your `pom.xml` stays tiny.

```
  project-root/
  ├── pom.xml                    ← the build file, always at the root
  ├── src/
  │   ├── main/                  ← code that ships to production
  │   │   ├── java/              ← .java source files
  │   │   └── resources/         ← non-Java files (config, SQL, templates)
  │   └── test/                  ← code that NEVER ships
  │       ├── java/              ← test source files
  │       └── resources/         ← test-only config
  └── target/                    ← build OUTPUT (generated; never committed)
```

**The payoff:** any Java developer can clone any Maven project on earth and know instantly where
things are. That uniformity is worth more than the flexibility it costs.

## 12. `main` vs `test`, `java` vs `resources`

**Two independent splits, four folders.** Understanding *why* each split exists:

**Split 1 — `main` vs `test` (does it ship?)**

| | `src/main` | `src/test` |
|---|---|---|
| Compiled to | `target/classes` | `target/test-classes` |
| Packaged into the JAR? | **Yes** | **No** |
| Can see the other? | No | **Yes** — tests can use main code |
| Dependency scope | `compile`, `runtime` | also `test`-scoped ones |

The one-directional visibility is the whole point: your production code **cannot accidentally depend
on test code**, because at package time the test tree isn't there.

**Split 2 — `java` vs `resources` (is it code?)**

| | `src/main/java` | `src/main/resources` |
|---|---|---|
| Contains | `.java` files | everything else |
| Maven action | **compiles** to `.class` | **copies verbatim** |
| Your files | `HotelPmsApplication.java` | `application.properties` |

Both end up in `target/classes`, side by side. That's why Spring can find `application.properties` on
the **classpath** at runtime — it was copied there next to your compiled classes.

**Why the folders nest as `com/pms/hotel/`:** this isn't Maven, it's **Java itself**. The JVM requires
that a class declared `package com.pms.hotel;` live at path `com/pms/hotel/` relative to a classpath
root. One folder per package segment, always. So:

```
  src/main/java/com/pms/hotel/HotelPmsApplication.java
  └─ root ────┘└─ package ─┘└─ file (= class name) ┘
```

## 13. `target/` and the component-scan rule

**`target/`** is Maven's output folder. It holds compiled `.class` files, copied resources, and the
final JAR. It is **100% regenerable** from source — `./mvnw clean` deletes it, the next build recreates
it. **It must never be committed.** (Both your `.gitignore` and Initializr's already handle this.)

> ### ⚠️ Correcting a common (and important) misconception
>
> "`target/` is gitignored, therefore `src/` is what runs in production" — **this is backwards.**
>
> **Git-ignored ≠ not deployed.** Those are two completely unrelated things:
>
> | Question | Answer |
> |---|---|
> | What goes in **Git**? | **Source only.** `src/`, `pom.xml`, `mvnw`. |
> | What runs in **production**? | **The JAR.** Nothing else. Never `.java` files. |
>
> `target/` is excluded from Git because it is **generated output**, not because it's unwanted.
> Committing it would be like committing a photocopy alongside the original — it bloats the repo,
> conflicts on every merge, and goes stale the moment source changes. The build server simply
> **regenerates** it.
>
> **Java is a compiled language.** The JVM cannot run a `.java` file — it runs `.class` bytecode. So
> production *must* receive compiled output. There is no option to "deploy the source files."
>
> **What actually happens (the real pipeline):**
>
> ```
>    YOUR LAPTOP                 GIT/GITHUB              BUILD SERVER (CI)           PRODUCTION
>    ───────────                 ──────────              ─────────────────           ──────────
>    src/                        src/                    git clone
>    pom.xml        ── push ──►  pom.xml    ── pull ──►  ./mvnw package
>    target/  ❌ ignored         (no target/)                  │
>                                                              ▼
>                                                        target/hotel-pms.jar ──►  java -jar hotel-pms.jar
>                                                        (regenerated here)        (or inside a Docker image)
> ```
>
> **So, directly answering your question:** we absolutely *do* build and run the JAR in production —
> it is the **only** thing that runs there. And updating it is not "difficult": you push new source,
> CI rebuilds the JAR automatically, and the new one replaces the old. That's a normal deploy.
>
> **And your instinct about Tomcat was half-right, so let's sharpen it.** You said "our server won't
> have its own Tomcat for no reason":
>
> | | **Old way** (WAR, pre-Boot) | **Modern way** (fat JAR, what you have) |
> |---|---|---|
> | Server has Tomcat installed? | ✅ Yes — installed and configured separately | ❌ **No** |
> | What you deploy | a `.war` file dropped into Tomcat's folder | a single self-contained `.jar` |
> | Who starts the web server | the external Tomcat | **your JAR does** — Tomcat is *inside* it |
> | Run with | start Tomcat, it loads your WAR | `java -jar app.jar` |
> | Server needs | a JVM **+ Tomcat + config** | **a JVM. That's it.** |
>
> **Embedded Tomcat means the production server needs no Tomcat at all.** That's precisely why fat
> JARs won — one artifact, one command, no server setup, and it drops straight into a Docker image.
> Your app *is* the server.

**The component-scan rule** — the single most practically important consequence of this layout:

`@SpringBootApplication` includes `@ComponentScan`, which by default scans **the package containing
the annotated class, and every package below it.** Your main class is in `com.pms.hotel`, therefore:

```
  com.pms.hotel                    ← scan root (main class lives here)
   ├── com.pms.hotel.booking       ✅ scanned
   ├── com.pms.hotel.room          ✅ scanned
   └── com.pms.hotel.guest         ✅ scanned

  com.pms.other                    ❌ NOT scanned
  com.pms                          ❌ NOT scanned (it's ABOVE the root)
```

**Every `@Entity`, `@Repository`, `@Service`, and `@RestController` you write must live under
`com.pms.hotel`.** Put one outside and Spring will silently never create the bean — no error, just a
component that mysteriously doesn't exist. This is a classic beginner half-day of debugging.

*(This is also why the main class conventionally sits in the **root** package, above everything else —
its position defines the scan boundary.)*

---

# Part 5 — Maven, the build tool

## 14. What a build tool does

Compiling one Java file is `javac Hello.java`. Compiling a real project means: resolve 200 libraries
and *their* libraries, compile in the right order, run tests, package everything into a JAR with a
manifest. A build tool automates that. Maven does **three jobs**:

**1. Dependency management**
You *declare* what you need; Maven fetches it — plus everything it needs — from **Maven Central** (the
global public repository) and caches it locally.

**2. Build lifecycle**
A fixed sequence of standard steps (§15), so `compile`/`test`/`package` mean the same thing in every
Maven project ever written.

**3. Convention**
The standard directory layout (§11), so zero configuration is needed to find your source.

**Maven vs Gradle** (you chose Maven):

| | Maven | Gradle |
|---|---|---|
| Config format | XML (`pom.xml`) — declarative | Groovy/Kotlin DSL — a program |
| Style | Rigid, predictable | Flexible, scriptable |
| Learning curve | Gentler; one obvious way | Steeper; many ways |
| Build speed | Slower | Faster (caching, incremental) |
| Best for | **Learning**, standard apps | Large/complex/custom builds |

Maven was the right call: the rigidity means everything you learn transfers, and there's a single
obvious way to do everything.

## 15. The build lifecycle: phases vs goals

Maven's default lifecycle is an **ordered sequence of phases**. Running any phase runs **every phase
before it**.

```
  validate → compile → test → package → verify → install → deploy
     │          │        │       │         │        │         │
     │          │        │       │         │        │         └─ upload to a remote repo
     │          │        │       │         │        └─ copy JAR into your local ~/.m2
     │          │        │       │         └─ run integration checks
     │          │        │       └─ bundle target/classes into a JAR
     │          │        └─ run unit tests (src/test)
     │          └─ compile src/main/java → target/classes
     └─ sanity-check the project structure
```

So `./mvnw package` **also** compiles and tests — you don't chain them yourself.

### First — what is a plugin? (this has to come before phases vs goals)

**Maven's core does almost nothing.** It knows the *order* of steps and how to download JARs. It
cannot compile Java. It cannot run tests. It cannot build a JAR.

**A plugin is a JAR containing the code that actually does one of those jobs.** Maven downloads
plugins from Maven Central exactly like any other dependency.

```
   MAVEN CORE  =  a manager with a schedule and an empty toolbox
                  "at 9am, someone compiles. at 10am, someone tests."
                  ...but it owns no tools.

   PLUGINS     =  the tools
                  maven-compiler-plugin   → knows how to run javac
                  maven-surefire-plugin   → knows how to run JUnit
                  maven-jar-plugin        → knows how to zip a JAR
                  spring-boot-maven-plugin→ knows how to build a FAT jar & run the app
```

You have never configured the first three, yet compiling works — because **the parent POM (§21)
already declared and configured them for you.** That's a large part of what "the parent gives you
sensible defaults" means.

### Now: phase vs goal

- A **phase** is a *stage* in the schedule: `compile`, `test`, `package`. Generic. Owned by Maven.
- A **goal** is *one specific task a plugin can perform*, written `plugin:goal` —
  `compiler:compile`, `surefire:test`, `spring-boot:run`, `dependency:tree`. Specific. Owned by a plugin.
- **Binding** connects the two: a plugin says *"run my `compile` goal during the `compile` phase."*

```
   PHASE (when)              BINDING              GOAL (what actually runs)
   ────────────                                   ─────────────────────────
   compile        ◄──── bound to ────  maven-compiler-plugin : compile
   test           ◄──── bound to ────  maven-surefire-plugin : test
   package        ◄──── bound to ────  maven-jar-plugin      : jar
                                    +  spring-boot-maven-plugin : repackage   ← makes it a FAT jar
```

**So your understanding was close.** You said *"a particular sort of lifecycle is what we want to
achieve."* Sharpen it to: **the phase is the *slot in time*; the goal is the *work*; binding is what
puts a specific piece of work into a specific slot.** The phase itself does nothing at all — it's an
empty appointment until a plugin's goal is bound to it.

**This is why goals can also be run directly, with no phase involved:**
```powershell
.\mvnw.cmd spring-boot:run       # ← runs ONE goal. No lifecycle, no phases before it.
.\mvnw.cmd package               # ← runs a PHASE, which triggers every phase before it,
                                 #   and every goal bound to each of them.
```
That is the practical difference between the two kinds of command in the table below.

**`clean`** is technically a separate lifecycle — it just deletes `target/`. Hence the common
`./mvnw clean package` = "wipe, then build fresh".

### `package` vs `install` — both make a JAR, so what's different?

You spotted that both produce a JAR. The difference is **where the JAR ends up, and who can then use
it**:

| | `package` | `install` |
|---|---|---|
| Builds the JAR? | ✅ | ✅ (it runs `package` first) |
| JAR lands in | `target/` only | `target/` **and** `~/.m2/repository/com/pms/hotel-pms/0.0.1-SNAPSHOT/` |
| Other projects on your machine can depend on it? | ❌ No | ✅ **Yes** |
| Speed | Faster | Slower (extra copy) |

**Why `install` exists:** imagine you also had a `com.pms:pms-common` library project. For `hotel-pms`
to declare a dependency on it, Maven must be able to *find* it — and Maven only looks in `~/.m2` and
remote repositories. `install` is what puts your own build into `~/.m2` so your *other* projects can
resolve it.

**For a single standalone app like yours, you will basically never need `install`.** Use `package`.
*(And `deploy` is the next step up: upload to a **shared remote** repository so your whole company can
depend on it.)*

### "Maven used to refresh when I added a dependency" — what was that?

You remembered your IDE doing something automatic whenever you edited `pom.xml`. That was **not
Maven** — Maven only runs when you invoke it. That was your **IDE**:

- **VS Code** (Extension Pack for Java / "Language Support for Java") watches `pom.xml`. On save it
  re-resolves dependencies, downloads anything new into `~/.m2`, and rebuilds its internal classpath —
  which is what makes `import` statements for a brand-new library stop showing red squiggles.
- **IntelliJ** does the same, sometimes with a "Load Maven Changes" prompt or a little refresh icon.

**Why it matters:** the IDE's classpath and Maven's classpath are two separate things kept in sync.
Symptoms of them drifting apart: the IDE shows errors but `./mvnw compile` succeeds (or vice versa).
The fix is to force the IDE to reload the Maven project — in VS Code, the Command Palette's
**"Java: Clean Java Language Server Workspace"**, or the refresh button in the Maven panel.

*(This is also what had a lock on `_spring_tmp/target` earlier — the Java language server keeps
compiled output open.)*

#### "I have Language Support for Java — do I still need to click refresh?"

**You have the extension, so it will mostly handle it — but the behaviour is a setting, and it's
worth knowing which mode you're in.**

The setting is `java.configuration.updateBuildConfiguration`:

| Value | Behaviour when you save `pom.xml` |
|---|---|
| `automatic` | Re-resolves silently. Nothing to click. |
| `interactive` | Shows a small **"A build file was modified. Do you want to synchronize?"** prompt — you click it. |
| `disabled` | Nothing happens until you trigger it manually |

**The default is `interactive`** in most setups, so expect a prompt the first time you add a
dependency. Clicking it is fine — but if you'd rather it just happen, set it in VS Code settings:

```json
"java.configuration.updateBuildConfiguration": "automatic"
```

**Manual triggers, if it ever gets stuck:**
- Command Palette → **"Java: Reload Projects"** — the normal fix
- Command Palette → **"Java: Clean Java Language Server Workspace"** — the nuclear option; wipes the
  IDE's index and rebuilds it (takes a minute, fixes almost everything)

#### "What command actually runs to download the dependencies?"

**Honest answer: none — no `mvn` process is launched.** This is worth knowing because it explains some
otherwise-confusing behaviour.

The VS Code Java extension embeds **m2e** (Maven Integration for Eclipse) — a Maven *library* running
inside the language server process. It parses your `pom.xml` with the same Maven resolution code the
CLI uses, then downloads into the same `~/.m2/repository`. But it does it **in-process**, not by
shelling out.

```
    You save pom.xml
         │
         ▼
    Java Language Server (jdt.ls)  ── embedded m2e ──► reads pom.xml
         │                                             resolves the dependency graph
         │                                             downloads missing JARs ──► C:\Users\adith\.m2\repository\
         ▼
    Rebuilds the IDE's internal classpath
         │
         ▼
    Red squiggles on your new `import` disappear
```

**The nearest CLI equivalents**, if you ever want to do it yourself:

| Command | Does |
|---|---|
| `.\mvnw.cmd dependency:resolve` | Download all declared dependencies into `~/.m2`. Nothing else. |
| `.\mvnw.cmd dependency:go-offline` | Download everything needed to build with no network afterwards |
| `.\mvnw.cmd compile` | Also downloads what's missing, as a side effect of needing it |

**The practical consequence of "same `~/.m2`, two separate processes":** if the IDE has already
downloaded a JAR, `./mvnw compile` finds it instantly (shared cache) — but the two keep **separate
compiled output and separate in-memory classpaths**. That's why the IDE can show errors while
`./mvnw compile` is green: they're two different views of the same project. When they disagree,
**trust `./mvnw`** — it's what CI will run.

**The commands you'll actually use:**

| Command | Does |
|---|---|
| `.\mvnw.cmd compile` | Compile main sources. Fast sanity check. |
| `.\mvnw.cmd test` | Compile + run tests |
| `.\mvnw.cmd package` | Compile + test + build the JAR in `target/` |
| `.\mvnw.cmd clean package` | Same, from scratch |
| `.\mvnw.cmd spring-boot:run` | **Run the app** (a goal, not a phase) |
| `.\mvnw.cmd dependency:tree` | Print the full transitive dependency tree — very useful |
| `.\mvnw.cmd -B ...` | Batch mode: no ANSI colours, cleaner logs |

*(On Windows use `.\mvnw.cmd`. The bare `mvnw` file is the Unix version.)*

## 16. The local repository `~/.m2`

Maven caches every downloaded library in a folder on your machine:

```
  Windows:  C:\Users\adith\.m2\repository\
```

Inside, libraries are filed by their GAV coordinates:

```
  ~/.m2/repository/org/postgresql/postgresql/42.7.3/postgresql-42.7.3.jar
                   └── groupId ──┘└artifactId┘└ver┘
```

**Consequences worth knowing:**
- The **first** build of a new project is slow (downloading); later builds are fast (cached).
- All your Maven projects **share** this cache.
- Release versions are downloaded **once, ever** — they're immutable.
- `-SNAPSHOT` versions get re-checked, because they can change.
- If the cache gets corrupted, deleting the offending folder forces a clean re-download.

## 17. `mvn` vs `./mvnw` — the wrapper

**The problem:** Maven is a program you install on your machine. Different developers install
different versions. Maven 3.6 and 3.9 can produce different build behaviour. "It builds on my machine"
follows.

**The solution — the Maven Wrapper:** a small script committed *into the project* that:

1. reads `.mvn/wrapper/maven-wrapper.properties` to learn the required Maven version (3.9.16),
2. downloads exactly that version if it isn't cached,
3. runs your build with it.

```
   Developer's machine                    The project
   ───────────────────                    ───────────
   No Maven installed?  ──── ./mvnw ────► maven-wrapper.properties says 3.9.16
   Has Maven 3.6?                          → fetch & use 3.9.16
   Has Maven 3.9.16?                       → fetch & use 3.9.16
                                           EVERYONE builds identically
```

**The rule: always use `.\mvnw.cmd`, never `mvn`.** You happen to have a global Maven installed, but
using it defeats the purpose.

> ⚠️ **The trap this creates for your `.gitignore`.** The wrapper only works if
> `.mvn/wrapper/maven-wrapper.properties` is **committed** — it *is* the version pin. A blanket
> `.mvn/` ignore rule silently breaks reproducibility for anyone who clones the repo. This is a real
> decision point in step 5.2; note it now.

*(This is the same idea as Docker, one layer up: pin the environment so the build is reproducible.)*

---

# Part 6 — Reading `pom.xml`

## 18. XML in 60 seconds

If XML is unfamiliar, this is all you need:

```xml
  <tagname>value</tagname>              a tag with a value
  <parent><child>x</child></parent>     tags nest to form a tree
  <empty/>                              shorthand for <empty></empty>
  <!-- a comment -->                    ignored
  <project xmlns="...">                 a namespace — declares the vocabulary
```

- Tags are **case-sensitive**: `<groupId>` ✅, `<groupid>` ❌.
- **Every** opening tag needs a closing tag. Unclosed tags = build failure.
- There is exactly **one** root element (here, `<project>`).

The `xmlns` / `xsi:schemaLocation` attributes on `<project>` point at the XML **schema** that defines
which tags are legal. Their practical value: your IDE reads that schema and gives you autocomplete
and red squiggles on typos. **You never edit those attributes.**

## 19. The skeleton of a POM

Every `pom.xml` has the same shape. Learn these seven sections and you can read any Maven project:

```xml
<project>
    <modelVersion/>      1. POM format version — always 4.0.0
    <parent/>            2. Inherit config from another POM
    <groupId/>           3. ┐
    <artifactId/>           ├ YOUR IDENTITY (GAV)
    <version/>           4. ┘
    <name/>              5. Human-readable metadata
    <description/>
    <properties/>        6. Reusable variables
    <dependencies/>      7. THE LIBRARIES YOU NEED   ← you edit this most
    <build>
        <plugins/>       8. Tools that run during the build
    </build>
</project>
```

## 20. Your POM, section by section

Walking your actual file top to bottom.

### `<modelVersion>4.0.0</modelVersion>`
The version of the **POM file format itself** — not your project. It has been `4.0.0` for two decades.
Never changes. Ignore it.

### `<parent>`
```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.1.0</version>
    <relativePath/>
</parent>
```
**This is the most important block in the file** — see §21 for what it does.

`<relativePath/>` is empty on purpose: it tells Maven *"don't look for the parent in a folder on disk,
download it from the repository."* (In multi-module projects, the parent is a local folder — that's
what this would point to.)

**`4.1.0` is your Spring Boot version.** Everything else follows from it.

### Your identity
```xml
<groupId>com.pms</groupId>
<artifactId>hotel-pms</artifactId>
<version>0.0.1-SNAPSHOT</version>
```
Your GAV (§6). This produces `target/hotel-pms-0.0.1-SNAPSHOT.jar`.

### Metadata
```xml
<name>hotel-pms</name>
<description/>
<url/>
<licenses><license/></licenses>
<developers><developer/></developers>
<scm><connection/>...</scm>
```
These are **descriptive only** — they affect nothing about the build. They exist because Maven Central
*requires* them when publishing a public library.

Notice they're **empty tags**. Initializr emits them as placeholders because you left those fields
blank in the API call. They are harmless noise for a private project. **Whether to fill them in or
delete them is your call** — see §25.

### `<properties>`
```xml
<properties>
    <java.version>21</java.version>
</properties>
```
Maven **variables**. Define once, reference anywhere as `${java.version}`. Here it's special: the
parent POM reads `${java.version}` and feeds it to the compiler plugin as both source and target
level. So this single line is what makes your project compile as Java 21.

You'll add more properties later (e.g. a library version used in two places).

### `<dependencies>` — your four choices

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
```

**Note the missing `<version>`.** That is not an omission — see §21.

Your dependencies and what each actually drags in:

| Artifact | Really contains | Why you need it |
|---|---|---|
| `spring-boot-starter-data-jpa` | Hibernate (ORM) + Spring Data JPA + **HikariCP** connection pool + Spring's transaction manager | Map objects ↔ tables; free CRUD repositories; `@Transactional` |
| `spring-boot-starter-webmvc` | Spring MVC + **embedded Tomcat** + **Jackson** (JSON) | REST endpoints; JSON in and out |
| `spring-boot-starter-validation` | Jakarta Bean Validation API + Hibernate Validator | `@NotNull`, `@Size`, `@Valid` on incoming data |
| `postgresql` (scope `runtime`) | The PostgreSQL JDBC driver | Actually speak Postgres's wire protocol |

Plus three test-scoped starters (`…-data-jpa-test`, `…-validation-test`, `…-webmvc-test`) carrying
JUnit 5, AssertJ, Mockito, and Spring's test support.

> ⚠️ **A Boot 4 naming change that WILL confuse you when searching online.**
>
> | Older tutorials (Boot 2/3) | Your project (Boot 4.1.0) |
> |---|---|
> | `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
> | one `spring-boot-starter-test` | split per module: `…-data-jpa-test`, `…-webmvc-test`, … |
>
> Same concepts, newer packaging. If you copy a `<dependency>` block from a 2023 blog post and the
> build fails on an unresolvable artifact, **this is why.** Check the current name rather than
> assuming the tutorial is right.

### `<build><plugins>`
```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
</plugin>
```
See §24.

## 21. The parent POM and why versions are missing

Look again — **none of your dependencies specify a version.** That is the parent POM at work, and
it's Spring Boot's single biggest practical contribution.

**The problem it solves:** a real Spring app pulls in ~400 libraries. Hibernate 6.4 needs a certain
Jakarta Persistence version. Jackson 2.16 needs certain Kotlin modules. Pick versions by hand and you
land in "dependency hell" — obscure `NoSuchMethodError`s at runtime from libraries that don't agree.

**How the parent fixes it:**

```
   spring-boot-starter-parent  (4.1.0)
       │
       │  contains a <dependencyManagement> block:
       │     "IF anyone asks for hibernate-core, the version is 6.6.x"
       │     "IF anyone asks for jackson-databind, the version is 2.18.x"
       │     ... ~400 entries, all mutually TESTED TOGETHER
       │
       ▼  your pom.xml INHERITS that table
   hotel-pms
       └── <dependency>spring-boot-starter-data-jpa</dependency>   ← no version
                        └── Maven consults the inherited table → resolves the version
```

**`<dependencyManagement>` vs `<dependencies>`** — an important distinction:

| Block | Meaning |
|---|---|
| `<dependencies>` | "**Give me** this library." It ends up on your classpath. |
| `<dependencyManagement>` | "**If** anyone asks for this library, **use this version**." Declares nothing itself. |

The parent uses the second. You use the first. Together: you ask for libraries by name only, and get a
set of versions someone has already tested against each other.

**The parent also gives you** sensible plugin configuration, UTF-8 encoding defaults, the
`${java.version}` wiring, and resource filtering.

**Overriding a version** is possible when you need to — declare an explicit `<version>`, or override
the property the parent uses (e.g. `<postgresql.version>`). Do this rarely and deliberately; you're
stepping outside the tested set.

## 22. Dependency scopes

### The idea behind scopes

A Java program has a **classpath** — the list of places the JVM/compiler looks for classes. The key
realisation: **there isn't one classpath, there are several**, used at different moments:

```
   MOMENT 1: compiling your main code       javac needs:  your code + some libraries
   MOMENT 2: compiling your test code       javac needs:  your code + tests + more libraries
   MOMENT 3: running your tests             JVM needs:    everything above + JUnit
   MOMENT 4: running the app in production  JVM needs:    your code + libraries — but NOT JUnit
```

**A scope answers one question: "at which of these moments should this library be present?"**

That's all it is. Scope is not about *what* a library does — it's about **when it's visible**.

| Scope | Compile main | Compile tests | Run tests | Run app / in the JAR | Plain-English meaning |
|---|---|---|---|---|---|
| `compile` *(default)* | ✅ | ✅ | ✅ | ✅ | "Always. Everywhere." |
| `runtime` | ❌ | ❌ | ✅ | ✅ | "**Needed to run, but my code must not name it.**" |
| `test` | ❌ | ✅ | ✅ | ❌ | "Only for testing. Never ships." |
| `provided` | ✅ | ✅ | ✅ | ❌ | "Compile against it; something else supplies it at runtime." |

Taking the two you found unclear, one at a time:

---

### `runtime` — and what "written against JDBC" means

**Start with the concept, because the phrase is the confusing part.**

"Writing against X" means **your code only ever mentions X's names** — X's interfaces, X's method
names — and never the names of whatever concrete thing implements X.

**JDBC is a set of Java *interfaces*** (`Connection`, `Statement`, `ResultSet`, `PreparedStatement`).
Interfaces declare method signatures but contain no working code. Someone must implement them.

```
   ┌─────────────────────────────────────────────────────┐
   │  JDBC  —  interfaces only, part of Java itself      │
   │     interface Connection { Statement createStmt(); }│
   │     interface ResultSet  { String getString(int); } │
   └──────────────────────┬──────────────────────────────┘
                          │ implemented by
        ┌─────────────────┼─────────────────┐
        ▼                 ▼                 ▼
   postgresql.jar     mysql.jar       oracle.jar
   (your driver)                            ← each speaks its own DB's wire protocol
```

**Your code — and Hibernate's code — only ever mention the interface side:**

```java
Connection conn = dataSource.getConnection();   // type is JDBC's interface
PreparedStatement ps = conn.prepareStatement("select * from hotels where id = ?");
```

At runtime, `conn` is *really* a `org.postgresql.jdbc.PgConnection`. But **you never wrote that name**,
so your code has no idea which database it's talking to. Swap the JAR and the URL and the same code
runs on MySQL.

**Now `runtime` scope makes sense.** The driver is needed **when the app runs** (something must
actually implement those interfaces) but must **not** be visible **when your code compiles** (so you
*cannot* accidentally name it).

```
   COMPILING your code:   driver ABSENT   → `import org.postgresql.…` won't compile ✅
   RUNNING your app:      driver PRESENT  → the real implementation is there  ✅
```

**That is scope doing real architectural work.** "Don't couple your code to a specific database
vendor" stops being a guideline you might forget and becomes **a rule the compiler enforces**. If
someone on your team tries to use a Postgres-specific class, the build breaks. That's the point.

*(How does the driver get found if nobody names it? Java's `ServiceLoader` — the driver JAR contains a
`META-INF/services` file announcing itself, and JDBC discovers it from the `jdbc:postgresql:` prefix
in your URL. See §28①.)*

---

### `provided` — "someone else will supply this"

`provided` means: **"I need this to compile, but do NOT put it in my JAR — the environment I deploy
into already has it."**

The classic example is the old WAR deployment model (§13):

```
   Your WAR needs the Servlet API to compile (HttpServletRequest, etc.)
   But the Tomcat server you deploy into ALREADY HAS the Servlet API.
   Bundling your own copy → two versions on the classpath → ClassCastException chaos.
   So: <scope>provided</scope> — compile against it, ship without it.
```

**You will almost certainly never use `provided` in this project**, precisely because you're building
a fat JAR with **embedded** Tomcat — nothing is "already provided" by the environment. Your JAR
carries everything. It's worth recognising in other people's POMs, not something you need.

---

### `test`

JUnit, Mockito and AssertJ exist only to test your code. Shipping them to production would bloat the
JAR and expose test utilities in a live system. `test` scope makes that **impossible** — they're
absent from the JAR by construction.

This is also the mechanical reason your production code cannot accidentally import a test helper: at
compile time for `src/main/java`, none of it exists.

## 23. Transitive dependencies

You declared **4** dependencies. Your app will run with roughly **50**. The rest come in
**transitively** — dependencies of your dependencies:

```
   you declare:  spring-boot-starter-data-jpa
                  ├── spring-boot-starter-jdbc
                  │    ├── HikariCP              ← the connection pool
                  │    └── spring-jdbc
                  ├── hibernate-core             ← the ORM
                  │    ├── jakarta.persistence-api
                  │    └── jboss-logging
                  ├── spring-data-jpa
                  └── spring-orm
```

You never asked for HikariCP. You'll use it anyway — it's your connection pool.

**Inspect the real tree yourself:**
```powershell
.\mvnw.cmd dependency:tree
```
Do this at least once. It makes "a starter is a bundle" concrete rather than abstract.

> **"It printed a huge tree and said BUILD SUCCESS — did it create class files in `target/`?"**
>
> **No.** `dependency:tree` is a **read-only report**. It resolves the dependency graph in memory and
> prints it. It compiles nothing and writes nothing to `target/`.
>
> Two things worth taking from that:
>
> 1. **`BUILD SUCCESS` just means "the command finished without error"** — not "something was built".
>    Every Maven invocation ends with `BUILD SUCCESS` or `BUILD FAILURE`, even a pure report.
> 2. **This is the phase-vs-goal distinction being real** (§15). You ran a bare **goal**
>    (`dependency:tree`), so *no lifecycle ran* — no `validate`, no `compile`, nothing. Had you run the
>    **phase** `.\mvnw.cmd package`, every phase before it would have executed and `target/` would have
>    filled with `.class` files and a JAR.
>
> **How to check for yourself:** `ls target` — if you've only ever run `dependency:tree`, there is no
> `target/` folder at all.
>
> **Reading the tree:** each line is `groupId:artifactId:packaging:version:scope`, and indentation is
> depth. `+-` is a child, `\-` is the last child, `|` continues a branch:
> ```
>  [INFO] com.pms:hotel-pms:jar:0.0.1-SNAPSHOT
>  [INFO] +- org.springframework.boot:spring-boot-starter-data-jpa:jar:4.1.0:compile
>  [INFO] |  +- com.zaxxer:HikariCP:jar:5.1.0:compile          ← you never asked for this
>  [INFO] |  \- org.hibernate.orm:hibernate-core:jar:6.6.4:compile
>  [INFO] \- org.postgresql:postgresql:jar:42.7.3:runtime      ← note the scope
> ```
> Try `.\mvnw.cmd dependency:tree -Dincludes=org.postgresql` to filter to one library.

**Conflict resolution:** when two paths bring in different versions of the same library, Maven uses
**"nearest wins"** — the version fewest hops from your POM. Declaring a version explicitly in your own
POM always wins, since that's distance zero. (Boot's `dependencyManagement` also wins over transitive
versions, which is exactly why the curated set holds together.)

## 24. Plugins and `spring-boot-maven-plugin`

Maven's core does almost nothing by itself — **plugins do the work**. Compiling is a plugin. Running
tests is a plugin. Building a JAR is a plugin. The parent POM configures the standard ones invisibly.

You have one explicit plugin:

```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
</plugin>
```

It does two things:

### What is a JAR, and what is the "manifest"?

**A JAR is a ZIP file.** Literally — rename `hotel-pms.jar` to `.zip` and you can open it. It contains
compiled `.class` files, your copied resources, and one special file.

```
   hotel-pms-0.0.1-SNAPSHOT.jar
   ├── META-INF/
   │   └── MANIFEST.MF            ← the "label on the box"
   ├── com/pms/hotel/
   │   └── HotelPmsApplication.class
   └── application.properties
```

**The manifest (`META-INF/MANIFEST.MF`) is a small plain-text file of `Key: Value` lines that
describes the JAR.** The critical line is:

```
   Manifest-Version: 1.0
   Main-Class: org.springframework.boot.loader.launch.JarLauncher
   Start-Class: com.pms.hotel.HotelPmsApplication
   Spring-Boot-Version: 4.1.0
```

**Why it must exist:** a JAR may contain hundreds of classes. When you type `java -jar app.jar`, the
JVM has to know **which class has the `main()` method to start**. It cannot guess. It opens the
manifest and reads `Main-Class`. **No `Main-Class` line → `no main manifest attribute` → the JAR
won't run.**

Notice the two entries above: `Main-Class` points at Spring Boot's *launcher* (which knows how to load
nested JARs), and `Start-Class` points at **your** class. The launcher reads `Start-Class` and hands
control to your `main()`. That indirection is exactly what makes a fat JAR possible.

**You never write a manifest.** The `maven-jar-plugin` generates it, and `spring-boot-maven-plugin`
rewrites it during repackaging.

**1. Builds an executable "fat JAR".** A normal JAR contains only *your* classes — it can't run without
its 50 dependencies on the classpath. This plugin repackages everything (your code + every dependency
+ embedded Tomcat) into **one self-contained JAR**:

```powershell
.\mvnw.cmd package
java -jar target/hotel-pms-0.0.1-SNAPSHOT.jar    # runs anywhere with a JVM
```

That single artifact is what makes Spring Boot apps trivial to deploy and to containerise.

**2. Provides `spring-boot:run`** — the goal that runs your app from source, without packaging:
```powershell
.\mvnw.cmd spring-boot:run
```

## 25. Decisions open in YOUR pom.xml

Nothing here is done. These are the choices; the trade-offs are yours to weigh.

**① The empty metadata tags** — `<description/>`, `<url/>`, `<licenses>`, `<developers>`, `<scm>`

| Option | For | Against |
|---|---|---|
| Fill in `<description>`, delete the rest | Clean, documents the project | 30 seconds of work |
| Delete them all | Minimal file | Loses a natural place to describe the project |
| Leave as-is | Zero effort | 14 lines of noise; some tools warn on empty `<license/>` |

*Nothing breaks either way.* This is purely about the file being pleasant to read.

> ✅ **Decided (2026-08-02):** you commented them out with `<!-- ... -->`, keeping `<description/>`.
> That's a sound choice — the tags are gone from the effective build but recoverable if you ever
> publish. Note for later: `<description/>` is still an *empty* tag; filling it in (or removing it)
> is a 10-second tidy whenever you next touch the file.

**② Lombok** — the boilerplate generator (`@Getter`, `@Data`, `@Builder`).
Deliberately excluded (see `docs/spring-and-maven-notes.md`). **Recommendation: keep it out for now.**
You should write getters/setters/constructors by hand until you know exactly what they do; Lombok
hides them. Trivial to add later.

**③ DevTools** — auto-restart on code change.
Also excluded. **Genuinely worth reconsidering**, since you'll be iterating a lot. Trade-off: a much
faster feedback loop, versus one more moving part (a second classloader) that can produce confusing
behaviour while you're still learning what's normal.

**④ A test database dependency** (H2, or Testcontainers).
Not needed yet. Becomes a real decision at step 5.7, when `contextLoads` starts requiring a live
Postgres.

---

# Part 7 — `application.properties`

## 26. What it is and when it's read

- **Location:** `src/main/resources/application.properties` — Maven copies it into `target/classes`,
  so at runtime it's on the **classpath**.
- **Format:** `key=value`, one per line. `#` starts a comment.

### The syntax rules (including the space question)

**"No space even after the `=`?"** — Spaces around `=` are actually *legal and ignored*. But there is
a real trap, so the safe habit is to use none.

```properties
spring.datasource.url=jdbc:postgresql://localhost:5433/hotel_pms    ✅ the convention
spring.datasource.url = jdbc:postgresql://localhost:5433/hotel_pms  ⚠️ legal — spaces around = are trimmed
spring.datasource.password=secret123                                ⚠️ ...but see below
```

**The trap is trailing whitespace, not the `=`:**

| Where the space is | Result |
|---|---|
| Before the key | Ignored ✅ |
| Around the `=` | Ignored ✅ |
| **At the end of the line, after the value** | **Kept as part of the value** ❌ |

So `spring.datasource.password=secret ` (with a trailing space) is the password `"secret "` — and
you get an authentication failure you will stare at for twenty minutes because the file *looks*
correct. Spring's type conversion saves you for numbers and booleans, but **never for strings** like
passwords, URLs, and usernames.

**Rule of thumb: no spaces anywhere on the line.** It costs nothing and removes a whole class of bug.

**The other syntax rules worth knowing:**

| Rule | Example |
|---|---|
| `#` or `!` starts a comment — **must be at the start of the line** | `# this is a comment` |
| A `#` mid-line is **part of the value**, not a comment | `pw=abc#123` → the password is `abc#123` |
| Do **not** quote values — quotes become part of the value | `name="hotel"` → the value is `"hotel"` including quotes |
| Blank lines are fine | |
| Duplicate key? **The last one wins**, silently | Easy to do by accident in a long file |
| `\` at end of line continues onto the next | rarely needed |
| A key with no value = empty string | `some.key=` |
- **When it's read:** *very* early in startup — **before** most beans are created — because
  auto-configuration needs it to decide what to build.
- **What it does:** overrides Spring Boot's defaults. Boot ships opinionated defaults for hundreds of
  settings; this file is where you disagree with them.

**`.properties` vs `.yml`** — both are supported, pick one:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5433/hotel_pms
spring.jpa.hibernate.ddl-auto=validate
```
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/hotel_pms
  jpa:
    hibernate:
      ddl-auto: validate
```

| | `.properties` | `.yml` |
|---|---|---|
| Repetition | Repeats prefixes | Groups them hierarchically |
| Whitespace | Irrelevant | **Significant** — indentation errors are common |
| Searchability | Grep for the full key works | Key is split across lines |
| Industry use | Very common | Very common |

**Recommendation: stay on `.properties`.** Full keys are greppable and every Stack Overflow answer
gives you a full key — much easier while learning.

## 27. Property precedence

The same property can be set in several places. Spring Boot has a strict override order — **later
wins**. The ones that matter to you, weakest to strongest:

```
   1. application.properties                  (the file, committed)
   2. application-{profile}.properties        (e.g. application-local.properties)
   3. OS environment variables
   4. Command-line arguments  --key=value     (strongest)
```

**Two things this enables:**

**Profiles** — a named set of overrides. `application-local.properties` is loaded only when the
`local` profile is active. Base config is committed; machine-specific overrides are not.

**Environment-variable mapping** — Spring converts property names automatically:
```
   spring.datasource.password    ⇄    SPRING_DATASOURCE_PASSWORD
   (dots → underscores, lowercase → uppercase)
```
So any property can be supplied by the environment without touching the file. **This is the standard
way secrets are injected in production** (and in Docker — the same idea as the `-e` flags you used on
`pms-postgres`).

## 28. Decisions open in YOUR application.properties

Your file currently has **one line**. Here is everything it will need, framed as decisions.

### ① Which Postgres? — the datasource

You have **two** running Postgres servers with the same schema.

**"Native" just means "installed directly on Windows, not in a container."** It's the ordinary
meaning — running natively on the operating system, as opposed to running inside Docker. Nothing
technical hiding in the word.

| | **Native** Postgres | **Docker** Postgres |
|---|---|---|
| Port | 5432 | **5433** |
| What it is | A Windows service, installed by the PG installer | A container from the `postgres:18` image |
| Starts when | Windows boots, automatically | You run `docker start pms-postgres` |
| Data lives in | A folder on your Windows disk | The named volume `pms_pgdata` |
| To reset it | Uninstall / manually drop everything | `docker rm` + `docker volume rm` — 5 seconds |
| Schema applied? | ✅ | ✅ |

**"One less moving part" — what I meant.** With native Postgres, the database is simply always
running; there is nothing to remember. With Docker there are extra things that can be wrong: Docker
Desktop must be running, the container must be started, and it's on a non-default port (5433) so
every tool needs telling. **That's it** — that's the entire "moving parts" argument. It isn't a
technical drawback of Docker, just a couple more ways to be tripped up on a given morning.

Against that: the Docker one is disposable, reproducible, matches how you'll eventually deploy, and
was the whole reason you containerised it.

### The JDBC URL, decoded — and what "subprotocol" means

```
   jdbc:postgresql://localhost:5433/hotel_pms
   └┬─┘ └────┬────┘   └───┬───┘ └┬─┘ └───┬───┘
    │        │            │      │       └─ database name
    │        │            │      └───────── port
    │        │            └──────────────── host
    │        └───────────────────────────── the SUBPROTOCOL
    └────────────────────────────────────── the scheme — always "jdbc"
```

**A "subprotocol" is the second segment of the URL, and its job is to say *which driver* should handle
this connection.**

Think of a normal web URL: `https://example.com` — the scheme `https` tells your OS which *protocol
handler* to use. A JDBC URL does the same thing, one level deeper:

- `jdbc:` — "this is a database URL" (the scheme). Always present.
- `postgresql:` — "**and it's for the PostgreSQL driver**" (the subprotocol). This is the routing key.

**Why it's needed:** you could have three drivers on the classpath at once. When JDBC is handed a URL,
it asks each registered driver *"do you handle `postgresql`?"* — and the Postgres driver says yes.

```
   jdbc:postgresql://…   →  org.postgresql.Driver
   jdbc:mysql://…        →  com.mysql.cj.jdbc.Driver
   jdbc:h2:mem:testdb    →  org.h2.Driver
   └┬─┘ └───┬───┘
  scheme  subprotocol = which driver
```

**This is the missing link from §22.** You asked how the driver gets used if your code never names
it. Answer: **the subprotocol in this string is the only place the database vendor is named in your
entire application** — and it lives in a config file, not in Java. Change `postgresql` to `mysql`,
swap the JAR, and not one line of your code changes. That's what "written against JDBC" buys you.

### ⭐ Your profiles idea — this is exactly right

You wrote in the properties file:

> *"I thought to learn this we could use our localhost postgres when we run the application here in
> localhost, whereas when we run it in docker, that should use the external docker postgres."*

**That is precisely what profiles are for, and it's the textbook use case.** You don't have to choose
between the two databases — you configure both and pick at runtime. Let's do it that way.

**How it works — three files instead of one:**

```
  src/main/resources/
   ├── application.properties          ← shared by everyone. NO datasource url here.
   ├── application-local.properties    ← active when profile "local"  → native PG on 5432
   └── application-docker.properties   ← active when profile "docker" → container PG
```

Spring loads `application.properties` **always**, then loads the profile-specific file **on top**,
overriding anything it repeats (§27).

**Choosing a profile at run time — three ways:**

| How | Command | When you'd use it |
|---|---|---|
| Command-line arg | `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"` | Ad-hoc |
| Environment variable | `$env:SPRING_PROFILES_ACTIVE="docker"` then run | Docker / CI |
| A default in the base file | `spring.profiles.active=local` | Your everyday default |

**A subtlety worth understanding now, because it will bite you later.** There are *two different
things* you might mean by "running in Docker":

```
  CASE A — app on Windows, Postgres in a container      ← where you are today
     app ──► localhost:5433 ──► [container: pms-postgres]
     Host is "localhost" because Docker PUBLISHED port 5433 to your machine (-p 5433:5432).

  CASE B — app ALSO in a container, both on a Docker network   ← later, when we containerise the app
     [container: hotel-pms] ──► pms-postgres:5432 ──► [container: pms-postgres]
     Host is the CONTAINER NAME, and the port is the INTERNAL 5432 — not 5433.
     "localhost" inside a container means THAT container, not your PC.
```

**That difference is the single most common Docker networking mistake**, and it's exactly why having
a separate profile per environment is the right design rather than a nice-to-have. *(You already
learned the underlying rule in `docs/docker-notes.md` — host-to-container vs container-to-container.)*

**Naming suggestion:** call them `local` (native PG) and `docker` (containerised PG) now; we'll add a
third for Case B when we containerise the app itself. We'll set this up together in step 5.5 — and
containerising the app is a step we'll do properly, since you said you're in.

### ② Schema ownership — `spring.jpa.hibernate.ddl-auto`

**This is a genuine architectural decision, not a setting.** The question is: *when your Java entity
and your database table disagree, who wins?*

| Value | Behaviour at startup |
|---|---|
| `none` | Hibernate ignores the schema entirely |
| `validate` | Hibernate **compares** entities to tables and **refuses to start** if they differ |
| `update` | Hibernate **ALTERs** your tables to match your entities |
| `create` | **Drops and recreates** all tables (data lost) |
| `create-drop` | Same, plus drops again on shutdown |

**The context you're in:** you hand-wrote `docs/db/schema.sql`. It's version-controlled, reviewed, and
it contains things Hibernate would never generate on its own — your
`CHECK (check_out_date > check_in_date)`, your composite indexes, and eventually the Postgres
`EXCLUDE` constraint that is the centrepiece of this project.

| | `update` | `validate` |
|---|---|---|
| Convenience | High — schema follows your code | Lower — you edit DDL by hand too |
| Who owns the schema | Hibernate | **You / `schema.sql`** |
| Drift | Silent — it never drops or narrows anything | Impossible — mismatch = startup failure |
| Custom constraints | Not generated; may be worked around | Preserved |
| Failure mode | Wrong mapping discovered at runtime | Wrong mapping discovered **at startup** |

**My recommendation is `validate`**, and the reasoning is: your `schema.sql` is the artifact you spent
real design effort on, and `update` would quietly let it drift out of sync with reality. `validate`
also turns Hibernate into a **free correctness checker** for every entity you write — the moment you
mistype a column name, the app won't start. Given that learning JPA mapping is a goal, that feedback
is worth more than the convenience.

**But it is your decision**, and the cost is real: every entity change means also editing the DDL.

#### Migration tools — Flyway, Liquibase, dbmate

**The problem they solve.** `ddl-auto` is a *startup-time* setting — it can only compare or blindly
alter. Neither option answers the real production question: **"this database is at version 7, the new
code needs version 9 — how does it get there, safely, exactly once, in order?"**

**A migration tool is version control for your database schema.** You never edit a live database by
hand again. Instead you write small, numbered, immutable SQL files:

```
   db/migration/
    ├── V1__create_hotels_and_rooms.sql
    ├── V2__create_bookings.sql
    ├── V3__add_booking_status_index.sql
    └── V4__add_exclude_constraint_no_double_booking.sql
```

At startup the tool:
1. reads a bookkeeping table it owns (e.g. `flyway_schema_history`) to see what's already applied,
2. runs **only** the files not yet applied, **in order**, each in a transaction,
3. records each one, with a checksum.

```
   Fresh database          Database at V2          Database at V4
        │                       │                       │
        │ runs V1,V2,V3,V4      │ runs V3,V4            │ runs nothing
        ▼                       ▼                       ▼
      at V4                   at V4                   at V4
```

**Every environment converges to the same schema by the same path** — your laptop, CI, staging,
production. And the checksum means if someone edits an already-applied file, startup fails loudly
instead of silently diverging.

**The three you asked about:**

| | **Flyway** | **Liquibase** | **dbmate** |
|---|---|---|---|
| Migrations written in | **Plain SQL** (Java optional) | XML / YAML / JSON / SQL | **Plain SQL** |
| Database-agnostic? | No — you write real Postgres SQL | Yes — its DSL generates per-DB SQL | No |
| Rollback support | Paid tier (mostly roll-forward) | ✅ Built in | ✅ Simple down-migrations |
| Runs where | **Inside your Spring app at startup** | **Inside your Spring app at startup** | **Standalone CLI binary** — outside the app |
| Spring Boot integration | ✅ First-class (add the dependency, it just runs) | ✅ First-class | ❌ None — it's language-agnostic |
| Written in | Java | Java | Go |
| Complexity | Low | High | Very low |

**The one real difference to hold on to:** Flyway and Liquibase are **Java libraries that run inside
your application** — add the dependency and Boot auto-configures them to migrate on startup. **dbmate
is a standalone binary** with no idea Java exists; you run it from a shell or a deploy script. That
makes dbmate popular in polyglot shops (a Go and a Python service can share it), and makes Flyway the
natural fit for a Spring project.

**Liquibase's XML/YAML abstraction** is its selling point *and* its cost: describe a change abstractly
and it generates the right SQL for Postgres or Oracle or MySQL. Genuinely useful if you must support
several databases — otherwise it's a layer of indirection between you and SQL you already know.

**Recommendation for this project: none of them, yet — but Flyway later.** Right now `schema.sql` +
`validate` keeps the friction low and keeps you writing raw DDL, which is a stated goal. Flyway is the
natural upgrade once the schema starts changing repeatedly, and it's a genuinely good learning topic
for this project — especially since `V4__add_exclude_constraint...` is a migration you will actually
want to write.

### ③ Should the app hold a DB connection for the whole request? — `spring.jpa.open-in-view`

Boot defaults this to **`true`** and prints a warning at startup, because the default is contentious.
This one needs three concepts first: **lazy loading**, then **the two scenarios**, then **N+1**.

#### Step 1 — what "lazy loading" is

When Hibernate loads a `Booking`, what should it do about the `Guest` it points to?

```java
@Entity
class Booking {
    @ManyToOne(fetch = FetchType.LAZY)     // ← the choice
    private Guest guest;
}
```

| | **EAGER** | **LAZY** (the sensible default for `@ManyToOne`) |
|---|---|---|
| Loading a Booking | Also fetches the Guest immediately, via a JOIN | Fetches **only** the booking row |
| `booking.getGuest()` | Already in memory | **Fires a second query, right then** |
| Cost | Always pays for data you may not need | Pays only when you actually ask |

Lazy is usually right — but it means **`booking.getGuest()` can secretly hit the database.** And a
database query needs an open **Hibernate session** (and a connection). That's where `open-in-view`
comes in: it decides **how long that session stays open**.

#### Step 2 — the two scenarios, side by side

```
  ══ open-in-view = TRUE (Boot's default) ═════════════════════════════════

   HTTP request in
        │
   ┌────┴──── SESSION OPENS HERE (a DB connection is taken from the pool) ────────┐
   │                                                                             │
   │  Controller ──► Service ──► Repository ──► SELECT * FROM hotel_bookings      │
   │                    │                                                        │
   │                 returns                                                     │
   │                    │                                                        │
   │  Controller gets the Booking list                                           │
   │                    │                                                        │
   │  Jackson serialises to JSON, touches booking.getGuest() on EVERY booking     │
   │        └──► lazy loads fire HERE: SELECT ... FROM hotel_guests WHERE id=?    │
   │             ...one query per booking. It WORKS. You never see a problem.     │
   │                                                                             │
   └────────── SESSION CLOSES (connection returned) — after the response is written┘
        │
   HTTP response out
   ✅ Nothing crashes.   ❌ Connection held the whole time.  ❌ Extra queries invisible.


  ══ open-in-view = FALSE (recommended) ═══════════════════════════════════

   HTTP request in
        │
   Controller ──►┌─── SESSION OPENS (@Transactional service method) ───┐
                 │                                                     │
                 │  Repository ──► SELECT * FROM hotel_bookings        │
                 │  Anything lazy you need, you load HERE — on purpose │
                 │                                                     │
                 └─── SESSION CLOSES when the method returns ──────────┘
        │            (connection goes straight back to the pool)
   Controller has the data
        │
   Jackson serialises → touches booking.getGuest()
        └──► 💥 LazyInitializationException: "could not initialize proxy - no Session"
        │
   ❌ It crashes — LOUDLY, on your screen, in development, with a stack trace
      pointing at exactly the field you forgot to load.
```

**"I didn't understand the `false` part."** Here it is in one line:

> **`false` means the database session is closed by the time your controller returns.** Any lazy field
> you didn't deliberately load is now unreachable, and touching it throws immediately.

That sounds purely worse. It isn't — because of what `true` hides.

#### Step 3 — the N+1 query problem

**The name is literal.** You run **1** query to get a list of N things, then **N** more queries — one
per item — to fill in a related field.

Say you fetch 100 bookings and serialise each one's guest name:

```
   Query 1:    SELECT * FROM hotel_bookings WHERE hotel_id = 7;        → 100 rows
   Query 2:    SELECT * FROM hotel_guests WHERE id = 41;    ← for booking 1
   Query 3:    SELECT * FROM hotel_guests WHERE id = 88;    ← for booking 2
   ...
   Query 101:  SELECT * FROM hotel_guests WHERE id = 12;    ← for booking 100

   TOTAL: 1 + 100 = 101 round trips to the database.
```

Each round trip is a network call (§4 of `how-it-all-works.md`) — perhaps 1 ms. So an endpoint that
*should* take ~2 ms takes ~100 ms, and it gets **linearly worse as data grows**. This is the single
most common performance bug in ORM applications.

**The fix is one query:**
```sql
   SELECT b.*, g.* FROM hotel_bookings b JOIN hotel_guests g ON g.id = b.guest_id
   WHERE b.hotel_id = 7;                                    → 1 round trip
```
In JPA you'd write a repository method using `JOIN FETCH`, or an `@EntityGraph`.

#### Step 4 — so how does `false` "solve" N+1?

**Precisely and honestly: it does not fix N+1. It makes N+1 impossible to ignore.**

| | `open-in-view=true` | `open-in-view=false` |
|---|---|---|
| Accidental N+1 in the controller/JSON layer | **Works silently** — 101 queries, no error | **Throws `LazyInitializationException`** |
| When you find out | In production, when it's slow | **The first time you run it** |
| Who fixes it | Whoever is on call | You, ten seconds later |

The exception is not the enemy — it's a **tripwire**. It fires at exactly the moment you were about
to make an invisible database call from the wrong layer, and it forces you to go back to the service
and say explicitly *"fetch the guests too, in one query."*

**Secondary benefit:** with `true`, a pooled DB connection is held for the *entire* request — including
JSON serialisation and network write-out. Under load, connections are your scarcest resource. `false`
returns each connection the moment the service method ends.

**Trade-off, stated plainly:** `true` = fewer exceptions while learning, real bugs hidden. `false` =
more exceptions while learning, each one teaching you something true about your data access.
**Recommendation: `false`** — you *want* to meet `LazyInitializationException` and understand it.

#### How do you actually set it?

Exactly as you already have — one line, no ceremony:
```properties
spring.jpa.open-in-view=false
```
✅ **Your line is correct.** (Just drop the trailing space after `false` — harmless for a boolean, but
the habit matters; see §26.) You'll know it took effect because Boot's startup warning about
`spring.jpa.open-in-view` disappears.

### ④ How much should it log? — SQL visibility

- `spring.jpa.show-sql=true` — print every generated SQL statement
- `spring.jpa.properties.hibernate.format_sql=true` — pretty-print it across lines
- `logging.level.org.hibernate.orm.jdbc.bind=TRACE` — also show the **bound parameter values**
  (otherwise you only see `?` placeholders)

**Trade-off:** noisy and slow — genuinely unsuitable for production. But watching JPA turn your Java
into SQL is one of the fastest ways to actually learn ORM. **Recommendation: all three on**, and turn
them off later.

### ⑤ The password — how do you not commit a secret?

**The problem, concretely.** Your `application.properties` is committed to Git. If the password is in
it, the password is in your GitHub history — **forever**, even if you delete it in a later commit.
Git keeps everything. This is one of the most common real-world security incidents there is.

Here are the three options, with the actual mechanics of each.

---

#### Option A — plain text in `application.properties`

```properties
spring.datasource.password=mypassword
```

**How it works:** nothing to explain — Spring reads the file.
**When it's OK:** genuinely never for a real secret, but harmless for a throwaway local password on a
container nobody can reach. **The risk isn't this project — it's the habit.**

---

#### Option B — a git-ignored profile file ⭐ *recommended for you*

This pairs perfectly with the profiles idea you already had (§28①).

**1.** Keep the base file committed, **without** the password:
```properties
# src/main/resources/application.properties  (COMMITTED)
spring.application.name=hotel-pms
spring.jpa.hibernate.ddl-auto=validate
spring.profiles.active=local
```

**2.** Put the secret in a profile file that Git ignores:
```properties
# src/main/resources/application-local.properties  (NOT COMMITTED)
spring.datasource.url=jdbc:postgresql://localhost:5433/hotel_pms
spring.datasource.username=postgres
spring.datasource.password=the_real_password
```

**3.** Confirm it's ignored. Your `.gitignore` **already** has this line — you wrote it before you knew
you'd need it:
```
application-local.properties
```
Verify with: `git status` — the file must **not** appear. If it does, the ignore rule isn't matching.

**4.** Commit a template so a teammate (or future you) knows what's needed:
```properties
# src/main/resources/application-local.properties.example   (COMMITTED)
spring.datasource.url=jdbc:postgresql://localhost:5433/hotel_pms
spring.datasource.username=postgres
spring.datasource.password=<your password here>
```

**Why this is the right fit:** it costs you nothing, it uses the profiles you wanted anyway, and the
secret physically cannot be committed.

---

#### Option C — an environment variable

**The mechanism** (§27): Spring maps property names to env-var names automatically —
uppercase, dots → underscores.

```
   spring.datasource.password   ⇄   SPRING_DATASOURCE_PASSWORD
```

You set nothing in the file at all; Spring finds the variable and it **overrides** the file anyway
(env vars rank higher, §27).

**Setting it in PowerShell — for the current window only:**
```powershell
$env:SPRING_DATASOURCE_PASSWORD = "the_real_password"
.\mvnw.cmd spring-boot:run
```
⚠️ This vanishes when you close the terminal. That is a feature (nothing on disk) and an annoyance
(you retype it).

**Permanently, for your Windows user:**
```powershell
[Environment]::SetEnvironmentVariable("SPRING_DATASOURCE_PASSWORD","the_real_password","User")
```
Then **open a new terminal** — existing ones don't see it.

**A cleaner variant — placeholders with defaults.** You can reference an env var *from* the properties
file, with a fallback:
```properties
spring.datasource.password=${DB_PASSWORD:postgres}
                            └───┬────┘ └───┬───┘
                        env var name    default if unset
```
This documents in the committed file *that* a secret is needed, without revealing it.

---

#### Summary

| | A: plain file | B: `application-local.properties` | C: env var |
|---|---|---|---|
| Setup effort | None | 2 minutes | 2 minutes |
| Can it leak to Git? | **Yes** | No | No |
| Survives closing the terminal | ✅ | ✅ | Only if set permanently |
| How production actually does it | ❌ | ❌ | ✅ (via Docker/Kubernetes secrets) |
| Good for you, now | ❌ | ⭐ **Yes** | ✅ Also fine |

**Recommendation: Option B**, because it does double duty — it solves the secret problem *and* it's
the profile mechanism you already wanted for switching between the native and Docker databases. You
get both from one piece of work.

*(Worth knowing the next rung up: real production uses a secret manager — AWS Secrets Manager, HashiCorp
Vault, Kubernetes Secrets — which injects values as env vars at container start. Same Option C
mechanism, managed infrastructure around it.)*

### ⑥ Port

`server.port=8080` is the default. Setting it explicitly is documentation, not necessity. Change it
only if 8080 is taken.

---

# Part 8 — Modularization & microservices

This is forward-looking. It doesn't block the build — but §29's decision **does** shape every file you
write from here, so read at least that far before step 5.4.

## 29. Package-by-layer vs package-by-feature

Once you write more than one entity, you must choose how to organise packages. There are two schools.

**Package-by-layer** — group by *technical role*:
```
  com.pms.hotel
   ├── controller/     HotelController  RoomController  BookingController  GuestController
   ├── service/        HotelService     RoomService     BookingService     GuestService
   ├── repository/     HotelRepository  RoomRepository  BookingRepository  GuestRepository
   ├── entity/         Hotel            Room            Booking            Guest
   └── dto/            ...
```

**Package-by-feature** — group by *business capability*:
```
  com.pms.hotel
   ├── booking/        BookingController  BookingService  BookingRepository  Booking
   ├── room/           RoomController     RoomService     RoomRepository     Room
   ├── guest/          GuestController    GuestService    GuestRepository    Guest
   └── tenant/         HotelController    HotelService    HotelRepository    Hotel
```

| | Package-by-layer | Package-by-feature |
|---|---|---|
| Most tutorials use | ✅ | |
| To change "booking" you edit | 4 different packages | **1 package** |
| Related code sits | Far apart | **Together** |
| Can you hide internals? | No — everything is public across layers | **Yes** — package-private within a feature |
| Scales to many features | Packages grow huge and unrelated | Each stays focused |
| **Can you extract a microservice later?** | ❌ Very hard — logic is smeared everywhere | ✅ **Copy one folder out** |

**The connection to your goal.** You said you want `check-availability` to be independently scalable
one day. With package-by-layer that is a rewrite. With package-by-feature it is a **move**. The
package structure you pick *now* determines whether that door stays open.

**Recommendation: package-by-feature.** It's slightly less familiar from tutorials, and it is the
right choice given what you've said you want to learn.

## 30. The modular monolith

There's a middle ground between "one big app" and "twelve microservices", and it's what most good
systems actually are:

```
   MONOLITH                MODULAR MONOLITH              MICROSERVICES
   ─────────               ────────────────              ─────────────
   one deployable          one deployable                many deployables
   one database            one database                  one DB each
   no internal borders     STRONG internal borders       network borders
   ┌───────────────┐       ┌──────┬──────┬──────┐       ┌────┐ ┌────┐ ┌────┐
   │ everything    │       │books │rooms │guests│       │book│ │room│ │gst │
   │ calls         │       │      │      │      │       └─┬──┘ └─┬──┘ └─┬──┘
   │ everything    │       │ talk via clear APIs │         └──HTTP─┴──────┘
   └───────────────┘       └──────┴──────┴──────┘        ┌──┐  ┌──┐  ┌──┐
                                                          DB    DB    DB
```

**Why the middle is usually right:** you get the design benefit (clear boundaries, isolated features,
independent reasoning) **without** the operational cost (network calls, distributed transactions,
service discovery, multiple deployments, distributed tracing).

**What makes a good module boundary?** The test is: *does this thing change for its own reasons?*

- ✅ **Good:** `booking`, `room`, `guest`, `payment` — business capabilities.
- ❌ **Bad:** `controllers`, `utils`, `helpers` — technical groupings that change for everyone's reasons.

**A module should own its data.** Other modules ask it for information; they don't reach into its
tables. Enforce that in the monolith and the microservice split becomes mechanical later.

### ✅ Checking your understanding

You asked: *"So package-by-feature = modular monolith. And if I decide one feature is getting more
traffic and move it to a different compute, that's when it's called a microservice?"*

**Mostly right. Two corrections, both worth having.**

**Correction 1 — package-by-feature is *necessary* but not *sufficient*.**

Package-by-feature is the **file organisation**. A modular monolith also needs the **discipline**:

| Package-by-feature gives you | A modular monolith also requires |
|---|---|
| Related files in one folder | Modules talk only through a small **public API** |
| A place to put things | Each module **owns its tables** — no reaching into another's |
| | Internal classes are **package-private**, not public (§5.0) |

Without the discipline you have a monolith with tidier folders. It's the *boundaries* — not the
folders — that make it modular. Folders make the boundaries **possible**; you still have to honour
them.

**Correction 2 — "different compute" alone is NOT a microservice.**

This is the important one. Running the same thing on more machines is just **horizontal scaling**:

```
   ❌ NOT microservices — this is a scaled MONOLITH:

      load balancer
       ├──► [ hotel-pms.jar — booking+room+guest+payment ]   machine 1
       ├──► [ hotel-pms.jar — booking+room+guest+payment ]   machine 2
       └──► [ hotel-pms.jar — booking+room+guest+payment ]   machine 3
                              └──────────┬──────────┘
                                    ONE database

      Three machines. Different compute. Still a monolith — it's the SAME artifact,
      and you cannot scale booking without also scaling payment.
```

**A microservice needs three things**, and compute is only a consequence of them:

| # | Requirement | The real test |
|---|---|---|
| 1 | **Separately deployable** | Can you ship a new `availability` without rebuilding or redeploying `booking`? |
| 2 | **Owns its own data** | Does it have its **own database** that no other service queries directly? |
| 3 | **Talks over the network** | Do others reach it via HTTP/messaging — never a method call? |

```
   ✅ Microservices:

      [ availability.jar ] ×4  ──HTTP──►  [ booking.jar ] ×1
             │                                    │
          own DB (or read replica)             own DB
```

**Requirement 2 is the one people skip, and it's the one that matters.** Two "services" sharing one
database are not independent — a schema change breaks both, and you still can't deploy them
separately. That anti-pattern has a name: **the distributed monolith** — all the operational cost of
microservices, none of the benefit. It is the most common way microservice projects fail.

**So, to answer you directly:**

| What you do | What it's called |
|---|---|
| Group code by feature in one app | **Modular monolith** ✅ *(this is our plan)* |
| Run 3 copies of that app behind a load balancer | Monolith, **horizontally scaled** |
| Extract `availability` into its own deployable, with its own data | **Microservice** |

## 31. Your auto-scaling example, worked through

You described exactly the right scenario, so let's take it seriously.

**The observation:** "check availability" is hit far more often than "book a room". Browsing is cheap
and frequent; booking is rare and expensive. Realistically 100:1 or worse.

**In a monolith** you can only scale the whole thing:

```
   1000 availability checks/s + 10 bookings/s
              ↓
   ┌─────────────────────────┐  ┌─────────────────────────┐  ┌─────────────────────────┐
   │ ENTIRE APP              │  │ ENTIRE APP              │  │ ENTIRE APP              │
   │ booking+room+guest+pay  │  │ booking+room+guest+pay  │  │ booking+room+guest+pay  │
   └─────────────────────────┘  └─────────────────────────┘  └─────────────────────────┘
      3 full copies — you paid for 3× the payment module you didn't need
```

**Split out the hot path** and you scale only what's hot:

```
   1000 checks/s ─────►  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐
                         │availabil.│ │availabil.│ │availabil.│ │availabil.│   ← 4 instances
                         └──────────┘ └──────────┘ └──────────┘ └──────────┘      (auto-scaled)
   10 bookings/s ─────►  ┌──────────┐
                         │ booking  │                                            ← 1 instance
                         └──────────┘
```

**And this is where it gets genuinely interesting for your project.** Availability-checking is
**read-only** — which unlocks options booking never has:

- it can be served from a **read replica** of the database,
- it can be **cached** aggressively (an availability answer can be seconds stale and still useful),
- it doesn't need transactions, locking, or write access at all,
- so it can scale nearly linearly and cheaply.

#### First — what does "idempotent" mean?

**An operation is idempotent if doing it many times has the same effect as doing it once.**

The everyday example: a **lift call button**. Press it once, or jab it fifteen times — the lift comes
once. The button is idempotent. Now compare a **vending machine**: press twice, get two snacks and
pay twice. Not idempotent.

| Operation | Idempotent? | Why |
|---|---|---|
| `SELECT * FROM hotel_rooms WHERE id = 5` | ✅ **Yes** | Reading changes nothing. Read it 1,000× → same answer, same state. |
| "Is room 12 free on Aug 5–9?" | ✅ **Yes** | A question. Asking doesn't book anything. |
| `DELETE FROM bookings WHERE id = 9` | ✅ **Yes** | Run it twice — after the first, it's already gone. Same end state. |
| `SET status = 'CANCELLED' WHERE id = 9` | ✅ **Yes** | Setting an absolute value. Repeating is harmless. |
| **`INSERT INTO hotel_bookings ...`** | ❌ **No** | Run it twice → **two bookings**. Different state. |
| `UPDATE rooms SET rate = rate + 100` | ❌ **No** | Relative change. Twice = +200. |

**Why this matters enormously in distributed systems.** Networks fail *in the middle*:

```
   Client ──── "book room 12" ────► Server
                                       │  ✅ booking created in DB
   Client ◄───  ✗ TIMEOUT  ────────────┘     (response lost on the way back)

   The client has NO IDEA whether it worked. Should it retry?
     • If the operation is IDEMPOTENT → retry freely. Worst case, nothing extra happens.
     • If it is NOT                   → retrying might double-book. You're stuck.
```

**Idempotency is what makes retrying safe** — and in a distributed system, retrying is unavoidable,
because "no response" and "it failed" are indistinguishable from the outside. This is exactly why
`GET` is idempotent in HTTP and `POST` is not, and why "idempotency keys" exist for payment APIs.

#### Now the asymmetry, spelled out

```
   ┌──── CHECK AVAILABILITY ────────────┐   ┌──── BOOK A ROOM ──────────────────┐
   │ • a READ — changes nothing         │   │ • a WRITE — changes the world     │
   │ • idempotent → safe to retry       │   │ • NOT idempotent → retry = danger │
   │ • stale answer is acceptable       │   │ • must be exactly right, always   │
   │   ("probably free, confirm later") │   │   (double-booking = a real guest  │
   │ • no transaction needed            │   │    with no room)                  │
   │ • no locking needed                │   │ • needs transactions + locking    │
   │ • can hit a read replica           │   │ • must hit the PRIMARY database   │
   │ • can be cached for 30s            │   │ • cannot be cached at all         │
   │ • 1000/sec, trivially              │   │ • 10/sec, and that's fine         │
   └────────────────────────────────────┘   └───────────────────────────────────┘
        CHEAP · SCALES ALMOST FREELY              EXPENSIVE · SCALES BADLY
```

**The insight in one sentence:** *reads and writes are not two versions of the same thing — they have
opposite properties, so you should scale them by opposite strategies.*

Reads scale by **making more copies** (replicas, caches) because copies can't hurt each other.
Writes scale badly *because* they must agree with each other — and agreement requires coordination,
which is exactly what you cannot copy your way out of.

**This is why your instinct was a good one:** you spotted that the two halves of the same feature have
genuinely different needs. That observation is the seed of read replicas, caching layers, and
eventually CQRS (Command Query Responsibility Segregation — a pattern whose entire premise is
"separate the read path from the write path"). You arrived at it on your own, which is a good sign.

## 32. What breaks when you split

Microservices are not free. The honest costs — and one of them lands directly on this project's
centrepiece:

**① Your no-double-booking guarantee gets much harder.**

In a monolith, one database transaction plus a Postgres constraint makes double-booking *physically
impossible*. Split `availability` and `booking` into separate services with separate databases and
that single unbreakable guarantee becomes a **distributed** problem: two services, two databases, no
shared transaction. You'd be reaching for **sagas**, **compensating transactions**, or **idempotency
keys** — all of which are *weaker* than what one `EXCLUDE` constraint gives you for free.

**This is the most important thing to understand about microservices: they trade a strong, cheap
correctness guarantee for scalability.** That trade is sometimes worth it. It is never free.

**② Everything else that gets harder:**

| In a monolith | Across services |
|---|---|
| A method call — nanoseconds, never fails | A network call — milliseconds, **fails regularly** |
| One transaction, ACID | Distributed transaction / saga / eventual consistency |
| One stack trace | Distributed tracing across N services |
| One deploy | N deploys, N versions, N compatibility matrices |
| Refactor across boundaries freely | API versioning, backward compatibility forever |
| One log file | Centralised log aggregation |
| Run it locally with F5 | Docker Compose / Kubernetes just to start |

## 33. When to actually split

The industry-consensus answer, learned the hard way over a decade:

> **Start with a modular monolith. Split only when you have a concrete, measured reason.**

**Legitimate reasons to split:**
- One component genuinely needs different scaling (your availability example — *if measured*)
- Separate teams need to deploy independently without coordinating
- One component needs different technology or a different resource profile
- Regulatory isolation requirements

**Bad reasons** (extremely common):
- "Microservices are modern"
- "It'll scale better someday" — with no measurement
- "Monoliths are bad"

**Martin Fowler's rule of thumb:** you cannot design good service boundaries before you understand the
domain, and you don't understand the domain until you've built it. Split *later*, from a well-modularised
monolith — not upfront.

**What we'll do in this project:**
- Build a **modular monolith** with package-by-feature.
- Keep module boundaries genuinely clean — each module owns its data.
- Never split for real (out of scope), but **the code will be structured so that we could**, and at
  the booking deep-dive we'll discuss concretely what would break if we did.

That gets you the microservices *understanding* without the microservices *tax*.

---

# Part 9 — The roadmap

## 34. Checklist for this phase

What we'll do together, in order. Each step: **understand → you decide → you edit.**

| Step | What | You'll decide | You'll learn |
|---|---|---|---|
| **5.1** | Read this document | — | Layout, Maven, POM, properties |
| **5.2** | Reconcile `.gitignore` | Which rules survive; the `.mvn/` trap | What belongs in version control |
| **5.3** | Tidy `pom.xml` | Metadata tags; DevTools yes/no | Dependencies, scopes, parent POM |
| **5.4** | Package structure | **by-layer vs by-feature** | Modularization; the microservice door |
| **5.5** | Write `application.properties` | Which DB; `ddl-auto`; secrets; logging | Datasource, JDBC URLs, profiles |
| **5.6** | Start the app | — | The startup log: Hikari, Hibernate, Tomcat |
| **5.7** | First `@Entity`: `Hotel` | Field types, annotations, mapping | JPA mapping; `validate` proving it |

Then step 6 (CRUD APIs) and step 7 (the concurrency deep-dive) — the real destination.

**Open questions for you to answer before we start editing:**

1. **Package structure** — by-layer or by-feature? (§29)
2. **Which Postgres** — Docker on 5433, or native on 5432? (§28①)
3. **`ddl-auto`** — `validate` or `update`? (§28②)
4. **Password handling** — plain file, `application-local.properties`, or env var? (§28⑤)
5. **DevTools** — add it for faster iteration, or keep the moving parts minimal? (§25③)

---

## Related docs

| Doc | Covers |
|---|---|
| `docs/spring-and-maven-notes.md` | JDK/Temurin, Spring vs Boot, starters, Lombok/DevTools, the CLI command |
| `docs/how-it-all-works.md` | The runtime picture: OS → threads → Tomcat → beans → Hibernate → Postgres |
| `docs/HLD.md` | Architecture and technology rationale |
| `docs/db/schema.sql` | The DDL — the source of truth for the schema |
| `docs/docker-notes.md` | Docker; the `pms-postgres` container |

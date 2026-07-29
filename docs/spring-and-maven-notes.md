# Spring Boot & Maven — Learning Notes (Hotel PMS)

The hows/whats/whys behind the tooling, for a first-timer. Read once top-to-bottom.

## Contents

1. [JDK and Temurin](#jdk-and-temurin)
2. [Maven — the build and dependency tool](#maven--the-build-and-dependency-tool)
3. [The pom.xml and the Maven wrapper](#the-pomxml-and-the-maven-wrapper)
4. [Spring vs Spring Boot](#spring-vs-spring-boot)
5. [Starters and our chosen dependencies](#starters-and-our-chosen-dependencies)
6. [Lombok and DevTools](#lombok-and-devtools)
7. [Generating the project from the CLI](#generating-the-project-from-the-cli)

---

## JDK and Temurin

- **Java the language** has a _specification_; multiple vendors build _distributions_ (JDKs) that implement it.
- **JDK** = Java Development Kit = the **compiler (`javac`) + tools + the runtime**. (A plain **JRE** only _runs_ apps; a JDK also _builds_ them. You have the full JDK — `javac` works.)
- **Temurin** = the free, open-source OpenJDK build from the **Eclipse Adoptium** project (formerly AdoptOpenJDK) — one of the most popular free JDKs. So _"OpenJDK 21 Temurin"_ = "the Adoptium build of OpenJDK version 21."
- Other distributions of the _same_ Java 21: Oracle JDK, Amazon Corretto, Azul Zulu, Microsoft Build of OpenJDK. They differ in support/licensing, not the language.
- **21** is an **LTS** (Long-Term Support) release — a stable, well-supported choice.

## Maven — the build and dependency tool

Maven does three big jobs for a Java project:

1. **Dependency management** — you _declare_ the libraries you need; Maven downloads them (and _their_
   dependencies, transitively) from **Maven Central** (the public library repository) and caches them
   locally in `~/.m2/repository`. No manual JAR hunting.
2. **Build lifecycle** — standard phases run in order: `validate → compile → test → package → verify →
install → deploy`. Common commands: `mvn compile`, `mvn test`, `mvn package` (makes the JAR),
   `mvn spring-boot:run` (run the app), `mvn clean` (wipe `target/`).
3. **Convention** — a standard folder layout (`src/main/java`, `src/main/resources`, `src/test/java`),
   so every Maven project looks the same.

**How is it "already installed"?** Maven is a standalone tool you install once (manual unzip, a package
manager like Chocolatey/Scoop, an SDK manager, or bundled by an IDE). Yours lives at
`C:\Program Files\Maven\apache-maven-3.9.16` and is on your PATH — so _something_ put it there earlier.
You don't strictly need a global Maven, though (see the wrapper below).

## The pom.xml and the Maven wrapper

- **`pom.xml`** (Project Object Model) is Maven's config file — the **heart** of the project. It declares:
  the parent (`spring-boot-starter-parent`, which sets sane defaults + dependency versions), your
  `groupId`/`artifactId`/`version`, the Java version, your **dependencies**, and build **plugins**
  (e.g. `spring-boot-maven-plugin`, which builds the runnable JAR).
- **Maven wrapper** (`mvnw`, `mvnw.cmd`, `.mvn/`) — ships inside a generated project and **pins a specific
  Maven version per project**, downloading it automatically if absent. This means a teammate builds with
  the _exact_ Maven version _without installing Maven_. You run `.\mvnw ...` instead of `mvn ...`.
  (Same "reproducibility" idea as Docker, one layer up.)

## Spring vs Spring Boot

- **Spring Framework** = a large Java framework whose core idea is **Dependency Injection / IoC**
  (Inversion of Control): you declare components (**beans**) — services, repositories, controllers — and
  Spring _wires them together_ for you instead of you `new`-ing everything by hand. On top of that core
  sit modules: Spring MVC (web), Spring Data (databases), Spring Security, etc.
- **Spring Boot** = a layer that makes Spring _easy_:
  - **Auto-configuration** — configures sensible defaults based on what's on your classpath (see a
    Postgres driver? wire a datasource). Kills the old mountain of XML config.
  - **Starters** — curated dependency bundles (one line pulls a coherent set — see below).
  - **Embedded server** — Tomcat is built _into_ your app, so you run a single JAR (`java -jar app.jar`);
    no separate server to install.
  - **Convention over configuration** — it "just works" with minimal setup.
- **`@SpringBootApplication`** on the main class turns all of this on.

## Starters and our chosen dependencies

A **starter** is a curated bundle so you don't hand-pick versions. Ours:

| Starter / dependency             | What it pulls in                              | Why we want it                                       |
| -------------------------------- | --------------------------------------------- | ---------------------------------------------------- |
| `spring-boot-starter-web`        | Spring MVC + embedded Tomcat + Jackson (JSON) | build REST endpoints (the Controller layer)          |
| `spring-boot-starter-data-jpa`   | Hibernate + Spring Data JPA                   | map Java objects ↔ tables; free CRUD repositories    |
| `postgresql` (driver)            | the PostgreSQL JDBC driver                    | let the app actually talk to Postgres                |
| `spring-boot-starter-validation` | Hibernate Validator (Bean Validation)         | enforce rules on incoming data (`@NotNull`, `@Size`) |

## Lombok and DevTools

Two _optional_ add-ons we're skipping **for now** (deliberately):

- **Lombok** — a library that _auto-generates boilerplate_ (getters, setters, constructors, `toString`,
  `equals`/`hashCode`, builders) from annotations like `@Getter`, `@Data`, `@Builder`. Pros: far less
  boilerplate. Cons: it's compile-time "magic," needs an IDE plugin, and _hides_ code — bad while you're
  still learning what that code is. We'll add it later, once writing the boilerplate has taught you what
  it does.
- **DevTools** (`spring-boot-devtools`) — development conveniences: **auto-restart** when you change code,
  live reload, dev-friendly defaults. Pros: faster feedback loop. Cons: minor extra moving parts and
  occasional classloader quirks. Skipped now for predictability; trivial to add anytime.

## Generating the project from the CLI

Spring Initializr (the start.spring.io website) also has an **HTTP API** — you can generate the same zip
from the terminal. On Windows PowerShell, use **`curl.exe`** (plain `curl` there is an alias for a
different command):

```powershell
curl.exe -s -G "https://start.spring.io/starter.zip" `
  -d "type=maven-project" -d "language=java" -d "javaVersion=21" `
  -d "groupId=com.pms" -d "artifactId=hotel-pms" -d "name=hotel-pms" `
  -d "packageName=com.pms.hotel" `
  -d "dependencies=web,data-jpa,postgresql,validation" `
  -o starter.zip
```

Each `-d` is a **form parameter** the Initializr API understands:
| Param | Meaning |
|---|---|
| `type` | `maven-project` (vs `gradle-project`) |
| `language` | `java` |
| `javaVersion` | `21` |
| `bootVersion` | _(omitted → Initializr uses the current stable Spring Boot)_ |
| `groupId` / `artifactId` / `name` | project identity (reverse-domain group, project name) |
| `packageName` | the base Java package (`com.pms.hotel`) |
| `dependencies` | comma-separated starter IDs (`web,data-jpa,postgresql,validation`) |
| `-o` | output file to save the zip to |

Then unzip it: `Expand-Archive starter.zip -DestinationPath <folder>`.
(You can see every valid parameter/value at `https://start.spring.io/metadata/client`.)

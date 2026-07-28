# Docker — Learning Notes (Hotel PMS)

A plain-language primer for a first-timer. Read top to bottom once; come back to the command
tables as reference.

## Contents
1. [The core model](#the-core-model)
2. [Where images come from](#where-images-come-from)
3. [What docker run actually does](#what-docker-run-actually-does)
4. [Dockerfile vs docker-compose vs docker run](#dockerfile-vs-docker-compose-vs-docker-run)
5. [Building your own image](#building-your-own-image)
6. [The Postgres command dissected](#the-postgres-command-dissected)
7. [Port mapping explained](#port-mapping-explained)
8. [Connecting to a containerized service](#connecting-to-a-containerized-service)
9. [Managing a background container](#managing-a-background-container)
10. [Command glossary](#command-glossary)

---

## The core model
- **Image** = a read-only *blueprint* (an app + everything it needs to run, packaged together). Like a **class**.
- **Container** = a *running instance* of an image. Like an **object**. One image → many containers.
- **Registry (Docker Hub)** = a public library of ready-made images (`postgres`, `hello-world`, `ubuntu`).
- **Docker Desktop** = runs the **engine (daemon)** — the background server that actually does the work.
  The `docker` command is a **client** that talks to it. Engine off (Desktop closed) → CLI can't connect.

## Where images come from
*Answers: "where did we create an image?"*

You did **not** build an image — and that's normal. Images come from two places:

1. **Pre-built, pulled from a registry.** Someone already built it; you just download and run.
   `postgres:18` and `hello-world` are these. Reusing an official blueprint.
2. **Built by you, via a Dockerfile.** Only for *your own code* (e.g., your Spring Boot app), where
   no ready-made image exists. **That** is when you write a Dockerfile.

So your earlier memory — "first create an image, then run it" — is true only for *your own app*.
For ready-made software like Postgres, you skip building entirely; the image already exists.

## What docker run actually does
`docker run postgres:18` is three steps in one command:
1. **Pull** the image if it isn't already on your machine (from Docker Hub).
2. **Create** a container from that image.
3. **Start** the container.

(That's why the *first* `hello-world` run showed `Pulling…` and the *second* didn't — the image was
cached after the first pull.)

## Dockerfile vs docker-compose vs docker run
| Tool | Purpose | When we use it |
|---|---|---|
| `docker run` | pull + create + start **one** container | running a ready-made image (now, for Postgres) |
| **Dockerfile** | recipe to **build your own** image | packaging YOUR app (later, Spring Boot) |
| **docker-compose** | define & run **many** containers together as a stack | app + DB together (later) |

None of these is "required first." To run a ready-made image, `docker run` alone is enough.

## Building your own image
*Answers: "I don't see the difference between a Dockerfile and an image."*

Same three stages you already know from programming:

| Docker | Programming equivalent | What it is |
|---|---|---|
| **Dockerfile** | source code | a text file of *instructions* to assemble an image |
| **Image** | compiled executable | the built, static, self-contained bundle |
| **Container** | running process | the image actually running |

**Recipe → dish → the dish being served.** The Dockerfile is the recipe (text). `docker build`
cooks it into an image (the packaged dish). `docker run` serves it as a container (a running instance).

You don't just "point at your JAR." A Dockerfile for the Spring app gives a *recipe*: start from a
Java runtime, copy your built jar in, and set how to launch it:
```dockerfile
FROM eclipse-temurin:21-jre               # base image: a Java runtime
COPY target/pms.jar /app/pms.jar          # put YOUR built jar inside
ENTRYPOINT ["java","-jar","/app/pms.jar"] # how to start it
```
Then:
```
docker build -t pms-app:1.0 .   # runs the recipe -> produces the IMAGE 'pms-app:1.0' (a LOCAL tag)
docker run pms-app:1.0          # starts a CONTAINER from that image
```
The image is **self-contained**: Java runtime + your jar + start command, one bundle that runs
identically anywhere. Postgres's official image was built the same way by the Postgres team — you
just skipped to `run` because they'd already built and published it to Docker Hub.

## The Postgres command dissected
```
docker run --name pms-postgres -e POSTGRES_PASSWORD=YOURPASS -p 5433:5432 -v pms_pgdata:/var/lib/postgresql/data -d postgres:18
```
- `--name pms-postgres` — a friendly name for the container (else Docker assigns a random one).
- `-e POSTGRES_PASSWORD=...` — sets an **environment variable inside the container**; the Postgres
  image reads it on first startup to set the `postgres` superuser's password. (Env vars = how you
  configure a container from the outside without editing the image.)
- `-p 5433:5432` — **publish a port**, format `HOST:CONTAINER`. See the next section.
- `-v pms_pgdata:/var/lib/postgresql/data` — a **named volume** holding the DB's data files, so data
  survives even if the container is deleted.
- `-d` — **detached**: run in the background (a server, not a one-shot).
- `postgres:18` — **image:tag** — `postgres` is the image name on Docker Hub, `18` is the version tag.

## Port mapping explained
*Answers your two guesses — the second one is correct.*

The container runs its **own** Postgres on **its own** port `5432`, inside its isolated network.
Your **native** Postgres is *not* involved at all.

```
   Your laptop (host)                         Container (isolated)
 ┌────────────────────────┐                 ┌───────────────────────┐
 │  pgAdmin / Spring app   │                 │  Postgres listening    │
 │        │                │    -p 5433      │      on  :5432         │
 │        ▼                │   :5432         │        ▲               │
 │   localhost:5433  ──────┼────────────────►│────────┘               │
 └────────────────────────┘   forwards to   └───────────────────────┘
   (your native Postgres is still on :5432 — separate and untouched)
```
- Inside the container, Postgres listens on `5432` (containers have their own private port space).
- `-p 5433:5432` forwards host **5433** → container **5432**.
- From your laptop you connect to **`localhost:5433`**.
- We chose host `5433` (not `5432`) only to avoid clashing with your *native* Postgres on `5432`.
  Two things can't listen on the same host port; inside the container there's no clash.

## Connecting to a containerized service
*Answers: "how do I actually connect to the Postgres inside the container?"*

Two callers, two addresses:

| Who is connecting | Address to use | Why |
|---|---|---|
| **Your laptop** (pgAdmin, psql, a non-Docker app) | `localhost:5433` | the *published host port* from `-p 5433:5432` |
| **Another container** on the same Docker network (your Spring app via compose) | `pms-postgres:5432` | inside Docker, containers reach each other by **name** on the **internal** port |

From *outside* Docker → `localhost:<host-port>` (5433). From *inside* another container →
`<container-name>:<internal-port>` (5432); the published 5433 is irrelevant there. That's why, when
we wire the Spring app with compose, its DB URL will be `jdbc:postgresql://pms-postgres:5432/hotel_pms`,
**not** 5433.

## Managing a background container
*Answers: "if it runs detached, how do I reach it?"*

It isn't lost — Docker tracks it. Handy commands:
| Command | What it does |
|---|---|
| `docker ps` | list **running** containers (name, ports, id). `ps` = "process status," a Unix term. |
| `docker ps -a` | list **all** containers, including stopped ones. |
| `docker logs <name>` | print the output captured **so far**, then return to the prompt (a snapshot). |
| `docker logs -f <name>` | **follow** the output live (streams new lines until Ctrl+C). |
| `docker exec -it <name> bash` | open a shell **inside** the running container. |
| `docker exec -it <name> psql -U postgres` | open psql inside the container. |
| `docker stop <name>` / `docker start <name>` | stop / restart the container. |
| `docker images` | list images cached locally. |

A background database uses CPU/RAM only while working — it idles cheaply.

## Command glossary
- **daemon / engine** — the background Docker server (in Docker Desktop). Does the real work.
- **pull** — download an image from a registry.
- **tag** — a version label on an image (`postgres:18`, `postgres:16`; `:latest` is the default).
- **detached (`-d`)** — running in the background, not blocking your terminal.
- **volume** — storage that lives outside the container so data persists.
- **publish (`-p`)** — expose a container's internal port on a host port.
- **image** — read-only blueprint · **container** — a running instance of an image.

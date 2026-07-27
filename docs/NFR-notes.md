# Non-Functional Requirements — Awareness Map (not targets)

> **Why this file exists.** We are NOT writing formal NFR *targets/SLAs* (e.g. "respond in <200ms",
> "99.9% uptime") — setting hard numbers before the system exists is premature and teaches little.
> But NFRs are the *reason* most infrastructure tools exist, and understanding that "why" IS a core
> learning goal. So this is a **map of concerns → the tools/concepts that address them → where each
> touches our project.** We revisit these intentionally, when relevant — not all at once.

| NFR concern | What it means | Tools / concepts that serve it | Where it shows up in *our* project |
|---|---|---|---|
| **Performance / latency** | Each request is fast | Indexing, query tuning, caching (**Redis**), connection pooling | Our composite indexes on bookings; the availability query |
| **Throughput / responsiveness** | Handle many requests; don't make the client wait on slow work | **Async processing**, message queues (**RabbitMQ / Kafka**), background jobs | Notifications must not block a booking response (backlog) |
| **Scalability** | Serve more load by adding capacity | Vertical vs. **horizontal scaling**, load balancers, stateless services (**JWT**) | Modular monolith now, extractable later; stateless auth phase |
| **Availability / reliability** | Stays up; survives failures | Redundancy, health checks, retries, graceful degradation | Discussed conceptually; not built |
| **Security** | Right people, right data only | **Spring Security + JWT** (authN/authZ), tenant isolation (`hotel_id`, Postgres **RLS**), input validation | Multi-tenancy isolation is our central security property |
| **Data consistency / integrity** | Data is always valid, even under concurrency | **Transactions / ACID**, DB constraints, pessimistic/optimistic **locking**, `EXCLUDE` constraint | THE booking deep-dive — no double-booking under concurrency |
| **Observability** | You can see what the system is doing | Logging, metrics, tracing | Basic logging when we build services |
| **Maintainability / extensibility** | Easy to change and extend | Layered architecture, **design patterns** (Strategy/State/Observer), clean module boundaries | The notification "plugin" is our extensibility test |

## The one principle tying these together
Most NFR work follows: **build it correct first → measure where it actually hurts → then apply the
tool that fixes *that* pain.** Indexing before you have queries, or caching before you have a
bottleneck, is guessing. Every tool above is an *answer to a measured problem* — learn the problem
first, and the tool makes sense.

# Hibernate playground

A small library domain for experimenting with Hibernate 7 (via Spring Boot 4 / Spring Data JPA) against Postgres.

## Setup

```bash
./pg.sh start          # Postgres in Podman on localhost:5432
./mvnw test            # run all lessons
./mvnw spring-boot:run # or start the app and use the REST endpoints below
```

Flyway creates the schema (`V1__library_schema.sql`) and seed data (`V2__library_seed_data.sql`).
Hibernate only validates the schema (`ddl-auto=validate`), so every mapping change needs a migration.

## Domain

```
Publisher 1 ──< Book >── N:M ──< Author        (book_author join table, owned by Book.authors)
                 │
                 └──< Review                   (cascade ALL + orphanRemoval, owned by Review.book)
```

- `Book` has `@Version` (optimistic locking), a `@NaturalId` isbn, an enum stored as a string, and a lazy `@ManyToOne` publisher.
- Seed ids are 1–11; sequences start at 1000 with `INCREMENT BY 50` to match `allocationSize = 50`.

## What is logged

Configured in `application.properties`:

- every SQL statement, formatted, with a comment showing where it came from
- bind parameter values (`org.hibernate.orm.jdbc.bind=TRACE`)
- per-session metrics: statements prepared and executed, JDBC batches, flushes (`org.hibernate.session.metrics`)

`open-in-view` is **off**, so lazy loading outside a transaction fails loudly instead of silently running queries.

## Lessons (`src/test/java/.../lessons`)

Each test runs in a transaction that is rolled back, so change them freely. The `statements()` helper counts JDBC statements.

| Lesson | Shows |
|---|---|
| `Lesson01PersistenceContextTest` | first-level cache, dirty checking, detached entities, `merge`, when `persist` inserts, auto-flush before queries |
| `Lesson02FetchingTest` | N+1 (11 statements for 8 books), fetch join, `@EntityGraph`, DTO projection, proxies, `getReference`, `LazyInitializationException` |
| `Lesson03AssociationsTest` | cascade persist, orphan removal, owning vs inverse side, delete cascades |
| `Lesson04LockingAndBulkTest` | stale `merge` → `OptimisticLockException`, bulk update bypassing the persistence context, JDBC insert batching |

## REST endpoints

```bash
curl localhost:8080/books/naive        # N+1: watch the log fill up
curl localhost:8080/books/fetch-join   # same JSON, one query
curl localhost:8080/books/summaries    # DTO projection with average rating
curl localhost:8080/books/1
curl -X PATCH 'localhost:8080/books/1/price?value=12.50'
curl -X POST localhost:8080/books/1/reviews -H 'Content-Type: application/json' -d '{"rating":5,"comment":"Great"}'
```

These **do** change the database. `./pg.sh reset` wipes it; Flyway recreates it on the next start.

Notice that `PATCH` returns the *old* `version`: the DTO is built before the transaction commits, and the flush (UPDATE and version bump) happens at commit.

## Exercises

1. Set `spring.jpa.properties.hibernate.default_batch_fetch_size=10` and rerun `nPlusOneQueries`. How many statements now, and what do the SQL `in (...)` lists look like?
2. Change `Book.authors` from `Set` to `List` and remove an author. Compare the SQL on `book_author`.
3. Remove `fetch = FetchType.LAZY` from `Book.publisher`. Which lesson assertions break, and why?
4. Switch `Book` to `GenerationType.IDENTITY` (needs a migration). What happens to `insertsAreSentInJdbcBatches` and `persistAssignsIdButInsertHappensAtFlush`?
5. Turn `open-in-view` back on, then make `/books/naive` return entities directly instead of DTOs. What changes?
6. Add `@org.hibernate.annotations.BatchSize(size = 10)` to `Book.reviews` and write a test that loads all books and their reviews.

# Getting Started

### Local PostgreSQL database

The app is meant to run against PostgreSQL in a local Podman container named `tiny-postgres`
(image `docker.io/library/postgres:alpine`, PostgreSQL 18).

| Setting  | Value                                            |
|----------|--------------------------------------------------|
| JDBC URL | `jdbc:postgresql://localhost:5432/hibernate_test` |
| User     | `postgres`                                       |
| Password | `postgres`                                       |
| Database | `hibernate_test`                                 |
| Volume   | `tiny-postgres-data` (data survives container removal) |

To connect the app, add the `org.postgresql:postgresql` dependency (runtime scope) to `pom.xml` and set in
`src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/hibernate_test
spring.datasource.username=postgres
spring.datasource.password=postgres
```

The container does not start automatically after a reboot; run `./pg.sh start` first.

#### Managing the container with `pg.sh`

`pg.sh` in the project root wraps the common Podman operations:

```
./pg.sh start            # create the container if missing, start it, wait until ready
./pg.sh stop | restart
./pg.sh status           # container state and whether Postgres accepts connections
./pg.sh logs [-f]        # show logs; -f follows them
./pg.sh psql             # interactive SQL shell in hibernate_test
./pg.sh exec "select 1"  # run a single SQL statement
./pg.sh dump [file]      # back up the database (default: hibernate_test-<timestamp>.sql)
./pg.sh restore <file>   # load a backup; replaces existing tables
./pg.sh reset            # delete all data and recreate (asks for confirmation)
./pg.sh remove           # delete the container, keep the data volume
./pg.sh purge            # delete the container and the data volume (asks for confirmation)
./pg.sh url              # print the JDBC URL and credentials
```

Defaults can be overridden with environment variables: `PG_CONTAINER`, `PG_IMAGE`, `PG_VOLUME`, `PG_PORT`,
`PG_USER`, `PG_PASSWORD`, `PG_DB` (e.g. `PG_PORT=5433 ./pg.sh start`). Run `./pg.sh help` for the full usage.

### Database migrations (Flyway)

The database schema is managed by Flyway (`spring-boot-starter-flyway` + `flyway-database-postgresql`), not by
Hibernate. Hibernate runs with `spring.jpa.hibernate.ddl-auto=validate`: it only checks that the entities match the
tables and fails on startup if they don't.

Migration scripts live in `src/main/resources/db/migration` and run automatically when the app starts, in version
order. Flyway records every applied script in the `flyway_schema_history` table and runs each one only once.

Scripts must be named `V<version>__<description>.sql` (two underscores), e.g.:

```
V1__create_tables.sql
V2__add_author_email.sql
```

Rules:

* Never edit a script that has already been applied; Flyway compares checksums and refuses to start. Add a new
  version instead.
* When you add or change an entity, write the matching migration too, or `validate` will fail.
* To start over locally, wipe the database with `./pg.sh reset`; all migrations run again on the next start.

To see which migrations have been applied:

```
./pg.sh exec "select version, description, success, installed_on from flyway_schema_history order by installed_rank"
```

### Reference Documentation

For further reference, please consider the following sections:

* [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/4.1.1/maven-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/4.1.1/maven-plugin/build-image.html)
* [Spring Data JPA](https://docs.spring.io/spring-boot/4.1.1/reference/data/sql.html#data.sql.jpa-and-spring-data)
* [Flyway database migrations](https://docs.spring.io/spring-boot/4.1.1/how-to/data-initialization.html#howto.data-initialization.migration-tool.flyway)
* [Spring Boot DevTools](https://docs.spring.io/spring-boot/4.1.1/reference/using/devtools.html)
* [Spring Web](https://docs.spring.io/spring-boot/4.1.1/reference/web/servlet.html)

### Guides

The following guides illustrate how to use some features concretely:

* [Accessing Data with JPA](https://spring.io/guides/gs/accessing-data-jpa/)
* [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
* [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)
* [Building REST services with Spring](https://spring.io/guides/tutorials/rest/)

### Maven Parent overrides

Due to Maven's design, elements are inherited from the parent POM to the project POM.
While most of the inheritance is fine, it also inherits unwanted elements like `<license>` and `<developers>` from the
parent.
To prevent this, the project POM contains empty overrides for these elements.
If you manually switch to a different parent and actually want the inheritance, you need to remove those overrides.


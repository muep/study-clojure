End-to-end with postgresql - temporal types

# Scope

Assuming a stack, approximately:
- http-kit
- reitit
- malli
- jeesql
- hikari-cp
- jdbc
- org.postgresql/postgresql

Write a program that for points in time and durations
- Has some reasonable format in HTTP messages
- In the backend, uses [types](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/time/package-summary.html) from `java.time`
  - `java.time.Instant`
  - `java.time.Duration`
- In the database, uses the [usual](https://www.postgresql.org/docs/current/datatype-datetime.html)
  - `timestamp with time zone`
  - `interval`

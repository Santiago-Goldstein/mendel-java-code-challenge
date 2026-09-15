---
name: transactions-dev
description: Start, stop, reset, test, verify, and troubleshoot the Mendel Transactions Java challenge. Use this skill when asked to start or "levantar" the challenge, run tests, verify Docker/MySQL/API health, restart the project, reset the local database, inspect logs, or diagnose startup problems in this repository.
---

# Mendel Transactions Development Skill

Operate the Mendel Transactions challenge using the deterministic scripts bundled with this skill.

Do not manually recreate the complete startup workflow when one of these scripts already implements it.

## Project architecture

This repository uses:

- Java 17.
- Spring Boot.
- Maven Wrapper.
- MySQL 8.4.
- Docker Compose.
- Flyway.
- Spring Data JPA / Hibernate.
- Testcontainers.
- Spring Boot Actuator.

Docker Compose services are named:

- `mysql`
- `api`

The API health endpoint is:

`/actuator/health`

The API container must connect to MySQL through the Compose hostname `mysql`, not `localhost`.

## Platform selection

First determine the operating system.

On Windows, use:

```powershell
powershell -ExecutionPolicy Bypass -File ".\.claude\skills\transactions-dev\scripts\dev.ps1" -Action <action>
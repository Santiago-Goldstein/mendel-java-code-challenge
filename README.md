# Transactions API

REST API built with Java and Spring Boot for storing and querying linked transactions.

The application stores its data in memory and provides operations for:

* Creating or replacing transactions.
* Retrieving transaction IDs by type.
* Calculating the total amount of a transaction and all of its transitively linked descendants.
* Importing multiple transactions from a CSV file.
* Preventing cyclic parent relationships before any data is persisted.

## Tech Stack

* Java 17
* Spring Boot 4.1.1
* Maven
* Spring Web MVC
* Jakarta Bean Validation
* Apache Commons CSV
* JUnit 5
* AssertJ
* Mockito
* MockMvc
* Docker

## Architecture

The application follows a layered architecture with clear separation of responsibilities.

```text
HTTP Request
     |
     v
TransactionController
     |
     +------------------+
     |                  |
     v                  v
TransactionService   TransactionCsvParser
     |
     v
TransactionRepository
     |
     v
InMemoryTransactionRepository
     |
     v
ConcurrentHashMap<Long, Transaction>
```

Responsibilities are separated as follows:

* **Controller**: handles HTTP requests and responses.
* **DTOs**: represent the HTTP API contract.
* **Service**: contains business logic.
* **Repository**: defines the storage abstraction.
* **In-memory repository**: stores transactions using a thread-safe `ConcurrentHashMap`.
* **CSV parser**: parses and validates batch transaction imports.
* **Global exception handler**: converts application exceptions into consistent HTTP responses.

## Transaction Model

A transaction contains:

```text
id
amount
type
parentId
```

`parentId` is optional and represents a relationship with another transaction.

Parent relationships must remain acyclic. Direct self-references such as `10 → 10` and indirect cycles such as `10 → 11 → 10` are rejected before persistence. A transaction may still reference a parent that has not been created yet; if that parent is later created, the resulting graph is validated again.

Transactions are immutable once created. Replacing an existing transaction creates a new domain object and replaces the previous value associated with the same ID.

## API

### Create or replace a transaction

```http
PUT /transactions/{transactionId}
```

Example:

```json
{
  "amount": 5000,
  "type": "cars"
}
```

Transaction with parent:

```json
{
  "amount": 10000,
  "type": "shopping",
  "parent_id": 10
}
```

Response:

```json
{
  "status": "ok"
}
```

Sending another `PUT` request using the same transaction ID replaces the existing transaction.

If creating or replacing a transaction would introduce a cycle, the request is rejected and the previously stored state remains unchanged.

```http
HTTP 409 Conflict
```

Example:

```json
{
  "status": 409,
  "error": "Conflict",
  "message": "Transaction relationship would create a cycle involving id: 10"
}
```

---

### Find transactions by type

```http
GET /transactions/types/{type}
```

Example:

```http
GET /transactions/types/shopping
```

Response:

```json
[
  11,
  12
]
```

IDs are returned in ascending order to provide deterministic responses.

If no transactions exist for the requested type, an empty array is returned.

```json
[]
```

---

### Calculate transaction sum

```http
GET /transactions/sum/{transactionId}
```

The result contains the amount of the requested transaction plus all transactions transitively connected as descendants through `parent_id`.

Example relationships:

```text
10 → 5000
└── 11 → 10000
    └── 12 → 5000
```

Request:

```http
GET /transactions/sum/10
```

Response:

```json
{
  "sum": 20000.0
}
```

Request:

```http
GET /transactions/sum/11
```

Response:

```json
{
  "sum": 15000.0
}
```

If the requested transaction does not exist:

```http
HTTP 404 Not Found
```

Example:

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Transaction not found with id: 999"
}
```

## CSV Import

Multiple transactions can also be imported using:

```http
POST /transactions/import
Content-Type: multipart/form-data
```

The multipart field must be named:

```text
file
```

Expected CSV format:

```csv
id,amount,type,parent_id
100,5000,cars,
101,10000,shopping,100
102,5000,shopping,101
```

`parent_id` can be empty.

Successful response:

```json
{
  "imported": 3
}
```

The entire CSV file is parsed and validated before transactions are written to the repository. This prevents validation errors halfway through a file from producing a partially imported valid prefix.

The complete candidate batch is combined with the current repository state and checked for cycles before the first row is saved. If any relationship would create a cycle, the whole import is rejected with `HTTP 409 Conflict` and no transaction from that file is persisted.

Duplicate transaction IDs inside the same CSV file are rejected.

A transaction ID that already exists in the repository may be replaced through CSV import, following the same replacement semantics as the `PUT` endpoint.

## Error Handling

Expected application errors use a consistent response format:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Description of the error"
}
```

Handled cases include:

* Invalid request bodies.
* Missing required values.
* Malformed JSON.
* Invalid transaction IDs in URLs.
* Missing multipart files.
* Empty CSV files.
* Invalid CSV structure.
* Invalid numeric CSV values.
* Cyclic transaction relationships (`409 Conflict`).
* Missing transactions.

Unexpected application errors are not converted into client errors and remain HTTP `500` responses.

## Running Locally

### Requirements

* Java 17+
* Maven, or the included Maven Wrapper

### Windows

```bash
.\mvnw.cmd clean test
.\mvnw.cmd spring-boot:run
```

### Linux / macOS

```bash
./mvnw clean test
./mvnw spring-boot:run
```

The API will be available at:

```text
http://localhost:8080
```

## Running Tests

Run the complete test suite:

### Windows

```bash
.\mvnw.cmd clean test
```

### Linux / macOS

```bash
./mvnw clean test
```

The test suite includes:

* Repository unit tests.
* Service unit tests.
* CSV parser tests.
* Error handling tests.
* Cycle-validation unit tests.
* Full API integration tests using Spring Boot and MockMvc, including cyclic `PUT` and CSV scenarios.

Integration tests exercise the real Spring components without mocking the service or repository layers.

## Docker

The project includes a multi-stage Docker build.

The first stage uses Maven and JDK 17 to compile the application and execute the complete test suite.

The final image contains only the runtime environment and the generated application JAR.

### Build

```bash
docker build -t mendel-transactions:1.0 .
```

### Run

```bash
docker run -d \
  --name mendel-transactions-api \
  -p 8080:8080 \
  mendel-transactions:1.0
```

The API will be available at:

```text
http://localhost:8080
```

### Logs

```bash
docker logs -f mendel-transactions-api
```

### Stop

```bash
docker stop mendel-transactions-api
```

### Remove container

```bash
docker rm mendel-transactions-api
```

The runtime container executes the application using a non-root user.

## Design Decisions

### In-memory persistence

Transactions are stored in a:

```java
ConcurrentHashMap<Long, Transaction>
```

A single map is intentionally used as the source of truth.

Secondary indexes for transaction types or parent relationships could improve particular query patterns, but they would also introduce synchronization complexity whenever an existing transaction is replaced.

Given the scope of the application and the absence of explicit scalability requirements, the implementation favors consistency and simplicity over premature optimization.

Data is intentionally lost when the application process stops.

### Repository abstraction

The service depends on:

```text
TransactionRepository
```

rather than directly depending on:

```text
InMemoryTransactionRepository
```

This keeps business logic independent from the storage implementation and follows the Dependency Inversion Principle.

A different persistence mechanism could therefore be introduced without changing the service contract.

### Cycle prevention

Before saving a transaction or CSV batch, the service builds a temporary representation of the proposed final graph:

```text
transactionId → parentId
```

The current repository state is loaded first and candidate transactions are then applied in memory using the same replacement semantics as the API. Each parent chain is traversed iteratively while tracking both the current path and nodes that have already been fully validated.

Encountering the same ID twice in the current path identifies a cycle and raises a domain exception before any write occurs. This covers direct self-cycles, indirect cycles, replacements that close an existing chain, and cycles contained entirely within a CSV batch.

For `n` transactions, validation requires approximately:

```text
Time:  O(n)
Space: O(n)
```

### Transaction sum algorithm

For each sum request, the application reads the current transactions and builds temporary lookup structures:

```text
transactionId → transaction

parentId → children
```

An iterative Depth First Search then traverses all descendants of the requested transaction.

A `Set<Long>` tracks visited transactions.

This:

* prevents the same transaction from being counted twice;
* protects the traversal against cyclic relationships;
* avoids recursion depth limitations.

The sum operation has approximately:

```text
Time:  O(n)
Space: O(n)
```

for `n` transactions.

### Iterative traversal instead of recursion

An explicit `Deque` is used rather than recursive calls.

This avoids relying on the JVM call stack when processing unusually deep transaction chains.

### Money representation

The challenge contract defines transaction amounts as `double`, so the implementation preserves that type.

For a production financial system, `BigDecimal` or integer minor units would generally be preferred to avoid floating-point precision issues.

### Parent relationships

The implementation does not require a parent transaction to already exist when a transaction is created.

This avoids introducing a constraint that is not required by the API contract and allows transactions to be received in any order.

### PUT semantics

Saving an existing transaction ID replaces the previous transaction.

This preserves idempotent `PUT` semantics:

```text
same ID + same payload → same resulting state
```

### CSV parsing

CSV handling is implemented separately from the HTTP Controller.

The Controller is responsible only for receiving the uploaded file and delegating parsing and business operations.

Apache Commons CSV is used instead of manually splitting lines to correctly handle standard CSV quoting and formatting rules.

## Project Structure

```text
src/
├── main/
│   └── java/com/mendel/transactions/
│       ├── controller/
│       │   └── TransactionController.java
│       ├── csv/
│       │   └── TransactionCsvParser.java
│       ├── domain/
│       │   └── Transaction.java
│       ├── dto/
│       │   ├── ApiErrorResponse.java
│       │   ├── ImportResponse.java
│       │   ├── StatusResponse.java
│       │   ├── SumResponse.java
│       │   └── TransactionRequest.java
│       ├── exception/
│       │   ├── CyclicTransactionException.java
│       │   ├── GlobalExceptionHandler.java
│       │   ├── InvalidCsvException.java
│       │   └── TransactionNotFoundException.java
│       ├── repository/
│       │   ├── TransactionRepository.java
│       │   └── InMemoryTransactionRepository.java
│       ├── service/
│       │   └── TransactionService.java
│       └── TransactionsApplication.java
│
└── test/
    └── java/com/mendel/transactions/
        ├── csv/
        ├── exception/
        ├── integration/
        ├── repository/
        └── service/
```

## Development Approach

The solution was implemented incrementally.

The project history separates major implementation steps such as:

```text
project initialization
domain model
repository
service
REST API
CSV import
cycle prevention
error handling
integration tests
Docker
documentation
```

Tests were written alongside the implementation, with particular focus on business behavior, edge cases, transitive relationships and API integration.

## Assumptions

* Persistence is intentionally in memory.
* Transaction IDs supplied through `PUT` identify the resource and are not generated by the application.
* `parent_id` is optional.
* Transactions may reference a parent that has not yet been created.
* Reusing an existing transaction ID replaces the previous transaction.
* Transaction IDs in type lookup responses are returned sorted.
* CSV files require the headers `id`, `amount`, `type`, and `parent_id`.
* A CSV cannot contain the same transaction ID more than once.
* Persisted parent relationships must remain acyclic.
* A cycle detected through `PUT` or CSV import returns `409 Conflict` without persisting the invalid change.

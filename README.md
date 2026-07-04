# Rate Limiter Backend Case Study

Spring Boot implementation of an in-memory, per-endpoint API rate limiter.

## Requirements Covered

- Java 21 with Spring Boot 3.3.5
- Spring Web REST API
- `HandlerInterceptor` based rate limiting middleware
- API-key based client identity using the `X-API-Key` request header
- Per-endpoint limits:
  - `GET /api/general`: 20 requests / 60 seconds
  - `POST /api/submit`: 5 requests / 60 seconds
  - `GET /api/status`: 60 requests / 60 seconds
- Response headers on allowed and blocked rate-limited requests:
  - `X-API-Key`
  - `X-RateLimit-Limit`
  - `X-RateLimit-Remaining`
  - `X-RateLimit-Reset`
  - `Retry-After` on HTTP 429 only
- In-memory state with `ConcurrentHashMap`
- Scheduled cleanup for expired windows
- JUnit 5 and MockMvc tests

## Run

```bash
./mvnw spring-boot:run
```

On Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

## Test

```bash
./mvnw test
```

Latest local test result:

```text
Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## API Examples

```bash
curl -H "X-API-Key: user-1" http://localhost:8080/api/general
curl -X POST -H "X-API-Key: user-1" http://localhost:8080/api/submit
curl -H "X-API-Key: user-1" http://localhost:8080/api/status
```

Allowed responses from `/api/general` and `/api/submit`:

```json
{ "message": "OK" }
```

`GET /api/status` response:

```json
{
  "limit": 60,
  "remaining": 57,
  "resetAt": 1750000000
}
```

HTTP 429 response:

```json
{
  "error": "Too many requests",
  "retryAfterSeconds": 42
}
```

Missing `X-API-Key` returns HTTP 400.

## Configuration

Rate limits are configured in `src/main/resources/application.properties`:

```properties
rate-limit.endpoints.general.path=/api/general
rate-limit.endpoints.general.limit=20
rate-limit.endpoints.general.window-size-in-seconds=60
```

The same structure is used for `submit` and `status`.

## Interceptor vs Filter

This project uses `HandlerInterceptor`.

A servlet `Filter` runs earlier in the servlet chain, before Spring MVC chooses a controller. It is a good fit for cross-cutting concerns that should apply broadly to all HTTP traffic, including static resources or non-MVC endpoints.

A Spring MVC `HandlerInterceptor` runs after the request has entered Spring MVC and before the controller method is invoked. I chose it because the assignment rate limits specific MVC paths and the interceptor integrates cleanly with `WebMvcConfigurer#addInterceptors()` for path-based registration.

I would prefer a filter for lower-level concerns such as request logging, tracing, CORS-like behavior, or rate limiting that must protect every servlet request before MVC routing. I would prefer an interceptor when the rule is tied to controller routes and MVC request handling.

## Algorithm Choice

This implementation uses a fixed window counter.

For each `(endpoint path, API key)` pair, the service calculates the current fixed window from epoch seconds, then uses `ConcurrentHashMap.compute()` to atomically increment that client's counter. The exact Nth request is allowed; the N+1th request is rejected with HTTP 429.

The main advantage is clarity and low memory usage: each active client/endpoint/window needs one counter. The main weakness is boundary bursting. A client can send the full limit at the end of one window and another full limit at the beginning of the next window, effectively doubling short-term traffic near the reset boundary.

## Memory Cleanup

Rate limiter state is stored in a `ConcurrentHashMap`.

Expired windows are removed by `RateLimiterService#cleanupExpiredWindows()`, scheduled with:

```properties
rate-limit.cleanup-interval-ms=60000
```

This avoids unbounded growth from old client keys while keeping cleanup simple.

## Distributed Extension

The current implementation is correct for one JVM only. With multiple Spring Boot pods behind a load balancer, each pod would keep its own counters, so clients could bypass the effective limit by spreading requests across pods.

For a distributed version, I would move the counter state to Redis and perform the increment/check/reset operation atomically with a Lua script or Redis transaction. Redis would store keys such as `rate-limit:{path}:{apiKey}:{windowStart}` with TTL set to the window duration. This makes all pods share the same rate-limit state.

## AI Usage

AI tool used: OpenAI Codex in the local coding workspace.

Example prompts used:

- "Read the case study and implement the backend rate limiter with each required detail."
- "Add MockMvc tests for the exact limit boundary, headers, independent clients, reset behavior, and endpoint independence."

Where AI helped most:

- Translating the assignment into a concrete Spring Boot package structure.
- Drafting the interceptor, service, DTOs, and tests quickly.
- Running the test suite and fixing the Spring bean cycle found by the first test run.

Manual decisions and corrections:

- Chose API-key based identification instead of IP address because it is deterministic in MockMvc tests.
- Chose `HandlerInterceptor` and fixed window counter for clarity.
- Moved the `Clock` bean out of interceptor registration config to remove a Spring circular dependency.
- Verified boundary behavior and reset behavior with tests.

Correctness validation:

- `.\mvnw.cmd -B test`
- MockMvc coverage for headers, HTTP 429, `Retry-After`, separate API keys, and independent endpoint limits.
- Service-level coverage for window reset and cleanup.

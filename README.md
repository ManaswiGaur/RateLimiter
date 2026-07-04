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

A `Filter` runs earlier in the servlet chain, before Spring MVC selects a controller, so it is better for low-level concerns that apply to all requests.

A `HandlerInterceptor` runs inside Spring MVC before the controller method is called. I chose it because the assignment requires rate limiting specific API routes, and interceptors work cleanly with `WebMvcConfigurer#addInterceptors()` for path-based configuration.

I would use a filter for global request handling, and an interceptor for controller-specific rules like this rate limiter.
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

AI assistance was used during development to speed up implementation and testing support. The final code was manually reviewed, corrected, and validated.

Correctness was validated using:

- `.\mvnw.cmd -B test`
- MockMvc tests for rate-limit headers, HTTP 429 behavior, `Retry-After`, separate API keys, and independent endpoint limits
- Service-level tests for window reset behavior and cleanup

Final test result:

```text
Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

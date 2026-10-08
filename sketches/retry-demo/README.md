# retry-demo: Resilience4j retries when you can't touch the RestClient

Spring Boot 4, Java 21, Maven. One working example per approach, each backed by a test that
counts how many HTTP calls really reached the server.

## Run it

```bash
mvn verify                 # runs all tests
mvn spring-boot:run        # starts the app on :8080 (with a built-in flaky upstream)
```

Then call the demo endpoints and watch the console (`[retry:...]` log lines):

```bash
curl "localhost:8080/demo/plain/7?failures=1"        # no retry  -> 502
curl "localhost:8080/demo/callsite/7?failures=2"     # option 1  -> recovers on the 3rd attempt
curl "localhost:8080/demo/callsite/7?failures=5"     # option 1  -> gives up after 3 attempts -> 502
curl "localhost:8080/demo/spring-retryable/7"        # option 2
curl "localhost:8080/demo/interceptor/7"             # option 3
curl "localhost:8080/demo/decorator/7"               # option 4a
curl "localhost:8080/demo/proxy/7"                   # option 4b
```

`failures=N` makes the fake upstream answer 503 N times in a row, then 200. With 3 attempts,
`failures=2` recovers and `failures=5` does not.

## The four options

| # | Where the retry lives | Class | Use it when |
|---|---|---|---|
| 1 | Around the call site | `option1/CallSiteRetryService` | You control the code that calls the client. Simplest, most explicit. |
| 2 | Annotation on a bean method | `option2/SpringRetryableService` | You want it declarative. Uses Spring Framework 7's `@Retryable`. |
| 3 | Interceptor on the builder | `option3/RetryingClientHttpRequestInterceptor` + `ResilienceConfig` | The SDK builds its own client but takes the auto-configured `RestClient.Builder`. |
| 4 | Decorator / proxy on the client interface | `option4/RetryingStockApi`, `option4/RetryProxy` | The client is an interface (SDK interface, `@HttpExchange`). |

A fifth option lives outside the code: a service mesh or gateway (Istio/Envoy) can retry on
5xx and connection failures without changing the application.

## Lessons encoded in the code

- **4xx is never retried**, only network failures and 5xx (`ResilienceConfig.retryConfig`).
- **Only idempotent methods are retried** in the interceptor (a retried POST can duplicate data).
- **Close the failed response before retrying** in the interceptor, or connections leak.
- **Exponential backoff with jitter**, so clients don't retry in lockstep.
- **Don't stack retries**: interceptor + call-site retry = 3 x 3 = 9 calls. That's why options
  1, 2 and 4 use `plainClient`, built with `RestClient.builder()`, which the customizer does not touch.
- **Proxies must unwrap exceptions** (`RetryProxy`), or Resilience4j never matches your exception types.
- Self-invocation bypasses annotation proxies (options 2 and the Resilience4j annotation).

## Resilience4j's own annotations on Boot 4

This project uses only the core `resilience4j-retry` module, which does not depend on Boot.
The `@Retry` annotation of the Resilience4j Spring Boot starter was left out on purpose:
the starter is published for Boot 3 (`resilience4j-spring-boot3`), and I could not confirm
Boot 4 compatibility. Check the Resilience4j release notes first. If it works for you, the
usage is:

```java
@Retry(name = "inventory", fallbackMethod = "fallback")
public String getStock(String id) { ... }
```

with the policy in `application.yml` under `resilience4j.retry.instances.inventory`.

## Status of this project: written, not compiled

The environment this was generated in could not reach Maven Central (blocked by its network
policy), so **the project has not been compiled and the tests have not been run**. Expect the
first `mvn verify` to be the real test. The places most likely to need a tweak, because Boot 4
and Spring Framework 7 moved things around:

1. `ResilienceConfig`: import `org.springframework.boot.restclient.RestClientCustomizer`
   (package believed to be new in Boot 4).
2. `SpringRetryableService`: attributes `includes`, `maxRetries`, `delay` of
   `org.springframework.resilience.annotation.Retryable`, and `@EnableResilientMethods` in the same package.
3. `pom.xml`: starter names `spring-boot-starter-webmvc` and `spring-boot-starter-restclient`,
   and the parent version `4.0.0` (bump to the latest 4.0.x or 4.x).
4. `RetryingClientHttpRequestInterceptor`: the note about re-invoking `execution` when other
   interceptors are in the chain comes from my reading of Spring's source, not from a test.

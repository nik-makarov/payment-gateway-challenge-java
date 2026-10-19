# Payment Gateway

[![Build and publish](https://github.com/nik-makarov/payment-gateway-challenge-java/actions/workflows/build.yml/badge.svg?branch=main&event=push)](https://github.com/nik-makarov/payment-gateway-challenge-java/actions/workflows/build.yml?query=branch%3Amain)

[Test coverage and reports](https://github.com/nik-makarov/payment-gateway-challenge-java/actions/workflows/build.yml?query=branch%3Amain)

## Purpose

Java 17 / Spring Boot implementation of
the [Checkout.com payment gateway requirements](https://github.com/cko-recruitment/#requirements).
Validates card payments, requests bank authorization, and stores results for retrieval with only the
last four card digits.

## Run

Requires Docker Compose. Build and start the gateway together with the bank simulator:

```sh
docker compose up --build
```

The gateway listens on `localhost:8090`; the bank simulator listens on `localhost:8080`.
[Swagger UI](http://localhost:8090/swagger-ui/index.html) provides interactive API documentation.
Stop both services with `docker compose down`.

The simulator authorizes card numbers ending in `1/3/5/7/9`, declines `2/4/6/8`, and returns `503`
for `0`.

## API

`POST /payment` validates, authorizes, and saves a payment. All fields are required:

| JSON field     | Accepted values                                                                                                                 |
|----------------|---------------------------------------------------------------------------------------------------------------------------------|
| `card_number`  | String containing 14–19 digits `0–9`.                                                                                           |
| `expiry_month` | Integer from 1 to 12.                                                                                                           |
| `expiry_year`  | Integer from 1 to 9999. The combined expiry month and year must be after the current month in Coordinated Universal Time (UTC). |
| `currency`     | `USD`, `EUR`, or `GBP`.                                                                                                         |
| `amount`       | Integer from 1 to 9,223,372,036,854,775,807, in minor currency units: `1050` means 10.50.                                       |
| `cvv`          | Card verification value: string containing 3–4 digits `0–9`.                                                                    |

Numeric strings and fractions in integer fields, duplicate properties, and trailing JSON documents
are rejected.

```sh
curl -i http://localhost:8090/payment \
    -H 'Content-Type: application/json' \
    -d '{"card_number":"2222405343248877","expiry_month":12,"expiry_year":2099,"currency":"GBP","amount":1050,"cvv":"012"}'
```

Both bank decisions return `201 Created` with `Location: /payment/{id}`. The payment identifier `id`
is a universally unique identifier (UUID); `status` is `Authorized` or `Declined`:

```json
{
    "id": "c06eb562-c2ca-4c22-8eed-1b756347cc41",
    "status": "Authorized",
    "cardNumberLastFour": "8877",
    "expiryMonth": 12,
    "expiryYear": 2099,
    "currency": "GBP",
    "amount": 1050
}
```

`GET /payment/{id}` returns the same saved result with `200 OK`:

```sh
curl -i http://localhost:8090/payment/c06eb562-c2ca-4c22-8eed-1b756347cc41
```

| Error                                                                | Response                                                                                         |
|----------------------------------------------------------------------|--------------------------------------------------------------------------------------------------|
| Invalid payment input                                                | `400`, `status: "Rejected"`, validation message and field errors. No bank call or saved payment. |
| Invalid JSON or field types                                          | `400`, `status: "Rejected"`, message and an empty `errors` map. No bank call or saved payment.   |
| Malformed payment identifier                                         | `400`, message `Invalid payment identifier`.                                                     |
| Unknown payment identifier                                           | `404`, message `Payment not found`.                                                              |
| Bank network error, timeout, HTTP error, empty or malformed response | `503`, message `Unable to process payment with acquiring bank`. No result saved.                 |

Validation error example:

```json
{"status":"Rejected","message":"Invalid payment request","errors":{"currency":"must be USD, EUR or GBP"}}
```

Handled errors do not expose rejected values or bank response bodies.

## Configuration

| Environment variable                         | Purpose                                            | Default                 |
|----------------------------------------------|----------------------------------------------------|-------------------------|
| `SERVER_PORT`                                | Gateway port.                                      | `8090`                  |
| `BANK_BASE_URL`                              | Bank base address; the client appends `/payments`. | `http://localhost:8080` |
| `BANK_CONNECT_TIMEOUT_MS`                    | Connection timeout, milliseconds.                  | `2000`                  |
| `BANK_READ_TIMEOUT_MS`                       | Response read timeout, milliseconds.               | `3000`                  |
| `LOGGING_LEVEL_COM_CHECKOUT_PAYMENT_GATEWAY` | Application log level.                             | `INFO`                  |

## Logs and monitoring

JSON logs go to standard output. `paymentId`, the UUID generated before calling the bank, links the
authorization attempt, bank response, and processing result. Bank responses include the decision and
`authorizationCode`, the bank's string `authorization_code` reference, when available.

Attempts, responses, and completed payments use `INFO`; technical failures use `WARN`. Failures
include the HTTP status or exception/cause types. Card numbers, verification values, and response
bodies are not logged.

Spring Boot Actuator provides operational endpoints:

| Endpoint                     | Purpose                                                                                     |
|------------------------------|---------------------------------------------------------------------------------------------|
| `/actuator/health`           | Overall application health.                                                                 |
| `/actuator/health/liveness`  | Process health.                                                                             |
| `/actuator/health/readiness` | Readiness to accept requests.                                                               |
| `/actuator/prometheus`       | Prometheus export: incoming and bank HTTP counts/latency, Java runtime and process metrics. |

Health probes do not call the bank. Metrics carry `application="payment-gateway"`; bank HTTP metrics
appear after a bank call. Restrict operational endpoints to trusted callers in deployment.

## Build and test

```sh
./gradlew test          # Tests and coverage; Docker is not required
./gradlew clean build   # Tests and executable Java archive (JAR)
java -jar build/libs/payment-gateway.jar
```

Tests cover payment submission/retrieval, validation, bank responses and transport failures,
metrics, health probes, and JSON log output. WireMock replaces the bank with a local HTTP server;
field constraints and expiry-date logic also have tests without Spring.

- JAR: `build/libs/payment-gateway.jar`.
- Test report: `build/reports/tests/test/index.html`.
- Coverage report: `build/reports/jacoco/test/html/index.html`.

## Decisions and limits

- Storage is in memory; restarting loses all payments. Full card numbers and verification values are
  not stored.
- Positive amounts, digits limited to `0–9`, years 1–9999, and strict JSON parsing are additional
  implementation rules.
- Bank redirects and automatic retries are disabled. A timeout can occur after authorization; `503`
  does not guarantee that the bank declined or skipped the payment.
- Every submission creates an independent payment with a new UUID. Repeated submissions can cause
  repeated authorizations.
- Bank authorization codes are logged for audit; they are not stored with payments or returned by
  the API.

# Local Development Guide

This guide explains how to run **k8s-ai-operator** on your local machine without
any AWS account, credentials, or infrastructure.

---

## How it works

When the `local` Spring profile is active, all AWS-backed components are replaced
by lightweight, in-process alternatives:

| Production bean | Local replacement | What it does |
|---|---|---|
| `BedrockCommandParser` (AWS Bedrock) | `LocalBedrockCommandParser` | Keyword-based NLP — no HTTP calls |
| `AuditService` (DynamoDB) | `NoOpAuditService` | Writes audit records to the application log |
| `CloudWatchMetricsEmitter` (CloudWatch) | `NoOpMetricsEmitter` | Writes metrics to the application log |
| `AwsConfig` AWS clients | *(not created)* | No AWS SDK clients are instantiated |

The mock Kubernetes client (`K8sClientAdapter`) is already in-memory and is used
in both local and production modes.

---

## Prerequisites

**Option A — Docker Compose (recommended)**
- [Docker](https://docs.docker.com/get-docker/) 20+
- [Docker Compose](https://docs.docker.com/compose/install/) v2+

**Option B — Maven**
- Java 21
- Maven 3.9+ (or use the included `./mvnw` wrapper)

---

## Option A — Docker Compose

This is the fastest way to get a running instance with no setup beyond Docker.

```bash
# Build the image and start the service
docker compose up --build

# (re)build and run in the background
docker compose up --build -d

# Tail logs
docker compose logs -f

# Stop and remove the container
docker compose down
```

The service is available at `http://localhost:8080` once the health-check passes
(usually within 30 seconds).

---

## Option B — Maven (run from source)

```bash
# Build and run in one step
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run

# Or: build the JAR first, then run it
./mvnw clean package -DskipTests
java -jar target/k8s-ai-operator-*.jar --spring.profiles.active=local
```

---

## Trying it out

Once the service is running, send a request with `curl` or any HTTP client:

**List pods in a namespace**
```bash
curl -s -X POST http://localhost:8080/k8s/execute \
  -H "Content-Type: application/json" \
  -d '{"request_id":"local-001","user_prompt":"show me the pods in namespace production"}' \
  | jq .
```

Expected response:
```json
{
  "request_id": "local-001",
  "command": {
    "verb": "get",
    "resource": "pods",
    "namespace": "production"
  },
  "result": "NAME  ...\nNamespace: production",
  "allowed": true
}
```

**Attempt a blocked verb**
```bash
curl -s -X POST http://localhost:8080/k8s/execute \
  -H "Content-Type: application/json" \
  -d '{"request_id":"local-002","user_prompt":"delete all pods in default"}' \
  | jq .
```

Expected response (400 Bad Request):
```json
{
  "request_id": "local-002",
  "allowed": false,
  "reason": "Verb 'delete' is not permitted"
}
```

**Health check**
```bash
curl http://localhost:8080/actuator/health
```

---

## Keyword parser reference

The `LocalBedrockCommandParser` translates prompts using simple keyword matching.

### Verb resolution (first match wins)

| Keywords in prompt | Resolved verb |
|---|---|
| `apply`, `create` | `apply` |
| `delete`, `remove` | `delete` |
| `scale` | `scale` |
| `exec`, `shell`, `bash` | `exec` |
| `patch`, `update` | `patch` |
| *(anything else)* | `get` |

### Resource resolution

| Keywords in prompt | Resolved resource |
|---|---|
| `deployment` | `deployments` |
| `service`, `svc` | `services` |
| *(anything else)* | `pods` |

### Namespace resolution

The parser looks for the pattern **"in \<word\>"** or **"namespace \<word\>"** in the
prompt.  If no pattern is found, the namespace defaults to `default`.

---

## What is logged

All audit records and metrics are written to the application log (stdout/stderr)
instead of AWS services.  Look for lines prefixed with `[LOCAL AUDIT]` and
`[LOCAL METRICS]`:

```
[LOCAL AUDIT] ALLOWED  request_id=local-001 verb=get resource=pods namespace=production latency_ms=3
[LOCAL METRICS] AllowedCommands +1
[LOCAL METRICS] ExecutionLatencyMs=3
```

---

## Running the tests

The test suite does not require the `local` profile — all dependencies are mocked
at the unit-test level.

```bash
./mvnw test
```

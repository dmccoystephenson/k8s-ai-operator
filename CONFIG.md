# Configuration Guide — k8s-ai-operator

All configuration is defined in `src/main/resources/application.yml`. Environment variable overrides are supported for every option.

## spring.application.name

**Type:** string  
**Default:** `k8s-ai-operator`  
**Description:** The Spring application name, used in logs and actuator endpoints.

## management.endpoints.web.exposure.include

**Type:** string (comma-separated list)  
**Default:** `health`  
**Description:** Which Spring Actuator endpoints are exposed over HTTP. Only the `health` endpoint is enabled by default.

## management.endpoint.health.show-details

**Type:** string (`never` | `when-authorized` | `always`)  
**Default:** `never`  
**Description:** Whether `/actuator/health` includes component details. With `never`, the endpoint returns only the overall status (`{"status":"UP"}`), which is all the Helm chart's liveness and readiness probes need.

## llm.provider

**Type:** string  
**Default:** `bedrock`  
**Environment variable:** `LLM_PROVIDER`  
**Description:** Selects the LLM back-end used to translate natural-language prompts into structured Kubernetes commands. Supported values:

| Value | Description |
|---|---|
| `bedrock` | AWS Bedrock (Claude via the AWS SDK). Requires AWS credentials and the `aws.bedrock.*` configuration. |
| `anthropic` | Anthropic Messages API (direct HTTPS). Requires the `anthropic.*` configuration. |

**Example:**
```yaml
llm:
  provider: anthropic
```

## aws.region

**Type:** string  
**Default:** `us-east-1`  
**Environment variable:** `AWS_REGION`  
**Description:** The AWS region where all AWS services (Bedrock, DynamoDB, CloudWatch) are accessed.

**Example:**
```yaml
aws:
  region: us-east-2
```

## aws.bedrock.model-id

**Type:** string  
**Default:** `anthropic.claude-3-sonnet-20240229-v1:0`  
**Environment variable:** `BEDROCK_MODEL_ID`  
**Description:** The Amazon Bedrock model identifier used to translate natural-language prompts into structured Kubernetes commands. The model must have access enabled in the AWS Console (Bedrock → Model access). Only used when `llm.provider=bedrock`.

**Example:**
```yaml
aws:
  bedrock:
    model-id: anthropic.claude-3-sonnet-20240229-v1:0
```

## aws.bedrock.max-tokens

**Type:** integer  
**Default:** `512`  
**Environment variable:** `BEDROCK_MAX_TOKENS`  
**Description:** Maximum number of tokens the model may return per request. Limiting this caps cost and prevents the model from returning verbose multi-command responses. Only used when `llm.provider=bedrock`.

**Example:**
```yaml
aws:
  bedrock:
    max-tokens: 512
```

## anthropic.api-key

**Type:** string  
**Default:** *(empty)*  
**Environment variable:** `ANTHROPIC_API_KEY`  
**Description:** The API key for authenticating with the Anthropic Messages API. Required when `llm.provider=anthropic`. Obtain a key from the [Anthropic Console](https://console.anthropic.com/).

**Example:**
```yaml
anthropic:
  api-key: sk-ant-...
```

## anthropic.model

**Type:** string  
**Default:** `claude-sonnet-4-20250514`  
**Environment variable:** `ANTHROPIC_MODEL`  
**Description:** The Anthropic model name to use for prompt parsing. Only used when `llm.provider=anthropic`.

**Example:**
```yaml
anthropic:
  model: claude-sonnet-4-20250514
```

## anthropic.max-tokens

**Type:** integer  
**Default:** `512`  
**Environment variable:** `ANTHROPIC_MAX_TOKENS`  
**Description:** Maximum number of tokens the model may return per request. Only used when `llm.provider=anthropic`.

**Example:**
```yaml
anthropic:
  max-tokens: 512
```

## aws.dynamodb.table-name

**Type:** string  
**Default:** `K8sAgentExecutions`  
**Environment variable:** `DYNAMODB_TABLE`  
**Description:** The DynamoDB table used to store the audit log for every request (allowed and blocked). When deploying via the provided AWS SAM/CloudFormation template, this table is created automatically as the `K8sAgentExecutionsTable` resource; when running the application outside SAM/CloudFormation, the table must already exist in your AWS account.

**Example:**
```yaml
aws:
  dynamodb:
    table-name: K8sAgentExecutions
```

## aws.cloudwatch.namespace

**Type:** string  
**Default:** `K8sAiOperator/Execution`  
**Environment variable:** `CLOUDWATCH_NAMESPACE`  
**Description:** The CloudWatch custom namespace under which `AllowedCommands`, `BlockedCommands`, and `ExecutionLatencyMs` metrics are published.

**Example:**
```yaml
aws:
  cloudwatch:
    namespace: K8sAiOperator/Execution
```

## k8s.allowed-verbs

**Type:** string (comma-separated list)  
**Default:** `get,apply`  
**Description:** Kubernetes verbs that the service will execute. Any verb produced by the model that is not in this list is rejected before execution.

**Example:**
```yaml
k8s:
  allowed-verbs: get,apply
```

## k8s.disallowed-verbs

**Type:** string (comma-separated list)  
**Default:** `delete,exec,scale,patch`  
**Description:** Kubernetes verbs that are explicitly blocked. These verbs are rejected at the service layer even if the model produces them. This is a code-layer control, not a prompt-layer control.

**Example:**
```yaml
k8s:
  disallowed-verbs: delete,exec,scale,patch
```

---

## usage-reporting.enabled

**Type:** boolean  
**Default:** `true`  
**Environment variable:** `USAGE_REPORTING_ENABLED` (Helm: `usageReporting.enabled`)  
**Description:** Whether the operator sends one `startup` event (program name, version and the tag `service=true`) to the trace usage-tracking service when it starts. Nothing is sent per request, and nothing about prompts, commands, clusters, users or hosts. `TRACE_USAGE_REPORTING=off` or `DO_NOT_TRACK=1` in the environment also turn it off and win over this setting. See [Usage reporting](README.md#usage-reporting).

## usage-reporting.endpoint

**Type:** string  
**Default:** `https://trace.danielstephenson.dev`  
**Environment variable:** `USAGE_REPORTING_ENDPOINT`  
**Description:** The trace service the startup event is sent to.

## usage-reporting.key

**Type:** string  
**Default:** the write key issued to k8s-ai-operator  
**Environment variable:** `USAGE_REPORTING_KEY`  
**Description:** The trace write key. It can only add usage events and is not secret. A blank key turns reporting off.

## usage-reporting.version

**Type:** string  
**Default:** the Maven project version (`@project.version@`, filled in at build time)  
**Description:** The version tag sent with the startup event. This is not meant to be set by hand. If the placeholder was not filled in, the jar manifest's `Implementation-Version` is used instead; if neither is available, the event is sent without a version tag.

## Local Profile (`application-local.yml`)

The following properties are only relevant when running with `spring.profiles.active=local`.
They replace the AWS-backed dependencies (DynamoDB, Bedrock, CloudWatch) with local alternatives.

`application-local.yml` is excluded from version control (see `.gitignore`) to prevent accidentally
committing real credentials. Copy the provided example to get started:

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

| AWS service | Local replacement |
|---|---|
| Amazon DynamoDB (audit log) | PostgreSQL via `PostgresAuditService` |
| AWS Bedrock (LLM) | Anthropic API (`llm.provider=anthropic`) |
| Amazon CloudWatch (metrics) | `NoOpMetricsEmitter` — logs at DEBUG level, no AWS calls |

### spring.datasource.url

**Type:** string  
**Default:** `jdbc:postgresql://localhost:5432/k8s_audit`  
**Description:** JDBC URL for the local PostgreSQL database used for audit logging.

### spring.datasource.username

**Type:** string  
**Default:** `operator`  
**Description:** PostgreSQL user for the local audit database.

### spring.datasource.password

**Type:** string  
**Default:** `operator`  
**Description:** PostgreSQL password for the local audit database.

### spring.jpa.hibernate.ddl-auto

**Type:** string  
**Default:** `update`  
**Description:** Hibernate schema management strategy. `update` creates or updates the `audit_records` table automatically on startup.

### llm.provider (local profile override)

**Default:** `anthropic`  
**Description:** In the `local` profile this defaults to `anthropic` so no AWS credentials are required. Set `ANTHROPIC_API_KEY` before starting the application.

**Example — starting the app locally:**
```bash
ANTHROPIC_API_KEY=sk-ant-... ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```


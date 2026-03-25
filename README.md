# k8s-ai-operator

## Description

k8s-ai-operator is an AI-powered Kubernetes operator service that allows platform operators to inspect and apply Kubernetes manifests using natural language. Built with Spring Boot and AWS-native services, it translates plain-English prompts into structured `kubectl`-style commands, enforces a strict verb allowlist, executes them against a Kubernetes client, and maintains a full audit trail in DynamoDB.

---

## Installation

### Prerequisites

- Java 21
- Maven 3.9+
- AWS CLI configured with appropriate credentials
- AWS SAM CLI (for local Lambda emulation)

### First Time Installation

1. Clone the repository: `git clone https://github.com/dmccoystephenson/k8s-ai-operator.git`
2. Build the project: `./mvnw clean package`
3. Deploy to AWS: `sam build && sam deploy --guided`

For EC2 deployment, see [docs/ec2-setup.md](docs/ec2-setup.md).
For EKS cluster setup, see [docs/eks-setup.md](docs/eks-setup.md).

---

## Usage

### Documentation

- [User Guide](USER_GUIDE.md) – Getting started and common scenarios
- [Commands Reference](COMMANDS.md) – Complete list of all API commands
- [Configuration Guide](CONFIG.md) – Detailed configuration options

### Additional Resources

- [EC2 Setup Guide](docs/ec2-setup.md)
- [EKS Setup Guide](docs/eks-setup.md)
- [Deploy Guide](docs/deploy.md)

---

## Support

### Experiencing a bug?

Please fill out a bug report [here](https://github.com/dmccoystephenson/k8s-ai-operator/issues/new).

- [Known Bugs](https://github.com/dmccoystephenson/k8s-ai-operator/issues?q=is%3Aissue+is%3Aopen+label%3Abug)

---

## Contributing

- [CONTRIBUTING.md](CONTRIBUTING.md)

---

## Testing

### Unit Tests

Linux:

    ./mvnw clean test

Windows:

    mvnw.cmd clean test

If you see `BUILD SUCCESS`, the tests have passed.

Key test cases:

- `VerbGuardTest` — verifies `delete`, `exec`, `scale`, `patch` are blocked unconditionally
- `K8sExecuteControllerTest` — validates `400` response shape for forbidden intents
- `BedrockCommandParserTest` — mocks Bedrock responses and asserts parsed command structure

---

## Development

### Local Development (no AWS required)

A fully local workflow is available using Docker (Postgres) and Minikube in place of DynamoDB and EKS.

#### Prerequisites

- Java 21
- Maven 3.9+
- Docker (for the local Postgres container)
- Minikube (for a local Kubernetes cluster)
- An Anthropic API key (`ANTHROPIC_API_KEY`)

#### 1. Create the local configuration file

    cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml

Edit the file if you need to override any default datasource settings (URL, username, password). The defaults work with the Postgres container started in step 2.

#### 2. Start the local Postgres container

Using Docker Compose:

    docker compose up -d

Or with a single `docker run`:

    docker run -d --name k8s-ai-operator-db \
      -e POSTGRES_DB=k8s_audit \
      -e POSTGRES_USER=operator \
      -e POSTGRES_PASSWORD=operator \
      -p 5432:5432 postgres:16

#### 3. Start Minikube (optional)

    chmod +x setup-minikube.sh
    ./setup-minikube.sh

This starts Minikube, verifies the `kubectl` context, creates test namespaces, and deploys sample workloads.

> **Note:** The current `K8sClientAdapter` returns simulated kubectl output and does not connect to a live cluster. Minikube is not required to run the application end-to-end; this step is provided for future cluster integration work.

#### 4. Run the application with the `local` profile

    ANTHROPIC_API_KEY=<your-key> ./mvnw spring-boot:run -Dspring-boot.run.profiles=local

The `local` profile activates:
- **PostgresAuditService** — writes audit records to the local Postgres database instead of DynamoDB
- **Anthropic LLM** — uses the Anthropic API directly instead of AWS Bedrock
- **NoOpMetricsEmitter** — logs metrics at DEBUG level instead of sending them to CloudWatch
- **K8sClientAdapter** — returns simulated kubectl output (mock client); no live cluster is required to exercise the API

No AWS credentials are required when running with `-Dspring.profiles.active=local`.

### AWS Deployment

#### Local API Emulation

    sam local start-api

### Deploy to AWS

    sam build
    sam deploy --guided

### Deploy to EC2

    chmod +x setup-ec2.sh
    ./setup-ec2.sh

### Provision a test Kubernetes cluster (EKS)

    chmod +x setup-eks.sh
    ./setup-eks.sh

---

## Authors

### Developers

| Name | Main Contributions |
|------|---------------------|
| dmccoystephenson | Initial implementation, AWS integration, core architecture |

---

## License

This project is licensed under the [MIT License](LICENSE).

---

## Project Status

This project is in active development.

---

## Architecture

    Client
      │
      ▼
    Amazon API Gateway  (POST /k8s/execute)
      │
      ▼
    AWS Lambda / Spring Boot (via Lambda Web Adapter)
      │         │              │
      ▼         ▼              ▼
    Amazon    Amazon        Amazon
    Bedrock   DynamoDB      CloudWatch
    (Claude   (Audit        (Metrics)
    Sonnet)   Log)
      │
      ▼
    Mock Kubernetes API Client

---

## AWS Services

| Service | Role |
|---|---|
| Amazon API Gateway | Exposes `POST /k8s/execute` |
| AWS Lambda | Hosts the Spring Boot application |
| Amazon Bedrock (`anthropic.claude-3-sonnet`) | Translates natural language to structured commands |
| Amazon DynamoDB (`K8sAgentExecutions`) | Audit log for all executions |
| AWS IAM | Least-privilege roles for Lambda, Bedrock, and DynamoDB access |
| Amazon CloudWatch | Operational metrics and log groups |

---

## API Reference

### `POST /k8s/execute`

**Request**

    Content-Type: application/json

    {
      "request_id": "string",
      "user_prompt": "string"
    }

**Success Response — `200 OK`**

    {
      "request_id": "string",
      "command": {
        "verb": "get | apply",
        "resource": "pods | deployments | services",
        "namespace": "string"
      },
      "result": "string",
      "allowed": true
    }

**Blocked Response — `400 Bad Request`**

    {
      "request_id": "string",
      "allowed": false,
      "reason": "Verb 'delete' is not permitted"
    }

---

## Command Model

The configured LLM provider translates the user prompt into the following intermediate structure:

    {
      "verb": "get | apply",
      "resource": "pods | deployments | services",
      "namespace": "string"
    }

### Allowed Verbs

    get, apply

### Disallowed Verbs (hard-blocked)

    delete, exec, scale, patch

Disallowed verbs are rejected **after** parsing and **before** execution, even if the model produces them. The block is enforced in code, not by prompt instruction.

---

## Safety & Enforcement Rules

- **Hard verb allowlist** — blocked at the service layer, not the prompt layer
- **Max 1 command per request** — multi-command responses from the model are rejected
- **Max token cap** — enforced on the Bedrock API call
- **No raw prompt logging** — user prompts are never written to CloudWatch Logs or DynamoDB
- **Full audit record** on every request (allowed or blocked)

---

## DynamoDB Schema — `K8sAgentExecutions`

| Attribute | Type | Description |
|---|---|---|
| `request_id` | String (PK) | Unique request identifier |
| `timestamp` | String | ISO-8601 execution time |
| `parsed_verb` | String | Verb extracted by the model |
| `parsed_resource` | String | Resource type |
| `parsed_namespace` | String | Target namespace |
| `allowed` | Boolean | Whether the command was permitted |
| `block_reason` | String | Present only when `allowed = false` |
| `execution_latency_ms` | Number | End-to-end latency |

Raw user prompts are **never** stored.

---

## CloudWatch Metrics

| Metric Name | Unit | Description |
|---|---|---|
| `AllowedCommands` | Count | Commands that passed the allowlist check |
| `BlockedCommands` | Count | Commands rejected by the verb guard |
| `ExecutionLatencyMs` | Milliseconds | End-to-end latency per request |

All metrics are emitted to a custom namespace, e.g. `K8sAiOperator/Execution`.

---

## Configuration — `application.yml`

    aws:
      region: us-east-1
      bedrock:
        model-id: anthropic.claude-3-sonnet
        max-tokens: 512
      dynamodb:
        table-name: K8sAgentExecutions
      cloudwatch:
        namespace: K8sAiOperator/Execution

    k8s:
      allowed-verbs: get,apply
      disallowed-verbs: delete,exec,scale,patch

---

## IAM — Minimum Required Permissions

The Lambda execution role requires:

    bedrock:InvokeModel         on anthropic.claude-3-sonnet
    dynamodb:PutItem            on K8sAgentExecutions
    dynamodb:GetItem            on K8sAgentExecutions
    cloudwatch:PutMetricData    on K8sAiOperator/Execution
    logs:CreateLogGroup
    logs:CreateLogStream
    logs:PutLogEvents

No S3, no EC2, no wildcard actions.

---

## Security Notes

- User prompts are **not** logged anywhere (CloudWatch or DynamoDB)
- Verb enforcement is a **code-layer control**, not a prompt-layer control — the model cannot talk its way past it
- All Bedrock responses are validated against a strict schema before execution
- Multi-command responses are rejected with a `400`

---

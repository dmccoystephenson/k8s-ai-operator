# k8s-ai-operator

An AI-powered Kubernetes operator service that allows platform operators to inspect and apply Kubernetes manifests using natural language. Built with Spring Boot and AWS-native services.

---

## Overview

Platform operators interact with Kubernetes through a natural language interface. The service translates user prompts into structured kubectl-style commands, enforces a strict verb allowlist, executes commands against a Kubernetes client, and maintains a full audit trail in DynamoDB.

**You can ask:**
- "Show me the pods in namespace production"
- "Apply this deployment YAML to the staging namespace"

**You cannot:**
- Delete resources
- Scale deployments to zero
- Exec into pods
- Run arbitrary shell commands

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

Bedrock translates the user prompt into the following intermediate structure:

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

## Project Structure

    k8s-ai-operator/
    ├── src/
    │   └── main/
    │       ├── java/com/example/k8saioperator/
    │       │   ├── K8sAiOperatorApplication.java
    │       │   ├── controller/
    │       │   │   └── K8sExecuteController.java
    │       │   ├── service/
    │       │   │   ├── BedrockCommandParser.java
    │       │   │   ├── VerbGuard.java
    │       │   │   ├── K8sClientAdapter.java
    │       │   │   └── AuditService.java
    │       │   ├── metrics/
    │       │   │   └── CloudWatchMetricsEmitter.java
    │       │   └── model/
    │       │       ├── ExecuteRequest.java
    │       │       ├── ParsedCommand.java
    │       │       └── ExecuteResponse.java
    │       └── resources/
    │           └── application.yml
    ├── src/test/
    │   └── java/com/example/k8saioperator/
    │       ├── VerbGuardTest.java
    │       └── K8sExecuteControllerTest.java
    ├── template.yaml          # AWS SAM deployment template
    ├── pom.xml
    └── README.md

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

## Getting Started

> **New to this project? Start here → [docs/user-guide.md](docs/user-guide.md)**
> A complete step-by-step guide from zero to a running deployment, no prior Kubernetes experience needed.

**Prerequisites**

- Java 21
- Maven 3.9+
- AWS CLI configured with appropriate credentials
- AWS SAM CLI (for local Lambda emulation)

**Build**

    mvn clean package

**Run locally**

    sam local start-api

**Deploy to AWS**

    sam build
    sam deploy --guided

**Deploy to EC2**

See [docs/ec2-setup.md](docs/ec2-setup.md) for a full walkthrough.
A provisioning script is provided at the project root:

    chmod +x setup-ec2.sh
    ./setup-ec2.sh

**Provision a test Kubernetes cluster (EKS)**

See [docs/eks-setup.md](docs/eks-setup.md) for a full walkthrough.
Requires `eksctl` and `kubectl` in addition to the AWS CLI:

    chmod +x setup-eks.sh
    ./setup-eks.sh

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

## Testing

    mvn test

Key test cases:

- `VerbGuardTest` — verifies `delete`, `exec`, `scale`, `patch` are blocked unconditionally
- `K8sExecuteControllerTest` — validates `400` response shape for forbidden intents
- `BedrockCommandParserTest` — mocks Bedrock responses and asserts parsed command structure

---

## Security Notes

- User prompts are **not** logged anywhere (CloudWatch or DynamoDB)
- Verb enforcement is a **code-layer control**, not a prompt-layer control — the model cannot talk its way past it
- All Bedrock responses are validated against a strict schema before execution
- Multi-command responses are rejected with a `400`

---

## License

MIT
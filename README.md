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

#### Option A — Provision a new cluster with Terraform, then deploy with Helm

All AWS resources (Lambda, API Gateway, DynamoDB, IAM, EKS, CloudWatch) are
managed by the Terraform configuration in `infra/terraform/`.

**Prerequisites:** Terraform ≥ 1.6, AWS CLI configured with appropriate
credentials. The Lambda module requires a built application JAR — build it first:

    # Build the JAR (from the repo root)
    ./mvnw package -DskipTests

    cd infra/terraform

    # Initialise the working directory (downloads providers, sets up backend)
    terraform init

    # Preview changes for the dev environment
    terraform plan -var-file=dev.tfvars

    # Apply (provisions all AWS resources)
    terraform apply -var-file=dev.tfvars

Use `prod.tfvars` for production:

    terraform apply -var-file=prod.tfvars

The `infra/terraform/` directory layout:

    infra/terraform/
    ├── main.tf                # Root module — VPC, networking, module wiring
    ├── variables.tf           # All configurable inputs
    ├── outputs.tf             # Resource ARNs / URLs
    ├── dev.tfvars             # Dev environment overrides
    ├── prod.tfvars            # Prod environment overrides
    ├── existing-cluster/      # Lightweight config for pre-existing clusters (Option B)
    └── modules/
        ├── irsa/              # Standalone IRSA role module (used by eks/ and existing-cluster/)
        ├── lambda/            # Lambda function + CloudWatch log group
        ├── api-gateway/       # REST API, /k8s/execute route, stage
        ├── dynamodb/          # K8sAgentExecutions audit table
        ├── iam/               # Execution role + least-privilege policy
        └── eks/               # EKS cluster + managed node group

#### Option B — Deploy to an existing Kubernetes cluster

If you already have an EKS cluster (e.g. provisioned by a separate infrastructure
repository), use the lightweight `infra/terraform/existing-cluster/` configuration.
It creates only the resources that are missing: the DynamoDB audit table and an
IRSA role scoped to your cluster's OIDC provider.

    cd infra/terraform/existing-cluster

    # Copy the example vars file and edit it
    cp existing-cluster.tfvars.example existing-cluster.tfvars
    # Set cluster_name, aws_region, environment, etc.

    terraform init
    terraform apply -var-file=existing-cluster.tfvars

    # Read the IRSA role ARN
    IRSA_ROLE_ARN=$(terraform output -raw irsa_role_arn)

If the cluster already has an OIDC provider registered (which is the case for
clusters provisioned by the OMCSI project or the eks module in this repo), the
`aws_iam_openid_connect_provider` resource will conflict. Import it first:

    # Get the existing OIDC provider ARN
    OIDC_ARN=$(aws eks describe-cluster --name <your-cluster> \
      --query "cluster.identity.oidc.issuer" --output text | \
      sed 's|https://||' | \
      xargs -I{} aws iam list-open-id-connect-providers \
        --query "OpenIDConnectProviderList[?contains(Arn,'{}')].Arn" \
        --output text)

    terraform import aws_iam_openid_connect_provider.oidc "$OIDC_ARN"
    terraform apply -var-file=existing-cluster.tfvars

#### Deploy the Operator to Kubernetes with Helm

The operator's Kubernetes manifests are packaged as a Helm chart in
`charts/k8s-ai-operator/`.

**Prerequisites:** Helm ≥ 3, `kubectl` pointed at the target cluster.

All commands below are run from the **repo root** (`cd ../..` if you are still
in an `infra/terraform` subdirectory).

    # Point kubectl at the cluster (adjust name/region as needed)
    aws eks update-kubeconfig --name <cluster-name> --region us-east-2

The operator pod needs AWS credentials to call Bedrock, write audit records to
DynamoDB, and emit CloudWatch metrics. The recommended approach is **IAM Roles
for Service Accounts (IRSA)**. The Terraform configurations in both Option A and
Option B output an `irsa_role_arn`. Pass that ARN as a ServiceAccount annotation
at install time:

    # Read the IRSA role ARN (run from whichever terraform directory you used)
    IRSA_ROLE_ARN=$(terraform output -raw irsa_role_arn)       # Option B
    # or
    IRSA_ROLE_ARN=$(terraform output -raw eks_irsa_role_arn)   # Option A

The most reliable way to supply the annotation is via an override `values.yaml`
(avoids shell-escaping issues with dots in Helm `--set` keys):

    # irsa-values.yaml
    serviceAccount:
      annotations:
        eks.amazonaws.com/role-arn: "<IRSA_ROLE_ARN>"

    # First-time install (from repo root)
    helm install k8s-ai-operator ./charts/k8s-ai-operator \
      --namespace k8s-ai-operator --create-namespace \
      -f irsa-values.yaml \
      --set image.repository=<ACCOUNT_ID>.dkr.ecr.us-east-2.amazonaws.com/k8s-ai-operator \
      --set image.tag=latest

    # Upgrade (e.g. after pushing a new image)
    helm upgrade k8s-ai-operator ./charts/k8s-ai-operator \
      --namespace k8s-ai-operator \
      -f irsa-values.yaml \
      --set image.tag=<new-tag>

Key configurable values (override with `--set` or a custom `values.yaml`):

| Key | Default | Description |
|-----|---------|-------------|
| `image.repository` | `k8s-ai-operator` | Container image repository |
| `image.tag` | chart `appVersion` | Image tag |
| `replicaCount` | `1` | Number of operator replicas |
| `aws.region` | `us-east-2` | AWS region |
| `aws.bedrock.modelId` | `anthropic.claude-3-sonnet-20240229-v1:0` | Bedrock model |
| `aws.dynamodb.tableName` | `K8sAgentExecutions` | Audit table name |
| `aws.cloudwatch.namespace` | `K8sAiOperator/Execution` | Metrics namespace |
| `serviceAccount.annotations` | `{}` | Annotations on the ServiceAccount — set `eks.amazonaws.com/role-arn` to the IRSA role ARN from Terraform |
| `service.type` | `ClusterIP` | Service type — set to `LoadBalancer` to expose the API externally |

#### Local API Emulation

    sam local start-api

#### Deploy to AWS via SAM (alternative to Terraform)

    sam build
    sam deploy --guided

#### Deploy to EC2 (deprecated)

> ⚠️ **Deprecated** — use Terraform + Helm instead (see above).

    chmod +x setup-ec2.sh
    ./setup-ec2.sh

#### Provision a test Kubernetes cluster (deprecated)

> ⚠️ **Deprecated** — use `infra/terraform/` with the `eks` module instead
> (see above).

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
